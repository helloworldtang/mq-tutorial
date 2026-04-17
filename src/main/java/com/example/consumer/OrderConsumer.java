package com.example.consumer;

import com.alibaba.fastjson2.JSON;
import com.example.entity.Order;
import com.example.mapper.OrderMapper;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

/**
 * 订单消费者
 */
@Component
public class OrderConsumer implements MessageListenerOrderly {

    private static final Logger logger = LoggerFactory.getLogger(OrderConsumer.class);

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 消费消息
     */
    @Override
    public ConsumeOrderlyStatus consumeMessage(
        List<MessageExt> msgs,
        ConsumeOrderlyContext context
    ) {
        // 消费消息（同一个订单的消息会按顺序到达）
        for (MessageExt msg : msgs) {
            try {
                processOrderMessage(msg);
            } catch (Exception e) {
                logger.error("订单处理失败", e);
                // 返回稍后重试
                return ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT;
            }
        }

        // 返回消费成功
        return ConsumeOrderlyStatus.SUCCESS;
    }

    /**
     * 处理订单消息
     */
    private void processOrderMessage(MessageExt msg) {
        // 1. 解析消息
        JSONObject json = JSON.parseObject(new String(msg.getBody()));
        int orderId = json.getIntValue("orderId");
        String eventType = json.getString("eventType");
        long newVersion = json.getLongValue("version");

        logger.info("收到订单消息: orderId={}, eventType={}, version={}", 
                 orderId, eventType, newVersion);

        // 2. 查询当前订单
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            logger.error("订单不存在: {}", orderId);
            return;
        }

        // 3. 检查状态流转是否合法
        String currentStatus = order.getStatus();
        String targetStatus = getTargetStatus(eventType);

        if (!canTransition(currentStatus, targetStatus)) {
            logger.error("订单状态流转不合法: orderId={}, from={}, to={}", 
                     orderId, currentStatus, targetStatus);
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
            logger.warn("版本冲突，更新失败: orderId={}, currentVersion={}", 
                     orderId, order.getVersion());
            return;
        }

        logger.info("订单处理完成: orderId={}, status={}", orderId, targetStatus);
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
