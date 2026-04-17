# RocketMQ顺序消息实战：保证订单状态机不乱序的完整方案

## 项目简介

这是一个实战项目，演示如何使用RocketMQ实现局部顺序消息，保证订单状态机不乱序。

## 核心知识点

1. **哈希路由**：根据订单ID选择MessageQueue，保证同一订单的消息进入同一个MessageQueue
2. **MessageListenerOrderly**：使用顺序消费监听器，保证同一个MessageQueue只能被一个消费者单线程消费
3. **版本号 + 幂等性**：使用版本号做乐观锁，保证订单状态不会乱序或重复更新

## 技术栈

- Java 17
- Spring Boot 3.2
- RocketMQ 5.1.0
- MySQL 8.0
- MyBatis 3.0
- JUnit 5

## 项目结构

```
src/
├── main/
│   ├── java/com/example/
│   │   ├── producer/
│   │   │   └── OrderProducer.java      # 生产者
│   │   ├── consumer/
│   │   │   └── OrderConsumer.java      # 消费者
│   │   ├── entity/
│   │   │   └── Order.java              # 订单实体
│   │   ├── mapper/
│   │   │   ├── OrderMapper.java        # Mapper接口
│   │   │   └── OrderMapper.xml         # Mapper XML
│   │   └── OrderApplication.java       # 启动类
│   └── resources/
│       ├── application.yml              # 配置文件
│       └── docker-compose.yml          # Docker Compose配置
└── test/
    └── java/com/example/
        └── OrderConsumerTest.java       # UT测试
```

## 快速开始

### 1. 启动RocketMQ和MySQL

```bash
docker-compose up -d
```

### 2. 创建数据库和表

```bash
docker exec -it rocketmq-mysql mysql -uroot -proot

CREATE DATABASE rocketmq_orderly;

USE rocketmq_orderly;

CREATE TABLE `order` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `status` varchar(20) NOT NULL COMMENT '订单状态',
  `version` bigint(20) NOT NULL DEFAULT '0' COMMENT '版本号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';
```

### 3. 运行测试

```bash
mvn test
```

### 4. 手动测试

**启动消费者：**
```bash
mvn spring-boot:run
```

**运行生产者：**
```bash
mvn exec:java -Dexec.mainClass="com.example.producer.OrderProducer"
```

## 核心代码

### 1. 生产者：哈希路由

```java
SendResult sendResult = producer.send(
    message,
    new MessageQueueSelector() {
        @Override
        public MessageQueue select(
            List<MessageQueue> mqs,
            Message msg,
            Object arg
        ) {
            Integer orderId = (Integer) arg;
            int index = Math.abs(orderId) % mqs.size();
            return mqs.get(index);
        }
    },
    orderId
);
```

### 2. 消费者：MessageListenerOrderly

```java
consumer.registerMessageListener(
    new MessageListenerOrderly() {
        @Override
        public ConsumeOrderlyStatus consumeMessage(
            List<MessageExt> msgs,
            ConsumeOrderlyContext context
        ) {
            for (MessageExt msg : msgs) {
                processOrderMessage(msg);
            }
            return ConsumeOrderlyStatus.SUCCESS;
        }
    }
);
```

### 3. 业务逻辑：版本号 + 幂等性

```java
int rows = orderMapper.updateStatusWithVersion(
    orderId,
    targetStatus,
    order.getVersion(),
    newVersion
);

if (rows == 0) {
    log.warn("版本冲突，更新失败: orderId={}, currentVersion={}", 
             orderId, order.getVersion());
    return;
}
```

## 测试验证

### 1. 验证顺序性

```java
@Test
public void testOrderSequence() throws Exception {
    // 发送10个订单消息
    for (int i = 0; i < 10; i++) {
        producer.sendOrderMessage(101 + i);
    }

    // 等待消费
    Thread.sleep(10000);

    // 验证订单状态
    Order order = orderMapper.selectById(101);
    assertEquals("PAID", order.getStatus());
}
```

### 2. 验证幂等性

```java
@Test
public void testIdempotency() throws Exception {
    // 发送相同的订单消息两次
    producer.sendOrderMessage(101);
    producer.sendOrderMessage(101);

    // 等待消费
    Thread.sleep(5000);

    // 验证订单状态只更新了一次
    Order order = orderMapper.selectById(101);
    assertEquals("PAID", order.getStatus());
}
```

## 常见问题

### Q1: 为什么不做全局有序？

全局有序必须单消费者，性能差，无法横向扩展，无法应对高并发场景。在高并发场景下，我们只做业务维度局部有序。

### Q2: 如何保证同一订单的消息进入同一个MessageQueue？

使用哈希路由，根据订单ID选择MessageQueue：`hash(orderId) % queueCount`。

### Q3: 如何保证同一个MessageQueue只能被一个消费者消费？

使用`MessageListenerOrderly`接口，RocketMQ会自动分配MessageQueue给消费者，保证同一个MessageQueue只能被一个消费者单线程消费。

### Q4: 如何防止订单状态乱序？

使用版本号做乐观锁，只有当版本号一致时才更新。版本号不一致说明被其他线程更新过，可能是因为乱序或重复消息。

## 参考文章

- [RocketMQ顺序消息实战：保证订单状态机不乱序的完整方案](https://mp.weixin.qq.com/s/JHXqx91hY5A8a1Zug_ayLg)
- [Apache RocketMQ GitHub](https://github.com/apache/rocketmq)

## 许可证

MIT License
