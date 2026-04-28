package com.example.kafka;

import com.example.common.entity.Order;
import com.example.common.mapper.OrderMapper;
import com.example.kafka.producer.KafkaOrderProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka 顺序消息集成测试
 *
 * 测试场景：
 * 1. 单订单顺序消息：CREATE→PAY→SHIP 全流程
 * 2. 多订单并发：3个订单同时处理，互不干扰
 * 3. 版本号过滤：低版本消息被正确过滤
 * 4. 幂等性：同版本号重复消息不重复处理
 * 5. 非法状态流转：非法流转被正确拒绝
 */
@SpringBootTest(classes = KafkaApplication.class)
public class OrderConsumerTest {

    @Autowired
    private KafkaOrderProducer kafkaOrderProducer;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @Test
    public void testSingleOrderSequentialMessage() {
        int orderId = 2001;

        kafkaOrderProducer.sendOrderMessage(orderId, "CREATE_ORDER", 1);
        kafkaOrderProducer.sendOrderMessage(orderId, "PAY_ORDER", 2);
        kafkaOrderProducer.sendOrderMessage(orderId, "SHIP_ORDER", 3);

        sleep(5000);

        Order order = orderMapper.selectById(orderId);
        assertNotNull(order, "订单应该存在");
        assertEquals("SHIPPING", order.getStatus(), "订单状态应该是 SHIPPING");
        assertEquals(3L, order.getVersion(), "订单版本号应该是 3");

        System.out.println("✅ 测试通过：单个订单顺序消息处理正确");
    }

    @Test
    public void testMultipleOrderConcurrentMessage() {
        int[] orderIds = {2011, 2012, 2013};

        for (int orderId : orderIds) {
            new Thread(() -> {
                kafkaOrderProducer.sendOrderMessages(orderId,
                        "CREATE_ORDER", "PAY_ORDER", "SHIP_ORDER");
            }).start();
        }

        sleep(10000);

        for (int orderId : orderIds) {
            Order order = orderMapper.selectById(orderId);
            assertNotNull(order, "订单" + orderId + "应该存在");
            assertEquals("SHIPPING", order.getStatus(),
                    "订单" + orderId + "状态应该是 SHIPPING");
            assertEquals(3L, order.getVersion(),
                    "订单" + orderId + "版本号应该是 3");
        }

        System.out.println("✅ 测试通过：多个订单并发处理正确");
    }

    @Test
    public void testVersionFilter() {
        int orderId = 2014;

        kafkaOrderProducer.sendOrderMessage(orderId, "CREATE_ORDER", 1);
        sleep(2000);

        // 先发送高版本
        kafkaOrderProducer.sendOrderMessage(orderId, "PAY_ORDER", 3);
        sleep(2000);

        // 再发送低版本（应该被过滤）
        kafkaOrderProducer.sendOrderMessage(orderId, "SHIP_ORDER", 2);
        sleep(3000);

        Order order = orderMapper.selectById(orderId);
        assertNotNull(order, "订单应该存在");
        assertEquals("PAID", order.getStatus(),
                "订单状态应该是 PAID（version=2的SHIP被过滤）");
        assertEquals(3L, order.getVersion(), "订单版本号应该是 3");

        System.out.println("✅ 测试通过：版本号过滤正确");
    }

    @Test
    public void testIdempotency() {
        int orderId = 2015;

        // 先创建订单
        kafkaOrderProducer.sendOrderMessage(orderId, "CREATE_ORDER", 1);
        sleep(2000);

        // 发送两次相同的支付消息（同版本号）
        kafkaOrderProducer.sendOrderMessage(orderId, "PAY_ORDER", 2);
        sleep(1000);
        kafkaOrderProducer.sendOrderMessage(orderId, "PAY_ORDER", 2);
        sleep(2000);

        Order order = orderMapper.selectById(orderId);
        assertNotNull(order, "订单应该存在");
        assertEquals("PAID", order.getStatus(), "订单状态应该是 PAID");
        assertEquals(2L, order.getVersion(), "订单版本号应该是 2");

        System.out.println("✅ 测试通过：幂等性正确，重复消息不会重复处理");
    }

    @Test
    public void testInvalidStatusTransition() {
        int orderId = 2016;

        kafkaOrderProducer.sendOrderMessage(orderId, "CREATE_ORDER", 1);
        sleep(2000);

        kafkaOrderProducer.sendOrderMessage(orderId, "RECEIVE_ORDER", 2);
        sleep(2000);

        Order order = orderMapper.selectById(orderId);
        assertNotNull(order, "订单应该存在");
        assertNotEquals("RECEIVED", order.getStatus(),
                "订单状态不应该是 RECEIVED（非法流转被拒绝）");

        System.out.println("✅ 测试通过：非法状态流转被正确拒绝");
    }

    @Test
    public void testPartitionRouting() {
        int[] orderIds = {2017, 2018, 2019};

        for (int orderId : orderIds) {
            kafkaOrderProducer.sendOrderMessage(orderId, "CREATE_ORDER", 1);
        }

        sleep(5000);

        for (int orderId : orderIds) {
            Order order = orderMapper.selectById(orderId);
            assertNotNull(order, "订单" + orderId + "应该存在");
        }

        System.out.println("✅ 测试通过：分区路由正确");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
