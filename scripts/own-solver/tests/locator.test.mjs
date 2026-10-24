// Phase 1.2 N5b A.4: the locator cache's data in the core and the worker (request.locator, worker
// steps P and B; ownSolverNext only, a request without the field changes nothing). Locate mode
// returns a part per kind answered cleanly; a part in the request narrows the work (P: only its
// candidates; B: no screen) and keeps every check; a part that does not match exactly is a miss
// and the kind runs in full in the same job (never a wrong value). Offline, on the fake player
// written for these tests (no YouTube code); its one top-level AudioContext read is given here so
// that the runs are clean (init errors 0), as on today's players.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import vm from "node:vm";
import { hitInIsolate, hitWithOwn } from "../harness.mjs";
import { expectedN, expectedSig, fakePlayer } from "./fake-player.mjs";
import { padded, solve } from "./solver-context.mjs";

const assets = new URL("../../../extractor-master-android/src/main/assets/yft-own-solver/", import.meta.url);
const read = (name) => readFileSync(new URL(name, assets), "utf8");
const lib = read("meriyah.umd.min.js");
const core = read("own.solver.core.js");
const workerSource = read("own-solver-worker.js");

const N = ["ZdZIqFPQK-Ty8wId", "abcDEF0123456789xyz"];
const SIG = ["AOq0QJ8wRAIgXmPlOPSBkkUs1bYFYlJCfe29xx8j7v1pDL0QwbdV96sCIEzpWqMGkFR20CFOg51Tp-7vj_EMu-m37KtXJoOySqa0wWq="];
const expected = { n: expectedN, sig: expectedSig };
const inputsOf = { n: N, sig: SIG };
const P = { tripwire: true };
const B = { strategy: "B", hint: false, tripwire: true };
const STRATEGIES = [["P", P], ["B", B]];
const HASH = /^[0-9a-f]{8}$/;
const CLEAN = "var AudioContext = function AudioContext() {};";

/** A worker-shaped context where the fake player's top level runs without an error. */
function cleanContext() {
  const context = vm.createContext({});
  vm.runInContext(CLEAN, context);
  vm.runInContext(lib, context);
  vm.runInContext(core, context);
  return context;
}

function run(player, strategy, extra = {}) {
  return solve({ player, n: N, sig: SIG, ...strategy, ...extra }, cleanContext());
}

/** Every answer is the right one or absent: a wrong value never comes out. */
function neverWrong(out, label) {
  for (const kind of ["n", "sig"]) {
    for (const [input, value] of Object.entries(out[kind] || {})) assert.equal(value, expected[kind](input), `${label} ${kind}`);
  }
}

function answersAll(out, label) {
  for (const kind of ["n", "sig"]) {
    for (const input of inputsOf[kind]) assert.equal(out[kind][input], expected[kind](input), `${label} ${kind}`);
  }
}

const copy = (value) => JSON.parse(JSON.stringify(value));

test("without a locator nothing changes: no parts, no locator report", () => {
  const player = padded(fakePlayer());
  for (const [name, strategy] of STRATEGIES) {
    const out = run(player, strategy);
    answersAll(out, name);
    assert.equal(out.locator, undefined, name);
    assert.equal(out.locatorHits, undefined, name);
    assert.equal(out.report.locator, undefined, name);
  }
});

test("locate mode returns data-only parts for clean kinds and the same answers", () => {
  const player = padded(fakePlayer({ throwing: true }));
  for (const [name, strategy] of STRATEGIES) {
    const out = run(player, strategy, { locator: {} });
    answersAll(out, name);
    assert.equal(out.locator.v, 1, name);
    assert.deepEqual(out.locatorHits, [], name);
    assert.deepEqual(out.report.locator, { n: "new", sig: "new" }, name);
    for (const kind of ["n", "sig"]) {
      const part = out.locator[kind];
      assert.equal(part.s, name, name);
      assert.match(part.o, HASH, name);
      if (name === "P") {
        assert.ok(part.c.length >= 1 && part.c.every((index) => Number.isInteger(index) && index >= 0 && index < out.report.candidates), name);
      } else {
        for (const member of part.m) {
          assert.equal(member.length, 4, name);
          assert.ok(Number.isInteger(member[0]) && Number.isInteger(member[1]) && Number.isInteger(member[3]), name);
          assert.match(member[2], HASH, name);
        }
      }
    }
    // Data only: no input, no output and no player text inside.
    const text = JSON.stringify(out.locator);
    for (const kind of ["n", "sig"]) {
      for (const input of inputsOf[kind]) {
        assert.ok(!text.includes(input) && !text.includes(expected[kind](input)), name);
      }
    }
    assert.ok(text.length < 400, name);
  }
});

test("a hit gives the same answers and parts: P runs only its candidates, B skips the screen", () => {
  const player = padded(fakePlayer({ throwing: true }));
  for (const [name, strategy] of STRATEGIES) {
    const miss = run(player, strategy, { locator: {} });
    const hit = run(player, strategy, { locator: miss.locator });
    answersAll(hit, name);
    assert.deepEqual(hit.locator, miss.locator, name);
    assert.deepEqual(hit.locatorHits, ["n", "sig"], name);
    assert.deepEqual(hit.report.locator, { n: "hit", sig: "hit" }, name);
    assert.deepEqual(hit.report.tripwire, { checked: 2, tripped: [] }, name);
    if (name === "P") {
      // The throwing candidate gives no signature: the miss tried it on every input, the hit not.
      assert.ok(miss.report.sig.errors.length > 0, name);
      assert.deepEqual(hit.report.sig.errors, [], name);
    } else {
      assert.ok(miss.report.screened > 0, name);
      assert.equal(hit.report.screen, "skipped", name);
      assert.equal(hit.report.screened, 0, name);
    }
  }
});

test("a hit still runs the tripwire for both kinds: an n that follows the environment trips", () => {
  for (const [name, strategy] of STRATEGIES) {
    for (const [option, kind] of [["envN", "n"], ["envSig", "sig"]]) {
      const label = `${name} ${option}`;
      const player = padded(fakePlayer({ [option]: true }));
      // Without the tripwire the kind is clean (the worker's stubs give one answer) and located.
      const untripped = run(player, { ...strategy, tripwire: false }, { locator: {} });
      assert.ok(untripped.locator[kind], label);
      const out = run(player, strategy, { locator: untripped.locator });
      assert.deepEqual(out[kind], {}, label);
      assert.match(out.failed, new RegExp(`${kind}:environment`), label);
      assert.equal(out.report.topLevelErrors, 1, label);
      // A trip is an init error: no part comes back, for either kind, and nothing hit.
      assert.deepEqual(out.locator, { v: 1 }, label);
      assert.deepEqual(out.locatorHits, [], label);
      assert.equal(out.report.locator[kind], "miss late", label);
    }
  }
});

test("poisoned P parts miss and the kind runs in full: same answers, never a wrong value", () => {
  const player = padded(fakePlayer({ throwing: true }));
  const base = run(player, P, { locator: {} });
  const count = base.report.candidates;
  const silent = [...Array(count).keys()].find((index) => !base.locator.sig.c.includes(index));
  assert.notEqual(silent, undefined);
  const other = base.locator.n.o === "00000000" ? "00000001" : "00000000";
  const cases = [
    ["index out of range", { sig: { ...base.locator.sig, c: [count] } }, "sig", "miss index"],
    ["a silent candidate alone", { sig: { ...base.locator.sig, c: [silent] } }, "sig", "miss check"],
    ["a silent one with the right one", { sig: { ...base.locator.sig, c: [...base.locator.sig.c, silent].sort() } }, "sig", "miss silent"],
    ["other probe outputs", { n: { ...base.locator.n, o: other } }, "n", "miss outputs"],
    ["the n part for sig", { sig: { ...base.locator.n } }, "sig", null],
    ["another core version", { v: 2 }, "n", "miss version"],
    ["a B part for P", { n: { s: "B", m: [[1, 0, "00000000", 10]], o: other } }, "n", "miss form"],
  ];
  for (const [label, change, kind, status] of cases) {
    const locator = { ...copy(base.locator), ...change };
    const out = run(player, P, { locator });
    answersAll(out, label);
    assert.deepEqual(out.locator, base.locator, label);
    assert.ok(!out.locatorHits.includes(kind), label);
    if (status) assert.equal(out.report.locator[kind], status, label);
    else assert.match(out.report.locator[kind], /^miss /, label);
  }
});

test("P's decoy fault still fails the kind with a part: its index is refused and the hit disagrees", () => {
  const player = padded(fakePlayer({ throwing: true }));
  const base = run(player, P, { locator: {} });
  for (const kind of ["n", "sig"]) {
    // The fault's extra candidate sits at the first index past the real ones.
    const decoyIndex = { ...copy(base.locator), [kind]: { ...base.locator[kind], c: [base.report.candidates] } };
    const good = copy(base.locator);
    for (const [label, locator, status] of [["decoy index", decoyIndex, "miss index"], ["good part", good, "miss check"]]) {
      const out = run(player, P, { locator, faults: { decoy: kind } });
      neverWrong(out, `${kind} ${label}`);
      assert.deepEqual(out[kind], {}, `${kind} ${label}`);
      assert.match(out.failed, new RegExp(`${kind}:disagree`), `${kind} ${label}`);
      assert.equal(out.report.locator[kind], status, `${kind} ${label}`);
      assert.equal(out.locator[kind], undefined, `${kind} ${label}`);
    }
  }
});

test("a P part naming one of two disagreeing candidates misses: every candidate still runs once", () => {
  // The decoy player has a second builder whose signature differs: the full run refuses sig.
  const decoy = padded(fakePlayer({ decoy: true }));
  const full = run(decoy, P, { locator: {} });
  assert.deepEqual(full.sig, {});
  assert.match(full.failed, /sig:disagree/);
  assert.equal(full.locator.sig, undefined);
  // A part from the plain player's builder (same signature steps), for each candidate alone.
  const real = run(padded(fakePlayer()), P, { locator: {} }).locator.sig;
  const statuses = [];
  for (let index = 0; index < full.report.candidates; index += 1) {
    const out = run(decoy, P, { locator: { v: 1, sig: { ...real, c: [index] } } });
    neverWrong(out, `candidate ${index}`);
    assert.deepEqual(out.sig, {}, `candidate ${index}`);
    statuses.push(out.report.locator.sig);
  }
  // The real builder alone passes SelfCheck with the part's outputs; the agreement check stops it.
  assert.ok(statuses.includes("miss agree"), statuses.join(", "));
});

test("poisoned B parts miss and the kind runs in full: same answers, never a wrong value", () => {
  const player = padded(fakePlayer({ throwing: true }));
  const base = run(player, B, { locator: {} });
  const member = base.locator.n.m[0];
  const other = (hash) => (hash === "00000000" ? "00000001" : "00000000");
  const withMember = (kind, change) => ({ ...base.locator[kind], m: [change(base.locator[kind].m[0]), ...base.locator[kind].m.slice(1)] });
  const cases = [
    ["another function id", { n: withMember("n", (m) => [m[0] + 1, m[1], m[2], m[3]]) }, "n"],
    ["an id not registered", { n: withMember("n", (m) => [999999, m[1], m[2], m[3]]) }, "n", "miss id"],
    ["the sig function for n", { n: { ...base.locator.n, m: base.locator.sig.m } }, "n", "miss shape"],
    ["another shape", { n: withMember("n", (m) => [m[0], m[1] + 1, m[2], m[3]]) }, "n", "miss shape"],
    ["another source hash", { n: withMember("n", (m) => [m[0], m[1], other(m[2]), m[3]]) }, "n", "miss source"],
    ["another size", { sig: withMember("sig", (m) => [m[0], m[1], m[2], m[3] + 1]) }, "sig", "miss id"],
    ["other probe outputs", { sig: { ...base.locator.sig, o: other(base.locator.sig.o) } }, "sig", "miss outputs"],
    ["a P part for B", { sig: { s: "P", c: [0], o: base.locator.sig.o } }, "sig", "miss form"],
  ];
  assert.ok(member.length === 4);
  for (const [label, change, kind, status] of cases) {
    const out = run(player, B, { locator: { ...copy(base.locator), ...change } });
    answersAll(out, label);
    assert.deepEqual(out.locator, base.locator, label);
    assert.ok(!out.locatorHits.includes(kind), label);
    // The other kind's part still hits, but the screen ran for this one.
    assert.notEqual(out.report.screen, "skipped", label);
    if (status) assert.equal(out.report.locator[kind], status, label);
    else assert.match(out.report.locator[kind], /^miss /, label);
  }
});

test("B's decoy fault still fails the kind with a good part", () => {
  const player = padded(fakePlayer({ throwing: true }));
  const base = run(player, B, { locator: {} });
  for (const kind of ["n", "sig"]) {
    const out = run(player, B, { locator: copy(base.locator), faults: { decoy: kind } });
    neverWrong(out, kind);
    assert.deepEqual(out[kind], {}, kind);
    assert.match(out.failed, new RegExp(`${kind}:disagree`), kind);
    assert.equal(out.report.locator[kind], "miss group", kind);
    assert.equal(out.locator[kind], undefined, kind);
  }
});

test("a part for one kind only: B screens for the other, the located kind hits", () => {
  const player = padded(fakePlayer());
  const base = run(player, B, { locator: {} });
  const out = run(player, B, { locator: { v: 1, sig: base.locator.sig } });
  answersAll(out, "B");
  assert.deepEqual(out.report.locator, { n: "new", sig: "hit" });
  assert.deepEqual(out.locatorHits, ["sig"]);
  assert.ok(out.report.screened > 0);
  assert.deepEqual(out.locator, base.locator);
});

test("page mode never locates (the frame refuses the field; page steps run in full)", () => {
  const player = padded(fakePlayer());
  for (const [name, strategy] of STRATEGIES) {
    const out = run(player, { ...strategy, tripwire: false, page: true }, { locator: {} });
    assert.equal(out.locator, undefined, name);
    assert.equal(out.report.locator, undefined, name);
  }
});

test("init errors: a part is not used and no part comes back", () => {
  const player = padded(fakePlayer());
  for (const [name, strategy] of STRATEGIES) {
    const base = run(player, strategy, { locator: {} });
    // The fake player as the other tests run it: its AudioContext read throws (one init error).
    const out = solve({ player, n: N, sig: SIG, ...strategy, locator: base.locator });
    answersAll(out, name);
    assert.equal(out.report.topLevelErrors, 1, name);
    assert.deepEqual(out.report.locator, { n: "miss init", sig: "miss init" }, name);
    assert.deepEqual(out.locator, { v: 1 }, name);
  }
});

test("a deadline cut gives no part for the cut kind; the other kind keeps its part", () => {
  const player = padded(fakePlayer());
  for (const [name, strategy] of STRATEGIES) {
    // n's tripwire check finished; the deadline passes as sig's starts.
    const phase = "tripwire_sig";
    const out = run(player, strategy, { locator: {}, deadline: 600000, faults: { deadline: phase } });
    assert.match(out.failed, /sig:budget/, name);
    assert.equal(out.locator.sig, undefined, name);
    assert.ok(out.locator.n, name);
  }
});

test("a part whose SelfCheck fails is a miss: the kind runs in full and fails, nothing hits", () => {
  const player = padded(fakePlayer());
  // A test vector no candidate gives: SelfCheck fails after every locator check has passed.
  const vectors = { n: [{ input: N[0], expected: "-" }], sig: [{ input: SIG[0], expected: "-" }] };
  for (const [name, strategy] of STRATEGIES) {
    const miss = run(player, strategy, { locator: {} });
    const out = run(player, strategy, { locator: miss.locator, vectors });
    assert.deepEqual(out.report.locator, { n: "miss check", sig: "miss check" }, name);
    assert.match(out.failed, /n:vector/, name);
    assert.match(out.failed, /sig:vector/, name);
    assert.deepEqual(out.n, {}, name);
    assert.deepEqual(out.sig, {}, name);
    assert.deepEqual(out.locator, { v: 1 }, name);
    assert.deepEqual(out.locatorHits, [], name);
  }
});

test("P: an input no candidate answers leaves its kind without a part", () => {
  const player = padded(fakePlayer());
  // A lone surrogate cannot be URL-encoded: no candidate gives it a signature, and the kind
  // still answers its other inputs (not a failure), but it was not answered cleanly.
  const out = solve({ player, n: N, sig: [...SIG, "\uD800"], ...P, locator: {} }, cleanContext());
  assert.equal(out.failed, undefined);
  assert.equal(out.sig[SIG[0]], expectedSig(SIG[0]));
  assert.equal(Object.keys(out.sig).length, 1);
  assert.equal(out.locator.sig, undefined);
  assert.ok(out.locator.n);
  assert.deepEqual(out.report.locator, { n: "new", sig: "none" });
});

test("the core's form check: parts are data only, bounded, and of the job's strategy", () => {
  const context = cleanContext();
  const ok = (locator, strategy) => vm.runInContext(`yftOwnSolver.locatorOk(${JSON.stringify(locator)}, ${JSON.stringify(strategy)})`, context);
  const p = { s: "P", c: [0, 3], o: "0123abcd" };
  const b = { s: "B", m: [[12, 0, "0123abcd", 345]], o: "89abcdef" };
  assert.equal(ok({}, "P"), true);
  assert.equal(ok({ v: 1, n: p, sig: p }, "P"), true);
  assert.equal(ok({ v: 1, sig: b }, "B"), true);
  // One function in two shapes is two members (the corpus's B groups have them); the pair is unique.
  assert.equal(ok({ v: 1, sig: { ...b, m: [[12, 0, "0123abcd", 5], [12, 1, "0123abcd", 5]] } }, "B"), true);
  const bad = [
    [[], "P"], ["x", "P"], [null, "P"], [{ v: 1, x: p }, "P"], [{ v: 0, n: p }, "P"], [{ v: 1.5, n: p }, "P"], [{ n: p }, "P"],
    [{ v: 1, n: { ...p, c: [] } }, "P"], [{ v: 1, n: { ...p, c: [...Array(17).keys()] } }, "P"], [{ v: 1, n: { ...p, c: [-1] } }, "P"],
    [{ v: 1, n: { ...p, c: [1.5] } }, "P"], [{ v: 1, n: { ...p, c: ["1"] } }, "P"], [{ v: 1, n: { ...p, c: [2, 2] } }, "P"],
    [{ v: 1, n: { ...p, c: [16] } }, "P"], [{ v: 1, n: { ...p, o: "XYZ" } }, "P"], [{ v: 1, n: { ...p, o: "0123abcd0" } }, "P"],
    [{ v: 1, n: { ...p, x: 1 } }, "P"], [{ v: 1, n: p }, "B"], [{ v: 1, n: b }, "P"], [{ v: 1, n: { ...b, m: [] } }, "B"],
    [{ v: 1, n: { ...b, m: [[12, 0, "0123abcd"]] } }, "B"], [{ v: 1, n: { ...b, m: [[12, 0, "zz", 345]] } }, "B"],
    [{ v: 1, n: { ...b, m: [[12, 0, "0123abcd", 0]] } }, "B"], [{ v: 1, n: { ...b, m: [...Array(9).keys()].map((i) => [i, 0, "0123abcd", 5]) } }, "B"],
    [{ v: 1, n: { ...b, m: [[12, 1, "0123abcd", 5], [12, 1, "0123abcd", 5]] } }, "B"], [{ v: 1, n: { ...b, m: [[-1, 0, "0123abcd", 5]] } }, "B"],
  ];
  for (const [locator, strategy] of bad) assert.equal(ok(locator, strategy), false, JSON.stringify(locator));
});

/** One job through a fresh worker-shaped context (clean, see above); returns the reply. */
function runWorker(job) {
  const replies = [];
  const context = vm.createContext({});
  vm.runInContext(`var self = this; ${CLEAN}`, context);
  context.postMessage = (message) => replies.push(message);
  vm.runInContext(workerSource, context);
  Object.assign(context, { lib, core, input: JSON.stringify(job) });
  vm.runInContext("self.onmessage({ data: { lib: lib, core: core, input: input } })", context, { timeout: 30_000 });
  assert.equal(replies.length, 1);
  return JSON.parse(replies[0]);
}

test("worker: a job's locator reaches the core; the reply carries the parts and the hits", () => {
  const player = padded(fakePlayer());
  const first = runWorker({ type: "player", keep_prepared: true, player, n: N, sig: SIG, tripwire: true, locator: {} });
  answersAll(first, "P");
  assert.equal(first.locator.n.s, "P");
  assert.equal(first.locator_hit, undefined);
  const again = runWorker({ type: "prepared", program: first.prepared_program, n: N, sig: SIG, tripwire: true, locator: first.locator });
  answersAll(again, "P kept");
  assert.deepEqual(again.locator_hit, ["n", "sig"]);
  assert.deepEqual(again.locator, first.locator);
  const b = runWorker({ type: "player", player, n: N, sig: SIG, strategy: "B", hint: false, tripwire: true, locator: {} });
  assert.equal(b.locator.sig.s, "B");
  const bHit = runWorker({ type: "player", player, n: N, sig: SIG, strategy: "B", hint: false, tripwire: true, locator: b.locator });
  answersAll(bHit, "B");
  assert.deepEqual(bHit.locator_hit, ["n", "sig"]);
  // Phase 1.1's jobs carry no locator and get none back.
  const plain = runWorker({ type: "player", player, n: N, sig: SIG });
  assert.equal(plain.locator, undefined);
  assert.equal(plain.locator_hit, undefined);
});

test("worker: a malformed locator, or one of the other strategy, is a bad job", () => {
  const player = padded(fakePlayer());
  const p = { s: "P", c: [0], o: "0123abcd" };
  const b = { s: "B", m: [[12, 0, "0123abcd", 345]], o: "89abcdef" };
  const jobs = [
    { type: "player", player, n: N, sig: SIG, locator: [] },
    { type: "player", player, n: N, sig: SIG, locator: null },
    { type: "player", player, n: N, sig: SIG, locator: "x" },
    { type: "player", player, n: N, sig: SIG, locator: { v: 1, n: { ...p, c: [99] } } },
    { type: "player", player, n: N, sig: SIG, locator: { v: 1, n: b } },
    { type: "prepared", program: "x", n: N, sig: SIG, locator: { v: 1, sig: { ...p, extra: true } } },
    { type: "player", player, n: N, sig: SIG, strategy: "B", hint: false, locator: { v: 1, n: p } },
  ];
  for (const job of jobs) assert.deepEqual(runWorker(job), { type: "error", error: "BadJob" }, JSON.stringify(job.locator));
});

test("the core's locator version is the app's (OwnSolverLocator.VERSION; the cache file's core)", () => {
  const kotlin = readFileSync(
    new URL("../../../extractor-master/src/main/kotlin/com/alal/yft/extractor/master/solver/OwnSolverLocator.kt", import.meta.url),
    "utf8",
  );
  const version = Number(/const val VERSION: Int = (\d+)/.exec(kotlin)?.[1]);
  assert.ok(version >= 1);
  for (const [label, strategy] of STRATEGIES) {
    const out = run(padded(fakePlayer()), strategy, { locator: {} });
    assert.equal(out.locator.v, version, label);
  }
});

// The runners' hits (verify-own-solver.mjs --ladder and --hits-only) go through hitWithOwn.
const STUB_CORE =
  "var yftOwnSolver = { prepare: function (p) { return p === 'bad' ? { ok: false, failed: 'too short' } : { ok: true, program: 'kept ' + p.length }; }," +
  " run: function (prepared, request) { return { n: {}, sig: {}, report: { program: prepared.program, ok: prepared.ok, player: 'player' in request, global: typeof player, keys: Object.keys(request).sort() } }; } };" +
  " function yftOwnSolve(request) { return { n: {}, sig: {}, report: { player: typeof request.player, keys: Object.keys(request).sort() } }; }";

test("harness hitWithOwn: kept runs the prepared program without the player text; the request is the job's", () => {
  const stub = { lib: "", core: STUB_CORE };
  const inputs = { n: ["a"], sig: [] };
  const kept = hitWithOwn(stub, "abc", inputs, { kept: true, report: true, dyncode: true, tripwire: true, locator: {} });
  assert.deepEqual(kept.report, { program: "kept 3", ok: true, player: false, global: "undefined", keys: ["locator", "n", "sig", "tripwire"] });
  assert.ok(Number.isFinite(kept.ms) && kept.ms >= 0 && Number.isFinite(kept.wall));
  const text = hitWithOwn(stub, "abc", inputs, { report: true, tripwire: true, locator: {} });
  assert.deepEqual(text.report, { player: "string", keys: ["locator", "n", "player", "sig", "tripwire"] });
  // A kept program that does not prepare gives its failure and no run; kept is worker P's only.
  const bad = hitWithOwn(stub, "bad", inputs, { kept: true });
  assert.deepEqual([bad.failed, bad.n, bad.sig, bad.ms], ["too short", {}, {}, 0]);
  for (const extra of [{ strategy: "B", kept: true }, { page: true, kept: true }]) {
    const refused = hitWithOwn(stub, "abc", inputs, extra);
    assert.equal(refused.error, "TypeError", JSON.stringify(extra));
  }
});

test("harness hitWithOwn: the core's hits - P kept and from the text, B from the text - answer as the miss", () => {
  // The fake player's AudioContext read, given before the core (the harness's context is bare).
  const clean = { lib: `${CLEAN}\n${lib}`, core };
  const player = padded(fakePlayer());
  for (const [label, strategy] of STRATEGIES) {
    const miss = hitWithOwn(clean, player, inputsOf, { ...strategy, locator: {} });
    answersAll(miss, `${label} miss`);
    assert.deepEqual(miss.report.locator, { n: "new", sig: "new" }, label);
    const keptToo = label === "P" ? [true, false] : [false];
    for (const kept of keptToo) {
      const hit = hitWithOwn(clean, player, inputsOf, { ...strategy, locator: miss.locator, kept });
      answersAll(hit, `${label} hit kept ${kept}`);
      assert.deepEqual(hit.locatorHits, ["n", "sig"], `${label} kept ${kept}`);
      assert.deepEqual(hit.locator, miss.locator, `${label} kept ${kept}`);
      assert.ok(Number.isFinite(hit.ms) && hit.ms >= 0);
    }
  }
  // The bare context: the one init error gives no part, and the answers stay right.
  const plain = hitWithOwn({ lib, core }, player, inputsOf, { ...P, locator: {} });
  answersAll(plain, "plain");
  assert.deepEqual(plain.report.locator, { n: "none", sig: "none" });
});

test("harness hitInIsolate: hitWithOwn's reply from a fresh isolate, with the thread's time", async () => {
  const stub = { lib: "", core: STUB_CORE };
  const inputs = { n: ["a"], sig: [] };
  const extra = { kept: true, report: true, tripwire: true, locator: {} };
  const away = await hitInIsolate(stub, "abc", inputs, extra);
  const here = hitWithOwn(stub, "abc", inputs, extra);
  assert.deepEqual(away.report, here.report);
  assert.ok(away.wall >= away.ms && away.thread >= away.wall, JSON.stringify([away.thread, away.wall, away.ms]));
  const refused = await hitInIsolate(stub, "abc", inputs, { strategy: "B", kept: true });
  assert.equal(refused.error, "TypeError");
  // The core's hit on the fake player (its AudioContext read given, as above).
  const clean = { lib: `${CLEAN}\n${lib}`, core };
  const player = padded(fakePlayer());
  const miss = hitWithOwn(clean, player, inputsOf, { ...P, locator: {} });
  const hit = await hitInIsolate(clean, player, inputsOf, { ...P, locator: miss.locator, kept: true });
  answersAll(hit, "isolate");
  assert.deepEqual(hit.locatorHits, ["n", "sig"]);
});
