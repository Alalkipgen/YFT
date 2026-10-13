// Phase 1.1 S5: the own solver's worker and host page (yft-own-solver/own-solver-worker.js and
// own-solver-page.js) with the job/reply protocol of OwnSolverProtocol.kt, offline, on the fake
// player written for these tests (no YouTube code).
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import vm from "node:vm";
import { expectedN, expectedSig, fakePlayer } from "./fake-player.mjs";
import { padded } from "./solver-context.mjs";

const assets = new URL("../../../extractor-master-android/src/main/assets/yft-own-solver/", import.meta.url);
const read = (name) => readFileSync(new URL(name, assets), "utf8");
const lib = read("meriyah.umd.min.js");
const core = read("own.solver.core.js");
const workerSource = read("own-solver-worker.js");
const pageSource = read("own-solver-page.js");

const N = ["ZdZIqFPQK-Ty8wId", "abcDEF0123456789xyz"];
const SIG = ["AOq0QJ8wRAIgXmPlOPSBkkUs1bYFYlJCfe29xx8j7v1pDL0QwbdV96sCIEzpWqMGkFR20CFOg51Tp-7vj_EMu-m37KtXJoOySqa0wWq="];

/** One job through a fresh worker-shaped context, as the WebView runs it; returns the reply. */
function runWorker(job, { input = JSON.stringify(job), libText = lib, coreText = core, globals = {} } = {}) {
  const replies = [];
  const context = vm.createContext({ ...globals });
  vm.runInContext("var self = this;", context);
  context.postMessage = (message) => replies.push(message);
  vm.runInContext(workerSource, context);
  vm.runInContext("self.onmessage({ data: { lib: lib, core: core, input: input } })", Object.assign(context, {
    lib: libText,
    core: coreText,
    input,
  }), { timeout: 30_000 });
  assert.equal(replies.length, 1);
  assert.equal(typeof replies[0], "string");
  return JSON.parse(replies[0]);
}

test("a raw job returns verified values and the prepared program when asked", () => {
  const reply = runWorker({ type: "player", keep_prepared: true, player: padded(fakePlayer()), n: N, sig: SIG });

  assert.equal(reply.type, "result");
  assert.equal(reply.failed, undefined);
  for (const input of N) assert.equal(reply.n[input], expectedN(input));
  for (const input of SIG) assert.equal(reply.sig[input], expectedSig(input));
  assert.equal(typeof reply.prepared_program, "string");
  assert.ok(reply.prepared_program.length > 0);
});

test("the prepared program answers a later job without the player", () => {
  const first = runWorker({ type: "player", keep_prepared: true, player: padded(fakePlayer({ style: "es6" })), n: N, sig: SIG });
  const again = runWorker({ type: "prepared", program: first.prepared_program, n: [N[1]], sig: SIG });

  assert.equal(again.type, "result");
  assert.equal(again.n[N[1]], expectedN(N[1]));
  assert.equal(again.sig[SIG[0]], expectedSig(SIG[0]));
  assert.equal(again.prepared_program, undefined);
});

test("no prepared program unless asked, and never after a failed run", () => {
  const plain = runWorker({ type: "player", player: padded(fakePlayer()), n: N, sig: [] });
  assert.equal(plain.prepared_program, undefined);

  const unknown = runWorker({ type: "player", keep_prepared: true, player: padded("(function () { var a = 1; })();"), n: N, sig: SIG });
  assert.equal(unknown.type, "result");
  assert.ok(unknown.failed);
  assert.deepEqual(unknown.n, {});
  assert.deepEqual(unknown.sig, {});
  assert.equal(unknown.prepared_program, undefined);
});

test("a kind SelfCheck refuses is named and empty; the program is still kept", () => {
  // The fake player's sig steps add a character the input never had: SelfCheck refuses sig only.
  const reply = runWorker({ type: "player", keep_prepared: true, player: padded(fakePlayer({ badSig: true })), n: N, sig: SIG });

  assert.match(reply.failed ?? "", /^sig:[a-z-]+$/);
  assert.deepEqual(reply.sig, {});
  for (const input of N) assert.equal(reply.n[input], expectedN(input));
  assert.equal(typeof reply.prepared_program, "string");
});

test("bad jobs, bad JSON and broken bundles give an error class, never a message", () => {
  assert.deepEqual(runWorker({ type: "other" }), { type: "error", error: "BadJob" });
  assert.deepEqual(runWorker(null, { input: "{not json" }), { type: "error", error: "SyntaxError" });
  assert.deepEqual(runWorker({ type: "player", player: "x", n: N, sig: [] }, { coreText: "throw new TypeError('secret value')" }), {
    type: "error",
    error: "TypeError",
  });
  const empty = runWorker({ type: "prepared", program: "", n: N, sig: [] });
  assert.equal(empty.failed, "not prepared");
});

test("only n and sig inputs reach the core: a job cannot inject faults or vectors", () => {
  const reply = runWorker({
    type: "player",
    player: padded(fakePlayer()),
    n: [N[0], N[0], "", 7, N[1]],
    sig: SIG,
    faults: { decoy: "n" },
    vectors: { n: [{ input: N[0], expected: "wrong" }] },
  });
  assert.equal(reply.failed, undefined);
  assert.deepEqual(Object.keys(reply.n).sort(), [...N].sort());
  assert.equal(reply.n[N[0]], expectedN(N[0]));
});

// Phase 1.2 N5b: the ladder's job fields. Strategy B runs from the player text (no prepared
// program in or out); the tripwire makes a player that follows the environment an init error.
test("a job can ask for strategy B and for the tripwire; B keeps nothing", () => {
  const b = runWorker({ type: "player", keep_prepared: true, strategy: "B", hint: false, player: padded(fakePlayer()), n: N, sig: SIG });
  assert.equal(b.n[N[0]], expectedN(N[0]));
  assert.equal(b.sig[SIG[0]], expectedSig(SIG[0]));
  assert.equal(b.prepared_program, undefined);
  assert.equal(typeof b.init_errors, "number");
  assert.deepEqual(runWorker({ type: "prepared", program: "x", strategy: "B", n: N, sig: [] }), { type: "error", error: "BadJob" });
  assert.deepEqual(runWorker({ type: "player", strategy: "C", player: "x", n: N, sig: [] }), { type: "error", error: "BadJob" });
  const env = padded(fakePlayer({ envN: true }));
  for (const extra of [{}, { strategy: "B", hint: false }]) {
    const label = JSON.stringify(extra);
    const plain = runWorker({ type: "player", player: env, n: N, sig: SIG, ...extra });
    assert.equal(plain.n[N[0]], expectedN(N[0]), label);
    const checked = runWorker({ type: "player", player: env, n: N, sig: SIG, tripwire: true, ...extra });
    assert.deepEqual(checked.n, {}, label);
    assert.equal(checked.sig[SIG[0]], expectedSig(SIG[0]), label);
    assert.equal(checked.init_errors, plain.init_errors + 1, label);
    assert.ok(checked.init_error_classes.includes("Tripwire"), label);
  }
});

test("player code cannot replace the worker's reply path", () => {
  const hostile = padded(`${fakePlayer()}\nself.postMessage = function () { throw new Error("hijacked"); };\nJSON.stringify = function () { return "{}"; };`);
  const reply = runWorker({ type: "player", player: hostile, n: N, sig: SIG });
  assert.equal(reply.type, "result");
});

test("the page loads its own four files, posts them to a blob worker and relays the reply", async () => {
  const fetched = [];
  const posted = [];
  const bridge = [];
  class FakeWorker {
    constructor(address) {
      this.address = address;
      FakeWorker.last = this;
    }
    postMessage(message) {
      posted.push(message);
      queueMicrotask(() => this.onmessage({ data: '{"type":"result","n":{},"sig":{}}' }));
    }
    terminate() {
      this.terminated = true;
    }
  }
  const window = {
    YftOwnSolverBridge: { post: (text) => bridge.push(text) },
  };
  const context = vm.createContext({
    window,
    Worker: FakeWorker,
    Blob: class {
      constructor(parts) {
        this.parts = parts;
      }
    },
    URL: Object.assign(function URL() {}, { createObjectURL: () => "blob:yft", revokeObjectURL: () => {} }),
    fetch: (name, options) => {
      fetched.push({ name, options });
      return Promise.resolve({ ok: true, text: () => Promise.resolve(`text of ${name}`) });
    },
  });
  vm.runInContext(pageSource, context);
  await new Promise((resolve) => setTimeout(resolve, 20));

  assert.deepEqual(
    fetched.map((entry) => entry.name),
    ["own-solver-worker.js", "meriyah.umd.min.js", "own.solver.core.js", "own-solver-input.json"],
  );
  assert.ok(fetched.every((entry) => entry.options.credentials === "omit" && entry.options.cache === "no-store"));
  assert.equal(FakeWorker.last.address, "blob:yft");
  // The message object comes from the page's realm; compare its JSON.
  assert.deepEqual(JSON.parse(JSON.stringify(posted)), [
    { lib: "text of meriyah.umd.min.js", core: "text of own.solver.core.js", input: "text of own-solver-input.json" },
  ]);
  assert.deepEqual(bridge, ['{"type":"result","n":{},"sig":{}}']);
  assert.equal(FakeWorker.last.terminated, true);
});

test("the page's routes and files match the app's route table", () => {
  const routes = readFileSync(
    new URL("../../../extractor-master-android/src/main/kotlin/com/alal/yft/extractor/master/android/solver/OwnSolverPageRoutes.kt", import.meta.url),
    "utf8",
  );
  for (const name of ["own-solver.html", "own-solver-page.js", "own-solver-worker.js", "meriyah.umd.min.js", "own.solver.core.js"]) {
    assert.ok(routes.includes(`"${name}"`), name);
    assert.ok(read(name).length > 0, name);
  }
  assert.ok(routes.includes('"own-solver-input.json"'));
  const csp = /CONTENT_SECURITY_POLICY: String =\s*"([^"]+)" \+\s*"([^"]+)"/.exec(routes);
  assert.ok(read("own-solver.html").includes(`content="${csp[1]}${csp[2]}"`));
});

test("a reply carries the run's init errors: a count and class names only (N5a)", () => {
  // The fake player's own top-level AudioContext read throws one TypeError (init-errors.test.mjs).
  const reply = runWorker({ type: "player", player: padded(fakePlayer()), n: N, sig: SIG });
  assert.equal(reply.init_errors, 1);
  assert.deepEqual(reply.init_error_classes, ["TypeError"]);
  // needsDom: the DOM call the worker lacks is one more TypeError.
  const dom = runWorker({ type: "player", player: padded(fakePlayer({ needsDom: true })), n: N, sig: SIG });
  assert.equal(dom.init_errors, 2);
  assert.deepEqual(dom.init_error_classes, ["TypeError"]);
  // A run that never instantiated says nothing: the ladder treats its init errors as unknown.
  const bad = runWorker({ type: "prepared", program: "", n: N, sig: SIG });
  assert.equal(bad.failed, "not prepared");
  assert.equal(bad.init_errors, undefined);
  assert.equal(bad.init_error_classes, undefined);
});

test("N5b A.2: a job's deadline reaches the core; 0 gives budget, keeps the program, and bad values are bad jobs", () => {
  const job = { type: "player", keep_prepared: true, player: padded(fakePlayer()), n: N, sig: SIG, tripwire: true };
  const far = runWorker({ ...job, deadline_ms: 600000 });
  assert.equal(far.failed, undefined);
  for (const input of N) assert.equal(far.n[input], expectedN(input));
  assert.equal(far.sig[SIG[0]], expectedSig(SIG[0]));

  const none = runWorker({ ...job, deadline_ms: 0 });
  assert.equal(none.failed, "n:budget,sig:budget");
  assert.deepEqual(none.n, {});
  assert.deepEqual(none.sig, {});
  assert.equal(typeof none.prepared_program, "string");
  assert.equal(none.init_errors, undefined);

  const kept = runWorker({ type: "prepared", program: far.prepared_program, n: N, sig: SIG, deadline_ms: 0 });
  assert.equal(kept.failed, "n:budget,sig:budget");
  const b = runWorker({ type: "player", player: padded(fakePlayer()), n: N, sig: SIG, strategy: "B", hint: false, tripwire: true, deadline_ms: 0 });
  assert.equal(b.failed, "n:budget,sig:budget");
  assert.deepEqual(b.n, {});

  for (const bad of [-1, 1.5, "40000", null, true, {}]) {
    assert.deepEqual(runWorker({ ...job, deadline_ms: bad }), { type: "error", error: "BadJob" });
  }
});

test("N7: the player's error events are cancelled in the worker, so they never fail the job", () => {
  // A worker global has Event and addEventListener (the bare context above has neither).
  const listeners = [];
  let cancelled = 0;
  function Event() {}
  Event.prototype.preventDefault = function () {
    cancelled += 1;
  };
  const addEventListener = (type, listener) => listeners.push({ type, listener });
  const job = { type: "player", player: padded(fakePlayer()), n: N, sig: SIG, strategy: "B", hint: false, tripwire: true };

  const reply = runWorker(job, { globals: { Event, addEventListener } });
  assert.equal(reply.type, "result");
  assert.equal(reply.failed, undefined);
  for (const input of N) assert.equal(reply.n[input], expectedN(input));
  assert.equal(reply.sig[SIG[0]], expectedSig(SIG[0]));
  assert.deepEqual(listeners.map((entry) => entry.type), ["error"]);
  assert.equal(cancelled, 0);

  // Taken before any player code runs: replacing preventDefault afterwards does not stop the cancel.
  const [{ listener }] = listeners;
  Event.prototype.preventDefault = function () {};
  listener(new Event());
  assert.equal(cancelled, 1);

  // No Event (or no addEventListener): no listener, and the job runs as before.
  const plain = runWorker(job, { globals: { addEventListener } });
  assert.equal(plain.type, "result");
  assert.equal(listeners.length, 1);
});

// N7: the page on a worker error event (own-solver-page.js workerErrorClass) and on a reply it
// cannot read. Synthetic messages only; the page must never hand any of their text to the app.
async function pageWith(fire) {
  const bridge = [];
  let worker;
  class FakeWorker {
    constructor() {
      worker = this;
    }
    postMessage() {
      queueMicrotask(() => fire(this));
    }
    terminate() {
      this.terminated = true;
    }
  }
  const context = vm.createContext({
    window: { YftOwnSolverBridge: { post: (text) => bridge.push(text) } },
    Worker: FakeWorker,
    Blob: class {},
    URL: Object.assign(function URL() {}, { createObjectURL: () => "blob:page", revokeObjectURL: () => {} }),
    fetch: () => Promise.resolve({ ok: true, text: () => Promise.resolve("{}") }),
  });
  vm.runInContext(pageSource, context);
  await new Promise((resolve) => setTimeout(resolve, 20));
  return { bridge, worker };
}

async function workerErrorOf(event) {
  let cancelled = 0;
  const { bridge, worker } = await pageWith((fake) => {
    fake.onerror({ ...event, preventDefault: () => (cancelled += 1) });
    // A late reply after the error changes nothing.
    fake.onmessage({ data: '{"type":"result","n":{},"sig":{}}' });
  });
  assert.equal(cancelled, 1);
  assert.equal(worker.terminated, true);
  assert.equal(bridge.length, 1);
  const reply = JSON.parse(bridge[0]);
  assert.deepEqual(Object.keys(reply), ["type", "error"]);
  assert.equal(reply.type, "error");
  assert.match(reply.error, /^[A-Za-z]{1,40}$/);
  return reply.error;
}

test("page: a worker error event goes back as a fixed class, never as its text", async () => {
  const classes = [
    ["Uncaught RangeError: Maximum call stack size exceeded", "WorkerStackOverflow"],
    ["InternalError: too much recursion", "WorkerStackOverflow"],
    ["Uncaught RangeError: Array buffer allocation failed", "WorkerOutOfMemory"],
    ["uncaught exception: out of memory", "WorkerOutOfMemory"],
    ["Uncaught TypeError: Cannot read properties of undefined (reading 'qz')", "WorkerTypeError"],
    ["Uncaught ReferenceError: document is not defined", "WorkerReferenceError"],
    ["SyntaxError: Unexpected token '}'", "WorkerSyntaxError"],
    ["Uncaught RangeError: Invalid array length", "WorkerRangeError"],
    ["Uncaught EvalError", "WorkerEvalError"],
  ];
  for (const [message, expected] of classes) {
    assert.equal(await workerErrorOf({ message }), expected, message);
  }
  // A thrown string or number, a made-up class name, a plain Error, a lower-case class, no message.
  const unknown = ["Uncaught Qwzrtplk", "Uncaught Mysecretname: Qwzrtplk", "Uncaught 4242", "Uncaught Error: Qwzrtplk",
    "Uncaught typeerror: x", "Qwzrtplk TypeError: x", "", undefined, 42, null];
  for (const message of unknown) {
    assert.equal(await workerErrorOf({ message }), "WorkerError", String(message));
  }
  assert.equal(await workerErrorOf({}), "WorkerError");
});

test("page: a worker error event without preventDefault, and a reply the page cannot read", async () => {
  const bare = await pageWith((fake) => fake.onerror({ message: "Uncaught TypeError: x" }));
  assert.equal(bare.worker.terminated, true);
  assert.deepEqual(bare.bridge, ['{"type":"error","error":"WorkerTypeError"}']);

  const none = await pageWith((fake) => fake.onerror(undefined));
  assert.deepEqual(none.bridge, ['{"type":"error","error":"WorkerError"}']);

  const unreadable = await pageWith((fake) => {
    fake.onmessageerror({ data: null });
    fake.onmessage({ data: '{"type":"result","n":{},"sig":{}}' });
  });
  assert.equal(unreadable.worker.terminated, true);
  assert.deepEqual(unreadable.bridge, ['{"type":"error","error":"WorkerMessageError"}']);
});
