package com.example;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * 启动类
 */
@SpringBootApplication
@MapperScan("com.example.mapper")
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }

    /**
     * RocketMQ生产者
     */
    @Bean
    public DefaultMQProducer defaultMQProducer(RocketMQTemplate rocketMQTemplate) {
        return rocketMQTemplate.getProducer();
    }
}
