package com.example.rabbitmqdlock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
 @ComponentScan(basePackages = { "com.example.rabbitmq.common", "com.example.rabbitmqdlock", "com.example.common"})
public class RabbitMQDlockApplication {

    public static void main(String[] args) {
        SpringApplication.run(RabbitMQDlockApplication.class, args);
    }
}
