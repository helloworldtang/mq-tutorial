package com.example.rabbitmqexclusive;

import com.example.common.entity.Order;
import com.example.common.mapper.OrderMapper;
import com.example.rabbitmqexclusive.producer.OrderProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = RabbitMQExclusiveApplication.class)
public class OrderConsumerTest {

    @Autowired
    private OrderProducer orderProducer;

    @Autowired
    private OrderMapper orderMapper;

    @Test
    public void testSingleOrderSequentialMessage() {
        int orderId = 4001;

        orderProducer.sendOrderMessage(orderId, "CREATE_ORDER", 1);
        orderProducer.sendOrderMessage(orderId, "PAY_ORDER", 2);
        orderProducer.sendOrderMessage(orderId, "SHIP_ORDER", 3);

        sleep(5000);

        Order order = orderMapper.selectById(orderId);
        assertNotNull(order, "订单应该存在");
        assertEquals("SHIPPING", order.getStatus(), "订单状态应该是 SHIPPING");
        assertEquals(3, order.getVersion(), "订单版本号应该是 3");

        System.out.println("✅ Exclusive测试通过：单个订单顺序消息处理正确");
    }

    @Test
    public void testVersionFilter() {
        int orderId = 4004;

        orderProducer.sendOrderMessage(orderId, "CREATE_ORDER", 1);
        sleep(2000);

        orderProducer.sendOrderMessage(orderId, "PAY_ORDER", 3);
        sleep(2000);

        orderProducer.sendOrderMessage(orderId, "SHIP_ORDER", 2);
        sleep(3000);

        Order order = orderMapper.selectById(orderId);
        assertNotNull(order, "订单应该存在");
        assertEquals("PAID", order.getStatus(),
            "订单状态应该是 PAID（version=2的SHIP被过滤）");
        assertEquals(3, order.getVersion(), "订单版本号应该是 3");

        System.out.println("✅ Exclusive测试通过：版本号过滤正确");
    }

    @Test
    public void testIdempotency() {
        int orderId = 4005;

        orderProducer.sendOrderMessage(orderId, "CREATE_ORDER", 1);
        sleep(2000);

        orderProducer.sendOrderMessage(orderId, "PAY_ORDER", 2);
        sleep(1000);
        orderProducer.sendOrderMessage(orderId, "PAY_ORDER", 2);
        sleep(2000);

        Order order = orderMapper.selectById(orderId);
        assertNotNull(order, "订单应该存在");
        assertEquals("PAID", order.getStatus(), "订单状态应该是 PAID");
        assertEquals(2, order.getVersion(), "订单版本号应该是 2");

        System.out.println("✅ Exclusive测试通过：幂等性正确");
    }

    @Test
    public void testInvalidStatusTransition() {
        int orderId = 4006;

        orderProducer.sendOrderMessage(orderId, "CREATE_ORDER", 1);
        sleep(2000);

        orderProducer.sendOrderMessage(orderId, "RECEIVE_ORDER", 2);
        sleep(2000);

        Order order = orderMapper.selectById(orderId);
        assertNotNull(order, "订单应该存在");
        assertNotEquals("RECEIVED", order.getStatus(),
            "订单状态不应该是 RECEIVED（非法流转被拒绝）");

        System.out.println("✅ Exclusive测试通过：非法状态流转被正确拒绝");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
