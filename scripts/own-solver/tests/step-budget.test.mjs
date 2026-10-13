// Phase 1.2 N5b: P's step budget. Every loop body of P's program counts a step (B's counter), init
// and every candidate attempt get their own budget, so a player loop that never ends stops with
// RangeError("steps") in the worker and in the page frame (measured before N5b: 631d3938 main
// M9c-nav ran out of heap in own P). Offline, on the fake player written for these tests.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { prepareBWithOwn, prepareWithOwn, solveWithOwn } from "../harness.mjs";
import { addLadder, hitOf, lookupTable, phaseTable, phasesOf } from "../phases.mjs";
import { expectedN, expectedSig, fakePlayer } from "./fake-player.mjs";
import { padded } from "./solver-context.mjs";

const assets = new URL("../../../extractor-master-android/src/main/assets/yft-own-solver/", import.meta.url);
const read = (name) => readFileSync(new URL(name, assets), "utf8");
const own = { lib: read("meriyah.umd.min.js"), core: read("own.solver.core.js") };
const inputs = {
  n: ["ZdZIqFPQK-Ty8wId", "abcDEF0123456789xyz"],
  sig: ["AOq0QJ8wRAIgXmPlOPSBkkUs1bYFYlJCfe29xx8j7v1pDL0QwbdV96sCIEzpWqMGkFR20CFOg51Tp-7vj_EMu-m37KtXJoOySqa0wWq="],
};
const MODES = [{ report: true }, { report: true, page: true }];
const STYLES = ["es5", "es6", "seq"];

test("P counts steps and keeps its answers", () => {
  for (const style of STYLES) {
    for (const mode of MODES) {
      const label = `${style} ${JSON.stringify(mode)}`;
      const out = solveWithOwn(own, padded(fakePlayer({ style })), inputs, mode);
      assert.equal(out.n[inputs.n[0]], expectedN(inputs.n[0]), label);
      assert.equal(out.sig[inputs.sig[0]], expectedSig(inputs.sig[0]), label);
      assert.equal(out.failed, undefined, label);
      assert.ok(out.report.steps.call > 0, label);
      assert.ok(out.report.steps.init < 1000, label);
    }
  }
});

test("an n that never ends stops at the step budget: no n, sig still right", () => {
  for (const style of STYLES) {
    for (const mode of [...MODES, { strategy: "B", hint: false }, { strategy: "B", hint: false, page: true }]) {
      const label = `${style} ${JSON.stringify(mode)}`;
      const started = Date.now();
      const out = solveWithOwn(own, padded(fakePlayer({ style, loopN: true })), inputs, mode);
      assert.deepEqual(out.n, {}, label);
      assert.equal(out.sig[inputs.sig[0]], expectedSig(inputs.sig[0]), label);
      assert.match(out.failed, /n:/, label);
      assert.ok(Date.now() - started < 30000, label);
      if (!mode.strategy) assert.ok(out.report.steps.call > 12000, label);
    }
  }
});

// Over the init budget, later top-level loops throw as well (more init errors, never a hang).
test("a top-level loop that never ends is an init error; the candidates still answer", () => {
  for (const mode of MODES) {
    const label = JSON.stringify(mode);
    const plain = solveWithOwn(own, padded(fakePlayer()), inputs, mode);
    const out = solveWithOwn(own, padded(fakePlayer({ loopInit: true })), inputs, mode);
    assert.ok(out.report.topLevelErrors > plain.report.topLevelErrors, label);
    assert.ok(out.report.topLevelErrorClasses.RangeError >= 1, label);
    assert.ok(out.report.steps.init > 30000, label);
    assert.equal(out.n[inputs.n[0]], expectedN(inputs.n[0]), label);
    assert.equal(out.sig[inputs.sig[0]], expectedSig(inputs.sig[0]), label);
  }
});

// Page mode refuses code made at run time (it would run without the step counter on the page
// frame's main thread); the worker runs it.
test("page mode refuses eval and the Function constructors; the worker runs them", () => {
  for (const strategy of [{ report: true }, { strategy: "B", hint: false }]) {
    const label = JSON.stringify(strategy);
    const dynamic = padded(fakePlayer({ dynN: true }));
    const worker = solveWithOwn(own, dynamic, inputs, strategy);
    assert.equal(worker.n[inputs.n[0]], expectedN(inputs.n[0]), label);
    const page = solveWithOwn(own, dynamic, inputs, { ...strategy, page: true });
    assert.deepEqual(page.n, {}, label);
    assert.equal(page.sig[inputs.sig[0]], expectedSig(inputs.sig[0]), label);
    const plain = solveWithOwn(own, padded(fakePlayer()), inputs, { ...strategy, page: true });
    const init = solveWithOwn(own, padded(fakePlayer({ dynInit: true })), inputs, { ...strategy, page: true });
    assert.equal(init.report.topLevelErrors, plain.report.topLevelErrors + 1, label);
    assert.equal(init.report.topLevelErrorClasses.EvalError, 1, label);
    const initWorker = solveWithOwn(own, padded(fakePlayer({ dynInit: true })), inputs, strategy);
    assert.equal(initWorker.report.topLevelErrors, solveWithOwn(own, padded(fakePlayer()), inputs, strategy).report.topLevelErrors, label);
  }
});

// N5b A.1: phase times in the report (milliseconds, counts only), the data for the job cap, the
// lookup cap and the locator cache's hit estimate. P: prepare (parse, find, build), init, search
// (attempts of candidates that gave no value: the part a locator hit skips), SelfCheck (the rest of
// the kinds' solve) and the tripwire (with its own search part). B: prepare (parse, instrument,
// templates, reparse, build), instantiate, screen, confirm per kind (and of the winning group), solve
// per kind and the tripwire.
const isMs = (value) => Number.isFinite(value) && value >= 0;

test("P reports its phase times, in the worker and in page mode", () => {
  for (const mode of [{ report: true, tripwire: true }, { report: true }, { report: true, page: true }]) {
    const label = JSON.stringify(mode);
    const out = solveWithOwn(own, padded(fakePlayer()), inputs, mode);
    assert.equal(out.n[inputs.n[0]], expectedN(inputs.n[0]), label);
    const ms = out.report.ms;
    for (const key of ["init", "search", "selfcheck"]) assert.ok(isMs(ms[key]), `${label} ${key}`);
    for (const key of ["parse", "find", "build"]) assert.ok(isMs(out.report.prepare.ms[key]), `${label} prepare ${key}`);
    if (mode.tripwire) {
      assert.ok(isMs(ms.tripwire) && isMs(ms.tripwire_search), label);
      assert.ok(ms.tripwire_search <= ms.tripwire, label);
    } else {
      assert.equal(ms.tripwire, undefined, label);
    }
  }
});

test("B reports its phase times, the tripwire's and the winning group's confirm", () => {
  for (const mode of [{ strategy: "B", hint: false, tripwire: true }, { strategy: "B", hint: false, page: true }]) {
    const label = JSON.stringify(mode);
    const out = solveWithOwn(own, padded(fakePlayer()), inputs, mode);
    assert.equal(out.sig[inputs.sig[0]], expectedSig(inputs.sig[0]), label);
    const ms = out.report.ms;
    for (const key of ["instantiate", "screen", "confirm_n", "confirm_sig", "solve_n", "solve_sig"]) assert.ok(isMs(ms[key]), `${label} ${key}`);
    for (const kind of ["n", "sig"]) {
      assert.ok(isMs(ms[`confirm_${kind}_win`]), `${label} ${kind}`);
      assert.ok(ms[`confirm_${kind}_win`] <= ms[`confirm_${kind}`], `${label} ${kind}`);
    }
    for (const key of ["parse", "instrument", "templates", "reparse", "build"]) assert.ok(isMs(out.report.prepare.ms[key]), `${label} prepare ${key}`);
    if (mode.tripwire) assert.ok(isMs(ms.tripwire), label);
    else assert.equal(ms.tripwire, undefined, label);
  }
});

// A hit names the winning candidates: B skips the screen and the losers' confirm (confirmWin stays).
test("phase sums and the hit estimate: the sum without the search; (a) keeps P's program only", () => {
  const p = phasesOf({ prepare: { ms: { parse: 100, find: 20, build: 30 } }, ms: { init: 40, search: 7, selfcheck: 5, tripwire: 50, tripwire_search: 6 } }, "P");
  // P's find (the candidates from the patterns) is search: a locator names the candidates.
  assert.deepEqual(p, { prepare: 130, init: 40, search: 33, confirm: 0, selfcheck: 5, tripwire: 44, total: 252 });
  assert.deepEqual(hitOf(p, "P"), { a: 89, b: 219 });
  const b = phasesOf({
    prepare: { ms: { parse: 100, instrument: 50, templates: 10, reparse: 90, build: 30 } },
    ms: { instantiate: 60, screen: 900, confirm_n: 240, confirm_sig: 250, confirm_n_win: 120, confirm_sig_win: 125, solve_n: 70, solve_sig: 65, tripwire: 130 },
  }, "B");
  assert.deepEqual(b, { prepare: 280, init: 60, search: 900, confirm: 490, confirmWin: 245, selfcheck: 135, tripwire: 130, total: 1995 });
  assert.deepEqual(hitOf(b, "B"), { a: 850, b: 850 });
  // Option 3 (B's prepared program kept in memory) helps (a) only.
  assert.deepEqual(hitOf(b, "B", { keptB: true }), { a: 570, b: 850 });
  assert.equal(phasesOf(null, "P"), null);
});

test("phase table: max and median per step, counts and milliseconds only", () => {
  const table = phaseTable();
  table.add("workerP", "aaaa1111 main", { prepare: 10, init: 4, search: 1, confirm: 0, selfcheck: 2, tripwire: 3, total: 20 }, { wall: 25, hit: { a: 9, b: 19 } });
  table.add("workerP", "bbbb2222 es6", { prepare: 30, init: 8, search: 3, confirm: 0, selfcheck: 4, tripwire: 5, total: 50 }, { wall: 60, hit: { a: 17, b: 47 } });
  table.add("workerP", "cccc3333 tce", { prepare: 20, init: 6, search: 2, confirm: 0, selfcheck: 3, tripwire: 4, total: 35 }, { wall: 40, hit: { a: 13, b: 33 } });
  table.add("workerB", "aaaa1111 main", { prepare: 90, init: 9, search: 500, confirm: 40, selfcheck: 8, tripwire: 7, total: 654 }, { wall: 700, hit: { a: 154, b: 154, a3: 64 } });
  const rows = table.rows();
  assert.equal(rows.length, 2);
  assert.equal(rows[0].step, "workerP");
  assert.equal(rows[0].files, 3);
  assert.deepEqual(rows[0].max, { prepare: 30, init: 8, search: 3, confirm: 0, selfcheck: 4, tripwire: 5, total: 50, wall: 60, hitA: 17, hitB: 47, hitA3: 17 });
  assert.deepEqual(rows[0].median, { prepare: 20, init: 6, search: 2, confirm: 0, selfcheck: 3, tripwire: 4, total: 35, wall: 40, hitA: 13, hitB: 33, hitA3: 13 });
  assert.equal(rows[0].slowest, "bbbb2222 es6");
  assert.equal(rows[1].max.hitA3, 64);
  assert.deepEqual(rows[0].over, { hitA: 0, hitB: 0, hitA3: 0 });
  table.add("workerB", "bbbb2222 es6", { prepare: 400, init: 9, search: 500, confirm: 40, selfcheck: 8, tripwire: 7, total: 964 }, { wall: 990, hit: { a: 501, b: 501, a3: 101 } });
  assert.deepEqual(table.rows()[1].over, { hitA: 1, hitB: 1, hitA3: 0 });
  assert.match(table.lines().join("\n"), /^phases P \(3 files, max\/median ms\): prepare 30\/20, init 8\/6, search 3\/2/m);
  assert.match(table.lines().join("\n"), /^phases B \(2 files, max\/median ms\): .*hitA3 101\/64; over 500 ms: hitA 1, hitB 1, hitA3 0; slowest bbbb2222 es6$/m);
});

test("ladder rows: one file's runs into the table, the wall per step and the lookup's largest", () => {
  const table = phaseTable();
  const pReport = { prepare: { ms: { parse: 5, find: 3, build: 2 } }, ms: { init: 4, search: 1, selfcheck: 2, tripwire: 3, tripwire_search: 1 } };
  const bReport = {
    prepare: { ms: { parse: 6, instrument: 2, templates: 1, reparse: 5, build: 1 } },
    ms: { instantiate: 4, screen: 300, confirm_n: 20, confirm_sig: 10, confirm_n_win: 6, confirm_sig_win: 4, solve_n: 3, solve_sig: 2, tripwire: 6 },
  };
  const outs = { workerP: { report: pReport }, workerB: { report: bReport }, pageP: { report: null }, pageB: null };
  const walls = { workerP: 30.4, workerB: 400.6, pageP: 12 };
  assert.equal(addLadder(table, "aaaa1111 main", outs, walls), "P 30, B 401, pP 12");
  const rows = table.rows();
  assert.deepEqual(rows.map((row) => row.step), ["workerP", "workerB"]);
  assert.deepEqual([rows[0].max.prepare, rows[0].max.search, rows[0].max.hitA, rows[0].max.hitB, rows[0].max.hitA3, rows[0].max.wall], [7, 5, 8, 15, 8, 30.4]);
  assert.deepEqual([rows[1].max.hitA, rows[1].max.hitB, rows[1].max.hitA3, rows[1].max.search], [40, 40, 25, 300]);
  const lookup = lookupTable();
  lookup.add("aaaa1111 main", walls);
  lookup.add("bbbb2222 es6", { workerP: 100, workerB: 200, pageP: 300, pageB: 400 });
  assert.deepEqual(lookup.largest(), { pb: { ms: 431, file: "aaaa1111 main" }, all: { ms: 1000, file: "bbbb2222 es6" } });
  assert.equal(lookup.line(), "lookup ms (largest per file): P+B 431 (aaaa1111 main), P+B+pP+pB 1000 (bbbb2222 es6)");
});

// N5b A.1, option 3 (B's prepared program kept in memory): its size, in characters only.
test("prepared sizes: P's kept program and B's prepared program, characters only", () => {
  const player = padded(fakePlayer());
  const p = prepareWithOwn(own, player);
  assert.ok(p.ok && p.chars > 0);
  assert.equal(p.program, undefined);
  const b = prepareBWithOwn(own, player);
  assert.ok(b.ok);
  assert.ok(b.sizes.program > 0 && b.sizes.meta > 2 && b.sizes.templates >= 2);
  assert.equal(b.sizes.total, b.sizes.program + b.sizes.meta + b.sizes.templates);
  assert.equal(b.program, undefined);
  assert.ok(b.info.functions > 0);
  assert.equal(prepareBWithOwn(own, "x").ok, false);
});
