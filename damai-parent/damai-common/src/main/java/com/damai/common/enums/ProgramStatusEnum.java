package com.damai.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 节目状态枚举 —— 对应 PRD §7.2 节目域生命周期。
 *
 * <p>状态流转：
 * <pre>
 *   草稿 ──上架──▶ 在售 ──结束──▶ 已结束
 *                  │
 *                  └──下架──▶ 已下架
 * </pre>
 *
 * <p>状态校验：抢票/下单时仅允许 {@link #ON_SALE} 状态的节目参与交易。
 */
@Getter
@AllArgsConstructor
public enum ProgramStatusEnum {

    /** 草稿（运营编辑中，未对外） */
    DRAFT(0, "草稿"),

    /** 在售（可购买） */
    ON_SALE(1, "在售"),

    /** 已下架（暂停售卖） */
    OFF_SHELF(2, "已下架"),

    /** 已结束（演出已过） */
    FINISHED(3, "已结束");

    private final Integer code;
    private final String desc;

    public static ProgramStatusEnum of(Integer code) {
        return Arrays.stream(values())
                .filter(e -> e.code.equals(code))
                .findFirst()
                .orElse(null);
    }
}
