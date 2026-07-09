# Apifox 集合整理说明

当前目录用于存放本地演示使用的 Postman/Apifox 集合文件。所有客户端请求都走 Gateway `{{baseUrl}}`，不直接访问 8082、8083 等内部服务端口。

## 分组

```text
01 认证与用户
02 商品与库存
03 秒杀活动
04 订单查询
05 AI 智能导购
06 实时推送
07 管理员流程
08 完整演示流程
09 B+ Production Hardening
```

`09 B+ Production Hardening` 是可选增强演示，包含 Redis 观察、Redis 库存重建、可靠投递事件、死信查询和死信重放。它是管理员/运维视角，不属于普通用户购物主流程。

## 环境变量

```text
baseUrl = http://localhost:8080
token
adminToken
userId
productId
activityId
orderId
orderStatus = PENDING_PAYMENT
seckillStartTime = 2026-07-09T00:00:00
seckillEndTime = 2026-12-31T23:59:59
publishEventId
deadLetterId
```

最终演示环境必须保持 `baseUrl = http://localhost:8080`，不使用 `8082`、`8083` 等服务直连地址。历史直连接口只作为开发联调记录，不进入最终 Apifox 演示口径。

## 完整演示顺序

1. 管理员登录。
2. 创建商品。
3. 设置库存。
4. 创建秒杀活动。
5. 普通用户注册或登录。
6. 在测试页建立 SSE 推送连接。
7. 发起秒杀，确认返回 `QUEUEING`。
8. 查看测试页收到订单创建推送，或查询秒杀结果保存 `orderId`。
9. 使用 `orderId` 查询订单详情，状态通常为 `PENDING_PAYMENT`。
10. 调用支付订单接口，状态变为 `PAID`。
11. 调用 AI 商品咨询。
12. 可选执行 B+：Redis 观察、可靠投递事件查询、Redis 重建库存、死信查询/重放。

## 演示数据初始化

推荐演示前先运行：

```powershell
node scripts\init-demo-products.js
```

该脚本会清空旧商品、活动、订单、秒杀资格、投递事件和死信演示数据，并重新创建一组商品和秒杀活动。运行后可以在演示页点击“拉取商品列表”，也可以将脚本输出的 `productId`、`activityId` 填入环境变量。

最终提交前，应从 Apifox 导出正式集合文件并放在本目录。
