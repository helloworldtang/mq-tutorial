package com.example;

import com.example.producer.OrderProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * 生产者启动类
 */
@SpringBootApplication
@MapperScan("com.example.mapper")
public class ProducerApplication implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(ProducerApplication.class);

    @Autowired
    private OrderProducer orderProducer;

    public static void main(String[] args) {
        SpringApplication.run(ProducerApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("开始发送订单消息...");

        // 创建订单消息
        for (int i = 0; i < 10; i++) {
            int orderId = 1000 + i;
            String eventType = "PAY_ORDER";
            long version = 2L;

            orderProducer.sendOrderMessage(orderId, eventType, version);

            Thread.sleep(1000); // 每秒发送一条消息
        }

        logger.info("订单消息发送完成");

        // 等待一段时间，确保消息被消费
        Thread.sleep(10000);

        System.exit(0);
    }
}
