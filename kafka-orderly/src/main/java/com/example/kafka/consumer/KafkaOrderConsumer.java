package com.example.kafka.consumer;

import com.alibaba.fastjson2.JSON;
import com.example.common.entity.Order;
import com.example.common.mapper.OrderMapper;
import com.example.rabbitmq.common.message.OrderMessage;
import com.example.rabbitmq.common.statemachine.OrderStatusMachine;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Kafka 订单消费者
 *
 * 核心机制：
 * 1. concurrency=1，单分区单线程消费，保证分区顺序
 * 2. orderId 作为 key，同一订单 → 同一分区 → 按顺序消费
 * 3. 版本号过滤：msgVersion <= dbVersion → 跳过（处理网络乱序）
 * 4. 状态机校验 + 乐观锁更新，与 RabbitMQ 方案一致
 * 5. 手动提交 offset，确保处理成功后才提交
 * 6. 失败消息进入 DLT（Dead Letter Topic）
 *
 * Kafka 与 RabbitMQ 顺序消息的核心区别：
 * - RabbitMQ：队列是消费单元，需要单消费者 + 版本号过滤
 * - Kafka：分区是消费单元，同一 key 的消息自动进入同一分区，单线程消费即保证顺序
 * - 网络乱序：两边都需要版本号过滤，Kafka 无需额外配置队列
 */
@Component
public class KafkaOrderConsumer {

    private static final Logger logger = LoggerFactory.getLogger(KafkaOrderConsumer.class);

    @Autowired
    private OrderMapper orderMapper;

    private static final String TOPIC = "orders";

    /**
     * 主消费者：监听 orders topic
     * concurrency=1 保证单分区单线程顺序消费
     * manual_immediate 手动提交 offset
     */
    @KafkaListener(topics = TOPIC, groupId = "kafka-orderly-consumer")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        long startTime = System.currentTimeMillis();
        String key = record.key();
        String value = record.value();
        int partition = record.partition();
        long offset = record.offset();

        logger.info("📥 Kafka 收到消息: key={}, partition={}, offset={}, value={}",
                key, partition, offset, value);

        try {
            OrderMessage orderMsg = JSON.parseObject(value, OrderMessage.class);
            int orderId = orderMsg.getOrderId();
            String eventType = orderMsg.getEventType();
            long msgVersion = orderMsg.getVersion();

            // 步骤1：查订单，不存在则自动创建（仅CREATE_ORDER场景）
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                if ("CREATE_ORDER".equals(eventType)) {
                    order = createOrder(orderId);
                    logger.info("自动创建订单: orderId={}, status=PENDING, version=0", orderId);
                } else {
                    logger.error("订单不存在且非创建事件，跳过: orderId={}, eventType={}", orderId, eventType);
                    ack.acknowledge();
                    return;
                }
            }

            // 步骤2：版本号过滤（处理网络乱序）
            if (msgVersion <= order.getVersion()) {
                logger.info("版本号过滤: orderId={}, msgVersion={}, dbVersion={} → 跳过",
                        orderId, msgVersion, order.getVersion());
                ack.acknowledge();
                return;
            }

            // 步骤3：状态机校验（先获取目标状态，再校验流转合法性）
            String currentStatus = order.getStatus();
            String targetStatus = OrderStatusMachine.getTargetStatus(eventType);

            if (targetStatus == null) {
                logger.error("未知事件类型: orderId={}, eventType={}", orderId, eventType);
                ack.acknowledge();
                return;
            }

            if (!OrderStatusMachine.canTransition(currentStatus, targetStatus)) {
                logger.warn("状态流转不合法: orderId={}, from={}, to={} → 跳过",
                        orderId, currentStatus, targetStatus);
                ack.acknowledge();
                return;
            }

            // 步骤4：乐观锁更新（使用消息版本号作为新版本）
            int updated = orderMapper.updateStatusWithVersion(
                    orderId, targetStatus, order.getVersion(), msgVersion);

            if (updated > 0) {
                logger.info("✅ 订单状态更新成功: orderId={}, {}→{}, version={}→{}",
                        orderId, currentStatus, targetStatus, order.getVersion(), msgVersion);
            } else {
                logger.warn("订单状态更新失败（乐观锁冲突）: orderId={}", orderId);
            }

            long cost = System.currentTimeMillis() - startTime;
            logger.info("消息处理完成: orderId={}, eventType={}, cost={}ms", orderId, eventType, cost);

            ack.acknowledge();

        } catch (Exception e) {
            logger.error("消息处理异常: key={}, partition={}, offset={}, error={}",
                    key, partition, offset, e.getMessage(), e);
            throw new RuntimeException("消息处理失败", e);
        }
    }

    /**
     * DLT 消费者：监听死信消息
     * 消费失败超过重试次数后，消息进入 orders.DLT topic
     */
    @KafkaListener(topics = TOPIC + ".DLT", groupId = "kafka-orderly-dlt-consumer")
    public void onDltMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        logger.error("🪦 DLT 收到死信: key={}, partition={}, offset={}, value={}",
                record.key(), record.partition(), record.offset(), record.value());
        ack.acknowledge();
    }

    private Order createOrder(int orderId) {
        Order order = new Order();
        order.setId(orderId);
        order.setStatus("PENDING");
        order.setVersion(0L);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.insert(order);
        return order;
    }
}
