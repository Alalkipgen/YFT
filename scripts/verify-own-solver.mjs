#!/usr/bin/env node
// Phase 1.1 S1 (MASTER_KEY_PHASE1_1_PLAN.md): own-solver parity runner, own core vs ejs.
//
//   node scripts/verify-own-solver.mjs                 # every corpus file: ejs vs vectors, own vs ejs
//   node scripts/verify-own-solver.mjs --only 74edf1a3 # one player
//   node scripts/verify-own-solver.mjs --own <file>    # another own core build
//   node scripts/verify-own-solver.mjs --record        # re-record every corpus hash (review first)
//   node scripts/verify-own-solver.mjs --add today     # add today's player (or --add <player ID>)
//
// Needs network access to www.youtube.com. Player text is cached under build/own-solver/players
// (never committed) and checked against corpus.json's SHA-256 before use. Like
// verify-youtube-solver.mjs, solvers run in a bare V8 context (no require, process or network).
// The report (stdout + build/own-solver/report.json) names players, variants, kinds and input
// indexes only. Exit 0 = every file passes; 1 = a failure; 2 = usage or corpus error.
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import {
  VARIANTS,
  challenges,
  compare,
  coverage,
  passes,
  playerUrl,
  sha256,
  solveWithEjs,
  solveWithOwn,
  summarize,
  validateCorpus,
} from "./own-solver/harness.mjs";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const corpusFile = join(root, "scripts/own-solver/corpus.json");
const vectorsFile = join(root, "scripts/youtube-solver-vectors.json");
const ejsAssets = join(root, "app/src/main/assets/youtube-solver");
const defaultOwn = join(root, "extractor-master-android/src/main/assets/yft-own-solver/own.solver.core.js");
const cacheDir = join(root, "build/own-solver/players");
const reportFile = join(root, "build/own-solver/report.json");

const args = process.argv.slice(2);
const option = (name) => {
  const index = args.indexOf(name);
  return index >= 0 ? args[index + 1] : undefined;
};
const only = option("--only");
const ownFile = option("--own") ?? defaultOwn;
const add = option("--add");
const record = args.includes("--record") || add !== undefined;

async function download(player, variant) {
  const response = await fetch(playerUrl(player, variant), {
    headers: { "Accept-Language": "en-US,en;q=0.9" },
  });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`${player} ${variant} answered ${response.status}`);
  return response.text();
}

async function todaysPlayer() {
  const response = await fetch("https://www.youtube.com/iframe_api");
  const text = await response.text();
  const id = /\\\/s\\\/player\\\/([A-Za-z0-9_-]{4,32})\\\//.exec(text)?.[1];
  if (!id) throw new Error("could not read today's player from the embed API");
  return id;
}

/** The cached or downloaded text of a recorded file, only when its hash matches. */
async function playerText(player, variant, expected) {
  const file = join(cacheDir, `${player}-${variant}.js`);
  if (existsSync(file)) {
    const cached = readFileSync(file, "utf8");
    if (sha256(cached) === expected.sha256) return { status: "ok", text: cached };
  }
  let text;
  try {
    text = await download(player, variant);
  } catch {
    return { status: "download-failed" };
  }
  if (text === null) return { status: "gone" };
  if (sha256(text) !== expected.sha256) return { status: "hash-mismatch" };
  mkdirSync(cacheDir, { recursive: true });
  writeFileSync(file, text);
  return { status: "ok", text };
}

async function recordCorpus(corpus) {
  if (add) {
    const id = add === "today" ? await todaysPlayer() : add;
    if (!corpus.players.some((entry) => entry.player === id)) {
      const day = new Date().toISOString().slice(0, 10);
      corpus.players.push({ player: id, source: add === "today" ? `today's player ${day}` : `added ${day}`, files: {} });
    }
  }
  for (const entry of corpus.players) {
    if (add && entry.files && Object.keys(entry.files).length && !args.includes("--record")) continue;
    entry.files = {};
    for (const variant of Object.keys(VARIANTS)) {
      const text = await download(entry.player, variant);
      entry.files[variant] = text === null ? null : { sha256: sha256(text), bytes: Buffer.byteLength(text) };
      if (text !== null) {
        mkdirSync(cacheDir, { recursive: true });
        writeFileSync(join(cacheDir, `${entry.player}-${variant}.js`), text);
      }
      console.log(`recorded ${entry.player} ${variant}: ${text === null ? "none" : "ok"}`);
    }
  }
  writeFileSync(corpusFile, `${JSON.stringify(corpus, null, 2)}\n`);
}

const corpus = JSON.parse(readFileSync(corpusFile, "utf8"));
if (record) {
  await recordCorpus(corpus);
}
const problems = validateCorpus(corpus);
if (problems.length) {
  problems.forEach((problem) => console.log(`CORPUS ${problem}`));
  process.exit(2);
}
const vectors = JSON.parse(readFileSync(vectorsFile, "utf8"));
const ejs = {
  lib: readFileSync(join(ejsAssets, "yt.solver.lib.min.js"), "utf8"),
  core: readFileSync(join(ejsAssets, "yt.solver.core.min.js"), "utf8"),
};
const own = existsSync(ownFile) ? { lib: ejs.lib, core: readFileSync(ownFile, "utf8") } : null;

const results = [];
for (const entry of corpus.players) {
  if (only && entry.player !== only) continue;
  const plan = challenges(entry.player, vectors);
  for (const variant of Object.keys(VARIANTS)) {
    const expected = entry.files[variant];
    if (expected === null) {
      results.push({ player: entry.player, variant, status: "absent" });
      continue;
    }
    const fetched = await playerText(entry.player, variant, expected);
    if (fetched.status !== "ok") {
      results.push({ player: entry.player, variant, status: fetched.status });
      continue;
    }
    const ejsOut = solveWithEjs(ejs, fetched.text, plan.inputs);
    const ownOut = own ? solveWithOwn(own, fetched.text, plan.inputs) : null;
    const result = { player: entry.player, variant, status: "ok", ...compare(plan, ejsOut, ownOut) };
    results.push(result);
    console.log(summarize([result], Boolean(own))[0]);
  }
}

const lines = summarize(results, Boolean(own));
console.log(`coverage: ${coverage(corpus).join(", ")}`);
console.log(lines[lines.length - 1]);
mkdirSync(dirname(reportFile), { recursive: true });
writeFileSync(reportFile, `${JSON.stringify({ ownBuilt: Boolean(own), results }, null, 2)}\n`);
process.exit(results.length > 0 && results.every(passes) ? 0 : 1);