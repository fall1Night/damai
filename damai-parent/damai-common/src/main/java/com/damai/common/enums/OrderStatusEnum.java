package com.damai.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 订单状态枚举 —— 对应 PRD §7.3 订单域状态流转。
 *
 * <p>状态机（不可逆规则见 PRD §10.5）：
 * <pre>
 *   待支付 ──支付成功──▶ 已支付 ──退款──▶ 已退款
 *     │
 *     ├──用户主动取消──▶ 已取消(手动)
 *     │
 *     └──15分钟超时────▶ 已取消(超时)
 * </pre>
 *
 * <p>设计要点：
 * <ul>
 *   <li>{@code code} 持久化到 DB（{@code t_order.status}）。</li>
 *   <li>{@code desc} 用于前端展示。</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
public enum OrderStatusEnum {

    /** 待支付 */
    WAIT_PAY(1, "待支付"),

    /** 已支付 */
    PAID(2, "已支付"),

    /** 已取消 - 用户手动取消 */
    CANCELED_MANUAL(3, "已取消"),

    /** 已取消 - 15 分钟超时自动取消 */
    CANCELED_TIMEOUT(4, "已取消"),

    /** 已退款 */
    REFUNDED(5, "已退款");

    /** 状态码（持久化） */
    private final Integer code;

    /** 状态描述（展示） */
    private final String desc;

    /**
     * 根据 code 解析枚举。
     *
     * @param code 状态码
     * @return 枚举值；未匹配返回 null
     */
    public static OrderStatusEnum of(Integer code) {
        return Arrays.stream(values())
                .filter(e -> e.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    /**
     * 判断是否为已取消状态（含手动/超时）。
     * 用于取消订单、归还库存等场景的统一判断。
     */
    public boolean isCanceled() {
        return this == CANCELED_MANUAL || this == CANCELED_TIMEOUT;
    }

    /**
     * 判断是否为终态（不可再变更）。
     * 已支付、已退款、已取消均为终态，但语义略有差异，此处仅标注"不再流转"。
     */
    public boolean isFinal() {
        return this == PAID || this == REFUNDED || isCanceled();
    }
}
