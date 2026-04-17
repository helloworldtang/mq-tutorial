package com.example;

import com.alibaba.fastjson2.JSON;
import com.example.consumer.OrderConsumer;
import com.example.entity.Order;
import com.example.mapper.OrderMapper;
import com.example.producer.OrderProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订单消费者测试
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application.yml")
public class OrderConsumerTest {

    private static final Logger logger = LoggerFactory.getLogger(OrderConsumerTest.class);

    @Autowired
    private OrderProducer orderProducer;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @BeforeEach
    public void setUp() {
        // 创建测试订单
        Order order = new Order();
        order.setId(999);
        order.setStatus("PENDING");
        order.setVersion(1L);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        orderMapper.insert(order);

        logger.info("测试订单创建成功: orderId={}", order.getId());
    }

    @AfterEach
    public void tearDown() {
        // 清理测试数据（可选）
        // orderMapper.deleteById(999);
    }

    /**
     * 测试顺序性：验证订单状态按预期流转
     */
    @Test
    public void testOrderSequence() throws Exception {
        // 1. 发送支付订单消息
        SendResult payResult = orderProducer.sendOrderMessage(999, "PAY_ORDER", 2L);
        assertNotNull(payResult);

        // 2. 等待消费
        Thread.sleep(3000);

        // 3. 验证订单状态变为PAID
        Order order = orderMapper.selectById(999);
        assertEquals("PAID", order.getStatus());
        assertEquals(2L, order.getVersion());

        logger.info("订单状态流转成功: orderId={}, status={}", order.getId(), order.getStatus());

        // 4. 发送发货订单消息
        SendResult shipResult = orderProducer.sendOrderMessage(999, "SHIP_ORDER", 3L);
        assertNotNull(shipResult);

        // 5. 等待消费
        Thread.sleep(3000);

        // 6. 验证订单状态变为SHIPPING
        order = orderMapper.selectById(999);
        assertEquals("SHIPPING", order.getStatus());
        assertEquals(3L, order.getVersion());

        logger.info("订单状态流转成功: orderId={}, status={}", order.getId(), order.getStatus());
    }

    /**
     * 测试幂等性：验证重复消息不会重复更新
     */
    @Test
    public void testIdempotency() throws Exception {
        // 1. 发送支付订单消息
        SendResult payResult1 = orderProducer.sendOrderMessage(999, "PAY_ORDER", 2L);
        assertNotNull(payResult1);

        // 2. 等待消费
        Thread.sleep(3000);

        // 3. 再次发送相同的支付订单消息
        SendResult payResult2 = orderProducer.sendOrderMessage(999, "PAY_ORDER", 2L);
        assertNotNull(payResult2);

        // 4. 等待消费
        Thread.sleep(3000);

        // 5. 验证订单状态只更新了一次
        Order order = orderMapper.selectById(999);
        assertEquals("PAID", order.getStatus());
        assertEquals(2L, order.getVersion());

        logger.info("幂等性验证成功: orderId={}, status={}, version={}", 
                 order.getId(), order.getStatus(), order.getVersion());
    }

    /**
     * 测试非法状态流转：验证不合法的状态流转会被拒绝
     */
    @Test
    public void testIllegalStateTransition() throws Exception {
        // 1. 发送发货订单消息（跳过支付）
        SendResult shipResult = orderProducer.sendOrderMessage(999, "SHIP_ORDER", 2L);
        assertNotNull(shipResult);

        // 2. 等待消费
        Thread.sleep(3000);

        // 3. 验证订单状态没有变化
        Order order = orderMapper.selectById(999);
        assertEquals("PENDING", order.getStatus());
        assertEquals(1L, order.getVersion());

        logger.info("非法状态流转被拒绝: orderId={}, status={}", order.getId(), order.getStatus());
    }

    /**
     * 测试乱序处理：验证乱序消息会被拒绝
     */
    @Test
    public void testOutOfOrder() throws Exception {
        // 1. 先发送发货订单消息
        SendResult shipResult = orderProducer.sendOrderMessage(999, "SHIP_ORDER", 3L);
        assertNotNull(shipResult);

        // 2. 等待消费
        Thread.sleep(3000);

        // 3. 验证订单状态没有变化（版本号不匹配）
        Order order = orderMapper.selectById(999);
        assertEquals("PENDING", order.getStatus());
        assertEquals(1L, order.getVersion());

        // 4. 再发送支付订单消息
        SendResult payResult = orderProducer.sendOrderMessage(999, "PAY_ORDER", 2L);
        assertNotNull(payResult);

        // 5. 等待消费
        Thread.sleep(3000);

        // 6. 验证订单状态变为PAID
        order = orderMapper.selectById(999);
        assertEquals("PAID", order.getStatus());
        assertEquals(2L, order.getVersion());

        logger.info("乱序处理验证成功: orderId={}, status={}", order.getId(), order.getStatus());
    }
}
