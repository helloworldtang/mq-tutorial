package com.example.rabbitmq.config;

import com.example.rabbitmq.common.constant.MQConstants;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ配置类（基础方案 + DLQ + Publisher Confirm）
 */
@Configuration
@MapperScan("com.example.common.mapper")
public class RabbitMQConfig {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQConfig.class);

    @Value("${spring.rabbitmq.host:localhost}")
    private String rabbitmqHost;

    @Value("${spring.rabbitmq.port:5672}")
    private int rabbitmqPort;

    @Value("${spring.rabbitmq.username:guest}")
    private String rabbitmqUsername;

    @Value("${spring.rabbitmq.password:guest}")
    private String rabbitmqPassword;

    @Value("${rabbitmq.exchange:order-exchange}")
    private String exchangeName;

    private static final String QUEUE_PREFIX = "OrderQueue";

    @Bean
    public CachingConnectionFactory connectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost(rabbitmqHost);
        factory.setPort(rabbitmqPort);
        factory.setUsername(rabbitmqUsername);
        factory.setPassword(rabbitmqPassword);
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        logger.info("RabbitMQ连接配置: host={}, port={}, publisherConfirm=ENABLED", rabbitmqHost, rabbitmqPort);
        return factory;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(CachingConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);

        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                logger.info("消息确认成功: correlationData={}", correlationData);
            } else {
                logger.error("消息确认失败: correlationData={}, cause={}", correlationData, cause);
            }
        });

        template.setReturnsCallback(returned -> {
            logger.error("消息无法路由: exchange={}, routingKey={}, replyText={}, message={}",
                    returned.getExchange(), returned.getRoutingKey(),
                    returned.getReplyText(), new String(returned.getMessage().getBody()));
        });

        return template;
    }

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public DirectExchange orderDlxExchange() {
        return new DirectExchange(exchangeName + MQConstants.DLQ_EXCHANGE_SUFFIX, true, false);
    }

    @Bean
    public Queue orderQueue0() { return createOrderQueue(0); }

    @Bean
    public Queue orderQueue1() { return createOrderQueue(1); }

    @Bean
    public Queue orderQueue2() { return createOrderQueue(2); }

    @Bean
    public Queue orderDlq0() { return new Queue(QUEUE_PREFIX + "0" + MQConstants.DLQ_SUFFIX, true); }

    @Bean
    public Queue orderDlq1() { return new Queue(QUEUE_PREFIX + "1" + MQConstants.DLQ_SUFFIX, true); }

    @Bean
    public Queue orderDlq2() { return new Queue(QUEUE_PREFIX + "2" + MQConstants.DLQ_SUFFIX, true); }

    @Bean
    public Binding bindingOrderQueue0() { return BindingBuilder.bind(orderQueue0()).to(orderExchange()).with("order.0"); }

    @Bean
    public Binding bindingOrderQueue1() { return BindingBuilder.bind(orderQueue1()).to(orderExchange()).with("order.1"); }

    @Bean
    public Binding bindingOrderQueue2() { return BindingBuilder.bind(orderQueue2()).to(orderExchange()).with("order.2"); }

    @Bean
    public Binding bindingOrderDlq0() { return BindingBuilder.bind(orderDlq0()).to(orderDlxExchange()).with(MQConstants.DLQ_ROUTING_KEY_PREFIX + "0"); }

    @Bean
    public Binding bindingOrderDlq1() { return BindingBuilder.bind(orderDlq1()).to(orderDlxExchange()).with(MQConstants.DLQ_ROUTING_KEY_PREFIX + "1"); }

    @Bean
    public Binding bindingOrderDlq2() { return BindingBuilder.bind(orderDlq2()).to(orderDlxExchange()).with(MQConstants.DLQ_ROUTING_KEY_PREFIX + "2"); }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new org.springframework.amqp.support.converter.Jackson2JsonMessageConverter();
    }

    private Queue createOrderQueue(int index) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", exchangeName + MQConstants.DLQ_EXCHANGE_SUFFIX);
        args.put("x-dead-letter-routing-key", MQConstants.DLQ_ROUTING_KEY_PREFIX + index);
        return new Queue(QUEUE_PREFIX + index, true, false, false, args);
    }
}
