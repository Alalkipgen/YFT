/*
 * YFT own solver page environment, sandboxed frame (Master, Phase 1.2 N4, strategy E).
 *
 * Runs in page-frame.html inside page-env.html's iframe (sandbox="allow-scripts", also set by the
 * app's response header): an opaque origin with no cookies, no storage, no network (CSP
 * connect-src 'none'; the app refuses every address it does not serve) and no access to the host
 * page. It evaluates the vendored parser and the own core it is given and runs one job with the
 * worker's protocol (own-solver-worker.js, OwnSolverProtocol.kt) in page mode: the core keeps
 * this window's real browser objects (own.solver.core.js installEnvironment).
 *
 * Before any player code runs it takes the reply path (the host's postMessage, JSON) and the
 * timer it needs, and removes WebRTC from this window. It takes the first job from the host page
 * only (source and origin checked), runs it outside the message event (so `window.event` never
 * shows the token to player code) and replies once, with the host's token. Only values for asked
 * inputs, failure classes and the prepared program go back, never a message text.
 */
(function () {
  "use strict";

  var HOST_ORIGIN = "https://appassets.androidplatform.net";
  var READY = "yft-page-frame-ready";
  var MAX_INPUTS = 64;
  var TOKEN = /^[0-9a-f]{32}$/;
  var WEBRTC = ["RTCPeerConnection", "webkitRTCPeerConnection"];

  var host = window.parent;
  if (!host || host === window) {
    return;
  }
  // Taken before any player code runs, so the reply path cannot be replaced by it.
  var post = host.postMessage.bind(host);
  var stringify = JSON.stringify;
  var parse = JSON.parse;
  var later = window.setTimeout.bind(window);
  var now = typeof Date === "function" && typeof Date.now === "function" ? Date.now.bind(Date) : function () { return 0; };
  var stopListening = window.removeEventListener.bind(window);
  var evaluate = window.eval;
  var started = false;

  function errorClass(error) {
    var name = error && typeof error.name === "string" ? error.name : "Error";
    return /^[A-Za-z]{1,40}$/.test(name) ? name : "Error";
  }

  function inputs(list) {
    var kept = [];
    if (Object.prototype.toString.call(list) !== "[object Array]") return kept;
    for (var i = 0; i < list.length && kept.length < MAX_INPUTS; i += 1) {
      if (typeof list[i] === "string" && list[i].length > 0 && kept.indexOf(list[i]) < 0) {
        kept.push(list[i]);
      }
    }
    return kept;
  }

  // One kind refused by SelfCheck ("n:shape"); anything else failed the run as a whole.
  function wholeFailure(failed) {
    if (!failed) return false;
    var parts = String(failed).split(",");
    for (var i = 0; i < parts.length; i += 1) {
      if (!/^(n|sig):[a-z-]{1,32}$/.test(parts[i])) return true;
    }
    return false;
  }

  // Init errors (N5a), as the worker's yftInitErrors: a count and class names only.
  function initErrors(reply, report) {
    if (!report || typeof report.topLevelErrors !== "number") return;
    reply.init_errors = report.topLevelErrors;
    reply.init_error_classes = Object.keys(report.topLevelErrorClasses || {}).slice(0, 9);
  }

  // N5b A.2: the job's deadline as the worker takes it (yftDeadlineOk, yftTimed): a whole number
  // of milliseconds from 0 up, counted from the job's arrival; absent: none.
  function deadlineOk(job) {
    var ms = job.deadline_ms;
    return ms === undefined || (typeof ms === "number" && ms >= 0 && ms !== Infinity && Math.floor(ms) === ms);
  }

  function timed(request, job, received) {
    if (job.deadline_ms !== undefined) request.deadline = Math.max(0, job.deadline_ms - Math.max(0, now() - received));
    return request;
  }

  // The worker's job runner (own-solver-worker.js yftRunJob) with the core in page mode.
  function runJob(solver, job, received) {
    // N5b A.4: page steps run in full every time; the locator cache is the worker steps' only.
    if (!job || (job.type !== "player" && job.type !== "prepared") || !deadlineOk(job) || job.locator !== undefined) {
      return { type: "error", error: "BadJob" };
    }
    // N5b: strategy B in the page (from the player text, nothing kept); no tripwire here, the
    // window's own objects stay.
    if (job.strategy === "B") {
      if (job.type !== "player") return { type: "error", error: "BadJob" };
      var outB = solver.solve(timed({
        player: String(job.player),
        n: inputs(job.n),
        sig: inputs(job.sig),
        strategy: "B",
        hint: job.hint === true,
        page: true,
      }, job, received));
      var replyB = { type: "result", n: outB.n || {}, sig: outB.sig || {} };
      if (outB.failed) replyB.failed = String(outB.failed);
      initErrors(replyB, outB.report);
      return replyB;
    }
    if (job.strategy !== undefined) return { type: "error", error: "BadJob" };
    var prepared =
      job.type === "prepared"
        ? { ok: typeof job.program === "string" && job.program.length > 0, program: job.program, failed: "not prepared" }
        : solver.prepare(String(job.player));
    if (!prepared.ok) {
      return { type: "result", n: {}, sig: {}, failed: String(prepared.failed || "not prepared") };
    }
    var output = solver.run(prepared, timed({ n: inputs(job.n), sig: inputs(job.sig), page: true }, job, received));
    var reply = { type: "result", n: output.n || {}, sig: output.sig || {} };
    if (output.failed) reply.failed = String(output.failed);
    initErrors(reply, output.report);
    if (job.type === "player" && job.keep_prepared === true && !wholeFailure(output.failed)) {
      reply.prepared_program = prepared.program;
    }
    return reply;
  }

  // WebRTC opens connections no content security policy covers: it is removed before player
  // code runs, and a window that keeps it never runs the job.
  function withoutWebRtc() {
    for (var i = 0; i < WEBRTC.length; i += 1) {
      try {
        delete window[WEBRTC[i]];
      } catch (ignored) {
        // Checked below.
      }
      if (typeof window[WEBRTC[i]] !== "undefined") return false;
    }
    return true;
  }

  function run(token, lib, core, input, received) {
    var reply;
    try {
      if (!withoutWebRtc()) {
        reply = stringify({ type: "error", error: "WebRtcOpen" });
      } else {
        // Indirect eval runs each bundle as global code, exactly as a script tag would.
        var job = parse(input);
        evaluate(lib);
        evaluate(core);
        reply = stringify(runJob(window.yftOwnSolver, job, received));
      }
    } catch (error) {
      reply = stringify({ type: "error", error: errorClass(error) });
    }
    post({ token: token, reply: reply }, HOST_ORIGIN);
  }

  function onMessage(event) {
    if (started || event.source !== host || event.origin !== HOST_ORIGIN) {
      return;
    }
    var data = event.data;
    if (!data || typeof data !== "object" || typeof data.token !== "string" || !TOKEN.test(data.token)) {
      return;
    }
    started = true;
    var received = now();
    stopListening("message", onMessage);
    var token = data.token;
    var lib = String(data.lib);
    var core = String(data.core);
    var input = String(data.input);
    // Player code runs in a later task, where `window.event` no longer holds this message.
    later(function () {
      run(token, lib, core, input, received);
    }, 0);
  }

  window.addEventListener("message", onMessage);
  post(READY, HOST_ORIGIN);
})();
