package com.example.kafka.producer;

import com.alibaba.fastjson2.JSON;
import com.example.rabbitmq.common.message.OrderMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Kafka 订单生产者
 *
 * 核心机制：
 * 1. 使用订单ID作为消息 Key，保证同一订单进入同一分区（Kafka 按 key 的 hash 路由分区）
 * 2. acks=all + 幂等性，保证消息不丢失
 * 3. 同步等待发送结果，超时 5 秒
 *
 * Kafka 顺序消息原理：
 * - Kafka 的顺序保证是 Partition 级，不是 Topic 级
 * - 同一 Partition 内的消息，按写入顺序被消费
 * - 用 orderId 作为 key，Kafka 会按 hash(key) % partitionCount 路由到固定分区
 * - 同一订单的所有消息 → 同一分区 → 消费时严格按顺序
 */
@Component
public class KafkaOrderProducer {

    private static final Logger logger = LoggerFactory.getLogger(KafkaOrderProducer.class);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private static final String TOPIC = "orders";

    /**
     * 发送订单消息（同步等待）
     *
     * @param orderId   订单ID（作为消息 Key，用于分区路由）
     * @param eventType 事件类型
     * @param version   版本号
     */
    public void sendOrderMessage(int orderId, String eventType, long version) {
        sendOrderMessage(orderId, eventType, version, TOPIC);
    }

    public void sendOrderMessage(int orderId, String eventType, long version, String topic) {
        try {
            OrderMessage orderMsg = new OrderMessage(orderId, eventType, version);
            String json = JSON.toJSONString(orderMsg);

            // Key = String.valueOf(orderId)，Kafka 按 hash(key) % partitions 路由
            // 保证同一订单进入同一分区
            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(topic, String.valueOf(orderId), json);

            // 同步等待发送结果
            SendResult<String, String> result = future.get(5, TimeUnit.SECONDS);

            logger.info("✅ Kafka 消息发送成功: orderId={}, eventType={}, topic={}, partition={}, offset={}, version={}",
                    orderId, eventType, topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    version);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("❌ Kafka 消息发送被中断: orderId={}, eventType={}", orderId, eventType, e);
            throw new RuntimeException("Kafka 消息发送被中断", e);
        } catch (ExecutionException | TimeoutException e) {
            logger.error("❌ Kafka 消息发送失败: orderId={}, eventType={}", orderId, eventType, e);
            throw new RuntimeException("Kafka 消息发送失败", e);
        }
    }

    /**
     * 发送一组顺序消息（用于测试）
     * 按事件顺序同步发送，保证版本号递增
     */
    public void sendOrderMessages(int orderId, String... events) {
        for (int i = 0; i < events.length; i++) {
            sendOrderMessage(orderId, events[i], i + 1);
        }
    }
}
