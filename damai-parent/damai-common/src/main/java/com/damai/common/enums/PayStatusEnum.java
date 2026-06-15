package com.damai.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 支付状态枚举 —— 对应 PRD §7.4 支付域状态流转。
 *
 * <p>状态机：
 * <pre>
 *   待支付 ──支付成功──▶ 支付成功
 *     │
 *     ├──支付失败──▶ 支付失败
 *     │
 *     └──超时未回调──▶ 已关闭(对账任务主动查询后关闭)
 * </pre>
 *
 * <p>幂等要点（PRD §10.5）：支付回调可能重复投递，
 * 消费端需依据状态机校验，已成功/已关闭的支付单不再处理。
 */
@Getter
@AllArgsConstructor
public enum PayStatusEnum {

    /** 待支付（支付单已生成，等待用户支付） */
    WAIT_PAY(1, "待支付"),

    /** 支付成功 */
    SUCCESS(2, "支付成功"),

    /** 支付失败 */
    FAIL(3, "支付失败"),

    /** 已关闭（超时未支付，对账关闭） */
    CLOSED(4, "已关闭"),

    /** 已退款 */
    REFUNDED(5, "已退款");

    private final Integer code;
    private final String desc;

    public static PayStatusEnum of(Integer code) {
        return Arrays.stream(values())
                .filter(e -> e.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    /** 是否为终态（不再接受回调变更） */
    public boolean isFinal() {
        return this == SUCCESS || this == FAIL || this == CLOSED || this == REFUNDED;
    }
}
