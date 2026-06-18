package com.damai.pay.service;

import com.damai.api.dto.pay.PayDto;
import com.damai.pay.entity.Pay;

import java.util.Map;

/**
 * 支付服务接口 —— 对应 PRD §7.4 支付域用例。
 *
 * <p>方法职责：
 * <ul>
 *   <li>{@link #createPay} —— UC-PAY-01 发起支付（模拟）</li>
 *   <li>{@link #payCallback} —— UC-PAY-02 支付结果回调（⭐核心，幂等）</li>
 *   <li>{@link #queryPayStatus} —— UC-PAY-03 支付状态查询</li>
 *   <li>{@link #refund} —— UC-PAY-04 模拟退款</li>
 * </ul>
 *
 * @author damai
 */
public interface PayService {

    /**
     * 发起支付（模拟）。
     *
     * <p>校验订单状态（待支付）→ 生成支付单 → 返回模拟支付参数。
     *
     * @param userId    用户 ID
     * @param orderId   订单 ID
     * @param payMethod 支付方式：1微信(模拟) 2支付宝(模拟) 3余额(模拟)
     * @return 模拟支付参数（含 payNo、payUrl、payAmount、expireTime）
     */
    Map<String, Object> createPay(Long userId, Long orderId, Integer payMethod);

    /**
     * 支付结果回调（⭐核心，幂等）。
     *
     * <p>幂等校验（Redis 防重 + 状态机双重）→ 更新支付单状态 → Kafka 异步通知订单已付。
     *
     * @param orderId    订单 ID
     * @param paySuccess 支付是否成功
     * @param payNo      支付单号（回调幂等键）
     * @param sign       回调签名（模拟，预留校验位）
     */
    void payCallback(Long orderId, Boolean paySuccess, String payNo, String sign);

    /**
     * 查询支付状态（按 orderId）。
     *
     * @param orderId 订单 ID
     * @return 支付单传输对象
     */
    PayDto queryPayStatus(Long orderId);

    /**
     * 模拟退款。
     *
     * @param userId  用户 ID
     * @param orderId 订单 ID
     */
    void refund(Long userId, Long orderId);

    /**
     * 内部接口：根据 orderId 查询支付单（供 order 服务对账通过 Feign 调用）。
     *
     * @param orderId 订单 ID
     * @return 支付单实体
     */
    Pay getPayByOrderId(Long orderId);
}
