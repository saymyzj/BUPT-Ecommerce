# 成员 A 工作指导

> 适用身份：成员 A  
> 固定方向：总体架构、API 契约、Gateway、用户认证与权限、联调与最终裁定  
> 编写依据：`README.md` 与 `docs/` 下现有规范文档

## 1. 成员 A 负责什么，不负责什么

### 1.1 成员 A 负责

1. 冻结并维护总体架构：确认服务拆分、调用链路、跨服务边界。
2. 负责 `gateway-service`：统一入口、路由转发、JWT 校验、角色拦截、基础限流、跨域、统一日志。
3. 负责 `user-service`：注册、登录、注销、用户信息、JWT 签发、角色权限。
4. 维护 API 契约：接口路径、请求字段、响应字段、权限标记、错误码语义。
5. 维护统一响应格式：所有 HTTP 接口必须返回 `{ code, message, data }`。
6. 做最终联调裁定：涉及 Gateway、认证、接口路径、权限、响应格式的争议，由 A 裁定。
7. 主导最终报告中的架构、部署、生产演进、项目组织章节。

### 1.2 成员 A 不负责

1. 不主写商品、库存、秒杀活动的完整业务逻辑。
2. 不主写 Redis Lua 扣减、MQ 消费、订单落库、补偿细节。
3. 不主写测试页、SSE/WebSocket 体验、Apifox 集合维护细节。
4. 不私自改其他成员负责的表结构、Redis key、MQ 字段。
5. 不绕过文档直接接受“临时接口”。

成员 A 的核心原则是：不抢业务实现，但必须守住系统契约。

## 2. 系统总体架构和服务边界

### 2.1 总体链路

```text
Client / simple-test-page / Apifox
 -> Gateway: 8080
 -> user-service: 8081
 -> product-seckill-service: 8082
 -> order-service: 8083
 -> ai-service: 8084
 -> push-service: 8085
 -> MySQL / Redis / RabbitMQ
```

### 2.2 服务边界

1. `gateway-service`
   - 只做通用能力：路由、鉴权、权限、限流、跨域、日志。
   - 不写商品、秒杀、订单业务判断。
   - 所有客户端请求必须从 `http://localhost:8080` 进入。

2. `user-service`
   - 负责 `/api/auth/**`、`/api/users/**`。
   - 签发 JWT，返回 `userId`、`username`、`role`。
   - 角色只允许 `CUSTOMER`、`ADMIN`。

3. `product-seckill-service`
   - 负责 `/api/products/**`、`/api/seckill/**`。
   - 商品、库存、秒杀活动、Redis 预热、Lua 原子扣减。
   - 秒杀接口只返回排队态，不同步落库订单。

4. `order-service`
   - 负责 `/api/orders/**`。
   - 消费 RabbitMQ，订单落库，订单查询，幂等与补偿。
   - 成功落库后触发推送事件。

5. `ai-service`
   - 负责 `/api/ai/**`。
   - 商品咨询、上下文构造、LLM 调用、缓存、降级。
   - 当前文档对 AI 负责人存在轻微口径差异，需由 A 尽快裁定并同步文档。

6. `push-service` 或 `order-service` 内推送模块
   - 负责 `/api/push/orders/subscribe`。
   - 推荐 SSE。
   - 只能推送当前用户自己的订单事件。

## 3. JWT、权限、统一响应和网关路由关键口径

### 3.1 JWT 口径

请求头统一为：

```text
Authorization: Bearer <token>
```

JWT payload 必须至少包含：

```json
{
  "userId": 10001,
  "username": "alice",
  "role": "CUSTOMER",
  "iat": 1710000000,
  "exp": 1710003600
}
```

约束：

1. 角色只允许 `CUSTOMER`、`ADMIN`。
2. 统一使用 `JWT_SECRET`，不得提交真实密钥。
3. Token 过期或非法返回 `401`。
4. 角色不足返回 `403`。
5. 注销策略如果暂不做服务端黑名单，必须明确“注销仅清除客户端态”。

### 3.2 权限口径

```text
PUBLIC   无需登录
CUSTOMER 普通用户可访问
ADMIN    管理员可访问
```

典型分配：

1. `PUBLIC`：注册、登录、管理员登录、商品列表、商品详情、秒杀活动详情。
2. `CUSTOMER`：发起秒杀、查询秒杀结果、查询订单、AI 咨询、建立推送连接。
3. `ADMIN`：创建商品、设置库存、创建秒杀活动、管理员订单查询。

### 3.3 统一响应口径

成功：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

失败：

```json
{
  "code": 400,
  "message": "请求参数错误",
  "data": null
}
```

分页：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [],
    "page": 1,
    "pageSize": 10,
    "total": 100
  }
}
```

关键错误码：

```text
0     success
400   请求参数错误
401   未登录或 Token 无效
403   无权限
404   资源不存在
409   业务冲突
429   请求过于频繁
500   服务器内部错误
10001 库存不足
10002 秒杀活动未开始
10003 秒杀活动已结束
10004 用户已参与该活动
20001 MQ 投递失败
30001 AI 服务暂不可用
```

### 3.4 Gateway 路由口径

```text
/api/auth/**       -> user-service
/api/users/**      -> user-service
/api/products/**   -> product-seckill-service
/api/seckill/**    -> product-seckill-service
/api/orders/**     -> order-service
/api/ai/**         -> ai-service
/api/push/**       -> push-service 或 order-service
```

## 4. 分阶段 TODO LIST

### 4.1 当务之急

1. 建立成员 A 的契约基线表：
   - 列出全部接口路径。
   - 列出每个接口权限：`PUBLIC` / `CUSTOMER` / `ADMIN`。
   - 列出每个接口请求字段、响应字段、错误码。

2. 锁定 Gateway 路由：
   - `/api/auth/**` 到 `user-service`。
   - `/api/users/**` 到 `user-service`。
   - `/api/products/**`、`/api/seckill/**` 到 `product-seckill-service`。
   - `/api/orders/**` 到 `order-service`。
   - `/api/ai/**` 到 `ai-service`。
   - `/api/push/**` 到推送模块。

3. 冻结认证规则：
   - JWT payload 字段不再随意变化。
   - `role` 只能是 `CUSTOMER` 或 `ADMIN`。
   - 明确注销是否仅清除客户端态。

4. 拉齐 B、C：
   - B 不得绕过 Gateway 暴露给测试页。
   - B 的秒杀接口只返回 `queued`，不能直接返回最终订单。
   - C 的测试页和 Apifox 只访问 `localhost:8080`。
   - C 的推送不能写死成功结果。

5. 确定 AI 归属：
   - 当前 `docs/总体架构设计.md` 写 `ai-service` 由 C 负责。
   - README 给成员 B 的 prompt 中也包含 AI 商品咨询。
   - 建议 A 裁定：AI 服务由 C 主导，B 提供商品数据接口；或 B 主导 AI 后端，C 只做页面和 Apifox。
   - 裁定后更新相关文档。

### 4.2 第一周

1. 完成 `gateway-service` 基线：
   - 路由转发可用。
   - CORS 可用。
   - JWT 鉴权可用。
   - PUBLIC 接口放行。
   - CUSTOMER / ADMIN 接口拦截。
   - 鉴权失败统一返回 `{ code: 401, message, data: null }`。
   - 权限不足统一返回 `{ code: 403, message, data: null }`。

2. 完成 `user-service` 基线：
   - `POST /api/auth/register`
   - `POST /api/auth/login`
   - `POST /api/auth/admin/login`
   - `GET /api/users/me`
   - `POST /api/auth/logout`

3. 完成接口契约核查：
   - 每个接口是否以 `/api` 开头。
   - 是否都走 `http://localhost:8080`。
   - 响应是否都有 `code/message/data`。
   - 分页字段是否是 `page/pageSize/records/total`。
   - 时间是否 ISO 8601。
   - 枚举是否大写英文。

4. 和 B 完成第一轮对接：
   - 商品创建字段：`name`、`price`、`description`。
   - 库存字段：`stock`。
   - 秒杀活动字段：`productId`、`startTime`、`endTime`、`seckillPrice`、`seckillStock`。
   - 秒杀排队响应：`activityId`、`status=QUEUEING`。
   - MQ 消息字段保持文档原样。

5. 和 C 完成第一轮对接：
   - Apifox 分组按文档 8 组建立。
   - 测试页只调用 Gateway。
   - 登录后保存 `token`、`adminToken`。
   - 推送连接携带用户 token。
   - 演示顺序按 Apifox 文档串起来。

### 4.3 第二周

1. 做全链路联调：
   - 管理员登录。
   - 创建商品。
   - 设置库存。
   - 创建秒杀活动。
   - 普通用户登录。
   - 建立推送连接。
   - 发起秒杀。
   - 返回 `queued`。
   - MQ 消费落库。
   - 推送订单成功。
   - 查询订单。
   - 调用 AI 咨询。

2. 做契约回归：
   - Apifox 全流程跑通。
   - 普通用户访问 ADMIN 接口必须 403。
   - 未登录访问 CUSTOMER 接口必须 401。
   - 秒杀重复参与返回 `10004` 或 `409`。
   - 库存不足返回 `10001`。
   - AI 降级返回 `30001`。

3. 整理最终材料：
   - 架构图。
   - 服务边界说明。
   - Gateway 路由表。
   - 权限矩阵。
   - Redis + MQ 秒杀链路说明。
   - 部署启动顺序。
   - 生产演进方案。
   - 成员分工与联调记录。

## 5. 如何与成员 B 对接秒杀、订单、MQ 和数据库契约

### 5.1 对接接口

B 负责的接口路径必须保持：

```text
POST /api/products
GET /api/products
GET /api/products/{productId}
PUT /api/products/{productId}/stock
POST /api/seckill/activities
GET /api/seckill/activities/{activityId}
POST /api/seckill/activities/{activityId}/orders
GET /api/seckill/activities/{activityId}/result
GET /api/orders
GET /api/orders/{orderId}
GET /api/orders/admin
```

### 5.2 秒杀链路

必须按以下链路实现：

```text
Gateway
 -> product-seckill-service
 -> Redis Lua 原子扣减
 -> RabbitMQ
 -> order-service
 -> MySQL
 -> push event
```

### 5.3 Redis key

不得私自修改：

```text
seckill:stock:{activityId}
seckill:user:{activityId}:{userId}
seckill:result:{activityId}:{userId}
seckill:activity:{activityId}
```

### 5.4 MQ 契约

不得私自修改：

```text
Exchange:    seckill.order.exchange
Queue:       seckill.order.create.queue
Routing Key: seckill.order.create
Event Type:  SECKILL_ORDER_CREATE
```

消息体字段保持：

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

### 5.5 数据库约束

必须保留：

```text
users.username 唯一索引
users.phone 唯一索引
product_stocks.product_id 唯一索引
orders.order_no 唯一索引
orders(user_id, activity_id) 唯一索引
mq_message_logs.message_id 唯一索引
mq_message_logs.business_key 唯一索引或复合唯一约束
```

B 每次改以下内容前必须先找 A：

1. 新增接口。
2. 改接口路径。
3. 改请求字段。
4. 改响应字段。
5. 改 Redis key。
6. 改 MQ 字段。
7. 改订单状态枚举。
8. 改数据库唯一索引。

## 6. 如何与成员 C 对接推送、测试页和 Apifox

1. C 的测试页必须只访问：

```text
http://localhost:8080
```

不能直接请求 `8081`、`8082`、`8083`、`8084`、`8085`。

2. Apifox 环境变量统一：

```text
baseUrl = http://localhost:8080
token
adminToken
userId
productId
activityId
orderId
```

3. Apifox 分组必须按文档：

```text
01 认证与用户
02 商品与库存
03 秒杀活动
04 订单查询
05 AI 智能导购
06 实时推送
07 管理员流程
08 完整演示流程
```

4. 测试页最少要有这些区域：
   - 管理员登录。
   - 创建商品。
   - 设置库存。
   - 创建秒杀活动。
   - 用户登录。
   - 建立推送连接。
   - 发起秒杀。
   - 秒杀结果/订单状态展示。
   - AI 咨询。

5. 推送不能写死：
   - 发起秒杀后先展示 `QUEUEING`。
   - 等订单服务落库成功后，由推送服务发事件。
   - 页面根据真实 SSE/WebSocket 事件更新状态。
   - 如果没有推送事件，页面不能自己假装成功。

6. 推送权限：
   - `/api/push/orders/subscribe` 是 `CUSTOMER`。
   - 一个用户只接收自己的订单事件。
   - Gateway 必须校验 token 后再放行。

## 7. 如何检查接口契约是否被破坏

每天做一轮契约巡检：

1. 查路径：
   - 是否所有客户端接口都以 `/api` 开头。
   - 是否 Apifox、测试页都指向 `localhost:8080`。
   - 是否有人绕过 Gateway 调内部端口。

2. 查响应格式：
   - 成功必须是 `{ code: 0, message: "success" 或 "queued", data: ... }`。
   - 失败必须是 `{ code, message, data: null }` 或文档允许的数据结构。
   - 不允许直接返回字符串、数组、异常栈。

3. 查权限：
   - PUBLIC 接口不要求 token。
   - CUSTOMER 接口无 token 返回 `401`。
   - ADMIN 接口普通用户访问返回 `403`。
   - 管理员接口不能只靠前端隐藏按钮。

4. 查字段：
   - 登录返回必须包含 `token` 和 `user`。
   - `user` 必须包含 `userId`、`username`、`role`。
   - 秒杀接口返回 `activityId`、`status=QUEUEING`。
   - AI 返回 `answer`。
   - 分页返回 `records/page/pageSize/total`。

5. 查枚举：
   - 角色：`CUSTOMER` / `ADMIN`。
   - 订单：`QUEUEING` / `CREATED` / `FAILED` / `CANCELLED` / `PAID`。
   - 活动：`DRAFT` / `READY` / `ONGOING` / `FINISHED` / `CANCELLED`。
   - 枚举必须大写英文，不要混用中文或小写状态。

6. 查跨服务契约：
   - Redis key 前缀是否私自变化。
   - MQ routing key 是否变化。
   - MQ 消息字段是否变化。
   - 数据库唯一索引是否还在。

7. 查文档同步：
   - 如果代码里出现新接口，`docs/API接口契约.md` 必须先有。
   - 如果表结构变了，`docs/数据库设计.md` 必须先有。
   - 如果 Redis key 变了，`docs/Redis-Key设计.md` 必须先有。
   - 如果 MQ 字段变了，`docs/MQ消息契约.md` 必须先有。

## 8. Git 上每天应该怎么操作

### 8.1 每天开始

```bash
git checkout dev
git pull origin dev
git checkout feature/member-a-architecture
git merge dev
```

开始改动前：

```bash
git status
```

### 8.2 改动原则

1. 文档变更和代码变更分清楚。
2. 跨服务变更先提交文档，再提交代码。
3. 不把 `.env`、真实密钥、日志、临时缓存提交上去。
4. 不把 B、C 未确认的业务实现误重构掉。

### 8.3 建议提交信息

```text
docs: update api contract for seckill result
feat: add gateway jwt authorization
fix: align auth error response format
test: add apifox contract checks
```

### 8.4 每天结束

```bash
git status
git add <明确文件>
git commit -m "docs: update member a integration checklist"
git push origin feature/member-a-architecture
```

### 8.5 合并前检查

1. `README.md`、`docs/` 是否同步。
2. Gateway 路由是否正常。
3. 用户登录是否能拿到 token。
4. CUSTOMER / ADMIN 权限是否可验证。
5. Apifox 完整演示流程是否仍能跑通。
6. 没有把其他成员的未完成代码误提交。

## 9. 当前最容易产生分歧的地方

1. AI 服务归属
   - `docs/总体架构设计.md` 写 `ai-service` 由 C 负责。
   - README 给成员 B 的 prompt 中也把 AI 商品咨询列给 B。
   - 建议 A 尽快裁定并更新分工文档。

2. 推送服务是否独立
   - 文档允许 `push-service` 或 `order-service` 内推送模块。
   - 时间紧可先放在订单服务内推送；如果要架构展示更清晰，可拆 `push-service`。
   - 一旦决定，Gateway 路由和部署文档要同步。

3. 注销策略
   - 文档允许客户端删除 token、服务端黑名单、注销时间三种方式。
   - 如果不做黑名单，必须明确“注销仅清除客户端态”。

4. 秒杀接口是否返回订单结果
   - 不允许。
   - 秒杀接口只能返回 `queued`。
   - 最终结果来自查询结果接口或推送。

5. 是否允许服务直连
   - 客户端、测试页、Apifox 不允许直连内部服务。
   - 内部服务间通信可以存在，但必须有明确接口、事件或消息契约。

6. 错误码用 HTTP 状态码还是业务 code
   - 当前文档要求响应体里统一 `code/message/data`。
   - HTTP 状态可以配合使用，但前端和 Apifox 判断应以响应体业务码为准。
   - 认证失败统一 `401`，权限不足统一 `403`。

7. 订单幂等靠 Redis 还是 MySQL
   - Redis 的用户参与标记用于前置拦截。
   - MySQL 的 `orders(user_id, activity_id)` 唯一索引用于最终兜底。
   - MQ 消费还要用 `messageId` 或 `orderNo` 做幂等。

8. mock 能不能用于演示
   - 可以用于并行开发。
   - 不能在最终演示中冒充真实能力。
   - README 已明确“不准用 mock 冒充真实能力”。

9. 接口字段临时调整
   - 任何路径、字段、权限、错误码变更，必须先改文档，再改代码，再通知 B/C 更新测试页和 Apifox。

## 10. 成员 A 的一句话工作标准

成员 A 最重要的工作不是多写几个业务接口，而是让所有成员每天都沿着同一条路走：

```text
Gateway 统一入口
JWT 统一身份
API 契约统一字段
响应格式统一结果
Redis / MQ / DB 契约统一链路
```

这件事守住了，项目最后联调阶段就不会散架。
