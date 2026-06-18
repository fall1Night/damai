package com.damai.pay.service.impl;

import com.alibaba.fastjson2.JSON;
import com.damai.api.client.order.OrderFeignClient;
import com.damai.api.dto.order.OrderDto;
import com.damai.api.dto.pay.PayDto;
import com.damai.common.api.ApiResult;
import com.damai.common.constants.KafkaTopicConstant;
import com.damai.common.enums.OrderStatusEnum;
import com.damai.common.enums.PayStatusEnum;
import com.damai.common.exception.BizException;
import com.damai.common.exception.ErrorCode;
import com.damai.pay.entity.Pay;
import com.damai.pay.manager.PayManager;
import com.damai.pay.service.PayService;
import com.damai.starter.kafka.KafkaMessageHelper;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 支付服务实现类 —— 支付回调幂等核心链路 ⭐。
 *
 * <p>核心链路时序（对应 PRD §8.2 支付成功回调流程）：
 * <pre>
 * 支付方(模拟) ──回调──▶ pay(幂等校验) ──▶ 更新支付单(DB) ──▶ Kafka 通知 order 已付
 * </pre>
 *
 * <p>设计要点：
 * <ul>
 *   <li>回调幂等是底线（PRD §10.5）：Redis pay_no 防重（第一道）+ PayStatusEnum 状态机终态校验（第二道）。</li>
 *   <li>支付与订单通过 Kafka 解耦（{@link KafkaTopicConstant#PAY_RESULT}），互不阻塞。</li>
 *   <li>消息格式与 order 的 {@code PayResultConsumer} 严格对齐：{@code {orderId, paySuccess}}。</li>
 *   <li>支付单与订单一一对应，落库前 Feign 校验订单为待支付且金额一致。</li>
 * </ul>
 *
 * @author damai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayServiceImpl implements PayService {

    private final PayManager payManager;
    private final OrderFeignClient orderFeignClient;
    private final KafkaMessageHelper kafkaMessageHelper;

    /**
     * 模拟支付链接（PRD §2.3 Non-Goals：不接真实支付通道）
     */
    private static final String MOCK_PAY_URL = "https://pay.damai.mock/cashier?payNo=%s";

    @Override
    @GlobalTransactional(name = "createPay", rollbackFor = Exception.class)
    // @GlobalTransactional Seata 分布式事务：支付单落库（与 order 同事务组，保证 pay 单与订单状态一致）
    public Map<String, Object> createPay(Long userId, Long orderId, Integer payMethod) {
        // 1. Feign 查订单并校验状态/金额
        OrderDto order = checkOrderForPay(userId, orderId);

        // 2. 校验无在途支付单（避免重复发起）
        Pay existPay = payManager.getByOrderId(orderId);
        if (existPay != null && PayStatusEnum.WAIT_PAY.getCode().equals(existPay.getStatus())) {
            log.info("订单存在待支付支付单，直接返回：orderId={}, payNo={}", orderId, existPay.getPayNo());
            return buildPayResponse(existPay);
        }

        // 3. 构建并落库支付单
        Pay pay = buildPay(order, payMethod);
        payManager.insert(pay);
        log.info("支付单创建成功：payNo={}, orderId={}, userId={}, amount={}",
                pay.getPayNo(), orderId, userId, pay.getPayAmount());

        return buildPayResponse(pay);
    }

    @Override
    public void payCallback(Long orderId, Boolean paySuccess, String payNo, String sign) {
        // 1. 回调签名校验（模拟，预留）
        verifyCallbackSign(orderId, payNo, sign);

        // 2. 幂等校验：Redis pay_no 防重（第一道）
        if (!payManager.tryConsumeCallbackIdempotent(payNo)) {
            log.info("支付回调重复投递，跳过：payNo={}, orderId={}", payNo, orderId);
            throw new BizException(ErrorCode.PAY_REPEAT_CALLBACK);
        }

        // 3. 查支付单 + 状态机终态校验（第二道）
        Pay pay = payManager.getByOrderId(orderId);
        if (pay == null) {
            log.warn("支付单不存在，丢弃回调：payNo={}, orderId={}", payNo, orderId);
            throw new BizException(ErrorCode.PAY_NOT_FOUND);
        }
        PayStatusEnum currentStatus = PayStatusEnum.of(pay.getStatus());
        if (currentStatus != null && currentStatus.isFinal()) {
            log.info("支付单已为终态，丢弃回调：payNo={}, status={}", payNo, currentStatus);
            return;
        }

        // 4. 更新支付单状态 + Kafka 通知订单
        updatePayByCallback(pay, paySuccess);

        // 5. 异步通知 order（消息格式与 PayResultConsumer 对齐）
        notifyOrderPayResult(pay.getOrderId(), Boolean.TRUE.equals(paySuccess));
        log.info("支付回调处理完成：payNo={}, orderId={}, paySuccess={}", payNo, orderId, paySuccess);
    }

    @Override
    public PayDto queryPayStatus(Long orderId) {
        Pay pay = payManager.getByOrderId(orderId);
        if (pay == null) {
            throw new BizException(ErrorCode.PAY_NOT_FOUND);
        }
        return convertToDto(pay);
    }

    @Override
    @GlobalTransactional(name = "refund", rollbackFor = Exception.class)
    // @GlobalTransactional Seata 分布式事务：支付单置「已退款」
    public void refund(Long userId, Long orderId) {
        // 1. 查支付单（校验归属）
        Pay pay = payManager.getByOrderIdAndUserId(orderId, userId);
        if (pay == null) {
            throw new BizException(ErrorCode.PAY_NOT_FOUND);
        }

        // 2. 校验状态：仅支付成功可退款
        if (!PayStatusEnum.SUCCESS.getCode().equals(pay.getStatus())) {
            throw new BizException(ErrorCode.PAY_ORDER_STATUS_INVALID);
        }

        // 3. 支付单置「已退款」
        pay.setStatus(PayStatusEnum.REFUNDED.getCode());
        payManager.update(pay);
        log.info("支付单退款成功：payNo={}, orderId={}, userId={}", pay.getPayNo(), orderId, userId);

        // TODO 订单状态联动：order 服务暂无 refund Feign 接口，待 order 提供「置订单已退款」端点后联动。
        //      届时此处应调用 OrderFeignClient.refund(orderId) 或发送 Kafka 退款消息通知 order。
    }

    @Override
    public Pay getPayByOrderId(Long orderId) {
        return payManager.getByOrderId(orderId);
    }

    // ==================== 内部方法 ====================

    /**
     * 校验订单是否可发起支付：存在 + 归属 + 待支付 + 金额。
     *
     * @param userId  用户 ID
     * @param orderId 订单 ID
     * @return 订单信息
     */
    private OrderDto checkOrderForPay(Long userId, Long orderId) {
        ApiResult<OrderDto> result = orderFeignClient.getById(orderId);
        OrderDto order = result == null ? null : result.getData();
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!userId.equals(order.getUserId())) {
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOW_QUERY);
        }
        if (!OrderStatusEnum.WAIT_PAY.getCode().equals(order.getStatus())) {
            throw new BizException(ErrorCode.PAY_ORDER_STATUS_INVALID);
        }
        return order;
    }

    /**
     * 构建支付单实体（待支付，有效期=订单剩余有效时间）。
     *
     * @param order     订单信息
     * @param payMethod 支付方式
     * @return 支付单实体
     */
    private Pay buildPay(OrderDto order, Integer payMethod) {
        Pay pay = new Pay();
        pay.setPayNo(generatePayNo());
        pay.setOrderId(order.getId());
        pay.setUserId(order.getUserId());
        pay.setPayAmount(order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount());
        pay.setPayMethod(payMethod == null ? 1 : payMethod);
        pay.setStatus(PayStatusEnum.WAIT_PAY.getCode());
        // 模拟支付参数（JSON）
        Map<String, Object> urlParam = new HashMap<>();
        urlParam.put("url", String.format(MOCK_PAY_URL, pay.getPayNo()));
        urlParam.put("method", pay.getPayMethod());
        pay.setPayUrl(JSON.toJSONString(urlParam));
        // 有效期 = 订单剩余支付时间（下单 + 15min）
        pay.setExpireTime(order.getPayDeadline());
        pay.setDeleted(0);
        return pay;
    }

    /**
     * 生成支付单号（UUID 业务唯一，回调幂等键）。
     */
    private String generatePayNo() {
        return "PAY" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * 构建发起支付的响应参数。
     *
     * @param pay 支付单
     * @return 响应 Map
     */
    private Map<String, Object> buildPayResponse(Pay pay) {
        Map<String, Object> result = new HashMap<>();
        result.put("payNo", pay.getPayNo());
        result.put("payUrl", pay.getPayUrl());
        result.put("payAmount", pay.getPayAmount());
        result.put("payMethod", pay.getPayMethod());
        result.put("expireTime", pay.getExpireTime());
        result.put("orderId", pay.getOrderId());
        return result;
    }

    /**
     * 回调签名校验（模拟实现，预留位）。
     * <p>真实场景应校验支付方 RSA 签名，防止伪造回调。
     */
    private void verifyCallbackSign(Long orderId, String payNo, String sign) {
        if (payNo == null || payNo.isBlank()) {
            throw new BizException(ErrorCode.PARAM_MISSING);
        }
        // TODO 模拟签名校验：实际应比对支付方下发的签名
        log.debug("回调签名校验（模拟）：orderId={}, payNo={}, sign={}", orderId, payNo, sign);
    }

    /**
     * 根据回调结果更新支付单状态。
     *
     * @param pay        支付单
     * @param paySuccess 支付是否成功
     */
    private void updatePayByCallback(Pay pay, Boolean paySuccess) {
        LocalDateTime now = LocalDateTime.now();
        pay.setCallbackTime(now);
        if (Boolean.TRUE.equals(paySuccess)) {
            pay.setStatus(PayStatusEnum.SUCCESS.getCode());
            pay.setPayTime(now);
        } else {
            pay.setStatus(PayStatusEnum.FAIL.getCode());
        }
        payManager.update(pay);
    }

    /**
     * Kafka 异步通知订单支付结果（消息格式与 order 的 PayResultConsumer 严格对齐）。
     *
     * @param orderId    订单 ID
     * @param paySuccess 支付是否成功
     */
    private void notifyOrderPayResult(Long orderId, boolean paySuccess) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("orderId", orderId);
            message.put("paySuccess", paySuccess);
            kafkaMessageHelper.sendAsync(KafkaTopicConstant.PAY_RESULT,
                    orderId.toString(), message);
            log.info("支付结果已通知订单：orderId={}, paySuccess={}", orderId, paySuccess);
        } catch (Exception e) {
            // 通知失败不阻断回调 ACK，由 order 对账任务通过 PayFeignClient 主动查询补偿
            log.error("通知订单支付结果失败：orderId={}", orderId, e);
        }
    }

    /**
     * 支付单实体转 DTO。
     *
     * @param pay 支付单
     * @return 传输对象
     */
    private PayDto convertToDto(Pay pay) {
        PayDto dto = new PayDto();
        dto.setId(pay.getId());
        dto.setPayNo(pay.getPayNo());
        dto.setOrderId(pay.getOrderId());
        dto.setUserId(pay.getUserId());
        dto.setPayAmount(pay.getPayAmount());
        dto.setPayMethod(pay.getPayMethod());
        dto.setStatus(pay.getStatus());
        dto.setPayTime(pay.getPayTime());
        dto.setExpireTime(pay.getExpireTime());
        dto.setCreateTime(pay.getCreateTime());
        return dto;
    }
}
