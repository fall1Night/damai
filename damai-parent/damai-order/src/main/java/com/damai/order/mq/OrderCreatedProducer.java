package com.damai.order.mq;

import com.damai.common.constants.KafkaTopicConstant;
import com.damai.starter.kafka.KafkaMessageHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 订单创建消息生产者 —— 下单成功后投递消息到 Kafka。
 *
 * <p>消息用途：
 * <ul>
 *   <li>{@link KafkaTopicConstant#ORDER_CREATED}：通知 program 服务异步扣 DB 库存</li>
 *   <li>{@link KafkaTopicConstant#ORDER_CANCEL_DELAY}：延迟 15min 超时取消</li>
 * </ul>
 *
 * <p>注意：本地消息表已保证消息最终投递成功，此处为即时投递尝试。
 * 投递失败不影响主流程，由本地消息表补偿任务兜底。
 *
 * @author damai
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedProducer {

    private final KafkaMessageHelper kafkaMessageHelper;

    /**
     * 发送订单创建消息（异步扣 DB 库存）
     *
     * @param orderId      订单ID
     * @param userId       用户ID
     * @param showId       场次ID
     * @param ticketTypeId 票档ID
     * @param quantity     数量
     */
    public void sendOrderCreatedMessage(Long orderId, Long userId, Long showId,
                                         Long ticketTypeId, Integer quantity) {
        Map<String, Object> message = new HashMap<>();
        message.put("orderId", orderId);
        message.put("userId", userId);
        message.put("showId", showId);
        message.put("ticketTypeId", ticketTypeId);
        message.put("quantity", quantity);

        kafkaMessageHelper.sendAsync(KafkaTopicConstant.ORDER_CREATED,
                orderId.toString(), message);
        log.info("订单创建消息已发送：orderId={}", orderId);
    }

    /**
     * 发送超时取消延迟消息
     *
     * @param orderId     订单ID
     * @param userId      用户ID
     * @param payDeadline 支付截止时间
     */
    public void sendCancelDelayMessage(Long orderId, Long userId, String payDeadline) {
        Map<String, Object> message = new HashMap<>();
        message.put("orderId", orderId);
        message.put("userId", userId);
        message.put("payDeadline", payDeadline);

        kafkaMessageHelper.sendAsync(KafkaTopicConstant.ORDER_CANCEL_DELAY,
                orderId.toString(), message);
        log.info("超时取消延迟消息已发送：orderId={}, payDeadline={}", orderId, payDeadline);
    }
}
