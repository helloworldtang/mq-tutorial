package com.example.rabbitmq.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置类（简化版）
 */
@Configuration
public class RabbitMQConfig {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQConfig.class);

    @Value("${rabbitmq.host:localhost}")
    private String rabbitmqHost;

    @Value("${rabbitmq.port:5672}")
    private int rabbitmqPort;

    @Value("${rabbitmq.username:guest}")
    private String rabbitmqUsername;

    @Value("${rabbitmq.password:guest}")
    private String rabbitmqPassword;

    /**
     * 配置连接工厂
     */
    @Bean
    public CachingConnectionFactory connectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost(rabbitmqHost);
        factory.setPort(rabbitmqPort);
        factory.setUsername(rabbitmqUsername);
        factory.setPassword(rabbitmqPassword);
        logger.info("RabbitMQ连接配置: host={}, port={}",
                 rabbitmqHost, rabbitmqPort);
        return factory;
    }

    /**
     * 配置RabbitMQ模板
     */
    @Bean
    public RabbitTemplate rabbitTemplate(CachingConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        return template;
    }

    /**
     * 配置队列（使用单消费者模式）
     */
    @Bean
    public Queue orderQueue() {
        return new Queue("OrderQueue", false);
    }
}
