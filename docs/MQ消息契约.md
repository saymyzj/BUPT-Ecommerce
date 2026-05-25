# MQ 消息契约

## 1. 目标

本文档冻结秒杀下单消息的交换机、队列、路由键、消息体和幂等字段，供 B 组实现异步订单链路使用。

## 2. 设计原则

1. 生产者和消费者必须遵守同一份契约。
2. 订单创建必须异步化，不在秒杀接口中同步落库。
3. 消息必须具备幂等处理能力。
4. 消费失败必须可重试，可进入死信队列。
5. 所有消息字段必须文档化。

## 3. 消息拓扑

推荐使用 RabbitMQ：

```text
seckill.order.exchange
 -> seckill.order.create.queue
 -> order-service
```

### 3.1 Exchange

| 名称 | 类型 | 说明 |
| --- | --- | --- |
| `seckill.order.exchange` | topic | 秒杀订单事件交换机 |

### 3.2 Queue

| 名称 | 说明 |
| --- | --- |
| `seckill.order.create.queue` | 订单创建队列 |
| `seckill.order.create.dlq` | 死信队列，可选但推荐 |

### 3.3 Routing Key

```text
seckill.order.create
```

## 4. 订单创建消息体

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

## 5. 字段说明

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `messageId` | string | 是 | 消息唯一 ID，用于幂等 |
| `eventType` | string | 是 | 固定为 `SECKILL_ORDER_CREATE` |
| `activityId` | bigint | 是 | 秒杀活动 ID |
| `userId` | bigint | 是 | 用户 ID |
| `productId` | bigint | 是 | 商品 ID |
| `quantity` | int | 是 | 购买数量 |
| `seckillPrice` | decimal | 是 | 秒杀成交价 |
| `orderNo` | string | 是 | 订单号，建议全局唯一 |
| `createdAt` | string | 是 | 事件产生时间，ISO 8601 |

## 6. 生产者约束

秒杀服务投递消息后应做到：

1. 先完成 Redis 原子扣减。
2. 再投递 MQ。
3. MQ 投递失败要有补偿策略。
4. 不允许在接口线程内同步等待订单落库结果。

## 7. 消费者约束

`order-service` 消费消息时必须满足：

1. 幂等。
2. 重试。
3. 可观测。
4. 可补偿。

### 7.1 幂等键

推荐至少使用以下一种或组合：

1. `messageId`
2. `activityId + userId`
3. `orderNo`

### 7.2 幂等实现建议

1. `mq_message_logs.message_id` 建唯一索引。
2. `orders(user_id, activity_id)` 建唯一索引。
3. 消费前先查重，消费后再更新状态。

## 8. 消费确认与重试

### 8.1 ACK 规则

1. 成功落库后 ACK。
2. 临时异常可重试。
3. 不可恢复异常进入死信或补偿流程。

### 8.2 重试策略

建议：

1. 首次失败重试。
2. 多次失败后进入死信队列。
3. 由补偿任务扫描异常订单。

## 9. 消息状态

| 状态 | 含义 |
| --- | --- |
| `RECEIVED` | 已收到但未处理 |
| `PROCESSED` | 已成功处理 |
| `FAILED` | 处理失败 |

## 10. 结果推送衔接

订单服务在订单落库成功后，向推送模块发送或发布订单成功事件，再由 SSE/WebSocket 通知前端。

推送事件建议另行定义，不与订单创建消息混用。

## 11. 禁止事项

1. 不得私自改消息体字段名。
2. 不得私自改 routing key。
3. 不得把同步下单结果当作 MQ 成功。
4. 不得省略 messageId。
5. 不得把订单状态直接塞进秒杀请求响应里冒充最终结果。

