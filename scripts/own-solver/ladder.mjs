// Phase 1.2 N5a (MASTER_KEY_PHASE1_2_PLAN.md): the solver ladder's rule on the computer, the mirror
// of OwnSolverLadderRule.kt. Both read one table of cases
// (extractor-master/src/test/resources/own-solver/ladder-rule-cases.json, ladder.test.mjs).
//
// The ladder runs worker P, then worker B, then the page (strategy E). A worker step answers a kind
// only when it answered every asked input of it with no init errors (0); the page runs only when a
// worker step needs it (an environment failure, an init error, or answers without an init count)
// and answers only with no init errors of its own, a signature only when the live check (V)
// passes. Steps hold kinds, counts and classes only, never a value.
export const LADDER_KINDS = ["n", "sig"];
const ENVIRONMENT_CLASSES = new Set(["ReferenceError", "TypeError"]);
const WORKER_SOURCES = ["P", "B"];

/** Whether one failure string names an environment error class (OwnSolverEnvironmentErrors). */
export function isEnvironmentFailure(failure) {
  const text = String(failure).trim();
  return ENVIRONMENT_CLASSES.has(text.slice(text.lastIndexOf(":") + 1));
}

/** A step: answered kinds, init errors (null = unknown) and failure classes. */
export function makeStep({ answered = [], init = null, failed = [] } = {}) {
  return { answered: new Set(answered), init: typeof init === "number" ? init : null, failed: [...failed] };
}

export function needsPage(step) {
  return step.failed.some(isEnvironmentFailure) || (step.init ?? 0) > 0 || (step.init === null && step.answered.size > 0);
}

/**
 * The source of [kind]'s answer: "P", "B", "E" or "none". [workers]: worker steps in ladder order
 * (P, then B; one that did not run is left off the end); [page]: the page step or null;
 * [live]: "pass", "fail" or "unknown".
 */
export function decide(kind, workers, page, live) {
  if (workers.length > WORKER_SOURCES.length) throw new Error("at most two worker steps (P, B)");
  for (let i = 0; i < workers.length; i += 1) {
    if (workers[i].init === 0 && workers[i].answered.has(kind)) return WORKER_SOURCES[i];
  }
  const pageAnswers = Boolean(page) && workers.some(needsPage) && page.init === 0 && page.answered.has(kind);
  if (!pageAnswers) return "none";
  return kind === "n" || live === "pass" ? "E" : "none";
}

/** A harness output (solveWithOwn, with its report) as a step; null when the step did not run. */
export function stepOf(out, inputs) {
  if (!out) return null;
  const answered = LADDER_KINDS.filter(
    (kind) => inputs[kind].length > 0 && inputs[kind].every((input) => typeof out[kind]?.[input] === "string" && out[kind][input].length > 0),
  );
  const count = out.report?.topLevelErrors;
  const failed = [...String(out.failed ?? "").split(","), ...(out.error ? [String(out.error)] : [])]
    .map((part) => part.trim())
    .filter(Boolean);
  return makeStep({ answered, init: typeof count === "number" ? count : null, failed });
}

// V on the computer: "sim" passes when every judged page signature equals the truth (a stand-in
// for the playback check); "offline" never checks.
function liveOf(mode, out, truth, inputs) {
  if (mode !== "sim" || !truth || !out) return "unknown";
  const judged = inputs.sig.filter((input) => typeof truth.sig?.[input] === "string");
  return judged.length > 0 && judged.every((input) => out.sig?.[input] === truth.sig[input]) ? "pass" : "fail";
}

/**
 * The ladder's answer from one file's runs { workerP, workerB, pageP, pageB } (null = not run).
 * options.page "PB": the page step is page P, or page B for a kind page P did not answer cleanly;
 * "B": page B only. options.live "offline" | "sim" (with options.truth). Returns
 * { n, sig, failed: "kind:ladder,...", sources: { n, sig } }.
 */
export function ladder(outs, inputs, { page = "PB", live = "offline", truth = null } = {}) {
  const workers = [stepOf(outs.workerP, inputs), stepOf(outs.workerB, inputs)].filter(Boolean);
  const pageOuts = page === "PB" ? [outs.pageP, outs.pageB] : [outs.pageB];
  const result = { n: {}, sig: {}, sources: {} };
  const failed = [];
  for (const kind of LADDER_KINDS) {
    if (!inputs[kind].length) continue;
    let pageStep = null;
    let pageOut = null;
    for (const out of pageOuts) {
      const step = stepOf(out, inputs);
      if (!step) continue;
      pageStep = step;
      pageOut = out;
      if (step.init === 0 && step.answered.has(kind)) break;
    }
    const source = decide(kind, workers, pageStep, kind === "sig" ? liveOf(live, pageOut, truth, inputs) : "unknown");
    result.sources[kind] = source;
    const from = source === "P" ? outs.workerP : source === "B" ? outs.workerB : source === "E" ? pageOut : null;
    if (from) for (const input of inputs[kind]) result[kind][input] = from[kind][input];
    else failed.push(`${kind}:ladder`);
  }
  if (failed.length) result.failed = failed.join(",");
  return result;
}

/**
 * The ladder run lazily, as the app will: [solve](name) runs one step ("workerP", "workerB",
 * "pageP", "pageB") and returns its harness output. Worker B runs only for a kind P did not give,
 * the page only when a worker step needs it, page B only for a kind page P did not answer cleanly
 * (option "PB"). Returns { outs, ladder }.
 */
export async function lazyLadder(solve, inputs, options = {}) {
  const kinds = LADDER_KINDS.filter((kind) => inputs[kind].length > 0);
  const outs = { workerP: await solve("workerP") };
  const workers = [stepOf(outs.workerP, inputs)];
  const open = () => kinds.filter((kind) => decide(kind, workers, null, "unknown") === "none");
  if (open().length) {
    outs.workerB = await solve("workerB");
    workers.push(stepOf(outs.workerB, inputs));
  }
  const left = open();
  if (left.length && workers.some(needsPage)) {
    const page = options.page ?? "PB";
    if (page === "PB") outs.pageP = await solve("pageP");
    const pageP = stepOf(outs.pageP, inputs);
    if (left.some((kind) => !(pageP && pageP.init === 0 && pageP.answered.has(kind)))) outs.pageB = await solve("pageB");
  }
  return { outs, ladder: ladder(outs, inputs, options) };
}
