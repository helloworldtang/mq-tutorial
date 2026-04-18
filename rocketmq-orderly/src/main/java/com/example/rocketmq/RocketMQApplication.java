package com.example.rocketmq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * RocketMQ模块启动类
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.example.rocketmq", "com.example.common"})
public class RocketMQApplication {

    public static void main(String[] args) {
        SpringApplication.run(RocketMQApplication.class, args);
    }
}
