package com.damai.order.service.impl;

import com.damai.api.client.program.ProgramFeignClient;
import com.damai.api.dto.program.StockDeductRequest;
import com.damai.api.dto.program.StockDeductResult;
import com.damai.common.api.PageResult;
import com.damai.common.constants.KafkaTopicConstant;
import com.damai.common.constants.RedisKeyConstant;
import com.damai.common.enums.OrderStatusEnum;
import com.damai.common.exception.BizException;
import com.damai.common.exception.ErrorCode;
import com.damai.order.entity.LocalMessage;
import com.damai.order.entity.Order;
import com.damai.order.entity.OrderDetail;
import com.damai.order.manager.OrderDetailManager;
import com.damai.order.manager.OrderManager;
import com.damai.order.service.IdempotentTokenService;
import com.damai.order.service.OrderService;
import com.damai.starter.kafka.KafkaMessageHelper;
import com.damai.starter.redis.DistributedLockHelper;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 订单服务实现类 —— 抢票下单核心链路 ⭐
 *
 * <p>核心链路时序（对应 PRD §8.1）：
 * <pre>
 * 用户 → 网关(限流) → order(领令牌) → order(提交下单)
 *    → 幂等校验(消费令牌) → 防重锁(5s) → Lua预扣Redis(Feign)
 *    → 落单DB(Seata) → 本地消息表 → Kafka延迟消息(15min)
 *    → Kafka异步通知DB库存扣减 → 返回订单号+金额+截止时间
 * </pre>
 *
 * @author damai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderManager orderManager;
    private final OrderDetailManager orderDetailManager;
    private final IdempotentTokenService idempotentTokenService;
    private final ProgramFeignClient programFeignClient;
    private final DistributedLockHelper distributedLockHelper;
    private final KafkaMessageHelper kafkaMessageHelper;

    /**
     * 支付截止时间（分钟）
     */
    private static final int PAY_DEADLINE_MINUTES = 15;

    /**
     * 防重锁 Key 前缀
     */
    private static final String LOCK_PREFIX = "damai:lock:submit:";

    @Override
    public String generateToken(Long userId) {
        return idempotentTokenService.generateToken(userId);
    }

    @Override
    @GlobalTransactional(name = "createOrder", rollbackFor = Exception.class)
    public Map<String, Object> createOrder(Long userId, Long showId, Long ticketTypeId,
                                            Integer quantity, String viewerInfo, String token) {
        // ====== 1. 幂等校验：Redis GETDEL 原子消费令牌 ======
        if (!idempotentTokenService.consumeToken(token, userId)) {
            throw new BizException(ErrorCode.ORDER_IDEMPOTENT_TOKEN_INVALID);
        }

        // ====== 2. 防重锁：Redis 短窗口 5s 分布式锁 ======
        String lockKey = LOCK_PREFIX + userId + ":createOrder";
        Boolean locked = distributedLockHelper.tryLock(lockKey, 5,
                java.util.concurrent.TimeUnit.SECONDS);
        if (!locked) {
            throw new BizException(ErrorCode.ORDER_REPEAT_SUBMIT);
        }

        try {
            // ====== 3. 库存预扣：Feign 调用 program 服务 Lua 预扣 ======
            StockDeductRequest deductRequest = new StockDeductRequest();
            deductRequest.setShowId(showId);
            deductRequest.setTicketTypeId(ticketTypeId);
            deductRequest.setQuantity(quantity);

            var deductResult = programFeignClient.deductStock(deductRequest);
            if (deductResult.getData() == null || !deductResult.getData().isSuccess()) {
                StockDeductResult result = deductResult.getData();
                if (result == StockDeductResult.STOCK_NOT_ENOUGH) {
                    throw new BizException(ErrorCode.ORDER_STOCK_NOT_ENOUGH);
                } else {
                    throw new BizException(ErrorCode.ORDER_STOCK_NOT_ENOUGH, "系统繁忙，请稍后再试");
                }
            }

            // ====== 4. 落订单：DB 写入待支付订单（Seata AT 包裹） ======
            Order order = buildOrder(userId, showId, ticketTypeId, quantity, viewerInfo);
            orderManager.insert(order);

            // ====== 5. 落订单明细 ======
            OrderDetail detail = buildOrderDetail(order, showId, ticketTypeId, quantity, viewerInfo);
            orderDetailManager.insert(detail);

            // ====== 6. 本地消息表：与订单同一事务写入 ======
            saveLocalMessage(order);

            // ====== 7. 延迟消息：Kafka 投递超时取消消息（15min） ======
            sendCancelDelayMessage(order);

            // ====== 8. 异步通知：Kafka 投递"订单已创建"消息 ======
            sendOrderCreatedMessage(order);

            // ====== 9. 返回结果 ======
            Map<String, Object> result = new HashMap<>();
            result.put("orderId", order.getId());
            result.put("totalAmount", order.getTotalAmount());
            result.put("payDeadline", order.getPayDeadline());
            log.info("订单创建成功：orderId={}, userId={}, totalAmount={}", order.getId(), userId, order.getTotalAmount());
            return result;
        } finally {
            distributedLockHelper.unlock(lockKey);
        }
    }

    @Override
    @GlobalTransactional(name = "cancelOrder", rollbackFor = Exception.class)
    public void cancelOrder(Long userId, Long orderId) {
        // 1. 查询订单（校验归属）
        Order order = orderManager.getByIdAndUserId(orderId, userId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }

        // 2. 校验状态：只有待支付才能取消
        if (!OrderStatusEnum.WAIT_PAY.getCode().equals(order.getStatus())) {
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOW_CANCEL);
        }

        // 3. 更新订单状态为"已取消(手动)"
        order.setStatus(OrderStatusEnum.CANCELED_MANUAL.getCode());
        order.setCancelReason("用户主动取消");
        order.setCancelTime(LocalDateTime.now());
        orderManager.update(order);

        // 4. 归还 Redis 库存（Feign 调用 program 服务）
        refundStock(order);

        // 5. 异步归还 DB 库存（Kafka）
        sendRefundDbStockMessage(order);

        log.info("订单取消成功：orderId={}, userId={}", orderId, userId);
    }

    @Override
    public PageResult<Order> queryOrders(Long userId, Integer status, int pageNum, int pageSize) {
        return orderManager.listByUserId(userId, status, pageNum, pageSize);
    }

    @Override
    public Map<String, Object> queryOrderDetail(Long userId, Long orderId) {
        // 查询订单（校验归属）
        Order order = orderManager.getByIdAndUserId(orderId, userId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }

        // 查询明细
        List<OrderDetail> details = orderDetailManager.listByOrderId(orderId);

        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("details", details);
        return result;
    }

    // ==================== 内部方法 ====================

    /**
     * 构建订单实体
     */
    private Order buildOrder(Long userId, Long showId, Long ticketTypeId,
                              Integer quantity, String viewerInfo) {
        Order order = new Order();
        // 雪花 ID（实际项目中使用雪花算法生成，此处简化）
        order.setId(generateOrderId(userId));
        order.setUserId(userId);
        order.setShowId(showId);
        order.setTicketTypeId(ticketTypeId);
        order.setQuantity(quantity);
        // TODO: 查询票档价格计算总金额，此处先用占位
        order.setTotalAmount(BigDecimal.ZERO);
        order.setStatus(OrderStatusEnum.WAIT_PAY.getCode());
        order.setViewerInfo(viewerInfo);
        order.setPayDeadline(LocalDateTime.now().plusMinutes(PAY_DEADLINE_MINUTES));
        order.setDeleted(0);
        return order;
    }

    /**
     * 构建订单明细实体
     */
    private OrderDetail buildOrderDetail(Order order, Long showId, Long ticketTypeId,
                                          Integer quantity, String viewerInfo) {
        OrderDetail detail = new OrderDetail();
        detail.setOrderId(order.getId());
        detail.setUserId(order.getUserId());
        // TODO: 从 Feign 查询节目/场次/票档快照信息
        detail.setProgramId(0L);
        detail.setProgramTitle("");
        detail.setShowId(showId);
        detail.setShowTime(LocalDateTime.now());
        detail.setTicketTypeId(ticketTypeId);
        detail.setTicketTypeName("");
        detail.setVenue("");
        detail.setUnitPrice(BigDecimal.ZERO);
        detail.setQuantity(quantity);
        detail.setSubAmount(BigDecimal.ZERO);
        detail.setViewerName("");
        detail.setViewerIdCard("");
        detail.setDeleted(0);
        return detail;
    }

    /**
     * 生成订单 ID（雪花算法，低位嵌入 user_id 分片信息）
     * <p>简化实现：时间戳 + user_id 低 5 位 + 随机数
     */
    private Long generateOrderId(Long userId) {
        long timestamp = System.currentTimeMillis();
        long userMask = userId % 32;
        long random = (long) (Math.random() * 1000);
        return timestamp * 100 + userMask * 10 + random % 10;
    }

    /**
     * 保存本地消息表（与订单同事务）
     */
    private void saveLocalMessage(Order order) {
        // 超时取消消息
        LocalMessage cancelMsg = new LocalMessage();
        cancelMsg.setMessageId(UUID.randomUUID().toString());
        cancelMsg.setTopic(KafkaTopicConstant.ORDER_CANCEL_DELAY);
        cancelMsg.setBizKey(order.getId().toString());
        cancelMsg.setMessageBody(com.alibaba.fastjson2.JSON.toJSONString(Map.of(
                "orderId", order.getId(),
                "userId", order.getUserId(),
                "payDeadline", order.getPayDeadline().toString()
        )));
        cancelMsg.setRetryCount(0);
        cancelMsg.setStatus(0);
        cancelMsg.setNextRetryTime(order.getPayDeadline());
        cancelMsg.setDeleted(0);
        orderManager.insertLocalMessage(cancelMsg);

        // 订单创建消息
        LocalMessage createdMsg = new LocalMessage();
        createdMsg.setMessageId(UUID.randomUUID().toString());
        createdMsg.setTopic(KafkaTopicConstant.ORDER_CREATED);
        createdMsg.setBizKey(order.getId().toString());
        createdMsg.setMessageBody(com.alibaba.fastjson2.JSON.toJSONString(Map.of(
                "orderId", order.getId(),
                "userId", order.getUserId(),
                "showId", order.getShowId(),
                "ticketTypeId", order.getTicketTypeId(),
                "quantity", order.getQuantity()
        )));
        createdMsg.setRetryCount(0);
        createdMsg.setStatus(0);
        createdMsg.setNextRetryTime(LocalDateTime.now());
        createdMsg.setDeleted(0);
        orderManager.insertLocalMessage(createdMsg);
    }

    /**
     * 发送超时取消延迟消息
     */
    private void sendCancelDelayMessage(Order order) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("orderId", order.getId());
            message.put("userId", order.getUserId());
            message.put("payDeadline", order.getPayDeadline().toString());
            kafkaMessageHelper.sendAsync(KafkaTopicConstant.ORDER_CANCEL_DELAY,
                    order.getId().toString(), message);
        } catch (Exception e) {
            log.error("发送超时取消延迟消息失败：orderId={}", order.getId(), e);
            // 消息发送失败不影响主流程，由本地消息表补偿
        }
    }

    /**
     * 发送订单创建消息（异步扣 DB 库存）
     */
    private void sendOrderCreatedMessage(Order order) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("orderId", order.getId());
            message.put("userId", order.getUserId());
            message.put("showId", order.getShowId());
            message.put("ticketTypeId", order.getTicketTypeId());
            message.put("quantity", order.getQuantity());
            kafkaMessageHelper.sendAsync(KafkaTopicConstant.ORDER_CREATED,
                    order.getId().toString(), message);
        } catch (Exception e) {
            log.error("发送订单创建消息失败：orderId={}", order.getId(), e);
        }
    }

    /**
     * 归还 Redis 库存（Feign 调用 program 服务）
     */
    private void refundStock(Order order) {
        try {
            StockDeductRequest refundRequest = new StockDeductRequest();
            refundRequest.setShowId(order.getShowId());
            refundRequest.setTicketTypeId(order.getTicketTypeId());
            refundRequest.setQuantity(order.getQuantity());
            programFeignClient.refundStock(refundRequest);
        } catch (Exception e) {
            log.error("归还 Redis 库存失败：orderId={}", order.getId(), e);
            // 库存归还失败由对账任务补偿
        }
    }

    /**
     * 发送异步归还 DB 库存消息
     */
    private void sendRefundDbStockMessage(Order order) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("orderId", order.getId());
            message.put("showId", order.getShowId());
            message.put("ticketTypeId", order.getTicketTypeId());
            message.put("quantity", order.getQuantity());
            message.put("action", "REFUND");
            kafkaMessageHelper.sendAsync(KafkaTopicConstant.ORDER_CREATED,
                    order.getId().toString(), message);
        } catch (Exception e) {
            log.error("发送归还 DB 库存消息失败：orderId={}", order.getId(), e);
        }
    }
}
