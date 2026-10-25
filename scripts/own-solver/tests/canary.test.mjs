// Phase 1.1 S6 + Phase 1.2 N6: the own-solver canary's verdict rules (scripts/own-solver-canary.mjs)
// and the canary workflow's job summary (scripts/own-solver-canary-summary.mjs), offline.
import assert from "node:assert/strict";
import test from "node:test";
import { STRATEGIES, buildLine, canaryPasses, judge, verdict, verdicts, withRetry } from "../../own-solver-canary.mjs";
import { corpusVerdict, corpusVerdicts, mutationVerdicts, stepState, summaryMarkdown } from "../../own-solver-canary-summary.mjs";

const inputs = { n: ["a1", "a2"], sig: ["s1"] };
const ejs = { n: { a1: "x1", a2: "x2" }, sig: { s1: "y1" } };

test("judge counts what ejs solved and, of that, what the own core matched, got wrong or missed", () => {
  assert.deepEqual(judge(inputs, ejs, ejs), {
    n: { total: 2, ejs: 2, agree: 2, wrong: 0, missed: 0 },
    sig: { total: 1, ejs: 1, agree: 1, wrong: 0, missed: 0 },
  });
  assert.deepEqual(judge(inputs, ejs, { n: { a1: "x1", a2: "zz" }, sig: {} }), {
    n: { total: 2, ejs: 2, agree: 1, wrong: 1, missed: 0 },
    sig: { total: 1, ejs: 1, agree: 0, wrong: 0, missed: 1 },
  });
  // A value where ejs gave none cannot be judged: neither agree nor wrong (the verdict says oracle).
  assert.deepEqual(judge(inputs, { n: { a1: "x1" }, sig: {} }, ejs), {
    n: { total: 2, ejs: 1, agree: 1, wrong: 0, missed: 0 },
    sig: { total: 1, ejs: 0, agree: 0, wrong: 0, missed: 0 },
  });
});

test("a kind passes only when the own core equals ejs on every input of every present build", () => {
  const pass = { present: true, ...judge(inputs, ejs, ejs) };
  const missing = { present: true, ...judge(inputs, ejs, { n: { a1: "x1" }, sig: { s1: "y1" } }) };
  const wrong = { present: true, ...judge(inputs, ejs, { n: { a1: "x1", a2: "zz" }, sig: { s1: "y1" } }) };
  assert.equal(verdict({ main: pass, phone: pass, tce: { present: false } }, "n"), "pass");
  assert.equal(verdict({ main: pass, phone: missing }, "n"), "miss");
  assert.equal(verdict({ main: pass, phone: missing }, "sig"), "pass");
  assert.equal(verdict({ main: missing, phone: wrong }, "n"), "wrong");
});

test("an ejs failure is an oracle problem, a wrong value outranks it, and no build at all is a failure", () => {
  const oracle = { present: true, ...judge(inputs, { n: {}, sig: {} }, ejs) };
  const half = { present: true, ...judge(inputs, { n: { a1: "x1" }, sig: { s1: "y1" } }, { n: { a1: "zz" }, sig: { s1: "y1" } }) };
  assert.equal(verdict({ main: oracle }, "n"), "oracle");
  assert.equal(verdict({ main: half }, "n"), "wrong");
  assert.equal(verdict({ main: { present: false } }, "sig"), "fail");
  assert.equal(verdict({}, "n"), "fail");
});

test("N6: the canary judges P and B alone on every build and passes only when all four verdicts pass", () => {
  assert.deepEqual(Object.keys(STRATEGIES), ["P", "B"]);
  assert.deepEqual(STRATEGIES.P, {});
  assert.deepEqual(STRATEGIES.B, { strategy: "B", hint: false });
  const good = judge(inputs, ejs, ejs);
  const bMiss = judge(inputs, ejs, { n: ejs.n, sig: {} });
  const builds = {
    main: { present: true, ms: { ejs: 1, P: 2, B: 3 }, P: good, B: good },
    phone: { present: true, ms: { ejs: 1, P: 2, B: 3 }, P: good, B: bMiss },
    es6: { present: false },
  };
  const all = verdicts(builds);
  assert.deepEqual(all, { P: { n: "pass", sig: "pass" }, B: { n: "pass", sig: "miss" } });
  assert.equal(canaryPasses(all), false);
  assert.equal(canaryPasses({ P: { n: "pass", sig: "pass" }, B: { n: "pass", sig: "pass" } }), true);
  assert.equal(canaryPasses({ P: { n: "pass", sig: "pass" } }), false);
  assert.deepEqual(verdicts({ main: { present: false } }), { P: { n: "fail", sig: "fail" }, B: { n: "fail", sig: "fail" } });
});

test("N6: a build's line holds counts, timings and failure classes, never an input or a value", () => {
  const marked = { n: ["IN-n-1", "IN-n-2"], sig: ["IN-sig-1"] };
  const truth = { n: { "IN-n-1": "OUT-n-1", "IN-n-2": "OUT-n-2" }, sig: { "IN-sig-1": "OUT-sig-1" } };
  const build = {
    present: true,
    ms: { ejs: 11, P: 22, B: 33 },
    P: judge(marked, truth, { n: { "IN-n-1": "OUT-n-1", "IN-n-2": "OUT-other" }, sig: truth.sig }),
    B: judge(marked, truth, { n: {}, sig: {} }),
    failed: { B: "no-candidate" },
    errors: { ejs: "TypeError" },
  };
  const line = buildLine(build);
  assert.equal(
    line,
    "n: ejs 2/2, P 1/2 (wrong 1), B 0/2 | sig: ejs 1/1, P 1/1, B 0/1 | ms ejs 11, P 22, B 33 | B failed no-candidate | ejs TypeError",
  );
  assert.doesNotMatch(line, /IN-|OUT-/);
});

test("N6: a network call is tried three times before the canary gives up", async () => {
  let calls = 0;
  assert.equal(await withRetry(async () => (++calls < 3 ? Promise.reject(new Error("flaky")) : "ok"), 3, 0), "ok");
  assert.equal(calls, 3);
  calls = 0;
  await assert.rejects(withRetry(async () => { calls += 1; throw new Error("down"); }, 3, 0), /down/);
  assert.equal(calls, 3);
  calls = 0;
  assert.equal(await withRetry(async () => { calls += 1; return null; }, 3, 0), null);
  assert.equal(calls, 1);
});

// ---- the job summary (N6) ----

const ok = (player, variant, extra = {}) => ({
  player,
  variant,
  status: "ok",
  ejs: { vectorsPassed: 2, vectorsFailed: [], unsolved: [], total: 6 },
  own: { vectorsPassed: 2, vectorsFailed: [], agree: 6, disagree: [], missing: [], total: 6 },
  ...extra,
});

test("N6 summary: a step's state comes from its exit code", () => {
  assert.equal(stepState(undefined), "not run");
  assert.equal(stepState(""), "not run");
  assert.equal(stepState("0"), "pass");
  assert.equal(stepState(0), "pass");
  assert.equal(stepState("1"), "fail");
  assert.equal(stepState("2"), "could not run");
  assert.equal(stepState("137"), "could not run");
  // A step its timeout stopped writes no exit code; its outcome says so.
  assert.equal(stepState("", "failure"), "stopped");
  assert.equal(stepState(undefined, "cancelled"), "stopped");
  assert.equal(stepState("", "skipped"), "not run");
  assert.equal(stepState("0", "success"), "pass");
});

test("N6 summary: a corpus player's verdict over its builds, worst first", () => {
  const own = (fields) => ({ own: { ...ok("p", "main").own, ...fields } });
  assert.equal(corpusVerdict([ok("p", "main"), { player: "p", variant: "tce", status: "absent" }]), "pass");
  assert.equal(corpusVerdict([ok("p", "main"), { player: "p", variant: "es6", status: "gone" }]), "gone");
  assert.equal(corpusVerdict([ok("p", "main", own({ agree: 5, disagree: ["n#1"] }))]), "wrong");
  assert.equal(corpusVerdict([ok("p", "main", { ejs: { ...ok("p", "main").ejs, unsolved: ["sig#0"] } })]), "oracle");
  assert.equal(corpusVerdict([ok("p", "main", own({ agree: 5, missing: ["sig#2"] }))]), "miss");
  assert.equal(corpusVerdict([]), "not checked");
  assert.deepEqual(corpusVerdicts({ results: [ok("p1", "main"), ok("p2", "main"), ok("p1", "phone", own({ agree: 4, missing: ["n#0", "n#1"] }))] }), [
    { player: "p1", verdict: "miss" },
    { player: "p2", verdict: "pass" },
  ]);
});

test("N6 summary: a change-simulator class fails only on a wrong value", () => {
  const counts = (wrong) => ({ survived: 1, safe: 0, wrong });
  const report = {
    files: 8,
    seeds: 1,
    strategies: ["P", "B"],
    classes: {
      M1: { files: 8, skipped: 0, ejs: counts(0), ownP: counts(0), ownB: counts(0) },
      M3: { files: 8, skipped: 0, ejs: counts(0), ownP: counts(0), ownB: counts(2) },
      M8t: { files: 8, skipped: 8, ejs: counts(0), ownP: counts(0), ownB: counts(0) },
    },
  };
  assert.deepEqual(mutationVerdicts(report), {
    M1: { ejs: "pass", ownP: "pass", ownB: "pass" },
    M3: { ejs: "pass", ownP: "pass", ownB: "wrong" },
    M8t: { ejs: "not judged", ownP: "not judged", ownB: "not judged" },
  });
  assert.deepEqual(Object.keys(mutationVerdicts({ ...report, strategies: ["P"] }).M1), ["ejs", "ownP"]);
});

test("N6 summary: names players, builds, classes and verdicts only, and skips the reports of checks that could not run", () => {
  const canary = {
    player: "pA1",
    inCorpus: false,
    builds: {
      main: { present: true, ms: { ejs: 1, P: 1, B: 1 }, P: judge(inputs, ejs, ejs), B: judge(inputs, ejs, ejs) },
      tce: { present: false },
    },
    verdicts: { P: { n: "pass", sig: "pass" }, B: { n: "pass", sig: "miss" } },
  };
  const corpus = { results: [ok("pC1", "main"), ok("pC2", "main")] };
  const mutations = {
    files: 2,
    seeds: 1,
    strategies: ["P", "B"],
    classes: { M1: { files: 2, skipped: 0, ejs: { wrong: 0 }, ownP: { wrong: 0 }, ownB: { wrong: 0 } } },
  };
  const text = summaryMarkdown({ exits: { today: "1", corpusP: "0", corpusB: "0", mutations: "0" }, canary, corpusP: corpus, corpusB: corpus, mutations });
  assert.match(text, /\*\*Result: FAIL\*\*/);
  assert.match(text, /\| Today's player, 4 builds: ejs, own P, own B \| fail \|/);
  assert.match(text, /### Today's player `pA1` \(not in the corpus\)/);
  assert.match(text, /Builds: main; absent: tce/);
  assert.match(text, /\| own B \| pass \| miss \|/);
  assert.match(text, /\| `pC1` \| pass \| pass \|/);
  assert.match(text, /\| M1 \| pass \| pass \| pass \|/);
  assert.match(text, /pass = no wrong value/);
  for (const secret of ["x1", "x2", "y1", "a1", "a2", "s1", "://"]) assert.equal(text.includes(secret), false, secret);

  const blocked = summaryMarkdown({ exits: { today: "2", corpusP: "1" }, outcomes: { corpusB: "failure" }, canary, corpusP: corpus });
  assert.match(blocked, /\| Today's player, 4 builds: ejs, own P, own B \| could not run \|/);
  assert.match(blocked, /\| Corpus: own B alone \| stopped \|/);
  assert.match(blocked, /\| Change simulator, quick: ejs, own P, own B \| not run \|/);
  assert.doesNotMatch(blocked, /### Today's player/);
  assert.match(blocked, /\| `pC1` \| pass \| - \|/);

  const green = summaryMarkdown({ exits: { today: "0", corpusP: "0", corpusB: "0", mutations: "0" } });
  assert.match(green, /\*\*Result: pass\*\*/);
  // A player name is shown as code and only with the characters of a player ID.
  assert.match(summaryMarkdown({ exits: { today: "0" }, canary: { ...canary, player: "p|<b>" } }), /`pb`/);
});
