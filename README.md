# BUPT-Ecommerce

> 项目方向：题目一，AI 驱动的“高并发秒杀与智能电商”后台系统  
> 当前状态：核心链路已完成，进入联调收口阶段  
> 项目策略：核心能力真实实现，复杂高并发场景采用可配置模拟验证；前期先做可验收的 2D/轻量前端与后端服务骨架，后续保留扩展能力。

## 一、项目概述

本项目面向高并发电商秒杀与智能导购场景，开发一个包含统一网关、用户认证、商品秒杀、订单异步落库、AI 商品咨询、实时推送和简单测试页面的后端系统。

系统需要实现：

1. 统一入口与用户认证。
2. 商品管理、库存管理和秒杀活动管理。
3. Redis 原子扣减库存，防止超卖。
4. MQ 异步创建订单，保证削峰。
5. 订单创建结果实时推送。
6. AI 商品咨询与购买建议。
7. Apifox 测试集与演示链路整理。
8. 简单测试页面辅助登录、秒杀、推送和 AI 咨询验证。

## 当前完成状态

当前原型已完成核心验收链路，重点能力如下：

1. Gateway 统一入口、JWT 鉴权、角色权限和用户上下文注入。
2. user-service 用户注册、登录、管理员初始化和用户表持久化。
3. product-seckill-service 商品、库存、秒杀活动、Redis 预热和 Lua 原子扣减。
4. order-service MQ 消费、订单落库、幂等、结果回写和订单查询。
5. push-service 基于 SSE 推送订单创建结果。
6. ai-service 商品上下文咨询、Redis 缓存和明确降级。
7. simple-test-page 和 Apifox 均以 Gateway `http://localhost:8080` 为最终演示入口。
8. 单元层自动化测试已覆盖 Gateway 策略、用户持久化、秒杀错误码、订单幂等、订单权限、AI 契约和服务内权限二次校验。

最终演示和环境恢复请优先查看：

1. [docs/最终验收主链路说明.md](docs/最终验收主链路说明.md)
2. [docs/演示环境恢复指南.md](docs/演示环境恢复指南.md)
3. [docs/部署与启动指南.md](docs/部署与启动指南.md)

一句话：

> 做一个“Gateway + 用户认证 + 秒杀库存 + MQ 异步订单 + 实时推送 + AI 咨询 + 测试页”的高并发智能电商后台系统。

## 二、快速启动

### 2.1 克隆仓库

```bash
git clone <你的仓库地址>
cd BUPT-Ecommerce
```

### 2.2 先读文档，再动代码

开始写代码前，必须先阅读：

1. [docs/总体架构设计.md](docs/总体架构设计.md)
2. [docs/API接口契约.md](docs/API接口契约.md)
3. [docs/统一响应格式与错误码.md](docs/统一响应格式与错误码.md)
4. [docs/认证与权限规范.md](docs/认证与权限规范.md)
5. [docs/数据库设计.md](docs/数据库设计.md)
6. [docs/Redis-Key设计.md](docs/Redis-Key设计.md)
7. [docs/MQ消息契约.md](docs/MQ消息契约.md)
8. [docs/部署与启动指南.md](docs/部署与启动指南.md)
9. [docs/生产环境演进方案.md](docs/生产环境演进方案.md)
10. [docs/Apifox接口清单.md](docs/Apifox接口清单.md)
11. [docs/分工指南.md](docs/分工指南.md)

### 2.3 本地启动顺序

建议顺序：

1. MySQL
2. Redis
3. RabbitMQ
4. `gateway-service`
5. `user-service`
6. `product-seckill-service`
7. `order-service`
8. `ai-service`
9. `push-service`
10. `simple-test-page`

也可以直接运行统一启动脚本：

```powershell
.\scripts\start-local.ps1
```

脚本会先拉起 MySQL、Redis、RabbitMQ，再按推荐顺序启动各个后端服务。

当前 `docker-compose.yml` 只编排 MySQL、Redis、RabbitMQ 三个中间件；本地演示“各微服务和中间件启动状态”时统一使用 `scripts/start-local.ps1` 启动全部服务并查看健康检查输出。

### 2.4 秒杀并发验证脚本

仓库提供可执行的 100 件库存、10000 并发验证脚本：

```bash
node scripts/seckill-load-test.js
```

脚本默认 `MODE=gateway`，通过 `http://localhost:8080` 登录管理员和压测用户，携带 JWT 创建商品、设置 100 件库存、创建秒杀活动并发起 10000 个不同用户的秒杀请求，最后输出 `ordersInMySQL`、`redisStock`、`duplicateOrders` 等答辩证据。运行前请先通过 `scripts/start-local.ps1` 启动 MySQL、Redis、RabbitMQ 以及全部后端服务。

如只想快速压测 Redis + MQ 核心链路，可显式使用内部直连模式：

```bash
MODE=internal BASE_URL=http://localhost:8082 node scripts/seckill-load-test.js
```

内部模式会直接向 `product-seckill-service` 注入 `X-User-Id` / `X-User-Role`，仅用于服务内压测，不作为客户端请求路径。

### 2.5 开发分支

三名成员固定分支：

```bash
git checkout dev
git pull origin dev
git checkout -b feature/member-a-architecture
git push -u origin feature/member-a-architecture
```

```bash
git checkout dev
git pull origin dev
git checkout -b feature/member-b-seckill-order
git push -u origin feature/member-b-seckill-order
```

```bash
git checkout dev
git pull origin dev
git checkout -b feature/member-c-ai-push-demo
git push -u origin feature/member-c-ai-push-demo
```

分支对应关系：

| 成员 | 分支 | 技术方向 |
|---|---|---|
| A | `feature/member-a-architecture` | 架构、Gateway、认证权限、接口契约、联调 |
| B | `feature/member-b-seckill-order` | 商品、秒杀、Redis、MQ、订单、AI 咨询 |
| C | `feature/member-c-ai-push-demo` | 推送、测试页面、Apifox、演示体验 |

### 2.6 日常开发流程

每天开始开发前：

```bash
git checkout dev
git pull origin dev
git checkout <自己的分支>
git merge dev
git status
```

如果 `dev` 还不存在，由组内先创建：

```bash
git checkout main
git pull origin main
git checkout -b dev
git push -u origin dev
```

每天提交：

```bash
git status
git add .
git commit -m "feat: 完成xxx功能"
git push origin <自己的分支>
```

每周结束：

1. 三人先把各自分支同步最新 `dev`。
2. 自测通过后，由负责人或对应成员把个人分支合并到 `dev`。
3. 三人共同跑一遍周验收主线。
4. 周验收通过后，再由负责人把 `dev` 合并到 `main`。

```bash
git checkout dev
git pull origin dev
git merge feature/member-a-architecture
git merge feature/member-b-seckill-order
git merge feature/member-c-ai-push-demo
git push origin dev
```

```bash
git checkout main
git pull origin main
git merge dev
git push origin main
```

## 三、有效文档清单

| 文档 | 作用 |
|---|---|
| [docs/总体架构设计.md](docs/总体架构设计.md) | 定义服务拆分、职责边界、调用链路和目录范围 |
| [docs/API接口契约.md](docs/API接口契约.md) | 定义接口路径、参数、响应、权限和变更控制 |
| [docs/统一响应格式与错误码.md](docs/统一响应格式与错误码.md) | 定义全局返回结构与错误码 |
| [docs/认证与权限规范.md](docs/认证与权限规范.md) | 定义 JWT、角色、权限、注销规则 |
| [docs/数据库设计.md](docs/数据库设计.md) | 定义表归属、ER 图、主键、索引和状态枚举 |
| [docs/Redis-Key设计.md](docs/Redis-Key设计.md) | 定义秒杀与缓存 key 语义 |
| [docs/MQ消息契约.md](docs/MQ消息契约.md) | 定义交换机、队列、路由键、消息体和幂等字段 |
| [docs/部署与启动指南.md](docs/部署与启动指南.md) | 定义本地部署、启动顺序和联调流程 |
| [docs/生产环境演进方案.md](docs/生产环境演进方案.md) | 定义高可用、高并发和生产演进说明 |
| [docs/Apifox接口清单.md](docs/Apifox接口清单.md) | 定义 Apifox 分组与演示顺序 |
| [docs/分工指南.md](docs/分工指南.md) | 定义三人分工、文档责任和协作边界 |

## 四、关键开发口径

所有成员必须统一以下口径：

1. 先文档，后代码。
2. 先契约，后实现。
3. 先核心链路，后边缘功能。
4. 所有客户端请求必须经过 Gateway。
5. 秒杀必须使用 Redis 原子扣减，不得直接数据库扣库存。
6. 订单必须通过 MQ 异步创建，不得在秒杀接口中同步落库。
7. 订单创建结果必须能实时推送。
8. AI 咨询必须真实接入或明确标识为降级，不得用硬编码问答充数。
9. 所有接口、字段、状态码、Redis key、MQ 消息体以文档为准。

## 五、开发规范

1. 必须基于文档及规范修改。
2. 保持代码干净清爽。
3. 不得为了通过检查堆砌垃圾代码。

禁止：

- 临时堆叠 `if` 分支
- 硬编码标准问答答案
- 重复 mock 数据
- 无用脚本
- 脏数据兜底
- 大段不可维护代码

4. 不得用 mock 冒充真实能力。
5. 测试结果必须可复现。
6. 提交前必须自测。
7. 测试报告和任务报告每阶段提交前，都必须放在个人的 `docs/` 文件夹内。

## 六、给成员 A 的 AI Prompt

```text
我正在参与一个比赛项目：AI 驱动的“高并发秒杀与智能电商”后台系统。

请严格基于当前仓库中的 README.md 和 docs/ 规范文档进行分析、开发和指导，不要脱离现有设计另起炉灶。

你必须优先阅读以下内容：

1. README.md
2. docs/总体架构设计.md
3. docs/API接口契约.md
4. docs/统一响应格式与错误码.md
5. docs/认证与权限规范.md
6. docs/数据库设计.md
7. docs/Redis-Key设计.md
8. docs/MQ消息契约.md
9. docs/部署与启动指南.md
10. docs/生产环境演进方案.md
11. docs/Apifox接口清单.md

我的身份是“成员 A”，我的固定技术方向是：

- 总体架构
- API 契约
- Gateway
- 用户认证与权限
- 联调与最终裁定

当前项目必须遵守以下关键口径：

- 所有客户端请求必须经过 Gateway
- JWT、角色、权限规则以 docs/认证与权限规范.md 为准
- 接口路径、字段、响应和错误码以 docs/API接口契约.md 为准
- 任何跨服务变更必须先更新文档，再改代码
- 成员 A 对接口契约和最终联调拥有最终裁定权

我的工作边界是：

- 主导总体架构设计、服务拆分、统一入口、权限规范和联调裁定
- 主导最终报告中的架构、部署、生产演进和项目组织章节
- 负责统一各成员接口、返回格式、错误码和变更控制
- 不主写全部商品、秒杀、订单、AI 和推送业务细节

请输出一份详细工作指导，必须包含：

1. 成员 A 当前负责什么，不负责什么
2. 系统总体架构和服务边界
3. JWT、权限、统一响应和网关路由的关键口径
4. 分阶段 TODO LIST，拆成：
   - 当务之急
   - 第一周
   - 第二周
5. 如何与成员 B 对接秒杀、订单、MQ 和数据库契约
6. 如何与成员 C 对接推送、测试页和 Apifox
7. 如何检查接口契约是否被破坏
8. Git 上每天应该怎么操作
9. 当前最容易产生分歧的地方有哪些

请尽量给出按步骤执行的清单，而不是泛泛建议。
```

## 七、给成员 B 的 AI Prompt

```text
我正在参与一个比赛项目：AI 驱动的“高并发秒杀与智能电商”后台系统。

请严格基于当前仓库中的 README.md 和 docs/ 规范文档进行分析、开发和指导，不要脱离现有设计另起炉灶。

你必须优先阅读以下内容：

1. README.md
2. docs/总体架构设计.md
3. docs/API接口契约.md
4. docs/统一响应格式与错误码.md
5. docs/认证与权限规范.md
6. docs/数据库设计.md
7. docs/Redis-Key设计.md
8. docs/MQ消息契约.md
9. docs/部署与启动指南.md
10. docs/生产环境演进方案.md
11. docs/Apifox接口清单.md

我的身份是“成员 B”，我的固定技术方向是：

- 商品与秒杀
- Redis 原子扣减
- MQ 异步订单
- 订单消费与落库
- AI 商品咨询
- 数据库与中间件

当前项目必须遵守以下关键口径：

- 商品、库存、秒杀、订单、AI 接口都必须与文档一致
- 秒杀库存必须走 Redis 原子扣减，不得直接扣数据库
- 订单创建必须通过 MQ 异步化，不得同步等待落库
- 消费端必须幂等，消息体字段以 docs/MQ消息契约.md 为准
- 数据表、主键、索引、状态枚举以 docs/数据库设计.md 为准

我的工作边界是：

- 主写商品、库存、秒杀活动、Redis、MQ、订单、AI 咨询后端逻辑
- 提供可复用的示例返回和 mock 接口支持并行开发
- 配合 A 统一契约，配合 C 提供接口和测试数据
- 不单独修改接口契约、权限规则和网关策略

请输出一份详细工作指导，必须包含：

1. 成员 B 当前负责什么，不负责什么
2. 后端模块拆分和推荐目录结构
3. 秒杀、Redis、MQ、订单和 AI 接口的实现顺序
4. 分阶段 TODO LIST，拆成：
   - 当务之急
   - 第一周
   - 第二周
5. 如何先提供 mock 接口支持前端并行
6. 如何与成员 A 对接接口契约和权限规则
7. 如何与成员 C 对接页面字段和测试数据
8. 如何准备部署、备份、恢复和演示环境
9. Git 上每天应该怎么操作

请尽量给出按步骤执行的清单，而不是泛泛建议。
```

## 八、给成员 C 的 AI Prompt

```text
我正在参与一个比赛项目：AI 驱动的“高并发秒杀与智能电商”后台系统。

请严格基于当前仓库中的 README.md 和 docs/ 规范文档进行分析、开发和指导，不要脱离现有设计另起炉灶。

你必须优先阅读以下内容：

1. README.md
2. docs/总体架构设计.md
3. docs/API接口契约.md
4. docs/统一响应格式与错误码.md
5. docs/认证与权限规范.md
6. docs/数据库设计.md
7. docs/Redis-Key设计.md
8. docs/MQ消息契约.md
9. docs/部署与启动指南.md
10. docs/生产环境演进方案.md
11. docs/Apifox接口清单.md

我的身份是“成员 C”，我的固定技术方向是：

- 前端测试页
- 实时推送
- Apifox
- 演示体验
- 接口联调

当前项目必须遵守以下关键口径：

- 页面和测试页必须按 docs/API接口契约.md 实现
- 实时推送必须和订单链路联动，不得静态展示
- 测试页面只做辅助演示，不得用假数据冒充真实链路
- Apifox 必须按 docs/Apifox接口清单.md 组织
- 演示流程必须能串起登录、商品、秒杀、订单、AI 咨询和推送

我的工作边界是：

- 主写测试页、推送体验、Apifox 集合整理和演示材料
- 根据 B 的接口和 A 的契约完成页面联动
- 负责使用手册前端部分、产品展示截图、演示视频录制
- 不主写后端商品、秒杀、订单、AI 业务逻辑

请输出一份详细工作指导，必须包含：

1. 成员 C 当前负责什么，不负责什么
2. 测试页和 Apifox 的页面/分组清单
3. 推送体验如何设计，如何避免写死结果
4. 分阶段 TODO LIST，拆成：
   - 当务之急
   - 第一周
   - 第二周
5. 如何使用 B 的 mock 接口并行开发
6. 如何与成员 A 对接接口字段和权限展示
7. 如何验证推送和订单链路真的联动
8. 如何准备产品展示截图、使用手册和演示视频
9. Git 上每天应该怎么操作

请尽量给出按步骤执行的清单，而不是泛泛建议。
```
