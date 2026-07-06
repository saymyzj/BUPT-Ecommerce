#!/usr/bin/env node

const { execFileSync } = require("node:child_process");

const mode = (process.env.MODE || "gateway").toLowerCase();

const config = {
  mode,
  baseUrl: process.env.BASE_URL || (mode === "internal" ? "http://localhost:8082" : "http://localhost:8080"),
  stock: Number(process.env.STOCK || 100),
  concurrency: Number(process.env.CONCURRENCY || 10000),
  adminId: Number(process.env.ADMIN_ID || 1),
  adminUsername: process.env.ADMIN_USERNAME || "admin",
  adminPassword: process.env.ADMIN_PASSWORD || "admin123456",
  startUserId: Number(process.env.START_USER_ID || 500000),
  userPrefix: process.env.USER_PREFIX || `loaduser${Date.now()}`,
  userPassword: process.env.USER_PASSWORD || "LoadTest123456",
  userPrepConcurrency: Number(process.env.USER_PREP_CONCURRENCY || 100),
  waitSeconds: Number(process.env.WAIT_SECONDS || 15),
  mysqlContainer: process.env.MYSQL_CONTAINER || "bupt-ecommerce-mysql",
  mysqlUser: process.env.MYSQL_USER || "root",
  mysqlPassword: process.env.MYSQL_PASSWORD || "password",
  mysqlDatabase: process.env.MYSQL_DATABASE || "bupt_ecommerce",
  redisContainer: process.env.REDIS_CONTAINER || "bupt-ecommerce-redis",
};

function assertFetch() {
  if (typeof fetch !== "function") {
    throw new Error("Node.js 18+ is required because this script uses built-in fetch.");
  }
}

async function request(method, path, body, headers = {}) {
  const response = await fetch(`${config.baseUrl}${path}`, {
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

async function requestWithRetry(method, path, body, headers = {}, attempts = 3) {
  let lastError;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      return await request(method, path, body, headers);
    } catch (error) {
      lastError = error;
      if (attempt === attempts) {
        break;
      }
      await sleep(200 * attempt);
    }
  }
  throw lastError;
}

async function loginAdmin() {
  if (config.mode === "internal") {
    return null;
  }
  const result = await requestWithRetry("POST", "/api/auth/admin/login", {
    username: config.adminUsername,
    password: config.adminPassword,
  });
  ensureSuccess(result, "admin login");
  return result.payload.data.token;
}

async function registerAndLoginUser(index) {
  const username = `${config.userPrefix}_${index}`;
  const register = await requestWithRetry("POST", "/api/auth/register", {
    username,
    password: config.userPassword,
  });
  if (register.payload.code !== 0 && register.payload.code !== 409) {
    throw new Error(`register user ${username} failed: ${JSON.stringify(register.payload)}`);
  }

  const login = await requestWithRetry("POST", "/api/auth/login", {
    username,
    password: config.userPassword,
  });
  ensureSuccess(login, `login user ${username}`);
  return login.payload.data.token;
}

async function mapWithLimit(items, limit, mapper) {
  const results = new Array(items.length);
  let nextIndex = 0;
  const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (nextIndex < items.length) {
      const current = nextIndex++;
      results[current] = await mapper(items[current], current);
    }
  });
  await Promise.all(workers);
  return results;
}

async function prepareUserTokens() {
  if (config.mode === "internal") {
    return null;
  }
  const indexes = Array.from({ length: config.concurrency }, (_, index) => index);
  console.log(`preparing ${config.concurrency} user tokens via Gateway login...`);
  return mapWithLimit(indexes, config.userPrepConcurrency, index => registerAndLoginUser(index));
}

async function createActivity() {
  const adminToken = await loginAdmin();
  const adminHeaders = config.mode === "internal" ? {
    "X-User-Id": String(config.adminId),
    "X-User-Role": "ADMIN",
  } : {
    Authorization: `Bearer ${adminToken}`,
  };
  const product = await requestWithRetry("POST", "/api/products", {
    name: `load-test-${Date.now()}`,
    price: 199,
    description: "100 stock / 10000 concurrent seckill load test",
  }, adminHeaders);
  ensureSuccess(product, "create product");
  const productId = product.payload.data.productId;

  const stock = await requestWithRetry("PUT", `/api/products/${productId}/stock`, {
    stock: config.stock,
  }, adminHeaders);
  ensureSuccess(stock, "set stock");

  const now = Date.now();
  const activity = await requestWithRetry("POST", "/api/seckill/activities", {
    productId,
    startTime: formatLocalDateTime(new Date(now - 5000)),
    endTime: formatLocalDateTime(new Date(now + 10 * 60 * 1000)),
    seckillPrice: 99,
    seckillStock: config.stock,
  }, adminHeaders);
  ensureSuccess(activity, "create seckill activity");

  return { productId, activityId: activity.payload.data.activityId };
}

function formatLocalDateTime(date) {
  const pad = value => String(value).padStart(2, "0");
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

function ensureSuccess(result, label) {
  if (result.payload.code !== 0) {
    throw new Error(`${label} failed: ${JSON.stringify(result.payload)}`);
  }
}

async function runConcurrentSeckill(activityId, userTokens) {
  const startedAt = Date.now();
  const tasks = Array.from({ length: config.concurrency }, (_, index) => {
    const userId = config.startUserId + index;
    const headers = config.mode === "internal" ? {
      "X-User-Id": String(userId),
    } : {
      Authorization: `Bearer ${userTokens[index]}`,
    };
    return request("POST", `/api/seckill/activities/${activityId}/orders`, { quantity: 1 }, headers).catch(error => ({
      httpStatus: 0,
      payload: { code: "NETWORK_ERROR", message: error.message },
    }));
  });

  const results = await Promise.all(tasks);
  const elapsedMs = Date.now() - startedAt;
  const codeCounts = new Map();
  let queued = 0;
  for (const result of results) {
    const code = result.payload.code;
    codeCounts.set(code, (codeCounts.get(code) || 0) + 1);
    if (code === 0 && result.payload.message === "queued") {
      queued += 1;
    }
  }
  return { elapsedMs, queued, codeCounts };
}

function dockerExec(args, options = {}) {
  return execFileSync("docker", args, {
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
    ...options,
  }).trim();
}

function mysqlScalar(sql) {
  return dockerExec([
    "exec",
    "-e",
    `MYSQL_PWD=${config.mysqlPassword}`,
    config.mysqlContainer,
    "mysql",
    `-u${config.mysqlUser}`,
    "-N",
    "-B",
    config.mysqlDatabase,
    "-e",
    sql,
  ]);
}

function redisScalar(key) {
  return dockerExec([
    "exec",
    config.redisContainer,
    "redis-cli",
    "GET",
    key,
  ]);
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function printCodeCounts(codeCounts) {
  return [...codeCounts.entries()]
    .sort(([left], [right]) => String(left).localeCompare(String(right)))
    .map(([code, count]) => `${code}:${count}`)
    .join(", ");
}

function assertVerification({ orderCount, duplicateOrderCount, redisStock }) {
  const parsedOrderCount = Number(orderCount);
  const parsedDuplicateOrderCount = Number(duplicateOrderCount);
  const parsedRedisStock = Number(redisStock);
  const failures = [];

  if (!Number.isFinite(parsedOrderCount) || parsedOrderCount > config.stock) {
    failures.push(`ordersInMySQL expected <= ${config.stock}, got ${orderCount}`);
  }
  if (!Number.isFinite(parsedDuplicateOrderCount) || parsedDuplicateOrderCount !== 0) {
    failures.push(`duplicateOrders expected 0, got ${duplicateOrderCount}`);
  }
  if (!Number.isFinite(parsedRedisStock) || parsedRedisStock !== 0) {
    failures.push(`redisStock expected 0, got ${redisStock}`);
  }

  if (failures.length > 0) {
    console.error("verification failed:");
    for (const failure of failures) {
      console.error(`- ${failure}`);
    }
    process.exit(2);
  }
  console.log("verificationStatus=PASS");
}

async function main() {
  assertFetch();
  if (!["gateway", "internal"].includes(config.mode)) {
    throw new Error("MODE must be gateway or internal.");
  }
  console.log(`mode=${config.mode}`);
  console.log(`baseUrl=${config.baseUrl}`);
  console.log(`stock=${config.stock}, concurrency=${config.concurrency}`);

  const userTokens = await prepareUserTokens();
  const { productId, activityId } = await createActivity();
  console.log(`productId=${productId}, activityId=${activityId}`);

  const load = await runConcurrentSeckill(activityId, userTokens);
  console.log(`requestElapsedMs=${load.elapsedMs}`);
  console.log(`queuedResponses=${load.queued}`);
  console.log(`responseCodeCounts=${printCodeCounts(load.codeCounts)}`);

  console.log(`waiting ${config.waitSeconds}s for order-service MQ consumption...`);
  await sleep(config.waitSeconds * 1000);

  const orderCount = mysqlScalar(`SELECT COUNT(*) FROM orders WHERE activity_id = ${activityId};`);
  const duplicateOrderCount = mysqlScalar(`
    SELECT COALESCE(SUM(t.cnt - 1), 0)
    FROM (
      SELECT user_id, COUNT(*) cnt
      FROM orders
      WHERE activity_id = ${activityId}
      GROUP BY user_id
      HAVING COUNT(*) > 1
    ) t;
  `.replace(/\s+/g, " "));
  const redisStock = redisScalar(`seckill:stock:${activityId}`);

  console.log("verification:");
  console.log(`ordersInMySQL=${orderCount}`);
  console.log(`redisStock=${redisStock}`);
  console.log(`duplicateOrders=${duplicateOrderCount}`);
  console.log(`expectedOrders<=${config.stock}`);
  assertVerification({ orderCount, duplicateOrderCount, redisStock });
}

main().catch(error => {
  console.error(error.stack || error.message);
  process.exit(1);
});
