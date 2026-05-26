# 成员 B 审查问题修复记录

## 1. 修复范围

本次修复针对成员 A 审查中与成员 B 职责直接相关的问题：

1. 默认用户兜底需要可关闭。
2. 秒杀活动创建时需要预占商品库存，避免多个活动重复使用同一批库存。
3. 消费失败后不应直接判定用户失败，应优先保留资格并重试创建订单。
4. `schema.sql` 建表边界需要按服务拆分。
5. 补充阶段测试报告入口，后续真实联调结果统一记录。

AI 商品咨询接口属于成员 C 职责。成员 B 只保证商品列表和商品详情接口可提供商品上下文，不负责 `ai-service` 的 LLM 调用、缓存和降级实现。

## 2. 默认用户兜底

原问题：

```text
直连 product-seckill-service 或 order-service 时，缺少 X-User-Id 会默认使用 10001。
```

修复方式：

```yaml
app:
  local-demo:
    enable-default-user: true
```

本地直连测试可以保持 `true`。通过 Gateway 联调和正式验收时，应改为 `false`，此时缺少 `X-User-Id` 会返回未登录错误，避免绕过 Gateway 的登录和权限链路。

## 3. 活动库存预占

原问题：

```text
商品库存 100，活动 A 设置 80，活动 B 也可能设置 80。
两个活动合计承诺卖 160 件，但商品实际只有 100 件。
```

修复方式：

1. 创建秒杀活动时用数据库写锁读取 `product_stocks`。
2. 校验 `seckillStock <= available_stock`。
3. 创建活动成功后：

```text
available_stock = available_stock - seckillStock
reserved_stock = reserved_stock + seckillStock
```

这样后续活动只能使用剩余可用库存。

## 4. 消费失败补偿

补偿原则：

```text
Redis 扣库存成功表示用户已经拿到秒杀资格。
如果订单消费失败，不应第一时间释放资格或直接判定失败。
```

当前修复：

1. `order-service` 增加补偿任务，默认每 60 秒扫描 `mq_message_logs` 中 `FAILED` 的消息。
2. 补偿任务会重新解析消息并再次调用订单创建逻辑。
3. 如果订单创建成功，会补写 Redis 秒杀结果为 `CREATED`。
4. 如果仍失败，会继续保留 `FAILED` 记录并累计重试次数。

最终失败只适用于消息内容不可恢复或超过补偿上限仍无法处理的情况。报告中应说明：系统优先保留用户资格并重试，最终失败是兜底策略，不是首选策略。

## 5. 建表边界

修复前：

```text
product-seckill-service 和 order-service 都维护完整 B 相关表结构。
```

修复后：

```text
product-seckill-service/schema.sql:
- products
- product_stocks
- seckill_activities

order-service/schema.sql:
- orders
- order_items
- mq_message_logs
```

两个服务只维护自己的表，避免后续表结构重复修改导致不一致。

## 6. 真实联调补充修复

打开 Docker Desktop 后执行真实联调，又发现并修复了 3 个运行期问题。

### 6.1 RabbitMQ 队列 Bean 注入歧义

现象：

```text
product-seckill-service/order-service 启动时，Binding 方法参数类型都是 Queue。
容器中同时存在 seckillOrderCreateQueue 和 seckillOrderCreateDlq 两个 Queue Bean，Spring 无法判断注入哪一个。
```

修复：

```text
RabbitMqConfig 的 Binding 方法参数增加 @Qualifier。
```

影响：

```text
两个服务可以正常启动并声明交换机、队列、绑定关系。
```

### 6.2 @PathVariable 参数名缺失

现象：

```text
PUT /api/products/1/stock 等接口运行时报错，原因是当前编译参数没有保留方法参数名。
```

修复：

```text
所有成员 B 相关路径参数显式写成 @PathVariable("productId")、@PathVariable("activityId")、@PathVariable("orderId")。
```

影响：

```text
商品详情、库存设置、秒杀活动详情、发起秒杀、订单详情接口均可正常解析路径参数。
```

### 6.3 MQ 消息 DTO 类型头跨服务不兼容

现象：

```text
product-seckill-service 发送消息时，JSON 消息头中带有发送方 DTO 类名：
com.bupt.ecommerce.product.dto.OrderCreateMessage。

order-service 注册了全局 Jackson2JsonMessageConverter，消费前尝试加载这个类。
order-service 没有该包名下的类，导致消息转换失败。
```

修复：

```text
order-service 移除全局 Jackson2JsonMessageConverter。
消费者读取 RabbitMQ 原始 Message body，再用 ObjectMapper 转为订单服务自己的 OrderCreateMessage DTO。
```

影响：

```text
消息跨服务不再依赖发送方 Java 包名，只依赖 JSON 字段契约。
```

### 6.4 统一异常处理扫描不到

现象：

```text
重复抢购能被 Redis Lua 拦截，但 BusinessException 没有被 common 模块的 GlobalExceptionHandler 接住，HTTP 响应变成 500。
```

修复：

```text
product-seckill-service 和 order-service 启动类使用 scanBasePackages = "com.bupt.ecommerce"。
```

影响：

```text
重复抢购现在返回 {"code":10004,"message":"用户已参与该活动","data":null}。
其他业务异常也会进入统一响应结构。
```

### 6.5 RequestParam 参数名缺失

现象：

```text
Apifox 导入 order-service OpenAPI 后，订单列表参数显示为 arg0、arg1。
实际请求 GET /api/orders?arg0=1&arg1=10 时，后端返回 {"code":400,"message":"请求参数错误","data":null}。
```

原因：

```text
@RequestParam 未显式声明 value，当前编译产物没有保留稳定的方法参数名。
```

修复：

```text
订单列表、管理员订单列表、内部秒杀结果查询、商品分页查询均显式声明参数名：
page、pageSize、status、activityId、userId、keyword。
```

影响：

```text
OpenAPI 显示稳定参数名，Apifox 中使用 page=1&pageSize=10 可以正常查询订单列表。
```

## 7. 真实联调结论

测试环境：

```text
MySQL: localhost:3307/bupt_ecommerce
Redis: localhost:6380
RabbitMQ: localhost:5672
product-seckill-service: localhost:8082
order-service: localhost:8083
```

主要验证结果：

1. 健康检查：两个服务均返回 `{"status":"UP"}`。
2. 活动库存预占：商品库存 100，活动库存 50，创建活动后 `available_stock=50`、`reserved_stock=50`。
3. Redis 预热：`seckill:stock:1=50`。
4. 秒杀成功：请求返回 `queued`，MQ 消费后结果变为 `CREATED`。
5. 订单落库：`orders` 中生成 `CREATED` 订单，`mq_message_logs.status=PROCESSED`。
6. 防重复：同一用户重复抢购返回 `code=10004`。
7. 防超卖：30 个用户抢 10 件，最终订单数为 10，Redis 库存为 0，重复订单组数为 0。
8. MQ 队列：`seckill.order.create.queue messages=0 consumers=1`，`seckill.order.create.dlq messages=0`。

完整证据见：

```text
docs/docs-B/成员B阶段测试报告.md
```
