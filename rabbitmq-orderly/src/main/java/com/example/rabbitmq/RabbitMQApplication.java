package com.example.rabbitmq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * RabbitMQ模块启动类
 */
@SpringBootApplication
 @ComponentScan(basePackages = { "com.example.rabbitmq.common", "com.example.rabbitmq", "com.example.common"})
public class RabbitMQApplication {

    public static void main(String[] args) {
        SpringApplication.run(RabbitMQApplication.class, args);
    }
}
