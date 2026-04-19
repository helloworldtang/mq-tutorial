package com.example.rabbitmq;

import com.example.rabbitmq.producer.OrderProducer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * RabbitMQ订单消息演示
 *
 * 演示场景：
 * 1. 单个订单的顺序消息
 * 2. 多个订单的并发消息
 * 3. 版本号过滤过时消息
 * 4. 幂等性防止重复处理
 */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    public CommandLineRunner demo(OrderProducer orderProducer) {
        return args -> {
            System.out.println("=== RabbitMQ 订单消息演示 ===");
            System.out.println();

            // 演示1：单个订单的顺序消息
            System.out.println("演示1：单个订单的顺序消息");
            System.out.println("----------------------------------------");
            System.out.println("订单1001: 创建(1) → 支付(2) → 发货(3)");
            orderProducer.sendOrderMessage(1001, "CREATE_ORDER", 1);
            orderProducer.sendOrderMessage(1001, "PAY_ORDER", 2);
            orderProducer.sendOrderMessage(1001, "SHIP_ORDER", 3);
            System.out.println("✅ 消息发送完成");
            System.out.println();

            // 演示2：多个订单的并发消息
            System.out.println("演示2：多个订单的并发消息");
            System.out.println("----------------------------------------");
            System.out.println("订单1002、1003、1004并发发送消息");
            for (int orderId : new int[]{1002, 1003, 1004}) {
                new Thread(() -> {
                    orderProducer.sendOrderMessages(orderId,
                        "CREATE_ORDER", "PAY_ORDER", "SHIP_ORDER");
                }).start();
            }
            System.out.println("✅ 消息发送完成");
            System.out.println();

            // 演示3：版本号过滤
            System.out.println("演示3：版本号过滤过时消息");
            System.out.println("----------------------------------------");
            System.out.println("订单1005: 先发送支付(2)，再发送创建(1)");
            orderProducer.sendOrderMessage(1005, "PAY_ORDER", 2);
            Thread.sleep(1000);
            orderProducer.sendOrderMessage(1005, "CREATE_ORDER", 1);
            System.out.println("✅ 消息发送完成，创建消息(1)应该被过滤");
            System.out.println();

            // 演示4：幂等性
            System.out.println("演示4：幂等性防止重复处理");
            System.out.println("----------------------------------------");
            System.out.println("订单1006: 发送两次相同的支付消息");
            orderProducer.sendOrderMessage(1006, "PAY_ORDER", 1);
            Thread.sleep(1000);
            orderProducer.sendOrderMessage(1006, "PAY_ORDER", 1);
            System.out.println("✅ 消息发送完成，第二次应该被幂等性过滤");
            System.out.println();

            System.out.println("=== 演示完成 ===");
            System.out.println("请查看 RabbitMQ 控制台: http://localhost:15672");
            System.out.println("用户名: admin, 密码: admin2026");
        };
    }
}
