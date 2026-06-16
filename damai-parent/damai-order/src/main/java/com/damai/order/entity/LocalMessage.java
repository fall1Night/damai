package com.damai.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 本地消息表实体 —— 对应 DDL {@code t_local_message}。
 *
 * <p>用于事务消息保证最终一致性：与业务表同事务写入，异步投递 Kafka，
 * 投递成功后更新状态，失败则退避重试。
 *
 * <p>状态机：0待发送 → 1已发送 → 2已确认 / 3失败
 *
 * @author damai
 */
@Data
@TableName("t_local_message")
public class LocalMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 消息业务唯一ID（幂等键，UUID）
     */
    private String messageId;

    /**
     * 目标 Kafka Topic
     */
    private String topic;

    /**
     * 业务键（如 orderId，便于查询）
     */
    private String bizKey;

    /**
     * 消息体（JSON）
     */
    private String messageBody;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 状态：0待发送 1已发送 2已确认 3失败
     */
    private Integer status;

    /**
     * 下次重试时间（退避策略）
     */
    private LocalDateTime nextRetryTime;

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
