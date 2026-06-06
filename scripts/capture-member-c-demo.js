const fs = require("fs");
const path = require("path");
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");

const root = path.resolve(__dirname, "..");
const imagesDir = path.join(root, "docs", "docs-C", "images");
const videosDir = path.join(root, "docs", "docs-C", "videos");
const pageFile = path.join(root, "simple-test-page", "index.html");

fs.mkdirSync(imagesDir, { recursive: true });
fs.mkdirSync(videosDir, { recursive: true });

const stamp = new Date().toISOString().replace(/[-:TZ.]/g, "").slice(0, 14);
const username = `demo_c_${stamp}`;

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function imageDataUri(file) {
  const data = fs.readFileSync(file).toString("base64");
  return `data:image/png;base64,${data}`;
}

async function waitText(page, selector, predicate, timeout = 30000) {
  await page.waitForFunction(
    ([sel, expected]) => {
      const text = document.querySelector(sel)?.textContent || "";
      if (expected === "__NON_EMPTY__") return text.trim() && text.trim() !== "-";
      return text.includes(expected);
    },
    [selector, predicate],
    { timeout }
  );
}

async function waitLog(page, expected, timeout = 30000) {
  await page.waitForFunction(
    ([value]) => (document.querySelector("#log")?.textContent || "").includes(value),
    [expected],
    { timeout }
  );
}

async function caption(page, text) {
  await page.evaluate((value) => {
    window.__demoCaption.textContent = value;
  }, text);
  await sleep(500);
}

async function focusCard(page, selector) {
  await page.evaluate((sel) => {
    const element = document.querySelector(sel);
    const card = element?.closest(".card") || element;
    card?.scrollIntoView({ block: "start", inline: "nearest" });
    window.scrollBy(0, -10);
  }, selector);
  await sleep(400);
}

async function screenshot(page, name) {
  await page.evaluate(() => window.__redactLog && window.__redactLog());
  await page.screenshot({
    path: path.join(imagesDir, name),
    fullPage: false
  });
}

async function hold(ms) {
  if (process.env.DEMO_FAST === "1") {
    await sleep(Math.min(ms, 800));
    return;
  }
  await sleep(ms);
}

async function showSummary(page) {
  const apifoxFiles = fs.readdirSync(imagesDir)
    .filter((name) => /^屏幕截图 2026-06-06 160\d{3}\.png$/.test(name))
    .sort();
  const flowScreens = apifoxFiles.slice(0, 12);
  const envScreen = apifoxFiles.find((name) => name.includes("160712")) || apifoxFiles[12];
  const groupImage = imageDataUri(path.join(imagesDir, flowScreens[0]));
  const envImage = imageDataUri(path.join(imagesDir, envScreen));
  const thumbnails = flowScreens.map((name, index) => ({
    index: index + 1,
    src: imageDataUri(path.join(imagesDir, name))
  }));

  await page.setContent(`<!doctype html>
    <meta charset="utf-8">
    <style>
      body{margin:0;background:#f4f7fb;font-family:system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;color:#122033;overflow:hidden}
      main{width:1600px;height:980px;position:relative}
      .slide{position:absolute;inset:0;padding:30px 34px 96px;box-sizing:border-box;opacity:0;transition:opacity .35s ease}
      body.summary-step-1 .slide:nth-child(1),
      body.summary-step-2 .slide:nth-child(2),
      body.summary-step-3 .slide:nth-child(3){opacity:1}
      h1{font-size:30px;margin:0 0 8px}
      p{font-size:17px;color:#566579;margin:0 0 18px;line-height:1.55}
      .shot{position:relative;background:white;border:1px solid #d8e1ec;border-radius:8px;padding:10px;box-shadow:0 8px 24px rgba(18,32,51,.08)}
      .shot img{width:100%;height:760px;object-fit:contain;display:block}
      .mask{position:absolute;background:#f7f7f9;border:1px solid #e2e5eb;border-radius:5px}
      .badge{position:absolute;left:22px;bottom:20px;background:#142033;color:#fff;padding:12px 16px;border-radius:8px;font-weight:800;font-size:18px}
      .thumbs{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}
      .thumb{background:white;border:1px solid #d8e1ec;border-radius:8px;padding:8px;position:relative;box-shadow:0 6px 18px rgba(18,32,51,.08)}
      .thumb img{width:100%;height:170px;object-fit:cover;object-position:top left;display:block}
      .num{position:absolute;left:12px;top:12px;background:#7c5cff;color:white;border-radius:999px;width:30px;height:30px;display:grid;place-items:center;font-weight:900}
      .panel{position:absolute;right:34px;bottom:96px;width:520px;background:#fff;border:1px solid #d8e1ec;border-radius:8px;padding:20px;box-shadow:0 8px 24px rgba(18,32,51,.08)}
      .panel h2{font-size:22px;margin:0 0 10px}.panel li{font-size:17px;margin:8px 0}
    </style>
    <main>
      <section class="slide">
        <h1>Apifox：8 个分组与完整演示流程</h1>
        <p>左侧按文档组织 8 个分组，“08 完整演示流程”包含 12 个顺序请求，用于复现登录、商品、秒杀、订单、AI 和推送链路。</p>
        <div class="shot">
          <img src="${groupImage}">
          <div class="mask" style="left:465px;top:515px;width:950px;height:255px"></div>
          <div class="badge">响应中的 token 已打码；保留分组、请求顺序和统一响应结构</div>
        </div>
      </section>
      <section class="slide">
        <h1>Apifox：本地联调环境变量</h1>
        <p>环境选择“BUPT Ecommerce Local”，baseUrl 统一为 Gateway 地址，登录后自动提取 token、adminToken、productId、activityId、orderId。</p>
        <div class="shot">
          <img src="${envImage}">
          <div class="mask" style="left:615px;top:360px;width:350px;height:110px"></div>
          <div class="mask" style="left:970px;top:450px;width:440px;height:95px"></div>
          <div class="badge">敏感 token 已打码；变量名和链路字段保留用于演示</div>
        </div>
      </section>
      <section class="slide">
        <h1>Apifox：12 步完整流程测试截图</h1>
        <p>这 12 张截图对应完整流程的每一步测试结果。最终事实来源仍是订单详情接口和 SSE ORDER_CREATED 推送。</p>
        <div class="thumbs">
          ${thumbnails.map(item => `<div class="thumb"><span class="num">${item.index}</span><img src="${item.src}"></div>`).join("")}
        </div>
        <div class="panel">
          <h2>生产演进</h2>
          <ul>
            <li>Gateway 多实例与统一鉴权</li>
            <li>Redis Sentinel / Cluster</li>
            <li>RabbitMQ 集群与死信补偿</li>
            <li>MySQL 主从与读写分离</li>
            <li>AI Redis 缓存、限流、排队、超时降级</li>
          </ul>
        </div>
      </section>
    </main>
    <script>
      document.body.className = "summary-step-1";
      window.__summaryStep = (step) => {
        document.body.className = "summary-step-" + step;
      };
    </script>`);
}

async function main() {
  const chromePath = process.env.CHROME_PATH || "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
  const launchOptions = fs.existsSync(chromePath)
    ? { headless: true, executablePath: chromePath }
    : { headless: true };
  const browser = await chromium.launch(launchOptions);
  const context = await browser.newContext({
    viewport: { width: 1600, height: 980 },
    recordVideo: { dir: videosDir, size: { width: 1600, height: 980 } }
  });
  const page = await context.newPage();
  await page.goto(`file:///${pageFile.replace(/\\/g, "/")}`);

  await page.addStyleTag({
    content: `
      body { padding-bottom: 82px !important; }
      header { padding: 18px 28px 14px !important; }
      header h1 { font-size: 25px !important; margin-bottom: 8px !important; }
      main {
        grid-template-columns: 430px minmax(0, 1fr) !important;
        gap: 14px !important;
        padding: 14px 18px 96px !important;
        align-items: start !important;
      }
      .stack { gap: 12px !important; }
      .stack:nth-child(2) { position: sticky !important; top: 12px !important; align-self: start !important; }
      .card { padding: 14px !important; border-radius: 8px !important; }
      .card h2 { font-size: 18px !important; margin: 0 0 10px !important; }
      .grid { gap: 8px !important; }
      label { margin-bottom: 4px !important; font-size: 12px !important; }
      input, textarea { padding: 8px 10px !important; font-size: 15px !important; }
      textarea { min-height: 58px !important; }
      button { min-height: 32px !important; padding: 7px 10px !important; font-size: 15px !important; }
      .actions { gap: 8px !important; }
      .state-row { grid-template-columns: repeat(4, minmax(0, 1fr)) !important; gap: 8px !important; }
      .state-item { padding: 10px !important; min-height: 54px !important; }
      #log {
        height: 520px !important;
        max-height: 520px !important;
        font-size: 13px !important;
        line-height: 1.45 !important;
      }
      #__demoCaption {
        position: fixed;
        left: 24px;
        right: 24px;
        bottom: 18px;
        z-index: 99999;
        padding: 14px 18px;
        border-radius: 8px;
        background: rgba(10, 18, 32, .92);
        color: #fff;
        font: 650 18px/1.45 system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
        box-shadow: 0 10px 30px rgba(0,0,0,.25);
      }
    `
  });
  await page.evaluate(() => {
    const caption = document.createElement("div");
    caption.id = "__demoCaption";
    caption.textContent = "成员 C 演示：所有请求通过 Gateway；秒杀接口只返回排队，订单成功必须来自 MQ 落库后的推送或订单查询。";
    document.body.appendChild(caption);
    window.__demoCaption = caption;
    window.__redactLog = () => {
      const log = document.querySelector("#log");
      if (!log) return;
      log.textContent = log.textContent.replace(
        /eyJ[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+/g,
        "eyJ...<masked-token>"
      );
    };
    setInterval(window.__redactLog, 200);
  });

  await page.fill("#username", username);
  await page.fill("#password", "123456");
  await page.fill("#phone", "13800000000");
  await page.fill("#productName", `演示秒杀耳机-${stamp}`);
  await page.fill("#price", "699");
  await page.fill("#stock", "30");
  await page.fill("#seckillStock", "5");
  await page.fill("#seckillPrice", "199");
  await page.fill("#description", "低延迟无线耳机，适合学生通勤、在线学习和限时秒杀演示。");

  await focusCard(page, "#adminLoginBtn");
  await caption(page, "1/7 Gateway 与权限：管理员登录只走 http://localhost:8080，经 JWT 鉴权后才能访问商品、库存和活动管理接口。右侧日志展示统一响应格式 { code, message, data }。");
  await page.click("#adminLoginBtn");
  await waitLog(page, "ADMIN");
  await screenshot(page, "01-admin-login.png");
  await hold(25000);

  await focusCard(page, "#createProductBtn");
  await caption(page, "2/7 管理员准备：创建真实商品、设置真实库存、创建秒杀活动。这里生成的 productId 和 activityId 会继续传入秒杀链路，不使用假数据。");
  await page.click("#createProductBtn");
  await waitText(page, "#stateProductId", "__NON_EMPTY__");
  await page.click("#setStockBtn");
  await sleep(800);
  await page.click("#createActivityBtn");
  await waitText(page, "#stateActivityId", "__NON_EMPTY__");
  await screenshot(page, "02-admin-product-activity.png");
  await hold(35000);

  await focusCard(page, "#registerBtn");
  await caption(page, "3/7 用户准备：普通用户先注册再登录，获取 CUSTOMER token；随后建立 SSE 连接。CONNECTED 只表示推送通道可用，不代表订单成功。");
  await page.click("#registerBtn");
  await sleep(600);
  await page.click("#loginBtn");
  await waitText(page, "#stateRole", "CUSTOMER");
  await focusCard(page, "#connectPushBtn");
  await page.click("#connectPushBtn");
  await waitText(page, "#pushState", "推送已连接");
  await screenshot(page, "03-user-login-sse-connected.png");
  await hold(35000);

  await focusCard(page, "#seckillBtn");
  await caption(page, "4/7 发起秒杀：接口返回 queued / QUEUEING 只说明请求进入异步链路。Redis 原子扣减后，订单创建消息进入 RabbitMQ。");
  await page.click("#seckillBtn");
  await waitText(page, "#stateSeckill", "QUEUEING");
  await screenshot(page, "04-seckill-queueing.png");
  await hold(30000);

  await focusCard(page, "#seckillBtn");
  await caption(page, "5/7 实时推送：order-service 消费 MQ 并落库后调用 push-service；测试页收到 order-result / ORDER_CREATED 才显示订单成功。");
  await waitText(page, "#stateOrder", "CREATED", 30000);
  await screenshot(page, "05-order-created-push.png");
  await hold(35000);

  await focusCard(page, "#orderDetailBtn");
  await caption(page, "6/7 订单复核与 AI：先用推送中的 orderId 查询订单详情，确认 userId、activityId、status；再基于真实商品上下文调用 AI 咨询。");
  await page.click("#orderDetailBtn");
  await sleep(1000);
  await focusCard(page, "#aiBtn");
  await page.click("#aiBtn");
  await waitText(page, "#stateAi", "SUCCESS", 45000).catch(async () => {
    await waitText(page, "#stateAi", "FALLBACK", 5000);
  });
  await screenshot(page, "06-order-detail-ai-consult.png");
  await hold(35000);

  await showSummary(page);
  await sleep(500);
  await screenshot(page, "07-apifox-groups.png");
  await hold(13000);
  await page.evaluate(() => window.__summaryStep(2));
  await sleep(500);
  await screenshot(page, "08-apifox-env.png");
  await hold(13000);
  await page.evaluate(() => window.__summaryStep(3));
  await sleep(500);
  await screenshot(page, "09-apifox-12-steps.png");
  await hold(9000);

  const srt = [
    "1",
    "00:00:00,000 --> 00:00:30,000",
    "Gateway 与权限：管理员登录只走 http://localhost:8080，经 JWT 鉴权后才能访问商品、库存和活动管理接口。",
    "",
    "2",
    "00:00:30,000 --> 00:01:10,000",
    "管理员准备：创建真实商品、设置真实库存、创建秒杀活动，生成 productId 和 activityId。",
    "",
    "3",
    "00:01:10,000 --> 00:01:50,000",
    "用户准备：普通用户注册登录并建立 SSE。CONNECTED 只表示通道可用，不代表订单成功。",
    "",
    "4",
    "00:01:50,000 --> 00:02:25,000",
    "发起秒杀：接口只返回 queued / QUEUEING。Redis 原子扣减后，订单创建消息进入 RabbitMQ。",
    "",
    "5",
    "00:02:25,000 --> 00:03:05,000",
    "实时推送：order-service 消费 MQ 并落库后触发 push-service，页面收到 ORDER_CREATED。",
    "",
    "6",
    "00:03:05,000 --> 00:03:45,000",
    "订单复核与 AI：用 orderId 查询订单详情，再基于真实商品上下文调用 AI 咨询。",
    "",
    "7",
    "00:03:45,000 --> 00:04:25,000",
    "Apifox 展示 8 个分组、环境变量和 12 步完整流程；生产演进包括 Gateway 多实例、Redis/RabbitMQ/MySQL 集群化，以及 AI 缓存、限流、排队和降级。",
    ""
  ].join("\n");
  fs.writeFileSync(path.join(videosDir, "member-c-demo.srt"), srt, "utf8");

  await sleep(1500);
  const video = page.video();
  await context.close();
  await browser.close();
  if (video) {
    const videoPath = await video.path();
    const target = path.join(videosDir, "member-c-demo.webm");
    if (fs.existsSync(target)) fs.rmSync(target);
    fs.renameSync(videoPath, target);
  }

  console.log(JSON.stringify({
    username,
    screenshots: [
      "01-admin-login.png",
      "02-admin-product-activity.png",
      "03-user-login-sse-connected.png",
      "04-seckill-queueing.png",
      "05-order-created-push.png",
      "06-order-detail-ai-consult.png",
      "07-apifox-groups.png",
      "08-apifox-env.png",
      "09-apifox-12-steps.png"
    ],
    video: "member-c-demo.webm",
    subtitles: "member-c-demo.srt"
  }, null, 2));
}

main().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
