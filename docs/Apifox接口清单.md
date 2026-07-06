# Apifox 接口清单

## 1. 目标

本文档用于给 C 组整理 Apifox 分组、接口顺序和演示链路，方便测试集导入与演示脚本编排。

## 2. 分组建议

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

## 3. 全局环境变量

```text
baseUrl = http://localhost:8080
token = 登录接口自动提取
adminToken = 管理员登录接口自动提取
userId
productId
activityId
orderId
```

## 4. 接口清单

### 4.1 认证与用户

1. `POST /api/auth/register`
2. `POST /api/auth/login`
3. `POST /api/auth/admin/login`
4. `POST /api/auth/logout`
5. `GET /api/users/me`

### 4.2 商品与库存

1. `POST /api/products`
2. `GET /api/products`
3. `GET /api/products/{productId}`
4. `PUT /api/products/{productId}/stock`
5. `PUT /api/products/{productId}/offline`

### 4.3 秒杀活动

1. `POST /api/seckill/activities`
2. `GET /api/seckill/activities/{activityId}`
3. `POST /api/seckill/activities/{activityId}/orders`
4. `GET /api/seckill/activities/{activityId}/result`

### 4.4 订单查询

1. `GET /api/orders`
2. `GET /api/orders/{orderId}`
3. `GET /api/orders/admin`

说明：`/api/orders/internal/**` 为服务间接口，Gateway 会拦截客户端访问，不放入 Apifox 客户端演示集合。

### 4.5 AI 智能导购

1. `POST /api/ai/products/{productId}/consult`

### 4.6 实时推送

1. `GET /api/push/orders/subscribe`

## 5. 建议测试顺序

### 5.1 管理员链路

1. 管理员登录。
2. 创建商品。
3. 设置库存。
4. 创建秒杀活动。
5. 查看活动详情。

### 5.2 用户链路

1. 普通用户登录。
2. 建立推送连接。
3. 发起秒杀。
4. 查看“排队中”结果。
5. 查询订单列表。
6. 调用 AI 咨询接口。

## 6. 演示流程要求

Apifox 中必须能够按如下顺序连续执行：

1. 管理员登录
2. 创建商品
3. 配置库存
4. 创建秒杀活动
5. 普通用户登录
6. 建立推送连接
7. 发起秒杀
8. 查看订单创建成功推送
9. 查询订单
10. 调用 AI 咨询

## 7. 分组备注

### 7.1 认证与用户

用于验证登录态、角色和 token 提取。

### 7.2 商品与库存

用于验证管理员商品管理能力。

### 7.3 秒杀活动

用于验证活动状态、库存预热和扣减。

### 7.4 订单查询

用于验证 MQ 消费落库是否成功。

### 7.5 AI 智能导购

用于验证缓存、降级和回答返回格式。

### 7.6 实时推送

用于验证 SSE/WebSocket 事件到达。

### 7.7 管理员流程

用于把所有管理员动作串成一条演示链。

### 7.8 完整演示流程

用于最终录屏，要求尽量少手工切换。

## 8. 维护规则

1. 接口路径与本文档不一致时，先改文档再改代码。
2. 新增接口必须同步补到对应分组。
3. 删除接口必须从演示流程中移除。
4. 字段名、权限、返回结构变更必须同步调整测试用例。

