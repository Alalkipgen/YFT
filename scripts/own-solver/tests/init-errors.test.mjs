// Phase 1.2 N4b: init errors (a top-level statement the worker cannot run, such as a DOM call the
// worker's stubs lack) are counted by class for the report, and the faults suite's "init-wrong"
// case (an init error after which every candidate agrees on a wrong value) does what the suite
// expects. Offline, on the fake player written for these tests (no YouTube code).
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { FAULTS, faultVerdict, solveWithOwn } from "../harness.mjs";
import { expectedN, expectedSig, fakePlayer } from "./fake-player.mjs";
import { padded } from "./solver-context.mjs";

const assets = new URL("../../../extractor-master-android/src/main/assets/yft-own-solver/", import.meta.url);
const read = (name) => readFileSync(new URL(name, assets), "utf8");
const own = { lib: read("meriyah.umd.min.js"), core: read("own.solver.core.js") };
const N = ["ZdZIqFPQK-Ty8wId", "abcDEF0123456789xyz"];
const SIG = ["AOq0QJ8wRAIgXmPlOPSBkkUs1bYFYlJCfe29xx8j7v1pDL0QwbdV96sCIEzpWqMGkFR20CFOg51Tp-7vj_EMu-m37KtXJoOySqa0wWq="];
const inputs = { n: N, sig: SIG };
const STRATEGIES = [{ report: true }, { strategy: "B", hint: false }];
const expected = { n: expectedN, sig: expectedSig };
const rotated = (value) => value.slice(1) + value.charAt(0);

// The fake player's own `g.audio=window.AudioContext.prototype` throws a TypeError at the top level
// in both environments (neither has AudioContext): every run below starts from that one error.
const BASE = { topLevelErrors: 1, topLevelErrorClasses: { TypeError: 1 } };
const errorsOf = (output) => ({
  topLevelErrors: output.report.topLevelErrors,
  topLevelErrorClasses: output.report.topLevelErrorClasses,
});

test("init errors are counted by class for the report (classes only)", () => {
  const player = padded(fakePlayer({ needsDom: true }));
  for (const strategy of STRATEGIES) {
    const label = JSON.stringify(strategy);
    assert.deepEqual(errorsOf(solveWithOwn(own, padded(fakePlayer()), inputs, strategy)), BASE, label);
    // needsDom: the DOM call the worker lacks is one more TypeError there, and n fails safely.
    const worker = solveWithOwn(own, player, inputs, strategy);
    assert.deepEqual(errorsOf(worker), { topLevelErrors: 2, topLevelErrorClasses: { TypeError: 2 } }, label);
    assert.equal(worker.n[N[0]], undefined, label);
    // The page (here the harness's DOM stub) has it: no extra error, and n is right.
    const page = solveWithOwn(own, player, inputs, { ...strategy, page: true });
    assert.deepEqual(errorsOf(page), BASE, label);
    assert.equal(page.n[N[0]], expectedN(N[0]), label);
  }
  const throwing = solveWithOwn(own, padded(fakePlayer({ throwing: true })), inputs, { report: true });
  const text = JSON.stringify(throwing.report);
  for (const value of [...N, ...SIG]) assert.ok(!text.includes(value));
});

test("init error classes: at most 8 named classes, the rest as other; odd names never pass through", () => {
  const names = ["ClassA", "ClassB", "ClassC", "ClassD", "ClassE", "ClassF", "ClassG", "ClassH", "ClassI", "bad name!"];
  const extra = names.map((name, i) => `var yftE${i}=(function(){throw {name:${JSON.stringify(name)}}})();`).join("\n");
  const player = padded(fakePlayer().replace("g.ready=!0,g.count=3;", `${extra}\ng.ready=!0,g.count=3;`));
  for (const strategy of STRATEGIES) {
    const output = solveWithOwn(own, player, inputs, strategy);
    const label = JSON.stringify(strategy);
    assert.equal(output.report.topLevelErrors, 11, label);
    assert.deepEqual(
      output.report.topLevelErrorClasses,
      { ClassA: 1, ClassB: 1, ClassC: 1, ClassD: 1, ClassE: 1, ClassF: 1, ClassG: 1, TypeError: 1, other: 3 },
      label,
    );
    for (const kind of ["n", "sig"]) {
      for (const input of inputs[kind]) assert.equal(output[kind][input], expected[kind](input), label);
    }
  }
  const odd = padded(fakePlayer().replace("g.ready=!0,g.count=3;", 'var yftOdd=(function(){throw {name:"bad name!"}})();\nvar yftText=(function(){throw "text"})();\ng.ready=!0,g.count=3;'));
  const output = solveWithOwn(own, odd, inputs, { report: true });
  assert.deepEqual(output.report.topLevelErrorClasses, { Error: 1, TypeError: 1, string: 1 });
});

test("fault init-wrong: after an init error every candidate agrees on a wrong value (worker mode only)", () => {
  const player = padded(fakePlayer());
  for (const strategy of STRATEGIES) {
    for (const kind of ["n", "sig"]) {
      const label = JSON.stringify({ ...strategy, kind });
      const other = kind === "n" ? "sig" : "n";
      const worker = solveWithOwn(own, player, inputs, { ...strategy, faults: { initWrong: kind } });
      assert.deepEqual(errorsOf(worker), { topLevelErrors: 2, topLevelErrorClasses: { TypeError: 2 } }, label);
      assert.equal(worker.failed, undefined, label);
      // SelfCheck cannot see a value every candidate agrees on. These are the wrong values the
      // N4b rule must keep out (a worker answer after an init error stays unverified until E
      // agrees, or V passes for sig); until then the faults suite counts them as wrong.
      for (const input of inputs[kind]) assert.equal(worker[kind][input], rotated(expected[kind](input)), label);
      for (const input of inputs[other]) assert.equal(worker[other][input], expected[other](input), label);
      const page = solveWithOwn(own, player, inputs, { ...strategy, page: true, faults: { initWrong: kind } });
      assert.deepEqual(errorsOf(page), BASE, label);
      for (const input of inputs[kind]) assert.equal(page[kind][input], expected[kind](input), label);
    }
  }
});

test("the faults suite has the init-wrong cases and they pass only with 0 wrong values", () => {
  const names = FAULTS.map((fault) => fault.name);
  assert.deepEqual(names.slice(-2), ["init-wrong-n", "init-wrong-sig"]);
  const fault = FAULTS.find((entry) => entry.name === "init-wrong-n");
  const ejs = { n: { a: "A" }, sig: { b: "B" } };
  assert.equal(faultVerdict(fault, ejs, { n: { a: "Z" }, sig: { b: "B" } }).ok, false);
  assert.equal(faultVerdict(fault, ejs, { n: {}, sig: { b: "B" }, failed: "n:probe" }).ok, true);
  assert.equal(faultVerdict(fault, ejs, { n: { a: "A" }, sig: { b: "B" } }).ok, true);
});
