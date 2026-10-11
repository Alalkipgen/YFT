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
function runWorker(job, { input = JSON.stringify(job), libText = lib, coreText = core } = {}) {
  const replies = [];
  const context = vm.createContext({});
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
