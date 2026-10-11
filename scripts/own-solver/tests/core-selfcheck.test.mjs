// Phase 1.1 S4: own solver core — G3 encoding checks and SelfCheck. Every injected fault must end
// as a FAIL of its kind (values dropped), never as a wrong value. Offline, on a fake player
// written for these tests (no YouTube code).
import assert from "node:assert/strict";
import test from "node:test";
import { expectedN, expectedSig, fakePlayer } from "./fake-player.mjs";
import { inside, padded, solve, solverContext } from "./solver-context.mjs";

const N = ["ZdZIqFPQK-Ty8wId", "abcDEF0123456789xyz"];
const SIG = ["AOq0QJ8wRAIgXmPlOPSBkkUs1bYFYlJCfe29xx8j7v1pDL0QwbdV96sCIEzpWqMGkFR20CFOg51Tp-7vj_EMu-m37KtXJoOySqa0wWq="];

function run(options, request = {}) {
  return solve({ player: padded(fakePlayer(options)), n: N, sig: SIG, ...request });
}

/** No value the solver returned may differ from the true one. */
function neverWrong(output) {
  for (const [input, value] of Object.entries(output.n)) assert.equal(value, expectedN(input));
  for (const [input, value] of Object.entries(output.sig)) assert.equal(value, expectedSig(input));
}

function failedKind(output, kind) {
  assert.match(output.failed ?? "", new RegExp(`(^|,)${kind}:`));
  assert.deepEqual(output[kind], {});
  assert.equal(output.report[kind].status, "failed");
  return output.report[kind].reason;
}

test("a clean player passes every check", () => {
  const output = run({});
  assert.equal(output.failed, undefined);
  assert.deepEqual([output.report.n.status, output.report.sig.status], ["ok", "ok"]);
  neverWrong(output);
  assert.equal(Object.keys(output.n).length + Object.keys(output.sig).length, 3);
});

test("agreement: a wrong candidate inside the player is a FAIL, never a value", () => {
  const output = run({ decoy: true });
  assert.equal(failedKind(output, "sig"), "disagree");
  assert.equal(output.report.n.status, "ok");
  neverWrong(output);
});

test("fault: an injected wrong candidate is a FAIL of the kind it alters", () => {
  for (const kind of ["n", "sig"]) {
    const output = run({}, { faults: { decoy: kind } });
    assert.equal(failedKind(output, kind), "disagree");
    neverWrong(output);
  }
});

test("fault: a broken encoding is a FAIL, never a wrong value (G3 edge probe)", () => {
  const output = run({}, { faults: { rawEncoding: true } });
  assert.ok(["probe", "shape", "encoding"].includes(failedKind(output, "sig")));
  assert.equal(output.report.n.status, "ok");
  neverWrong(output);
});

test("fault: a missing global is a FAIL, never a wrong value (G4)", () => {
  const output = run({ needsSelf: true }, { faults: { omit: ["self"] } });
  assert.equal(failedKind(output, "n"), "probe");
  neverWrong(output);
  for (const name of ["window", "location", "navigator", "document", "XMLHttpRequest"]) {
    neverWrong(run({ needsSelf: true }, { faults: { omit: [name] } }));
  }
});

test("shape: n must be URL-safe, differ from its input and keep a sane length", () => {
  const verdicts = inside(solverContext(), () => {
    const shape = yftOwnSolver.internals.shapeOk;
    return [
      shape("n", "abcdefghijklmnop", "QRSTuvwx-_09"),
      shape("n", "abcdefghijklmnop", "abcdefghijklmnop"),
      shape("n", "abcdefghijklmnop", "abc def"),
      shape("n", "abcdefghijklmnop", "x".repeat(41)),
    ];
  });
  assert.deepEqual(verdicts, [true, false, false, false]);
});

test("shape: sig must reuse its input's characters and keep a plausible length", () => {
  const verdicts = inside(solverContext(), () => {
    const shape = yftOwnSolver.internals.shapeOk;
    const input = "AB%C/D=E+F" + "g".repeat(90);
    return [
      shape("sig", input, input.slice(3)),
      shape("sig", input, input.split("").reverse().join("")),
      shape("sig", input, input + "x"),
      shape("sig", input, input.slice(0, 50)),
      shape("sig", input, "~" + input.slice(2)),
    ];
  });
  assert.deepEqual(verdicts, [true, true, false, false, false]);
  const output = run({ badSig: true });
  assert.equal(failedKind(output, "sig"), "shape");
});

test("G3 index map: the edge probe must follow the positions the map probe reveals", () => {
  const mapped = inside(solverContext(), () => {
    const { applyIndexMap, probes } = yftOwnSolver.internals;
    const reversed = probes.sigMap.split("").reverse().join("");
    return [
      applyIndexMap(probes.sigMap, reversed, probes.sigEdge) === probes.sigEdge.split("").reverse().join(""),
      applyIndexMap(probes.sigMap, "!" + reversed.slice(1), probes.sigEdge),
      new Set(probes.sigMap).size,
      probes.sigMap.length === probes.sigEdge.length,
    ];
  });
  assert.deepEqual(mapped, [true, null, 64, true]);
  const output = run({ sortSig: true });
  assert.equal(failedKind(output, "sig"), "encoding");
});

test("distinct n inputs must give distinct outputs", () => {
  assert.equal(failedKind(run({ constantN: true }), "n"), "not-distinct");
});

test("known vectors must match", () => {
  const good = run({}, { vectors: { n: [{ input: "vectorInputAbc123", expected: expectedN("vectorInputAbc123") }] } });
  assert.equal(good.report.n.status, "ok");
  const bad = run({}, { vectors: { sig: [{ input: SIG[0], expected: SIG[0].slice(4) }] } });
  assert.equal(failedKind(bad, "sig"), "vector");
  neverWrong(bad);
});

test("the first value must still be the same at the end of the run", () => {
  const context = solverContext();
  const baseline = solve({ player: padded(fakePlayer({ driftAfter: 1e9 })), n: N }, context);
  assert.equal(baseline.report.n.status, "ok");
  const calls = context.__fakeCalls;
  // Drift just before the final stability run (it calls the scramble once per read path).
  const output = run({ driftAfter: calls - 3 }, { sig: [] });
  assert.equal(failedKind(output, "n"), "unstable");
});

test("probes and reports never carry an input or an output", () => {
  const outputs = [run({}), run({ decoy: true }), run({ throwing: true }), run({}, { faults: { rawEncoding: true } })];
  for (const output of outputs) {
    const report = JSON.stringify(output.report) + String(output.failed ?? "");
    for (const value of [...N, ...SIG, ...N.map(expectedN), ...SIG.map(expectedSig)]) {
      assert.ok(!report.includes(value));
    }
  }
});
