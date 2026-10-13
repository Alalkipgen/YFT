#!/usr/bin/env node
// Phase 1.1 S6 (MASTER_KEY_PHASE1_1_PLAN.md §3 S6, runbook §6) and Phase 1.2 N6
// (MASTER_KEY_PHASE1_2_PLAN.md §3 N6): own-solver canary.
//
//   node scripts/own-solver-canary.mjs              # today's player (from YouTube's embed API)
//   node scripts/own-solver-canary.mjs <player ID>  # a given player
//
// Downloads every build of the player (main, tce, es6, phone), runs main's bundled ejs solver and
// the own core's worker strategies P (patterns) and B alone (behaviour discovery, N3), as
// verify-own-solver.mjs runs them, on the same generated inputs (3 n, 3 sig with one encoding
// edge case). Verdict per strategy and kind (n, sig) over every build: pass (ejs solved every
// input and the strategy gave ejs's value for each), miss (no value where ejs gave one: safe),
// wrong (a value that differs from ejs's: never allowed), oracle (ejs failed too, so the player
// cannot be judged) or fail (no build). The canary passes only when all four verdicts pass. The
// phone check, with both WebView engines, is scripts/own-solver-canary.sh.
//
// It needs www.youtube.com, so it is not part of the build CI: it runs on a computer, on a phone
// with Node (e.g. Termux) or in the canary workflow (.github/workflows/own-solver-canary.yml,
// N6). Solvers run in a bare V8 context (no require, process or network), as in
// verify-own-solver.mjs. Report: stdout + build/own-solver/canary-report.json with the player ID,
// builds, verdicts, counts and timings only (never an input, an output or player text).
// Exit 0 = every verdict passes; 1 = a verdict fails (follow the runbook); 2 = the canary could
// not run.
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { KINDS, VARIANTS, generatedInputs, playerUrl, solveWithEjs, solveWithOwn } from "./own-solver/harness.mjs";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const corpusFile = join(root, "scripts/own-solver/corpus.json");
const ejsAssets = join(root, "app/src/main/assets/youtube-solver");
const ownAssets = join(root, "extractor-master-android/src/main/assets/yft-own-solver");
const reportFile = join(root, "build/own-solver/canary-report.json");
const PLAYER_ID = /^[A-Za-z0-9_-]{4,32}$/;

async function todaysPlayer() {
  const response = await fetch("https://www.youtube.com/iframe_api");
  const text = await response.text();
  const id = /\\\/s\\\/player\\\/([A-Za-z0-9_-]{4,32})\\\//.exec(text)?.[1];
  if (!id) throw new Error("could not read today's player from the embed API");
  return id;
}

/** [task] (a network call), tried up to [attempts] times [waitMs] apart; the last error is thrown. */
export async function withRetry(task, attempts = 3, waitMs = 5000) {
  for (let attempt = 1; ; attempt += 1) {
    try {
      return await task();
    } catch (error) {
      if (attempt >= attempts) throw error;
      await new Promise((resolve) => setTimeout(resolve, waitMs));
    }
  }
}

async function download(player, variant) {
  const response = await fetch(playerUrl(player, variant), { headers: { "Accept-Language": "en-US,en;q=0.9" } });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`${variant} answered ${response.status}`);
  return response.text();
}

/** The own core's worker strategies the canary checks, each with its request (as --strategy B). */
export const STRATEGIES = { P: {}, B: { strategy: "B", hint: false } };

/**
 * Per kind: the inputs, the ones ejs solved and, of those, the ones the own strategy matched,
 * answered with another value (wrong) or left without a value (missed).
 */
export function judge(inputs, ejs, own) {
  const counts = {};
  for (const kind of KINDS) {
    const truth = ejs?.[kind] ?? {};
    const given = own?.[kind] ?? {};
    const solved = inputs[kind].filter((input) => typeof truth[input] === "string");
    counts[kind] = {
      total: inputs[kind].length,
      ejs: solved.length,
      agree: solved.filter((input) => given[input] === truth[input]).length,
      wrong: solved.filter((input) => typeof given[input] === "string" && given[input] !== truth[input]).length,
      missed: solved.filter((input) => typeof given[input] !== "string").length,
    };
  }
  return counts;
}

/**
 * One strategy's verdict on a kind over every build ([builds]: variant -> { present, n, sig } with
 * judge's counts), worst first: wrong (a value that differs from ejs's), oracle (ejs failed on an
 * input, so the player cannot be judged), miss (no value where ejs gave one), else pass; fail when
 * no build was there.
 */
export function verdict(builds, kind) {
  const present = Object.values(builds).filter((build) => build.present);
  if (!present.length) return "fail";
  if (present.some((build) => build[kind].wrong > 0)) return "wrong";
  if (present.some((build) => build[kind].ejs < build[kind].total)) return "oracle";
  return present.every((build) => build[kind].agree === build[kind].total) ? "pass" : "miss";
}

/** strategy -> kind -> verdict, from the canary's builds (variant -> { present, ms, P, B, ... }). */
export function verdicts(builds) {
  return Object.fromEntries(
    Object.keys(STRATEGIES).map((strategy) => {
      const view = Object.fromEntries(
        Object.entries(builds).map(([variant, build]) => [variant, build.present ? { present: true, ...build[strategy] } : { present: false }]),
      );
      return [strategy, Object.fromEntries(KINDS.map((kind) => [kind, verdict(view, kind)]))];
    }),
  );
}

/** The canary passes only when every strategy passes every kind. */
export function canaryPasses(all) {
  return Object.keys(STRATEGIES).every((strategy) => KINDS.every((kind) => all?.[strategy]?.[kind] === "pass"));
}

/** One build's line: per kind ejs's and each strategy's counts, then timings and failure classes. */
export function buildLine(build) {
  const names = Object.keys(STRATEGIES);
  const kinds = KINDS.map((kind) => {
    const cells = names.map((name) => {
      const counts = build[name][kind];
      return `${name} ${counts.agree}/${counts.total}${counts.wrong ? ` (wrong ${counts.wrong})` : ""}`;
    });
    return `${kind}: ejs ${build[names[0]][kind].ejs}/${build[names[0]][kind].total}, ${cells.join(", ")}`;
  });
  const ms = ["ejs", ...names].map((name) => `${name} ${build.ms[name]}`).join(", ");
  const failed = Object.entries(build.failed ?? {}).map(([name, failure]) => `${name} failed ${failure}`);
  const errors = Object.entries(build.errors ?? {}).map(([name, error]) => `${name} ${error}`);
  return [...kinds, `ms ${ms}`, ...failed, ...errors].join(" | ");
}

async function main() {
  const asked = process.argv[2];
  if (asked !== undefined && !PLAYER_ID.test(asked)) {
    console.log("usage: node scripts/own-solver-canary.mjs [player ID]");
    return 2;
  }
  const player = asked ?? (await withRetry(todaysPlayer));
  const ejsSolver = {
    lib: readFileSync(join(ejsAssets, "yt.solver.lib.min.js"), "utf8"),
    core: readFileSync(join(ejsAssets, "yt.solver.core.min.js"), "utf8"),
  };
  const ownSolver = {
    lib: readFileSync(join(ownAssets, "meriyah.umd.min.js"), "utf8"),
    core: readFileSync(join(ownAssets, "own.solver.core.js"), "utf8"),
  };
  const inputs = generatedInputs(player);
  const builds = {};
  for (const variant of Object.keys(VARIANTS)) {
    const text = await withRetry(() => download(player, variant));
    if (text === null) {
      builds[variant] = { present: false };
      console.log(`${player} ${variant.padEnd(5)} absent`);
      continue;
    }
    let started = Date.now();
    const ejs = solveWithEjs(ejsSolver, text, inputs);
    const build = { present: true, ms: { ejs: Date.now() - started } };
    if (ejs.error) build.errors = { ejs: ejs.error };
    for (const [strategy, request] of Object.entries(STRATEGIES)) {
      started = Date.now();
      const own = solveWithOwn(ownSolver, text, inputs, request);
      build.ms[strategy] = Date.now() - started;
      build[strategy] = judge(inputs, ejs, own);
      if (own.failed) build.failed = { ...build.failed, [strategy]: own.failed };
      if (own.error) build.errors = { ...build.errors, [strategy]: own.error };
    }
    builds[variant] = build;
    console.log(`${player} ${variant.padEnd(5)} ${buildLine(build)}`);
  }
  const all = verdicts(builds);
  const corpus = existsSync(corpusFile) ? JSON.parse(readFileSync(corpusFile, "utf8")) : { players: [] };
  const inCorpus = corpus.players.some((entry) => entry.player === player);
  const report = {
    canary: "own-solver",
    date: new Date().toISOString(),
    player,
    inCorpus,
    strategies: Object.keys(STRATEGIES),
    builds,
    verdicts: all,
  };
  mkdirSync(dirname(reportFile), { recursive: true });
  writeFileSync(reportFile, `${JSON.stringify(report, null, 2)}\n`);
  const named = Object.entries(all).map(([strategy, byKind]) => `${strategy} ${KINDS.map((kind) => `${kind} ${byKind[kind].toUpperCase()}`).join(", ")}`);
  console.log(`verdict: ${named.join("; ")} (player ${player}, ${inCorpus ? "in" : "not in"} corpus)`);
  const passed = canaryPasses(all);
  if (!passed) {
    console.log("runbook (MASTER_KEY_PHASE1_1_PLAN.md §6):");
    if (!inCorpus) console.log(`  1. node scripts/verify-own-solver.mjs --add ${player}   # add to the corpus, then review`);
    console.log(`  2. node scripts/verify-own-solver.mjs --only ${player}   # reproduce (B: add --strategy B); classify G1-G4 or new`);
    console.log("  3. fix the core, then: node scripts/verify-own-solver.mjs --faults   # every player, no wrong value");
  } else if (!inCorpus) {
    console.log(`note: not in the corpus yet; to keep it as a regression case: node scripts/verify-own-solver.mjs --add ${player}`);
  }
  console.log(`report: ${reportFile}`);
  return passed ? 0 : 1;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  main().then(
    (code) => process.exit(code),
    (error) => {
      console.log(`canary could not run: ${error?.message ?? error}`);
      process.exit(2);
    },
  );
}
