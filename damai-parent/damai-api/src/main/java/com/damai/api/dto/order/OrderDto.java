package com.damai.api.dto.order;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单传输对象 —— 对应 DDL {@code t_order}。
 *
 * <p>用于服务间订单信息查询（pay 服务回调时查订单状态、program 服务归还库存时确认）。
 * 去除了内部字段（is_deleted），保留业务语义字段。
 *
 * <p>状态码与 {@link com.damai.common.enums.OrderStatusEnum} 对齐：
 * 1待支付 2已支付 3已取消(手动) 4已取消(超时) 5已退款
 */
@Data
public class OrderDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单 ID（雪花算法，低位嵌入 user_id） */
    private Long id;

    /** 用户 ID（分片键） */
    private Long userId;

    /** 节目 ID */
    private Long programId;

    /** 场次 ID */
    private Long showId;

    /** 票档 ID */
    private Long ticketTypeId;

    /** 购买数量 */
    private Integer quantity;

    /** 订单总金额（元） */
    private BigDecimal totalAmount;

    /** 状态：1待支付 2已支付 3已取消 4已取消(超时) 5已退款（OrderStatusEnum） */
    private Integer status;

    /** 支付截止时间（下单时间 + 15分钟） */
    private LocalDateTime payDeadline;

    /** 实际支付时间 */
    private LocalDateTime payTime;

    /** 取消原因 */
    private String cancelReason;

    /** 取消时间 */
    private LocalDateTime cancelTime;

    private LocalDateTime createTime;
}
