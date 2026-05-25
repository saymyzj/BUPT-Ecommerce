# 成员 B 开发 TODO

## 1. 文档定位

本文档是成员 B 后续开发的执行清单。开发时必须以仓库中的 `README.md` 和 `docs/` 规范文档为准，不允许脱离现有设计另起炉灶。

成员 B 固定方向：

1. 商品与库存
2. 秒杀活动
3. Redis 原子扣减
4. MQ 异步订单
5. 订单消费与落库
6. 数据库与中间件
7. AI 商品咨询后端支持

## 2. 必须遵守的核心口径

1. 商品、库存、秒杀、订单、AI 接口必须与 `docs/API接口契约.md` 一致。
2. 秒杀库存必须走 Redis 原子扣减，不得直接扣数据库。
3. 订单创建必须通过 MQ 异步化，不得在秒杀接口中同步等待订单落库。
4. 消费端必须幂等，消息体字段以 `docs/MQ消息契约.md` 为准。
5. 数据表、主键、索引、状态枚举以 `docs/数据库设计.md` 为准。
6. Redis key 命名和语义以 `docs/Redis-Key设计.md` 为准。
7. 不单独修改接口契约、权限规则和网关策略。
8. 涉及接口、权限、响应字段、错误码的变更，必须先和成员 A 对齐。
9. 涉及测试页面字段、Apifox 示例、演示数据的变更，必须同步成员 C。

## 3. 成员 B 负责范围

### 3.1 负责

1. `product-seckill-service` 商品接口真实实现。
2. `product-seckill-service` 库存接口真实实现。
3. `product-seckill-service` 秒杀活动接口真实实现。
4. Redis 秒杀库存预热。
5. Redis Lua 原子扣减库存。
6. Redis 用户参与标记。
7. Redis 秒杀结果写入和查询。
8. RabbitMQ 订单创建消息投递。
9. `order-service` 订单消息消费。
10. `order-service` 订单落库。
11. 订单查询接口。
12. MQ 消费幂等、失败重试、死信和补偿方案。
13. B 负责表结构、索引和状态枚举落地。
14. 为 C 提供稳定 mock 返回、测试数据和字段说明。
15. 为 AI 咨询提供商品详情、价格、描述、活动价等后端数据支持。

### 3.2 不负责

1. Gateway 路由策略。
2. JWT 鉴权实现和权限拦截规则。
3. 统一响应格式和错误码裁定。
4. 接口路径、字段名和权限标记的单方面变更。
5. SSE/WebSocket 推送服务主实现。
6. 简单前端测试页主实现。
7. Apifox 集合主整理。
8. 完整支付流程。
9. 完整电商营销体系。

## 4. 推荐目录结构

在现有仓库结构上扩展，不新建无关工程。

```text
product-seckill-service/src/main/java/com/bupt/ecommerce/product/
  controller/
    ProductController.java
    SeckillController.java
  service/
  service/impl/
  repository/
  entity/
  dto/
  redis/
  mq/
  config/

order-service/src/main/java/com/bupt/ecommerce/order/
  controller/
    OrderController.java
  service/
  service/impl/
  repository/
  entity/
  dto/
  mq/
  config/

ai-service/src/main/java/com/bupt/ecommerce/ai/
  service/      # 如需 B 协助商品上下文和缓存支持
  dto/
```

建议逐步把当前 `Map<String, Object>` 占位实现替换为明确 DTO，但接口路径、请求字段、响应字段必须保持文档一致。

## 5. 实现顺序

### 5.1 第一阶段：数据基础

1. 根据 `docs/数据库设计.md` 完成 B 负责表结构。
2. 实现 `products` 表实体。
3. 实现 `product_stocks` 表实体。
4. 实现 `seckill_activities` 表实体。
5. 实现 `orders` 表实体。
6. 实现 `order_items` 表实体。
7. 实现 `mq_message_logs` 表实体。
8. 建立必要唯一索引：
   - `product_stocks.product_id`
   - `orders.order_no`
   - `orders(user_id, activity_id)`
   - `mq_message_logs.message_id`
   - `mq_message_logs.business_key`

### 5.2 第二阶段：商品与库存

1. 实现 `POST /api/products`。
2. 实现 `GET /api/products?page=1&pageSize=10&keyword=phone`。
3. 实现 `GET /api/products/{productId}`。
4. 实现 `PUT /api/products/{productId}/stock`。
5. 商品状态使用：
   - `ON_SALE`
   - `OFF_SALE`
6. 库存写入 `product_stocks`，不要只放内存。

### 5.3 第三阶段：秒杀活动

1. 实现 `POST /api/seckill/activities`。
2. 实现 `GET /api/seckill/activities/{activityId}`。
3. 活动状态使用：
   - `DRAFT`
   - `READY`
   - `ONGOING`
   - `FINISHED`
   - `CANCELLED`
4. 创建活动后准备 Redis 预热逻辑。
5. 活动库存不得超过商品可售库存。

### 5.4 第四阶段：Redis 秒杀链路

1. 配置 Redis 连接。
2. 定义 Redis key 工具类。
3. 预热 `seckill:stock:{activityId}`。
4. 预热 `seckill:activity:{activityId}`。
5. 实现 Lua 脚本。
6. Lua 脚本一次性完成：
   - 检查活动是否存在
   - 检查活动状态
   - 检查用户是否已参与
   - 检查库存是否大于 0
   - 扣减库存
   - 写入用户参与标记
   - 写入秒杀结果 `QUEUEING`
   - 返回执行结果
7. 秒杀成功后接口只返回 `queued`，不等待订单落库。

### 5.5 第五阶段：MQ 异步下单

1. 配置 RabbitMQ。
2. 声明 exchange：
   - `seckill.order.exchange`
3. 声明 queue：
   - `seckill.order.create.queue`
   - `seckill.order.create.dlq`
4. routing key 使用：
   - `seckill.order.create`
5. 按契约构造消息体：

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

6. MQ 投递失败时返回或记录 `20001 MQ 投递失败`。
7. 不得把 MQ 投递成功当作订单创建成功。

### 5.6 第六阶段：订单消费与落库

1. `order-service` 消费 `seckill.order.create.queue`。
2. 消费前检查 `mq_message_logs.message_id`。
3. 消费前检查 `orders(user_id, activity_id)`。
4. 插入订单主表 `orders`。
5. 插入订单明细表 `order_items`。
6. 更新 `mq_message_logs.status=PROCESSED`。
7. 更新 Redis 结果 `seckill:result:{activityId}:{userId}` 为 `CREATED`。
8. 订单状态使用：
   - `QUEUEING`
   - `CREATED`
   - `FAILED`
   - `CANCELLED`
   - `PAID`
9. 失败时记录 `FAILED`，并准备补偿说明。

### 5.7 第七阶段：订单查询

1. 实现 `GET /api/orders?page=1&pageSize=10`。
2. 实现 `GET /api/orders/{orderId}`。
3. 实现 `GET /api/orders/admin?page=1&pageSize=10&status=CREATED`。
4. 普通用户只能查询自己的订单。
5. 管理员可以按状态分页查询订单。

### 5.8 第八阶段：AI 商品咨询后端支持

1. 确保商品详情接口能稳定返回：
   - `productId`
   - `name`
   - `description`
   - `price`
   - `status`
2. 如 C 需要秒杀上下文，提供：
   - `activityId`
   - `seckillPrice`
   - `seckillStock`
   - `startTime`
   - `endTime`
   - `activityStatus`
3. 如 B 直接参与 AI 缓存实现，使用：
   - `ai:product:qa:{productId}:{questionHash}`
4. AI 接口路径和权限不得私自修改。

## 6. 分阶段 TODO

### 6.1 当务之急

```text
[ ] 确认当前分支是 feature/member-b-seckill-order
[ ] 阅读 README.md
[ ] 阅读 docs/API接口契约.md
[ ] 阅读 docs/数据库设计.md
[ ] 阅读 docs/Redis-Key设计.md
[ ] 阅读 docs/MQ消息契约.md
[ ] 阅读 docs/统一响应格式与错误码.md
[ ] 阅读 docs/认证与权限规范.md
[ ] 检查 product-seckill-service 当前占位接口
[ ] 检查 order-service 当前占位接口
[ ] 列出需要替换的 Map mock 返回
[ ] 和成员 A 确认用户上下文传递方式
[ ] 和成员 A 确认管理员权限如何进入后端服务
[ ] 和成员 C 确认测试页需要的商品、活动、订单字段
[ ] 准备 B 负责表的建表脚本或迁移方案
[ ] 准备固定演示商品数据
[ ] 准备固定演示秒杀活动数据
[ ] 保留可用 mock 接口支持 C 并行开发
```

### 6.2 第一周

```text
[ ] 实现 products 表实体和 Repository
[ ] 实现 product_stocks 表实体和 Repository
[ ] 实现 seckill_activities 表实体和 Repository
[ ] 实现商品创建接口
[ ] 实现商品分页查询接口
[ ] 实现商品详情接口
[ ] 实现设置库存接口
[ ] 实现创建秒杀活动接口
[ ] 实现秒杀活动详情接口
[ ] 配置 Redis 连接
[ ] 定义 Redis key 工具类
[ ] 实现秒杀活动库存预热
[ ] 实现 seckill:stock:{activityId}
[ ] 实现 seckill:activity:{activityId}
[ ] 实现 seckill:user:{activityId}:{userId}
[ ] 实现 seckill:result:{activityId}:{userId}
[ ] 编写 Redis Lua 脚本
[ ] 秒杀接口接入 Lua 原子扣减
[ ] 秒杀成功返回 queued
[ ] 秒杀失败返回库存不足、重复抢购、未开始、已结束等错误码
[ ] 更新 docs/docs-B 中本周进度记录
[ ] 向 A 同步接口实现情况
[ ] 向 C 同步 mock 数据和字段说明
```

### 6.3 第二周

```text
[ ] 配置 RabbitMQ 连接
[ ] 声明 seckill.order.exchange
[ ] 声明 seckill.order.create.queue
[ ] 声明 seckill.order.create.dlq
[ ] 实现 routing key seckill.order.create
[ ] 实现订单创建消息 DTO
[ ] 秒杀扣减成功后投递 MQ
[ ] MQ 投递失败记录或返回 20001
[ ] 实现 orders 表实体和 Repository
[ ] 实现 order_items 表实体和 Repository
[ ] 实现 mq_message_logs 表实体和 Repository
[ ] 实现 order-service 消费者
[ ] 实现 messageId 幂等
[ ] 实现 orderNo 幂等
[ ] 实现 userId + activityId 幂等
[ ] 实现订单主表落库
[ ] 实现订单明细落库
[ ] 消费成功后更新 mq_message_logs 为 PROCESSED
[ ] 消费失败后更新 mq_message_logs 为 FAILED
[ ] 消费成功后更新 Redis 秒杀结果为 CREATED
[ ] 实现订单列表接口
[ ] 实现订单详情接口
[ ] 实现管理员订单查询接口
[ ] 准备 100 件库存、10000 并发验证方案
[ ] 准备演示前 Redis 清理和预热步骤
[ ] 准备 RabbitMQ 管理台演示观察点
[ ] 准备 MySQL 订单落库演示观察点
```

## 7. Mock 接口支持并行开发

在真实链路完成前，必须提供稳定 mock，支持成员 C 做测试页和 Apifox。

### 7.1 商品 mock

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "productId": 20001,
    "name": "Demo 秒杀商品",
    "price": 6999.00,
    "description": "用于秒杀演示的商品",
    "status": "ON_SALE"
  }
}
```

### 7.2 活动 mock

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "activityId": 1,
    "productId": 20001,
    "seckillPrice": 99.90,
    "seckillStock": 100,
    "status": "READY"
  }
}
```

### 7.3 秒杀 mock

```json
{
  "code": 0,
  "message": "queued",
  "data": {
    "activityId": 1,
    "status": "QUEUEING"
  }
}
```

注意：mock 只能表示“已进入队列”，不得返回“订单已创建成功”。

### 7.4 结果查询 mock

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "activityId": 1,
    "status": "QUEUEING",
    "orderId": null,
    "message": "queued"
  }
}
```

真实链路完成后，该接口改为读取 `seckill:result:{activityId}:{userId}`。

## 8. 与成员 A 对接清单

每天至少同步一次以下内容：

```text
[ ] 是否修改接口路径
[ ] 是否修改请求字段
[ ] 是否修改响应字段
[ ] 是否新增错误码
[ ] 是否需要 Gateway 放行新接口
[ ] 是否涉及 ADMIN 权限
[ ] 是否涉及 CUSTOMER 权限
[ ] userId 从哪里获取
[ ] role 从哪里获取
[ ] Swagger/OpenAPI 是否可访问
```

必须向 A 确认：

1. 用户上下文是通过 Header 传递，还是通过服务内上下文传递。
2. 后端服务是否信任 Gateway 注入的用户信息。
3. 管理员接口是否只靠 Gateway 拦截，还是服务内也二次校验。
4. 是否允许新增内部预热接口，若允许，路径和权限是什么。

## 9. 与成员 C 对接清单

提供给 C 的固定数据：

```text
productId = 20001
activityId = 1
seckillStock = 100
normalUserId = 10001
adminUserId = 1
orderStatus = QUEUEING / CREATED / FAILED
```

必须向 C 同步：

```text
[ ] 商品列表字段
[ ] 商品详情字段
[ ] 活动详情字段
[ ] 秒杀按钮需要传的参数
[ ] 秒杀成功 queued 返回格式
[ ] 秒杀失败错误码
[ ] 结果查询返回格式
[ ] 订单列表字段
[ ] 订单详情字段
[ ] AI 咨询需要的商品上下文字段
[ ] 演示前需要预热库存
```

建议给 C 的页面字段：

```json
{
  "productId": 20001,
  "productName": "Demo 秒杀商品",
  "price": 6999.00,
  "seckillPrice": 99.90,
  "activityId": 1,
  "activityStatus": "ONGOING",
  "stock": 100,
  "seckillResult": "QUEUEING",
  "orderStatus": "CREATED"
}
```

## 10. 部署、备份、恢复和演示环境 TODO

### 10.1 部署准备

```text
[ ] 确认 MySQL 端口 3306
[ ] 确认 Redis 端口 6379
[ ] 确认 RabbitMQ 端口 5672
[ ] 确认 RabbitMQ 管理台端口 15672
[ ] 确认 product-seckill-service 端口 8082
[ ] 确认 order-service 端口 8083
[ ] 确认 ai-service 端口 8084
[ ] 确认环境变量和 .env.example 一致
[ ] 不提交 .env
[ ] 不提交真实密钥
```

### 10.2 备份准备

```text
[ ] 准备 MySQL 初始数据 SQL
[ ] 准备商品数据备份
[ ] 准备库存数据备份
[ ] 准备秒杀活动数据备份
[ ] 准备订单表清空说明
[ ] 准备 Redis 秒杀 key 清理说明
[ ] 准备 RabbitMQ 队列清理说明
```

### 10.3 恢复准备

演示前恢复到以下状态：

```text
[ ] 商品存在
[ ] 商品库存为 100
[ ] 秒杀活动存在
[ ] 秒杀活动状态为 READY 或 ONGOING
[ ] Redis 已清理旧 seckill:* key
[ ] Redis 已重新预热活动库存
[ ] orders 表无旧演示脏数据
[ ] order_items 表无旧演示脏数据
[ ] mq_message_logs 表无旧演示脏数据
[ ] RabbitMQ 队列无旧消息堆积
```

### 10.4 演示观察点

```text
[ ] Redis 中 seckill:stock:{activityId} 扣减
[ ] Redis 中 seckill:user:{activityId}:{userId} 写入
[ ] Redis 中 seckill:result:{activityId}:{userId} 变化
[ ] RabbitMQ 管理台看到消息进入队列
[ ] order-service 控制台看到消费日志
[ ] MySQL orders 表出现订单
[ ] MySQL order_items 表出现明细
[ ] MySQL mq_message_logs 表出现消费记录
```

## 11. 每日 Git 操作

### 11.1 开始开发前

```bash
git switch dev
git pull --ff-only origin dev
git switch feature/member-b-seckill-order
git merge dev
```

如 `dev` 有更新，先处理冲突，再继续开发。

### 11.2 开发过程中

```bash
git status
git add <本次修改文件>
git commit -m "feat: implement seckill redis stock deduction"
```

提交信息示例：

```text
feat: implement product stock persistence
feat: add seckill activity creation
feat: add redis lua stock deduction
feat: publish seckill order mq message
feat: add order mq consumer idempotency
fix: handle duplicated seckill request
docs: update member b seckill progress
```

### 11.3 每天结束前

```bash
git status
git push origin feature/member-b-seckill-order
```

检查：

```text
[ ] 没有提交 .env
[ ] 没有提交真实密钥
[ ] 没有提交日志文件
[ ] 没有提交 .DS_Store
[ ] 文档和代码保持一致
[ ] 已向 A 同步接口和联调风险
[ ] 已向 C 同步测试字段和 mock 数据
```

## 12. 验收前自查清单

```text
[ ] 商品创建接口可用
[ ] 商品分页接口可用
[ ] 商品详情接口可用
[ ] 库存设置接口可用
[ ] 秒杀活动创建接口可用
[ ] 秒杀活动详情接口可用
[ ] Redis 库存预热成功
[ ] Redis Lua 扣减成功
[ ] 重复抢购会被拦截
[ ] 库存不足会被拦截
[ ] 秒杀接口只返回 queued
[ ] MQ 消息体字段符合契约
[ ] order-service 能消费消息
[ ] 消费端幂等可验证
[ ] 订单主表落库
[ ] 订单明细落库
[ ] 秒杀结果查询可用
[ ] 订单列表可用
[ ] 订单详情可用
[ ] 管理员订单查询可用
[ ] 100 件库存不会超卖
[ ] 演示链路可从 Apifox 连续执行
```
