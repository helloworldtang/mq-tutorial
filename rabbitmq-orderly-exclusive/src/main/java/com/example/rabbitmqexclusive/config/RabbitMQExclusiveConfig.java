package com.example.rabbitmqexclusive.config;

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

@Configuration
@MapperScan("com.example.common.mapper")
public class RabbitMQExclusiveConfig {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQExclusiveConfig.class);

    @Value("${spring.rabbitmq.host:localhost}")
    private String rabbitmqHost;

    @Value("${spring.rabbitmq.port:5672}")
    private int rabbitmqPort;

    @Value("${spring.rabbitmq.username:guest}")
    private String rabbitmqUsername;

    @Value("${spring.rabbitmq.password:guest}")
    private String rabbitmqPassword;

    @Value("${rabbitmq.exchange:exclusive-order-exchange}")
    private String exchangeName;

    private static final String QUEUE_PREFIX = "ExclusiveOrderQueue";

    @Bean
    public CachingConnectionFactory exclusiveConnectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost(rabbitmqHost);
        factory.setPort(rabbitmqPort);
        factory.setUsername(rabbitmqUsername);
        factory.setPassword(rabbitmqPassword);
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        logger.info("Exclusive RabbitMQ连接配置: host={}, port={}", rabbitmqHost, rabbitmqPort);
        return factory;
    }

    @Bean
    public RabbitTemplate exclusiveRabbitTemplate(CachingConnectionFactory exclusiveConnectionFactory) {
        RabbitTemplate template = new RabbitTemplate(exclusiveConnectionFactory);
        template.setMandatory(true);

        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                logger.info("Exclusive消息确认成功: correlationData={}", correlationData);
            } else {
                logger.error("Exclusive消息确认失败: correlationData={}, cause={}", correlationData, cause);
            }
        });

        template.setReturnsCallback(returned -> {
            logger.error("Exclusive消息无法路由: exchange={}, routingKey={}, replyText={}",
                    returned.getExchange(), returned.getRoutingKey(), returned.getReplyText());
        });

        return template;
    }

    @Bean
    public DirectExchange orderExchange() { return new DirectExchange(exchangeName, true, false); }

    @Bean
    public DirectExchange orderDlxExchange() { return new DirectExchange(exchangeName + MQConstants.DLQ_EXCHANGE_SUFFIX, true, false); }

    @Bean
    public Queue exclusiveOrderQueue0() { return createExclusiveQueue(0); }

    @Bean
    public Queue exclusiveOrderQueue1() { return createExclusiveQueue(1); }

    @Bean
    public Queue exclusiveOrderQueue2() { return createExclusiveQueue(2); }

    @Bean
    public Queue exclusiveOrderDlq0() { return new Queue(QUEUE_PREFIX + "0" + MQConstants.DLQ_SUFFIX, true); }

    @Bean
    public Queue exclusiveOrderDlq1() { return new Queue(QUEUE_PREFIX + "1" + MQConstants.DLQ_SUFFIX, true); }

    @Bean
    public Queue exclusiveOrderDlq2() { return new Queue(QUEUE_PREFIX + "2" + MQConstants.DLQ_SUFFIX, true); }

    @Bean
    public Binding bindingExclusiveOrderQueue0() { return BindingBuilder.bind(exclusiveOrderQueue0()).to(orderExchange()).with("order.0"); }

    @Bean
    public Binding bindingExclusiveOrderQueue1() { return BindingBuilder.bind(exclusiveOrderQueue1()).to(orderExchange()).with("order.1"); }

    @Bean
    public Binding bindingExclusiveOrderQueue2() { return BindingBuilder.bind(exclusiveOrderQueue2()).to(orderExchange()).with("order.2"); }

    @Bean
    public Binding bindingExclusiveOrderDlq0() { return BindingBuilder.bind(exclusiveOrderDlq0()).to(orderDlxExchange()).with(MQConstants.DLQ_ROUTING_KEY_PREFIX + "0"); }

    @Bean
    public Binding bindingExclusiveOrderDlq1() { return BindingBuilder.bind(exclusiveOrderDlq1()).to(orderDlxExchange()).with(MQConstants.DLQ_ROUTING_KEY_PREFIX + "1"); }

    @Bean
    public Binding bindingExclusiveOrderDlq2() { return BindingBuilder.bind(exclusiveOrderDlq2()).to(orderDlxExchange()).with(MQConstants.DLQ_ROUTING_KEY_PREFIX + "2"); }

    @Bean
    public MessageConverter jsonMessageConverter() { return new org.springframework.amqp.support.converter.Jackson2JsonMessageConverter(); }

    private Queue createExclusiveQueue(int index) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", exchangeName + MQConstants.DLQ_EXCHANGE_SUFFIX);
        args.put("x-dead-letter-routing-key", MQConstants.DLQ_ROUTING_KEY_PREFIX + index);
        return new Queue(QUEUE_PREFIX + index, true, false, false, args);
    }
}
