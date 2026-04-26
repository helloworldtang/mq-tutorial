package com.example.rabbitmqsac.config;

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
public class RabbitMQSacConfig {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQSacConfig.class);

    @Value("${spring.rabbitmq.host:localhost}")
    private String rabbitmqHost;

    @Value("${spring.rabbitmq.port:5672}")
    private int rabbitmqPort;

    @Value("${spring.rabbitmq.username:guest}")
    private String rabbitmqUsername;

    @Value("${spring.rabbitmq.password:guest}")
    private String rabbitmqPassword;

    @Value("${rabbitmq.exchange:sac-order-exchange}")
    private String exchangeName;

    private static final String QUEUE_PREFIX = "SacOrderQueue";

    @Bean
    public CachingConnectionFactory sacConnectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost(rabbitmqHost);
        factory.setPort(rabbitmqPort);
        factory.setUsername(rabbitmqUsername);
        factory.setPassword(rabbitmqPassword);
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        logger.info("SAC RabbitMQ连接配置: host={}, port={}", rabbitmqHost, rabbitmqPort);
        return factory;
    }

    @Bean
    public RabbitTemplate sacRabbitTemplate(CachingConnectionFactory sacConnectionFactory) {
        RabbitTemplate template = new RabbitTemplate(sacConnectionFactory);
        template.setMandatory(true);

        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                logger.info("SAC消息确认成功: correlationData={}", correlationData);
            } else {
                logger.error("SAC消息确认失败: correlationData={}, cause={}", correlationData, cause);
            }
        });

        template.setReturnsCallback(returned -> {
            logger.error("SAC消息无法路由: exchange={}, routingKey={}, replyText={}",
                    returned.getExchange(), returned.getRoutingKey(), returned.getReplyText());
        });

        return template;
    }

    @Bean
    public DirectExchange orderExchange() { return new DirectExchange(exchangeName, true, false); }

    @Bean
    public DirectExchange orderDlxExchange() { return new DirectExchange(exchangeName + MQConstants.DLQ_EXCHANGE_SUFFIX, true, false); }

    @Bean
    public Queue sacOrderQueue0() { return createSacQueue(0); }

    @Bean
    public Queue sacOrderQueue1() { return createSacQueue(1); }

    @Bean
    public Queue sacOrderQueue2() { return createSacQueue(2); }

    @Bean
    public Queue sacOrderDlq0() { return new Queue(QUEUE_PREFIX + "0" + MQConstants.DLQ_SUFFIX, true); }

    @Bean
    public Queue sacOrderDlq1() { return new Queue(QUEUE_PREFIX + "1" + MQConstants.DLQ_SUFFIX, true); }

    @Bean
    public Queue sacOrderDlq2() { return new Queue(QUEUE_PREFIX + "2" + MQConstants.DLQ_SUFFIX, true); }

    @Bean
    public Binding bindingSacOrderQueue0() { return BindingBuilder.bind(sacOrderQueue0()).to(orderExchange()).with("order.0"); }

    @Bean
    public Binding bindingSacOrderQueue1() { return BindingBuilder.bind(sacOrderQueue1()).to(orderExchange()).with("order.1"); }

    @Bean
    public Binding bindingSacOrderQueue2() { return BindingBuilder.bind(sacOrderQueue2()).to(orderExchange()).with("order.2"); }

    @Bean
    public Binding bindingSacOrderDlq0() { return BindingBuilder.bind(sacOrderDlq0()).to(orderDlxExchange()).with(MQConstants.DLQ_ROUTING_KEY_PREFIX + "0"); }

    @Bean
    public Binding bindingSacOrderDlq1() { return BindingBuilder.bind(sacOrderDlq1()).to(orderDlxExchange()).with(MQConstants.DLQ_ROUTING_KEY_PREFIX + "1"); }

    @Bean
    public Binding bindingSacOrderDlq2() { return BindingBuilder.bind(sacOrderDlq2()).to(orderDlxExchange()).with(MQConstants.DLQ_ROUTING_KEY_PREFIX + "2"); }

    @Bean
    public MessageConverter jsonMessageConverter() { return new org.springframework.amqp.support.converter.Jackson2JsonMessageConverter(); }

    private Queue createSacQueue(int index) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-single-active-consumer", true);
        args.put("x-dead-letter-exchange", exchangeName + MQConstants.DLQ_EXCHANGE_SUFFIX);
        args.put("x-dead-letter-routing-key", MQConstants.DLQ_ROUTING_KEY_PREFIX + index);
        return new Queue(QUEUE_PREFIX + index, true, false, false, args);
    }
}
