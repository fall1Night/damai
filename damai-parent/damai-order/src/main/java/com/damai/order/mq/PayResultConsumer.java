package com.damai.order.mq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.damai.common.constants.KafkaTopicConstant;
import com.damai.common.constants.RedisKeyConstant;
import com.damai.common.enums.OrderStatusEnum;
import com.damai.order.entity.Order;
import com.damai.order.manager.OrderManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 支付结果消费者 —— 监听 pay 服务的支付结果通知。
 *
 * <p>消费 {@link KafkaTopicConstant#PAY_RESULT} Topic，
 * 更新订单状态为"已支付"并触发出票（模拟）。
 *
 * <p>核心逻辑：
 * <ol>
 *   <li>消费幂等校验（Redis 去重）</li>
 *   <li>校验订单状态机（不可逆：已支付不可回退）</li>
 *   <li>更新订单为"已支付"</li>
 *   <li>触发出票（模拟）</li>
 * </ol>
 *
 * @author damai
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayResultConsumer {

    private final OrderManager orderManager;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 消费幂等 Key 前缀
     */
    private static final String PAY_RESULT_IDEMPOTENT_PREFIX = "damai:idempotent:pay:result:";

    /**
     * 消费幂等 TTL（秒）- 24 小时
     */
    private static final long PAY_RESULT_IDEMPOTENT_TTL = 24 * 3600;

    /**
     * 监听支付结果 Topic
     *
     * @param record       消息记录
     * @param acknowledgment ACK 确认
     */
    @KafkaListener(topics = KafkaTopicConstant.PAY_RESULT, groupId = "damai-order-pay")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        String messageBody = record.value();
        log.info("收到支付结果消息：{}", messageBody);

        try {
            JSONObject json = JSON.parseObject(messageBody);
            Long orderId = json.getLong("orderId");
            Boolean paySuccess = json.getBoolean("paySuccess");

            // 1. 消费幂等校验
            String idempotentKey = PAY_RESULT_IDEMPOTENT_PREFIX + orderId;
            Boolean isNew = stringRedisTemplate.opsForValue().setIfAbsent(idempotentKey, "1",
                    PAY_RESULT_IDEMPOTENT_TTL, TimeUnit.SECONDS);
            if (Boolean.FALSE.equals(isNew)) {
                log.info("支付结果消息已消费，跳过：orderId={}", orderId);
                acknowledgment.acknowledge();
                return;
            }

            // 2. 查询订单
            Order order = orderManager.getById(orderId);
            if (order == null) {
                log.warn("订单不存在，丢弃消息：orderId={}", orderId);
                acknowledgment.acknowledge();
                return;
            }

            // 3. 校验状态机（不可逆：只有待支付才能变为已支付）
            if (!OrderStatusEnum.WAIT_PAY.getCode().equals(order.getStatus())) {
                log.info("订单非待支付状态，丢弃消息：orderId={}, status={}", orderId, order.getStatus());
                acknowledgment.acknowledge();
                return;
            }

            // 4. 支付成功 → 更新订单为"已支付"
            if (Boolean.TRUE.equals(paySuccess)) {
                order.setStatus(OrderStatusEnum.PAID.getCode());
                order.setPayTime(LocalDateTime.now());
                orderManager.update(order);

                // 5. 触发出票（模拟）
                triggerIssueTicket(order);

                log.info("订单支付成功，已更新状态：orderId={}", orderId);
            } else {
                log.info("支付失败，订单保持待支付状态：orderId={}", orderId);
            }

            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("支付结果处理异常：{}", messageBody, e);
            // 失败不 ACK，等待重试
        }
    }

    /**
     * 触发出票（模拟）
     * <p>实际项目中会调用出票服务或发送出票消息。
     */
    private void triggerIssueTicket(Order order) {
        log.info("触发出票（模拟）：orderId={}, userId={}, quantity={}",
                order.getId(), order.getUserId(), order.getQuantity());
        // TODO: 实际项目中调用出票服务
    }
}
