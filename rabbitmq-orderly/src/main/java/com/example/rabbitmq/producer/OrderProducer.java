package com.example.rabbitmq.producer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.example.common.entity.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ订单生产者（支持顺序消息）
 *
 * 核心机制：
 * 1. 使用订单ID作为路由键，保证同一订单进入同一队列
 * 2. 配合Direct Exchange + 多队列，实现局部顺序
 */
@Component
public class OrderProducer {

    private static final Logger logger = LoggerFactory.getLogger(OrderProducer.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange:order-exchange}")
    private String exchange;

    /**
     * 发送订单消息（使用订单ID作为路由键）
     *
     * @param orderId 订单ID
     * @param eventType 事件类型
     * @param version 版本号
     */
    public void sendOrderMessage(int orderId, String eventType, long version) {
        try {
            // 1. 创建消息体
            JSONObject orderJson = new JSONObject();
            orderJson.put("orderId", orderId);
            orderJson.put("eventType", eventType);
            orderJson.put("version", version);

            // 2. 构建消息
            Message message = MessageBuilder.withBody(orderJson.toJSONString().getBytes())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(String.valueOf(orderId) + "-" + version)
                .setHeader("orderId", orderId)
                .setHeader("eventType", eventType)
                .setHeader("version", version)
                .build();

            // 3. 使用订单ID作为路由键发送消息
            // 同一订单的消息会进入同一个队列（如果队列数量固定）
            String routingKey = "order." + (orderId % 3); // 假设有3个队列
            rabbitTemplate.convertAndSend(exchange, routingKey, message);

            logger.info("RabbitMQ订单消息发送成功: orderId={}, eventType={}, routingKey={}, version={}",
                     orderId, eventType, routingKey, version);

        } catch (Exception e) {
            logger.error("RabbitMQ订单消息发送失败: orderId={}, eventType={}",
                         orderId, eventType, e);
            throw new RuntimeException("订单消息发送失败", e);
        }
    }

    /**
     * 批量发送订单消息（测试用）
     *
     * @param orderId 订单ID
     * @param events 事件列表
     */
    public void sendOrderMessages(int orderId, String... events) {
        for (int i = 0; i < events.length; i++) {
            sendOrderMessage(orderId, events[i], i + 1);
        }
    }
}
