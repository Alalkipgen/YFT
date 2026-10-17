#!/usr/bin/env node
// Phase 1.2 N6 (MASTER_KEY_PHASE1_2_PLAN.md §3 N6): the canary workflow's job summary, Markdown on
// stdout, from the reports its checks wrote. It names players, builds, classes and verdicts only
// (never an input, a value, an address or player text).
//
//   TODAY_EXIT=0 CORPUS_P_EXIT=0 CORPUS_B_EXIT=0 MUTATIONS_EXIT=0 \
//     node scripts/own-solver-canary-summary.mjs >> "$GITHUB_STEP_SUMMARY"
//
// Each *_EXIT is its check's exit code: 0 = pass, 1 = fail, else the check could not run (its
// report is then not read); empty = the step did not run, or was stopped (its *_OUTCOME failure). Reports, under build/own-solver/:
// canary-report.json (own-solver-canary.mjs), corpus-p.json and corpus-b.json
// (verify-own-solver.mjs's report.json, kept by the workflow after its run without and with
// --strategy B) and mutation-report.json (verify-own-solver-mutations.mjs --quick --strategy P,B).
import { existsSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { KINDS, passes } from "./own-solver/harness.mjs";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const reports = join(root, "build/own-solver");

/** A check's state from its exit code, else from the step's outcome (a timeout stops a step). */
export function stepState(code, outcome) {
  if (code === undefined || code === null || String(code).trim() === "") {
    return outcome === "failure" || outcome === "cancelled" ? "stopped" : "not run";
  }
  const value = String(code).trim();
  return value === "0" ? "pass" : value === "1" ? "fail" : "could not run";
}

/** One corpus player's verdict over its builds' results in a verify-own-solver.mjs report. */
export function corpusVerdict(results) {
  if (!results.length) return "not checked";
  const unavailable = results.find((result) => result.status !== "ok" && result.status !== "absent");
  if (unavailable) return String(unavailable.status);
  const checked = results.filter((result) => result.status === "ok");
  if (checked.some((result) => (result.own?.disagree?.length ?? 0) > 0)) return "wrong";
  if (checked.some((result) => result.ejs.error || result.ejs.vectorsFailed.length > 0 || result.ejs.unsolved.length > 0)) {
    return "oracle";
  }
  return results.every(passes) ? "pass" : "miss";
}

/** [{ player, verdict }] in the report's order. */
export function corpusVerdicts(report) {
  const byPlayer = new Map();
  for (const result of report?.results ?? []) {
    if (!byPlayer.has(result.player)) byPlayer.set(result.player, []);
    byPlayer.get(result.player).push(result);
  }
  return [...byPlayer].map(([player, results]) => ({ player, verdict: corpusVerdict(results) }));
}

/** The change simulator's solvers in a report: ejs always, own P and own B when they ran. */
export function mutationSolvers(report) {
  const strategies = report?.strategies ?? ["P"];
  return [
    ["ejs", "ejs"],
    ...(strategies.includes("P") ? [["own P", "ownP"]] : []),
    ...(strategies.includes("B") ? [["own B", "ownB"]] : []),
  ];
}

/** class -> solver key -> pass, wrong (a wrong value) or not judged (every file skipped). */
export function mutationVerdicts(report) {
  const out = {};
  for (const [cls, bucket] of Object.entries(report?.classes ?? {})) {
    out[cls] = {};
    for (const [, key] of mutationSolvers(report)) {
      const counts = bucket[key] ?? { wrong: 0 };
      out[cls][key] = counts.wrong > 0 ? "wrong" : bucket.files - bucket.skipped > 0 ? "pass" : "not judged";
    }
  }
  return out;
}

const code = (name) => `\`${String(name).replace(/[^A-Za-z0-9_-]/g, "")}\``;

/** The summary: the checks' states, then each report's players or classes with their verdicts. */
export function summaryMarkdown({ exits = {}, outcomes = {}, canary = null, corpusP = null, corpusB = null, mutations = null }) {
  const states = Object.fromEntries(
    ["today", "corpusP", "corpusB", "mutations"].map((check) => [check, stepState(exits[check], outcomes[check])]),
  );
  const passed = Object.values(states).every((state) => state === "pass");
  const lines = [
    "## Own solver canary (Phase 1.2 N6)",
    "",
    `**Result: ${passed ? "pass" : "FAIL"}**`,
    "",
    "| Check | Verdict |",
    "|---|---|",
    `| Today's player, 4 builds: ejs, own P, own B | ${states.today} |`,
    `| Corpus against the vectors: ejs, own P | ${states.corpusP} |`,
    `| Corpus: own B alone | ${states.corpusB} |`,
    `| Change simulator, quick: ejs, own P, own B | ${states.mutations} |`,
    "",
  ];
  if (canary && (states.today === "pass" || states.today === "fail")) {
    const builds = Object.entries(canary.builds ?? {});
    const present = builds.filter(([, build]) => build.present).map(([variant]) => variant);
    const absent = builds.filter(([, build]) => !build.present).map(([variant]) => variant);
    lines.push(`### Today's player ${code(canary.player)} (${canary.inCorpus ? "in" : "not in"} the corpus)`, "");
    lines.push(`Builds: ${present.join(", ") || "none"}${absent.length ? `; absent: ${absent.join(", ")}` : ""}`, "");
    lines.push(`| Strategy | ${KINDS.join(" | ")} |`, `|---|${KINDS.map(() => "---").join("|")}|`);
    for (const [strategy, byKind] of Object.entries(canary.verdicts ?? {})) {
      lines.push(`| own ${strategy} | ${KINDS.map((kind) => byKind?.[kind] ?? "-").join(" | ")} |`);
    }
    lines.push("");
  }
  const corpusRows = new Map();
  for (const [column, state, report] of [["p", states.corpusP, corpusP], ["b", states.corpusB, corpusB]]) {
    if (!report || (state !== "pass" && state !== "fail")) continue;
    for (const { player, verdict } of corpusVerdicts(report)) {
      corpusRows.set(player, { ...corpusRows.get(player), [column]: verdict });
    }
  }
  if (corpusRows.size) {
    lines.push(`### Corpus (${corpusRows.size} players, every build, against the recorded vectors)`, "");
    lines.push("| Player | ejs + own P | own B alone |", "|---|---|---|");
    for (const [player, row] of corpusRows) lines.push(`| ${code(player)} | ${row.p ?? "-"} | ${row.b ?? "-"} |`);
    lines.push("");
  }
  if (mutations && (states.mutations === "pass" || states.mutations === "fail")) {
    const solvers = mutationSolvers(mutations);
    const byClass = mutationVerdicts(mutations);
    lines.push(`### Change simulator, quick (${mutations.files} files: the phone build of each player; ${mutations.seeds} seed)`, "");
    lines.push("pass = no wrong value (a safe failure, no value, is allowed)", "");
    lines.push(`| Class | ${solvers.map(([label]) => label).join(" | ")} |`, `|---|${solvers.map(() => "---").join("|")}|`);
    for (const [cls, row] of Object.entries(byClass)) lines.push(`| ${cls} | ${solvers.map(([, key]) => row[key]).join(" | ")} |`);
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}

function readReport(name) {
  const file = join(reports, name);
  if (!existsSync(file)) return null;
  try {
    return JSON.parse(readFileSync(file, "utf8"));
  } catch {
    return null;
  }
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  process.stdout.write(
    summaryMarkdown({
      exits: {
        today: process.env.TODAY_EXIT,
        corpusP: process.env.CORPUS_P_EXIT,
        corpusB: process.env.CORPUS_B_EXIT,
        mutations: process.env.MUTATIONS_EXIT,
      },
      outcomes: {
        today: process.env.TODAY_OUTCOME,
        corpusP: process.env.CORPUS_P_OUTCOME,
        corpusB: process.env.CORPUS_B_OUTCOME,
        mutations: process.env.MUTATIONS_OUTCOME,
      },
      canary: readReport("canary-report.json"),
      corpusP: readReport("corpus-p.json"),
      corpusB: readReport("corpus-b.json"),
      mutations: readReport("mutation-report.json"),
    }),
  );
}
