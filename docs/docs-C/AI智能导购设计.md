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
 -> 查询本地内存缓存
 -> 查询 product-seckill-service 商品详情作为上下文
 -> 若配置 LLM_BASE_URL 和 LLM_API_KEY，则调用 OpenAI 兼容 chat completions
 -> 成功后缓存回答
 -> 失败或无配置时返回 30001 降级
```

## 4. 配置项

```text
LLM_BASE_URL
LLM_API_KEY
LLM_MODEL
```

本地默认不提交真实密钥。

## 5. 约束

1. 不用硬编码正常回答冒充 AI。
2. 降级必须明确返回 `30001` 和 `status=FALLBACK`。
3. 真实 LLM 调用必须基于商品上下文和用户问题。
4. 生产环境需补充 Redis 缓存、用户级限流、请求排队和超时控制。
