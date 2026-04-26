package com.example.rabbitmqsac;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
 @ComponentScan(basePackages = { "com.example.rabbitmq.common", "com.example.rabbitmqsac", "com.example.common"})
public class RabbitMQSacApplication {

    public static void main(String[] args) {
        SpringApplication.run(RabbitMQSacApplication.class, args);
    }
}
