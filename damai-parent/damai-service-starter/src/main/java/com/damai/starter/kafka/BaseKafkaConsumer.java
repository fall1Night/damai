package com.damai.starter.kafka;

import com.alibaba.fastjson2.JSON;
import com.damai.common.exception.ErrorCode;
import com.damai.common.exception.SystemException;
import com.damai.common.utils.TraceIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.Acknowledgment;

/**
 * Kafka 通用消费者基类 —— 对应技术文档 §8.2.3 消费幂等与重试策略。
 *
 * <p>设计原则：
 * <ul>
 *   <li><b>手动 ACK</b>：配合 {@code spring.kafka.listener.ack-mode=MANUAL}，
 *       业务处理成功后再确认，失败不确认触发重试。</li>
 *   <li><b>traceId 透传</b>：从消息 Header 提取 traceId 写入 MDC，保持全链路可追踪。</li>
 *   <li><b>模板方法</b>：子类只需实现 {@link #handleMessage(ConsumerRecord, Acknowledgment)}，
 *       框架负责日志、异常兜底、ACK。</li>
 * </ul>
 *
 * <p>消费幂等（技术文档 §8.2.3）：所有消费者以"业务唯一键 + Redis 去重"保证幂等。
 * 幂等逻辑在子类 handleMessage 中实现（因每个业务的幂等键不同）。
 *
 * <p>使用示例（业务消费者）：
 * <pre>
 *   &#64;Component
 *   public class OrderCreatedConsumer extends BaseKafkaConsumer&lt;OrderCreatedMessage&gt; {
 *
 *       &#64;Resource
 *       private OrderService orderService;
 *
 *       &#64;KafkaListener(topics = KafkaTopicConstant.ORDER_CREATED, groupId = "damai-order-group")
 *       public void onMessage(ConsumerRecord&lt;String, String&gt; record, Acknowledgment ack) {
 *           super.consume(record, ack, OrderCreatedMessage.class);
 *       }
 *
 *       &#64;Override
 *       protected void handleMessage(ConsumerRecord&lt;String, String&gt; record,
 *                                      Acknowledgment ack, OrderCreatedMessage message) {
 *           orderService.asyncDeductStock(message);
 *       }
 *   }
 * </pre>
 *
 * @param <T> 消息体反序列化后的目标类型
 */
@Slf4j
public abstract class BaseKafkaConsumer<T> {

    /**
     * 消费入口模板方法（子类 onMessage 调用此方法）。
     *
     * <p>执行流程：
     * <ol>
     *   <li>提取 traceId 写入 MDC；</li>
     *   <li>反序列化消息体；</li>
     *   <li>调用子类 handleMessage；</li>
     *   <li>子类处理成功 → 手动 ACK；</li>
     *   <li>子类抛异常 → 记录错误日志，不 ACK（等待重试）。</li>
     * </ol>
     *
     * @param record Kafka 消费记录
     * @param ack    手动 ACK 对象
     * @param clazz  消息体目标类型
     */
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack, Class<T> clazz) {
        String topic = record.topic();
        String key = record.key();
        String value = record.value();

        log.info("[KafkaConsumer] 收到消息: topic={}, key={}, partition={}, offset={}",
                topic, key, record.partition(), record.offset());

        try {
            // traceId 透传（从 Header 提取或生成）
            propagateTraceId(record);

            // 反序列化消息体
            T message = JSON.parseObject(value, clazz);
            if (message == null) {
                log.error("[KafkaConsumer] 消息反序列化为 null: topic={}, key={}, value={}", topic, key, value);
                ack.acknowledge();
                return;
            }

            // 子类处理业务逻辑
            handleMessage(record, ack, message);

            // 子类正常返回（未抛异常），确认消费成功
            log.info("[KafkaConsumer] 消费成功: topic={}, key={}", topic, key);
            ack.acknowledge();

        } catch (Exception e) {
            log.error("[KafkaConsumer] 消费失败: topic={}, key={}", topic, key, e);
            // 不 ACK → 触发 Kafka 重试（由 spring.kafka.consumer.properties 配置重试次数/间隔）
            // 超过重试次数后进入死信队列（DLQ），人工介入
        }
    }

    /**
     * 子类实现：核心业务处理逻辑。
     *
     * <p>子类在处理前应自行实现幂等校验（Redis 业务唯一键去重）。
     * 幂等校验通过后再执行业务，成功正常返回（框架自动 ACK），
     * 失败抛异常（框架不 ACK，触发重试）。
     *
     * @param record  Kafka 消费记录
     * @param ack     手动 ACK 对象（子类一般无需直接操作，框架兜底 ACK）
     * @param message 反序列化后的消息体
     * @throws Exception 业务异常（框架会记录日志并触发重试）
     */
    protected abstract void handleMessage(ConsumerRecord<String, String> record,
                                           Acknowledgment ack,
                                           T message) throws Exception;

    /**
     * 从 Kafka 消息 Header 提取 traceId 并写入 MDC，保持链路追踪。
     *
     * <p>Header Key 见 {@link com.damai.common.constants.HeaderConstant#TRACE_ID}。
     */
    private void propagateTraceId(ConsumerRecord<String, String> record) {
        String traceId = TraceIdUtil.getTraceId();
        // 尝试从 Header 读取上游传递的 traceId
        org.apache.kafka.common.header.Headers headers = record.headers();
        if (headers != null) {
            var header = headers.lastHeader(com.damai.common.constants.HeaderConstant.TRACE_ID);
            if (header != null) {
                traceId = new String(header.value());
            }
        }
        TraceIdUtil.setTraceId(traceId);
    }
}
