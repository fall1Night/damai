package com.damai.pay;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 支付服务启动类。
 *
 * <p>对应 PRD §7.4 支付服务，承担：发起模拟支付、支付回调幂等（Redis 防重 + 状态机双重）、
 * Kafka 异步通知订单已付、定时对账补偿、模拟退款。
 *
 * <p>与 {@code damai-order} 同分片组（分片键 user_id，4 库 × 8 表），
 * 共用 {@code damai_order} 数据库。
 *
 * @author damai
 */
@SpringBootApplication(scanBasePackages = "com.damai")
@MapperScan("com.damai.pay.dao")
@EnableFeignClients(basePackages = "com.damai.api.client")
@EnableKafka
@EnableScheduling
public class PayApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayApplication.class, args);
    }
}
