package com.damai.api.dto.order;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细传输对象 —— 对应 DDL {@code t_order_detail}。
 *
 * <p>明细快照了节目/场次/票档/单价信息，即使源数据变更也不影响已下单的记录。
 * 体现了订单"不可变快照"的设计原则。
 */
@Data
public class OrderDetailDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 订单 ID */
    private Long orderId;

    /** 用户 ID */
    private Long userId;

    /** 节目 ID */
    private Long programId;

    /** 节目名称（快照） */
    private String programTitle;

    /** 场次 ID */
    private Long showId;

    /** 演出时间（快照） */
    private LocalDateTime showTime;

    /** 票档 ID */
    private Long ticketTypeId;

    /** 票档名称（快照） */
    private String ticketTypeName;

    /** 场馆名称（快照） */
    private String venue;

    /** 单价（快照） */
    private BigDecimal unitPrice;

    /** 数量 */
    private Integer quantity;

    /** 小计金额 */
    private BigDecimal subAmount;

    /** 观演人姓名 */
    private String viewerName;

    /** 观演人证件号（加密） */
    private String viewerIdCard;

    private LocalDateTime createTime;
}
