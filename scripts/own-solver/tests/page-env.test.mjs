// Phase 1.2 N4 (strategy E): the own solver's page environment — the core's page mode, the
// sandboxed frame's bootstrap (yft-own-solver/page-frame.js), the host page (page-env.js) and
// their match with the app's route table (OwnSolverPageEnvRoutes.kt). Offline, on the fake
// player written for these tests (no YouTube code); the page-frame DOM stub is the harness's.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import vm from "node:vm";
import { pageContext, solveWithOwn } from "../harness.mjs";
import { expectedN, expectedSig, fakePlayer } from "./fake-player.mjs";
import { padded } from "./solver-context.mjs";

const assets = new URL("../../../extractor-master-android/src/main/assets/yft-own-solver/", import.meta.url);
const read = (name) => readFileSync(new URL(name, assets), "utf8");
const lib = read("meriyah.umd.min.js");
const core = read("own.solver.core.js");
const workerSource = read("own-solver-worker.js");
const frameSource = read("page-frame.js");
const hostSource = read("page-env.js");
const routes = readFileSync(
  new URL("../../../extractor-master-android/src/main/kotlin/com/alal/yft/extractor/master/android/solver/OwnSolverPageEnvRoutes.kt", import.meta.url),
  "utf8",
);

const ORIGIN = "https://appassets.androidplatform.net";
const READY = "yft-page-frame-ready";
const TOKEN = "0123456789abcdef0123456789abcdef";
const N = ["ZdZIqFPQK-Ty8wId", "abcDEF0123456789xyz"];
const SIG = ["AOq0QJ8wRAIgXmPlOPSBkkUs1bYFYlJCfe29xx8j7v1pDL0QwbdV96sCIEzpWqMGkFR20CFOg51Tp-7vj_EMu-m37KtXJoOySqa0wWq="];
const own = { lib, core };

// ---- the core's page mode ------------------------------------------------------------------

test("page mode keeps a window's own browser objects and adds only the missing ones", () => {
  const context = vm.createContext({});
  // As on a real window, document cannot be redefined (a vm context's var would stay configurable).
  vm.runInContext(
    "Object.defineProperty(this, 'document', { value: { real: true }, configurable: false }); self = this; navigator = { real: true };",
    context,
  );
  vm.runInContext(lib, context);
  vm.runInContext(core, context);
  vm.runInContext("yftOwnSolver.internals.installEnvironment(this, null, null, true)", context);
  const seen = JSON.parse(vm.runInContext(
    "JSON.stringify({ document: document.real === true, navigator: navigator.real === true, self: self === this, " +
      "window: window === this, location: location.hostname, xhr: typeof XMLHttpRequest })",
    context,
  ));
  assert.deepEqual(seen, { document: true, navigator: true, self: true, window: true, location: "www.youtube.com", xhr: "function" });

  // Worker mode is unchanged: it defines every name, and a window's document cannot be redefined.
  assert.throws(() => vm.runInContext("yftOwnSolver.internals.installEnvironment(this, null, null, false)", context), /redefine/);
});

test("on a window-shaped global the worker mode fails with an environment error; page mode solves with P and B", () => {
  const player = padded(fakePlayer());
  const inputs = { n: N, sig: SIG };
  const context = pageContext(lib);
  vm.runInContext(core, context);
  context.input = JSON.stringify({ player, ...inputs });
  const worker = JSON.parse(vm.runInContext("JSON.stringify(yftOwnSolve(JSON.parse(input)))", context, { timeout: 30_000 }));
  assert.equal(worker.failed, "TypeError");
  assert.deepEqual([worker.n, worker.sig], [{}, {}]);

  for (const extra of [{ page: true }, { page: true, strategy: "B", hint: false }]) {
    const out = solveWithOwn(own, player, inputs, extra);
    assert.equal(out.failed, undefined, JSON.stringify(extra));
    assert.equal(out.error, undefined, JSON.stringify(extra));
    for (const input of N) assert.equal(out.n[input], expectedN(input));
    for (const input of SIG) assert.equal(out.sig[input], expectedSig(input));
  }
});

test("a player whose n needs the DOM fails n in the worker and passes in the page (P and B)", () => {
  const player = padded(fakePlayer({ needsDom: true }));
  const inputs = { n: N, sig: SIG };
  for (const strategy of [{}, { strategy: "B", hint: false }]) {
    const worker = solveWithOwn(own, player, inputs, strategy);
    assert.match(worker.failed ?? "", /(^|,)n:[a-z-]+/, JSON.stringify(strategy));
    assert.deepEqual(worker.n, {});
    for (const input of SIG) assert.equal(worker.sig[input], expectedSig(input));

    const page = solveWithOwn(own, player, inputs, { ...strategy, page: true });
    assert.equal(page.failed, undefined, JSON.stringify(strategy));
    for (const input of N) assert.equal(page.n[input], expectedN(input));
    for (const input of SIG) assert.equal(page.sig[input], expectedSig(input));
  }
});

test("the page-frame stub is shaped like the sandboxed frame", () => {
  const context = pageContext(lib);
  const seen = JSON.parse(vm.runInContext(
    "(function(){var cookie, storage; try { cookie = document.cookie } catch (e) { cookie = e.name }" +
      " try { storage = localStorage } catch (e) { storage = e.name }" +
      " return JSON.stringify({ cookie: cookie, storage: storage, href: location.href, div: document.createElement('div').tagName," +
      " top: top === window, fixed: Object.getOwnPropertyDescriptor(this, 'document').configurable })}).call(this)",
    context,
  ));
  assert.deepEqual(seen, {
    cookie: "SecurityError",
    storage: "SecurityError",
    href: `${ORIGIN}/yft-own-solver/page-frame.html`,
    div: "DIV",
    top: true,
    fixed: false,
  });
});

// ---- the sandboxed frame (page-frame.js) -----------------------------------------------------

/** The worker's reply to [job] (own-solver-worker.js), for parity. */
function workerReply(job) {
  const replies = [];
  const context = vm.createContext({});
  vm.runInContext("var self = this;", context);
  context.postMessage = (message) => replies.push(message);
  vm.runInContext(workerSource, context);
  Object.assign(context, { lib, core, input: JSON.stringify(job) });
  vm.runInContext("self.onmessage({ data: { lib: lib, core: core, input: input } })", context, { timeout: 30_000 });
  return JSON.parse(replies[0]);
}

/** A frame-shaped context running page-frame.js; [setup] may add globals first. */
function frame(setup = () => {}) {
  const posts = [];
  const timers = [];
  let listener = null;
  const host = { postMessage: (message, target) => posts.push({ message, target }) };
  const context = vm.createContext({});
  vm.runInContext("var window = this;", context);
  Object.assign(context, {
    parent: host,
    setTimeout: (fn) => timers.push(fn),
    addEventListener: (type, fn) => {
      if (type === "message") listener = fn;
    },
    removeEventListener: (type, fn) => {
      if (type === "message" && listener === fn) listener = null;
    },
  });
  setup(context);
  vm.runInContext(frameSource, context);
  return {
    context,
    host,
    posts,
    // As the browser does: window.event is the message only while its listeners run.
    deliver(event) {
      if (!listener) return false;
      context.event = event;
      try {
        listener(event);
      } finally {
        delete context.event;
      }
      return true;
    },
    runTimers() {
      while (timers.length) timers.shift()();
    },
    get listening() {
      return listener !== null;
    },
  };
}

const job = (player, extra = {}) => ({ type: "player", keep_prepared: true, player, n: N, sig: SIG, ...extra });
const message = (f, input, token = TOKEN) => ({ source: f.host, origin: ORIGIN, data: { token, lib, core, input: JSON.stringify(input) } });

test("the frame says it is ready, runs the host's job once and replies with the token like the worker", () => {
  const f = frame();
  assert.deepEqual(f.posts, [{ message: READY, target: ORIGIN }]);
  const input = job(padded(fakePlayer()));
  assert.ok(f.deliver(message(f, input)));
  assert.equal(f.listening, false);
  assert.equal(f.posts.length, 1, "the job runs in a later task, not inside the message event");
  f.runTimers();

  assert.equal(f.posts.length, 2);
  const { message: reply, target } = f.posts[1];
  assert.equal(target, ORIGIN);
  assert.equal(reply.token, TOKEN);
  assert.deepEqual(JSON.parse(reply.reply), workerReply(input));
  assert.equal(JSON.parse(reply.reply).n[N[0]], expectedN(N[0]));
});

// Phase 1.2 N5b: strategy B in the frame (from the player text, nothing kept; no tripwire there).
test("the frame runs a strategy B job and keeps nothing", () => {
  const f = frame();
  assert.ok(f.deliver(message(f, job(padded(fakePlayer()), { strategy: "B", hint: false, tripwire: true }))));
  f.runTimers();
  const reply = JSON.parse(f.posts[1].message.reply);
  assert.equal(reply.n[N[0]], expectedN(N[0]));
  assert.equal(reply.sig[SIG[0]], expectedSig(SIG[0]));
  assert.equal(reply.prepared_program, undefined);
  assert.equal(typeof reply.init_errors, "number");
  const bad = frame();
  assert.ok(bad.deliver(message(bad, job("x", { strategy: "C" }))));
  bad.runTimers();
  assert.deepEqual(JSON.parse(bad.posts[1].message.reply), { type: "error", error: "BadJob" });
});

test("the frame takes a job only from the host page, with a well-formed token", () => {
  const f = frame();
  const input = job(padded(fakePlayer()));
  const others = [
    { ...message(f, input), source: {} },
    { ...message(f, input), origin: "null" },
    { ...message(f, input), origin: "https://www.youtube.com" },
    message(f, input, "not-a-token"),
    message(f, input, TOKEN.toUpperCase()),
    { source: f.host, origin: ORIGIN, data: "job" },
    { source: f.host, origin: ORIGIN, data: null },
  ];
  for (const event of others) f.deliver(event);
  f.runTimers();
  assert.equal(f.posts.length, 1);
  assert.equal(f.listening, true);
  assert.equal(typeof f.context.yftOwnSolver, "undefined", "nothing was evaluated");
});

test("player code cannot read the token, forge the reply or replace the reply path", () => {
  const hostile = [
    "var leaked=window.event&&window.event.data?window.event.data.token:\"none\";",
    "var forged=window.parent.postMessage({token:leaked,reply:'{\"type\":\"result\",\"n\":{},\"sig\":{}}'},\"*\");",
    "JSON.stringify=function(){return\"{}\"};",
    "Function.prototype.bind=function(){return function(){}};",
    "window.parent={postMessage:function(){}};",
    "window.setTimeout=function(){};",
  ].join("");
  const player = padded(fakePlayer().replace("var window=this;", `var window=this;${hostile}`));
  const f = frame();
  f.deliver(message(f, job(player)));
  f.runTimers();

  const forged = f.posts.filter((post) => post.message?.token === "none");
  assert.equal(forged.length, 1, "the hostile statements ran");
  const genuine = f.posts.filter((post) => post.message?.token === TOKEN);
  assert.equal(genuine.length, 1);
  const reply = JSON.parse(genuine[0].message.reply);
  assert.equal(reply.type, "result");
  for (const input of N) assert.equal(reply.n[input], expectedN(input));
});

test("WebRTC is removed before player code, and a frame that keeps it never runs the job", () => {
  const open = frame((context) => {
    context.RTCPeerConnection = function RTCPeerConnection() {};
    context.webkitRTCPeerConnection = context.RTCPeerConnection;
  });
  open.deliver(message(open, job(padded(fakePlayer()))));
  open.runTimers();
  assert.equal(JSON.parse(open.posts[1].message.reply).type, "result");
  assert.equal(typeof open.context.RTCPeerConnection, "undefined");
  assert.equal(typeof open.context.webkitRTCPeerConnection, "undefined");

  const kept = frame((context) => {
    Object.defineProperty(context, "RTCPeerConnection", { value: function RTCPeerConnection() {}, configurable: false });
  });
  kept.deliver(message(kept, job(padded(fakePlayer()))));
  kept.runTimers();
  assert.deepEqual(JSON.parse(kept.posts[1].message.reply), { type: "error", error: "WebRtcOpen" });
  assert.equal(typeof kept.context.yftOwnSolver, "undefined");
});

test("bad jobs and broken bundles give an error class, never a message", () => {
  const f = frame();
  f.deliver({ source: f.host, origin: ORIGIN, data: { token: TOKEN, lib, core: "throw new TypeError('secret value')", input: "{}" } });
  f.runTimers();
  assert.deepEqual(JSON.parse(f.posts[1].message.reply), { type: "error", error: "TypeError" });

  const g = frame();
  g.deliver(message(g, { type: "other" }));
  g.runTimers();
  assert.deepEqual(JSON.parse(g.posts[1].message.reply), { type: "error", error: "BadJob" });
});

// ---- the host page (page-env.js) -------------------------------------------------------------

function hostPage({ bridge = true, fetchFails = false } = {}) {
  const log = [];
  const fetched = [];
  const bridged = [];
  const toFrame = [];
  let listener = null;
  const frameWindow = { postMessage: (data, target) => toFrame.push({ data, target }) };
  const element = {
    style: {},
    contentWindow: null,
    setAttribute: (name, value) => log.push(`attr ${name}=${value}`),
    set src(value) {
      log.push(`src ${value}`);
    },
  };
  const context = vm.createContext({});
  vm.runInContext("var window = this;", context);
  Object.assign(context, {
    ...(bridge ? { yftPageSolver: { postMessage: (text) => bridged.push(text) } } : {}),
    crypto: { getRandomValues: (bytes) => { for (let i = 0; i < bytes.length; i += 1) bytes[i] = i * 17; return bytes; } },
    fetch: (name, options) => {
      fetched.push({ name, options });
      if (fetchFails) return Promise.reject(new TypeError("offline"));
      return Promise.resolve({ ok: true, text: () => Promise.resolve(`text of ${name}`) });
    },
    document: {
      createElement: (tag) => {
        log.push(`create ${tag}`);
        return element;
      },
      body: {
        appendChild: (child) => {
          log.push("append");
          child.contentWindow = frameWindow;
          return child;
        },
      },
    },
    addEventListener: (type, fn) => {
      if (type === "message") listener = fn;
    },
    removeEventListener: (type, fn) => {
      if (type === "message" && listener === fn) listener = null;
    },
  });
  vm.runInContext(hostSource, context);
  return { log, fetched, bridged, toFrame, frameWindow, send: (event) => listener && listener(event), get listening() { return listener !== null; } };
}

const settleHost = () => new Promise((resolve) => setTimeout(resolve, 10));

test("without the app's reply object the host page does nothing", async () => {
  const page = hostPage({ bridge: false });
  await settleHost();
  assert.deepEqual(page.log, []);
  assert.deepEqual(page.fetched, []);
});

test("the host page sandboxes the frame first, loads three files and sends the job only when the frame is ready", async () => {
  const page = hostPage();
  assert.deepEqual(page.log, [
    "create iframe",
    "attr sandbox=allow-scripts",
    "attr referrerpolicy=no-referrer",
    "src page-frame.html",
    "append",
  ]);
  await settleHost();
  assert.deepEqual(page.fetched.map((entry) => entry.name), ["meriyah.umd.min.js", "own.solver.core.js", "page-env-input.json"]);
  assert.ok(page.fetched.every((entry) => entry.options.credentials === "omit" && entry.options.cache === "no-store"));
  assert.equal(page.toFrame.length, 0, "no job before the frame is ready");

  page.send({ source: {}, data: READY });
  assert.equal(page.toFrame.length, 0, "ready from another window is ignored");
  page.send({ source: page.frameWindow, data: READY });
  assert.equal(page.toFrame.length, 1);
  const { data, target } = page.toFrame[0];
  assert.equal(target, "*");
  assert.match(data.token, /^[0-9a-f]{32}$/);
  assert.deepEqual(
    { lib: data.lib, core: data.core, input: data.input },
    { lib: "text of meriyah.umd.min.js", core: "text of own.solver.core.js", input: "text of page-env-input.json" },
  );
  page.send({ source: page.frameWindow, data: READY });
  assert.equal(page.toFrame.length, 1, "the job goes once");
});

test("the host page passes on only the frame's first reply with the token", async () => {
  const page = hostPage();
  await settleHost();
  page.send({ source: page.frameWindow, data: READY });
  const token = page.toFrame[0].data.token;
  page.send({ source: {}, data: { token, reply: "from elsewhere" } });
  page.send({ source: page.frameWindow, data: { token: "0".repeat(32), reply: "forged" } });
  page.send({ source: page.frameWindow, data: "forged" });
  assert.deepEqual(page.bridged, []);
  page.send({ source: page.frameWindow, data: { token, reply: '{"type":"result","n":{},"sig":{}}' } });
  page.send({ source: page.frameWindow, data: { token, reply: "second" } });
  assert.deepEqual(page.bridged, ['{"type":"result","n":{},"sig":{}}']);
  assert.equal(page.listening, false);
});

test("a reply that is not text or too long, or a file that cannot load, fails with a class", async () => {
  const notText = hostPage();
  await settleHost();
  notText.send({ source: notText.frameWindow, data: READY });
  notText.send({ source: notText.frameWindow, data: { token: notText.toFrame[0].data.token, reply: { type: "result" } } });
  assert.deepEqual(notText.bridged, ['{"type":"error","error":"NoText"}']);

  const long = hostPage();
  await settleHost();
  long.send({ source: long.frameWindow, data: READY });
  long.send({ source: long.frameWindow, data: { token: long.toFrame[0].data.token, reply: "x".repeat(12 * 1024 * 1024 + 1) } });
  assert.deepEqual(long.bridged, ['{"type":"error","error":"TooLong"}']);

  const offline = hostPage({ fetchFails: true });
  await settleHost();
  assert.deepEqual(offline.bridged, ['{"type":"error","error":"TypeError"}']);
});

// ---- the app's route table -------------------------------------------------------------------

/** A Kotlin `const val NAME: String = "…" + "…"` from the route table, joined. */
function kotlinConstant(name) {
  const match = new RegExp(`const val ${name}: String =\\s*((?:"[^"]*"\\s*\\+?\\s*)+)`).exec(routes);
  assert.ok(match, name);
  return [...match[1].matchAll(/"([^"]*)"/g)].map((part) => part[1]).join("");
}

test("the pages, scripts and policies match the app's route table", () => {
  for (const name of ["page-env.html", "page-frame.html", "page-env.js", "page-frame.js", "meriyah.umd.min.js", "own.solver.core.js"]) {
    assert.ok(routes.includes(`"${name}" to Entry(`), name);
    assert.ok(read(name).length > 0, name);
  }
  for (const name of ["own-solver.html", "own-solver-page.js", "own-solver-worker.js"]) {
    assert.ok(!routes.includes(`"${name}"`), `${name} is the worker's, not served here`);
  }
  assert.equal(kotlinConstant("INPUT_NAME"), "page-env-input.json");
  assert.equal(kotlinConstant("BRIDGE_NAME"), "yftPageSolver");
  assert.equal(kotlinConstant("ORIGIN"), ORIGIN);
  assert.ok(read("page-env.html").includes(`content="${kotlinConstant("HOST_CSP")}"`));
  assert.ok(read("page-frame.html").includes(`content="${kotlinConstant("FRAME_CSP")}"`));
  assert.ok(hostSource.includes("window.yftPageSolver;"));
  assert.ok(frameSource.includes(`var HOST_ORIGIN = "${ORIGIN}";`));
});

test("N5b A.2: the frame passes the job's deadline on like the worker", () => {
  const replyOf = (extra) => {
    const f = frame();
    assert.ok(f.deliver(message(f, job(padded(fakePlayer()), extra))));
    f.runTimers();
    return JSON.parse(f.posts[1].message.reply);
  };
  const far = replyOf({ deadline_ms: 600000 });
  assert.equal(far.failed, undefined);
  assert.equal(far.n[N[0]], expectedN(N[0]));
  const none = replyOf({ deadline_ms: 0 });
  assert.equal(none.failed, "n:budget,sig:budget");
  assert.deepEqual(none.n, {});
  assert.equal(typeof none.prepared_program, "string");
  const b = replyOf({ strategy: "B", hint: false, deadline_ms: 0 });
  assert.equal(b.failed, "n:budget,sig:budget");
  assert.deepEqual(b.sig, {});
  for (const bad of [-1, 2.5, "35000"]) {
    assert.deepEqual(replyOf({ deadline_ms: bad }), { type: "error", error: "BadJob" });
  }
});

test("N5b A.4: the frame refuses a locator: page steps run in full every time", () => {
  const replyOf = (extra) => {
    const f = frame();
    assert.ok(f.deliver(message(f, job(padded(fakePlayer()), extra))));
    f.runTimers();
    return JSON.parse(f.posts[1].message.reply);
  };
  const plain = replyOf({});
  assert.equal(plain.n[N[0]], expectedN(N[0]));
  assert.equal(plain.locator, undefined);
  const part = { s: "P", c: [0], o: "0123abcd" };
  for (const extra of [{ locator: {} }, { locator: { v: 1, n: part } }, { strategy: "B", hint: false, locator: {} }]) {
    assert.deepEqual(replyOf(extra), { type: "error", error: "BadJob" }, JSON.stringify(extra));
  }
});
