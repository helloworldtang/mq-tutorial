package com.example.kafka.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;

import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 配置类
 *
 * 核心设计：
 * 1. 创建 3 个分区（对应 rabbitmq 的 3 个队列），实现局部顺序
 * 2. 配置 Producer 幂等性 + acks=all，保证消息不丢失
 * 3. 配置 Consumer 单线程消费（concurrency=1），保证分区顺序
 * 4. 配置 DLQ（Dead Letter Topic），失败消息进入 DLT
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConfig.class);

    private static final String TOPIC = "orders";
    private static final int PARTITION_COUNT = 3;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:kafka-orderly-consumer}")
    private String groupId;

    // ==================== Admin ====================

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        KafkaAdmin admin = new KafkaAdmin(configs);
        logger.info("Kafka Admin 创建完成, bootstrapServers={}", bootstrapServers);
        return admin;
    }

    @Bean
    public NewTopic orderTopic() {
        return TopicBuilder.name(TOPIC)
                .partitions(PARTITION_COUNT)
                .replicas(1)
                .config("retention.ms", String.valueOf(7 * 24 * 3600 * 1000L))
                .build();
    }

    @Bean
    public NewTopic orderTopicDlt() {
        return TopicBuilder.name(TOPIC + ".DLT")
                .partitions(1)
                .replicas(1)
                .config("retention.ms", String.valueOf(7 * 24 * 3600 * 1000L))
                .build();
    }

    // ==================== Producer ====================

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // 幂等性
        configs.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configs.put(ProducerConfig.ACKS_CONFIG, "all");
        configs.put(ProducerConfig.RETRIES_CONFIG, 3);
        // 保证顺序：同一 key 的消息按顺序发送
        configs.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        ProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(configs);
        logger.info("Kafka ProducerFactory 创建完成, idempotence=true, acks=all");
        return factory;
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        KafkaTemplate<String, String> template = new KafkaTemplate<>(producerFactory());
        template.setObservationEnabled(true);
        logger.info("KafkaTemplate 创建完成");
        return template;
    }

    // ==================== Consumer ====================

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // 关闭自动提交，手动提交 offset
        configs.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // 单线程消费，保证分区顺序（Kafka 不像 RabbitMQ 可以多消费者争抢同一队列）
        // concurrency=1 意味着每个 partition 只有一个线程消费
        // 注意：concurrency 应该 <= partition 数量，否则多出来的消费者会空转
        configs.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        ConsumerFactory<String, String> factory = new DefaultKafkaConsumerFactory<>(configs);
        logger.info("Kafka ConsumerFactory 创建完成, groupId={}, autoCommit=false", groupId);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // 单线程消费，分区级顺序的核心
        factory.setConcurrency(1);
        // 手动提交 offset，确保消息处理成功后才提交
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        // 配置错误处理器：最多重试 2 次，间隔 1 秒，之后发到 DLT
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(kafkaTemplate),
                new FixedBackOff(1000L, 2L)
        );
        factory.setCommonErrorHandler(errorHandler);
        logger.info("KafkaListenerContainerFactory 创建完成, concurrency=1, ackMode=MANUAL_IMMEDIATE, maxRetries=2");
        return factory;
    }
}
