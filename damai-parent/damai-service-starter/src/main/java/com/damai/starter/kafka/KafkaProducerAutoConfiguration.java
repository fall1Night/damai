package com.damai.starter.kafka;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka 自动配置。
 *
 * <p>Spring Boot 自带的 {@code spring-boot-starter-kafka} 已包含 Producer/Consumer
 * 自动配置，此处扩展：
 * <ul>
 *   <li>注册通用消息发送工具 {@link KafkaMessageHelper}；</li>
 *   <li>提供通用消费者基类的约定（{@link BaseKafkaConsumer}）。</li>
 * </ul>
 *
 * <p>Kafka Topic 与分区数约定见 {@link com.damai.common.constants.KafkaTopicConstant}。
 * 消费幂等策略见技术文档 §8.2.3。
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
public class KafkaProducerAutoConfiguration {

    // KafkaTemplate 由 spring-kafka 自动配置，此处仅需声明条件装配
    // KafkaMessageHelper 以 @Component 方式注册，见该类
}
