# Kafka 顺序消息：分区级顺序 + 版本号过滤

> 先发的消息，后到。怎么办？

这是一个真实的问题：生产者按顺序发了「创建 → 支付 → 发货」，消费者却收到了「支付 → 创建 → 发货」。

RabbitMQ 是怎么解决的，之前的文章已经说过。本文只讲 **Kafka**。

---

## 一、Kafka 顺序消息的本质

Kafka 的顺序保证是 **Partition 级**，不是 Topic 级。

```
Topic: orders (3 partitions)
┌─ Partition 0 ← orderId=1001, 1004, 1007... (key % 3 = 1)
├─ Partition 1 ← orderId=1002, 1005, 1008... (key % 3 = 0)
└─ Partition 2 ← orderId=1003, 1006, 1009... (key % 3 = 2)
```

**Kafka 的保证**：同一 Partition 内的消息，按写入顺序被消费。

也就是说：
- 同一订单的所有消息 → 同一 Partition → 按顺序消费 ✅
- 不同订单的消息 → 可能进入不同 Partition → 互不干扰 ✅

这就是 **局部顺序**——Kafka 只保证同一个 Partition 内的顺序，不保证全局顺序。

> 对业务来说，局部顺序足够了。同一个订单的消息需要有序，不同订单之间没有顺序依赖。

---

## 二、Kafka vs RabbitMQ：两种流派

| 对比项 | Kafka | RabbitMQ |
|--------|-------|----------|
| **顺序粒度** | Partition 级（原生） | 队列级（需配置） |
| **路由机制** | Key hash % partitions | 手动路由 key |
| **消费模型** | 分区单线程（原生） | 需配置单消费者 |
| **网络乱序** | 仍需版本号过滤 | 需版本号过滤 |
| **失败处理** | DLT（Dead Letter Topic） | DLQ（Dead Letter Queue） |

**核心结论**：Kafka 的分区机制比 RabbitMQ 的队列配置更原生，但处理网络乱序的思路完全一致——版本号过滤。

---

## 三、问题一：同一订单如何进入同一分区？

### 生产者：指定 Key

```java
@Autowired
private KafkaTemplate<String, String> kafkaTemplate;

public void sendOrderMessage(int orderId, String eventType, long version) {
    OrderMessage orderMsg = new OrderMessage(orderId, eventType, version);
    String json = JSON.toJSONString(orderMsg);

    // Key = orderId，Kafka 按 hash(key) % partitions 路由到固定分区
    // 同一 orderId → 同一分区 → 消费时按写入顺序
    kafkaTemplate.send("orders", String.valueOf(orderId), json);
}
```

**原理**：Kafka 使用 `hash(key) % partitionCount` 决定消息落入哪个分区。只要 key 不变，分区号就固定。

### 分区数量与并发

分区数量决定了最大并发消费数：

```java
// 创建 Topic 时指定 3 个分区
TopicBuilder.name("orders")
    .partitions(3)
    .replicas(1)
    .build();
```

消费者数量建议 **≤ 分区数量**。如果消费者 > 分区数，多出的消费者会空转。

---

## 四、问题二：网络乱序怎么办？

即使 Kafka Partition 保证了写入顺序，网络层面仍然可能乱序：

```
生产者顺序：CREATE(v=1) → PAY(v=2) → SHIP(v=3)
网络延迟：  CREATE(v=1) → SHIP(v=3) → PAY(v=2)  ← 支付比发货先到
```

消费者按到达顺序处理：先 SHIP(v=3)，找不到订单；再 PAY(v=2)，订单状态不对。

**解决方案：版本号过滤**（与 RabbitMQ 完全一致）

```java
// 消费时，先查数据库中的版本号
Order order = orderMapper.selectById(orderId);

// 消息版本 ≤ 数据库版本 → 消息过时，跳过
if (msgVersion <= order.getVersion()) {
    logger.info("版本号过滤: orderId={}, msgVersion={}, dbVersion={} → 跳过",
            orderId, msgVersion, order.getVersion());
    ack.acknowledge();  // 手动提交 offset
    return;
}
```

---

## 五、完整消费流程

```java
@KafkaListener(topics = "orders", groupId = "kafka-orderly-consumer")
public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
    OrderMessage orderMsg = JSON.parseObject(record.value(), OrderMessage.class);

    // 1. 查订单
    Order order = orderMapper.selectById(orderMsg.getOrderId());

    // 2. 订单不存在 → 创建
    if (order == null) { ... }

    // 3. 版本号过滤（核心！）
    if (msgVersion <= order.getVersion()) {
        ack.acknowledge();  // 跳过，不处理
        return;
    }

    // 4. 状态机校验
    if (!orderStatusMachine.canTransition(order.getStatus(), eventType)) {
        ack.acknowledge();  // 非法流转，跳过
        return;
    }

    // 5. 乐观锁更新
    int updated = orderMapper.updateStatusWithVersion(
        orderId, targetStatus, oldVersion, newVersion);

    // 6. 手动提交 offset
    ack.acknowledge();
}
```

---

## 六、配置要点

### 生产者：幂等性 + acks=all

```yaml
spring:
  kafka:
    producer:
      acks: all              # 所有 ISR 副本确认
      retries: 3             # 失败重试
      enable.idempotence: true  # 幂等性
```

### 消费者：单线程 + 手动提交 offset

```yaml
spring:
  kafka:
    consumer:
      enable.auto.commit: false   # 关闭自动提交
    listener:
      ack-mode: manual_immediate  # 手动立即提交
```

> `concurrency=1` 在 Kafka 里的含义与 RabbitMQ 不同。Kafka 不需要配置 concurrency，因为 **每个 Partition 天然只有一个消费者线程**。你只需要确保消费者数量 ≤ 分区数量。

### 失败处理：DLT

```java
// 最多重试 2 次，间隔 1 秒，之后发到 DLT
DefaultErrorHandler errorHandler = new DefaultErrorHandler(
    new DeadLetterPublishingRecoverer(kafkaTemplate),
    new FixedBackOff(1000L, 2L)
);
```

```java
// DLT 消费者：监控死信
@KafkaListener(topics = "orders.DLT", groupId = "kafka-orderly-dlt-consumer")
public void onDltMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
    logger.error("🪦 DLT 收到死信: {}", record.value());
    // 生产环境：告警通知
    ack.acknowledge();
}
```

---

## 七、核心机制总结

```
生产者                                    Kafka Broker
  │                                          │
  │ send(key=1001, v=1) ──→ Partition 0        │  ← 哈希路由
  │ send(key=1001, v=2) ──→ Partition 0        │  ← 同一 key 同分区
  │ send(key=1001, v=3) ──→ Partition 0        │
  │                                          │
  │                     Consumer (单线程) ←── │  ← 按 offset 顺序拉取
  │                           │               │
  │                           ▼               │
  │                    版本号 v=1 → 通过       │
  │                    版本号 v=2 → 通过       │
  │                    版本号 v=3 → 通过       │
  │                           │               │
  │                           ▼               │
  │                    乐观锁更新成功          │
```

三层保障：

1. **哈希路由**：同一 orderId → 同一分区
2. **单线程消费**：Partition 内顺序保证
3. **版本号过滤**：处理网络乱序

---

## 八、什么时候选 Kafka？

| 场景 | 推荐 |
|------|------|
| 日志收集、流处理、大数据 | Kafka ✅ |
| 业务消息，灵活路由需求 | RabbitMQ |
| 已有 Kafka 集群 | Kafka ✅ |
| 需要严格全局顺序 | 不建议，选型重新评估 |
| 事务消息（半消息） | RocketMQ |

**Kafka 的优势**：分区机制原生支持局部顺序，无需像 RabbitMQ 那样配置单消费者。扩展性好，增加分区即可提升并发消费能力。

**Kafka 的局限**：单分区吞吐有上限（受单分区 IO 限制），需要权衡分区数量与顺序保证。
