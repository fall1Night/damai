package com.damai.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体 —— 对应 DDL {@code t_order}（分片表，分片键 user_id）。
 *
 * <p>分片策略：user_id % 32 路由到 4 库 × 8 表。
 * 雪花算法生成的 order_id 低位嵌入 user_id 分片信息，保证同 user_id 的订单落在同一分片。
 *
 * <p>状态码与 {@link com.damai.common.enums.OrderStatusEnum} 对齐：
 * 1待支付 2已支付 3已取消(手动) 4已取消(超时) 5已退款
 *
 * @author damai
 */
@Data
@TableName("t_order")
public class Order implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 订单ID（雪花算法，低位嵌入 user_id）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID（分片键）
     */
    private Long userId;

    /**
     * 节目ID
     */
    private Long programId;

    /**
     * 场次ID
     */
    private Long showId;

    /**
     * 票档ID
     */
    private Long ticketTypeId;

    /**
     * 购买数量
     */
    private Integer quantity;

    /**
     * 订单总金额（元）
     */
    private BigDecimal totalAmount;

    /**
     * 状态：1待支付 2已支付 3已取消(手动) 4已取消(超时) 5已退款
     */
    private Integer status;

    /**
     * 观演人信息快照（JSON，下单时锁定）
     */
    private String viewerInfo;

    /**
     * 支付截止时间（下单时间 + 15分钟）
     */
    private LocalDateTime payDeadline;

    /**
     * 实际支付时间
     */
    private LocalDateTime payTime;

    /**
     * 取消原因
     */
    private String cancelReason;

    /**
     * 取消时间
     */
    private LocalDateTime cancelTime;

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
