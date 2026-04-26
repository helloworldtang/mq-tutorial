package com.example.rabbitmq.common.statemachine;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 订单状态机
 *
 * 定义合法的状态流转规则，所有RabbitMQ模块共用
 *
 * 合法流转：
 * PENDING → PAID | CANCELLED
 * PAID    → SHIPPING
 * SHIPPING → SHIPPED
 * SHIPPED → RECEIVED
 */
public final class OrderStatusMachine {

    private OrderStatusMachine() {}

    private static final Set<String> FROM_PENDING = new HashSet<>(Arrays.asList("PAID", "CANCELLED"));
    private static final Set<String> FROM_PAID = new HashSet<>(Arrays.asList("SHIPPING"));
    private static final Set<String> FROM_SHIPPING = new HashSet<>(Arrays.asList("SHIPPED"));
    private static final Set<String> FROM_SHIPPED = new HashSet<>(Arrays.asList("RECEIVED"));

    /**
     * 检查状态流转是否合法
     */
    public static boolean canTransition(String fromStatus, String toStatus) {
        if (fromStatus == null || toStatus == null) {
            return false;
        }
        switch (fromStatus) {
            case "PENDING":
                return FROM_PENDING.contains(toStatus);
            case "PAID":
                return FROM_PAID.contains(toStatus);
            case "SHIPPING":
                return FROM_SHIPPING.contains(toStatus);
            case "SHIPPED":
                return FROM_SHIPPED.contains(toStatus);
            default:
                return false;
        }
    }

    /**
     * 根据事件类型获取目标状态
     */
    public static String getTargetStatus(String eventType) {
        if (eventType == null) {
            return null;
        }
        switch (eventType) {
            case "CREATE_ORDER":
                return "PENDING";
            case "PAY_ORDER":
                return "PAID";
            case "SHIP_ORDER":
                return "SHIPPING";
            case "RECEIVE_ORDER":
                return "RECEIVED";
            case "CANCEL_ORDER":
                return "CANCELLED";
            default:
                return null;
        }
    }
}
