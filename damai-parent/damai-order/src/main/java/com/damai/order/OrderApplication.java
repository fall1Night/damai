package com.damai.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 订单服务启动类
 *
 * @author damai
 */
@SpringBootApplication(scanBasePackages = "com.damai")
@MapperScan("com.damai.order.dao")
@EnableFeignClients(basePackages = "com.damai.api.client")
@EnableKafka
@EnableScheduling
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
