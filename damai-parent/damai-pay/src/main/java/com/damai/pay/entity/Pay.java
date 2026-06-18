package com.damai.pay.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单实体 —— 对应 DDL {@code t_pay}（分片表，与 {@code t_order} 同分片组）。
 *
 * <p>分片策略：分片键 {@code user_id}（冗余），与 t_order 一同按 user_id % 32 路由到 4 库 × 8 表。
 * {@code order_id} 雪花算法低位嵌入 user_id，保证支付单与订单落在同一分片。
 *
 * <p>状态码与 {@link com.damai.common.enums.PayStatusEnum} 对齐：
 * 1待支付 2成功 3失败 4已关闭 5已退款。
 *
 * <p>幂等键：{@code pay_no} 为业务唯一号，回调以 pay_no 作为 Redis 防重键
 * （{@link com.damai.common.constants.RedisKeyConstant#PAY_CALLBACK_IDEMPOTENT}）。
 *
 * @author damai
 */
@Data
@TableName("t_pay")
public class Pay implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 支付单 ID（雪花算法）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 支付单号（业务唯一，回调幂等键）
     */
    private String payNo;

    /**
     * 订单 ID（分片键，与 t_order 同分片组）
     */
    private Long orderId;

    /**
     * 用户 ID（冗余分片键，保证同分片路由）
     */
    private Long userId;

    /**
     * 支付金额（元）
     */
    private BigDecimal payAmount;

    /**
     * 支付方式：1微信(模拟) 2支付宝(模拟) 3余额(模拟)
     */
    private Integer payMethod;

    /**
     * 状态：1待支付 2成功 3失败 4已关闭 5已退款（PayStatusEnum）
     */
    private Integer status;

    /**
     * 模拟支付链接/参数（JSON）
     */
    private String payUrl;

    /**
     * 回调到达时间
     */
    private LocalDateTime callbackTime;

    /**
     * 支付成功时间
     */
    private LocalDateTime payTime;

    /**
     * 支付单过期时间（= 订单剩余有效时间）
     */
    private LocalDateTime expireTime;

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
