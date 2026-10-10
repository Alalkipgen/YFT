// Phase 1.1 S1: offline checks of the own-solver parity harness (no network, no player text).
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import {
  KINDS,
  VARIANTS,
  challenges,
  compare,
  coverage,
  generatedInputs,
  passes,
  playerUrl,
  summarize,
  validateCorpus,
} from "../harness.mjs";

const corpus = JSON.parse(readFileSync(new URL("../corpus.json", import.meta.url), "utf8"));
const URL_SAFE = /^[A-Za-z0-9_=-]+$/;
const vectors = [
  { player: "p1", n: [{ input: "nA", expected: "nA!" }], sig: [{ input: "sA", expected: "sA!" }] },
];
const truth = (inputs) => {
  const out = {};
  for (const kind of KINDS) out[kind] = Object.fromEntries(inputs[kind].map((i) => [i, `${i}!`]));
  return out;
};
const result = (ejs, own) => ({ player: "p1", variant: "main", status: "ok", ...compare(challenges("p1", vectors), ejs, own) });

test("committed corpus is well formed and covers every variant", () => {
  assert.deepEqual(validateCorpus(corpus), []);
  assert.ok(corpus.players.length >= 8);
  assert.deepEqual(coverage(corpus), Object.keys(VARIANTS));
});

test("validateCorpus names bad entries", () => {
  const files = Object.fromEntries(Object.keys(VARIANTS).map((v) => [v, null]));
  const bad = {
    players: [
      { player: "ok01", source: "x", files },
      { player: "ok01", source: "x", files },
      { player: "bad id!", source: "", files: { main: { sha256: "zz", bytes: 5 }, other: null } },
    ],
  };
  const problems = validateCorpus(bad);
  assert.ok(problems.some((p) => p.includes("duplicate")));
  assert.ok(problems.some((p) => p.includes("bad player ID")));
  assert.ok(problems.some((p) => p.includes("source missing")));
  assert.ok(problems.some((p) => p.includes("unknown variant other")));
  assert.ok(problems.some((p) => p.includes("bad sha256")));
  assert.ok(problems.some((p) => p.includes("bad size")));
  assert.ok(problems.some((p) => p.includes("tce not recorded")));
  assert.deepEqual(validateCorpus({}), ["corpus.players must be a list"]);
});

test("player URLs stay on youtube.com/s/player", () => {
  for (const variant of Object.keys(VARIANTS)) {
    assert.match(playerUrl("5203c085", variant), /^https:\/\/www\.youtube\.com\/s\/player\/5203c085\/[^?#]+\/base\.js$/);
  }
});

test("generated inputs are deterministic, URL-safe and shaped like YouTube's", () => {
  const a = generatedInputs("5203c085");
  assert.deepEqual(a, generatedInputs("5203c085"));
  assert.notDeepEqual(a, generatedInputs("74edf1a3"));
  assert.deepEqual(a.n.map((s) => s.length), [16, 18, 19]);
  assert.deepEqual(a.sig.map((s) => s.length), [104, 108]);
  for (const s of [...a.n, ...a.sig]) assert.match(s, URL_SAFE);
  assert.ok(a.n.every((s) => !s.includes("=")));
});

test("challenges merge vector inputs first, without duplicates", () => {
  const { steps, inputs } = challenges("p1", vectors);
  assert.equal(steps.n.length, 1);
  assert.equal(inputs.n[0], "nA");
  assert.equal(inputs.n.length, 4);
  assert.equal(inputs.sig.length, 3);
  const none = challenges("unknown", vectors);
  assert.deepEqual(none.steps, { n: [], sig: [] });
  assert.equal(none.inputs.n.length, 3);
});

test("sound oracle without an own core passes; own core matching ejs passes", () => {
  const { inputs } = challenges("p1", vectors);
  const alone = result(truth(inputs), null);
  assert.equal(passes(alone), true);
  assert.equal(alone.ejs.vectorsPassed, 2);
  const both = result(truth(inputs), truth(inputs));
  assert.equal(passes(both), true);
  assert.equal(both.own.agree, both.own.total);
});

test("oracle faults fail the file", () => {
  const { inputs } = challenges("p1", vectors);
  const wrong = truth(inputs);
  wrong.n.nA = "other";
  assert.equal(passes(result(wrong, null)), false);
  const unsolved = truth(inputs);
  delete unsolved.sig[inputs.sig[2]];
  const r = result(unsolved, null);
  assert.deepEqual(r.ejs.unsolved, ["sig#2"]);
  assert.equal(passes(r), false);
  assert.equal(passes(result({ error: "TimeoutError" }, null)), false);
});

test("own core disagreeing, missing or failing fails the file", () => {
  const { inputs } = challenges("p1", vectors);
  const disagree = truth(inputs);
  disagree.n[inputs.n[3]] = "x";
  const r1 = result(truth(inputs), disagree);
  assert.deepEqual(r1.own.disagree, ["n#3"]);
  assert.equal(passes(r1), false);
  const missing = truth(inputs);
  delete missing.sig.sA;
  const r2 = result(truth(inputs), missing);
  assert.deepEqual(r2.own.missing, ["sig#0"]);
  assert.deepEqual(r2.own.vectorsFailed, ["sig#0"]);
  assert.equal(passes(r2), false);
  const r3 = result(truth(inputs), { n: {}, sig: {}, failed: "no-n-function" });
  assert.equal(r3.own.failed, "no-n-function");
  assert.equal(passes(r3), false);
});

test("absent variants pass, download faults fail", () => {
  assert.equal(passes({ status: "absent" }), true);
  assert.equal(passes({ status: "gone" }), false);
  assert.equal(passes({ status: "download-failed" }), false);
  assert.equal(passes({ status: "hash-mismatch" }), false);
});

test("summary names only players, variants, kinds and indexes", () => {
  const { inputs } = challenges("p1", vectors);
  const disagree = truth(inputs);
  disagree.sig[inputs.sig[1]] = "leak-me";
  const lines = summarize([result(truth(inputs), disagree), { player: "p1", variant: "phone", status: "absent" }], true);
  const text = lines.join("\n");
  assert.match(text, /disagree sig#1/);
  assert.match(text, /FAIL/);
  assert.match(text, /phone absent/);
  assert.match(text, /1\/2 files pass/);
  for (const secret of [...inputs.n, ...inputs.sig, "leak-me", "nA!", "sA!"]) assert.ok(!text.includes(secret));
  assert.match(summarize([], false).join("\n"), /own core not built yet/);
});
