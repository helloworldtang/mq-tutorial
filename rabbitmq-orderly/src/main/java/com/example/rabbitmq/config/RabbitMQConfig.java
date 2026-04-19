package com.example.rabbitmq.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置类（支持局部顺序消息）
 *
 * 核心机制：
 * 1. 使用Direct Exchange + 多队列实现局部顺序
 * 2. 每个队列绑定不同的路由键
 * 3. 使用订单ID作为路由键，保证同一订单进入同一队列
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

    @Value("${rabbitmq.exchange:order-exchange}")
    private String exchangeName;

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
        // 开启消息返回确认
        template.setMandatory(true);
        return template;
    }

    /**
     * 配置交换机（Direct Exchange）
     *
     * Direct Exchange：根据路由键精确匹配队列
     */
    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(exchangeName, true, false);
    }

    /**
     * 配置订单队列（3个队列）
     *
     * 同一订单的消息会进入同一个队列（通过路由键：order.{orderId % 3}）
     * 不同订单的消息可以进入不同队列，实现并行消费
     */
    @Bean
    public Queue orderQueue0() {
        return new Queue("OrderQueue0", false);
    }

    @Bean
    public Queue orderQueue1() {
        return new Queue("OrderQueue1", false);
    }

    @Bean
    public Queue orderQueue2() {
        return new Queue("OrderQueue2", false);
    }

    /**
     * 绑定队列到交换机
     *
     * 队列0绑定路由键：order.0
     * 队列1绑定路由键：order.1
     * 队列2绑定路由键：order.2
     */
    @Bean
    public Binding bindingOrderQueue0() {
        return BindingBuilder.bind(orderQueue0())
            .to(orderExchange())
            .with("order.0");
    }

    @Bean
    public Binding bindingOrderQueue1() {
        return BindingBuilder.bind(orderQueue1())
            .to(orderExchange())
            .with("order.1");
    }

    @Bean
    public Binding bindingOrderQueue2() {
        return BindingBuilder.bind(orderQueue2())
            .to(orderExchange())
            .with("order.2");
    }

    /**
     * 配置消息转换器（JSON）
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new org.springframework.amqp.support.converter.Jackson2JsonMessageConverter();
    }
}
