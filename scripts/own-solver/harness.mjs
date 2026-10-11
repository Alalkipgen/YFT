// Phase 1.1 S1 (MASTER_KEY_PHASE1_1_PLAN.md): the own-solver parity harness, pure parts.
//
// The corpus (corpus.json) lists YouTube player IDs with the SHA-256 and size of each variant;
// the player text itself is never committed and is downloaded at test time. The vectors
// (scripts/youtube-solver-vectors.json) come from the yt-dlp ejs test suite (Unlicense).
// ejs (main's bundled solver) is the oracle; the own core is compared with it on the same
// player and inputs. Reports name players, variants, kinds and input indexes only, never a
// solver input or output.
//
// Own core contract (written in S2-S4, read here):
//   file  extractor-master-android/src/main/assets/yft-own-solver/own.solver.core.js
//   runs  in a bare V8 context that already holds `meriyah` (the vendored parser,
//         extractor-master-android/src/main/assets/yft-own-solver/meriyah.umd.min.js)
//   API   yftOwnSolve({player, n: [input], sig: [input]})
//           -> {n: {input: output}, sig: {input: output}, failed?: "reason"}
//         an input without a verified output is simply absent (never guessed).
import { createHash } from "node:crypto";
import vm from "node:vm";

export const VARIANTS = {
  main: "player_ias.vflset/en_US/base.js",
  tce: "player_ias_tce.vflset/en_US/base.js",
  es6: "player_es6.vflset/en_US/base.js",
  phone: "player-plasma-ias-phone-en_US.vflset/base.js",
};
export const KINDS = ["n", "sig"];
export const SOLVE_TIMEOUT_MS = 60_000;
const PLAYER_ID = /^[A-Za-z0-9_-]{4,32}$/;
const SHA256 = /^[0-9a-f]{64}$/;

export function playerUrl(player, variant) {
  return `https://www.youtube.com/s/player/${player}/${VARIANTS[variant]}`;
}

export function sha256(text) {
  return createHash("sha256").update(text, "utf8").digest("hex");
}

/** Problems of a corpus document; empty when it is well formed. */
export function validateCorpus(corpus) {
  const problems = [];
  if (!corpus || !Array.isArray(corpus.players)) return ["corpus.players must be a list"];
  const seen = new Set();
  for (const [index, entry] of corpus.players.entries()) {
    const label = `players[${index}]`;
    if (!PLAYER_ID.test(entry?.player ?? "")) problems.push(`${label}: bad player ID`);
    if (seen.has(entry?.player)) problems.push(`${label}: duplicate player ${entry.player}`);
    seen.add(entry?.player);
    if (typeof entry?.source !== "string" || !entry.source) problems.push(`${label}: source missing`);
    const files = entry?.files ?? {};
    for (const key of Object.keys(files)) {
      if (!(key in VARIANTS)) problems.push(`${label}: unknown variant ${key}`);
    }
    for (const variant of Object.keys(VARIANTS)) {
      if (!(variant in files)) {
        problems.push(`${label}: ${variant} not recorded (null when the player has none)`);
        continue;
      }
      const file = files[variant];
      if (file === null) continue;
      if (!SHA256.test(file?.sha256 ?? "")) problems.push(`${label}.${variant}: bad sha256`);
      if (!Number.isInteger(file?.bytes) || file.bytes < 10_000) problems.push(`${label}.${variant}: bad size`);
    }
  }
  return problems;
}

/** Which variant kinds the corpus covers with at least one recorded file. */
export function coverage(corpus) {
  return Object.keys(VARIANTS).filter((variant) =>
    corpus.players.some((entry) => entry.files?.[variant]),
  );
}

function prng(seedText) {
  let seed = 2166136261;
  for (const char of seedText) seed = Math.imul(seed ^ char.charCodeAt(0), 16777619) >>> 0;
  return () => {
    seed = (seed + 0x6d2b79f5) >>> 0;
    let t = seed;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const N_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
const SIG_CHARS = `${N_CHARS}=`;
// G3 edge cases: the characters an encoding bug mangles (ejs fix #54 class).
const EDGE_PREFIX = "%/+&?# \t=";
const EDGE_CHARS = `${SIG_CHARS}%/+&?# \t`;

/**
 * Deterministic synthetic challenges in the shapes YouTube sends (per player, every run alike),
 * plus one sig edge case with the characters an encoding bug mangles.
 */
export function generatedInputs(player) {
  const random = prng(player);
  const pick = (chars, length) =>
    Array.from({ length }, () => chars[Math.floor(random() * chars.length)]).join("");
  const n = [16, 18, 19].map((length) => pick(N_CHARS, length));
  const sig = [104, 108].map((length) => pick(SIG_CHARS, length));
  sig.push(EDGE_PREFIX + pick(EDGE_CHARS, 107 - EDGE_PREFIX.length));
  return { n, sig };
}

/** Vector steps for [player] plus its generated inputs, per kind, without duplicates. */
export function challenges(player, vectors) {
  const test = vectors.find((entry) => entry.player === player);
  const steps = { n: test?.n ?? [], sig: test?.sig ?? [] };
  const generated = generatedInputs(player);
  const inputs = {};
  for (const kind of KINDS) {
    inputs[kind] = [...new Set([...steps[kind].map((step) => step.input), ...generated[kind]])];
  }
  return { steps, inputs };
}

function context(lib) {
  const sandbox = vm.createContext({});
  vm.runInContext(lib, sandbox, { timeout: SOLVE_TIMEOUT_MS });
  vm.runInContext("var meriyah = lib.meriyah, astring = lib.astring;", sandbox);
  return sandbox;
}

/** main's bundled ejs core on [player]; outputs per kind, or an error class (never its text). */
export function solveWithEjs({ lib, core }, player, inputs) {
  try {
    const sandbox = context(lib);
    vm.runInContext(core, sandbox, { timeout: SOLVE_TIMEOUT_MS });
    sandbox.input = JSON.stringify({
      type: "player",
      player,
      output_preprocessed: false,
      requests: KINDS.map((kind) => ({ type: kind, challenges: inputs[kind] })),
    });
    const output = JSON.parse(
      vm.runInContext("JSON.stringify(jsc(JSON.parse(input)))", sandbox, { timeout: SOLVE_TIMEOUT_MS }),
    );
    const result = {};
    KINDS.forEach((kind, index) => {
      const response = output.responses?.[index];
      result[kind] = response?.type === "result" ? response.data ?? {} : {};
    });
    return result;
  } catch (error) {
    return { n: {}, sig: {}, error: error?.name ?? "Error" };
  }
}

/** A bare context holding only the vendored meriyah parser, as the own solver's worker does. */
function ownContext(lib) {
  const sandbox = vm.createContext({});
  vm.runInContext(lib, sandbox, { timeout: SOLVE_TIMEOUT_MS });
  return sandbox;
}

/** The own core's preparation of [player]: counts and the failure class only. */
export function prepareWithOwn({ lib, core }, player) {
  try {
    const sandbox = ownContext(lib);
    vm.runInContext(core, sandbox, { timeout: SOLVE_TIMEOUT_MS });
    sandbox.player = player;
    return JSON.parse(
      vm.runInContext(
        "(function(){var p=yftOwnSolver.prepare(player);return JSON.stringify({ok:p.ok,failed:p.failed,info:p.info})})()",
        sandbox,
        { timeout: SOLVE_TIMEOUT_MS },
      ),
    );
  } catch (error) {
    return { ok: false, failed: error?.name ?? "Error" };
  }
}

/** The own core on [player] through its contract (see the header); [extra] adds faults. */
export function solveWithOwn({ lib, core }, player, inputs, extra = {}) {
  try {
    const sandbox = ownContext(lib);
    vm.runInContext(core, sandbox, { timeout: SOLVE_TIMEOUT_MS });
    sandbox.input = JSON.stringify({ player, n: inputs.n, sig: inputs.sig, ...extra });
    const output = JSON.parse(
      vm.runInContext("JSON.stringify(yftOwnSolve(JSON.parse(input)))", sandbox, {
        timeout: SOLVE_TIMEOUT_MS,
      }),
    );
    return {
      n: output?.n ?? {},
      sig: output?.sig ?? {},
      ...(output?.failed ? { failed: String(output.failed).slice(0, 60) } : {}),
    };
  } catch (error) {
    return { n: {}, sig: {}, error: error?.name ?? "Error" };
  }
}

/**
 * One file's result: ejs against the vectors and on every input, and (when built) the own core
 * against the vectors and against ejs. Only kinds and input indexes are named.
 */
export function compare({ steps, inputs }, ejs, own) {
  const vectorCheck = (outputs) => {
    const failed = [];
    let passed = 0;
    for (const kind of KINDS) {
      steps[kind].forEach((step, index) => {
        if (outputs?.[kind]?.[step.input] === step.expected) passed += 1;
        else failed.push(`${kind}#${index}`);
      });
    }
    return { passed, failed };
  };
  const total = KINDS.reduce((sum, kind) => sum + inputs[kind].length, 0);
  const ejsVectors = vectorCheck(ejs);
  const ejsUnsolved = [];
  for (const kind of KINDS) {
    inputs[kind].forEach((input, index) => {
      if (typeof ejs?.[kind]?.[input] !== "string") ejsUnsolved.push(`${kind}#${index}`);
    });
  }
  const result = {
    ejs: { vectorsPassed: ejsVectors.passed, vectorsFailed: ejsVectors.failed, unsolved: ejsUnsolved, total },
    own: null,
  };
  if (ejs?.error) result.ejs.error = ejs.error;
  if (own) {
    const ownVectors = vectorCheck(own);
    const disagree = [];
    const missing = [];
    let agree = 0;
    for (const kind of KINDS) {
      inputs[kind].forEach((input, index) => {
        const value = own[kind]?.[input];
        if (typeof value !== "string") missing.push(`${kind}#${index}`);
        else if (value === ejs?.[kind]?.[input]) agree += 1;
        else disagree.push(`${kind}#${index}`);
      });
    }
    result.own = { vectorsPassed: ownVectors.passed, vectorsFailed: ownVectors.failed, agree, disagree, missing, total };
    if (own.error) result.own.error = own.error;
    if (own.failed) result.own.failed = own.failed;
  }
  return result;
}

/** Whether a file's result passes: ejs is a sound oracle, and the own core (if built) equals it. */
export function passes(result) {
  if (result.status !== "ok") return result.status === "absent";
  const ejs = result.ejs;
  const oracle = !ejs.error && ejs.vectorsFailed.length === 0 && ejs.unsolved.length === 0;
  if (!result.own) return oracle;
  const own = result.own;
  return oracle && own.vectorsFailed.length === 0 && own.agree === own.total;
}

/** One line per file plus totals; only names, counts and indexes. */
export function summarize(results, ownBuilt) {
  const lines = [];
  let ok = 0;
  let checked = 0;
  let agree = 0;
  let total = 0;
  for (const result of results) {
    const head = `${result.player} ${result.variant.padEnd(5)}`;
    if (result.status !== "ok") {
      lines.push(`${head} ${result.status}`);
      if (passes(result)) ok += 1;
      continue;
    }
    checked += 1;
    const ejs = result.ejs;
    const vectorTotal = ejs.vectorsPassed + ejs.vectorsFailed.length;
    let line = `${head} ejs: vectors ${ejs.vectorsPassed}/${vectorTotal}, solved ${ejs.total - ejs.unsolved.length}/${ejs.total}`;
    if (ejs.vectorsFailed.length) line += ` (failed ${ejs.vectorsFailed.join(",")})`;
    if (ejs.unsolved.length) line += ` (unsolved ${ejs.unsolved.join(",")})`;
    if (ejs.error) line += ` (${ejs.error})`;
    if (result.own) {
      const own = result.own;
      agree += own.agree;
      total += own.total;
      line += ` | own: agree ${own.agree}/${own.total}, vectors ${own.vectorsPassed}/${vectorTotal}`;
      if (own.disagree.length) line += ` (disagree ${own.disagree.join(",")})`;
      if (own.missing.length) line += ` (no value ${own.missing.join(",")})`;
      if (own.failed || own.error) line += ` (${own.failed ?? own.error})`;
    } else {
      line += " | own: not built";
    }
    line += passes(result) ? "  PASS" : "  FAIL";
    if (passes(result)) ok += 1;
    lines.push(line);
  }
  const parity = ownBuilt ? `own = ejs on ${agree}/${total} inputs` : "own core not built yet";
  lines.push(`${ok}/${results.length} files pass (${checked} checked); ${parity}`);
  return lines;
}

const OMITTABLE = ["window", "self", "location", "navigator", "document", "XMLHttpRequest"];

/**
 * Faults injected into the own core (S4). A wrong candidate and a broken encoding must FAIL the
 * kind they hit; a missing global may FAIL or still give right values. None may give a value
 * that differs from ejs.
 */
export const FAULTS = [
  { name: "decoy-n", faults: { decoy: "n" }, expect: "n" },
  { name: "decoy-sig", faults: { decoy: "sig" }, expect: "sig" },
  { name: "raw-encoding", faults: { rawEncoding: true }, expect: "sig" },
  ...OMITTABLE.map((name) => ({ name: `omit-${name}`, faults: { omit: [name] }, expect: null })),
  { name: "omit-all", faults: { omit: OMITTABLE }, expect: null },
];

/** A fault run against ejs: wrong values (must be 0) and whether an expected FAIL happened. */
export function faultVerdict(fault, ejs, own) {
  let wrong = 0;
  for (const kind of KINDS) {
    for (const [input, value] of Object.entries(own?.[kind] ?? {})) {
      if (value !== ejs?.[kind]?.[input]) wrong += 1;
    }
  }
  const detected = fault.expect
    ? new RegExp(`(^|,)${fault.expect}:`).test(own?.failed ?? "") &&
      Object.keys(own?.[fault.expect] ?? {}).length === 0
    : null;
  return { name: fault.name, wrong, detected, ok: wrong === 0 && detected !== false };
}
