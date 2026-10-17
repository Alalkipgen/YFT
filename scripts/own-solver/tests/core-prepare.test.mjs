// Phase 1.1 S2: own solver core — parse, G1 unwrap, G2 normalisation and candidates (offline,
// on a fake player written for these tests; no YouTube code).
import assert from "node:assert/strict";
import test from "node:test";
import { fakePlayer } from "./fake-player.mjs";
import { inside, padded, solverContext } from "./solver-context.mjs";

function prepare(source) {
  return inside(solverContext(), (text) => {
    const prepared = yftOwnSolver.prepare(text);
    return { ok: prepared.ok, failed: prepared.failed, info: prepared.info, program: prepared.program };
  }, source);
}

test("G1 unwrap accepts the known wrappers", () => {
  for (const wrapper of ["namespace", "call", "arrow"]) {
    const prepared = prepare(padded(fakePlayer({ wrapper })));
    assert.equal(prepared.ok, true, wrapper);
    assert.equal(prepared.info.candidates, 1, wrapper);
  }
});

test("G1 unwrap fails cleanly on an unknown structure, never guesses", () => {
  for (const wrapper of ["define", "three"]) {
    assert.deepEqual(prepare(padded(fakePlayer({ wrapper }))).failed, "unexpected structure", wrapper);
  }
  assert.equal(prepare(padded("var a = 1;")).failed, "unexpected structure");
  assert.equal(prepare(padded("(function(){")).failed, "parse");
  assert.equal(prepare("").failed, "no player");
});

test("G2 candidates come from definitions and from call sites, in every code style", () => {
  for (const style of ["es5", "es6", "seq"]) {
    const { info } = prepare(padded(fakePlayer({ style })));
    assert.equal(info.fromDefinitions, 1, style);
    assert.equal(info.fromCallSites, 1, style);
    assert.equal(info.candidates, 1, style);
  }
  const renamed = prepare(padded(fakePlayer({ setter: "Ax", getter: "By" })));
  assert.equal(renamed.info.candidates, 1);
});

test("G2 collects every match, not the first one", () => {
  const { info } = prepare(padded(fakePlayer({ decoy: true, throwing: true })));
  assert.equal(info.fromDefinitions, 3);
  assert.equal(info.candidates, 3);
});

test("no marked builder and no call site is a clean FAIL", () => {
  assert.equal(prepare(padded(fakePlayer({ unmarked: true }))).failed, "no candidates");
});

test("G2 normalisation: dotted, computed and string-table member names match alike", () => {
  const names = inside(solverContext(), (source) => {
    const internals = yftOwnSolver.internals;
    const body = meriyah.parse(source, { ranges: true }).body;
    const tables = internals.stringTables(body);
    const members = body.slice(2).map((statement) => internals.propertyName(statement.expression, tables, null));
    return { tables: Object.keys(tables), members };
  }, 'var T = "alr;set;s".split(";"); T2 = ["a","b","c","d","e","f","g","h"]; x.set; x["set"]; x[T[1]]; x[T[9]]; x[y]; x[`set`]; x["se" + "t"]; x[T2[7]];');
  assert.deepEqual(names.tables, ["T", "T2"]);
  assert.deepEqual(names.members, ["set", "set", "set", null, null, "set", "set", "h"]);
});

test("G2 normalisation: a table name the function binds itself is not resolved", () => {
  const values = inside(solverContext(), (source) => {
    const internals = yftOwnSolver.internals;
    const body = meriyah.parse(source, { ranges: true }).body;
    const tables = internals.stringTables(body);
    const definitions = internals.definitions(body, source);
    return internals.builderDefinitions(definitions, tables);
  }, 'var T = "alr;yes".split(";"); var f = function (a, T) { a = new U(a); a.set(T[0], T[1]); return a }; var h = function (a) { a = new U(a); a.set(T[0], T[1]); return a };');
  assert.deepEqual(values, ["h"]);
});

test("G2 definitions: function, var, let/const, arrow and member forms, also in sequences", () => {
  const refs = inside(solverContext(), (source) => {
    const body = meriyah.parse(source, { ranges: true }).body;
    return yftOwnSolver.internals.definitions(body, source).map((definition) => definition.ref);
  }, 'function a(){} var b = function(){}, c = () => {}; let d = function(){}; const e = () => 1; g.f = function(){}; g["h"] = function(){}; k = function(){}, l.m = () => {}; n = o = function(){}; p[q()] = function(){};');
  assert.deepEqual(refs, ["a", "b", "c", "d", "e", "g.f", 'g["h"]', "k", "l.m", "o"]);
});

test("G2 flat steps split sequences (also in returns), declarators and arrow bodies", () => {
  const steps = inside(solverContext(), (source) => {
    const fn = meriyah.parse(source, { ranges: true }).body[0].expression;
    return yftOwnSolver.internals.flatSteps(fn).map((step) => (step.ret ? "ret:" : "expr:") + (step.ret || step.expr).type);
  }, "(function (a) { a = 1, b(); var c = 2; if (a) d(); return e(), f })");
  assert.deepEqual(steps, ["expr:AssignmentExpression", "expr:CallExpression", "expr:Literal", "expr:CallExpression", "ret:Identifier"]);
});

test("G1 program keeps declarations, assignments and control statements and drops side effects", () => {
  const { program, info } = prepare(padded(fakePlayer()));
  assert.equal(info.dropped, 2);
  assert.ok(program.includes("g.Url.prototype.set=function"));
  assert.ok(program.includes("if(typeof g.flag"));
  assert.ok(!program.includes("g.boot("));
  assert.ok(!program.includes('new g.Url("https://example.invalid/")'));
  // Every var declarator and assignment runs in its own try, so one failure cannot stop the rest.
  assert.ok(program.includes("try{var dsig=function"));
  assert.ok(program.includes("try{g.ready=!0;"));
  assert.ok(program.includes("try{g.count=3;"));
  // The candidates are read at the end of the top level, inside the player's own scope.
  assert.match(program, /try\{__yftOwnRegistry\$\.c\[0\]=zW\}catch/);
  assert.ok(program.startsWith("var _yt_player={};(function(g){"));
});
