package com.example.rabbitmqexclusive.producer;

import com.alibaba.fastjson2.JSON;
import com.example.rabbitmq.common.constant.MQConstants;
import com.example.rabbitmq.common.message.OrderMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OrderProducer {

    private static final Logger logger = LoggerFactory.getLogger(OrderProducer.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange:exclusive-order-exchange}")
    private String exchange;

    public void sendOrderMessage(int orderId, String eventType, long version) {
        try {
            OrderMessage orderMsg = new OrderMessage(orderId, eventType, version);
            String json = JSON.toJSONString(orderMsg);

            org.springframework.amqp.core.Message message = MessageBuilder
                    .withBody(json.getBytes())
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setMessageId(orderId + "-" + version)
                    .setHeader("orderId", orderId)
                    .setHeader("eventType", eventType)
                    .setHeader("version", version)
                    .build();

            String routingKey = "order." + (orderId % MQConstants.DEFAULT_QUEUE_COUNT);
            rabbitTemplate.convertAndSend(exchange, routingKey, message);

            logger.info("Exclusive订单消息发送成功: orderId={}, eventType={}, routingKey={}, version={}",
                    orderId, eventType, routingKey, version);

        } catch (Exception e) {
            logger.error("Exclusive订单消息发送失败: orderId={}, eventType={}", orderId, eventType, e);
            throw new RuntimeException("订单消息发送失败", e);
        }
    }

    public void sendOrderMessages(int orderId, String... events) {
        for (int i = 0; i < events.length; i++) {
            sendOrderMessage(orderId, events[i], i + 1);
        }
    }
}
