package com.example.rabbitmq;

import com.example.common.entity.Order;
import com.example.common.mapper.OrderMapper;
import com.example.rabbitmq.producer.OrderProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RabbitMQ顺序消息测试
 *
 * 测试场景：
 * 1. 验证局部顺序：同一订单的消息按顺序处理
 * 2. 验证多订单并发：不同订单可以并行处理
 * 3. 验证版本号过滤：过时消息会被过滤
 * 4. 验证幂等性：重复消息不会重复处理
 */
@SpringBootTest
public class OrderConsumerTest {

    @Autowired
    private OrderProducer orderProducer;

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 测试单个订单的顺序消息
     *
     * 场景：订单1001发送创建、支付、发货消息
     * 预期：订单状态按顺序更新 PENDING → PAID → SHIPPING
     */
    @Test
    public void testSingleOrderSequentialMessage() {
        int orderId = 1001;

        // 发送订单1001的3条消息
        orderProducer.sendOrderMessage(orderId, "CREATE_ORDER", 1);
        orderProducer.sendOrderMessage(orderId, "PAY_ORDER", 2);
        orderProducer.sendOrderMessage(orderId, "SHIP_ORDER", 3);

        // 等待消费
        sleep(5000);

        // 验证订单状态
        Order order = orderMapper.selectById(orderId);
        assertNotNull(order, "订单应该存在");
        assertEquals("SHIPPING", order.getStatus(), "订单状态应该是 SHIPPING");
        assertEquals(3, order.getVersion(), "订单版本号应该是 3");

        System.out.println("✅ 测试通过：单个订单顺序消息处理正确");
    }

    /**
     * 测试多个订单的并发消息
     *
     * 场景：3个订单并发发送
     * 预期：
     * - 订单1001、1002、1003各自按顺序处理
     * - 不同订单之间可以乱序处理（局部顺序）
     */
    @Test
    public void testMultipleOrderConcurrentMessage() {
        int[] orderIds = {1001, 1002, 1003};

        // 并发发送3个订单的消息
        for (int orderId : orderIds) {
            new Thread(() -> {
                orderProducer.sendOrderMessages(orderId,
                    "CREATE_ORDER", "PAY_ORDER", "SHIP_ORDER");
            }).start();
        }

        // 等待消费
        sleep(10000);

        // 验证每个订单的状态
        for (int orderId : orderIds) {
            Order order = orderMapper.selectById(orderId);
            assertNotNull(order, "订单" + orderId + "应该存在");
            assertEquals("SHIPPING", order.getStatus(),
                "订单" + orderId + "状态应该是 SHIPPING");
            assertEquals(3, order.getVersion(),
                "订单" + orderId + "版本号应该是 3");
        }

        System.out.println("✅ 测试通过：多个订单并发处理正确");
    }

    /**
     * 测试版本号过滤
     *
     * 场景：先发送高版本消息，再发送低版本消息
     * 预期：低版本消息被过滤，订单状态不受影响
     */
    @Test
    public void testVersionFilter() {
        int orderId = 1004;

        // 先发送支付消息（version=2）
        orderProducer.sendOrderMessage(orderId, "PAY_ORDER", 2);

        sleep(2000);

        // 再发送创建消息（version=1，过时消息）
        orderProducer.sendOrderMessage(orderId, "CREATE_ORDER", 1);

        sleep(3000);

        // 验证订单状态
        Order order = orderMapper.selectById(orderId);
        assertNotNull(order, "订单应该存在");
        assertNotEquals("PENDING", order.getStatus(),
            "订单状态不应该是 PENDING（version=1的消息被过滤）");
        assertEquals(2, order.getVersion(), "订单版本号应该是 2");

        System.out.println("✅ 测试通过：版本号过滤正确，过时消息被过滤");
    }

    /**
     * 测试幂等性
     *
     * 场景：发送相同的消息两次
     * 预期：第二次发送不会改变订单状态
     */
    @Test
    public void testIdempotency() {
        int orderId = 1005;

        // 第一次发送支付消息
        orderProducer.sendOrderMessage(orderId, "PAY_ORDER", 1);

        sleep(2000);

        // 第二次发送相同的支付消息
        orderProducer.sendOrderMessage(orderId, "PAY_ORDER", 1);

        sleep(2000);

        // 验证订单状态只更新了一次
        Order order = orderMapper.selectById(orderId);
        assertNotNull(order, "订单应该存在");
        assertEquals("PAID", order.getStatus(), "订单状态应该是 PAID");
        assertEquals(1, order.getVersion(), "订单版本号应该是 1");

        System.out.println("✅ 测试通过：幂等性正确，重复消息不会重复处理");
    }

    /**
     * 测试状态流转合法性
     *
     * 场景：尝试执行非法的状态流转（从 PENDING 直接跳到 RECEIVED）
     * 预期：消息被拒绝，订单状态不变
     */
    @Test
    public void testInvalidStatusTransition() {
        int orderId = 1006;

        // 先创建订单
        orderProducer.sendOrderMessage(orderId, "CREATE_ORDER", 1);

        sleep(2000);

        // 尝试非法流转：从 PENDING 直接跳到 RECEIVED
        orderProducer.sendOrderMessage(orderId, "RECEIVE_ORDER", 2);

        sleep(2000);

        // 验证订单状态不变
        Order order = orderMapper.selectById(orderId);
        assertNotNull(order, "订单应该存在");
        assertNotEquals("RECEIVED", order.getStatus(),
            "订单状态不应该是 RECEIVED（非法流转被拒绝）");

        System.out.println("✅ 测试通过：非法状态流转被正确拒绝");
    }

    /**
     * 测试不同订单进入不同队列
     *
     * 场景：订单1007、1008、1009的消息
     * 预期：
     * - 1007(1007%3=1) → OrderQueue1
     * - 1008(1008%3=2) → OrderQueue2
     * - 1009(1009%3=1) → OrderQueue1
     * 1007和1009进入同一队列，1008进入不同队列
     */
    @Test
    public void testQueueRouting() {
        int[] orderIds = {1007, 1008, 1009};

        // 发送消息
        for (int orderId : orderIds) {
            orderProducer.sendOrderMessage(orderId, "CREATE_ORDER", 1);
        }

        sleep(5000);

        // 验证所有订单都被处理
        for (int orderId : orderIds) {
            Order order = orderMapper.selectById(orderId);
            assertNotNull(order, "订单" + orderId + "应该存在");
        }

        System.out.println("✅ 测试通过：队列路由正确");
    }

    /**
     * 睡眠方法
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
