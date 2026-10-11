// Phase 1.2 N1 (T3): the change simulator's mutators keep behaviour. Each test mutates a small
// hand-written program (no YouTube code), runs the original and the mutated text in a bare V8
// context, and checks they return the same values. Covers the scope cases a rename can break:
// shadowing, hoisting, closures, catch parameters, default parameters, block scope, globals.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import vm from "node:vm";
import * as M from "../mutate.mjs";

const lib = readFileSync(new URL("../../../app/src/main/assets/youtube-solver/yt.solver.lib.min.js", import.meta.url), "utf8");
const cg = vm.createContext({});
vm.runInContext(lib, cg);
vm.runInContext("var meriyah = lib.meriyah, astring = lib.astring;", cg);
const parse = (text) => vm.runInContext("meriyah.parse", cg)(text, { ranges: false, webcompat: true, loc: false });
const generate = (ast) => vm.runInContext("astring.generate", cg)(ast);
M.setStatementParser((src) => parse(src).body);

// Runs [text] (which must assign `result`) in a fresh context and returns `result` as JSON.
function run(text) {
  const context = vm.createContext({});
  vm.runInContext(text, context, { timeout: 5000 });
  return JSON.stringify(context.result);
}

function mutated(text, mutator, seed = "s") {
  const program = parse(text);
  const edits = mutator(program, M.rng(seed));
  return { edits, text: generate(program) };
}

// A wrapper-shaped program like a player build: exactly two top-level statements, a namespace
// object and an IIFE over it (the IIFE copies its answer to the global `result`).
const WRAPPED = (inner) => `var ns = {}; (function (g) { ${inner} result = g.out; })(ns);`;

const SCOPE_CASES = {
  shadowing: `var x = 1; function f(x) { return x + 1; } function h() { var x = 10; return x; } result = [f(5), h(), x];`,
  hoisting: `result = [typeof later, g()]; function g() { return "hoisted"; } var later = 3;`,
  closures: `function counter() { var c = 0; return function () { c += 1; return c; }; } var k = counter(); k(); result = [k(), k()];`,
  catchParam: `var e = "outer"; try { throw "inner"; } catch (e) { var seen = e; } result = [e, seen];`,
  defaultParams: `var d = 4; function f(a, b = a * d) { return b; } result = [f(2), f(2, 1)];`,
  blockScope: `let a = 1; { let a = 2; var b = a; } result = [a, b];`,
  globalsUntouched: `var m = Math; result = [typeof m.max, JSON.stringify([1]), String(parseInt("7"))];`,
  shorthand: `var a = 1, b = { a }; var { a: c } = b; result = [b.a, Object.keys(b), c];`,
  namedFunctionExpression: `var f = 1; var g = function f(n) { return n ? f(n - 1) + 1 : 0; }; result = [g(3), f];`,
  switchLet: `var y = "outer"; switch (1) { case 1: let y = "inner"; var seen = y; } result = [y, seen];`,
  argumentsUse: `function f(a, b) { return arguments.length + a + b; } result = [f(1, 2)];`,
  classes: `class Box { constructor(v) { this.v = v; } get double() { return this.v * 2; } } var C = class Named { who() { return typeof Named; } }; result = [new Box(4).double, new C().who()];`,
  arrows: `const add = (p, q) => p + q; const twice = (fn) => (v) => fn(fn(v)); result = [add(2, 3), twice((n) => n * 3)(2)];`,
  methodsAndProps: `var o = { k: 1, m: function (v) { return this.k + v; } }; o["n"] = 5; result = [o.m(2), o.n, o.k];`,
  forLoops: `var s = 0; for (let i = 0; i < 4; i++) { s += i; } for (var j in { p: 1, q: 2 }) { s += j.length; } result = [s];`,
};

// Inside a function, as everything in a player is inside its wrapper.
const IIFE = (source) => `(function () { ${source} })();`;

for (const [name, body] of Object.entries(SCOPE_CASES)) {
  test(`M1 rename keeps behaviour: ${name}`, () => {
    const source = IIFE(body);
    const before = run(source);
    const after = mutated(source, M.renameLocals);
    assert.ok(after.edits > 0, "renamed at least one binding");
    assert.equal(run(after.text), before);
  });
}

test("M1 renames locals but never program-level globals", () => {
  const source = `var _yt_player = { v: 1 }; (function (g) { var inner = 2; result = [g.v, inner]; })(_yt_player);`;
  const after = mutated(source, M.renameLocals);
  assert.ok(after.edits >= 1);
  assert.match(after.text, /_yt_player/);
  assert.doesNotMatch(after.text, /\binner\b/);
  assert.doesNotMatch(after.text, /_m\$/);
  assert.equal(run(after.text), run(source));
});

test("M1 keeps the wrapper parameter; M1w renames it too", () => {
  const source = `var ns = { v: 3 }; (function (g) { var k = 2; result = [g.v * k]; })(ns);`;
  const kept = mutated(source, M.renameLocals);
  assert.match(kept.text, /function \(g\)/);
  const all = mutated(source, (p, r) => M.renameLocals(p, r, { keepWrapperParams: false }));
  assert.doesNotMatch(all.text, /function \(g\)/);
  assert.equal(run(kept.text), run(source));
  assert.equal(run(all.text), run(source));
});

test("M1 keeps a local deliberately named window", () => {
  const source = `var ns = {}; (function (g) { var window = { tag: "w" }; var k = window.tag; result = [k]; })(ns);`;
  const after = mutated(source, M.renameLocals);
  assert.match(after.text, /var window/);
  assert.equal(run(after.text), run(source));
});

test("M1 leaves globals and property names alone", () => {
  const source = IIFE(`var o = { length: 2 }; result = [o.length, Math.abs(-3), typeof undefined];`);
  const after = mutated(source, M.renameLocals);
  assert.match(after.text, /\.length/);
  assert.match(after.text, /Math\.abs/);
  assert.equal(run(after.text), run(source));
});

test("M1 refuses programs that use eval or with (names reached by text)", () => {
  for (const source of [
    IIFE(`var a = 1; result = [eval("a")];`),
    IIFE(`var o = { p: 1 }; with (o) { result = [p]; }`),
  ]) {
    assert.equal(mutated(source, M.renameLocals).edits, 0, source);
  }
});

test("M2 toggles dotted and computed access and keeps behaviour", () => {
  const source = `var o = { alpha: 1, beta: { gamma: 2 } }; o["delta"] = 3; result = [o.alpha, o.beta.gamma, o.delta, o["alpha"]];`;
  const after = mutated(source, M.toggleMemberAccess, "m2");
  assert.ok(after.edits > 0);
  assert.equal(run(after.text), run(source));
});

test("M2 never turns a reserved word or an invalid name into dotted access", () => {
  const source = `var o = {}; o["default"] = 1; o["a-b"] = 2; o["class"] = 3; result = [o["default"], o["a-b"], o["class"]];`;
  const after = mutated(source, M.toggleMemberAccess, "m2r");
  assert.doesNotMatch(after.text, /\.default\b|\.class\b|\.a-b/);
  assert.equal(run(after.text), run(source));
});

test("M3 re-wraps the top-level IIFE and keeps behaviour", () => {
  for (const source of [
    WRAPPED(`var t = 21; g.out = t * 2;`),
    `var ns = {}; (function () { this.out = 7; result = this.out; }).call(ns);`,
    `var ns = {}; ((g) => { g.out = typeof this; result = g.out; })(ns);`,
  ]) {
    const after = mutated(source, M.rewrapTopLevel);
    assert.equal(after.edits, 1, source);
    assert.match(after.text, /\.apply\(/);
    assert.equal(run(after.text), run(source));
  }
});

test("M3 does nothing to a program without a known wrapper", () => {
  const source = `var a = 1; var b = 2; result = [a + b];`;
  assert.equal(mutated(source, M.rewrapTopLevel).edits, 0);
});

test("M6 moves strings into a shuffled table and keeps behaviour", () => {
  const source = WRAPPED(`var parts = ["alpha", "bravo", "charlie", "delta", "echo"]; g.out = parts.join("-") + "/" + "foxtrot" + "golf".length;`);
  const after = mutated(source, M.tableLiterals, "m6");
  assert.ok(after.edits >= 4, `edits ${after.edits}`);
  assert.match(after.text, /_m\$tbl/);
  assert.equal(run(after.text), run(source));
});

test("M6 keeps a \"use strict\" directive a directive", () => {
  const source = `var ns = {}; (function (g) { "use strict"; var w = "alpha" + "bravo"; g.out = [w, (function () { return this === undefined; })()]; globalThis.result = g.out; })(ns);`;
  const after = mutated(source, M.tableLiterals, "m6d");
  assert.ok(after.edits > 0);
  assert.equal(run(after.text), run(source));
});

test("M6 leaves object keys alone", () => {
  const source = WRAPPED(`var o = { "keyone": "valone", "keytwo": "valtwo", "keythree": "valthree", "keyfour": "valfour", "keyfive": "valfive" }; g.out = Object.keys(o).join() + Object.values(o).join();`);
  const after = mutated(source, M.tableLiterals, "m6k");
  assert.equal(run(after.text), run(source));
});

test("M10 adds unreachable decoys and keeps behaviour", () => {
  const source = WRAPPED(`g.out = [1, 2, 3].map(function (v) { return v * 2; });`);
  const after = mutated(source, M.appendDecoys, "m10");
  assert.equal(after.edits, 3);
  assert.match(after.text, /_m\$dc/);
  assert.equal(run(after.text), run(source));
});

test("mutations are deterministic for one seed", () => {
  const source = WRAPPED(`var aa = 1, bb = 2; function cc(dd) { return dd + aa; } g.out = cc(bb);`);
  assert.equal(mutated(source, M.renameLocals, "same").text, mutated(source, M.renameLocals, "same").text);
});
