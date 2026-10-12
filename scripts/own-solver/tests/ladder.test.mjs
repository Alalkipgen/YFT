// Phase 1.2 N5a: the ladder rule on the computer (ladder.mjs) against the table OwnSolverLadderRule.kt
// reads too, and the runners' use of it (one file's runs, lazily). Offline; no player code except
// the fake player written for these tests.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { decide, ladder, lazyLadder, makeStep, needsPage, stepOf } from "../ladder.mjs";
import { solveWithOwn } from "../harness.mjs";
import { fakePlayer } from "./fake-player.mjs";
import { padded } from "./solver-context.mjs";

const table = JSON.parse(
  readFileSync(new URL("../../../extractor-master/src/test/resources/own-solver/ladder-rule-cases.json", import.meta.url), "utf8"),
);

test("every case of the shared table (also read by OwnSolverLadderRuleTest.kt)", () => {
  assert.ok(table.cases.length >= 15);
  const seen = new Set();
  for (const c of table.cases) {
    const workers = c.worker.map(makeStep);
    const page = c.page ? makeStep(c.page) : null;
    for (const kind of ["n", "sig"]) {
      assert.equal(decide(kind, workers, page, c.live), c.expect[kind], `${c.name} (${kind})`);
      seen.add(`${kind}:${c.expect[kind]}`);
    }
  }
  for (const kind of ["n", "sig"]) for (const source of ["P", "B", "E", "none"]) assert.ok(seen.has(`${kind}:${source}`), `${kind}:${source}`);
});

const inputs = { n: ["n1", "n2"], sig: ["s1"] };
const truth = { n: { n1: "N1", n2: "N2" }, sig: { s1: "S1" } };
const out = (values, init, failed) => ({
  n: values.n ?? {},
  sig: values.sig ?? {},
  ...(failed ? { failed } : {}),
  ...(init === undefined ? {} : { report: { topLevelErrors: init } }),
});

test("a harness output becomes a step: all inputs answered, the init count, failure classes", () => {
  const step = stepOf(out({ n: { n1: "a", n2: "b" }, sig: { s1: "" } }, 0, "sig:disagree"), inputs);
  assert.deepEqual([...step.answered], ["n"]);
  assert.equal(step.init, 0);
  assert.deepEqual(step.failed, ["sig:disagree"]);
  const partial = stepOf(out({ n: { n1: "a" } }, undefined), inputs);
  assert.deepEqual([...partial.answered], []);
  assert.equal(partial.init, null);
  const crashed = stepOf({ n: {}, sig: {}, error: "TypeError", report: null }, inputs);
  assert.ok(needsPage(crashed));
  assert.equal(stepOf(null, inputs), null);
});

test("the ladder takes a clean worker answer, keeps answers after init errors out and needs V for a page signature", () => {
  const right = { n: { n1: "N1", n2: "N2" }, sig: { s1: "S1" } };
  const wrongSig = { n: { n1: "N1", n2: "N2" }, sig: { s1: "XX" } };
  // Worker P clean: P for both, the page never matters.
  assert.deepEqual(ladder({ workerP: out(right, 0) }, inputs).sources, { n: "P", sig: "P" });
  // Worker B answered after init errors (the N4b case); page P clean.
  const outs = { workerP: out({}, 3, "n:disagree,sig:disagree"), workerB: out(wrongSig, 3), pageP: out(right, 0), pageB: out(right, 0) };
  const offline = ladder(outs, inputs, { page: "PB", live: "offline" });
  assert.deepEqual(offline.sources, { n: "E", sig: "none" });
  assert.deepEqual(offline.n, truth.n);
  assert.deepEqual(offline.sig, {});
  assert.equal(offline.failed, "sig:ladder");
  const sim = ladder(outs, inputs, { page: "PB", live: "sim", truth });
  assert.deepEqual(sim.sources, { n: "E", sig: "E" });
  assert.deepEqual(sim.sig, truth.sig);
  // A page signature that differs from the truth fails V: no signature.
  const badPage = ladder({ ...outs, pageP: out(wrongSig, 0), pageB: out(wrongSig, 0) }, inputs, { page: "PB", live: "sim", truth });
  assert.deepEqual(badPage.sources, { n: "E", sig: "none" });
  // "PB": page B stands in for a kind page P did not answer cleanly; "B": page B only.
  const pagePInit = { ...outs, pageP: out(right, 1) };
  assert.deepEqual(ladder(pagePInit, inputs, { page: "PB", live: "sim", truth }).sources, { n: "E", sig: "E" });
  assert.deepEqual(ladder({ ...outs, pageB: out({}, 0, "n:disagree,sig:disagree") }, inputs, { page: "B", live: "sim", truth }).sources, { n: "none", sig: "none" });
  // Plain refusals in the worker never reach the page.
  const refused = { workerP: out({}, 0, "n:disagree,sig:disagree"), workerB: out({}, 0, "n:disagree,sig:disagree"), pageP: out(right, 0) };
  assert.deepEqual(ladder(refused, inputs, { live: "sim", truth }).sources, { n: "none", sig: "none" });
});

test("the lazy ladder runs worker B, the page and page B only when the rule needs them", async () => {
  const right = { n: { n1: "N1", n2: "N2" }, sig: { s1: "S1" } };
  const runs = async (table, options) => {
    const called = [];
    const result = await lazyLadder(async (name) => { called.push(name); return table[name] ?? null; }, inputs, options);
    return { called, sources: result.ladder.sources };
  };
  assert.deepEqual(await runs({ workerP: out(right, 0) }), { called: ["workerP"], sources: { n: "P", sig: "P" } });
  assert.deepEqual(
    await runs({ workerP: out({ n: right.n }, 0, "sig:disagree"), workerB: out(right, 0) }),
    { called: ["workerP", "workerB"], sources: { n: "P", sig: "B" } },
  );
  assert.deepEqual(
    await runs({ workerP: out({}, 0, "n:disagree,sig:disagree"), workerB: out({}, 0, "n:disagree,sig:disagree") }),
    { called: ["workerP", "workerB"], sources: { n: "none", sig: "none" } },
  );
  assert.deepEqual(
    await runs({ workerP: out(right, 2), workerB: out(right, 2), pageP: out(right, 0) }, { page: "PB", live: "sim", truth }),
    { called: ["workerP", "workerB", "pageP"], sources: { n: "E", sig: "E" } },
  );
  assert.deepEqual(
    await runs({ workerP: out(right, 2), workerB: out(right, 2), pageP: out(right, 1), pageB: out(right, 0) }, { page: "PB", live: "offline" }),
    { called: ["workerP", "workerB", "pageP", "pageB"], sources: { n: "E", sig: "none" } },
  );
  assert.deepEqual(
    await runs({ workerP: out(right, 2), workerB: out(right, 2), pageB: out(right, 0) }, { page: "B", live: "offline" }),
    { called: ["workerP", "workerB", "pageB"], sources: { n: "E", sig: "none" } },
  );
});

test("on the fake player, whose own top-level code throws once everywhere, no step is trusted", () => {
  const assets = new URL("../../../extractor-master-android/src/main/assets/yft-own-solver/", import.meta.url);
  const own = { lib: readFileSync(new URL("meriyah.umd.min.js", assets), "utf8"), core: readFileSync(new URL("own.solver.core.js", assets), "utf8") };
  const fake = { n: ["ZdZIqFPQK-Ty8wId"], sig: ["AOq0QJ8wRAIgXmPlOPSBkkUs1bYFYlJCfe29xx8j7v1pDL0QwbdV96sCIEzpWqMGkFR20CFOg51Tp-7vj_EMu-m37KtXJoOySqa0wWq="] };
  const player = padded(fakePlayer());
  const outs = {
    workerP: solveWithOwn(own, player, fake, { report: true }),
    workerB: solveWithOwn(own, player, fake, { strategy: "B", hint: false }),
    pageP: solveWithOwn(own, player, fake, { page: true, report: true }),
  };
  for (const name of Object.keys(outs)) assert.equal(stepOf(outs[name], fake).init, 1, name);
  const result = ladder(outs, fake, { page: "PB", live: "offline" });
  assert.deepEqual(result.sources, { n: "none", sig: "none" });
  assert.deepEqual(result.n, {});
  assert.equal(result.failed, "n:ladder,sig:ladder");
});

test("--dyncode counts player eval reads and Function calls at init and later, and a direct eval keeps its scope", () => {
  const assets = new URL("../../../extractor-master-android/src/main/assets/yft-own-solver/", import.meta.url);
  const own = { lib: readFileSync(new URL("meriyah.umd.min.js", assets), "utf8"), core: readFileSync(new URL("own.solver.core.js", assets), "utf8") };
  const fake = { n: ["ZdZIqFPQK-Ty8wId"], sig: ["AOq0QJ8wRAIgXmPlOPSBkkUs1bYFYlJCfe29xx8j7v1pDL0QwbdV96sCIEzpWqMGkFR20CFOg51Tp-7vj_EMu-m37KtXJoOySqa0wWq="] };
  const plain = padded(fakePlayer());
  // One Function call and one eval read at the top level; a direct eval of a local in n.
  const dynamic = plain
    .replace("var T,K,A,NQ;", "var T,K,A,NQ;var DY=Function(\"return 2\")()+eval(\"1\");")
    .replace('b.join("").slice(2)', 'eval("b").join("").slice(2)');
  assert.notEqual(dynamic, plain);
  for (const extra of [{ report: true }, { page: true, report: true }]) {
    const label = JSON.stringify(extra);
    const before = solveWithOwn(own, plain, fake, { ...extra, dyncode: true });
    assert.deepEqual(before.dyncode, { evalInit: 0, evalCall: 0, functionInit: 0, functionCall: 0, otherInit: 0, otherCall: 0, unknown: 0 }, label);
    const after = solveWithOwn(own, dynamic, fake, { ...extra, dyncode: true });
    const reference = solveWithOwn(own, dynamic, fake, extra);
    assert.equal(after.dyncode.functionInit, 1, label);
    assert.equal(after.dyncode.evalInit, 1, label);
    assert.ok(after.dyncode.evalCall >= 1, label);
    assert.equal(after.dyncode.unknown, 0, label);
    // The counting changes nothing: same answers with and without it, and n uses its local.
    assert.deepEqual(after.n, reference.n, label);
    assert.deepEqual(after.n, before.n, label);
    assert.ok(Object.keys(after.n).length > 0, label);
  }
});
