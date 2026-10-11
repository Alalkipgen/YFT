#!/usr/bin/env node
// Phase 1.1 S6 (MASTER_KEY_PHASE1_1_PLAN.md §3 S6, runbook §6): own-solver canary, owner-run.
//
//   node scripts/own-solver-canary.mjs              # today's player (from YouTube's embed API)
//   node scripts/own-solver-canary.mjs <player ID>  # a given player
//
// Downloads every build of the player (main, tce, es6, phone), runs main's bundled ejs solver and
// the own core on the same generated inputs (3 n, 3 sig with one encoding edge case) and reports
// pass/fail per kind (n, sig). A kind passes when ejs solved every input and the own core gave
// the same value for every input on every build. The phone check of the same thing, with both
// WebView engines, is scripts/own-solver-canary.sh.
//
// Never part of CI: it needs www.youtube.com. Runs on a computer, or on a phone with Node (e.g.
// Termux). Solvers run in a bare V8 context (no require, process or network), as in
// verify-own-solver.mjs. Report: stdout + build/own-solver/canary-report.json with the player ID,
// builds, verdicts, counts and timings only (never an input, an output or player text).
// Exit 0 = both kinds pass; 1 = a kind fails (follow the runbook); 2 = the canary could not run.
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

async function download(player, variant) {
  const response = await fetch(playerUrl(player, variant), { headers: { "Accept-Language": "en-US,en;q=0.9" } });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`${variant} answered ${response.status}`);
  return response.text();
}

/** Per kind: inputs ejs solved, inputs where the own core gave ejs's value, and the total. */
export function judge(inputs, ejs, own) {
  const counts = {};
  for (const kind of KINDS) {
    const list = inputs[kind];
    counts[kind] = {
      total: list.length,
      ejs: list.filter((input) => typeof ejs?.[kind]?.[input] === "string").length,
      agree: list.filter((input) => typeof own?.[kind]?.[input] === "string" && own[kind][input] === ejs?.[kind]?.[input]).length,
      wrong: list.filter((input) => typeof own?.[kind]?.[input] === "string" && own[kind][input] !== ejs?.[kind]?.[input]).length,
    };
  }
  return counts;
}

/** A kind's verdict over every build: pass, fail (own missing or wrong) or oracle (ejs failed too). */
export function verdict(builds, kind) {
  const present = Object.values(builds).filter((build) => build.present);
  if (!present.length) return "fail";
  if (present.some((build) => build[kind].ejs < build[kind].total)) return "oracle";
  return present.every((build) => build[kind].agree === build[kind].total) ? "pass" : "fail";
}

async function main() {
  const asked = process.argv[2];
  if (asked !== undefined && !PLAYER_ID.test(asked)) {
    console.log("usage: node scripts/own-solver-canary.mjs [player ID]");
    return 2;
  }
  const player = asked ?? (await todaysPlayer());
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
    const text = await download(player, variant);
    if (text === null) {
      builds[variant] = { present: false };
      console.log(`${player} ${variant.padEnd(5)} absent`);
      continue;
    }
    let started = Date.now();
    const ejs = solveWithEjs(ejsSolver, text, inputs);
    const ejsMs = Date.now() - started;
    started = Date.now();
    const own = solveWithOwn(ownSolver, text, inputs);
    const ownMs = Date.now() - started;
    const counts = judge(inputs, ejs, own);
    builds[variant] = {
      present: true,
      ms: { ejs: ejsMs, own: ownMs },
      ...counts,
      ...(own.failed ? { ownFailed: own.failed } : {}),
      ...(own.error ? { ownError: own.error } : {}),
      ...(ejs.error ? { ejsError: ejs.error } : {}),
    };
    console.log(
      `${player} ${variant.padEnd(5)} ` +
        KINDS.map((kind) => `${kind}: ejs ${counts[kind].ejs}/${counts[kind].total}, own ${counts[kind].agree}/${counts[kind].total}`).join(" | ") +
        ` | ejs ${ejsMs} ms, own ${ownMs} ms` +
        (own.failed ? ` | own failed ${own.failed}` : ""),
    );
  }
  const verdicts = Object.fromEntries(KINDS.map((kind) => [kind, verdict(builds, kind)]));
  const corpus = existsSync(corpusFile) ? JSON.parse(readFileSync(corpusFile, "utf8")) : { players: [] };
  const inCorpus = corpus.players.some((entry) => entry.player === player);
  const report = {
    canary: "own-solver",
    date: new Date().toISOString(),
    player,
    inCorpus,
    builds,
    verdicts,
  };
  mkdirSync(dirname(reportFile), { recursive: true });
  writeFileSync(reportFile, `${JSON.stringify(report, null, 2)}\n`);
  console.log(`verdict: n ${verdicts.n.toUpperCase()}, sig ${verdicts.sig.toUpperCase()} (player ${player}, ${inCorpus ? "in" : "not in"} corpus)`);
  const passed = KINDS.every((kind) => verdicts[kind] === "pass");
  if (!passed) {
    console.log("runbook (MASTER_KEY_PHASE1_1_PLAN.md §6):");
    if (!inCorpus) console.log(`  1. node scripts/verify-own-solver.mjs --add ${player}   # add to the corpus, then review`);
    console.log(`  2. node scripts/verify-own-solver.mjs --only ${player}   # reproduce; classify G1-G4 or new`);
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
