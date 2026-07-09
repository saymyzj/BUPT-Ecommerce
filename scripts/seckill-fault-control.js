#!/usr/bin/env node

const { execFileSync } = require("node:child_process");

const action = (process.env.ACTION || process.argv[2] || "status").toLowerCase();
const component = (process.env.COMPONENT || process.argv[3] || "redis").toLowerCase();
const containers = {
  redis: process.env.REDIS_CONTAINER || "bupt-ecommerce-redis",
  mysql: process.env.MYSQL_CONTAINER || "bupt-ecommerce-mysql",
  rabbitmq: process.env.RABBITMQ_CONTAINER || "bupt-ecommerce-rabbitmq",
  "redis-primary": process.env.REDIS_PRIMARY_CONTAINER || "bupt-ecommerce-redis-primary",
};

if (!["status", "stop", "start", "restart"].includes(action)) {
  throw new Error("ACTION must be status, stop, start, or restart");
}
if (!containers[component]) {
  throw new Error(`COMPONENT must be one of: ${Object.keys(containers).join(", ")}`);
}

const container = containers[component];
const args = action === "status"
  ? ["inspect", "-f", "{{.State.Status}}", container]
  : [action, container];

try {
  const output = execFileSync("docker", args, { encoding: "utf8" }).trim();
  console.log(`component=${component}`);
  console.log(`container=${container}`);
  console.log(`action=${action}`);
  console.log(`result=${output || "ok"}`);
} catch (error) {
  process.stderr.write(error.stderr || error.message);
  process.exit(1);
}
