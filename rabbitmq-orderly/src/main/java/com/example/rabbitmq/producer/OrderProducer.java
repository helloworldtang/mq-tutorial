package com.example.rabbitmq.producer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.example.common.entity.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ订单生产者（支持顺序消息）
 */
@Component
public class OrderProducer {

    private static final Logger logger = LoggerFactory.getLogger(OrderProducer.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送订单消息（使用单消费者队列保证顺序）
     */
    public void sendOrderMessage(int orderId, String eventType, long version) {
        try {
            // 1. 创建消息体
            JSONObject orderJson = new JSONObject();
            orderJson.put("orderId", orderId);
            orderJson.put("eventType", eventType);
            orderJson.put("version", version);

            // 2. 使用订单ID作为routing key，保证同一订单消息进入同一个队列
            String routingKey = "order." + orderId;
            rabbitTemplate.convertAndSend("OrderExchange", routingKey, orderJson.toJSONString());

            logger.info("RabbitMQ订单消息发送成功: orderId={}, eventType={}, routingKey={}",
                     orderId, eventType, routingKey);

        } catch (Exception e) {
            logger.error("RabbitMQ订单消息发送失败: orderId={}, eventType={}",
                         orderId, eventType, e);
            throw new RuntimeException("订单消息发送失败", e);
        }
    }
}
