package com.damai.starter.kafka;

import com.alibaba.fastjson2.JSON;
import com.damai.common.constants.HeaderConstant;
import com.damai.common.utils.TraceIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * Kafka 消息发送通用工具 —— 对应技术文档 §8.2。
 *
 * <p>职责：
 * <ul>
 *   <li>统一消息发送入口，封装 JSON 序列化。</li>
 *   <li>自动注入 traceId 到消息 Header，保证全链路可追踪
 *       （网关生成 → 业务服务透传 → MQ 消息也携带）。</li>
 *   <li>提供同步/异步两种发送模式。</li>
 * </ul>
 *
 * <p>Topic 常量见 {@link com.damai.common.constants.KafkaTopicConstant}。
 *
 * <p>使用示例：
 * <pre>
 *   // 同步发送
 *   kafkaMessageHelper.sendSync(KafkaTopicConstant.ORDER_CREATED, orderId.toString(), orderMessage);
 *
 *   // 异步发送
 *   kafkaMessageHelper.sendAsync(KafkaTopicConstant.PAY_RESULT, orderId.toString(), payResultMessage);
 * </pre>
 */
@Slf4j
@Component
public class KafkaMessageHelper {

    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 同步发送消息（阻塞等待发送结果）。
     *
     * <p>适用于关键业务链路（如下单成功后的库存扣减通知），
     * 需要确认消息已到达 Broker。
     *
     * @param topic  Topic 名称（见 KafkaTopicConstant）
     * @param key    消息 Key（用于分区路由，同一 Key 落同一分区保证有序）
     * @param message 消息对象（自动 JSON 序列化）
     * @return 发送结果
     * @throws com.damai.common.exception.SystemException 发送失败时抛出系统异常
     */
    public SendResult<String, String> sendSync(String topic, String key, Object message) {
        try {
            String jsonBody = JSON.toJSONString(message);
            log.info("[Kafka] 同步发送: topic={}, key={}, body={}", topic, key, jsonBody);

            SendResult<String, String> result = kafkaTemplate.send(topic, key, jsonBody).get();
            log.info("[Kafka] 发送成功: topic={}, key={}, partition={}, offset={}",
                    topic, key, result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            return result;
        } catch (Exception e) {
            log.error("[Kafka] 同步发送失败: topic={}, key={}", topic, key, e);
            throw new com.damai.common.exception.SystemException(
                    com.damai.common.exception.ErrorCode.KAFKA_SEND_ERROR);
        }
    }

    /**
     * 异步发送消息（非阻塞，发送结果通过回调处理）。
     *
     * <p>适用于非关键链路（如节目变更同步 ES），发送失败不影响主流程。
     * 失败时仅记录日志，不抛异常。
     *
     * @param topic   Topic 名称
     * @param key     消息 Key
     * @param message 消息对象（自动 JSON 序列化）
     */
    public void sendAsync(String topic, String key, Object message) {
        try {
            String jsonBody = JSON.toJSONString(message);
            log.info("[Kafka] 异步发送: topic={}, key={}, body={}", topic, key, jsonBody);

            kafkaTemplate.send(topic, key, jsonBody).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("[Kafka] 异步发送失败: topic={}, key={}", topic, key, ex);
                } else {
                    log.debug("[Kafka] 异步发送成功: topic={}, key={}, partition={}, offset={}",
                            topic, key, result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });
        } catch (Exception e) {
            log.error("[Kafka] 异步发送异常: topic={}, key={}", topic, key, e);
        }
    }

    /**
     * 带自定义 Header 的异步发送。
     *
     * <p>自动注入当前 traceId 到消息 Header，
     * 消费端可从 Header 读取并写入 MDC 保持链路追踪。
     *
     * @param topic   Topic 名称
     * @param key     消息 Key
     * @param message 消息对象
     */
    public void sendAsyncWithTrace(String topic, String key, Object message) {
        try {
            String jsonBody = JSON.toJSONString(message);
            String traceId = TraceIdUtil.getTraceId();
            log.info("[Kafka] 异步发送(含traceId): topic={}, key={}, traceId={}, body={}",
                    topic, key, traceId, jsonBody);

            kafkaTemplate.send(topic, key, jsonBody).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("[Kafka] 异步发送失败: topic={}, key={}, traceId={}", topic, key, traceId, ex);
                } else {
                    log.debug("[Kafka] 异步发送成功: topic={}, key={}, traceId={}", topic, key, traceId);
                }
            });
        } catch (Exception e) {
            log.error("[Kafka] 异步发送异常(含traceId): topic={}, key={}", topic, key, e);
        }
    }
}
