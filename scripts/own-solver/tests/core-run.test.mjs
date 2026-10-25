// Phase 1.1 S3: own solver core — G4 environment and G1 multiTry in the worker-shaped context
// (offline, on a fake player written for these tests; no YouTube code).
import assert from "node:assert/strict";
import test from "node:test";
import { expectedN, expectedSig, fakePlayer } from "./fake-player.mjs";
import { inside, padded, solve, solverContext } from "./solver-context.mjs";

const N = ["ZdZIqFPQK-Ty8wId", "abcDEF0123456789xyz"];
const SIG = ["AOq0QJ8wRAIgXmPlOPSBkkUs1bYFYlJCfe29xx8j7v1pDL0QwbdV96sCIEzpWqMGkFR20CFOg51Tp-7vj_EMu-m37KtXJoOySqa0" + "wWq="];

function expectAll(output, n, sig) {
  for (const input of n) assert.equal(output.n[input], expectedN(input));
  for (const input of sig) assert.equal(output.sig[input], expectedSig(input));
}

test("multiTry gets n and sig from the fake player in every code style and wrapper", () => {
  const variants = [
    {},
    { style: "es6" },
    { style: "seq" },
    { nIn: "build" },
    { style: "es6", nIn: "build" },
    { setter: "Ax", getter: "By" },
    { wrapper: "call" },
    { wrapper: "arrow" },
  ];
  for (const options of variants) {
    const output = solve({ player: padded(fakePlayer(options)), n: N, sig: SIG });
    assert.equal(output.failed, undefined, JSON.stringify(options));
    expectAll(output, N, SIG);
    assert.equal(output.report.n.solved, N.length);
    assert.equal(output.report.sig.solved, SIG.length);
  }
});

test("sig goes in URI-encoded and comes out decoded exactly once (G3 application)", () => {
  const edge = [
    "abc%2Fdef/=+ ghi\tjklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyz0123456789",
    "%%%25%zz&?#=+/\n\r\u0001" + "Z".repeat(60) + "0123456789-_",
    "==" + "x".repeat(100) + "==",
  ];
  const output = solve({ player: padded(fakePlayer()), sig: edge });
  assert.equal(output.failed, undefined);
  expectAll(output, [], edge);
});

test("a failing top-level statement or candidate does not stop the rest", () => {
  const output = solve({ player: padded(fakePlayer({ throwing: true })), n: N, sig: SIG });
  assert.equal(output.failed, undefined);
  expectAll(output, N, SIG);
  assert.ok(output.report.topLevelErrors >= 1);
  assert.deepEqual(output.report.sig.errors, ["1:Error"]);
});

test("failures name candidate indexes and error classes only, never values", () => {
  const output = solve({ player: padded(fakePlayer({ throwing: true })), n: N, sig: SIG });
  const report = JSON.stringify(output.report) + String(output.failed ?? "");
  for (const value of [...N, ...SIG, ...N.map(expectedN), ...SIG.map(expectedSig)]) {
    assert.ok(!report.includes(value));
    assert.ok(!report.includes(encodeURIComponent(value)));
  }
  assert.ok(!report.includes("cannot use"));
});

test("G4 environment: browser-like globals are own properties and nothing navigates", () => {
  const seen = inside(solverContext(), () => {
    yftOwnSolver.internals.installEnvironment(globalThis, { userAgent: "UA-test", language: "my-MM" });
    const before = location.href;
    location.assign("https://example.invalid/");
    location.replace("https://example.invalid/");
    return {
      window: window === globalThis,
      self: self === globalThis,
      own: ["window", "self", "location", "navigator", "document"].every((name) =>
        Object.prototype.hasOwnProperty.call(globalThis, name)),
      href: location.href,
      stayed: location.href === before,
      origin: location.origin,
      userAgent: navigator.userAgent,
      language: navigator.language,
      cookie: document.cookie,
      documentUrl: document.URL,
      xhr: typeof XMLHttpRequest,
    };
  });
  assert.deepEqual(seen, {
    window: true,
    self: true,
    own: true,
    href: "https://www.youtube.com/watch?v=yft-test",
    stayed: true,
    origin: "https://www.youtube.com",
    userAgent: "UA-test",
    language: "my-MM",
    cookie: "",
    documentUrl: "https://www.youtube.com/watch?v=yft-test",
    xhr: "function",
  });
});

test("G4: player tables read from self and navigator work in the worker-shaped context", () => {
  const output = solve({ player: padded(fakePlayer({ needsSelf: true })), n: N });
  assert.equal(output.failed, undefined);
  expectAll(output, N, []);
});

test("run refuses a missing or failed preparation", () => {
  const output = inside(solverContext(), () => [
    yftOwnSolver.run(null, { n: ["abc"] }).failed,
    yftOwnSolver.run({ ok: false, failed: "parse" }, { n: ["abc"] }).failed,
  ]);
  assert.deepEqual(output, ["not prepared", "not prepared"]);
});

test("prepare once, run many times: the prepared program is reusable", () => {
  const values = inside(solverContext(), (player, n) => {
    const prepared = yftOwnSolver.prepare(player);
    return [yftOwnSolver.run(prepared, { n: [n] }).n[n], yftOwnSolver.run(prepared, { n: [n] }).n[n]];
  }, padded(fakePlayer()), N[0]);
  assert.deepEqual(values, [expectedN(N[0]), expectedN(N[0])]);
});

test("an empty request asks for nothing and solves nothing", () => {
  const output = solve({ player: padded(fakePlayer()) });
  assert.deepEqual([output.n, output.sig, output.failed], [{}, {}, undefined]);
});
