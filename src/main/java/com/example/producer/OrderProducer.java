package com.example.producer;

import com.alibaba.fastjson2.JSON;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单生产者
 */
@Component
public class OrderProducer {

    private static final Logger logger = LoggerFactory.getLogger(OrderProducer.class);

    @Autowired
    private DefaultMQProducer producer;

    /**
     * 发送订单消息
     */
    public SendResult sendOrderMessage(int orderId, String eventType, long version) {
        try {
            // 1. 创建消息
            JSONObject orderJson = new JSONObject();
            orderJson.put("orderId", orderId);
            orderJson.put("eventType", eventType);
            orderJson.put("version", version);

            Message message = new Message(
                "OrderTopic",           // Topic
                "TagA",                // Tag
                orderJson.toJSONString().getBytes()
            );
            message.setKeys(String.valueOf(orderId));

            // 2. 使用哈希路由选择MessageQueue
            SendResult sendResult = producer.send(
                message,
                new MessageQueueSelector() {
                    @Override
                    public MessageQueue select(
                        List<MessageQueue> mqs,
                        Message msg,
                        Object arg
                    ) {
                        Integer orderId = (Integer) arg;
                        // 哈希路由：根据订单ID选择MessageQueue
                        int index = Math.abs(orderId) % mqs.size();
                        return mqs.get(index);
                    }
                },
                orderId
            );

            logger.info("订单消息发送成功: orderId={}, eventType={}, result={}", 
                     orderId, eventType, sendResult.getSendStatus());

            return sendResult;

        } catch (Exception e) {
            logger.error("订单消息发送失败: orderId={}, eventType={}", 
                         orderId, eventType, e);
            throw new RuntimeException("订单消息发送失败", e);
        }
    }
}
