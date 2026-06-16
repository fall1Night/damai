package com.damai.api.dto.program;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 票档传输对象 —— 对应 DDL {@code t_ticket_type}。
 *
 * <p>注意：{@code saleStock} 返回的是 Redis 实时预扣后的值（PRD §7.2.4 详情页实时库存），
 * 而非 DB 的兜底值。详情查询走 Manager 层从 Redis 读取。
 */
@Data
public class TicketTypeDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long showId;
    private Long programId;
    /** 票档名称（如 VIP / 980 / 580） */
    private String name;
    private BigDecimal price;
    /** 总库存（DB 权威值） */
    private Integer totalStock;
    /** 可售库存（实时，来自 Redis 预扣后值） */
    private Integer saleStock;
    private Integer sortOrder;
    /** 状态：1启用 0停售 */
    private Integer status;
}
