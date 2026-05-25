# 成员 B 代码实现自审与工作总结

## 1. 本轮工作范围

本轮工作目标是根据 `docs/` 中已冻结的接口契约、数据库设计、Redis Key 设计、MQ 消息契约和秒杀订单实现规格，完成成员 B 负责的后端核心链路第一版代码落地。

涉及模块：

1. `common`
2. `product-seckill-service`
3. `order-service`

本轮没有修改成员 A 负责的 Gateway 路由、JWT 鉴权和权限规则，也没有修改成员 C 负责的 AI 咨询、推送服务和测试页面。

## 2. 已完成工作总结

### 2.1 common 公共能力

已完成：

1. 为 `BusinessException` 增加基于 `ErrorCode` 的构造方式。
2. 为全局异常处理补充参数错误、请求体格式错误、类型转换错误等统一返回。
3. 补充 `common` 模块依赖，保证公共异常处理可独立编译。

作用：

1. 秒杀库存不足、重复抢购、活动未开始、活动已结束、MQ 投递失败等业务错误可以按统一响应格式返回。
2. 避免接口直接暴露 Java 原始异常。

### 2.2 商品与库存接口

已完成：

1. 替换 `ProductController` 中的 mock 返回。
2. 实现 `POST /api/products` 商品创建。
3. 实现 `GET /api/products` 商品分页查询。
4. 实现 `GET /api/products/{productId}` 商品详情查询。
5. 实现 `PUT /api/products/{productId}/stock` 库存设置。
6. 新增 `Product`、`ProductStock` 实体和 Repository。
7. 新增商品、库存相关 DTO。

符合文档点：

1. 商品状态使用 `ON_SALE` / `OFF_SALE`。
2. 库存写入 `product_stocks` 表，而不是内存 mock。
3. 分页返回使用统一 `PageResponse`。

### 2.3 秒杀活动与 Redis 链路

已完成：

1. 替换 `SeckillController` 中的 mock 返回。
2. 实现 `POST /api/seckill/activities` 创建秒杀活动。
3. 实现 `GET /api/seckill/activities/{activityId}` 查询活动详情。
4. 实现 `POST /api/seckill/activities/{activityId}/orders` 发起秒杀。
5. 实现 `GET /api/seckill/activities/{activityId}/result` 查询秒杀结果。
6. 新增 `SeckillActivity` 实体和 Repository。
7. 新增 Redis key 工具类。
8. 实现活动创建后的 Redis 预热。
9. 实现 Redis Lua 原子扣减脚本。

Redis Key 遵守：

```text
seckill:stock:{activityId}
seckill:user:{activityId}:{userId}
seckill:result:{activityId}:{userId}
seckill:activity:{activityId}
```

Lua 脚本已覆盖：

1. 活动是否存在。
2. 活动时间是否允许。
3. 用户是否重复参与。
4. 库存是否充足。
5. 原子扣减库存。
6. 写入用户参与标记。
7. 写入 `QUEUEING` 秒杀结果。

### 2.4 RabbitMQ 异步下单

已完成：

1. 新增 RabbitMQ 配置。
2. 声明 `seckill.order.exchange`。
3. 声明 `seckill.order.create.queue`。
4. 声明 `seckill.order.create.dlq`。
5. 使用 routing key `seckill.order.create`。
6. 新增 `OrderCreateMessage`，字段遵守 `docs/MQ消息契约.md`。
7. 秒杀扣减成功后投递 MQ。
8. 秒杀接口返回 `queued`，不等待订单落库。

消息体字段：

```json
{
  "messageId": "uuid",
  "eventType": "SECKILL_ORDER_CREATE",
  "activityId": 1,
  "userId": 10001,
  "productId": 20001,
  "quantity": 1,
  "seckillPrice": 99.9,
  "orderNo": "ORD202605250001",
  "createdAt": "2026-05-25T10:00:00"
}
```

### 2.5 订单消费与落库

已完成：

1. 替换 `OrderController` 中的 mock 返回。
2. 实现 `GET /api/orders` 用户订单分页查询。
3. 实现 `GET /api/orders/{orderId}` 用户订单详情查询。
4. 实现 `GET /api/orders/admin` 管理员订单分页查询。
5. 新增 `Order`、`OrderItem`、`MqMessageLog` 实体和 Repository。
6. 新增 RabbitMQ 消费者 `OrderMessageConsumer`。
7. 消费消息后写入 `orders`。
8. 消费消息后写入 `order_items`。
9. 消费消息后写入并更新 `mq_message_logs`。
10. 消费成功后回写 Redis 秒杀结果为 `CREATED`。

幂等策略：

1. `messageId` 对应 `mq_message_logs.message_id`。
2. `orderNo` 对应 `orders.order_no`。
3. `activityId + userId` 对应 `orders(user_id, activity_id)`。
4. `businessKey = SECKILL_ORDER:{activityId}:{userId}`。

### 2.6 数据库初始化

已完成：

1. 为 `product-seckill-service` 增加 `schema.sql`。
2. 为 `order-service` 增加 `schema.sql`。
3. 覆盖 B 负责的核心表：
   - `products`
   - `product_stocks`
   - `seckill_activities`
   - `orders`
   - `order_items`
   - `mq_message_logs`

关键唯一约束已包含：

```text
product_stocks.product_id
orders.order_no
orders(user_id, activity_id)
mq_message_logs.message_id
mq_message_logs.business_key
```

## 3. 编译验证结果

本次已按成员 A 最新基线重新集成：

```text
origin/dev = 30d67bf feat:完成A第一周内容
集成分支 = codex/member-b-dev-integration
```

已解决与成员 A 基线的冲突：

1. `common/pom.xml`：合并 A 的 `jackson-databind` 与 B 的 validation/context 依赖。
2. `GlobalExceptionHandler.java`：保留 B 的显式参数异常处理，移除 A 临时字符串判断兜底。
3. `OrderController.java`：保留 A 的 Swagger 注解，保留 B 的真实订单查询实现。
4. `ProductController.java`：保留 A 的 Swagger 注解，保留 B 的真实商品/库存实现。
5. `SeckillController.java`：保留 A 的 Swagger 注解，保留 B 的真实秒杀实现。

已执行：

```bash
/Users/zhoujia/.m2/wrapper/dists/apache-maven-3.9.11-bin/6mqf5t809d9geo83kj4ttckcbc/apache-maven-3.9.11/bin/mvn -pl product-seckill-service,order-service -am test -DskipTests
```

结果：

```text
BUILD SUCCESS
```

已执行：

```bash
git diff --check
```

结果：

```text
通过，无空白错误。
```

未完成：

1. 未完成 MySQL + Redis + RabbitMQ 的真实端到端联调。
2. 未完成 Apifox/curl 接口测试记录。
3. 未完成 Redis、RabbitMQ、MySQL 控制台截图或日志证据。

原因：

```text
Docker daemon 当前未启动，无法连接本地 docker compose 中间件。
```

## 4. 自我审核总体结论

本轮代码已经达到“基于成员 A 最新 dev 基线可编译、可无冲突合并”的状态，但还没有达到“完成真实中间件端到端验收”的状态。

风险等级：

```text
中
```

合并建议：

```text
可以作为代码集成版本合并到 dev；演示验收前仍需补真实联调证据。
```

原因：

1. 成员 A 的 Gateway/JWT/Swagger 基线已合入，控制器冲突已解决。
2. 成员 B 的商品、秒杀、订单核心链路在最新 dev 基线上编译通过。
3. 已修复上一轮自审中的 MQ 投递确认、消费重试、Redis miss 订单回查和库存 key 自动恢复风险。
4. 仍缺 MySQL + Redis + RabbitMQ 的真实端到端联调证据，不应直接宣称演示验收完成。

## 5. 接口一致性自审

| 接口 | 文档要求 | 实际情况 | 是否通过 | 修复建议 |
| --- | --- | --- | --- | --- |
| `POST /api/products` | 创建商品，ADMIN | 已实现落库 | 通过 | 等 A 完成权限后联调 |
| `GET /api/products` | 商品分页查询，PUBLIC | 已实现分页和 keyword | 通过 | 补 Apifox 示例 |
| `GET /api/products/{productId}` | 商品详情，PUBLIC | 已返回商品和库存 | 通过 | 补测试数据 |
| `PUT /api/products/{productId}/stock` | 设置库存，ADMIN | 已写 `product_stocks` | 通过 | 等 A 完成权限后联调 |
| `POST /api/seckill/activities` | 创建秒杀活动，ADMIN | 已实现并预热 Redis | 基本通过 | 补真实 Redis 验证 |
| `GET /api/seckill/activities/{activityId}` | 活动详情，PUBLIC | 已实现 | 通过 | 补 Apifox 示例 |
| `POST /api/seckill/activities/{activityId}/orders` | 发起秒杀，CUSTOMER | 已走 Redis Lua + MQ，返回 queued | 基本通过 | 补 MQ confirm 和端到端测试 |
| `GET /api/seckill/activities/{activityId}/result` | 查询秒杀结果，CUSTOMER | 已查 Redis 结果 | 部分通过 | Redis miss 时应回查订单 |
| `GET /api/orders` | 用户订单列表，CUSTOMER | 已按用户分页 | 通过 | 等 Gateway 注入用户 Header |
| `GET /api/orders/{orderId}` | 用户订单详情，CUSTOMER | 已限制只能查本人订单 | 通过 | 等 Gateway 注入用户 Header |
| `GET /api/orders/admin` | 管理员订单查询，ADMIN | 已按状态分页 | 通过 | 等 A 完成权限拦截 |
| `POST /api/ai/products/{productId}/consult` | AI 商品咨询，CUSTOMER/ADMIN | B 未主写该接口，当前只提供商品上下文 | 不适用 | 与 C 对接商品详情字段 |

## 6. Redis、MQ 与一致性风险自审

### 6.1 已符合要求

1. 秒杀库存没有直接扣数据库。
2. Redis 扣减使用 Lua 脚本，具备原子性。
3. 同一用户重复抢购通过 `seckill:user:{activityId}:{userId}` 阻止。
4. 秒杀成功后返回 `QUEUEING`，不等待订单落库。
5. 订单创建通过 RabbitMQ 异步消费。
6. MQ 消息体包含 `messageId`。
7. 消费端有 `messageId`、`orderNo`、`userId + activityId` 三层幂等。
8. 数据库唯一索引可以兜住重复消息。

### 6.2 本次已修复的风险

#### P1：MQ 投递确认不完整

修复结果：

1. `OrderMessagePublisher` 发送消息时创建 `CorrelationData`。
2. 发送后等待 RabbitMQ broker confirm。
3. confirm nack、confirm 超时、unroutable return 都会抛出异常。
4. 秒杀服务捕获投递异常后回滚 Redis 库存、用户参与标记和结果状态。

#### P1：消费失败重试逻辑不够严格

修复结果：

1. `OrderMessageConsumer` 成功消费后手动 ack。
2. 失败时记录消息失败状态并继续抛出异常。
3. 不再在首次失败时直接 `basicNack(requeue=false)`。
4. 让 `spring.rabbitmq.listener.simple.retry.max-attempts=3` 接管重试，超过次数后再进入拒绝/死信路径。

#### P1：秒杀结果查询缺少订单回查

修复结果：

1. `order-service` 增加内部查询接口：

```text
GET /api/orders/internal/seckill-result?activityId={activityId}&userId={userId}
```

2. `product-seckill-service` 在 Redis result miss 时调用订单服务回查。
3. 订单已存在时返回真实订单状态、`orderId` 和 `orderNo`。
4. 订单仍未生成时继续返回 `QUEUEING`。

#### P1：抢购请求中自动恢复库存 key 有超卖风险

修复结果：

1. 秒杀入口不再在 `seckill:stock:{activityId}` 缺失时按活动原始库存自动恢复。
2. 库存 key 缺失时返回业务冲突，避免 Redis key 被误删后重新放出全部库存。
3. 活动创建时仍会执行 Redis 预热，活动元信息缺失时可以重新缓存非库存元数据。

### 6.3 仍需验证的风险

#### P0：缺少真实端到端联调证据

当前只有编译通过，尚未证明以下链路真实跑通：

```text
Gateway/直连请求 -> product-seckill-service -> Redis Lua -> RabbitMQ -> order-service -> MySQL -> Redis result
```

修复建议：

1. 启动 Docker Desktop。
2. 执行 `docker compose up -d`。
3. 启动 `product-seckill-service` 和 `order-service`。
4. 用 curl 或 Apifox 完整跑一遍链路。
5. 将 Redis、RabbitMQ、MySQL 结果写入测试报告。

## 7. 与成员 A/C 的依赖影响

### 7.1 对成员 A

当前不阻塞 A 继续做 Gateway 和权限，但需要 A 明确：

1. Gateway 是否注入 `X-User-Id`。
2. Gateway 是否注入 `X-User-Role`。
3. ADMIN / CUSTOMER 权限是否只由 Gateway 拦截。
4. 是否允许本地直连时使用默认用户 ID。

### 7.2 对成员 C

当前不阻塞 C 做测试页面和 Apifox，因为商品、活动、秒杀、订单接口路径已经稳定。

需要同步给 C：

1. 秒杀成功只返回 `QUEUEING`。
2. 最终订单结果需要轮询 `GET /api/seckill/activities/{activityId}/result`。
3. 商品详情已经包含库存信息。
4. 订单列表和详情接口已经有真实返回结构。

### 7.3 A/C 对 B 的反向阻塞

1. A 未完成 Gateway 用户 Header 注入前，B 只能用本地默认用户 ID 做直连演示。
2. C 未完成测试页面前，B 只能用 curl/Apifox 验证链路。
3. Docker 未启动前，B 无法完成 Redis/MQ/MySQL 端到端证据。

## 8. 下阶段 TODO

1. 启动 MySQL、Redis、RabbitMQ，完成完整链路联调。
2. 补充 `docs/docs-B/成员B阶段测试报告.md`，记录 curl/Apifox、Redis、RabbitMQ、MySQL 证据。
3. 与成员 A 确认 `X-User-Id`、`X-User-Role` 的最终传递方式和 Gateway 内部接口绕行策略。
4. 与成员 C 同步接口字段和固定演示数据。
5. 如演示需要，补充秒杀活动重新预热的管理端接口或运维脚本。

## 9. 本轮合并建议

是否允许 `feature/member-b-seckill-order` 合并到 `dev`：

```text
建议合并当前集成分支 codex/member-b-dev-integration，而不是旧的 feature/member-b-seckill-order 工作树。
```

合并前检查项：

1. 当前分支必须基于 `origin/dev` 最新提交 `30d67bf`。
2. `git diff --check` 必须通过。
3. `mvn -pl product-seckill-service,order-service -am test -DskipTests` 必须通过。
4. 不提交 `.DS_Store`。
5. 真实端到端联调证据可以作为演示验收前置项，不再阻塞代码集成。

合并后需要通知 A/C：

1. 通知 A：成员 B 接口路径未变，但依赖 Gateway 注入用户 Header。
2. 通知 C：秒杀接口返回 `QUEUEING`，最终状态通过结果查询接口获取。
3. 通知 C：商品、活动、订单接口已经具备真实数据返回，可替换前端 mock。
