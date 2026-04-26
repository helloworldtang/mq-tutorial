package com.example.rabbitmqexclusive.consumer;

import com.example.rabbitmq.common.consumer.AbstractOrderConsumer;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ订单消费者（Exclusive Consumer方案）
 *
 * 核心机制：
 * 1. @RabbitListener 设置 concurrency="1" + exclusive="true"
 * 2. RabbitMQ Broker保证只有一个消费者能消费该队列
 * 3. 继承AbstractOrderConsumer，复用版本号过滤+状态机+乐观锁
 */
@Component
public class OrderConsumer extends AbstractOrderConsumer {

    @RabbitListener(queues = "ExclusiveOrderQueue0", concurrency = "1", exclusive = true)
    public void onMessageQueue0(Message message) {
        processOrderMessage(message, "ExclusiveOrderQueue0");
    }

    @RabbitListener(queues = "ExclusiveOrderQueue1", concurrency = "1", exclusive = true)
    public void onMessageQueue1(Message message) {
        processOrderMessage(message, "ExclusiveOrderQueue1");
    }

    @RabbitListener(queues = "ExclusiveOrderQueue2", concurrency = "1", exclusive = true)
    public void onMessageQueue2(Message message) {
        processOrderMessage(message, "ExclusiveOrderQueue2");
    }
}
