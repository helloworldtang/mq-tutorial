package com.example.rabbitmq.consumer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.example.common.entity.Order;
import com.example.common.mapper.OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

/**
 * RabbitMQ订单消费者（支持顺序消息）
 *
 * 核心机制：
 * 1. 每个队列只有一个消费者，保证队列内顺序消费
 * 2. 同一订单的消息进入同一队列，保证局部顺序
 * 3. 使用版本号过滤网络乱序，保证业务正确性
 */
@Component
public class OrderConsumer {

    private static final Logger logger = LoggerFactory.getLogger(OrderConsumer.class);

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 消费OrderQueue0的消息（单线程保证顺序）
     */
    @RabbitListener(queues = "OrderQueue0")
    public void onMessageQueue0(Message message) {
        processOrderMessage(message, "OrderQueue0");
    }

    /**
     * 消费OrderQueue1的消息（单线程保证顺序）
     */
    @RabbitListener(queues = "OrderQueue1")
    public void onMessageQueue1(Message message) {
        processOrderMessage(message, "OrderQueue1");
    }

    /**
     * 消费OrderQueue2的消息（单线程保证顺序）
     */
    @RabbitListener(queues = "OrderQueue2")
    public void onMessageQueue2(Message message) {
        processOrderMessage(message, "OrderQueue2");
    }

    /**
     * 处理订单消息
     *
     * @param message RabbitMQ消息
     * @param queueName 队列名称
     */
    private void processOrderMessage(Message message, String queueName) {
        try {
            String messageBody = new String(message.getBody());
            JSONObject json = JSON.parseObject(messageBody);
            int orderId = json.getIntValue("orderId");
            String eventType = json.getString("eventType");
            long newVersion = json.getLongValue("version");

            logger.info("RabbitMQ收到订单消息: queue={}, orderId={}, eventType={}, version={}",
                     queueName, orderId, eventType, newVersion);

            // 1. 查询当前订单
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                logger.error("订单不存在: {}", orderId);
                return;
            }

            // 2. 版本号过滤（处理网络乱序）
            if (newVersion <= order.getVersion()) {
                logger.info("RabbitMQ消息过时，跳过: queue={}, orderId={}, messageVersion={}, dbVersion={}",
                         queueName, orderId, newVersion, order.getVersion());
                return;
            }

            // 3. 检查状态流转是否合法
            String currentStatus = order.getStatus();
            String targetStatus = getTargetStatus(eventType);

            if (!canTransition(currentStatus, targetStatus)) {
                logger.error("RabbitMQ订单状态流转不合法: queue={}, orderId={}, from={}, to={}",
                         queueName, orderId, currentStatus, targetStatus);
                return;
            }

            // 4. 核心：原子性更新（版本号校验 + 状态变更）
            int rows = orderMapper.updateStatusWithVersion(
                orderId,
                targetStatus,
                order.getVersion(),
                newVersion
            );

            if (rows == 0) {
                logger.warn("RabbitMQ版本冲突，更新失败: queue={}, orderId={}, currentVersion={}",
                         queueName, orderId, order.getVersion());
                return;
            }

            logger.info("RabbitMQ订单处理完成: queue={}, orderId={}, status={}, version={}",
                     queueName, orderId, targetStatus, newVersion);

        } catch (Exception e) {
            logger.error("RabbitMQ订单处理失败", e);
            throw new RuntimeException("订单处理失败", e);
        }
    }

    /**
     * 检查是否可以执行状态流转
     */
    private boolean canTransition(String fromStatus, String toStatus) {
        // 定义合法的状态流转
        Set<String> allowedFromPENDING = new HashSet<>(Arrays.asList("PAID", "CANCELLED"));
        Set<String> allowedFromPAID = new HashSet<>(Arrays.asList("SHIPPING"));
        Set<String> allowedFromSHIPPING = new HashSet<>(Arrays.asList("SHIPPED"));
        Set<String> allowedFromSHIPPED = new HashSet<>(Arrays.asList("RECEIVED"));

        switch (fromStatus) {
            case "PENDING":
                return allowedFromPENDING.contains(toStatus);
            case "PAID":
                return allowedFromPAID.contains(toStatus);
            case "SHIPPING":
                return allowedFromSHIPPING.contains(toStatus);
            case "SHIPPED":
                return allowedFromSHIPPED.contains(toStatus);
            default:
                return false;
        }
    }

    /**
     * 根据事件类型获取目标状态
     */
    private String getTargetStatus(String eventType) {
        switch (eventType) {
            case "CREATE_ORDER":
                return "PENDING";
            case "PAY_ORDER":
                return "PAID";
            case "SHIP_ORDER":
                return "SHIPPING";
            case "RECEIVE_ORDER":
                return "RECEIVED";
            case "CANCEL_ORDER":
                return "CANCELLED";
            default:
                return null;
        }
    }
}
