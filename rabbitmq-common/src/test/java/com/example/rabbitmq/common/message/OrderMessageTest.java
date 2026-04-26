package com.example.rabbitmq.common.message;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrderMessage 单元测试
 */
@DisplayName("OrderMessage 消息模型测试")
class OrderMessageTest {

    @Nested
    @DisplayName("构造函数")
    class Constructor {

        @Test
        @DisplayName("无参构造")
        void noArgs() {
            OrderMessage msg = new OrderMessage();
            assertEquals(0, msg.getOrderId());
            assertNull(msg.getEventType());
            assertEquals(0, msg.getVersion());
        }

        @Test
        @DisplayName("全参构造")
        void allArgs() {
            OrderMessage msg = new OrderMessage(1001, "PAY_ORDER", 2);
            assertEquals(1001, msg.getOrderId());
            assertEquals("PAY_ORDER", msg.getEventType());
            assertEquals(2, msg.getVersion());
        }
    }

    @Nested
    @DisplayName("fromMessage 反序列化")
    class FromMessage {

        @Test
        @DisplayName("正常JSON反序列化")
        void normalDeserialize() {
            String json = "{\"orderId\":1001,\"eventType\":\"PAY_ORDER\",\"version\":2}";
            Message amqpMsg = new Message(json.getBytes(), new MessageProperties());
            OrderMessage msg = OrderMessage.fromMessage(amqpMsg);

            assertEquals(1001, msg.getOrderId());
            assertEquals("PAY_ORDER", msg.getEventType());
            assertEquals(2, msg.getVersion());
        }

        @Test
        @DisplayName("CREATE_ORDER消息")
        void createOrderMessage() {
            String json = "{\"orderId\":2001,\"eventType\":\"CREATE_ORDER\",\"version\":1}";
            Message amqpMsg = new Message(json.getBytes(), new MessageProperties());
            OrderMessage msg = OrderMessage.fromMessage(amqpMsg);

            assertEquals(2001, msg.getOrderId());
            assertEquals("CREATE_ORDER", msg.getEventType());
            assertEquals(1, msg.getVersion());
        }

        @Test
        @DisplayName("字段缺失时使用默认值")
        void missingFields() {
            String json = "{\"orderId\":1001}";
            Message amqpMsg = new Message(json.getBytes(), new MessageProperties());
            OrderMessage msg = OrderMessage.fromMessage(amqpMsg);

            assertEquals(1001, msg.getOrderId());
            assertNull(msg.getEventType());
            assertEquals(0, msg.getVersion());
        }
    }

    @Nested
    @DisplayName("Setter/Getter")
    class SetterGetter {

        @Test
        @DisplayName("setter/getter正常工作")
        void setterGetter() {
            OrderMessage msg = new OrderMessage();
            msg.setOrderId(3001);
            msg.setEventType("SHIP_ORDER");
            msg.setVersion(3);

            assertEquals(3001, msg.getOrderId());
            assertEquals("SHIP_ORDER", msg.getEventType());
            assertEquals(3, msg.getVersion());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("toString包含关键字段")
        void toStringContainsFields() {
            OrderMessage msg = new OrderMessage(1001, "PAY_ORDER", 2);
            String str = msg.toString();
            assertTrue(str.contains("1001"));
            assertTrue(str.contains("PAY_ORDER"));
            assertTrue(str.contains("2"));
        }
    }
}
