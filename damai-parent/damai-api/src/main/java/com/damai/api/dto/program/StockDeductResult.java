package com.damai.api.dto.program;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 库存预扣结果 —— Lua 脚本原子预扣的返回码，对应技术文档 §9.1 Lua 脚本返回值。
 *
 * <p>用枚举而非原始 int，避免魔法数字散落业务代码。
 *
 * <p>Lua 脚本返回值约定：
 * <ul>
 *   <li>{@code 1} → {@link #SUCCESS}：库存充足，已原子扣减成功。</li>
 *   <li>{@code -1} → {@link #STOCK_NOT_ENOUGH}：库存不足，扣减未执行。</li>
 *   <li>{@code -2} → {@link #KEY_NOT_EXIST}：库存 Key 不存在（未预热）。</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
public enum StockDeductResult {

    /** 库存充足，扣减成功 */
    SUCCESS("success", "库存扣减成功"),

    /** 库存不足 */
    STOCK_NOT_ENOUGH("stock_not_enough", "库存不足"),

    /** 库存 Key 不存在（未预热） */
    KEY_NOT_EXIST("key_not_exist", "库存尚未预热");

    /** 结果码（与 Lua 返回值映射后存储） */
    private final String code;

    /** 描述 */
    private final String message;

    /**
     * 将 Lua 脚本的原始返回值映射为枚举。
     *
     * @param luaReturnValue Lua 脚本返回的 Long（1 / -1 / -2）
     * @return 对应枚举；未识别返回 null（理论上不会出现）
     */
    public static StockDeductResult fromLuaReturn(Long luaReturnValue) {
        if (luaReturnValue == null) {
            return null;
        }
        return switch (luaReturnValue.intValue()) {
            case 1 -> SUCCESS;
            case -1 -> STOCK_NOT_ENOUGH;
            case -2 -> KEY_NOT_EXIST;
            default -> null;
        };
    }

    /** 是否扣减成功 */
    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
