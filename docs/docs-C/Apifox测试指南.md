# Apifox 测试指南

## 1. 目标

本文档由成员 C 维护，用于按 `docs/Apifox接口清单.md` 组织 Apifox 集合、环境变量和最终演示流程。所有接口路径、字段、权限和返回结构以 `docs/API接口契约.md` 为准。

## 2. 环境变量

```text
baseUrl = http://localhost:8080
token = 普通用户登录后自动提取
adminToken = 管理员登录后自动提取
userId
productId
activityId
orderId
```

## 3. 分组清单

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

## 4. 接口整理

### 4.1 01 认证与用户

1. `POST {{baseUrl}}/api/auth/register`
2. `POST {{baseUrl}}/api/auth/login`
3. `POST {{baseUrl}}/api/auth/admin/login`
4. `POST {{baseUrl}}/api/auth/logout`
5. `GET {{baseUrl}}/api/users/me`

普通用户登录成功后提取：

```text
token = $.data.token
userId = $.data.user.userId
```

管理员登录成功后提取：

```text
adminToken = $.data.token
```

### 4.2 02 商品与库存

1. `POST {{baseUrl}}/api/products`
2. `GET {{baseUrl}}/api/products?page=1&pageSize=10&keyword=`
3. `GET {{baseUrl}}/api/products/{{productId}}`
4. `PUT {{baseUrl}}/api/products/{{productId}}/stock`

创建商品成功后提取：

```text
productId = $.data.productId
```

### 4.3 03 秒杀活动

1. `POST {{baseUrl}}/api/seckill/activities`
2. `GET {{baseUrl}}/api/seckill/activities/{{activityId}}`
3. `POST {{baseUrl}}/api/seckill/activities/{{activityId}}/orders`
4. `GET {{baseUrl}}/api/seckill/activities/{{activityId}}/result`

创建秒杀活动成功后提取：

```text
activityId = $.data.activityId
```

发起秒杀的验收点：

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

该响应只能证明进入异步队列，不能作为订单创建成功凭证。

### 4.4 04 订单查询

1. `GET {{baseUrl}}/api/orders?page=1&pageSize=10`
2. `GET {{baseUrl}}/api/orders/{{orderId}}`
3. `GET {{baseUrl}}/api/orders/admin?page=1&pageSize=10&status=CREATED`

订单创建成功后，从 SSE 推送事件或订单列表中提取：

```text
orderId = $.data.orderId
```

### 4.5 05 AI 智能导购

1. `POST {{baseUrl}}/api/ai/products/{{productId}}/consult`

请求体：

```json
{
  "question": "这款商品适合学生党购买吗？"
}
```

如果本地没有配置 `LLM_API_KEY` / `LLM_BASE_URL`，接口返回 `30001` 并携带 `status=FALLBACK`，这是明确降级，不作为真实 LLM 成功回答。

### 4.6 06 实时推送

1. `GET {{baseUrl}}/api/push/orders/subscribe`

该接口为 SSE 长连接，Apifox 中用于说明和手工验证。测试页使用 `fetch` 流式读取并携带：

```text
Authorization: Bearer {{token}}
```

订单服务消费 MQ 并落库后，推送服务发送：

```json
{
  "eventType": "ORDER_CREATED",
  "activityId": 1,
  "orderId": 90001,
  "orderNo": "ORD202606040001",
  "userId": 10001,
  "status": "CREATED",
  "message": "订单创建成功"
}
```

## 5. 完整演示流程

1. 管理员登录，保存 `adminToken`。
2. 创建商品，保存 `productId`。
3. 设置库存。
4. 创建秒杀活动，保存 `activityId`。
5. 普通用户注册或登录，保存 `token` 和 `userId`。
6. 打开 `simple-test-page/index.html` 并建立推送连接。
7. 发起秒杀，确认接口返回 `queued` / `QUEUEING`。
8. 等待测试页收到 `ORDER_CREATED` 推送事件。
9. 使用推送中的 `orderId` 查询订单详情。
10. 调用 AI 商品咨询接口。

## 6. 维护规则

1. Apifox 路径必须与 `docs/API接口契约.md` 一致。
2. 返回结构必须为 `{ code, message, data }`。
3. 秒杀接口不得把 `QUEUEING` 冒充为订单成功。
4. 推送事件必须来自订单服务落库后的内部发布。
5. AI 降级必须显示 `code=30001` 和 `status=FALLBACK`。
