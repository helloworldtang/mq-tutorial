package com.example.rabbitmqsac.consumer;

import com.example.rabbitmq.common.consumer.AbstractOrderConsumer;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ订单消费者（SAC方案）
 *
 * 核心机制：
 * 1. 队列配置了 x-single-active-consumer=true，Broker保证单消费者活跃
 * 2. 继承AbstractOrderConsumer，复用版本号过滤+状态机+乐观锁
 */
@Component
public class OrderConsumer extends AbstractOrderConsumer {

    @RabbitListener(queues = "SacOrderQueue0")
    public void onMessageQueue0(Message message) {
        processOrderMessage(message, "SacOrderQueue0");
    }

    @RabbitListener(queues = "SacOrderQueue1")
    public void onMessageQueue1(Message message) {
        processOrderMessage(message, "SacOrderQueue1");
    }

    @RabbitListener(queues = "SacOrderQueue2")
    public void onMessageQueue2(Message message) {
        processOrderMessage(message, "SacOrderQueue2");
    }
}
