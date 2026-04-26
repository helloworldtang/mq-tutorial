package com.example.rabbitmqexclusive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
 @ComponentScan(basePackages = { "com.example.rabbitmq.common", "com.example.rabbitmqexclusive", "com.example.common"})
public class RabbitMQExclusiveApplication {

    public static void main(String[] args) {
        SpringApplication.run(RabbitMQExclusiveApplication.class, args);
    }
}
