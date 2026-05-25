# Gateway/Auth 可复现测试记录

> 角色：成员 A  
> 范围：Gateway 鉴权策略、JWT 解析、用户服务 `/api/users/me` 直连保护

## 1. 相关测试文件

1. [gateway-service/src/test/java/com/bupt/ecommerce/gateway/filter/GatewayAuthPolicyTest.java](E:/BUPT/web/BUPT-Ecommerce/gateway-service/src/test/java/com/bupt/ecommerce/gateway/filter/GatewayAuthPolicyTest.java)
2. [gateway-service/src/test/java/com/bupt/ecommerce/gateway/filter/JwtTokenUtilTest.java](E:/BUPT/web/BUPT-Ecommerce/gateway-service/src/test/java/com/bupt/ecommerce/gateway/filter/JwtTokenUtilTest.java)
3. [user-service/src/test/java/com/bupt/ecommerce/user/controller/UserControllerTest.java](E:/BUPT/web/BUPT-Ecommerce/user-service/src/test/java/com/bupt/ecommerce/user/controller/UserControllerTest.java)
4. [user-service/src/test/java/com/bupt/ecommerce/user/service/UserAccountServiceTest.java](E:/BUPT/web/BUPT-Ecommerce/user-service/src/test/java/com/bupt/ecommerce/user/service/UserAccountServiceTest.java)

## 2. 测试目标

1. 验证 PUBLIC 接口可免 token 访问。
2. 验证 ADMIN 路由必须管理员角色。
3. 验证 JWT 可签发、可解析、过期失效。
4. 验证 `user-service` 的 `/api/users/me` 只接受 Gateway 注入头。
5. 验证缺少 Gateway 注入头时返回未登录异常。

## 3. 可复现用例

### 3.1 Gateway 公开路由

测试类：`GatewayAuthPolicyTest`

期望：

1. `POST /api/auth/login` 为 PUBLIC。
2. `POST /api/auth/register` 为 PUBLIC。
3. `GET /api/products` 为 PUBLIC。
4. `GET /api/products/{productId}` 为 PUBLIC。
5. `GET /api/seckill/activities/{activityId}` 为 PUBLIC。
6. `GET /swagger-ui/index.html`、`/v3/api-docs`、`/openapi/user` 等 Swagger/OpenAPI 入口为 PUBLIC。

### 3.2 Gateway 管理员路由

测试类：`GatewayAuthPolicyTest`

期望：

1. `POST /api/products` 需要 `ADMIN`。
2. `PUT /api/products/{productId}/stock` 需要 `ADMIN`。
3. `POST /api/seckill/activities` 需要 `ADMIN`。
4. `GET /api/orders/admin` 需要 `ADMIN`。

### 3.3 JWT 生成与解析

测试类：`JwtTokenUtilTest`

期望：

1. 使用同一密钥签发的 token 可成功解析。
2. 解析后 `userId`、`username`、`role` 与原始值一致。
3. 过期 token 必须抛出非法参数异常。

### 3.4 用户服务直连保护

测试类：`UserControllerTest`

期望：

1. 仅传入 Gateway 注入头时，`/api/users/me` 返回当前用户信息。
2. 不传入 Gateway 注入头时，`/api/users/me` 抛出未登录异常。
3. 不再接受客户端直连 `Authorization` 兜底解析。

### 3.5 管理员账号策略

测试类：`UserAccountServiceTest`

期望：

1. 默认管理员账号已预置，不在登录时创建。
2. `admin/admin123456` 可以登录为 `ADMIN`。
3. 未知管理员用户名不能在登录时自动生成账号。

## 4. 复跑方式

建议在项目根目录执行：

```bash
mvn -pl gateway-service,user-service -am test
```

本次已完成复跑，结果为 `BUILD SUCCESS`。

模块测试结果：

| 模块 | 测试结果 |
|---|---|
| `gateway-service` | Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 |
| `user-service` | Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 |

## 5. 结论

这组测试用于固定 Gateway/Auth 的核心契约：

1. 公开接口放行。
2. 管理员接口拦截。
3. JWT 可验签。
4. 用户服务只信任 Gateway 注入头。
5. 默认管理员账号为预置账号，不在登录时创建。
