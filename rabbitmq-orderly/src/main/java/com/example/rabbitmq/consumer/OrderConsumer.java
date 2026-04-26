package com.example.rabbitmq.consumer;

import com.example.rabbitmq.common.consumer.AbstractOrderConsumer;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ订单消费者（基础方案）
 *
 * 核心机制：
 * 1. 每个队列只有一个消费者，保证队列内顺序消费
 * 2. 同一订单的消息进入同一队列，保证局部顺序
 * 3. 继承AbstractOrderConsumer，复用版本号过滤+状态机+乐观锁
 */
@Component
public class OrderConsumer extends AbstractOrderConsumer {

    @RabbitListener(queues = "OrderQueue0")
    public void onMessageQueue0(Message message) {
        processOrderMessage(message, "OrderQueue0");
    }

    @RabbitListener(queues = "OrderQueue1")
    public void onMessageQueue1(Message message) {
        processOrderMessage(message, "OrderQueue1");
    }

    @RabbitListener(queues = "OrderQueue2")
    public void onMessageQueue2(Message message) {
        processOrderMessage(message, "OrderQueue2");
    }
}
