package com.example.rabbitmq.common.consumer;

import com.example.common.entity.Order;
import com.example.common.mapper.OrderMapper;
import com.example.rabbitmq.common.message.OrderMessage;
import com.example.rabbitmq.common.statemachine.OrderStatusMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

/**
 * 订单消费者抽象基类
 *
 * 模板方法模式：
 * - processOrderMessage()：模板方法，定义处理流程骨架
 * - executeWithStrategy()：策略钩子，子类可覆盖（如分布式锁方案加锁）
 * - doBusinessProcess()：核心业务逻辑，所有方案共用
 *
 * 子类只需：
 * 1. 添加 @RabbitListener 监听队列
 * 2. 调用 processOrderMessage(message, queueName)
 * 3. 如需加锁，覆盖 executeWithStrategy()
 */
public abstract class AbstractOrderConsumer {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    protected OrderMapper orderMapper;

    /**
     * 模板方法：消息处理主流程
     */
    protected void processOrderMessage(Message message, String queueName) {
        OrderMessage orderMsg = deserializeMessage(message);
        logger.info("收到订单消息: queue={}, orderId={}, eventType={}, version={}",
                queueName, orderMsg.getOrderId(), orderMsg.getEventType(), orderMsg.getVersion());

        executeWithStrategy(orderMsg, queueName);
    }

    /**
     * 策略钩子：默认直接执行业务逻辑
     * DLock方案覆盖此方法，在业务逻辑外加分布式锁
     */
    protected void executeWithStrategy(OrderMessage orderMsg, String queueName) {
        doBusinessProcess(orderMsg, queueName);
    }

    /**
     * 核心业务逻辑：所有方案共用
     *
     * 1. 查询/创建订单
     * 2. 版本号过滤（处理乱序）
     * 3. 状态流转校验
     * 4. 原子更新（乐观锁）
     */
    protected final void doBusinessProcess(OrderMessage orderMsg, String queueName) {
        int orderId = orderMsg.getOrderId();
        long newVersion = orderMsg.getVersion();
        String eventType = orderMsg.getEventType();

        // 1. 查询订单，不存在则自动创建（CREATE_ORDER场景）
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            if ("CREATE_ORDER".equals(eventType)) {
                order = createOrder(orderId);
                logger.info("自动创建订单: orderId={}, status=PENDING, version=0", orderId);
            } else {
                logger.error("订单不存在且非创建事件，跳过: orderId={}, eventType={}", orderId, eventType);
                return;
            }
        }

        // 2. 版本号过滤：新版本 <= 当前版本，说明消息过时
        if (newVersion <= order.getVersion()) {
            logger.info("消息过时，跳过: queue={}, orderId={}, msgVersion={}, dbVersion={}",
                    queueName, orderId, newVersion, order.getVersion());
            return;
        }

        // 3. 状态流转校验
        String currentStatus = order.getStatus();
        String targetStatus = OrderStatusMachine.getTargetStatus(eventType);

        if (targetStatus == null) {
            logger.error("未知事件类型: queue={}, orderId={}, eventType={}", queueName, orderId, eventType);
            return;
        }

        if (!OrderStatusMachine.canTransition(currentStatus, targetStatus)) {
            logger.error("状态流转不合法: queue={}, orderId={}, from={}, to={}",
                    queueName, orderId, currentStatus, targetStatus);
            return;
        }

        // 4. 原子更新（版本号乐观锁）
        int rows = orderMapper.updateStatusWithVersion(orderId, targetStatus, order.getVersion(), newVersion);
        if (rows == 0) {
            logger.warn("版本冲突，更新失败: queue={}, orderId={}, currentVersion={}",
                    queueName, orderId, order.getVersion());
            return;
        }

        logger.info("订单处理完成: queue={}, orderId={}, status={}, version={}",
                queueName, orderId, targetStatus, newVersion);
    }

    /**
     * 反序列化消息
     */
    protected OrderMessage deserializeMessage(Message message) {
        return OrderMessage.fromMessage(message);
    }

    /**
     * 自动创建订单
     */
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
