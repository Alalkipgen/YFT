#!/usr/bin/env node
// Phase 1.2 N1 (MASTER_KEY_PHASE1_2_PLAN.md): the change simulator.
//
//   node scripts/verify-own-solver-mutations.mjs            # every cached file, every class
//   node scripts/verify-own-solver-mutations.mjs --quick    # the phone build of each player, every class, 1 seed
//   node scripts/verify-own-solver-mutations.mjs --only 5203c085
//   node scripts/verify-own-solver-mutations.mjs --only 5203c085 --variant es6   # one file
//   node scripts/verify-own-solver-mutations.mjs --class M1,M3
//   node scripts/verify-own-solver-mutations.mjs --seeds 3
//   node scripts/verify-own-solver-mutations.mjs --strategy P,B   # also strategy B alone (N3)
//   node scripts/verify-own-solver-mutations.mjs --strategy B     # ejs and B alone only
//   node scripts/verify-own-solver-mutations.mjs --class M9 --strategy P,B --page
//                    # strategy E (N4): own P/B in page mode in the harness's page-frame DOM stub
//   node scripts/verify-own-solver-mutations.mjs --class M9b,M9bs --strategy P,B --both
//                    # N4b: own P/B in the worker AND in page mode, one row per class
//                    # (M9b/M9bs are environment classes, not in the default list; see mutate.mjs)
//   node scripts/verify-own-solver-mutations.mjs --class M9b,M9bs --strategy P,B --ladder
//                    # N5a: as --both, plus the ladder's answer (ladder.mjs) from those four runs:
//                    # page step "PB" (page P, else page B) or "B" (page B only), each with the
//                    # live check simulated (+V: a page signature counts only if it equals the
//                    # truth) or off (no page signature)
//   ... --ladder --phases   # N5b A.1: also wall ms per step on every line, phase times per step
//                           # and the lookup's time at the end (milliseconds and counts only)
//   ... --ladder --no-deadline   # N5b A.2: --ladder sends the app's step caps as deadlines
//                                # (worker 40 s, page 35 s); this drops them
//   ... --ladder --judge ladder  # N5b A.3: exit 1 only on a ladder wrong value (ejs's and each
//                                # step's own wrong values are still counted and printed);
//                                # default --judge all: any wrong value fails the run
//   ... --ladder --no-locator    # N5b A.4: --ladder's worker steps run in locate mode (locator
//                                # {}, as the app's jobs on a cache miss); this drops it
//
// It mutates each cached player so the code is WRITTEN differently but DOES the same thing, then
// measures how often ejs and the own solver's pattern strategy (P) — and, with --strategy B,
// strategy B alone (N3) — still return the right n and sig, and that none ever returns a wrong
// one (MASTER_KEY_PHASE1_2_PLAN.md section 3 N1).
//
// Truth without executing player code ourselves: the correct n/sig for an input is a property of
// the input, not of how the player is written, so a behaviour-preserving mutation has the same
// answers as the original. Truth = ejs on the ORIGINAL player (the proven oracle the parity runner
// already uses). Every solve goes through scripts/own-solver/harness.mjs, which runs ejs and the
// own core in a bare V8 context with no network, cookies or require — the same sandbox as
// verify-own-solver.mjs. This script never runs player code in any other way.
//
// Mutator-bug guard: a mutation that accidentally changed behaviour would make ejs and the own core
// agree on a NEW value that differs from truth; such a file is marked "behaviour-changed" and
// skipped, never counted as a wrong value. meriyah + astring come from main's yt.solver.lib.min.js
// and are used only here. Player text and mutated text are never written to the repo; the report
// (build/own-solver/mutation-report.json) names classes and counts only.
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import vm from "node:vm";
import { challenges, settle, solveWithEjs, solveWithOwn, VARIANTS } from "./own-solver/harness.mjs";
import { JUDGES, STEP_CAPS_MS, judgeExit, ladder } from "./own-solver/ladder.mjs";
import { addLadder, lookupTable, phaseTable } from "./own-solver/phases.mjs";
import * as M from "./own-solver/mutate.mjs";

// YFT_STAGE=1 (local diagnosis): the stage and heap before each step on stderr (names and sizes only).
const stage = process.env.YFT_STAGE === "1" ? (name) => process.stderr.write(`stage ${name} heap ${Math.round(process.memoryUsage().heapUsed / 1048576)} MB\n`) : () => {};
const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const corpus = JSON.parse(readFileSync(join(root, "scripts/own-solver/corpus.json"), "utf8"));
const vectors = JSON.parse(readFileSync(join(root, "scripts/youtube-solver-vectors.json"), "utf8"));
const playersDir = join(root, "build/own-solver/players");
const ejsDir = join(root, "app/src/main/assets/youtube-solver");
const ownDir = join(root, "extractor-master-android/src/main/assets/yft-own-solver");
const reportFile = join(root, "build/own-solver/mutation-report.json");

const libText = readFileSync(join(ejsDir, "yt.solver.lib.min.js"), "utf8");
const ejs = { lib: libText, core: readFileSync(join(ejsDir, "yt.solver.core.min.js"), "utf8") };
const own = { lib: readFileSync(join(ownDir, "meriyah.umd.min.js"), "utf8"), core: readFileSync(join(ownDir, "own.solver.core.js"), "utf8") };

// ---- CLI ----
const argv = process.argv.slice(2);
const flag = (name) => argv.includes(name);
const value = (name, fallback) => { const i = argv.indexOf(name); return i >= 0 && i + 1 < argv.length ? argv[i + 1] : fallback; };
const quick = flag("--quick");
const only = value("--only", null);
const onlyVariant = value("--variant", null);
const classes = (value("--class", M.CLASSES.join(","))).split(",").filter(Boolean);
const seeds = Number(value("--seeds", quick ? "1" : "2"));
// Own strategies to judge: P (the pattern strategy, default) and/or B alone (no hint from P).
const strategies = value("--strategy", "P").split(",").filter(Boolean);
if (!strategies.length || strategies.some((x) => x !== "P" && x !== "B")) {
  console.error("--strategy must be P, B or P,B");
  process.exit(2);
}
const withP = strategies.includes("P");
const withB = strategies.includes("B");
// --page (N4, strategy E): the own solves run in the page-frame DOM stub with the core in page
// mode; ejs and the truth stay as they are (bare context).
const page = flag("--page");
const pageExtra = page ? { page: true } : {};
// --ladder (N5a): the ladder's answer from the four runs of --both (needs --strategy P,B).
const withLadder = flag("--ladder");
if (withLadder && !(withP && withB)) {
  console.error("--ladder needs --strategy P,B");
  process.exit(2);
}
// --judge (N5b A.3): what the exit code judges, "all" (default) or "ladder" (with --ladder).
const judgeBy = value("--judge", "all");
if (!JUDGES.includes(judgeBy)) {
  console.error("--judge must be all or ladder");
  process.exit(2);
}
if (judgeBy === "ladder" && !withLadder) {
  console.error("--judge ladder runs with --ladder");
  process.exit(2);
}
// --both (N4b): each own strategy in the worker (bare context) and in page mode, side by side.
const both = flag("--both") || withLadder;
// N5b: --ladder sends the environment tripwire as the app's ladder does (--no-tripwire: N5a's runs).
const skipEjs = flag("--skip-ejs");
const tripExtra = withLadder && !flag("--no-tripwire") ? { tripwire: true } : {};
// N5b A.2: --ladder gives each step the app's step cap as its deadline (--no-deadline: A.1's runs).
const capExtra = (step) => (withLadder && !flag("--no-deadline") ? { deadline: STEP_CAPS_MS[step] } : {});
// N5b A.4: --ladder's worker steps locate as the app's do on a cache miss (--no-locator: A.3's runs).
const locateExtra = withLadder && !flag("--no-locator") ? { locator: {} } : {};
if (flag("--no-locator") && !withLadder) {
  console.error("--no-locator runs with --ladder");
  process.exit(2);
}
if (both && page) {
  console.error("--both and --ladder already run page mode; drop --page");
  process.exit(2);
}
// The ladder reads P's init errors from its report (B and page B always return it).
const reportExtra = withLadder ? { report: true } : {};
// --phases (N5b A.1, with --ladder): the phase table (phases.mjs) and the lookup's time.
const withPhases = flag("--phases");
if (withPhases && !withLadder) {
  console.error("--phases runs with --ladder");
  process.exit(2);
}
const phases = phaseTable();
const lookup = lookupTable();
const timed = (walls, step, solve) => {
  const started = performance.now();
  const out = solve();
  walls[step] = performance.now() - started;
  return out;
};
const LADDERS = [
  ["ladder PB+V", "ladderPBV", { page: "PB", live: "sim" }],
  ["ladder PB off", "ladderPBoff", { page: "PB", live: "offline" }],
  ["ladder B+V", "ladderBV", { page: "B", live: "sim" }],
  ["ladder B off", "ladderBoff", { page: "B", live: "offline" }],
];
const quickVariant = "phone";

// ---- a V8 context with the vendored parser + code generator (used only to mutate the AST) ----
function codegenContext() {
  const ctx = vm.createContext({});
  vm.runInContext(libText, ctx);
  vm.runInContext("var meriyah = lib.meriyah, astring = lib.astring;", ctx);
  return ctx;
}
const cg = codegenContext();
const parse = (text) => vm.runInContext("meriyah.parse", cg)(text, { ranges: false, webcompat: true, loc: false });
const generate = (ast) => vm.runInContext("astring.generate", cg)(ast);
M.setStatementParser((src) => parse(src).body);

// ---- mutate one AST by class; returns a count of edits (0 = nothing changed) ----
function mutate(program, cls, random) {
  switch (cls) {
    case "M1": return M.renameLocals(program, random);
    case "M1w": return M.renameLocals(program, random, { keepWrapperParams: false });
    case "M2": return M.toggleMemberAccess(program, random);
    case "M3": return M.rewrapTopLevel(program);
    case "M4": return M.splitHelperObjects(program, random);
    case "M5": return M.aliasCallSites(program, random);
    case "M6": return M.tableLiterals(program, random);
    case "M7": return M.flattenFunctions(program, random);
    case "M8": return M.wrapEncoding(program, random);
    case "M8t": return M.wrapEncoding(program, random, { atTop: true });
    case "M9": return M.readBrowserObjects(program, random);
    case "M9b": return M.domDependentLiterals(program, random);
    case "M9bs": return M.domDependentLiterals(program, random, { silent: true });
    case "M9c-nav": return M.environmentLiterals(program, random, { env: "nav" });
    case "M9c-loc": return M.environmentLiterals(program, random, { env: "loc" });
    case "M9c-mix": return M.environmentLiterals(program, random, { env: "mix" });
    case "M10": return M.appendDecoys(program, random);
    case "M11": return M.addPrelude(program, random);
    case "M11f": return M.addPrelude(program, random, { functions: true });
    default: return 0;
  }
}

// ---- compare a strategy's output to the truth: per kind, right / wrong / missing ----
function judge(truth, got, inputs) {
  const out = {};
  for (const kind of ["n", "sig"]) {
    let total = 0, right = 0, wrong = 0, missing = 0;
    for (const input of inputs[kind]) {
      const expected = truth[kind] ? truth[kind][input] : undefined;
      if (typeof expected !== "string") continue; // the oracle gave no value here; do not judge it
      total += 1;
      const value = got[kind] ? got[kind][input] : undefined;
      if (typeof value !== "string") missing += 1;
      else if (value === expected) right += 1;
      else wrong += 1;
    }
    out[kind] = { total, right, wrong, missing };
  }
  return out;
}
function score(v) {
  const wrong = v.n.wrong > 0 || v.sig.wrong > 0;
  const survived = v.n.total > 0 && v.sig.total > 0 && v.n.right === v.n.total && v.sig.right === v.sig.total;
  return wrong ? "wrong" : survived ? "survived" : "safe";
}
// "safe (n 0/12, sig 12/12)": which kind failed, for the report lines (right / judged inputs).
const detail = (v, s) => (s === "survived" ? s : `${s} (n ${v.n.right}/${v.n.total}, sig ${v.sig.right}/${v.sig.total})`);
// A mutation changed behaviour when ejs and own agree on a value that differs from truth.
function behaviourChanged(truth, ejsOut, ownOut, inputs) {
  let agreedDifferent = 0, judged = 0;
  for (const kind of ["n", "sig"]) {
    for (const input of inputs[kind]) {
      const t = truth[kind] ? truth[kind][input] : undefined;
      if (typeof t !== "string") continue;
      judged += 1;
      const e = ejsOut[kind] ? ejsOut[kind][input] : undefined;
      const o = ownOut[kind] ? ownOut[kind][input] : undefined;
      if (typeof e === "string" && e === o && e !== t) agreedDifferent += 1;
    }
  }
  return judged > 0 && agreedDifferent > 0;
}

// ---- files ----
const files = [];
for (const entry of corpus.players) {
  if (only && entry.player !== only) continue;
  for (const variant of Object.keys(VARIANTS)) {
    if (!entry.files || !entry.files[variant]) continue;
    if (quick && variant !== quickVariant) continue;
    if (onlyVariant && variant !== onlyVariant) continue;
    const path = join(playersDir, `${entry.player}-${variant}.js`);
    if (existsSync(path)) files.push({ player: entry.player, variant, path });
  }
}
if (!files.length) {
  console.error("No cached players. Run: node scripts/verify-own-solver.mjs --add <id>  (downloads the cache).");
  process.exit(2);
}

const empty = () => ({
  files: 0,
  skipped: 0,
  ejs: { survived: 0, safe: 0, wrong: 0 },
  ownP: { survived: 0, safe: 0, wrong: 0 },
  ownB: { survived: 0, safe: 0, wrong: 0 },
  pageP: { survived: 0, safe: 0, wrong: 0 },
  pageB: { survived: 0, safe: 0, wrong: 0 },
  ...(withLadder ? Object.fromEntries(LADDERS.map(([, key]) => [key, { survived: 0, safe: 0, wrong: 0 }])) : {}),
});
const byClass = {};
for (const cls of classes) byClass[cls] = empty();
const lines = [];
let wrongTotal = 0;
let ladderWrong = 0;

for (const file of files) {
  const playerText = readFileSync(file.path, "utf8");
  const { inputs } = challenges(file.player, vectors);
  const truth = solveWithEjs(ejs, playerText, inputs); // the proven oracle, on the original player
  await settle();
  const haveTruth = ["n", "sig"].some((k) => Object.keys(truth[k] || {}).length > 0);
  for (const cls of classes) {
    const bucket = byClass[cls];
    bucket.files += 1;
    let done = false, note = "";
    for (let seed = 0; seed < seeds && !done; seed += 1) {
      const random = M.rng(`${file.player}:${file.variant}:${cls}:${seed}`);
      let mutated;
      try {
        stage("mutate");
        const program = parse(playerText);
        const edits = mutate(program, cls, random);
        if (!edits) { note = "no-op"; continue; }
        mutated = generate(program);
      } catch (error) {
        note = "mutate:" + (error && error.name);
        continue;
      }
      if (!haveTruth) { note = "no-truth"; continue; }
      stage("ejs");
      // --skip-ejs (N5b): main's ejs runs out of heap on 631d3938 main M9c-nav before the own
      // solver runs; skipped, its column counts the line as safe (no value).
      const ejsOut = skipEjs ? { n: {}, sig: {}, error: "Skipped" } : solveWithEjs(ejs, mutated, inputs);
      stage("own");
      await settle(); // releases the player's unhandled rejections after every solve (see harness.mjs)
      const walls = {};
      stage("own P");
      const ownOut = withP ? timed(walls, "workerP", () => solveWithOwn(own, mutated, inputs, { ...pageExtra, ...reportExtra, ...tripExtra, ...capExtra("worker"), ...locateExtra })) : null;
      await settle();
      stage("own B");
      const bOut = withB ? timed(walls, "workerB", () => solveWithOwn(own, mutated, inputs, { strategy: "B", hint: false, ...pageExtra, ...tripExtra, ...capExtra("worker"), ...locateExtra })) : null;
      await settle();
      stage("page P");
      const pagePOut = both && withP ? timed(walls, "pageP", () => solveWithOwn(own, mutated, inputs, { page: true, ...reportExtra, ...capExtra("page") })) : null;
      if (both) await settle();
      stage("page B");
      const pageBOut = both && withB ? timed(walls, "pageB", () => solveWithOwn(own, mutated, inputs, { strategy: "B", hint: false, page: true, ...capExtra("page") })) : null;
      if (both) await settle();
      // Environment classes (M9b/M9bs) are written so that an environment without the DOM gives
      // other values: ejs and a worker agreeing on one is the finding, not a mutator bug. Their
      // behaviour is proven where the DOM exists (page columns), so the guard is not used.
      if (!M.ENV_CLASSES.includes(cls) && behaviourChanged(truth, ejsOut, ownOut ?? bOut, inputs)) { note = "behaviour-changed"; continue; }
      const ev = judge(truth, ejsOut, inputs);
      const e = score(ev);
      bucket.ejs[e] += 1;
      if (e === "wrong") wrongTotal += 1;
      let line = `${file.player} ${file.variant} ${cls}: ejs ${detail(ev, e)}`;
      for (const [label, out, key] of [["own P", ownOut, "ownP"], ["own B", bOut, "ownB"], ["page P", pagePOut, "pageP"], ["page B", pageBOut, "pageB"]]) {
        if (!out) continue;
        const v = judge(truth, out, inputs);
        const o = score(v);
        bucket[key][o] += 1;
        if (o === "wrong") wrongTotal += 1;
        line += `, ${label} ${detail(v, o)}${o !== "survived" && (out.failed || out.error) ? ` [${out.failed || out.error}]` : ""}${out.late ? " {late promise jobs cut}" : ""}`;
        if (withLadder && out.report && typeof out.report.topLevelErrors === "number" && out.report.topLevelErrors > 0) line += ` {init errors ${out.report.topLevelErrors}}`;
      }
      if (withLadder) {
        const outs = { workerP: ownOut, workerB: bOut, pageP: pagePOut, pageB: pageBOut };
        for (const [label, key, options] of LADDERS) {
          const answer = ladder(outs, inputs, { ...options, truth });
          const v = judge(truth, answer, inputs);
          const o = score(v);
          bucket[key][o] += 1;
          if (o === "wrong") ladderWrong += 1;
          line += `, ${label} ${detail(v, o)} (n ${answer.sources.n ?? "-"}, sig ${answer.sources.sig ?? "-"})`;
        }
        if (withPhases) {
          const name = `${file.player} ${file.variant} ${cls}`;
          line += ` | ms ${addLadder(phases, name, outs, walls)}`;
          lookup.add(name, walls);
        }
      }
      lines.push(line);
      process.stderr.write(`${line}\n`); // progress for long runs (counts and classes only)
      done = true;
    }
    if (!done) { bucket.skipped += 1; lines.push(`${file.player} ${file.variant} ${cls}: skipped (${note})`); }
  }
}

const summary = { generated: new Date().toISOString().slice(0, 10), files: files.length, seeds, classes: {} };
lines.push("");
lines.push(
  `class   | ejs surv/safe/wrong |${withP ? " own P surv/safe/wrong |" : ""}${withB ? " own B surv/safe/wrong |" : ""}` +
    `${both && withP ? " page P surv/safe/wrong |" : ""}${both && withB ? " page B surv/safe/wrong |" : ""}` +
    `${withLadder ? LADDERS.map(([label]) => ` ${label.padEnd(13)} s/s/w |`).join("") : ""} skipped`,
);
for (const cls of classes) {
  const b = byClass[cls];
  summary.classes[cls] = b;
  const f = (x) => String(x).padStart(2);
  const cell = (c) => `    ${f(c.survived)}/${f(c.safe)}/${f(c.wrong)}          |`;
  const pageCells = `${both && withP ? ` ${cell(b.pageP)}` : ""}${both && withB ? ` ${cell(b.pageB)}` : ""}`;
  const ladderCells = withLadder ? LADDERS.map(([, key]) => `       ${f(b[key].survived)}/${f(b[key].safe)}/${f(b[key].wrong)}     |`).join("") : "";
  lines.push(`${cls.padEnd(7)} |   ${f(b.ejs.survived)}/${f(b.ejs.safe)}/${f(b.ejs.wrong)}         |${withP ? cell(b.ownP) : ""}${withB ? cell(b.ownB) : ""}${pageCells}${ladderCells} ${b.skipped}`);
}
lines.push("");
lines.push(`${files.length} files x ${classes.length} classes x ${seeds} seed(s)${page ? " (own in page mode)" : both ? " (own in the worker and in page mode)" : ""}; wrong values: ${wrongTotal}${withLadder ? `; ladder wrong values: ${ladderWrong}` : ""}; judged by ${judgeBy === "ladder" ? "the ladder (other wrong values reported only)" : "every wrong value"}`);
if (withPhases) lines.push(...phases.lines(), lookup.line());
console.log(lines.join("\n"));
mkdirSync(dirname(reportFile), { recursive: true });
// Page-mode runs keep their own report, so a worker-mode run can go at the same time.
const outFile = page
  ? reportFile.replace(/\.json$/, "-page.json")
  : withLadder
    ? reportFile.replace(/\.json$/, "-ladder.json")
    : both ? reportFile.replace(/\.json$/, "-both.json") : reportFile;
const phaseReport = withPhases ? { phases: phases.rows(), lookup: lookup.largest() } : {};
writeFileSync(outFile, JSON.stringify({ ...summary, strategies, page, both, ladder: withLadder, judge: judgeBy, wrongTotal, ...(withLadder ? { ladderWrong } : {}), ...phaseReport }, null, 2));
process.exit(judgeExit(judgeBy, { wrong: wrongTotal, ladderWrong }));
