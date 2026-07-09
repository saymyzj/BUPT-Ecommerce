# 秒杀异常恢复与生产化增强设计

## Summary

在不破坏现有 Apifox、Gateway、SSE、RabbitMQ 和单机 Docker 演示流程的前提下，分三批补齐秒杀资格状态、永久失败补偿、死信闭环、可靠投递、Redis 高可用、公平候补和故障验证。所有增强必须保持现有外部 API 路径、请求字段、响应结构及默认成功链路兼容。

## Complexity

`large`

本任务跨越商品秒杀、订单、推送、Gateway、MySQL、Redis、RabbitMQ、Docker 和压测脚本，包含新的持久化状态机、跨组件一致性、故障恢复和多批次发布，具有较高回归与回滚风险。

## Context

- 当前秒杀服务使用 Redis Lua 完成库存扣减和一人一单，成功后同步等待 RabbitMQ Confirm。
- 当前订单服务通过消息日志和数据库唯一索引保证幂等，失败消息由每分钟定时任务重试。
- 当前 DLQ 已声明但没有消费、查询、重放和终态处理闭环。
- 当前 MQ Confirm 超时会直接恢复 Redis，存在消息实际已到达时误返库存的风险。
- 当前 Redis 为单节点，库存 Key 丢失时拒绝秒杀，没有高可用环境和可信重建流程。
- 当前 100 件库存只放行 100 个请求，其余立即失败，没有候补与服务端故障公平性。
- 成员 A、C 已完成演示准备，默认演示环境、接口和 SSE 事件不能破坏。

## Frozen Compatibility Contract

- 保持现有 API 路径、HTTP 方法、权限、请求字段和字段类型。
- 保持统一响应 `code/message/data` 以及对外 `QUEUEING/CREATED/FAILED` 状态。
- 保持现有 Gateway 路由、服务端口、Apifox 集合和默认演示账号可用。
- 保持现有 RabbitMQ 交换机、队列、Routing Key 和消息必填字段兼容。
- 保持 SSE 事件 `connected`、`order-result` 及现有数据字段兼容。
- 保留根目录 `docker-compose.yml` 的单机 MySQL、Redis、RabbitMQ 快速演示方式。
- 新表和字段采用增量、可默认初始化的方式；旧数据和旧消息必须可读取。
- 公平候补默认关闭，开启后仍保持原秒杀接口响应结构。

## Goals

- 建立持久化资格状态机，明确资格、订单、重试、释放和终态。
- 永久失败时仅在确认无订单后幂等返还一次库存。
- 将补偿任务升级为可分类、可退避、可终止、可多实例安全执行的恢复机制。
- 建立 DLQ 可观测、可查询、可重放、可终结的管理闭环。
- 消除 MQ Confirm 不确定状态下直接返库存导致的超卖风险。
- 提供不替换默认演示环境的 Redis Sentinel 高可用配置和库存重建能力。
- 提供默认关闭的有限公平候补，服务端失败时保留原资格，终态失败后按序递补。
- 通过单元测试、集成测试、故障注入和压测验证库存不变量。

## Non-Goals

- 不实现支付、退款、物流等完整交易闭环。
- 不把课程项目改造成 Kubernetes 或公有云部署。
- 不承诺严格的全局一次且仅一次消息语义，而以幂等和可对账的最终一致性实现。
- 不修改成员 A 已录制视频中的正常业务流程。

## Invariants

- `CREATED 订单数 + 有效预留资格数 + Redis 剩余库存 = 活动初始库存`。
- 同一用户同一活动最多一个有效资格和一笔订单。
- 已存在订单的资格永远不能返库存。
- 同一资格无论补偿、DLQ、人工重放执行多少次，库存最多返还一次。
- 网络超时和 MQ Confirm 超时不能直接推断业务失败。
- 通知失败不能改变订单和库存事实。

## Proposed Approach

### Batch 1: Correctness Closure

- 新增秒杀资格记录及状态机，记录 request/message/order、失败原因和重试计划。
- 秒杀成功后持久化资格；订单创建和消息状态变更同步更新资格。
- 重构补偿任务：错误分类、指数退避、终态、幂等 Redis 释放和结果更新。
- 保持旧请求无需新增字段，由服务端生成稳定 requestId。

### Batch 2: Messaging And Redis Resilience

- 增加死信记录、DLQ 消费、管理员查询与幂等重放。
- 增加可靠事件/投递状态与对账任务，Confirm 不确定时不立即返库存。
- 增加独立 Redis Sentinel Compose 配置、AOF、健康检查和故障说明。
- 增加基于数据库资格与订单事实的库存重建/对账管理能力。

### Batch 3: Fairness And Multi-Instance Safety

- 给补偿和对账任务增加租约/抢占，避免多实例重复处理。
- 增加默认关闭的有限候补队列，使用服务器序号保证顺序。
- 服务端可重试错误保留原资格；终态失败释放后提升候补队首。
- 保持默认演示模式仍为售罄立即返回库存不足。

## Rollout And Fallback

- 每批次独立提交并推送 `feature/member-b-seckill-order`。
- 每批次提交前运行模块测试和冻结协议回归。
- 新能力通过配置开关启用；默认演示配置保持原行为。
- Sentinel 使用独立 Compose 文件，不替换根 `docker-compose.yml`。
- 若某批故障测试失败，不进入下一批，不通过回退其他成员代码来规避。

## Validation Plan

- Maven 全模块单元测试和针对新增状态机、补偿、DLQ、重放、公平性的测试。
- 检查 OpenAPI/接口契约、Apifox 路径、SSE 事件和 RabbitMQ 名称未变化。
- 默认 Docker 环境健康检查及成员 A/C 正常演示主链路回归。
- Redis、RabbitMQ、MySQL 故障注入和恢复验证。
- 100 库存、10000 用户 Gateway 压测，校验订单、库存、资格、重复订单和死信。
- 公平模式单独验证候补顺序，默认模式验证旧行为不变。

## TODO List

### Baseline

- [x] 固定分支、协议、演示环境和测试基线
  Scope: Git、API 文档、Apifox、Compose、现有测试
  Validation: 分支快进到 `dev`，记录冻结契约，确认默认 Compose 未改

### Batch 1

- [x] 增加资格状态机和兼容数据库模型
  Scope: product-seckill-service、order-service、schema
  Validation: 状态转换、唯一约束和旧接口单元测试

- [x] 将订单消费与资格状态关联
  Scope: MQ message、order-service
  Validation: 重复消息、已有订单和结果缓存回归

- [x] 重构补偿任务并实现永久失败幂等返库存
  Scope: order-service、Redis 内部补偿接口
  Validation: 重复补偿十次只返一次，订单存在时不返库存

- [x] 完成第一批兼容回归、提交并推送
  Scope: 全模块、Git
  Validation: 测试通过，默认演示路径不变，远程分支包含提交

### Batch 2

- [x] 建立 DLQ 记录、查询、重放和终态闭环
  Scope: order-service、Gateway 兼容路由、RabbitMQ
  Validation: 非法/失败消息进入死信并可幂等重放

- [x] 增加可靠投递状态和 Confirm 不确定对账
  Scope: product-seckill-service、RabbitMQ、schema
  Validation: 模拟 Confirm 超时但消息到达时不返库存、不重复订单

- [x] 增加 Redis Sentinel 环境和库存恢复/对账
  Scope: Compose、product-seckill-service、order-service、docs
  Validation: 默认 Compose 不变；Sentinel 主节点切换后恢复；库存可重建

- [x] 完成第二批兼容回归、提交并推送
  Scope: 全模块、Git
  Validation: 测试通过，成员 A/C 正常链路回归，远程分支包含提交

### Batch 3

- [x] 增加多实例任务租约
  Scope: order-service、product-seckill-service、schema
  Validation: 两个任务执行者只处理一次同一记录

- [x] 增加默认关闭的有限公平候补
  Scope: product-seckill-service、Redis、配置、结果查询/SSE 兼容
  Validation: 默认模式旧行为不变；公平模式按服务器顺序递补

- [x] 增加故障注入与一致性验证脚本
  Scope: scripts、docs
  Validation: Redis/MQ/MySQL/网络不确定场景及库存不变量通过

- [-] 完成第三批兼容回归、提交并推送
  Scope: 全模块、Git
  Validation: 测试通过，远程分支包含提交

### Final Verification

- [ ] 执行默认模式和增强模式压测
  Scope: scripts、运行环境
  Validation: 100/10000 压测及资格库存不变量通过

- [ ] 编写异常恢复与生产化增强演示说明
  Scope: docs
  Validation: 包含准备、正常链路、故障注入、恢复、查询和回滚步骤

## Notes During Implementation

- 2026-07-09：`feature/member-b-seckill-order` 可从 `dev` 快进，未跟踪的两份报告文档不纳入提交。
- 2026-07-09：默认演示兼容优先于默认开启新行为；候补和 Sentinel 使用独立配置。
- 2026-07-09：第一批全仓库测试通过，共覆盖 Gateway、用户、秒杀、订单、AI 和推送模块；使用 Java 21 运行 Maven 以兼容当前 Mockito/Byte Buddy。
- 2026-07-09：第一批提交 `ad38088` 已推送至 `origin/feature/member-b-seckill-order`。
- 2026-07-09：第二批新增接口均为管理员或内部接口；旧 API、MQ 拓扑、SSE 事件和默认 Compose 未改变。核心模块测试及 HA Compose 配置校验通过。
- 2026-07-09：第二批提交 `e17dd58` 已推送至 `origin/feature/member-b-seckill-order`。
- 2026-07-09：第三批全仓库测试通过；候补默认关闭，压测脚本增加订单、资格、Redis 库存和可靠投递不变量校验。
