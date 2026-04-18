package com.example.rocketmq.producer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.example.common.entity.Order;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RocketMQ订单生产者
 */
@Component
public class OrderProducer {

    private static final Logger logger = LoggerFactory.getLogger(OrderProducer.class);

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 发送订单消息（支持哈希路由）
     */
    public SendResult sendOrderMessage(int orderId, String eventType, long version) {
        try {
            // 1. 创建消息体
            JSONObject orderJson = new JSONObject();
            orderJson.put("orderId", orderId);
            orderJson.put("eventType", eventType);
            orderJson.put("version", version);

            // 2. 构建Spring消息
            Message<String> message = MessageBuilder.withPayload(orderJson.toJSONString())
                .setHeader("orderId", orderId)
                .build();

            // 3. 使用哈希路由发送消息
            SendResult result = rocketMQTemplate.syncSend(
                "OrderTopic",
                message,
                (mqs, msg, arg) -> {
                    Integer id = (Integer) arg;
                    int index = Math.abs(id) % mqs.size();
                    return mqs.get(index);
                },
                orderId
            );

            logger.info("RocketMQ订单消息发送成功: orderId={}, eventType={}, result={}",
                     orderId, eventType, result.getSendStatus());

            return result;

        } catch (Exception e) {
            logger.error("RocketMQ订单消息发送失败: orderId={}, eventType={}",
                         orderId, eventType, e);
            throw new RuntimeException("订单消息发送失败", e);
        }
    }
}
