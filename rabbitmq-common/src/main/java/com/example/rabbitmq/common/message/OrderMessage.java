package com.example.rabbitmq.common.message;

import com.alibaba.fastjson2.JSON;
import org.springframework.amqp.core.Message;

/**
 * 订单消息统一模型
 *
 * 替代各模块中手动 JSONObject 解析，统一消息反序列化
 */
public class OrderMessage {

    private int orderId;
    private String eventType;
    private long version;

    public OrderMessage() {}

    public OrderMessage(int orderId, String eventType, long version) {
        this.orderId = orderId;
        this.eventType = eventType;
        this.version = version;
    }

    /**
     * 从RabbitMQ Message反序列化
     */
    public static OrderMessage fromMessage(Message message) {
        String body = new String(message.getBody());
        return JSON.parseObject(body, OrderMessage.class);
    }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    @Override
    public String toString() {
        return "OrderMessage{orderId=" + orderId + ", eventType='" + eventType +
               "', version=" + version + "}";
    }
}
