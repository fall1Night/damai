package com.damai.api.dto.program;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 库存预扣请求 —— 下单时 order 服务通过 Feign 调 program 服务的核心入参。
 *
 * <p>对应 PRD §8.1 抢票链路第 4 步「Redis Lua 预扣库存」、技术文档 §9.1。
 *
 * <p>字段语义：
 * <ul>
 *   <li>{@code showId} + {@code ticketTypeId}：唯一定位 Redis 库存 Key
 *       {@code damai:stock:{showId}:{ticketTypeId}}（RedisKeyConstant.STOCK）。</li>
 *   <li>{@code quantity}：本次扣减数量，必须 &gt; 0。</li>
 * </ul>
 *
 * <p>校验注解（{@code @NotNull}/@Min）：由被调用方 Controller 侧 {@code @Valid} 触发。
 */
@Data
public class StockDeductRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 场次 ID */
    @NotNull(message = "场次ID不能为空")
    private Long showId;

    /** 票档 ID */
    @NotNull(message = "票档ID不能为空")
    private Long ticketTypeId;

    /** 扣减数量（必须 >= 1） */
    @NotNull(message = "扣减数量不能为空")
    @Min(value = 1, message = "扣减数量必须大于0")
    private Integer quantity;
}
