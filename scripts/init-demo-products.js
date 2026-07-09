#!/usr/bin/env node

const { execFileSync } = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");

const config = {
  baseUrl: process.env.BASE_URL || "http://localhost:8080",
  adminUsername: process.env.ADMIN_USERNAME || "admin",
  adminPassword: process.env.ADMIN_PASSWORD || "admin123456",
  mysqlContainer: process.env.MYSQL_CONTAINER || "bupt-ecommerce-mysql",
  mysqlUser: process.env.MYSQL_USER || "root",
  mysqlPassword: process.env.MYSQL_PASSWORD || "password",
  mysqlDatabase: process.env.MYSQL_DATABASE || "bupt_ecommerce",
  redisContainer: process.env.REDIS_CONTAINER || "bupt-ecommerce-redis",
  skipClear: /^true|1|yes$/i.test(process.env.SKIP_CLEAR || ""),
  purgeMq: !/^false|0|no$/i.test(process.env.PURGE_MQ || "true"),
};

const products = [
  {
    name: "北邮 AI 降噪耳机 Pro",
    price: 399,
    stock: 120,
    description: "适合学生党、通勤和自习室使用。主动降噪，单次续航 8 小时，配合充电盒约 32 小时，低延迟模式适合网课和轻度游戏。",
    seckill: { price: 199, stock: 100 },
  },
  {
    name: "校园轻薄学习平板 11",
    price: 1599,
    stock: 80,
    description: "11 英寸护眼屏，支持手写笔，适合课堂笔记、PDF 阅读、网课和轻办公。重量轻，续航约 10 小时。",
    seckill: { price: 1299, stock: 50 },
  },
  {
    name: "程序员机械键盘 青轴版",
    price: 299,
    stock: 150,
    description: "87 键布局，PBT 键帽，适合编程、文档输入和宿舍桌面使用。声音清脆，夜间使用需要注意环境。",
  },
  {
    name: "智能护眼台灯",
    price: 179,
    stock: 90,
    description: "支持三档色温、无频闪、定时休息提醒，适合宿舍学习、阅读和备考场景。",
  },
  {
    name: "大容量快充充电宝 20000mAh",
    price: 129,
    stock: 200,
    description: "支持 22.5W 快充，双 USB 输出，适合出差、旅行、校园全天使用，可给手机多次充电。",
  },
  {
    name: "运动健康手环 S2",
    price: 229,
    stock: 110,
    description: "支持心率、睡眠、运动记录和消息提醒，续航约 14 天，适合跑步、健身和日常健康管理。",
    seckill: { price: 159, stock: 30 },
  },
];

main().catch((error) => {
  console.error(`初始化失败: ${error.message}`);
  process.exit(1);
});

async function main() {
  assertFetch();
  console.log(`baseUrl=${config.baseUrl}`);
  const adminToken = await loginAdmin();
  console.log("管理员登录成功");

  if (!config.skipClear) {
    clearDemoDatabase();
    clearSeckillRedisKeys();
    if (config.purgeMq) {
      purgeRabbitQueues();
    }
    console.log("旧演示数据已清理");
  } else {
    console.log("SKIP_CLEAR=true，跳过数据清理");
  }

  const adminHeaders = { Authorization: `Bearer ${adminToken}` };
  const created = [];
  for (const item of products) {
    const product = await createProduct(item, adminHeaders);
    await setStock(product.productId, item.stock, adminHeaders);
    const record = {
      productId: product.productId,
      name: item.name,
      price: item.price,
      stock: item.stock,
      activityId: null,
      seckillPrice: null,
      seckillStock: null,
    };
    if (item.seckill) {
      const activity = await createSeckillActivity(product.productId, item.seckill, adminHeaders);
      record.activityId = activity.activityId;
      record.seckillPrice = item.seckill.price;
      record.seckillStock = item.seckill.stock;
    }
    created.push(record);
    console.log(formatCreatedLine(record));
  }

  const outputFile = path.resolve(__dirname, "demo-products-latest.json");
  fs.writeFileSync(outputFile, JSON.stringify({
    generatedAt: new Date().toISOString(),
    baseUrl: config.baseUrl,
    products: created,
  }, null, 2));

  console.log("");
  console.log(`初始化完成，共创建 ${created.length} 个商品。`);
  console.log(`商品与活动 ID 已写入: ${outputFile}`);
  console.log("演示页操作：点击“拉取商品列表”，然后选择需要演示的商品。");
  const firstActivity = created.find((item) => item.activityId);
  if (firstActivity) {
    console.log(`推荐主链路演示商品: productId=${firstActivity.productId}, activityId=${firstActivity.activityId}`);
  }
}

function assertFetch() {
  if (typeof fetch !== "function") {
    throw new Error("需要 Node.js 18+，因为脚本使用内置 fetch。");
  }
}

async function loginAdmin() {
  const result = await request("POST", "/api/auth/admin/login", {
    username: config.adminUsername,
    password: config.adminPassword,
  });
  ensureSuccess(result, "管理员登录");
  return result.payload.data.token;
}

async function createProduct(item, headers) {
  const result = await request("POST", "/api/products", {
    name: item.name,
    price: item.price,
    description: item.description,
  }, headers);
  ensureSuccess(result, `创建商品 ${item.name}`);
  return result.payload.data;
}

async function setStock(productId, stock, headers) {
  const result = await request("PUT", `/api/products/${productId}/stock`, { stock }, headers);
  ensureSuccess(result, `设置库存 productId=${productId}`);
  return result.payload.data;
}

async function createSeckillActivity(productId, seckill, headers) {
  const now = Date.now();
  const result = await request("POST", "/api/seckill/activities", {
    productId,
    startTime: formatLocalDateTime(new Date(now - 5 * 60 * 1000)),
    endTime: formatLocalDateTime(new Date(now + 2 * 60 * 60 * 1000)),
    seckillPrice: seckill.price,
    seckillStock: seckill.stock,
  }, headers);
  ensureSuccess(result, `创建秒杀活动 productId=${productId}`);
  return result.payload.data;
}

async function request(method, apiPath, body, headers = {}) {
  const response = await fetch(`${config.baseUrl}${apiPath}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let payload;
  try {
    payload = text ? JSON.parse(text) : {};
  } catch {
    payload = { code: response.status, message: text };
  }
  return { httpStatus: response.status, payload };
}

function ensureSuccess(result, label) {
  if (result.httpStatus >= 400 || result.payload.code !== 0) {
    throw new Error(`${label}失败: http=${result.httpStatus}, response=${JSON.stringify(result.payload)}`);
  }
}

function clearDemoDatabase() {
  const sql = `
SET FOREIGN_KEY_CHECKS=0;
TRUNCATE TABLE dead_letter_records;
TRUNCATE TABLE mq_message_logs;
TRUNCATE TABLE order_items;
TRUNCATE TABLE orders;
TRUNCATE TABLE seckill_waitlist_entries;
TRUNCATE TABLE seckill_publish_events;
TRUNCATE TABLE seckill_reservations;
TRUNCATE TABLE seckill_activities;
TRUNCATE TABLE product_stocks;
TRUNCATE TABLE products;
TRUNCATE TABLE scheduled_task_locks;
SET FOREIGN_KEY_CHECKS=1;
`;
  mysqlExec(sql);
}

function clearSeckillRedisKeys() {
  let output = "";
  try {
    output = dockerExec([
      "exec",
      config.redisContainer,
      "redis-cli",
      "--scan",
      "--pattern",
      "seckill:*",
    ]);
  } catch (error) {
    console.warn(`Redis key 清理跳过: ${error.message}`);
    return;
  }
  const keys = output.split(/\r?\n/).map((key) => key.trim()).filter(Boolean);
  for (let index = 0; index < keys.length; index += 100) {
    dockerExec([
      "exec",
      config.redisContainer,
      "redis-cli",
      "DEL",
      ...keys.slice(index, index + 100),
    ]);
  }
  console.log(`Redis seckill:* key 已清理 ${keys.length} 个`);
}

function purgeRabbitQueues() {
  const queues = [
    "seckill.order.create.queue",
    "seckill.order.create.dlq",
  ];
  for (const queue of queues) {
    try {
      dockerExec([
        "exec",
        "bupt-ecommerce-rabbitmq",
        "rabbitmqadmin",
        "purge",
        "queue",
        `name=${queue}`,
      ]);
      console.log(`RabbitMQ 队列已清空: ${queue}`);
    } catch (error) {
      console.warn(`RabbitMQ 队列清理跳过 ${queue}: ${error.message}`);
    }
  }
}

function mysqlExec(sql) {
  dockerExec([
    "exec",
    "-i",
    "-e",
    `MYSQL_PWD=${config.mysqlPassword}`,
    config.mysqlContainer,
    "mysql",
    `-u${config.mysqlUser}`,
    config.mysqlDatabase,
  ], { input: sql });
}

function dockerExec(args, options = {}) {
  return execFileSync("docker", args, {
    encoding: "utf8",
    stdio: ["pipe", "pipe", "pipe"],
    ...options,
  }).trim();
}

function formatLocalDateTime(date) {
  const pad = (value) => String(value).padStart(2, "0");
  return [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate()),
  ].join("-") + "T" + [
    pad(date.getHours()),
    pad(date.getMinutes()),
    pad(date.getSeconds()),
  ].join(":");
}

function formatCreatedLine(record) {
  const base = `# productId=${record.productId} | ${record.name} | 库存=${record.stock}`;
  if (!record.activityId) {
    return `${base} | 日常商品`;
  }
  return `${base} | activityId=${record.activityId} | 秒杀=${record.seckillPrice} | 秒杀库存=${record.seckillStock}`;
}
