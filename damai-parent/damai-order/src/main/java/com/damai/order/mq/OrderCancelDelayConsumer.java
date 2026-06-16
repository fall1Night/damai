package com.damai.order.mq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.damai.api.client.program.ProgramFeignClient;
import com.damai.api.dto.program.StockDeductRequest;
import com.damai.common.constants.KafkaTopicConstant;
import com.damai.common.constants.RedisKeyConstant;
import com.damai.common.enums.OrderStatusEnum;
import com.damai.order.entity.Order;
import com.damai.order.manager.OrderManager;
import com.damai.starter.kafka.KafkaMessageHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 超时取消消费者 —— 对应 PRD §8.3 延迟取消机制。
 *
 * <p>消费 {@link KafkaTopicConstant#ORDER_CANCEL_DELAY} Topic，
 * 处理 15 分钟超时未支付的订单。
 *
 * <p>核心逻辑：
 * <ol>
 *   <li>二次校验订单状态（延迟窗口内订单可能已支付）</li>
 *   <li>已支付 → 丢弃消息</li>
 *   <li>待支付 → 更新订单为"已取消(超时)"</li>
 *   <li>Feign 归还 Redis 库存</li>
 *   <li>Kafka 异步归还 DB 库存</li>
 * </ol>
 *
 * <p>消费幂等：Redis 去重（Key: {@code damai:idempotent:cancel:{orderId}}）。
 *
 * @author damai
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelDelayConsumer {

    private final OrderManager orderManager;
    private final ProgramFeignClient programFeignClient;
    private final KafkaMessageHelper kafkaMessageHelper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 消费幂等 Key 前缀
     */
    private static final String CANCEL_IDEMPOTENT_PREFIX = "damai:idempotent:cancel:";

    /**
     * 消费幂等 TTL（秒）- 1 小时
     */
    private static final long CANCEL_IDEMPOTENT_TTL = 3600;

    /**
     * 监听订单超时取消延迟 Topic
     *
     * @param record       消息记录
     * @param acknowledgment ACK 确认
     */
    @KafkaListener(topics = KafkaTopicConstant.ORDER_CANCEL_DELAY, groupId = "damai-order-cancel")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        String messageBody = record.value();
        log.info("收到订单超时取消消息：{}", messageBody);

        try {
            JSONObject json = JSON.parseObject(messageBody);
            Long orderId = json.getLong("orderId");
            Long userId = json.getLong("userId");

            // 1. 消费幂等校验
            String idempotentKey = CANCEL_IDEMPOTENT_PREFIX + orderId;
            Boolean isNew = stringRedisTemplate.opsForValue().setIfAbsent(idempotentKey, "1",
                    CANCEL_IDEMPOTENT_TTL, TimeUnit.SECONDS);
            if (Boolean.FALSE.equals(isNew)) {
                log.info("订单超时取消消息已消费，跳过：orderId={}", orderId);
                acknowledgment.acknowledge();
                return;
            }

            // 2. 二次校验订单状态
            Order order = orderManager.getById(orderId);
            if (order == null) {
                log.warn("订单不存在，丢弃消息：orderId={}", orderId);
                acknowledgment.acknowledge();
                return;
            }

            if (!OrderStatusEnum.WAIT_PAY.getCode().equals(order.getStatus())) {
                log.info("订单非待支付状态，丢弃消息：orderId={}, status={}", orderId, order.getStatus());
                acknowledgment.acknowledge();
                return;
            }

            // 3. 更新订单为"已取消(超时)"
            order.setStatus(OrderStatusEnum.CANCELED_TIMEOUT.getCode());
            order.setCancelReason("支付超时，系统自动取消");
            order.setCancelTime(LocalDateTime.now());
            orderManager.update(order);

            // 4. Feign 归还 Redis 库存
            refundStock(order);

            // 5. Kafka 异步归还 DB 库存
            sendRefundDbStockMessage(order);

            log.info("订单超时取消成功：orderId={}", orderId);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("订单超时取消处理异常：{}", messageBody, e);
            // 失败不 ACK，等待重试
        }
    }

    /**
     * 归还 Redis 库存（Feign 调用 program 服务）
     */
    private void refundStock(Order order) {
        try {
            StockDeductRequest refundRequest = new StockDeductRequest();
            refundRequest.setShowId(order.getShowId());
            refundRequest.setTicketTypeId(order.getTicketTypeId());
            refundRequest.setQuantity(order.getQuantity());
            programFeignClient.refundStock(refundRequest);
        } catch (Exception e) {
            log.error("归还 Redis 库存失败：orderId={}", order.getId(), e);
            // 库存归还失败由对账任务补偿
        }
    }

    /**
     * 发送异步归还 DB 库存消息
     */
    private void sendRefundDbStockMessage(Order order) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("orderId", order.getId());
            message.put("showId", order.getShowId());
            message.put("ticketTypeId", order.getTicketTypeId());
            message.put("quantity", order.getQuantity());
            message.put("action", "REFUND");
            kafkaMessageHelper.sendAsync(KafkaTopicConstant.ORDER_CREATED,
                    order.getId().toString(), message);
        } catch (Exception e) {
            log.error("发送归还 DB 库存消息失败：orderId={}", order.getId(), e);
        }
    }
}
