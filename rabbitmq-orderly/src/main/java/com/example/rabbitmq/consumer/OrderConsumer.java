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
 */
@Component
public class OrderConsumer {

    private static final Logger logger = LoggerFactory.getLogger(OrderConsumer.class);

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 消费RabbitMQ消息（单线程保证顺序）
     */
    @RabbitListener(queues = "OrderQueue")
    public void onMessage(Message message) {
        try {
            String messageBody = new String(message.getBody());
            JSONObject json = JSON.parseObject(messageBody);
            int orderId = json.getIntValue("orderId");
            String eventType = json.getString("eventType");
            long newVersion = json.getLongValue("version");

            logger.info("RabbitMQ收到订单消息: orderId={}, eventType={}, version={}",
                     orderId, eventType, newVersion);

            // 查询当前订单
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                logger.error("订单不存在: {}", orderId);
                return;
            }

            // 检查状态流转是否合法
            String currentStatus = order.getStatus();
            String targetStatus = getTargetStatus(eventType);

            if (!canTransition(currentStatus, targetStatus)) {
                logger.error("订单状态流转不合法: orderId={}, from={}, to={}",
                         orderId, currentStatus, targetStatus);
                return;
            }

            // 核心：原子性更新（版本号校验 + 状态变更）
            int rows = orderMapper.updateStatusWithVersion(
                orderId,
                targetStatus,
                order.getVersion(),
                newVersion
            );

            if (rows == 0) {
                logger.warn("RabbitMQ版本冲突，更新失败: orderId={}, currentVersion={}",
                         orderId, order.getVersion());
                return;
            }

            logger.info("RabbitMQ订单处理完成: orderId={}, status={}", orderId, targetStatus);

        } catch (Exception e) {
            logger.error("RabbitMQ订单处理失败", e);
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
