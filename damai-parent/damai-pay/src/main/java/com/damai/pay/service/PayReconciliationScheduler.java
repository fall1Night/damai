package com.damai.pay.service;

import com.damai.common.enums.PayStatusEnum;
import com.damai.pay.entity.Pay;
import com.damai.pay.manager.PayManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付对账定时任务 —— 对应 PRD §7.4.3 UC-PAY-03。
 *
 * <p>职责：扫描「待支付且未回调」的支付单，主动查询支付方（模拟），补偿状态：
 * <ul>
 *   <li>未过期：模拟查询支付方，命中成功则补发支付成功回调；</li>
 *   <li>已过期：置为「已关闭」，释放占用（避免僵尸支付单）。</li>
 * </ul>
 *
 * <p>这是「支付已成功但回调丢失」极端场景的最终兜底（PRD §10.5）。
 *
 * @author damai
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayReconciliationScheduler {

    private final PayManager payManager;
    private final PayService payService;

    /**
     * 单次对账扫描条数上限
     */
    private static final int SCAN_LIMIT = 200;

    /**
     * 对账执行频率：每 2 分钟扫描一次
     */
    private static final long FIXED_DELAY_MS = 2 * 60 * 1000L;

    /**
     * 模拟支付方查询成功率（对账兜底用，真实场景调用支付方查单接口）
     */
    private static final double MOCK_QUERY_SUCCESS_RATE = 0.9;

    /**
     * 对账主流程：扫描待对账支付单 → 逐条补偿。
     */
    @Scheduled(fixedDelay = FIXED_DELAY_MS)
    public void reconcile() {
        List<Pay> pendingList = payManager.listPendingCallback(SCAN_LIMIT);
        if (pendingList.isEmpty()) {
            log.debug("对账扫描：无待处理支付单");
            return;
        }
        log.info("对账扫描：发现 {} 笔待处理支付单", pendingList.size());
        for (Pay pay : pendingList) {
            try {
                handleOne(pay);
            } catch (Exception e) {
                log.error("对账处理异常：payNo={}, orderId={}", pay.getPayNo(), pay.getOrderId(), e);
            }
        }
    }

    /**
     * 处理单笔待对账支付单。
     * <p>已过期 → 关闭；未过期 → 模拟查询支付方结果。
     *
     * @param pay 待对账支付单
     */
    private void handleOne(Pay pay) {
        // 1. 已过期：关闭支付单，释放占用
        if (payManager.isExpired(pay)) {
            closePay(pay);
            return;
        }
        // 2. 未过期：模拟查询支付方结果
        if (mockQueryPayResult(pay)) {
            // 命中成功：补发支付成功回调（复用 payCallback，自带幂等）
            payService.payCallback(pay.getOrderId(), true, pay.getPayNo(), null);
            log.info("对账补偿：补发支付成功回调 payNo={}, orderId={}", pay.getPayNo(), pay.getOrderId());
        }
    }

    /**
     * 关闭已过期的支付单（置「已关闭」）。
     *
     * @param pay 支付单
     */
    private void closePay(Pay pay) {
        pay.setStatus(PayStatusEnum.CLOSED.getCode());
        pay.setCallbackTime(LocalDateTime.now());
        payManager.update(pay);
        log.info("对账关闭过期支付单：payNo={}, orderId={}", pay.getPayNo(), pay.getOrderId());
    }

    /**
     * 模拟查询支付方结果（PRD §2.3 Non-Goals：不接真实支付通道）。
     * <p>真实场景应调用支付方查单接口；此处按成功率模拟。
     *
     * @param pay 支付单
     * @return true=支付方返回成功
     */
    private boolean mockQueryPayResult(Pay pay) {
        boolean success = Math.random() < MOCK_QUERY_SUCCESS_RATE;
        log.debug("模拟查询支付方：payNo={}, orderId={}, success={}", pay.getPayNo(), pay.getOrderId(), success);
        return success;
    }
}
