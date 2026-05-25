# 成员 B 每周任务审查 AI Prompt

> 审查对象：成员 B  
> 技术方向：商品、库存、秒杀、Redis、MQ、订单、AI 咨询  
> 使用方式：每周结束时复制 Prompt 正文，并补充成员 B 的提交、接口测试、Redis/MQ 证据、订单落库结果和任务报告。

## Prompt 正文

```text
我正在参与一个北京邮电大学 Web 后端开发课程项目：AI 驱动的“高并发秒杀与智能电商”后台系统。

请严格基于当前仓库中的 README.md 和 docs/ 规范文档，只审查“成员 B”的本周任务完成情况。不要审查成员 A 或成员 C，除非是说明 B 对他们造成的阻塞或依赖。

你必须优先阅读：

1. README.md
2. docs/总体架构设计.md
3. docs/API接口契约.md
4. docs/统一响应格式与错误码.md
5. docs/数据库设计.md
6. docs/Redis-Key设计.md
7. docs/MQ消息契约.md
8. docs/认证与权限规范.md
9. docs/部署与启动指南.md
10. docs/生产环境演进方案.md
11. docs/Apifox接口清单.md

成员 B 的固定技术方向：

- 商品管理
- 库存管理
- 秒杀活动
- Redis 预热与 Lua 原子扣减
- MQ 异步下单
- 订单消费与落库
- 消费幂等、失败重试、补偿方案
- AI 商品咨询后端能力

当前项目关键口径：

- 秒杀库存必须使用 Redis 原子扣减，不得直接数据库扣库存。
- 秒杀接口扣减成功后必须立即返回 QUEUEING，不得同步创建订单。
- 订单创建必须通过 MQ 异步消费并落库。
- MQ 消息体、exchange、queue、routing key 以 docs/MQ消息契约.md 为准。
- Redis key 命名和语义以 docs/Redis-Key设计.md 为准。
- 数据库表、主键、唯一索引和状态枚举以 docs/数据库设计.md 为准。
- 不得用重复 mock 数据或硬编码结果冒充真实秒杀、订单或 AI 能力。

本次审查周次：第【填写 1/2】周。

我将提供以下材料：

1. 成员 B 本周 git 提交摘要：
【粘贴】

2. 成员 B 本周自述：
【粘贴】

3. 商品、库存、秒杀、订单、AI 接口测试结果：
【粘贴 curl/Apifox/日志】

4. Redis key 和 Lua 扣减测试证据：
【粘贴】

5. MQ 投递、消费、幂等和订单落库证据：
【粘贴】

6. 与成员 A 的契约对齐情况：
【粘贴】

7. 与成员 C 的页面/Apifox 联调情况：
【粘贴】

8. 本周自测报告和任务报告路径：
【粘贴，必须位于 docs/docs-B/ 下】

请输出成员 B 的独立审查报告，结构如下：

一、总体结论

- 成员 B 本周是否达到 README.md 和 docs/分工指南.md 中对应周次要求。
- 风险等级：低 / 中 / 高。
- 是否允许成员 B 分支合并到 dev。
- 如果不允许，列出阻塞项。

二、任务完成度审查

请按周次检查：

第 1 周重点：
- product-seckill-service 和 order-service 基础骨架是否完成。
- 商品创建、查询、详情、库存配置接口是否与 docs/API接口契约.md 一致。
- 秒杀活动创建、详情、发起秒杀、查询结果接口是否可用。
- 数据库表结构是否覆盖 products、product_stocks、seckill_activities、orders、order_items、mq_message_logs。
- Redis key 是否按 docs/Redis-Key设计.md 设计。
- MQ exchange、queue、routing key、消息体是否按 docs/MQ消息契约.md 设计。
- 是否为 C 提供可并行调试的接口返回。
- 是否提交 docs/docs-B/ 下的本周任务报告和测试报告。

第 2 周重点：
- Redis 库存预热是否可用。
- Lua 原子扣减是否可复现验证不超卖和防重复抢购。
- 秒杀成功后是否投递 MQ 并立即返回 QUEUEING。
- order-service 是否消费消息并落库。
- 消费幂等是否通过 messageId、activityId + userId 或 orderNo 保证。
- 消费失败重试、死信或补偿方案是否有代码或明确文档。
- AI 商品咨询是否具备真实调用、缓存或明确降级能力。
- 是否完成核心演示链路的后端联调。
- 是否提交 docs/docs-B/ 下的阶段任务报告和可复现测试报告。

三、接口一致性审查

请对照 docs/API接口契约.md 输出表格：

| 接口 | 文档要求 | 实际情况 | 是否通过 | 修复建议 |

至少检查：

- `POST /api/products`
- `GET /api/products`
- `GET /api/products/{productId}`
- `PUT /api/products/{productId}/stock`
- `POST /api/seckill/activities`
- `GET /api/seckill/activities/{activityId}`
- `POST /api/seckill/activities/{activityId}/orders`
- `GET /api/seckill/activities/{activityId}/result`
- `GET /api/orders`
- `GET /api/orders/{orderId}`
- `GET /api/orders/admin`
- `POST /api/ai/products/{productId}/consult`

四、Redis、MQ 与一致性风险审查

请检查：

- 是否存在直接数据库扣库存。
- Redis 扣减是否原子。
- 是否能防止超卖。
- 是否能防止同一用户重复抢购。
- MQ 消息是否有 messageId。
- 消费端是否幂等。
- 订单唯一索引是否能兜住重复消息。
- MQ 失败是否有补偿说明。
- 测试结果是否可复现。

五、与 A/C 的依赖影响

请说明：

- B 是否阻塞 A 的联调和报告整合。
- B 是否阻塞 C 的测试页、推送、Apifox。
- A/C 是否反过来阻塞 B。
- 需要谁在什么时候补齐什么。

六、问题列表

请按严重级别列出问题：

| 级别 | 问题 | 证据 | 负责人 | 修复建议 |

级别只能使用：

- P0：阻塞本周验收或合并。
- P1：影响演示质量或下周开发。
- P2：优化项。

七、下周任务建议

请只给成员 B 输出 5-8 条下周 TODO。每条必须具体到接口、服务、数据库、Redis、MQ、测试或文档，不要泛泛建议。

八、合并建议

请明确输出：

- 是否允许 `feature/member-b-seckill-order` 合并到 `dev`。
- 合并前必须补齐什么。
- 合并后需要通知 A/C 什么。
```

