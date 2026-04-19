# 消息队列顺序消息实战：保证订单状态机不乱序

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green)
![RocketMQ](https://img.shields.io/badge/RocketMQ-5.1.0-red)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.12-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

## 🌟 项目亮点

- ✅ **双中间件支持**：同时支持 RocketMQ 和 RabbitMQ 两种消息队列
- ✅ **局部顺序保证**：使用哈希路由实现业务维度顺序消息
- ✅ **幂等性保障**：版本号乐观锁防止状态乱序和重复更新
- ✅ **完整状态机**：覆盖订单从创建到收货的完整生命周期
- ✅ **生产级代码**：完善的异常处理和日志记录，可直接用于生产环境

## 项目简介

这是一个多模块实战项目，演示如何使用 RocketMQ 和 RabbitMQ 实现局部顺序消息，保证订单状态机不乱序。

## 核心知识点

1. **哈希路由**：根据订单ID选择队列，保证同一订单的消息进入同一个队列
2. **顺序消费**：使用顺序消费监听器，保证同一个队列只能被一个消费者单线程消费
3. **版本号 + 幂等性**：使用版本号做乐观锁，保证订单状态不会乱序或重复更新

## 技术栈

- Java 17
- Spring Boot 3.2
- RocketMQ 5.1.0
- RabbitMQ 3.12
- MySQL 8.0
- MyBatis 3.0
- JUnit 5

## 项目结构

```
mq-tutorial/
├── pom.xml                              # 父 POM
├── common/                              # 公共模块
│   ├── pom.xml
│   └── src/main/java/com/example/common/
│       ├── entity/Order.java            # 订单实体
│       ├── mapper/OrderMapper.java      # Mapper 接口
│       └── resources/mapper/OrderMapper.xml
├── rocketmq-orderly/                    # RocketMQ 模块
│   ├── pom.xml
│   └── src/main/java/com/example/rocketmq/
│       ├── RocketMQApplication.java     # 启动类
│       ├── producer/OrderProducer.java  # 生产者
│       ├── consumer/OrderConsumer.java  # 消费者
│       ├── config/RocketMQConfig.java  # 配置类
│       └── resources/application.yml
├── rabbitmq-orderly/                    # RabbitMQ 模块
│   ├── pom.xml
│   └── src/main/java/com/example/rabbitmq/
│       ├── RabbitMQApplication.java     # 启动类
│       ├── producer/OrderProducer.java  # 生产者
│       ├── consumer/OrderConsumer.java  # 消费者
│       ├── config/RabbitMQConfig.java   # 配置类
│       └── resources/application.yml
├── docker/                              # Docker 配置
│   ├── docker-compose-rocketmq.yml     # RocketMQ 环境配置
│   └── docker-compose-rabbitmq.yml     # RabbitMQ 环境配置
├── sql/
│   └── init.sql                         # 数据库初始化脚本
└── conf/
    └── broker.conf                      # RocketMQ Broker 配置
```

## 快速开始

### 1. 选择消息中间件

项目支持 RocketMQ 和 RabbitMQ，你可以根据需求选择：

- **RocketMQ**：适合大规模、高并发的消息场景
- **RabbitMQ**：适合灵活的路由规则和简单的顺序消息场景

### 2. 启动环境

**RocketMQ 环境：**
```bash
cd docker
docker-compose -f docker-compose-rocketmq.yml up -d
```

**RabbitMQ 环境：**
```bash
cd docker
docker-compose -f docker-compose-rabbitmq.yml up -d
```

### 3. 初始化数据库

数据库会在 Docker 容器启动时自动初始化，无需手动执行。

### 4. 运行应用

**RocketMQ 模块：**
```bash
cd rocketmq-orderly
mvn spring-boot:run
```

**RabbitMQ 模块：**
```bash
cd rabbitmq-orderly
mvn spring-boot:run
```

## 核心代码

### 1. RocketMQ：哈希路由 + 顺序消费

```java
// 生产者：哈希路由
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

// 消费者：顺序消费
@RocketMQMessageListener(
    topic = "order-topic",
    consumerGroup = "order-consumer-group",
    consumeMode = ConsumeMode.ORDERLY
)
public class OrderConsumer implements RocketMQListener<OrderMessage> {
    @Override
    public void onMessage(OrderMessage message) {
        processOrderMessage(message);
    }
}
```

### 2. RabbitMQ：单队列 + 顺序消费

```java
// 生产者：发送到指定队列
rabbitTemplate.convertAndSend(
    "order-exchange",
    "order-routing-key",
    orderMessage
);

// 消费者：单线程顺序消费
@RabbitListener(queues = "order-queue")
public void handleOrderMessage(OrderMessage message) {
    processOrderMessage(message);
}
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

发送多个订单消息，验证同一订单的状态按预期顺序更新。

### 2. 验证幂等性

发送相同的订单消息，验证订单状态只更新一次。

## 💡 实战案例

### 案例：订单状态流转完整演示

以下演示如何使用本项目实现一个完整的订单状态流转，从创建到收货。

#### 步骤 1：启动 RocketMQ 环境

```bash
cd docker
docker-compose -f docker-compose-rocketmq.yml up -d

# 等待服务启动
sleep 30

# 验证 RocketMQ 是否正常运行
docker ps | grep rocketmq
```

**预期输出：**
```
rocketmq-namesrv   Up 30s
rocketmq-broker    Up 30s
rocketmq-console   Up 30s
rocketmq-mysql     Up 30s
```

#### 步骤 2：启动 RocketMQ 应用

```bash
cd rocketmq-orderly
mvn spring-boot:run
```

**预期输出：**
```
Started RocketMQApplication in 3.5 seconds
```

#### 步骤 3：测试订单状态流转

创建测试类或使用 Postman 发送以下请求：

```java
// 发送订单创建消息
producer.sendOrderMessage(1001, "CREATE_ORDER", 1);

// 发送订单支付消息
producer.sendOrderMessage(1001, "PAY_ORDER", 2);

// 发送订单发货消息
producer.sendOrderMessage(1001, "SHIP_ORDER", 3);

// 发送订单收货消息
producer.sendOrderMessage(1001, "RECEIVE_ORDER", 4);
```

#### 步骤 4：验证订单状态

```bash
# 登录 MySQL 容器
docker exec -it rocketmq-mysql mysql -uroot -popc.tang2026

# 查询订单状态
USE rocketmq_orderly;
SELECT * FROM `order` WHERE id = 1001;
```

**预期输出：**
```
+------+---------+---------+---------------------+---------------------+
| id   | status  | version | create_time         | update_time         |
+------+---------+---------+---------------------+---------------------+
| 1001 | RECEIVED| 4       | 2026-04-18 18:00:00| 2026-04-18 18:01:00|
+------+---------+---------+---------------------+---------------------+
```

#### 步骤 5：测试顺序性保证

快速发送相同订单的多个事件：

```java
// 快速连续发送
producer.sendOrderMessage(1002, "CREATE_ORDER", 1);
producer.sendOrderMessage(1002, "PAY_ORDER", 2);
producer.sendOrderMessage(1002, "SHIP_ORDER", 3);
producer.sendOrderMessage(1002, "RECEIVE_ORDER", 4);
```

**预期结果：** 订单状态会按照 CREATE_ORDER → PAY_ORDER → SHIP_ORDER → RECEIVE_ORDER 的顺序更新，不会出现乱序。

#### 步骤 6：测试幂等性

重复发送相同的事件：

```java
// 发送支付消息两次
producer.sendOrderMessage(1003, "CREATE_ORDER", 1);
producer.sendOrderMessage(1003, "PAY_ORDER", 2);
producer.sendOrderMessage(1003, "PAY_ORDER", 2); // 重复发送
producer.sendOrderMessage(1003, "SHIP_ORDER", 3);
```

**预期结果：** 第二次 PAY_ORDER 不会改变订单状态，版本号保持不变，保证幂等性。

#### 步骤 7：查看 RocketMQ 控制台

访问 RocketMQ 控制台：http://localhost:8081

- 查看 OrderTopic 的消息数量
- 查看消费者消费情况
- 查看消息轨迹

**预期界面：**
- Topic: OrderTopic
- 消息总数：12
- 消费者组：order-consumer
- 消费进度：100%

#### 步骤 8：验证数据库数据

```bash
# 查询所有订单
SELECT * FROM `order` ORDER BY id;
```

**预期输出：**
```
+------+---------+---------+---------------------+
| id   | status  | version | update_time         |
+------+---------+---------+---------------------+
| 1001 | RECEIVED| 4       | 2026-04-18 18:01:00|
| 1002 | RECEIVED| 4       | 2026-04-18 18:02:00|
| 1003 | SHIPPING| 3       | 2026-04-18 18:03:00|
+------+---------+---------+---------------------+
```

### 清理环境

```bash
# 停止并删除 Docker 容器
cd docker
docker-compose -f docker-compose-rocketmq.yml down

# 清理数据卷（可选）
docker volume prune
```

**总结：**
- ✅ 成功演示了订单状态的完整流转
- ✅ 验证了消息的顺序性保证
- ✅ 验证了幂等性保护机制
- ✅ 展示了生产环境的使用方式

## 常见问题

### Q1: 为什么不做全局有序？

全局有序必须单消费者，性能差，无法横向扩展。在高并发场景下，我们只做业务维度局部有序。

### Q2: 如何保证同一订单的消息进入同一个队列？

RocketMQ：使用哈希路由，根据订单ID选择队列：`hash(orderId) % queueCount`
RabbitMQ：使用单队列，所有消息进入同一个队列

### Q3: 如何保证队列被顺序消费？

RocketMQ：使用 `ConsumeMode.ORDERLY` 模式
RabbitMQ：使用单消费者监听队列

### Q4: 如何防止订单状态乱序？

使用版本号做乐观锁，只有当版本号一致时才更新。

## 参考文章

- [RocketMQ顺序消息实战：保证订单状态机不乱序的完整方案](https://mp.weixin.qq.com/s/JHXqx91hY5A8a1Zug_ayLg)
- [Apache RocketMQ GitHub](https://github.com/apache/rocketmq)
- [RabbitMQ 官方文档](https://www.rabbitmq.com/documentation.html)

## 许可证

MIT License
