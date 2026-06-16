package com.damai.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 用户服务启动类
 *
 * @author damai
 */
@SpringBootApplication(scanBasePackages = "com.damai")
@MapperScan("com.damai.user.dao")
@EnableFeignClients(basePackages = "com.damai.api.client")
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
