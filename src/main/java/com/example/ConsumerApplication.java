package com.example;

import com.example.consumer.OrderConsumer;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * 消费者启动类
 */
@SpringBootApplication
@MapperScan("com.example.mapper")
public class ConsumerApplication {

    private static final Logger logger = LoggerFactory.getLogger(ConsumerApplication.class);

    @Autowired
    private OrderConsumer orderConsumer;

    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class, args);
    }

    @Bean
    public org.apache.rocketmq.client.consumer.DefaultMQPushConsumer orderConsumer(RocketMQTemplate rocketMQTemplate) {
        return rocketMQTemplate.getConsumer();
    }

    @Bean
    @RocketMQMessageListener(
        topic = "OrderTopic",
        consumerGroup = "order-consumer",
        messageModel = org.apache.rocketmq.common.protocol.heartbeat.MessageModel.CLUSTERING,
        consumeMode = org.apache.rocketmq.spring.annotation.ConsumeMode.ORDERLY
    )
    public RocketMQListener orderRocketMQListener() {
        return (message) -> {
            // 这里只是示例，实际的顺序消费逻辑在OrderConsumer中
            logger.info("收到消息: {}", new String((byte[]) message));
        };
    }
}
