/*
 * YFT own solver worker (Master, Phase 1.1 S5).
 *
 * Evaluates the vendored meriyah parser and the own core (own.solver.core.js), then runs one job
 * (protocol: extractor-master .../solver/OwnSolverProtocol.kt):
 *
 *   job    {"type":"player","keep_prepared":true,"player":"…","n":[…],"sig":[…]}
 *          {"type":"prepared","program":"…","n":[…],"sig":[…]}
 *   reply  {"type":"result","n":{input:output},"sig":{…},"failed"?:"sig:disagree",
 *           "prepared_program"?:"…"}
 *          {"type":"error","error":"<error class>"}
 *
 * A worker has no document and no navigable location, so nothing the player's code does here can
 * navigate the host page. Only n and sig inputs reach the core: a job cannot switch on the core's
 * test-only fault injection or pass vectors. Replies carry values only for asked inputs, failure
 * classes and the prepared program; never a message text.
 */
"use strict";

var YFT_MAX_INPUTS = 64;
// Taken before any player code runs, so the reply path cannot be replaced by it.
var yftPost = self.postMessage.bind(self);
var yftStringify = JSON.stringify;
var yftParse = JSON.parse;

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

function yftRunJob(solver, job) {
  if (!job || (job.type !== "player" && job.type !== "prepared")) {
    return { type: "error", error: "BadJob" };
  }
  var prepared =
    job.type === "prepared"
      ? { ok: typeof job.program === "string" && job.program.length > 0, program: job.program, failed: "not prepared" }
      : solver.prepare(String(job.player));
  if (!prepared.ok) {
    return { type: "result", n: {}, sig: {}, failed: String(prepared.failed || "not prepared") };
  }
  var output = solver.run(prepared, { n: yftInputs(job.n), sig: yftInputs(job.sig) });
  var reply = { type: "result", n: output.n || {}, sig: output.sig || {} };
  if (output.failed) reply.failed = String(output.failed);
  if (job.type === "player" && job.keep_prepared === true && !yftWholeFailure(output.failed)) {
    reply.prepared_program = prepared.program;
  }
  return reply;
}

self.onmessage = function (event) {
  var job = event.data || {};
  var reply;
  try {
    // Indirect eval runs each bundle as global code, exactly as a script tag would.
    var evaluate = eval;
    var input = yftParse(String(job.input));
    evaluate(String(job.lib));
    evaluate(String(job.core));
    reply = yftStringify(yftRunJob(self.yftOwnSolver, input));
  } catch (error) {
    reply = yftStringify({ type: "error", error: yftErrorClass(error) });
  }
  yftPost(reply);
};
