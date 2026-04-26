package com.example.rabbitmq.common.constant;

/**
 * RabbitMQ公共常量
 */
public final class MQConstants {

    private MQConstants() {}

    /** 默认队列数量 */
    public static final int DEFAULT_QUEUE_COUNT = 3;

    /** 死信交换机后缀 */
    public static final String DLQ_EXCHANGE_SUFFIX = "-dlq-exchange";

    /** 死信队列后缀 */
    public static final String DLQ_SUFFIX = ".dlq";

    /** 死信路由键前缀 */
    public static final String DLQ_ROUTING_KEY_PREFIX = "dlq.";
}
