# 数据库 DDL 与初始化数据设计

## 1. 文档目标

本文档用于指导本地 MySQL 建表、初始化演示数据、演示前清理和恢复。

本文档不替代 `docs/数据库设计.md`，而是把其中的表结构落成可执行 SQL 设计。

## 2. 建表原则

1. 使用 MySQL 8.x。
2. 表名、字段名、状态枚举遵守 `docs/数据库设计.md`。
3. 关键唯一索引必须存在。
4. 演示数据必须可重复初始化。
5. 不依赖 JPA 自动建表。

## 3. B 负责表 DDL

以下 SQL 可放入后续 `schema.sql`。

```sql
CREATE TABLE IF NOT EXISTS products (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  description TEXT NULL,
  price DECIMAL(10, 2) NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS product_stocks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  total_stock INT NOT NULL,
  available_stock INT NOT NULL,
  reserved_stock INT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_product_stocks_product_id (product_id)
);

CREATE TABLE IF NOT EXISTS seckill_activities (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  activity_name VARCHAR(128) NOT NULL,
  seckill_price DECIMAL(10, 2) NOT NULL,
  seckill_stock INT NOT NULL,
  start_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  KEY idx_seckill_activities_product_id (product_id)
);

CREATE TABLE IF NOT EXISTS orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  activity_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL,
  total_amount DECIMAL(10, 2) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_orders_order_no (order_no),
  UNIQUE KEY uk_orders_user_activity (user_id, activity_id),
  KEY idx_orders_user_id (user_id),
  KEY idx_orders_activity_id (activity_id),
  KEY idx_orders_status (status)
);

CREATE TABLE IF NOT EXISTS order_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  unit_price DECIMAL(10, 2) NOT NULL,
  created_at DATETIME NOT NULL,
  KEY idx_order_items_order_id (order_id),
  KEY idx_order_items_product_id (product_id)
);

CREATE TABLE IF NOT EXISTS mq_message_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  message_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  business_key VARCHAR(128) NULL,
  status VARCHAR(20) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  payload TEXT NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_mq_message_logs_message_id (message_id),
  UNIQUE KEY uk_mq_message_logs_business_key (business_key),
  KEY idx_mq_message_logs_status (status)
);
```

说明：

1. 外键在本地原型中可不强制声明，避免多服务初始化顺序造成建表失败。
2. 逻辑关系仍按 `docs/数据库设计.md` 维护。
3. 生产环境可根据 DBA 要求补充外键或改用应用层约束。

## 4. 状态枚举约束

应用层必须只写入以下状态。

```text
products.status:
ON_SALE
OFF_SALE

seckill_activities.status:
DRAFT
READY
ONGOING
FINISHED
CANCELLED

orders.status:
QUEUEING
CREATED
FAILED
CANCELLED
PAID

mq_message_logs.status:
RECEIVED
PROCESSED
FAILED
```

## 5. 演示初始化数据

以下 SQL 可放入后续 `demo-data.sql`。

```sql
INSERT INTO products (id, name, description, price, status, created_by, created_at, updated_at)
VALUES
  (20001, 'Demo 秒杀商品', '用于 100 件库存、10000 并发秒杀演示的商品', 6999.00, 'ON_SALE', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  description = VALUES(description),
  price = VALUES(price),
  status = VALUES(status),
  updated_at = NOW();

INSERT INTO product_stocks (product_id, total_stock, available_stock, reserved_stock, version, updated_at)
VALUES
  (20001, 100, 100, 0, 0, NOW())
ON DUPLICATE KEY UPDATE
  total_stock = VALUES(total_stock),
  available_stock = VALUES(available_stock),
  reserved_stock = VALUES(reserved_stock),
  version = version + 1,
  updated_at = NOW();

INSERT INTO seckill_activities (id, product_id, activity_name, seckill_price, seckill_stock, start_time, end_time, status, created_at, updated_at)
VALUES
  (1, 20001, '秒杀活动-20001', 99.90, 100, '2026-05-25 10:00:00', '2026-05-25 12:00:00', 'READY', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  product_id = VALUES(product_id),
  activity_name = VALUES(activity_name),
  seckill_price = VALUES(seckill_price),
  seckill_stock = VALUES(seckill_stock),
  start_time = VALUES(start_time),
  end_time = VALUES(end_time),
  status = VALUES(status),
  updated_at = NOW();
```

固定演示数据：

| 字段 | 值 |
| --- | --- |
| `productId` | `20001` |
| `activityId` | `1` |
| `seckillStock` | `100` |
| `seckillPrice` | `99.90` |
| `normalUserId` | `10001` |
| `adminUserId` | `1` |

## 6. 演示前清理 SQL

演示前需要把订单和消息日志清空。

```sql
DELETE FROM order_items;
DELETE FROM orders;
DELETE FROM mq_message_logs;
```

如果需要重置自增 ID：

```sql
ALTER TABLE order_items AUTO_INCREMENT = 1;
ALTER TABLE orders AUTO_INCREMENT = 1;
ALTER TABLE mq_message_logs AUTO_INCREMENT = 1;
```

## 7. Redis 清理与预热

演示前清理：

```text
DEL seckill:stock:1
DEL seckill:activity:1
DEL seckill:user:1:10001
DEL seckill:result:1:10001
```

如果需要清理所有演示用户：

```text
SCAN MATCH seckill:user:1:*
SCAN MATCH seckill:result:1:*
```

预热后应满足：

```text
seckill:stock:1 = 100
seckill:activity:1 存在活动信息
```

## 8. RabbitMQ 清理

演示前检查：

```text
seckill.order.create.queue 无旧消息堆积
seckill.order.create.dlq 无旧失败消息
```

如果队列有旧消息，应通过 RabbitMQ 管理台清空。

## 9. 恢复到标准演示状态

标准演示状态：

```text
商品存在：productId=20001
商品库存：100
活动存在：activityId=1
活动库存：100
Redis 秒杀库存：100
orders 表为空
order_items 表为空
mq_message_logs 表为空
RabbitMQ 队列为空
```

恢复顺序：

```text
1. 执行 schema.sql。
2. 执行清理 SQL。
3. 执行 demo-data.sql。
4. 清理 Redis seckill:* key。
5. 启动后端服务。
6. 创建或触发活动预热。
7. 检查 Redis 库存为 100。
8. 检查 RabbitMQ 队列为空。
```

## 10. 验证 SQL

### 10.1 订单数量

```sql
SELECT COUNT(*) AS order_count
FROM orders
WHERE activity_id = 1;
```

预期：

```text
order_count <= 100
```

### 10.2 重复订单检查

```sql
SELECT user_id, activity_id, COUNT(*) AS cnt
FROM orders
GROUP BY user_id, activity_id
HAVING cnt > 1;
```

预期：

```text
返回空结果
```

### 10.3 MQ 消费状态

```sql
SELECT status, COUNT(*) AS count
FROM mq_message_logs
GROUP BY status;
```

预期：

```text
PROCESSED 数量与成功订单数一致
FAILED 数量可解释
```
