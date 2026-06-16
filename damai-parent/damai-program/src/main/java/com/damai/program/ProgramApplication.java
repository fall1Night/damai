package com.damai.program;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 演出节目服务启动类
 *
 * @author damai
 */
@SpringBootApplication(scanBasePackages = "com.damai")
@MapperScan("com.damai.program.dao")
@EnableFeignClients(basePackages = "com.damai.api.client")
@EnableScheduling
public class ProgramApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProgramApplication.class, args);
    }
}
