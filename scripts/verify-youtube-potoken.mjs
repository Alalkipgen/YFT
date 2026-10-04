#!/usr/bin/env node
// Runs YFT's proof-of-origin page (app/src/main/assets/youtube-potoken) in headless Chromium
// against YouTube's live attestation service and checks that it mints a token (ADR-006).
//
// Usage: node scripts/verify-youtube-potoken.mjs [videoId]
//
// Needs network access to www.youtube.com and the `playwright` package (PLAYWRIGHT_MODULE may
// point at its directory; CHROMIUM at a Chromium binary). The page is served on the app's
// reserved asset origin exactly as the app's WebView serves it, and every other request the
// page makes is refused and counted: the expected count is zero. No token is printed.
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const assets = join(root, "app/src/main/assets/youtube-potoken");
const videoId = process.argv[2] ?? "dQw4w9WgXcQ";
const origin = "https://appassets.androidplatform.net";
const requestKey = "O43z0dpjhgX20SCx4KAo";
const userAgent =
  "Mozilla/5.0 (Linux; Android 14; Pixel 8; wv) AppleWebKit/537.36 (KHTML, like Gecko) " +
  "Version/4.0 Chrome/140.0.0.0 Mobile Safari/537.36";
const policy = "default-src 'none'; script-src 'self' 'unsafe-eval'; connect-src 'self'";

let chromium;
try {
  const require = createRequire(join(process.env.PLAYWRIGHT_MODULE ?? root, "noop.js"));
  ({ chromium } = require("playwright"));
} catch {
  console.log("playwright is not installed; set PLAYWRIGHT_MODULE to its node_modules folder");
  process.exit(2);
}

async function text(url, init = {}) {
  const response = await fetch(url, init);
  if (!response.ok) throw new Error(`${new URL(url).pathname} answered ${response.status}`);
  return response.text();
}

// The attestation key comes from today's player, as the app reads it.
const widget = await text("https://www.youtube.com/iframe_api");
const playerId = /\\\/s\\\/player\\\/([A-Za-z0-9_-]{4,32})\\\//.exec(widget)?.[1];
const player = await text(
  `https://www.youtube.com/s/player/${playerId}/player-plasma-ias-phone-en_US.vflset/base.js`,
);
const key = /"X-Goog-Api-Key"\]?\s*[:=]\s*"([A-Za-z0-9_-]{20,60})"/.exec(player)?.[1];
if (!key) {
  console.log(`Player ${playerId}: attestation key NOT found`);
  process.exit(1);
}
const headers = {
  "content-type": "application/json+protobuf",
  "x-goog-api-key": key,
  "x-user-agent": "grpc-web-javascript/0.1",
  "user-agent": userAgent,
};
const created = JSON.parse(
  await text("https://www.youtube.com/api/jnn/v1/Create", {
    method: "POST",
    headers,
    body: JSON.stringify([requestKey]),
  }),
);
const fields =
  typeof created[1] === "string"
    ? JSON.parse(
        Buffer.from(
          Buffer.from(created[1], "base64").map((byte) => (byte + 97) & 0xff),
        ).toString("utf8"),
      )
    : created[0];
const interpreter = (fields[1] ?? []).find((value) => typeof value === "string" && value);
const challenge = JSON.stringify({ interpreter, program: fields[4], globalName: fields[5] });
console.log(`Player ${playerId}: challenge with inline interpreter: ${Boolean(interpreter)}`);

const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROMIUM });
const context = await browser.newContext({ userAgent });
const page = await context.newPage();
const waiting = new Map();
const early = new Map();
await page.exposeFunction("__yftPost", (message) => {
  const reply = JSON.parse(message);
  const resolve = waiting.get(reply.id);
  if (resolve) resolve(reply);
  else early.set(reply.id, reply);
});
await page.addInitScript(() => {
  window.YftPoTokenBridge = { post: (message) => window.__yftPost(message) };
});
let refused = 0;
await context.route("**/*", (route) => {
  const url = new URL(route.request().url());
  const files = { "/yft-potoken/potoken.html": "text/html", "/yft-potoken/potoken-page.js": "text/javascript" };
  const serve = (contentType, body) =>
    route.fulfill({ status: 200, contentType, body, headers: { "Content-Security-Policy": policy } });
  if (url.origin === origin && files[url.pathname]) {
    return serve(files[url.pathname], readFileSync(join(assets, url.pathname.split("/").pop())));
  }
  if (url.origin === origin && url.pathname === "/yft-potoken/challenge.json") {
    return serve("application/json", challenge);
  }
  refused += 1;
  return route.abort();
});
const reply = (id) =>
  early.has(id)
    ? Promise.resolve(early.get(id))
    : Promise.race([
        new Promise((resolve) => waiting.set(id, resolve)),
        new Promise((_, reject) => setTimeout(() => reject(new Error(`step ${id} timed out`)), 30000)),
      ]);

const started = Date.now();
await page.goto(`${origin}/yft-potoken/potoken.html`);
await reply(0);
await page.evaluate(() => window.yftPoToken.snapshot(1));
const answer = await reply(1);
if (!answer.value) throw new Error(`BotGuard did not answer (${answer.error})`);
const integrity = JSON.parse(
  await text("https://www.youtube.com/api/jnn/v1/GenerateIT", {
    method: "POST",
    headers,
    body: JSON.stringify([requestKey, answer.value]),
  }),
);
await page.evaluate((token) => window.yftPoToken.createMinter(2, token), integrity[0]);
const minter = await reply(2);
await page.evaluate((binding) => window.yftPoToken.mint(3, binding), videoId);
const minted = await reply(3);
await page.close();
await browser.close();

const valid = typeof minted.value === "string" && /^[A-Za-z0-9_-]{16,4096}={0,2}$/.test(minted.value);
console.log(
  `Integrity token lifetime ${integrity[1]} s; minter ${minter.value ?? minter.error}; ` +
    `token ${valid ? "minted" : `NOT minted (${minted.error})`} in ${Date.now() - started} ms; ` +
    `requests refused: ${refused}`,
);
process.exit(valid && refused === 0 ? 0 : 1);
