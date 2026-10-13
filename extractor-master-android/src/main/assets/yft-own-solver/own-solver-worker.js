/*
 * YFT own solver worker (Master, Phase 1.1 S5).
 *
 * Evaluates the vendored meriyah parser and the own core (own.solver.core.js), then runs one job
 * (protocol: extractor-master .../solver/OwnSolverProtocol.kt):
 *
 *   job    {"type":"player","keep_prepared":true,"player":"…","n":[…],"sig":[…]}
 *          {"type":"prepared","program":"…","n":[…],"sig":[…]}
 *          N5b fields: "tripwire":true (the environment tripwire), "strategy":"B" with
 *          "hint":true|false (strategy B; from the player text only, never prepared or kept),
 *          "deadline_ms":40000 (N5b A.2: the app's min(job cap, time left in the lookup); it
 *          counts from the job's arrival and the core gets what is left of it after the parse),
 *          "locator":{} or {"v":1,"n":{…},"sig":{…}} (N5b A.4, locate mode: the parts the
 *          app's locator cache holds for this step; data only, see the core's locatorOk; a
 *          malformed one is a BadJob)
 *   reply  {"type":"result","n":{input:output},"sig":{…},"failed"?:"sig:disagree",
 *           "prepared_program"?:"…","locator"?:{"v":1,"n"?:{…},"sig"?:{…}},
 *           "locator_hit"?:["n","sig"]}
 *          {"type":"error","error":"<error class>"}
 *
 * A worker has no document and no navigable location, so nothing the player's code does here can
 * navigate the host page, and its error events are cancelled here (N7), so they cannot fail the
 * job there either. Only n and sig inputs, the strategy, the hint, the tripwire, the deadline and
 * the locator reach the core: a job cannot switch on the core's test-only fault injection or pass
 * vectors. Replies carry values only for asked inputs, failure classes and the prepared program;
 * never a message text.
 */
"use strict";

var YFT_MAX_INPUTS = 64;
// Taken before any player code runs, so the reply path cannot be replaced by it.
var yftPost = self.postMessage.bind(self);
var yftStringify = JSON.stringify;
var yftParse = JSON.parse;
var yftNow = typeof Date === "function" && typeof Date.now === "function" ? Date.now.bind(Date) : function () { return 0; };

// N7: the player's error events (an exception one of its own callbacks throws, or one it reports)
// are not the job's. Cancelled here, by a listener added before any player code runs, they never
// reach the host page, which fails the job on a worker error (strategy B on the phone). The job's
// own errors are caught below and replied as error classes. No event target (the offline tests'
// bare context): no listener.
if (
  typeof Event === "function" &&
  Event.prototype &&
  typeof Event.prototype.preventDefault === "function" &&
  typeof self.addEventListener === "function"
) {
  var yftCancel = Function.prototype.call.bind(Event.prototype.preventDefault);
  self.addEventListener("error", function (event) {
    yftCancel(event);
  });
}

function yftErrorClass(error) {
  var name = error && typeof error.name === "string" ? error.name : "Error";
  return /^[A-Za-z]{1,40}$/.test(name) ? name : "Error";
}

function yftInputs(list) {
  var inputs = [];
  if (Object.prototype.toString.call(list) !== "[object Array]") return inputs;
  for (var i = 0; i < list.length && inputs.length < YFT_MAX_INPUTS; i += 1) {
    if (typeof list[i] === "string" && list[i].length > 0 && inputs.indexOf(list[i]) < 0) {
      inputs.push(list[i]);
    }
  }
  return inputs;
}

// One kind refused by SelfCheck ("n:shape"); anything else failed the run as a whole.
function yftWholeFailure(failed) {
  if (!failed) return false;
  var parts = String(failed).split(",");
  for (var i = 0; i < parts.length; i += 1) {
    if (!/^(n|sig):[a-z-]{1,32}$/.test(parts[i])) return true;
  }
  return false;
}

// Init errors (N5a): how many guarded top-level statements threw, and their classes (names
// like TypeError only, never a message). A reply without them is a run that never instantiated.
function yftInitErrors(reply, report) {
  if (!report || typeof report.topLevelErrors !== "number") return;
  reply.init_errors = report.topLevelErrors;
  reply.init_error_classes = Object.keys(report.topLevelErrorClasses || {}).slice(0, 9);
}

// N5b A.2: the job's deadline in milliseconds, a whole number from 0 up; absent: none (Phase 1.1).
function yftDeadlineOk(job) {
  var ms = job.deadline_ms;
  return ms === undefined || (typeof ms === "number" && ms >= 0 && ms !== Infinity && Math.floor(ms) === ms);
}

// The core's request with what is left of the job's deadline, counted from [received].
function yftTimed(request, job, received) {
  if (job.deadline_ms !== undefined) request.deadline = Math.max(0, job.deadline_ms - Math.max(0, yftNow() - received));
  return request;
}

// N5b A.4: the job's locator, checked by the core (data only: numbers and short hashes); absent:
// Phase 1.1, nothing located.
function yftLocatorOk(solver, job) {
  return job.locator === undefined || solver.locatorOk(job.locator, job.strategy === "B" ? "B" : "P");
}

function yftLocated(request, job) {
  if (job.locator !== undefined) request.locator = job.locator;
  return request;
}

// The core's locator output (only in locate mode): the parts and the kinds that hit.
function yftLocatorReply(reply, output) {
  if (!output.locator) return;
  reply.locator = output.locator;
  if (output.locatorHits && output.locatorHits.length) reply.locator_hit = output.locatorHits.slice(0, 2);
}

function yftRunJob(solver, job, received) {
  if (!job || (job.type !== "player" && job.type !== "prepared") || !yftDeadlineOk(job) || !yftLocatorOk(solver, job)) {
    return { type: "error", error: "BadJob" };
  }
  if (job.strategy === "B") {
    if (job.type !== "player") return { type: "error", error: "BadJob" };
    // B prepares from the player text every time; nothing is kept.
    var outB = solver.solve(yftTimed(yftLocated({
      player: String(job.player),
      n: yftInputs(job.n),
      sig: yftInputs(job.sig),
      strategy: "B",
      hint: job.hint === true,
      tripwire: job.tripwire === true,
    }, job), job, received));
    var replyB = { type: "result", n: outB.n || {}, sig: outB.sig || {} };
    if (outB.failed) replyB.failed = String(outB.failed);
    yftInitErrors(replyB, outB.report);
    yftLocatorReply(replyB, outB);
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
  var output = solver.run(prepared, yftTimed(yftLocated({ n: yftInputs(job.n), sig: yftInputs(job.sig), tripwire: job.tripwire === true }, job), job, received));
  var reply = { type: "result", n: output.n || {}, sig: output.sig || {} };
  if (output.failed) reply.failed = String(output.failed);
  yftInitErrors(reply, output.report);
  yftLocatorReply(reply, output);
  if (job.type === "player" && job.keep_prepared === true && !yftWholeFailure(output.failed)) {
    reply.prepared_program = prepared.program;
  }
  return reply;
}

self.onmessage = function (event) {
  var received = yftNow();
  var job = event.data || {};
  var reply;
  try {
    // Indirect eval runs each bundle as global code, exactly as a script tag would.
    var evaluate = eval;
    var input = yftParse(String(job.input));
    evaluate(String(job.lib));
    evaluate(String(job.core));
    reply = yftStringify(yftRunJob(self.yftOwnSolver, input, received));
  } catch (error) {
    reply = yftStringify({ type: "error", error: yftErrorClass(error) });
  }
  yftPost(reply);
};
