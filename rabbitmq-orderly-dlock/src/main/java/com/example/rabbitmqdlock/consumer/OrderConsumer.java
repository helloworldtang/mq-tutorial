package com.example.rabbitmqdlock.consumer;

import com.example.rabbitmq.common.consumer.AbstractOrderConsumer;
import com.example.rabbitmq.common.message.OrderMessage;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * RabbitMQ订单消费者（分布式锁方案）
 *
 * 核心机制：
 * 1. 消费消息时按orderId获取Redisson分布式锁
 * 2. 保证同一订单的消息在多进程间串行处理
 * 3. 继承AbstractOrderConsumer，覆盖executeWithStrategy()加锁
 */
@Component
public class OrderConsumer extends AbstractOrderConsumer {

    @Autowired
    private RedissonClient redissonClient;

    @RabbitListener(queues = "DlockOrderQueue0")
    public void onMessageQueue0(Message message) {
        processOrderMessage(message, "DlockOrderQueue0");
    }

    @RabbitListener(queues = "DlockOrderQueue1")
    public void onMessageQueue1(Message message) {
        processOrderMessage(message, "DlockOrderQueue1");
    }

    @RabbitListener(queues = "DlockOrderQueue2")
    public void onMessageQueue2(Message message) {
        processOrderMessage(message, "DlockOrderQueue2");
    }

    /**
     * 覆盖策略钩子：在分布式锁内执行业务逻辑
     */
    @Override
    protected void executeWithStrategy(OrderMessage orderMsg, String queueName) {
        String lockKey = "order:lock:" + orderMsg.getOrderId();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(10, 30, TimeUnit.SECONDS);
            if (!acquired) {
                logger.warn("DLock获取锁失败: orderId={}, lockKey={}", orderMsg.getOrderId(), lockKey);
                throw new RuntimeException("获取分布式锁失败: orderId=" + orderMsg.getOrderId());
            }

            try {
                super.executeWithStrategy(orderMsg, queueName);
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("DLock订单处理被中断: orderId={}", orderMsg.getOrderId(), e);
            throw new RuntimeException("订单处理被中断", e);
        }
    }
}
