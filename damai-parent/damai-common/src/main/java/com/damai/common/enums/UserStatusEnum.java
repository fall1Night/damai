package com.damai.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 用户状态枚举 —— 对应 PRD §7.1 用户域状态流转。
 *
 * <p>状态流转：
 * <pre>
 *   注册 ──▶ 正常
 *            │
 *            ├──违规/管理──▶ 禁用（禁止登录与下单）
 *            │
 *            └──注销──▶ 已注销
 * </pre>
 */
@Getter
@AllArgsConstructor
public enum UserStatusEnum {

    /** 正常 */
    NORMAL(1, "正常"),

    /** 禁用（登录失败次数过多或违规） */
    DISABLED(0, "禁用"),

    /** 已注销 */
    DELETED(-1, "已注销");

    private final Integer code;
    private final String desc;

    public static UserStatusEnum of(Integer code) {
        return Arrays.stream(values())
                .filter(e -> e.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    /** 是否可登录 */
    public boolean canLogin() {
        return this == NORMAL;
    }
}
