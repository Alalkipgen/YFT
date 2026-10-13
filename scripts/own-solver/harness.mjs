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
import { Worker } from "node:worker_threads";

export const VARIANTS = {
  main: "player_ias.vflset/en_US/base.js",
  tce: "player_ias_tce.vflset/en_US/base.js",
  es6: "player_es6.vflset/en_US/base.js",
  phone: "player-plasma-ias-phone-en_US.vflset/base.js",
};
export const KINDS = ["n", "sig"];
export const SOLVE_TIMEOUT_MS = 60_000;

// Player code (B's probes call thousands of its functions) can leave rejected promises that
// nobody handles. Node keeps each one, and through its error the whole player context, until
// the event loop turns; the runners are synchronous for minutes, so the heap grew to its limit
// (N3: the B fault run and the mutation suite stopped with "heap out of memory"). Such
// rejections are player noise: they are ignored (never printed), and runners call settle()
// between solves.
process.on("unhandledRejection", () => {});

/** Lets the event loop turn once, so Node drops the rejections kept since the last turn. */
export function settle() {
  return new Promise((resolve) => setImmediate(resolve));
}

// The app's worker and page frame post their reply as soon as the job returns; promise jobs the
// player queued run only after that. Default vm contexts put those jobs on Node's own queue, which
// runs them during settle() with no timeout: in N4b one mutated player's B run looped in promise
// jobs there for minutes (memory grew to 1.5 GB). So every context runs its promise jobs right
// after each evaluation, inside the evaluation's timeout ("afterEvaluate"), and a solve stores its
// reply before they run: such a loop costs at most the timeout and never changes the reply.
const CONTEXT_OPTIONS = { microtaskMode: "afterEvaluate" };
const REPLY = "__yftHarnessReply";

/** Runs [expression] (JSON text) in [sandbox]; the reply survives a timeout in later promise jobs. */
function replyOf(sandbox, expression) {
  const store = `Object.defineProperty(globalThis, "${REPLY}", { value: ${expression}, writable: false, configurable: false }); void 0;`;
  try {
    vm.runInContext(store, sandbox, { timeout: SOLVE_TIMEOUT_MS });
  } catch (error) {
    const stored = storedReply(sandbox);
    if (stored === null) throw error;
    return { text: stored, late: true };
  }
  const text = storedReply(sandbox);
  if (text === null) throw new Error("no reply");
  return { text, late: false };
}

function storedReply(sandbox) {
  try {
    const value = sandbox[REPLY];
    return typeof value === "string" ? value : null;
  } catch (ignored) {
    return null;
  }
}
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
  const sandbox = vm.createContext({}, CONTEXT_OPTIONS);
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
    const output = JSON.parse(replyOf(sandbox, "JSON.stringify(jsc(JSON.parse(input)))").text);
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
  const sandbox = vm.createContext({}, CONTEXT_OPTIONS);
  vm.runInContext(lib, sandbox, { timeout: SOLVE_TIMEOUT_MS });
  return sandbox;
}

// Phase 1.2 N4 (strategy E) on the computer: a DOM stub shaped like the app's sandboxed page
// frame (yft-own-solver/page-frame.html). As on a real window, window, document, location and top
// cannot be redefined; the other browser objects exist and can be; cookies and storage throw as in
// a frame without same-origin rights; timers and microtasks never fire during the synchronous run
// (in the frame they would fire only after the reply). No network: fetch rejects, XMLHttpRequest
// never sends. It runs inside the context, before the parser and the core.
export const PAGE_FRAME_URL = "https://appassets.androidplatform.net/yft-own-solver/page-frame.html";

function pagePrelude(g, href) {
  "use strict";
  var fixed = function (name, value) {
    Object.defineProperty(g, name, { value: value, writable: false, enumerable: true, configurable: false });
  };
  var plain = function (name, value) {
    Object.defineProperty(g, name, { value: value, writable: true, enumerable: true, configurable: true });
  };
  var denied = function () {
    var error = new Error("sandboxed");
    error.name = "SecurityError";
    throw error;
  };
  var noop = function () {};
  var Element = function (tag) {
    this.tagName = String(tag).toUpperCase();
    this.nodeName = this.tagName;
    this.nodeType = 1;
    this.childNodes = [];
    this.children = this.childNodes;
    this.parentNode = null;
    this.style = {};
    this.dataset = {};
    this.attributes = {};
    this.textContent = "";
    this.innerHTML = "";
    this.classList = { add: noop, remove: noop, contains: function () { return false; }, toggle: function () { return false; } };
  };
  Element.prototype.appendChild = function (child) { this.childNodes.push(child); child.parentNode = this; return child; };
  Element.prototype.insertBefore = function (child) { return this.appendChild(child); };
  Element.prototype.removeChild = function (child) {
    var at = this.childNodes.indexOf(child);
    if (at >= 0) this.childNodes.splice(at, 1);
    child.parentNode = null;
    return child;
  };
  Element.prototype.setAttribute = function (name, value) { this.attributes[name] = String(value); };
  Element.prototype.getAttribute = function (name) { return Object.prototype.hasOwnProperty.call(this.attributes, name) ? this.attributes[name] : null; };
  Element.prototype.hasAttribute = function (name) { return Object.prototype.hasOwnProperty.call(this.attributes, name); };
  Element.prototype.removeAttribute = function (name) { delete this.attributes[name]; };
  Element.prototype.addEventListener = noop;
  Element.prototype.removeEventListener = noop;
  Element.prototype.dispatchEvent = function () { return true; };
  Element.prototype.getElementsByTagName = function () { return []; };
  Element.prototype.querySelector = function () { return null; };
  Element.prototype.querySelectorAll = function () { return []; };
  Element.prototype.contains = function () { return false; };
  Element.prototype.cloneNode = function () { return new Element(this.tagName); };
  Element.prototype.canPlayType = function () { return ""; };
  Element.prototype.getBoundingClientRect = function () { return { x: 0, y: 0, width: 0, height: 0, top: 0, left: 0, right: 0, bottom: 0 }; };
  var root = new Element("html");
  var head = root.appendChild(new Element("head"));
  var body = root.appendChild(new Element("body"));
  var origin = "https://appassets.androidplatform.net";
  var location = {
    href: href, protocol: "https:", host: "appassets.androidplatform.net", hostname: "appassets.androidplatform.net",
    port: "", pathname: href.slice(origin.length), search: "", hash: "", origin: origin,
    ancestorOrigins: { length: 1, 0: origin }, assign: noop, replace: noop, reload: noop,
    toString: function () { return href; },
  };
  var document = {
    nodeType: 9, location: location, URL: href, documentURI: href, baseURI: href, referrer: "", domain: "",
    title: "", readyState: "complete", visibilityState: "visible", hidden: false, characterSet: "UTF-8",
    contentType: "text/html", compatMode: "CSS1Compat", documentElement: root, head: head, body: body,
    defaultView: g, currentScript: null,
    createElement: function (tag) { return new Element(tag); },
    createElementNS: function (ns, tag) { return new Element(tag); },
    createTextNode: function (text) { return { nodeType: 3, data: String(text), textContent: String(text) }; },
    createDocumentFragment: function () { return new Element("#document-fragment"); },
    createEvent: function () { return { initEvent: noop }; },
    getElementById: function () { return null; },
    getElementsByTagName: function (tag) { tag = String(tag).toLowerCase(); return tag === "head" ? [head] : tag === "body" ? [body] : tag === "html" ? [root] : []; },
    getElementsByClassName: function () { return []; },
    querySelector: function () { return null; },
    querySelectorAll: function () { return []; },
    addEventListener: noop, removeEventListener: noop, dispatchEvent: function () { return true; },
    hasFocus: function () { return false; },
  };
  Object.defineProperty(document, "cookie", { get: denied, set: denied, enumerable: true, configurable: true });
  fixed("window", g);
  fixed("document", document);
  fixed("location", location);
  fixed("top", g);
  plain("self", g);
  plain("parent", g);
  plain("frames", g);
  plain("origin", "null");
  plain("isSecureContext", true);
  plain("navigator", {
    userAgent: "Mozilla/5.0 (Linux; Android 14; Pixel 7; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/131.0.0.0 Mobile Safari/537.36",
    language: "en-US", languages: ["en-US", "en"], platform: "Linux armv8l", vendor: "Google Inc.",
    cookieEnabled: false, onLine: true, hardwareConcurrency: 8, maxTouchPoints: 5, webdriver: false,
  });
  plain("screen", { width: 412, height: 915, availWidth: 412, availHeight: 915, colorDepth: 24, pixelDepth: 24 });
  plain("devicePixelRatio", 2.625);
  plain("innerWidth", 0);
  plain("innerHeight", 0);
  ["localStorage", "sessionStorage"].forEach(function (name) {
    Object.defineProperty(g, name, { get: denied, enumerable: true, configurable: true });
  });
  plain("addEventListener", noop);
  plain("removeEventListener", noop);
  plain("dispatchEvent", function () { return true; });
  plain("postMessage", noop);
  plain("close", noop);
  plain("setTimeout", function () { return 1; });
  plain("clearTimeout", noop);
  plain("setInterval", function () { return 1; });
  plain("clearInterval", noop);
  plain("requestAnimationFrame", function () { return 1; });
  plain("cancelAnimationFrame", noop);
  plain("queueMicrotask", noop);
  plain("matchMedia", function () { return { matches: false, media: "", addListener: noop, removeListener: noop, addEventListener: noop, removeEventListener: noop }; });
  plain("getComputedStyle", function () { return {}; });
  plain("fetch", function () { return Promise.reject(new TypeError("Failed to fetch")); });
  var Request = function XMLHttpRequest() { this.readyState = 0; };
  Request.prototype.open = noop;
  Request.prototype.send = noop;
  Request.prototype.setRequestHeader = noop;
  Request.prototype.abort = noop;
  Request.prototype.addEventListener = noop;
  plain("XMLHttpRequest", Request);
  plain("Element", Element);
  plain("HTMLElement", Element);
}

/** A bare context with the page-frame DOM stub, then the vendored parser (strategy E, N4). */
export function pageContext(lib) {
  const sandbox = vm.createContext({}, CONTEXT_OPTIONS);
  vm.runInContext(`(${pagePrelude.toString()})(this, ${JSON.stringify(PAGE_FRAME_URL)});`, sandbox);
  vm.runInContext(lib, sandbox, { timeout: SOLVE_TIMEOUT_MS });
  return sandbox;
}

// Phase 1.2 N5a (--dyncode): how often player code reads `eval` or calls the Function constructor
// (also through a function's .constructor, and the async/generator function constructors), at init
// (a core frame named *instantiate* is on the stack) or later (n/sig calls, B's probes). It runs in
// the context after the parser and before the core. `eval` becomes an accessor whose getter returns
// the real eval, so a direct eval stays direct (it keeps its scope); Function becomes a Proxy of the
// real one. Callers are told apart by V8 call sites: player code is compiled by the core with
// Function, so its frames are eval frames; the core's own calls are not counted. Not covered:
// setTimeout/setInterval with a string (the stubs never run them) and code reaching another
// realm's constructors. Counts only; the counts are read through a function the player cannot
// replace.
export const DYNCODE_CORE_FILE = "yft-own-solver-core.js";

function dyncodePrelude(g, coreFile) {
  "use strict";
  var E = g.Error;
  var capture = E.captureStackTrace;
  var stringify = g.JSON.stringify;
  var isArray = g.Array.isArray;
  var counts = { evalInit: 0, evalCall: 0, functionInit: 0, functionCall: 0, otherInit: 0, otherCall: 0, unknown: 0 };
  function sites(skip) {
    var saved = E.prepareStackTrace;
    var limit = E.stackTraceLimit;
    var holder = {};
    try {
      E.stackTraceLimit = Infinity;
      E.prepareStackTrace = function (ignored, callSites) {
        return callSites;
      };
      capture(holder, skip);
      return holder.stack;
    } catch (ignored) {
      return null;
    } finally {
      E.prepareStackTrace = saved;
      E.stackTraceLimit = limit;
    }
  }
  function note(kind, skip) {
    var list = sites(skip);
    if (!isArray(list) || !list.length) {
      counts.unknown += 1;
      return;
    }
    var first = list[0];
    if (!first.isEval() && first.getFileName()) return; // the core, the parser or the harness
    var init = false;
    for (var i = 1; i < list.length; i += 1) {
      var site = list[i];
      if (!site.isEval() && site.getFileName() === coreFile && /instantiate/i.test(site.getFunctionName() || "")) {
        init = true;
        break;
      }
    }
    counts[kind + (init ? "Init" : "Call")] += 1;
  }
  var realEval = g.eval;
  var current = realEval;
  Object.defineProperty(g, "eval", {
    get: function evalGetter() {
      note("eval", evalGetter);
      return current;
    },
    set: function (value) {
      current = value;
    },
    enumerable: false,
    configurable: true,
  });
  function wrap(target, kind) {
    var proxy = new Proxy(target, {
      apply: function applyTrap(fn, self, args) {
        note(kind, applyTrap);
        return Reflect.apply(fn, self, args);
      },
      construct: function constructTrap(fn, args, newTarget) {
        note(kind, constructTrap);
        return Reflect.construct(fn, args, newTarget === proxy ? fn : newTarget);
      },
    });
    Object.defineProperty(target.prototype, "constructor", { value: proxy, writable: true, enumerable: false, configurable: true });
    return proxy;
  }
  var RealFunction = g.Function;
  ["async function () {}", "function* () {}", "async function* () {}"].forEach(function (source) {
    try {
      wrap(Object.getPrototypeOf(RealFunction("return " + source)()).constructor, "other");
    } catch (ignored) {
      // A context without that kind of function.
    }
  });
  Object.defineProperty(g, "Function", { value: wrap(RealFunction, "function"), writable: true, enumerable: false, configurable: true });
  Object.defineProperty(g, "__yftDyncode", {
    value: function () {
      return stringify(counts);
    },
    writable: false,
    enumerable: false,
    configurable: false,
  });
}

function dyncodeOf(sandbox) {
  if (!sandbox) return null;
  try {
    return JSON.parse(vm.runInContext("__yftDyncode()", sandbox, { timeout: 2_000 }));
  } catch (ignored) {
    return null;
  }
}

/**
 * The own core's preparation of [player]: counts and the failure class only; [chars] (N5b A.1) is
 * the length of P's kept program (the program itself never leaves the sandbox).
 */
export function prepareWithOwn({ lib, core }, player) {
  try {
    const sandbox = ownContext(lib);
    vm.runInContext(core, sandbox, { timeout: SOLVE_TIMEOUT_MS });
    sandbox.player = player;
    return JSON.parse(
      vm.runInContext(
        "(function(){var p=yftOwnSolver.prepare(player);return JSON.stringify({ok:p.ok,failed:p.failed,info:p.info,chars:p.ok?p.program.length:undefined})})()",
        sandbox,
        { timeout: SOLVE_TIMEOUT_MS },
      ),
    );
  } catch (error) {
    return { ok: false, failed: error?.name ?? "Error" };
  }
}

// N5b A.1, option 3 (B's prepared program kept in memory): what B would keep, in characters.
const PREPARE_B_SIZES =
  "(function(){var p=yftOwnSolver.prepareB(player);if(!p.ok)return JSON.stringify({ok:false,failed:p.failed});" +
  "var s={program:p.program.length,meta:JSON.stringify(p.meta).length,templates:JSON.stringify(p.templates).length};" +
  "s.total=s.program+s.meta+s.templates;return JSON.stringify({ok:true,info:p.info,sizes:s})})()";

/**
 * B's preparation of [player] (N5b A.1): counts, the failure class and the sizes in characters of
 * the prepared program, its function table and its templates (never the program itself).
 */
export function prepareBWithOwn({ lib, core }, player) {
  try {
    const sandbox = ownContext(lib);
    vm.runInContext(core, sandbox, { timeout: SOLVE_TIMEOUT_MS });
    sandbox.player = player;
    return JSON.parse(vm.runInContext(PREPARE_B_SIZES, sandbox, { timeout: SOLVE_TIMEOUT_MS }));
  } catch (error) {
    return { ok: false, failed: error?.name ?? "Error" };
  }
}

/**
 * The own core on [player] through its contract (see the header); [extra] adds faults. With
 * extra.page (N4, strategy E) it runs in the page-frame DOM stub and the core in page mode.
 * extra.report (N4b) returns the core's report for P too (B and E return it with extra.strategy);
 * it is not part of the request. extra.dyncode (N5a) adds the dynamic-code counts (see
 * dyncodePrelude); it is not part of the request either.
 */
export function solveWithOwn({ lib, core }, player, inputs, extra = {}) {
  let sandbox = null;
  const { report: wantReport, dyncode: wantDyncode, ...request } = extra;
  const withReport = Boolean(extra.strategy) || wantReport === true;
  const dyncode = wantDyncode === true ? () => ({ dyncode: dyncodeOf(sandbox) }) : () => ({});
  try {
    sandbox = extra.page ? pageContext(lib) : ownContext(lib);
    if (wantDyncode === true) {
      vm.runInContext(`(${dyncodePrelude.toString()})(this, ${JSON.stringify(DYNCODE_CORE_FILE)});`, sandbox);
      vm.runInContext(core, sandbox, { timeout: SOLVE_TIMEOUT_MS, filename: DYNCODE_CORE_FILE });
    } else {
      vm.runInContext(core, sandbox, { timeout: SOLVE_TIMEOUT_MS });
    }
    sandbox.input = JSON.stringify({ player, n: inputs.n, sig: inputs.sig, ...request });
    const reply = replyOf(sandbox, "JSON.stringify(yftOwnSolve(JSON.parse(input)))");
    const output = JSON.parse(reply.text);
    return {
      // The reply was stored, then the player's later promise jobs ran into the timeout.
      ...(reply.late ? { late: true } : {}),
      n: output?.n ?? {},
      sig: output?.sig ?? {},
      ...(output?.failed ? { failed: String(output.failed).slice(0, 60) } : {}),
      // N5b A.4 (request.locator): the parts (data only: indexes, ids, short hashes) and the hits.
      ...(output?.locator ? { locator: output.locator, locatorHits: output.locatorHits ?? [] } : {}),
      // Counts and classes only (the core's report never holds an input, an output or code).
      ...(withReport ? { report: output?.report ?? null } : {}),
      // Local diagnosis (--verbose) only: per group answers, compared by the caller, never printed.
      ...(extra.debug ? { debug: output?.debug ?? null } : {}),
      ...dyncode(),
    };
  } catch (error) {
    return { n: {}, sig: {}, error: error?.name ?? "Error", ...(withReport ? { report: progressOf(sandbox) } : {}), ...dyncode() };
  }
}

// N5b A.4: P's kept program, prepared before the timed hit (the app keeps it in the same process)
// in a context of its own; it comes out only as the worker's "prepared" job would carry it (JSON
// text, never printed) or as "F" and the failure class.
const KEEP =
  "(function(){var p=yftOwnSolver.prepare(player);return p.ok?JSON.stringify({program:p.program}):'F'+String(p.failed)})()";
// The timed part, inside the sandbox before any player code runs (its own Date.now, so the
// promise jobs the player leaves after the reply are not counted, as in the app).
const TIMED = (solveExpression) =>
  `(function(){var now=Date.now,start=now(),out=${solveExpression};return JSON.stringify({ms:now()-start,out:out})})()`;
const SOLVE_INPUT = "yftOwnSolve(JSON.parse(input))";
const RUN_KEPT = "(function(){var job=JSON.parse(__yftKept);return yftOwnSolver.run({ok:typeof job.program==='string'&&job.program.length>0,program:job.program},JSON.parse(input))})()";

/**
 * N5b A.4: one worker job of the own core on [player] with the job's [extra] (strategy, hint,
 * tripwire, deadline and locator: {} or the parts a cache gave), timed. extra.kept (P only): the
 * hit in the same process: P's program is prepared first (untimed, in another context) and the
 * job runs run(kept) alone, as the worker's "prepared" job; otherwise the job is the whole solve
 * from the text (after a restart; B prepares from the text every time). ms is the core's own time
 * (Date.now in the sandbox, so whole milliseconds); wall is the job: a fresh context, the parser's
 * and the core's load, the run and the reply. The reply holds the answers, the parts (data only:
 * indexes, ids, short hashes), the hits and the report (counts, classes and the locator statuses).
 */
export function hitWithOwn({ lib, core }, player, inputs, extra = {}) {
  let sandbox = null;
  const { kept, report: ignoredReport, dyncode: ignoredDyncode, ...request } = extra;
  let started = performance.now();
  const wall = () => Math.round(performance.now() - started);
  try {
    if (kept === true && (request.strategy === "B" || request.page === true)) throw new TypeError("kept is worker P's");
    let keptJob = null;
    if (kept === true) {
      const preparing = ownContext(lib);
      vm.runInContext(core, preparing, { timeout: SOLVE_TIMEOUT_MS });
      preparing.player = player;
      keptJob = String(vm.runInContext(KEEP, preparing, { timeout: SOLVE_TIMEOUT_MS }));
      if (keptJob.startsWith("F")) return { n: {}, sig: {}, failed: keptJob.slice(1, 61), report: null, ms: 0, wall: 0 };
      started = performance.now();
    }
    sandbox = ownContext(lib);
    vm.runInContext(core, sandbox, { timeout: SOLVE_TIMEOUT_MS });
    let expression = TIMED(SOLVE_INPUT);
    if (keptJob !== null) {
      Object.defineProperty(sandbox, "__yftKept", { value: keptJob, enumerable: false, writable: false, configurable: false });
      keptJob = null;
      sandbox.input = JSON.stringify({ n: inputs.n, sig: inputs.sig, ...request });
      expression = TIMED(RUN_KEPT);
    } else {
      sandbox.input = JSON.stringify({ player, n: inputs.n, sig: inputs.sig, ...request });
    }
    const reply = replyOf(sandbox, expression);
    const timed = JSON.parse(reply.text);
    const output = timed?.out;
    return {
      ...(reply.late ? { late: true } : {}),
      n: output?.n ?? {},
      sig: output?.sig ?? {},
      ...(output?.failed ? { failed: String(output.failed).slice(0, 60) } : {}),
      ...(output?.locator ? { locator: output.locator, locatorHits: output.locatorHits ?? [] } : {}),
      report: output?.report ?? null,
      ms: Number.isFinite(timed?.ms) ? timed.ms : wall(),
      wall: wall(),
    };
  } catch (error) {
    return { n: {}, sig: {}, error: error?.name ?? "Error", report: progressOf(sandbox), ms: wall(), wall: wall() };
  }
}

// A hit's isolate gets the old generation a whole solve needs on the corpus (the runner's own heap
// stays apart), and a hard stop well after the solve's own timeouts.
const ISOLATE_HEAP_MB = 1536;
const ISOLATE_STOP_MS = 3 * SOLVE_TIMEOUT_MS;

/**
 * N5b A.4: hitWithOwn in a fresh V8 isolate (a worker thread), as the app runs each job in a new
 * Worker: in one isolate, a player's second run is warm (V8 reuses what the first one compiled),
 * which made a kept P hit 8x faster than its first run. Resolves hitWithOwn's reply (ms: the
 * core's own time; wall: the job) plus thread: the thread's whole time (its start, the module's
 * load and, for a kept hit, the untimed prepare too).
 */
export function hitInIsolate(own, player, inputs, extra = {}) {
  return new Promise((resolve) => {
    const started = performance.now();
    const elapsed = () => Math.round(performance.now() - started);
    let worker = null;
    let finished = false;
    let stop = null;
    const finish = (out) => {
      if (finished) return;
      finished = true;
      clearTimeout(stop);
      resolve({ ...out, thread: elapsed() });
      worker?.terminate();
    };
    const failure = (name) => finish({ n: {}, sig: {}, error: name, report: null, ms: elapsed() });
    try {
      worker = new Worker(new URL("./hit-isolate.mjs", import.meta.url), {
        workerData: { own, player, inputs, extra },
        resourceLimits: { maxOldGenerationSizeMb: ISOLATE_HEAP_MB },
      });
    } catch (error) {
      failure(error?.name ?? "Error");
      return;
    }
    stop = setTimeout(() => failure("Timeout"), ISOLATE_STOP_MS);
    worker.once("message", finish);
    // Only an error class, never its message (player code may be in it).
    worker.once("error", (error) => failure(error?.name ?? "Error"));
    worker.once("exit", () => failure("Exit"));
  });
}

/** After a timeout: the own core's running report (counts and classes only), when it keeps one. */
function progressOf(sandbox) {
  if (!sandbox) return null;
  try {
    return JSON.parse(
      vm.runInContext(
        "JSON.stringify(typeof yftOwnSolver === 'object' && yftOwnSolver.progress ? yftOwnSolver.progress() : null)",
        sandbox,
        { timeout: 2_000 },
      ),
    );
  } catch (ignored) {
    return null;
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
 *
 * init-wrong (N4b): an init error after which every candidate agrees on a wrong value of one
 * kind (worker mode; inert in page mode). SelfCheck cannot see it, so the core alone gives the
 * wrong values: these cases fail (wrong > 0) until the ladder keeps a worker answer after an
 * init error unverified (MASTER_KEY_PHASE1_2_PLAN.md, N4b). They stay last.
 */
export const FAULTS = [
  { name: "decoy-n", faults: { decoy: "n" }, expect: "n" },
  { name: "decoy-sig", faults: { decoy: "sig" }, expect: "sig" },
  { name: "raw-encoding", faults: { rawEncoding: true }, expect: "sig" },
  ...OMITTABLE.map((name) => ({ name: `omit-${name}`, faults: { omit: [name] }, expect: null })),
  { name: "omit-all", faults: { omit: OMITTABLE }, expect: null },
  { name: "init-wrong-n", faults: { initWrong: "n" }, expect: null },
  { name: "init-wrong-sig", faults: { initWrong: "sig" }, expect: null },
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
