// Phase 1.2 N3: own solver core, strategy B (register, probe, classify, decide) on small players
// written for these tests (no YouTube code), in the worker-shaped context. One test per
// classifier rule: decoys, trivial transforms, a non-deterministic function, a looping function,
// plus an unknown wrapper, dispatcher keys derived inside a dispatcher, and a sig split over
// nested calls (a chain).
import assert from "node:assert/strict";
import test from "node:test";
import { expectedN, expectedSig, fakePlayer } from "./fake-player.mjs";
import { inside, padded, solve, solverContext } from "./solver-context.mjs";

const N = ["ZdZIqFPQK-Ty8wId", "abcDEF0123456789xyz"];
const SIG = ["AOq0QJ8wRAIgXmPlOPSBkkUs1bYFYlJCfe29xx8j7v1pDL0QwbdV96sCIEzpWqMGkFR20CFOg51Tp-7vj_EMu-m37KtXJoOySqa0" + "wWq="];
const B_ALONE = { strategy: "B", hint: false };

// The fake player's n scramble and sig steps (same results as expectedN / expectedSig).
const ALPHABET = 'var A="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";';
const HELPERS = "var K={sw:function(a,b){var c=a[0];a[0]=a[b%a.length];a[b%a.length]=c},rv:function(a){a.reverse()},sp:function(a,b){a.splice(0,b)}};";
const N_BODY = 'var b=T.split(""),c=A.length;for(var x=0;x<b.length;x++){var e=A.indexOf(b[x]);b[x]=e<0?b[x]:A[(e+x*7+3)%c]}b.reverse();return b.join("").slice(2)';
const NSCR = `var nscr=function(T){${N_BODY}};`;
const DSIG = 'var dsig=function(a){a=a.split("");K.sw(a,7);K.rv(a);K.sp(a,3);K.sw(a,21);return a.join("")};';
const FILLER = ["g.ready=!0;", "g.count=3;", "for(var i=0;i<3;i++)g[\"k\"+i]=i;", "if(typeof g.flag===\"undefined\"){g.flag=1}", "g.list=[1,2,3];", "g.name=\"t\";"];

/** A namespace-wrapped player made of [parts] (statements) plus the alphabet, helpers and filler. */
function bPlayer(parts, wrapper = "namespace") {
  const body = ["var window=this;", ALPHABET, HELPERS, ...parts, ...FILLER].join("\n");
  if (wrapper === "rewrapped") {
    // The M3 shape: the top-level call re-written as `void (fn).apply(receiver, [args])`.
    return padded(`var _yt_player={};void (function(g){\n${body}\n}).apply(this,[_yt_player]);\n`);
  }
  return padded(`var _yt_player={};(function(g){\n${body}\n})(_yt_player);\n`);
}

function expectAll(output, n, sig) {
  for (const input of n) assert.equal(output.n[input], expectedN(input), `n ${input}`);
  for (const input of sig) assert.equal(output.sig[input], expectedSig(input), `sig ${input}`);
}

test("B alone gets n and sig from the fake player in every wrapper P knows", () => {
  for (const options of [{}, { style: "es6" }, { style: "seq" }, { wrapper: "call" }, { wrapper: "arrow" }]) {
    const output = solve({ player: padded(fakePlayer(options)), n: N, sig: SIG, ...B_ALONE });
    assert.equal(output.failed, undefined, JSON.stringify(options));
    expectAll(output, N, SIG);
    assert.equal(output.report.strategy, "B");
    assert.equal(output.report.n.hint, false);
    assert.equal(output.report.n.groups, 1);
    assert.equal(output.report.sig.groups, 1);
  }
});

test("an unknown wrapper (the M3 re-wrap) fails P safely, B alone still answers", () => {
  const player = bPlayer([NSCR, DSIG], "rewrapped");
  const p = solve({ player, n: N, sig: SIG });
  assert.ok(p.failed, "P has no answer on a wrapper it does not know");
  assert.deepEqual(p.n, {});
  const b = solve({ player, n: N, sig: SIG, ...B_ALONE });
  assert.equal(b.failed, undefined);
  expectAll(b, N, SIG);
});

test("n decoys are dropped by class: a fixed-length hash and a hex encoding", () => {
  const hash =
    'var hsh=function(a){for(var h=7,i=0;i<a.length;i++)h=(h*31+a.charCodeAt(i))|0;return ("0000000"+(h>>>0).toString(36)).slice(-7)+("0000000"+(h>>>5).toString(36)).slice(-7)};';
  const hex = 'var hex=function(a){return a.split("").map(function(c){return c.charCodeAt(0).toString(16)}).join("")};';
  const output = solve({ player: bPlayer([NSCR, DSIG, hash, hex]), n: N, sig: SIG, ...B_ALONE, debug: true });
  assert.equal(output.failed, undefined);
  expectAll(output, N, SIG);
  assert.equal(output.report.n.groups, 1);
  assert.equal(output.debug.n.length, 1);
  assert.equal(output.debug.n[0].members[0][1], "nscr");
});

test("a second n-like transform that passes every rule makes n fail (disagree), never a guess", () => {
  const other = `var nsc2=function(T){${N_BODY.replace("x*7+3", "x*5+11")}};`;
  const output = solve({ player: bPlayer([NSCR, DSIG, other]), n: N, sig: SIG, ...B_ALONE });
  assert.match(output.failed ?? "", /n:disagree/);
  assert.deepEqual(output.n, {});
  expectAll(output, [], SIG);
});

test("trivial transforms are never sig: identity, reverse, rotation, a cut and one swap", () => {
  const context = solverContext();
  const classes = inside(context, () => {
    const t = yftOwnSolver.internals.bTrivialMap;
    const probe = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    const swapped = probe.split("");
    const c = swapped[0];
    swapped[0] = swapped[9];
    swapped[9] = c;
    const real = probe.split("");
    real.reverse();
    real.splice(0, 3);
    const d = real[0];
    real[0] = real[21];
    real[21] = d;
    return [
      t(probe, probe),
      t(probe, probe.split("").reverse().join("")),
      t(probe, probe.slice(5) + probe.slice(0, 5)),
      t(probe, probe.slice(2)),
      t(probe, swapped.join("")),
      t(probe, real.join("")),
    ];
  });
  assert.deepEqual(classes, ["identity", "reverse", "rotation", "cut", "swap", false]);
  // A player whose only sig-like functions are one reverse and one swap: no sig candidate.
  const reverse = 'var rsig=function(a){return a.split("").reverse().join("")};';
  const swap = 'var ssig=function(a){a=a.split("");K.sw(a,7);return a.join("")};';
  const output = solve({ player: bPlayer([NSCR, reverse, swap]), n: N, sig: SIG, ...B_ALONE });
  assert.match(output.failed ?? "", /sig:no candidates/);
  assert.deepEqual(output.sig, {});
  expectAll(output, N, []);
});

test("a non-deterministic n-like function is dropped (screening or fresh-instance confirm)", () => {
  const drifting = `var ndr=function(T){g.calls=(g.calls||0)+1;var o=nscr(T);return o.slice(0,-1)+A[g.calls%64]};`;
  const output = solve({ player: bPlayer([NSCR, DSIG, drifting]), n: N, sig: SIG, ...B_ALONE });
  assert.equal(output.failed, undefined);
  expectAll(output, N, SIG);
  assert.equal(output.report.n.groups, 1);
});

test("a looping function is stopped by the step limit and the rest still answers", () => {
  const loops = ["var spin=function(a){for(;;){}};", "var spin2=function(a){var i=0;while(a){i++}return i};"];
  const output = solve({ player: bPlayer([NSCR, DSIG, ...loops]), n: N, sig: SIG, ...B_ALONE });
  assert.equal(output.failed, undefined);
  expectAll(output, N, SIG);
});

test("dispatcher keys derived inside a dispatcher are propagated statically", () => {
  // n lives only in a dispatcher branch; the only literal call site passes keys to `outer`,
  // which derives the keys it passes on (v = w ^ F, then v ^ 72 and v ^ 47 -> dsp(5, 98, x)).
  const parts = [
    `var dsp=function(p,m,T){var d=m^p;if(p===5&&d===103){${N_BODY}}return T+"!"};`,
    "var outer=function(F,w,a){var v=w^F;return dsp(v^72,v^47,a)};",
    // A local the body changes is never a constant key: this call must keep its hole.
    "var other=function(F,w,a){var v=w^F,k=0;k=a;return dsp(v^72,v^47,k)};",
    "g.go=function(x){return outer(1,76,x)+other(1,76,x)};",
    DSIG,
  ];
  const player = bPlayer(parts);
  const context = solverContext();
  const templates = inside(
    context,
    (text) => {
      const prepared = yftOwnSolver.prepareB(text);
      return prepared.templates.dsp;
    },
    player,
  );
  assert.ok(templates.some((t) => t.derived && t.hole === 2 && t.args[0] === 5 && t.args[1] === 98), JSON.stringify(templates));
  const output = solve({ player, n: N, sig: SIG, ...B_ALONE });
  assert.equal(output.failed, undefined);
  expectAll(output, N, SIG);
});

test("a sig split over nested dispatcher calls is probed as one chain", () => {
  // Each call alone is one primitive (a swap, a reverse with a cut); only the chain is sig.
  const parts = [
    NSCR,
    'var st=function(p,m,T){var d=m^p,a=T.split("");if(d===1)K.sw(a,7);if(d===2){K.rv(a);K.sp(a,3)}if(d===3)K.sw(a,21);return a.join("")};',
    "g.sg=function(s){var C=st(9,11,st(5,4,s));return st(16,19,C)};",
  ];
  const output = solve({ player: bPlayer(parts), n: N, sig: SIG, ...B_ALONE });
  assert.equal(output.failed, undefined);
  expectAll(output, N, SIG);
});

test("a one-swap sig counts only with builder evidence, also when the builder calls it through an alias (M5)", () => {
  // One player's real sig nets out to a single swap (weak tier): kept only because the stream-URL
  // builder names the function. M5 makes the builder call it through `var dali = dsig`.
  const swapSig = (input) => {
    const list = input.split("");
    const first = list[0];
    list[0] = list[7 % list.length];
    list[7 % list.length] = first;
    return list.join("");
  };
  const oneSwap = 'var dsig=function(a){a=a[T[6]]("");K.sw(a,7);return a[T[7]]("")};';
  const plain = fakePlayer().replace(/var dsig=function\(a\)\{[^}]*\};/, oneSwap);
  assert.notEqual(plain, fakePlayer(), "the sig steps were replaced");
  const aliased = plain.replace(oneSwap, `${oneSwap}var dali=dsig;`).replace("c=dsig(dec(c))", "c=dali(dec(c))");
  assert.notEqual(aliased, plain, "the builder calls the alias");
  for (const player of [plain, aliased]) {
    const output = solve({ player: padded(player), n: N, sig: SIG, ...B_ALONE });
    assert.equal(output.failed, undefined);
    for (const input of SIG) assert.equal(output.sig[input], swapSig(input));
    assert.equal(output.report.sig.weak, true);
    expectAll(output, N, []);
  }
  // Without the builder naming it (no call from the builder), the same swap is never sig.
  const unnamed = plain.replace("c=dsig(dec(c))", "c=dec(c)");
  const output = solve({ player: padded(unnamed), n: N, sig: SIG, ...B_ALONE });
  assert.deepEqual(output.sig, {});
  assert.match(output.failed ?? "", /sig:/);
});

test("B's report and failures name counts and classes only, never values", () => {
  const other = `var nsc2=function(T){${N_BODY.replace("x*7+3", "x*5+11")}};`;
  const output = solve({ player: bPlayer([NSCR, DSIG, other]), n: N, sig: SIG, ...B_ALONE });
  const report = JSON.stringify(output.report) + String(output.failed ?? "");
  for (const value of [...N, ...SIG, ...N.map(expectedN), ...SIG.map(expectedSig)]) {
    assert.ok(!report.includes(value));
  }
});
