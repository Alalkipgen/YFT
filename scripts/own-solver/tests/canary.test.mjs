// Phase 1.1 S6: the own-solver canary's verdict rules (scripts/own-solver-canary.mjs), offline.
import assert from "node:assert/strict";
import test from "node:test";
import { judge, verdict } from "../../own-solver-canary.mjs";

const inputs = { n: ["a1", "a2"], sig: ["s1"] };
const ejs = { n: { a1: "x1", a2: "x2" }, sig: { s1: "y1" } };

test("judge counts what ejs solved, what the own core matched and what it got wrong", () => {
  assert.deepEqual(judge(inputs, ejs, ejs), {
    n: { total: 2, ejs: 2, agree: 2, wrong: 0 },
    sig: { total: 1, ejs: 1, agree: 1, wrong: 0 },
  });
  assert.deepEqual(judge(inputs, ejs, { n: { a1: "x1", a2: "zz" }, sig: {} }), {
    n: { total: 2, ejs: 2, agree: 1, wrong: 1 },
    sig: { total: 1, ejs: 1, agree: 0, wrong: 0 },
  });
});

test("a kind passes only when the own core equals ejs on every input of every present build", () => {
  const pass = { present: true, ...judge(inputs, ejs, ejs) };
  const missing = { present: true, ...judge(inputs, ejs, { n: { a1: "x1" }, sig: { s1: "y1" } }) };
  assert.equal(verdict({ main: pass, phone: pass, tce: { present: false } }, "n"), "pass");
  assert.equal(verdict({ main: pass, phone: missing }, "n"), "fail");
  assert.equal(verdict({ main: pass, phone: missing }, "sig"), "pass");
});

test("an ejs failure is reported as an oracle problem, and no build at all is a failure", () => {
  const oracle = { present: true, ...judge(inputs, { n: {}, sig: {} }, ejs) };
  assert.equal(verdict({ main: oracle }, "n"), "oracle");
  assert.equal(verdict({ main: { present: false } }, "sig"), "fail");
  assert.equal(verdict({}, "n"), "fail");
});
