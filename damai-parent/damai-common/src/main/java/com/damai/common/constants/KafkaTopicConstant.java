package com.damai.common.constants;

/**
 * Kafka Topic 常量 —— 对应技术文档 §8.2.1 Topic 规划。
 *
 * <p>Topic 职责一览：
 * <ul>
 *   <li>{@link #ORDER_CREATED}：订单创建 → 异步扣 DB 库存。</li>
 *   <li>{@link #ORDER_CANCEL_DELAY}：订单超时延迟取消（15 分钟）。</li>
 *   <li>{@link #PAY_RESULT}：支付结果 → 通知订单更新为已支付。</li>
 *   <li>{@link #PROGRAM_SYNC_ES}：节目变更 → 同步 ES 索引。</li>
 * </ul>
 *
 * <p>消费幂等（技术文档 §8.2.3）：所有消费者以业务唯一键 + Redis 去重保证幂等。
 */
public final class KafkaTopicConstant {

    private KafkaTopicConstant() {}

    /** 订单创建 Topic（order → program：异步扣 DB 库存） */
    public static final String ORDER_CREATED = "damai-order-created";

    /** 订单超时取消延迟 Topic（order → order：15 分钟后消费取消） */
    public static final String ORDER_CANCEL_DELAY = "damai-order-cancel-delay";

    /** 支付结果 Topic（pay → order：通知订单已支付） */
    public static final String PAY_RESULT = "damai-pay-result";

    /** 节目变更同步 ES Topic（program → program：ES 消费者更新索引） */
    public static final String PROGRAM_SYNC_ES = "damai-program-sync-es";

    // ==================== 延迟队列级别（技术文档 §8.2.2 多级延迟） ====================

    /** 1 分钟延迟级别 */
    public static final String DELAY_LEVEL_1M = "damai-delay-1m";

    /** 5 分钟延迟级别 */
    public static final String DELAY_LEVEL_5M = "damai-delay-5m";

    /** 15 分钟延迟级别（订单超时取消） */
    public static final String DELAY_LEVEL_15M = "damai-delay-15m";

    /** 30 分钟延迟级别 */
    public static final String DELAY_LEVEL_30M = "damai-delay-30m";

    // ==================== 默认分区数（容量规划参考） ====================

    /** 订单类 Topic 默认分区数 */
    public static final int PARTITION_ORDER = 8;

    /** 通知类 Topic 默认分区数 */
    public static final int PARTITION_NOTIFY = 4;
}
