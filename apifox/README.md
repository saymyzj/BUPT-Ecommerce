# Apifox 集合整理说明

成员 C 按 `docs/Apifox接口清单.md` 和 `docs/docs-C/Apifox测试指南.md` 维护 Apifox。当前目录用于存放最终从 Apifox 导出的集合文件。

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
```

## 环境变量

```text
baseUrl = http://localhost:8080
token
adminToken
userId
productId
activityId
orderId
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
8. 查看测试页收到 `ORDER_CREATED` 推送。
9. 使用 `orderId` 查询订单详情。
10. 调用 AI 商品咨询。

最终提交前，应从 Apifox 导出正式集合文件并放在本目录。
