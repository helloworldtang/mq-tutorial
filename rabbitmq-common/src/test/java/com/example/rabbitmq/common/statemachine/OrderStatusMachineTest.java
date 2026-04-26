package com.example.rabbitmq.common.statemachine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrderStatusMachine 单元测试
 */
@DisplayName("OrderStatusMachine 状态机测试")
class OrderStatusMachineTest {

    @Nested
    @DisplayName("canTransition - 合法流转")
    class CanTransitionValid {

        @Test
        @DisplayName("PENDING → PAID")
        void pendingToPaid() {
            assertTrue(OrderStatusMachine.canTransition("PENDING", "PAID"));
        }

        @Test
        @DisplayName("PENDING → CANCELLED")
        void pendingToCancelled() {
            assertTrue(OrderStatusMachine.canTransition("PENDING", "CANCELLED"));
        }

        @Test
        @DisplayName("PAID → SHIPPING")
        void paidToShipping() {
            assertTrue(OrderStatusMachine.canTransition("PAID", "SHIPPING"));
        }

        @Test
        @DisplayName("SHIPPING → SHIPPED")
        void shippingToShipped() {
            assertTrue(OrderStatusMachine.canTransition("SHIPPING", "SHIPPED"));
        }

        @Test
        @DisplayName("SHIPPED → RECEIVED")
        void shippedToReceived() {
            assertTrue(OrderStatusMachine.canTransition("SHIPPED", "RECEIVED"));
        }
    }

    @Nested
    @DisplayName("canTransition - 非法流转")
    class CanTransitionInvalid {

        @Test
        @DisplayName("PENDING → SHIPPING (跳步)")
        void pendingToShipping() {
            assertFalse(OrderStatusMachine.canTransition("PENDING", "SHIPPING"));
        }

        @Test
        @DisplayName("PENDING → RECEIVED (跳步)")
        void pendingToReceived() {
            assertFalse(OrderStatusMachine.canTransition("PENDING", "RECEIVED"));
        }

        @Test
        @DisplayName("PAID → PENDING (回退)")
        void paidToPending() {
            assertFalse(OrderStatusMachine.canTransition("PAID", "PENDING"));
        }

        @Test
        @DisplayName("SHIPPED → PAID (回退)")
        void shippedToPaid() {
            assertFalse(OrderStatusMachine.canTransition("SHIPPED", "PAID"));
        }

        @Test
        @DisplayName("CANCELLED → PAID (已取消不能再支付)")
        void cancelledToPaid() {
            assertFalse(OrderStatusMachine.canTransition("CANCELLED", "PAID"));
        }

        @Test
        @DisplayName("RECEIVED → 任何状态 (终态)")
        void receivedToAny() {
            assertFalse(OrderStatusMachine.canTransition("RECEIVED", "PENDING"));
            assertFalse(OrderStatusMachine.canTransition("RECEIVED", "PAID"));
        }
    }

    @Nested
    @DisplayName("canTransition - 边界情况")
    class CanTransitionEdge {

        @Test
        @DisplayName("null fromStatus")
        void nullFrom() {
            assertFalse(OrderStatusMachine.canTransition(null, "PAID"));
        }

        @Test
        @DisplayName("null toStatus")
        void nullTo() {
            assertFalse(OrderStatusMachine.canTransition("PENDING", null));
        }

        @Test
        @DisplayName("未知状态")
        void unknownStatus() {
            assertFalse(OrderStatusMachine.canTransition("UNKNOWN", "PAID"));
            assertFalse(OrderStatusMachine.canTransition("PENDING", "UNKNOWN"));
        }

        @Test
        @DisplayName("相同状态 (PENDING → PENDING)")
        void sameStatus() {
            assertFalse(OrderStatusMachine.canTransition("PENDING", "PENDING"));
        }
    }

    @Nested
    @DisplayName("getTargetStatus - 事件类型映射")
    class GetTargetStatus {

        @Test
        @DisplayName("CREATE_ORDER → PENDING")
        void createOrder() {
            assertEquals("PENDING", OrderStatusMachine.getTargetStatus("CREATE_ORDER"));
        }

        @Test
        @DisplayName("PAY_ORDER → PAID")
        void payOrder() {
            assertEquals("PAID", OrderStatusMachine.getTargetStatus("PAY_ORDER"));
        }

        @Test
        @DisplayName("SHIP_ORDER → SHIPPING")
        void shipOrder() {
            assertEquals("SHIPPING", OrderStatusMachine.getTargetStatus("SHIP_ORDER"));
        }

        @Test
        @DisplayName("RECEIVE_ORDER → RECEIVED")
        void receiveOrder() {
            assertEquals("RECEIVED", OrderStatusMachine.getTargetStatus("RECEIVE_ORDER"));
        }

        @Test
        @DisplayName("CANCEL_ORDER → CANCELLED")
        void cancelOrder() {
            assertEquals("CANCELLED", OrderStatusMachine.getTargetStatus("CANCEL_ORDER"));
        }

        @Test
        @DisplayName("未知事件 → null")
        void unknownEvent() {
            assertNull(OrderStatusMachine.getTargetStatus("UNKNOWN_EVENT"));
        }

        @Test
        @DisplayName("null事件 → null")
        void nullEvent() {
            assertNull(OrderStatusMachine.getTargetStatus(null));
        }
    }

    @Nested
    @DisplayName("完整流转链路")
    class FullFlow {

        @Test
        @DisplayName("完整链路: PENDING → PAID → SHIPPING → SHIPPED → RECEIVED")
        void fullHappyPath() {
            assertTrue(OrderStatusMachine.canTransition("PENDING", "PAID"));
            assertTrue(OrderStatusMachine.canTransition("PAID", "SHIPPING"));
            assertTrue(OrderStatusMachine.canTransition("SHIPPING", "SHIPPED"));
            assertTrue(OrderStatusMachine.canTransition("SHIPPED", "RECEIVED"));
        }

        @Test
        @DisplayName("取消链路: PENDING → CANCELLED")
        void cancelPath() {
            assertTrue(OrderStatusMachine.canTransition("PENDING", "CANCELLED"));
        }

        @Test
        @DisplayName("已支付不可取消")
        void paidCannotCancel() {
            assertFalse(OrderStatusMachine.canTransition("PAID", "CANCELLED"));
        }
    }
}
