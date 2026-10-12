// Phase 1.2 N5b: P's step budget. Every loop body of P's program counts a step (B's counter), init
// and every candidate attempt get their own budget, so a player loop that never ends stops with
// RangeError("steps") in the worker and in the page frame (measured before N5b: 631d3938 main
// M9c-nav ran out of heap in own P). Offline, on the fake player written for these tests.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { solveWithOwn } from "../harness.mjs";
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
