# Redis Key 设计

## 1. 目标

本文档冻结秒杀、缓存和结果查询所使用的 Redis key 命名、语义和过期策略，避免 B 组在并发链路中出现 key 冲突或语义漂移。

## 2. 设计原则

1. key 命名必须分模块、分业务、分对象。
2. 秒杀库存必须使用原子操作保护。
3. 用户参与标记必须能阻止重复抢购。
4. 结果查询必须可直接读 Redis。
5. 所有 key 必须在文档中登记后再实现。

## 3. 命名规范

统一格式：

```text
{module}:{biz}:{identifier}
```

示例：

```text
seckill:stock:1001
```

## 4. 秒杀核心 Key

| Key | 类型 | 语义 | 过期策略 | 负责人 |
| --- | --- | --- | --- | --- |
| `seckill:stock:{activityId}` | string/int | 秒杀活动可售库存 | 活动结束后清理或保留短期 | B |
| `seckill:user:{activityId}:{userId}` | string | 用户是否已参与活动 | 与活动周期一致 | B |
| `seckill:result:{activityId}:{userId}` | string/hash | 用户抢购结果 | 成单后保留一段时间 | B |
| `seckill:activity:{activityId}` | hash/json | 秒杀活动基础信息 | 活动周期内有效 | B |

## 5. 秒杀 Key 说明

### 5.1 `seckill:stock:{activityId}`

用途：

1. 活动开始前预热库存。
2. 秒杀请求中执行原子扣减。
3. 作为是否还能继续抢购的判断依据。

建议值类型：

```text
整数
```

### 5.2 `seckill:user:{activityId}:{userId}`

用途：

1. 标记用户是否已经抢购过。
2. 防止同一用户重复提交。

建议值类型：

```text
1 / true
```

### 5.3 `seckill:result:{activityId}:{userId}`

用途：

1. 记录排队中、成功、失败、补偿中等结果。
2. 支撑前端轮询或结果查询接口。

建议字段：

```json
{
  "status": "QUEUEING",
  "orderId": 90001,
  "message": "queued"
}
```

### 5.4 `seckill:activity:{activityId}`

用途：

1. 缓存活动基础信息。
2. 减少活动详情读库压力。

建议字段：

```json
{
  "activityId": 1,
  "productId": 20001,
  "startTime": "2026-05-25T10:00:00",
  "endTime": "2026-05-25T12:00:00",
  "seckillPrice": 99.9,
  "status": "ONGOING"
}
```

## 6. 秒杀 Lua 原子流程

Redis 扣减必须由 Lua 脚本一次性完成，不允许分散成多次非原子操作。

推荐逻辑：

1. 检查活动是否存在。
2. 检查活动状态是否允许抢购。
3. 检查用户是否已参与。
4. 检查库存是否大于 0。
5. 扣减库存。
6. 写入用户参与标记。
7. 写入结果状态。
8. 返回执行结果。

## 7. 推荐返回码语义

| 返回码 | 含义 |
| --- | --- |
| `0` | 成功扣减并进入队列 |
| `1` | 库存不足 |
| `2` | 用户重复抢购 |
| `3` | 活动未开始 |
| `4` | 活动已结束 |
| `5` | 活动不存在 |

## 8. 可选辅助 Key

| Key | 语义 | 说明 |
| --- | --- | --- |
| `ai:product:qa:{productId}:{questionHash}` | AI 问答缓存 | C 负责 |
| `user:token:{userId}` | Token 黑名单或注销标记 | A 可选 |
| `limit:{ip}:{api}` | 限流计数器 | Gateway 可选 |

## 9. 过期与清理原则

1. 活动相关 key 应与活动生命周期绑定。
2. 成交结果 key 可保留到用户查看完成。
3. 缓存 key 应允许自然失效。
4. 结果类 key 不能无限期堆积。

## 10. 变更控制

以下内容不得私自修改：

1. key 前缀
2. key 参数顺序
3. key 语义
4. Lua 脚本中的 key 顺序
5. 活动库存 key 的扣减方式

