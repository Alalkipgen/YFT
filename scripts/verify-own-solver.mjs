#!/usr/bin/env node
// Phase 1.1 S1 (MASTER_KEY_PHASE1_1_PLAN.md): own-solver parity runner, own core vs ejs.
//
//   node scripts/verify-own-solver.mjs                 # every corpus file: ejs vs vectors, own vs ejs
//   node scripts/verify-own-solver.mjs --only 74edf1a3 # one player
//   node scripts/verify-own-solver.mjs --own <file>    # another own core build
//   node scripts/verify-own-solver.mjs --record        # re-record every corpus hash (review first)
//   node scripts/verify-own-solver.mjs --add today     # add today's player (or --add <player ID>)
//   node scripts/verify-own-solver.mjs --prepare       # own core: candidates per file only (S2)
//   node scripts/verify-own-solver.mjs --faults        # also inject faults (S4): never a wrong value
//   ... --faults --fault-cases init-wrong-n,init-wrong-sig   # only these fault cases (N4b)
//   node scripts/verify-own-solver.mjs --strategy B    # own core with strategy B alone (Phase 1.2 N3)
//   ... --only <player> --variant <main|tce|es6|...> --verbose   # one file; B timings and stages
//   ... --strategy B --hint                       # B ranked by P's answer (the ladder's use)
//   ... --page                                    # strategy E (N4): the core in page mode in the
//                                                 # harness's page-frame DOM stub (P, or B with --strategy B)
//   ... --init                                    # init errors per file: count and classes (N4b)
//   node scripts/verify-own-solver.mjs --ladder    # N5a: worker P, worker B, page P, page B per file
//                                                 # (B alone) and the ladder's answer (ladder.mjs:
//                                                 # page step PB, live check simulated) against ejs
//   ... --ladder --dyncode                        # also eval reads / Function calls by player code
//   ... --faults --ladder                         # each fault case through the ladder, run lazily
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
  FAULTS,
  faultVerdict,
  prepareWithOwn,
  settle,
} from "./own-solver/harness.mjs";
import { ladder, lazyLadder } from "./own-solver/ladder.mjs";

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
const onlyVariant = option("--variant");
const ownFile = option("--own") ?? defaultOwn;
const add = option("--add");
const record = args.includes("--record") || add !== undefined;
const prepareOnly = args.includes("--prepare");
const withFaults = args.includes("--faults");
const faultCaseNames = option("--fault-cases")?.split(",").filter(Boolean) ?? null;
const faultCases = faultCaseNames ? FAULTS.filter((fault) => faultCaseNames.includes(fault.name)) : FAULTS;
if (faultCaseNames && faultCases.length !== faultCaseNames.length) {
  console.error(`--fault-cases: unknown name (known: ${FAULTS.map((fault) => fault.name).join(",")})`);
  process.exit(2);
}
const strategy = option("--strategy");
if (strategy !== undefined && strategy !== "P" && strategy !== "B") {
  console.error("--strategy must be P or B");
  process.exit(2);
}
const verbose = args.includes("--verbose");
// B alone by default (no hint from P); --hint lets B rank by P's answer as the ladder may.
const withHint = args.includes("--hint");
// --page: strategy E on the computer (N4), the same solve in the page-frame DOM stub, page mode.
const page = args.includes("--page");
// --init (N4b): each file's init errors (top-level statements that threw), count and classes only.
const withInit = args.includes("--init");
// --ladder (N5a): the four runs the ladder may use and its answer; --dyncode adds eval/Function
// counts per run. They run every strategy themselves.
const withLadder = args.includes("--ladder");
const withDyncode = args.includes("--dyncode");
if (withDyncode && !withLadder) {
  console.error("--dyncode runs with --ladder");
  process.exit(2);
}
if (withLadder && (strategy || page || withInit)) {
  console.error("--ladder runs every strategy and reports init errors itself; drop --strategy, --page and --init");
  process.exit(2);
}
// B alone (no hint), as the mutation runner's --ladder.
const LADDER_STEPS = {
  workerP: { report: true },
  workerB: { strategy: "B", hint: false },
  pageP: { page: true, report: true },
  pageB: { strategy: "B", hint: false, page: true },
};
const STEP_LABEL = { workerP: "P", workerB: "B", pageP: "pP", pageB: "pB" };
const LADDER_OPTIONS = [
  ["PB+V", { page: "PB", live: "sim" }],
  ["PB off", { page: "PB", live: "offline" }],
  ["B+V", { page: "B", live: "sim" }],
  ["B off", { page: "B", live: "offline" }],
];
const ladderInit = Object.fromEntries(Object.keys(LADDER_STEPS).map((name) => [name, { files: 0, withErrors: 0, unknown: 0, total: 0 }]));
const ladderTotals = Object.fromEntries(LADDER_OPTIONS.map(([label]) => [label, { agree: 0, wrong: 0, missing: 0, total: 0 }]));
const ladderSources = {};
const dyncodeTotals = Object.fromEntries(Object.keys(LADDER_STEPS).map((name) => [name, { files: 0, withAny: 0, counts: {} }]));
const tally = (map, key) => {
  map[key] = (map[key] ?? 0) + 1;
};
// Per input against ejs: same value, another value, or none.
function againstEjs(totals, answer, ejsOut, inputs) {
  for (const kind of ["n", "sig"]) {
    for (const input of inputs[kind]) {
      const expected = ejsOut[kind]?.[input];
      if (typeof expected !== "string") continue;
      totals.total += 1;
      const value = answer[kind]?.[input];
      if (typeof value !== "string") totals.missing += 1;
      else if (value === expected) totals.agree += 1;
      else totals.wrong += 1;
    }
  }
}
const strategyExtra = {
  ...(strategy === "B" ? { strategy: "B", hint: withHint, ...(verbose ? { debug: true } : {}) } : {}),
  ...(page ? { page: true } : {}),
};

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
const ownLib = join(dirname(defaultOwn), "meriyah.umd.min.js");
const own = existsSync(ownFile) ? { lib: readFileSync(ownLib, "utf8"), core: readFileSync(ownFile, "utf8") } : null;

const results = [];
let preparedCount = 0;
let faultRuns = 0;
let faultWrong = 0;
let faultMissed = 0;
let faultFiles = 0;
const faultTotals = new Map();
const initCounts = [];
const initClasses = {};
for (const entry of corpus.players) {
  if (only && entry.player !== only) continue;
  const plan = challenges(entry.player, vectors);
  for (const variant of Object.keys(VARIANTS)) {
    if (onlyVariant && variant !== onlyVariant) continue;
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
    if (prepareOnly) {
      const prepared = own ? prepareWithOwn(own, fetched.text) : { ok: false, failed: "own core not built" };
      const info = prepared.info ?? {};
      const ok = prepared.ok && info.candidates > 0;
      preparedCount += ok ? 1 : 0;
      console.log(
        `${entry.player} ${variant.padEnd(5)} ` +
          (prepared.ok
            ? `candidates ${info.candidates} (definitions ${info.fromDefinitions}, call sites ${info.fromCallSites}), ` +
              `kept ${info.kept}/${info.statements}, tables ${info.tables}`
            : `FAIL ${prepared.failed}`) +
          (ok ? "  PASS" : "  FAIL"),
      );
      results.push({ player: entry.player, variant, status: ok ? "absent" : "prepare-failed" });
      continue;
    }
    const ejsOut = solveWithEjs(ejs, fetched.text, plan.inputs);
    await settle();
    let ownOut = null;
    let ladderRuns = null;
    if (own && withLadder) {
      ladderRuns = {};
      for (const [name, extra] of Object.entries(LADDER_STEPS)) {
        ladderRuns[name] = solveWithOwn(own, fetched.text, plan.inputs, { ...extra, ...(withDyncode ? { dyncode: true } : {}) });
        await settle();
      }
      ownOut = ladder(ladderRuns, plan.inputs, { page: "PB", live: "sim", truth: ejsOut });
    } else {
      ownOut = own ? solveWithOwn(own, fetched.text, plan.inputs, { ...strategyExtra, ...(withInit ? { report: true } : {}) }) : null;
      await settle();
    }
    const result = { player: entry.player, variant, status: "ok", ...compare(plan, ejsOut, ownOut) };
    if (own && withFaults) {
      result.faults = [];
      for (const fault of faultCases) {
        if (withLadder) {
          const lazy = await lazyLadder(async (name) => {
            const out = solveWithOwn(own, fetched.text, plan.inputs, { ...LADDER_STEPS[name], faults: fault.faults });
            await settle();
            return out;
          }, plan.inputs, { page: "PB", live: "sim", truth: ejsOut });
          result.faults.push({ ...faultVerdict(fault, ejsOut, lazy.ladder), sources: lazy.ladder.sources, steps: Object.keys(lazy.outs) });
          continue;
        }
        const faulted = solveWithOwn(own, fetched.text, plan.inputs, { ...strategyExtra, faults: fault.faults });
        result.faults.push(faultVerdict(fault, ejsOut, faulted));
        await settle();
      }
      faultRuns += result.faults.length;
      faultWrong += result.faults.reduce((sum, verdict) => sum + verdict.wrong, 0);
      faultMissed += result.faults.filter((verdict) => verdict.detected === false).length;
      for (const verdict of result.faults) {
        const total = faultTotals.get(verdict.name) ?? { runs: 0, wrong: 0, missed: 0, bad: 0 };
        total.runs += 1;
        total.wrong += verdict.wrong;
        total.missed += verdict.detected === false ? 1 : 0;
        total.bad += verdict.ok ? 0 : 1;
        if (verdict.sources) {
          total.sources = total.sources ?? {};
          for (const [kind, source] of Object.entries(verdict.sources)) tally(total.sources, `${kind} ${source}`);
          total.steps = (total.steps ?? 0) + verdict.steps.length;
        }
        faultTotals.set(verdict.name, total);
      }
    }
    results.push(result);
    let line = summarize([result], Boolean(own))[0];
    if (result.faults) {
      const bad = result.faults.filter((verdict) => !verdict.ok).map((verdict) => verdict.name);
      line += ` | faults ${result.faults.length - bad.length}/${result.faults.length} ok${bad.length ? ` (bad ${bad.join(",")})` : ""}`;
      if (bad.length) faultFiles += 1;
    }
    if (ladderRuns) {
      const inits = Object.entries(ladderRuns).map(([name, out]) => {
        const count = out.report?.topLevelErrors;
        const totals = ladderInit[name];
        totals.files += 1;
        if (typeof count !== "number") totals.unknown += 1;
        else {
          totals.total += count;
          if (count > 0) totals.withErrors += 1;
        }
        return `${STEP_LABEL[name]} ${typeof count === "number" ? count : "?"}`;
      });
      line += ` | init errors ${inits.join(", ")} | ladder n ${ownOut.sources.n ?? "-"}, sig ${ownOut.sources.sig ?? "-"}`;
      for (const [kind, source] of Object.entries(ownOut.sources)) tally(ladderSources, `${kind} ${source}`);
      for (const [label, options] of LADDER_OPTIONS) {
        againstEjs(ladderTotals[label], ladder(ladderRuns, plan.inputs, { ...options, truth: ejsOut }), ejsOut, plan.inputs);
      }
      if (withDyncode) {
        const parts = Object.entries(ladderRuns).map(([name, out]) => {
          const d = out.dyncode;
          const totals = dyncodeTotals[name];
          totals.files += 1;
          if (!d) return `${STEP_LABEL[name]} ?`;
          if (Object.values(d).some((count) => count > 0)) totals.withAny += 1;
          for (const [key, count] of Object.entries(d)) totals.counts[key] = (totals.counts[key] ?? 0) + count;
          return `${STEP_LABEL[name]} eval ${d.evalInit}/${d.evalCall}, Function ${d.functionInit}/${d.functionCall}, other ${d.otherInit}/${d.otherCall}${d.unknown ? `, unknown ${d.unknown}` : ""}`;
        });
        line += ` | dyncode (init/later) ${parts.join("; ")}`;
      }
    }
    if (withInit) {
      const count = ownOut?.report?.topLevelErrors;
      const classes = ownOut?.report?.topLevelErrorClasses ?? {};
      if (typeof count === "number") initCounts.push(count);
      for (const [name, n] of Object.entries(classes)) initClasses[name] = (initClasses[name] ?? 0) + n;
      const named = Object.entries(classes).map(([name, n]) => `${name} ${n}`).join(", ");
      line += ` | init errors ${typeof count === "number" ? count : "?"}${named ? ` (${named})` : ""}`;
    }
    if (ownOut?.report && strategy === "B") {
      const r = ownOut.report;
      const kind = (k) => (r[k] ? `${k} ${r[k].shortlisted}/${r[k].confirmed}/${r[k].groups}${r[k].reason ? ` ${r[k].reason}` : ""}` : `${k} -`);
      line += ` | B: wrapper ${r.prepare?.wrapper ?? "?"}, registered ${r.registered ?? "?"}, screened ${r.screened ?? "?"}${r.skipped ? ` (skipped ${r.skipped})` : ""}, ` +
        `${kind("n")}, ${kind("sig")} (shortlisted/confirmed/groups)`;
      if (verbose) {
        line += ` | stage ${r.stage ?? "?"} current ${JSON.stringify(r.current ?? null)}` +
          ` | ms prepare ${JSON.stringify(r.prepare?.ms ?? {})} run ${JSON.stringify(r.ms ?? {})}` +
          ` | slow ${r.slow ?? "?"} ${JSON.stringify(r.slowest ?? [])} | shapes ${r.shapes ?? "?"}, templates ${r.prepare?.templates ?? "?"} (propagated ${r.prepare?.propagated ?? "?"}, bindings ${r.prepare?.bindings ?? "?"}, chains ${r.prepare?.chains ?? "?"})`;
        (ownOut.debug?.builders ?? [])
          .sort((a, b) => b[2] - a[2])
          .slice(0, 60)
          .forEach((b) => {
            line += `\n    builder-named fn ${JSON.stringify(b)}`;
          });
        for (const k of ["n", "sig"]) {
          (ownOut.debug?.[k] ?? []).forEach((group, index) => {
            const inputs = plan.inputs[k];
            const right = inputs.filter((input) => group.answers?.[input] !== undefined && group.answers[input] === ejsOut[k]?.[input]).length;
            const wrong = inputs.filter((input) => group.answers?.[input] !== undefined && group.answers[input] !== ejsOut[k]?.[input]).length;
            line += `\n    ${k} group ${index}: size ${group.size}, score ${group.score}, first ${group.first}, ` +
              `reason ${group.reason ?? "-"}, = ejs ${right}, != ejs ${wrong}, members ${JSON.stringify(group.members)}`;
          });
        }
      }
    }
    console.log(line);
  }
}

console.log(`coverage: ${coverage(corpus).join(", ")}`);
if (prepareOnly) {
  console.log(`${preparedCount}/${results.length} files yield candidates`);
  process.exit(results.length > 0 && preparedCount === results.length ? 0 : 1);
}
const lines = summarize(results, Boolean(own));
console.log(lines[lines.length - 1]);
if (withFaults) {
  console.log(`faults: ${faultRuns} runs, wrong values ${faultWrong}, expected FAILs missed ${faultMissed}`);
  for (const [name, total] of faultTotals) {
    const ladderPart = total.sources
      ? ` | ladder ${Object.entries(total.sources).sort().map(([key, count]) => `${key} ${count}`).join(", ")}; steps run ${total.steps}`
      : "";
    console.log(`  fault ${name}: ${total.runs} runs, wrong values ${total.wrong}, missed ${total.missed}, files not ok ${total.bad}${ladderPart}`);
  }
}
if (withInit) {
  const withErrors = initCounts.filter((count) => count > 0).length;
  const sum = initCounts.reduce((a, b) => a + b, 0);
  const range = initCounts.length ? `${Math.min(...initCounts)}-${Math.max(...initCounts)}` : "-";
  const named = Object.entries(initClasses).sort().map(([name, n]) => `${name} ${n}`).join(", ");
  console.log(`init errors: files with errors ${withErrors}/${initCounts.length}, total ${sum}, per file ${range}${named ? `, classes ${named}` : ""}`);
}
if (withLadder) {
  const step = (name) => {
    const t = ladderInit[name];
    return `${STEP_LABEL[name]} ${t.withErrors}/${t.files}${t.unknown ? ` (unknown ${t.unknown})` : ""}`;
  };
  console.log(`ladder init errors: files with errors ${Object.keys(LADDER_STEPS).map(step).join(", ")}`);
  console.log(`ladder sources (PB+V, per file): ${Object.entries(ladderSources).sort().map(([key, count]) => `${key} ${count}`).join(", ")}`);
  console.log(
    `ladder vs ejs: ${LADDER_OPTIONS.map(([label]) => {
      const t = ladderTotals[label];
      return `${label} = ejs ${t.agree}/${t.total}, wrong ${t.wrong}, no value ${t.missing}`;
    }).join("; ")}`,
  );
  if (withDyncode) {
    for (const [name, t] of Object.entries(dyncodeTotals)) {
      const c = t.counts;
      console.log(
        `dyncode ${STEP_LABEL[name]}: files with any ${t.withAny}/${t.files}; eval reads init ${c.evalInit ?? 0}, later ${c.evalCall ?? 0}; ` +
          `Function calls init ${c.functionInit ?? 0}, later ${c.functionCall ?? 0}; other constructors init ${c.otherInit ?? 0}, later ${c.otherCall ?? 0}; unknown ${c.unknown ?? 0}`,
      );
    }
  }
}
// Page-mode and ladder runs keep their own report, so a worker-mode run can go at the same time.
const outFile = withLadder
  ? reportFile.replace(/\.json$/, "-ladder.json")
  : page ? reportFile.replace(/\.json$/, `${strategy === "B" ? "-b" : ""}-page.json`) : reportFile;
mkdirSync(dirname(outFile), { recursive: true });
writeFileSync(outFile, `${JSON.stringify({ ownBuilt: Boolean(own), page, results }, null, 2)}\n`);
const ladderOk = !withLadder || Object.values(ladderTotals).every((t) => t.wrong === 0);
const faultsOk = (!withFaults || (faultWrong === 0 && faultMissed === 0 && faultFiles === 0)) && ladderOk;
process.exit(results.length > 0 && results.every(passes) && faultsOk ? 0 : 1);