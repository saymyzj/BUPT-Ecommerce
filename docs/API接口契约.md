# API 接口契约

## 1. 契约目标

本文档冻结本项目当前阶段的接口边界，供 B、C 两组直接开发和联调使用。

所有新增接口、字段改名、路径调整、权限变化，必须先更新本文档，再实现代码。

## 2. 统一约定

### 2.1 Base URL

```text
http://localhost:8080
```

### 2.2 鉴权方式

需要登录的接口统一使用：

```text
Authorization: Bearer <token>
```

### 2.3 通用响应

响应结构见 `docs/统一响应格式与错误码.md`。

### 2.4 权限规则

| 标记 | 含义 |
| --- | --- |
| `PUBLIC` | 无需登录 |
| `CUSTOMER` | 普通用户可访问 |
| `ADMIN` | 管理员可访问 |

## 3. 认证与用户接口

### 3.1 用户注册

```text
POST /api/auth/register
```

权限：`PUBLIC`

请求体：

```json
{
  "username": "alice",
  "password": "123456",
  "phone": "13800000000"
}
```

响应体：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": 10001
  }
}
```

### 3.2 用户登录

```text
POST /api/auth/login
```

权限：`PUBLIC`

请求体：

```json
{
  "username": "alice",
  "password": "123456"
}
```

响应体：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "jwt-token",
    "user": {
      "userId": 10001,
      "username": "alice",
      "role": "CUSTOMER"
    }
  }
}
```

### 3.3 当前用户信息

```text
GET /api/users/me
```

权限：`CUSTOMER`, `ADMIN`

响应体：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": 10001,
    "username": "alice",
    "role": "CUSTOMER"
  }
}
```

### 3.4 用户注销

```text
POST /api/auth/logout
```

权限：`CUSTOMER`, `ADMIN`

请求体：无

响应体：

```json
{
  "code": 0,
  "message": "success",
  "data": true
}
```

## 4. 商品与库存接口

### 4.1 创建商品

```text
POST /api/products
```

权限：`ADMIN`

请求体：

```json
{
  "name": "iPhone",
  "price": 6999,
  "description": "demo product"
}
```

### 4.2 商品分页查询

```text
GET /api/products?page=1&pageSize=10&keyword=phone
```

权限：`PUBLIC`

### 4.3 商品详情

```text
GET /api/products/{productId}
```

权限：`PUBLIC`

### 4.4 设置库存

```text
PUT /api/products/{productId}/stock
```

权限：`ADMIN`

请求体：

```json
{
  "stock": 100
}
```

### 4.5 商品下架

```text
PUT /api/products/{productId}/offline
```

权限：`ADMIN`

说明：

1. 调用成功后商品 `status` 置为 `OFF_SALE`。
2. `OFF_SALE` 商品不得再创建新的秒杀活动。

## 5. 秒杀活动接口

### 5.1 创建秒杀活动

```text
POST /api/seckill/activities
```

权限：`ADMIN`

请求体：

```json
{
  "productId": 20001,
  "startTime": "2026-05-25T10:00:00",
  "endTime": "2026-05-25T12:00:00",
  "seckillPrice": 99.9,
  "seckillStock": 50
}
```

约束：只有 `ON_SALE` 商品可以创建秒杀活动，下架商品返回业务冲突。

### 5.2 秒杀活动详情

```text
GET /api/seckill/activities/{activityId}
```

权限：`PUBLIC`

### 5.3 发起秒杀

```text
POST /api/seckill/activities/{activityId}/orders
```

权限：`CUSTOMER`

请求体：

```json
{
  "quantity": 1
}
```

响应体：

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

### 5.4 查询秒杀结果

```text
GET /api/seckill/activities/{activityId}/result
```

权限：`CUSTOMER`

### 5.5 管理员重建秒杀库存

```text
POST /api/seckill/activities/{activityId}/reconcile
```

权限：`ADMIN`

说明：根据 MySQL 已创建订单和有效资格记录重建 Redis 库存与用户参与标记。该接口只用于 Redis 恢复和验收演示，不改变原秒杀接口。

## 6. 订单接口

### 6.1 订单列表

```text
GET /api/orders?page=1&pageSize=10
```

权限：`CUSTOMER`

### 6.2 订单详情

```text
GET /api/orders/{orderId}
```

权限：`CUSTOMER`

### 6.3 管理员订单查询

```text
GET /api/orders/admin?page=1&pageSize=10&status=CREATED
```

权限：`ADMIN`

### 6.4 内部秒杀结果回查

```text
GET /api/orders/internal/seckill-result?activityId=1&userId=10001
```

权限：`INTERNAL`

说明：

1. 该接口仅供 `product-seckill-service` 在秒杀结果查询链路中回查 `order-service`。
2. 调用方必须携带内部服务头 `X-Internal-Token`，token 值通过 `INTERNAL_SERVICE_TOKEN` 配置。
3. Gateway 入口直接拦截 `/api/orders/internal/**`，客户端和 Apifox 演示流程不直接调用该接口。
4. 对外查询秒杀结果仍使用 `GET /api/seckill/activities/{activityId}/result`。

### 6.5 内部秒杀对账

```text
GET /api/orders/internal/seckill-accounting?activityId=1
```

权限：`INTERNAL`

说明：仅供 `product-seckill-service` 重建 Redis 库存时查询活动已创建订单数，Gateway 禁止外部访问。

### 6.6 管理员查询订单死信

```text
GET /api/orders/admin/dead-letters?page=1&pageSize=20
```

权限：`ADMIN`

### 6.7 管理员重放订单死信

```text
POST /api/orders/admin/dead-letters/{id}/replay
```

权限：`ADMIN`

说明：重放保持原 `messageId`，依赖订单服务幂等约束防止重复订单。

## 7. AI 咨询接口

### 7.1 商品咨询

```text
POST /api/ai/products/{productId}/consult
```

权限：`CUSTOMER`, `ADMIN`

服务内约束：`ai-service` 只信任 Gateway 注入的 `X-User-Id`。直连服务且缺少该请求头时返回 `401`。

请求体：

```json
{
  "question": "这款商品适合学生党购买吗？"
}
```

响应体：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "answer": "..."
  }
}
```

## 8. 实时推送接口

### 8.1 建立推送连接

```text
GET /api/push/orders/subscribe
```

权限：`CUSTOMER`

说明：

1. 推荐使用 SSE。
2. 连接建立后，服务端向客户端推送订单创建结果。
3. 一个用户只接收自己的订单事件。

## 9. 管理员辅助接口

### 9.1 管理员登录

```text
POST /api/auth/admin/login
```

权限：`PUBLIC`

说明：若实现上与普通登录共用，也必须在返回 `role=ADMIN` 后由网关和权限层区分。

## 10. 通用查询参数约定

分页接口统一使用：

```text
page
pageSize
keyword
status
startTime
endTime
```

## 11. 变更控制

以下变更必须先改本文档，再改代码：

1. 接口路径
2. 请求方法
3. 请求字段名
4. 响应字段名
5. 权限标记
6. 错误码语义
7. 分页字段名
