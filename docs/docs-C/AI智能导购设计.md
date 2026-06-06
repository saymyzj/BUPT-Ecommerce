# AI 智能导购设计

## 1. 目标

AI 智能导购用于围绕商品信息回答用户咨询问题，接口遵守 `docs/API接口契约.md`：

```text
POST /api/ai/products/{productId}/consult
```

权限：

```text
CUSTOMER, ADMIN
```

## 2. 请求与响应

请求体：

```json
{
  "question": "这款商品适合学生党购买吗？"
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "answer": "...",
    "status": "SUCCESS",
    "cacheHit": false
  }
}
```

无 LLM 配置或调用失败时明确降级：

```json
{
  "code": 30001,
  "message": "AI 服务暂不可用",
  "data": {
    "answer": "当前咨询服务繁忙，建议先查看商品详情页信息。",
    "status": "FALLBACK",
    "cacheHit": false
  }
}
```

## 3. 当前实现流程

```text
接收 productId 和 question
 -> 校验 question
 -> 查询本地内存缓存和 Redis 缓存
 -> 查询 product-seckill-service 商品详情作为上下文
 -> 若配置 LLM_BASE_URL 和 LLM_API_KEY，则调用 OpenAI 兼容 chat completions
 -> 成功后写入本地内存缓存和 Redis 缓存
 -> 失败或无配置时返回 30001 降级
```

Redis 缓存 key：

```text
ai:product:qa:{productId}:{questionHash}
```

其中 `questionHash` 为用户问题标准化后的 SHA-256，避免长问题直接进入 key。

## 4. 配置项

```text
LLM_BASE_URL
LLM_API_KEY
LLM_MODEL
```

本地默认不提交真实密钥。

AI 缓存配置：

```text
app.ai.cache.ttl-seconds = 3600
```

## 5. 约束

1. 不用硬编码正常回答冒充 AI。
2. 降级必须明确返回 `30001` 和 `status=FALLBACK`。
3. 真实 LLM 调用必须基于商品上下文和用户问题。
4. 当前阶段已补充 Redis 缓存；生产环境仍需把限流、排队和熔断接入统一网关或独立 AI 调度层。

## 6. 限流、排队和超时降级说明

当前演示阶段已经实现明确超时降级：`app.llm.timeout-ms` 控制 LLM 调用超时时间，调用失败或超时返回 `code=30001`、`status=FALLBACK`，不会返回伪造的成功回答。

用户级限流和排队的生产演进口径：

1. 在 Gateway 或 AI 服务前置层按 `userId` 维护令牌桶，超过阈值返回 `429`。
2. 对可等待请求写入 AI 队列，异步处理后通过缓存或消息通知返回结果。
3. 队列过长或 LLM 超时时进入降级路径，返回 `30001 / FALLBACK`。
4. 缓存命中请求不进入 LLM 队列，优先返回 Redis 中的历史答案。

该说明用于报告和答辩：当前系统具备超时降级和缓存减压能力，用户级限流与排队作为生产环境演进项。
