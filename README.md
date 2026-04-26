# 消息队列顺序消息实战：保证订单状态机不乱序

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green)
![RocketMQ](https://img.shields.io/badge/RocketMQ-5.1.0-red)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.12-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

## 项目简介

多模块实战项目，演示如何在 RocketMQ 和 RabbitMQ 中实现**业务维度局部顺序消息**，保证订单状态机不会因并发消费而乱序。

**订单状态流转链路：**

```
PENDING → PAID → SHIPPING → SHIPPED → RECEIVED
    ↓
CANCELLED (仅 PENDING 可取消)
```

**全局顺序 vs 局部顺序：**

| 类型 | 说明 | 性能 | 适用场景 |
|------|------|------|----------|
| 全局顺序 | 所有消息严格按时间顺序处理 | 差（单消费者） | 基本不可用 |
| 局部顺序 | 同一业务KEY的消息有序，不同KEY可并行 | 好 | ✅ 生产推荐 |

本项目采用**局部顺序**方案，同一订单的消息有序，不同订单可并行处理。

## 项目结构

```
mq-tutorial/
├── pom.xml
├── common/                              # 公共模块（实体、Mapper）
├── rabbitmq-common/                     # RabbitMQ 公共模块
│   └── src/main/java/com/example/rabbitmq/common/
│       ├── constant/MQConstants.java     # 常量（队列数、死信队列命名）
│       ├── message/OrderMessage.java    # 统一消息模型
│       ├── statemachine/OrderStatusMachine.java  # 状态机
│       └── consumer/AbstractOrderConsumer.java   # 消费者抽象基类
├── rocketmq-orderly/                    # RocketMQ 模块
├── rabbitmq-orderly/                    # RabbitMQ · 基础方案
├── rabbitmq-orderly-sac/               # RabbitMQ · SAC 方案
├── rabbitmq-orderly-dlock/             # RabbitMQ · 分布式锁方案
├── rabbitmq-orderly-exclusive/         # RabbitMQ · Exclusive Consumer 方案
├── docker/
│   ├── docker-compose-rocketmq.yml
│   └── docker-compose-rabbitmq.yml     # 含 MySQL + Redis
└── sql/init.sql
```

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 运行环境 |
| Spring Boot | 3.2 | 基础框架 |
| RocketMQ | 5.1.0 | 消息队列 |
| RabbitMQ | 3.12 | 消息队列 |
| MySQL | 8.0 | 持久化订单数据 |
| MyBatis | 3.0 | ORM 框架 |
| Redisson | 3.25 | 分布式锁（dlock 模块） |
| JUnit 5 | 5.10 | 单元测试 |

## 快速开始

### 1. 启动依赖环境

```bash
cd docker
docker-compose -f docker-compose-rabbitmq.yml up -d

# 验证服务状态
docker ps | grep -E "rabbitmq|mysql|redis"
```

访问 RabbitMQ 管理界面：http://localhost:15672（用户名 admin，密码 admin2026）

### 2. 初始化数据库

表结构在 `sql/init.sql`，Docker 启动时自动初始化。

### 3. 编译项目

```bash
mvn clean install -DskipTests
```

### 4. 运行测试

```bash
# 运行所有 RabbitMQ 模块测试
mvn test -pl rabbitmq-orderly,rabbitmq-orderly-sac,rabbitmq-orderly-dlock,rabbitmq-orderly-exclusive

# 运行指定模块测试
mvn test -pl rabbitmq-orderly-sac
```

---

## 核心机制（所有模块共享）

### 1. 哈希路由

```java
// 同一订单的所有消息进入同一个队列
String routingKey = "order." + (orderId % 3);
// orderId=1001 → routingKey="order.1" → OrderQueue1
// orderId=1002 → routingKey="order.2" → OrderQueue2
// orderId=1003 → routingKey="order.0" → OrderQueue0
rabbitTemplate.convertAndSend(exchange, routingKey, message);
```

### 2. 版本号乐观锁（防乱序 + 幂等性）

```java
// 数据库更新带版本号条件
UPDATE orders SET status='PAID', version=2
WHERE id=? AND version=1;

// 新版本 ≤ 当前版本 → 消息过期，跳过处理
if (newVersion <= currentVersion) {
    logger.warn("消息过时，跳过: orderId={}, msgVersion={}, dbVersion={}",
                orderId, newVersion, currentVersion);
    return;
}
```

### 3. 状态机校验

```java
// 只允许合法流转，拒绝乱序操作
PENDING  → PAID / CANCELLED
PAID     → SHIPPING
SHIPPING → SHIPPED
SHIPPED  → RECEIVED

// 非法流转示例：PENDING → RECEIVED（跳步）→ 被拒绝
```

### 4. 死信队列（DLQ）

```
消费失败 (throw RuntimeException)
        ↓
消息进入 DLQ（带原始消息内容）
        ↓
运维排查 + 人工补偿
```

### 5. Publisher Confirm

```java
// 消息到达 Broker 确认
template.setConfirmCallback((correlationData, ack, cause) -> {
    if (!ack) {
        logger.error("消息发送失败: {}", cause);
    }
});

// 消息无法路由（无匹配队列）
template.setReturnsCallback(returned -> {
    logger.error("消息无法路由: exchange={}, routingKey={}",
                 returned.getExchange(), returned.getRoutingKey());
});
```

---

## 四种 RabbitMQ 方案对比

### 方案一：基础方案 `rabbitmq-orderly`

**原理：** 单队列单消费者，通过 Spring AMQP 默认单线程消费保证队列内顺序。

```java
@RabbitListener(queues = "OrderQueue0")
public void onMessageQueue0(Message message) {
    processOrderMessage(message, "OrderQueue0");
}
```

**优点：**
- 配置最简单，无额外依赖
- 性能好，适合快速上手

**缺点：**
- 多进程部署时，每个队列只能有一个消费者实例，否则顺序被破坏
- 进程重启期间消息积压，消费者恢复后才能继续

**适用场景：** 单机部署，或每个队列只需部署一个消费者实例的环境。

---

### 方案二：SAC 方案 `rabbitmq-orderly-sac`

**原理：** 队列声明时添加 `x-single-active-consumer=true`，RabbitMQ 3.12+ 原生支持。

```java
// 队列创建时指定
Map<String, Object> args = new HashMap<>();
args.put("x-single-active-consumer", true);  // 核心配置
args.put("x-dead-letter-exchange", "sac-order-exchange-dlx");
args.put("x-dead-letter-routing-key", "dlq.order.0");
return new Queue("SacOrderQueue0", true, false, false, args);
```

**优点：**
- Broker 原生保证，多进程部署时只有一个消费者活跃，其他standby
- 活跃消费者断开时， standby 消费者自动接管，**消息不丢失**
- 配置简单，不需要额外依赖
- 生产环境推荐方案

**缺点：**
- 需要 RabbitMQ 3.12+（较新版本）
- standby 消费者不会消费，只是保活

**适用场景：** 多进程部署的生产环境，**首选方案**。

---

### 方案三：分布式锁方案 `rabbitmq-orderly-dlock`

**原理：** 消费消息时按 `orderId` 获取 Redisson 分布式锁，保证同一订单在多进程间串行处理。

```java
@Override
protected void executeWithStrategy(OrderMessage orderMsg, String queueName) {
    String lockKey = "order:lock:" + orderMsg.getOrderId();
    RLock lock = redissonClient.getLock(lockKey);

    try {
        boolean acquired = lock.tryLock(10, 30, TimeUnit.SECONDS);
        if (!acquired) {
            throw new RuntimeException("获取分布式锁失败: orderId=" + orderMsg.getOrderId());
        }
        super.executeWithStrategy(orderMsg, queueName);  // 锁内执行业务
    } finally {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

**优点：**
- 灵活性最高，锁逻辑在代码层，可自由扩展
- 适合跨多种 MQ 技术栈（RabbitMQ / RocketMQ / Kafka）共用同一套锁机制
- 可精细控制锁粒度（按 orderId）

**缺点：**
- 依赖 Redisson（额外服务：Redis）
- 每次消费都要加锁，有少量性能开销
- 锁获取失败需要重试，增加复杂度

**适用场景：** 需要跨多个 MQ 技术栈统一顺序保障，或业务逻辑需要跨服务协调的场景。

---

### 方案四：Exclusive Consumer 方案 `rabbitmq-orderly-exclusive`

**原理：** `@RabbitListener` 设置 `concurrency="1"` + `exclusive="true"`，Broker 保证同时只有一个消费者能消费该队列。

```java
@RabbitListener(queues = "ExclusiveOrderQueue0", concurrency = "1", exclusive = true)
public void onMessageQueue0(Message message) {
    processOrderMessage(message, "ExclusiveOrderQueue0");
}
```

**优点：**
- Broker 原生保证，配置简单
- 不需要额外依赖

**缺点：**
- **独占期间其他实例完全无法消费**，如果消费者断开连接，该队列在消费者重新连接之前处于无消费者状态，消息积压无法被处理
- 与 SAC 方案相比，SAC 有 standby 机制，Exclusive 没有
- 多进程部署时，其他进程无法参与消费，资源利用率低

**适用场景：** 备选方案，当 SAC 不可用且无法使用分布式锁时的折中选择。**不推荐作为生产首选**。

---

### 选型建议

| 场景 | 推荐方案 |
|------|----------|
| 单机部署 | 基础方案 |
| 多进程部署 | **SAC 方案（首选）** |
| 需跨 MQ 技术栈 | 分布式锁方案 |
| SAC 不可用时的备选 | Exclusive Consumer |

> 💡 **特别说明：** 本项目为 Tutorial（教程），四种方案均已实现并附有完整测试。选择哪种方案由业务场景决定，本项目仅提供参考实现。

---

## 测试用例说明

每个模块均包含以下测试：

| 测试用例 | 验证内容 |
|----------|----------|
| `testSingleOrderSequentialMessage` | 单订单顺序消息：CREATE → PAY → SHIP，最终状态 SHIPPING |
| `testMultipleOrderConcurrentMessage` | 多订单并发：3个订单并发发送，最终状态均为 SHIPPING |
| `testVersionFilter` | 版本号过滤：高版本消息先到，低版本消息后到，低版本被过滤 |
| `testIdempotency` | 幂等性：同版本号消息重复发送，状态只更新一次 |
| `testInvalidStatusTransition` | 非法流转拒绝：跳步操作（如 PENDING → RECEIVED）被拒绝 |

运行测试：
```bash
cd rabbitmq-orderly
mvn test
```

---

## RabbitMQ 公共模块 `rabbitmq-common`

所有 RabbitMQ 模块共享以下公共代码：

```java
// 1. 统一消息模型
OrderMessage msg = new OrderMessage(orderId, eventType, version);

// 2. 状态机
OrderStatusMachine.canTransition("PENDING", "PAID");  // true
OrderStatusMachine.getTargetStatus("PAY_ORDER");       // "PAID"

// 3. 消费者抽象基类（模板方法模式）
public class OrderConsumer extends AbstractOrderConsumer {
    // 子类只需覆盖 executeWithStrategy() 即可扩展（如加分布式锁）
    @Override
    protected void executeWithStrategy(OrderMessage orderMsg, String queueName) {
        // 自定义策略，如加锁
    }
}
```

---

## 常见问题

**Q: 为什么不做全局有序？**

全局有序必须单消费者单队列，无法横向扩展，在高并发场景下性能差到不可接受。生产环境一律采用业务维度局部有序。

**Q: 如何保证同一订单进入同一队列？**

`orderId % 3` 作为路由键，同一订单ID始终映射到同一个队列。

**Q: 网络乱序怎么办？**

版本号乐观锁：新消息 version ≤ 数据库当前 version → 跳过处理（消息过期）。

**Q: 如何防止重复消费？**

两个层面：① 版本号相同则乐观锁 UPDATE 影响行数为0；② 数据库更新本身带 version 条件，天然幂等。

---

## 参考资料

- [RabbitMQ x-single-active-consumer](https://www.rabbitmq.com/consumers.html#single-active-consumers)
- [RabbitMQ Dead Letter Exchanges](https://www.rabbitmq.com/dlx.html)
- [Redisson 分布式锁](https://github.com/redisson/redisson)
- [RocketMQ 顺序消息](https://rocketmq.apache.org/docs/4.x/java/02ordermessage)

## 许可证

MIT License
