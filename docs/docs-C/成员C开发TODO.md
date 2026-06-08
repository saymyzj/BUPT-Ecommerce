# 成员 C 开发 TODO

本文档按 `README.md`、`docs/API接口契约.md`、`docs/Apifox接口清单.md` 和成员 C 工作边界维护。测试页、推送、AI、Apifox 和演示材料必须服务于真实链路，不得用静态数据冒充订单或 AI 能力。

## 1. 当务之急

- [x] 同步最新集成基线到本地 `check`。
- [x] 在 `feature/member-c-ai-push-demo` 分支上开发成员 C 内容。
- [x] 确认测试页所有接口默认走 Gateway：`http://localhost:8080`。
- [x] 将 `simple-test-page/index.html` 从占位页改为联调测试页。
- [x] 测试页支持普通用户注册、普通用户登录、管理员登录和当前用户查询。
- [x] 测试页支持管理员创建商品、设置库存、创建秒杀活动。
- [x] 测试页支持商品列表、商品详情、活动详情查询。
- [x] 测试页支持发起秒杀，并只展示 `queued` / `QUEUEING`。
- [x] 测试页支持订单列表、订单详情、管理员订单查询。
- [x] 测试页支持 AI 商品咨询调用。
- [x] 测试页使用 `fetch` 流式读取 SSE，并携带 `Authorization: Bearer <token>`。
- [x] 新建 `apifox/` 目录并补充整理说明。
- [x] 新增 `docs/docs-C/Apifox测试指南.md`。
- [x] 新增 `docs/docs-C/实时推送设计.md`。
- [x] 新增 `docs/docs-C/AI智能导购设计.md`。
- [x] 新增 `docs/docs-C/演示视频脚本.md`。

## 2. 推送联动

- [x] `push-service` 按 `X-User-Id` 维护 SSE 连接。
- [x] `push-service` 订阅成功只发送 `CONNECTED`，不发送订单成功。
- [x] `push-service` 新增内部订单事件发布接口。
- [x] `order-service` 在 MQ 消费并订单落库后调用推送服务。
- [x] 推送事件使用 `ORDER_CREATED`，并携带 `activityId`、`orderId`、`orderNo`、`userId`、`status`、`message`。
- [x] 测试页只有收到 `order-result` 事件后才更新订单成功状态。
- [x] 联调验证 A 用户不会收到 B 用户的订单事件。
- [x] 联调验证 SSE 断开后重新连接仍可继续接收新订单事件。
- [x] 补充推送失败时的演示说明：如果推送失败，仍可通过订单查询验证结果。

## 3. AI 智能导购

- [x] `ai-service` 不再返回 `code=0` 的硬编码占位回答。
- [x] 未配置 `LLM_BASE_URL` / `LLM_API_KEY` 时返回 `code=30001` 和降级回答。
- [x] 配置 LLM 时调用 OpenAI 兼容 `chat/completions` 接口。
- [x] AI 请求基于商品详情上下文和用户问题构造。
- [x] 增加本地内存问答缓存；对外响应仍按契约只返回 `answer`。
- [x] 使用真实 LLM 配置完成一次端到端 AI 咨询截图。
- [x] 后续如时间允许，将内存缓存替换或扩展为 Redis key：`ai:product:qa:{productId}:{questionHash}`。
- [x] 补充用户级限流、排队、超时降级的报告截图或说明。

## 4. Apifox

- [x] 按文档确认分组：
  - `01 认证与用户`
  - `02 商品与库存`
  - `03 秒杀活动`
  - `04 订单查询`
  - `05 AI 智能导购`
  - `06 实时推送`
  - `07 管理员流程`
  - `08 完整演示流程`
- [x] 在 `docs/docs-C/Apifox测试指南.md` 中记录环境变量和提取字段。
- [x] 在 `apifox/README.md` 中记录导出文件存放规则。
- [x] 在 Apifox 客户端中创建完整集合。
- [x] 设置环境变量：`baseUrl`、`token`、`adminToken`、`userId`、`productId`、`activityId`、`orderId`。
- [x] 为登录、创建商品、创建活动、订单查询配置变量提取。
- [x] 导出 Apifox 集合文件并放入 `apifox/`。
- [x] 使用 Apifox 跑通 `08 完整演示流程`。

## 5. 演示材料

- [x] 编写 `docs/docs-C/演示视频脚本.md`。
- [x] 准备服务启动截图。
- [x] 准备测试页登录成功截图。
- [x] 准备管理员创建商品、设置库存、创建活动截图。
- [x] 准备秒杀返回 `QUEUEING` 截图。
- [x] 准备 SSE 收到 `ORDER_CREATED` 推送截图。
- [x] 准备用 `orderId` 查询订单详情截图。
- [x] 准备 AI 咨询成功或明确降级截图。
- [x] 准备 Apifox 8 个分组和环境变量截图。
- [x] 录制 3-5 分钟演示视频。
- [x] 补齐 `docs/docs-C/第1周任务报告.md`。
- [x] 补齐 `docs/docs-C/第1周测试报告.md`。
- [x] 补齐 `docs/docs-C/第2周任务报告.md`。
- [x] 补齐 `docs/docs-C/第2周测试报告.md`。

## 6. 验证

- [x] `mvn test` 通过，所有后端模块编译成功。
- [x] 测试页 JavaScript 语法检查通过。
- [x] `git diff --check` 无空白错误。
- [x] 本地启动 MySQL、Redis、RabbitMQ 后完成真实全链路联调。
- [x] 使用测试页验证：登录 -> 建立推送 -> 发起秒杀 -> 收到推送 -> 查询订单 -> AI 咨询。
- [x] 使用 Apifox 验证完整演示流程。

## 7. Git

- [x] 当前开发分支：`feature/member-c-ai-push-demo`。
- [x] 本地已创建并同步 `check` 基线。
- [ ] 提交前再次执行 `git status` 和 `git diff`。
- [ ] 提交成员 C 变更。
- [ ] 推送 `feature/member-c-ai-push-demo`。
- [ ] 向 `dev` 发起 PR。
