// Phase 1.2 N5b: the environment tripwire (request.tripwire, worker mode only). A fake player whose
// n or sig reads navigator.cookieEnabled when called (the M9c-nav shape: the worker's stubs give
// one number, a real browser another, with no error) trips it: one more init error (class
// "Tripwire") and no answer for that kind. A pure player passes with the same answers. Offline, on
// the fake player written for these tests (no YouTube code).
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
const expected = { n: expectedN, sig: expectedSig };
const STRATEGIES = [{ report: true }, { strategy: "B", hint: false }];

test("a pure player passes the tripwire with the same answers and init errors", () => {
  const player = padded(fakePlayer());
  for (const strategy of STRATEGIES) {
    const label = JSON.stringify(strategy);
    const plain = solveWithOwn(own, player, inputs, strategy);
    const checked = solveWithOwn(own, player, inputs, { ...strategy, tripwire: true });
    assert.deepEqual(checked.n, plain.n, label);
    assert.deepEqual(checked.sig, plain.sig, label);
    assert.equal(checked.n[inputs.n[0]], expectedN(inputs.n[0]), label);
    assert.equal(checked.report.topLevelErrors, plain.report.topLevelErrors, label);
    assert.deepEqual(checked.report.tripwire, { checked: 2, tripped: [] }, label);
  }
});

test("an n or a sig that follows the environment trips it: an init error, no answer", () => {
  for (const strategy of STRATEGIES) {
    for (const [option, kind] of [["envN", "n"], ["envSig", "sig"]]) {
      const label = `${JSON.stringify(strategy)} ${option}`;
      const player = padded(fakePlayer({ [option]: true }));
      // Without the tripwire the worker answers from its stubs, with no error.
      const plain = solveWithOwn(own, player, inputs, strategy);
      assert.equal(plain[kind][inputs[kind][0]], expected[kind](inputs[kind][0]), label);
      const out = solveWithOwn(own, player, inputs, { ...strategy, tripwire: true });
      assert.deepEqual(out[kind], {}, label);
      assert.equal(out.report.topLevelErrors, plain.report.topLevelErrors + 1, label);
      assert.equal(out.report.topLevelErrorClasses.Tripwire, 1, label);
      assert.deepEqual(out.report.tripwire.tripped, [kind], label);
      assert.match(out.failed, new RegExp(`${kind}:environment`), label);
      // Page mode keeps the window's own objects: the tripwire does not run there.
      const page = solveWithOwn(own, player, inputs, { ...strategy, page: true, tripwire: true });
      assert.equal(page.report?.tripwire, undefined, label);
    }
  }
});
