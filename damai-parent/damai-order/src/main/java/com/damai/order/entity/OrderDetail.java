package com.damai.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细实体 —— 对应 DDL {@code t_order_detail}（分片表，与 t_order 同分片组）。
 *
 * <p>明细快照了节目/场次/票档/单价信息，即使源数据变更也不影响已下单的记录。
 * 体现了订单"不可变快照"的设计原则。
 *
 * @author damai
 */
@Data
@TableName("t_order_detail")
public class OrderDetail implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 明细ID（雪花算法）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 用户ID（冗余分片键，保证同分片路由）
     */
    private Long userId;

    /**
     * 节目ID
     */
    private Long programId;

    /**
     * 节目名称（快照）
     */
    private String programTitle;

    /**
     * 场次ID
     */
    private Long showId;

    /**
     * 演出时间（快照）
     */
    private LocalDateTime showTime;

    /**
     * 票档ID
     */
    private Long ticketTypeId;

    /**
     * 票档名称（快照）
     */
    private String ticketTypeName;

    /**
     * 场馆名称（快照）
     */
    private String venue;

    /**
     * 单价（快照）
     */
    private BigDecimal unitPrice;

    /**
     * 数量
     */
    private Integer quantity;

    /**
     * 小计金额
     */
    private BigDecimal subAmount;

    /**
     * 观演人姓名
     */
    private String viewerName;

    /**
     * 观演人证件号（加密）
     */
    private String viewerIdCard;

    /**
     * 逻辑删除：0-未删除，1-已删除
     */
    @TableLogic
    private Integer deleted;

    /**
     * 创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
