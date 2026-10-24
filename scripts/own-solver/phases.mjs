// Phase 1.2 N5b A.1 (MASTER_KEY_PHASE1_2_PLAN.md §3 N5b "Final plan", A.1): the phase times of one
// solve, read from the own core's report (milliseconds and counts only, never an input, an output
// or code), and the locator cache's hit estimate before the cache exists.
//
// Phases: prepare (P: parse, build; B: parse, instrument, templates, reparse, build), init (the
// player's top-level code on the first instance), search (P: find, the candidates from the patterns,
// and attempts of candidates that gave no value, in the solve and in the tripwire; B: the
// screen), confirm (B only, every shortlisted
// candidate; confirmWin: the winning group's), SelfCheck (P: the rest of the kinds' solve; B: the
// solve per kind on a fresh instance) and the tripwire (P: without its search part).
//
// A locator hit skips the search: the estimate is the phase sum without it (B: without the losing
// candidates' confirm too, the locator names the winners). (a) same process: P's
// prepared program is kept (no prepare); B prepares from the player text every time unless its
// prepared program is kept in memory too (option 3, keptB). (b) after a restart: prepare as well
// (the player text is fetched first; the network is not counted).
export const PHASES = ["prepare", "init", "search", "confirm", "selfcheck", "tripwire"];
export const STEP_LABEL = { workerP: "P", workerB: "B", pageP: "pP", pageB: "pB" };
// The plan's target for a hit (N5b A.1: over it, the owner chooses).
export const HIT_TARGET_MS = 500;
const STRATEGY = { workerP: "P", workerB: "B", pageP: "P", pageB: "B" };

const num = (value) => (Number.isFinite(value) && value > 0 ? value : 0);
const sum = (object) => Object.values(object ?? {}).reduce((total, value) => total + num(value), 0);

/** One solve's phases from its report ([strategy] "P" or "B"); null without a report. */
export function phasesOf(report, strategy) {
  if (!report) return null;
  const ms = report.ms ?? {};
  let phases;
  if (strategy === "B") {
    phases = {
      prepare: sum(report.prepare?.ms),
      init: num(ms.instantiate),
      search: num(ms.screen),
      confirm: num(ms.confirm_n) + num(ms.confirm_sig),
      confirmWin: num(ms.confirm_n_win) + num(ms.confirm_sig_win),
      selfcheck: num(ms.solve_n) + num(ms.solve_sig),
      tripwire: num(ms.tripwire),
    };
  } else {
    // P's find (the candidates from the patterns) is search: a locator names the candidates.
    const find = num(report.prepare?.ms?.find);
    const tripwireSearch = Math.min(num(ms.tripwire_search), num(ms.tripwire));
    phases = {
      prepare: sum(report.prepare?.ms) - find,
      init: num(ms.init),
      search: find + num(ms.search) + tripwireSearch,
      confirm: 0,
      selfcheck: num(ms.selfcheck),
      tripwire: num(ms.tripwire) - tripwireSearch,
    };
  }
  phases.total = PHASES.reduce((total, name) => total + phases[name], 0);
  return phases;
}

/**
 * The hit estimate: (a) same process, (b) after a restart; [keptB]: option 3 for B. A hit names the
 * winning candidates, so B confirms those only (confirmWin; the losers' confirm is search).
 */
export function hitOf(phases, strategy, { keptB = false } = {}) {
  const confirm = Number.isFinite(phases.confirmWin) ? phases.confirmWin : phases.confirm;
  const rest = phases.init + confirm + phases.selfcheck + phases.tripwire;
  return { a: strategy === "B" && !keptB ? phases.prepare + rest : rest, b: phases.prepare + rest };
}

/** The strategy of a ladder step name (workerP, workerB, pageP, pageB). */
export function strategyOf(step) {
  return STRATEGY[step] ?? "P";
}

const median = (values) => {
  const sorted = [...values].sort((a, b) => a - b);
  return sorted.length ? sorted[(sorted.length - 1) >> 1] : 0;
};

/**
 * Per ladder step: the largest and the median of every phase, the whole solve (wall, timed by
 * the runner around the harness call) and the hit estimates over the files added (hitA3: (a) with
 * option 3), the files whose hit estimate is over the target; the slowest file names a player ID
 * and variant only.
 */
export function phaseTable() {
  const steps = new Map();
  const keys = [...PHASES, "total", "wall", "hitA", "hitB", "hitA3"];
  const hits = ["hitA", "hitB", "hitA3"];
  return {
    // hit.a3: (a) with option 3 (B's prepared program kept); P's (a) when not given.
    add(step, file, phases, { wall = 0, hit = { a: 0, b: 0 } } = {}) {
      if (!phases) return;
      const entry = steps.get(step) ?? { files: [], rows: [] };
      entry.files.push(file);
      const hitA3 = Number.isFinite(hit.a3) ? hit.a3 : hit.a;
      entry.rows.push({ ...Object.fromEntries(PHASES.map((name) => [name, phases[name]])), total: phases.total, wall, hitA: hit.a, hitB: hit.b, hitA3 });
      steps.set(step, entry);
    },
    rows() {
      return [...steps.entries()].map(([step, entry]) => {
        const max = Object.fromEntries(keys.map((key) => [key, Math.max(...entry.rows.map((row) => row[key]))]));
        const mid = Object.fromEntries(keys.map((key) => [key, median(entry.rows.map((row) => row[key]))]));
        const slowestAt = entry.rows.reduce((best, row, index) => (row.wall > entry.rows[best].wall ? index : best), 0);
        const over = Object.fromEntries(hits.map((key) => [key, entry.rows.filter((row) => row[key] > HIT_TARGET_MS).length]));
        return { step, files: entry.rows.length, max, median: mid, over, slowest: entry.files[slowestAt] };
      });
    },
    lines() {
      return this.rows().map((row) =>
        `phases ${STEP_LABEL[row.step] ?? row.step} (${row.files} files, max/median ms): ` +
          keys.map((key) => `${key} ${Math.round(row.max[key])}/${Math.round(row.median[key])}`).join(", ") +
          `; over ${HIT_TARGET_MS} ms: ${hits.map((key) => `${key} ${row.over[key]}`).join(", ")}; slowest ${row.slowest}`,
      );
    },
  };
}

/**
 * One file's ladder runs ([outs] by step name, null when not run) into [table], with the wall
 * time per step ([walls], ms); returns "P 30, B 401, ..." for the file's line.
 */
export function addLadder(table, file, outs, walls) {
  const ran = Object.keys(outs).filter((step) => outs[step]);
  for (const step of ran) {
    const strategy = strategyOf(step);
    const phases = phasesOf(outs[step].report, strategy);
    if (!phases) continue;
    const hit = hitOf(phases, strategy);
    if (strategy === "B") hit.a3 = hitOf(phases, "B", { keptB: true }).a;
    table.add(step, file, phases, { wall: walls[step] ?? 0, hit });
  }
  return ran.map((step) => `${STEP_LABEL[step] ?? step} ${Math.round(walls[step] ?? 0)}`).join(", ");
}

/**
 * The lookup's time per file: worker P + worker B (B runs in the time P left) and all four steps
 * (the page steps after them); the largest over the files, with the file (player ID and variant).
 */
export function lookupTable() {
  let pb = { ms: 0, file: "-" };
  let all = { ms: 0, file: "-" };
  return {
    add(file, walls) {
      const wall = (step) => num(walls[step]);
      const two = wall("workerP") + wall("workerB");
      const four = two + wall("pageP") + wall("pageB");
      if (two > pb.ms) pb = { ms: two, file };
      if (four > all.ms) all = { ms: four, file };
    },
    largest() {
      return { pb: { ms: Math.round(pb.ms), file: pb.file }, all: { ms: Math.round(all.ms), file: all.file } };
    },
    line() {
      const { pb: two, all: four } = this.largest();
      return `lookup ms (largest per file): P+B ${two.ms} (${two.file}), P+B+pP+pB ${four.ms} (${four.file})`;
    },
  };
}
