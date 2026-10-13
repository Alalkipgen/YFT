/*
 * YFT own solver host page (Master, Phase 1.1 S5).
 *
 * Loads the vendored parser, the own core and the job from the app's own origin, runs the core
 * in a dedicated worker and hands the worker's JSON reply to the app's bridge. The page makes no
 * other request; the app refuses every address it does not serve itself. Same model as main's
 * solver page, with its own files.
 *
 * N7: a worker error event (an exception the worker did not catch or cancel) goes back as a fixed
 * class only (workerErrorClass), and a reply the page cannot read as "WorkerMessageError".
 */
(function () {
  "use strict";

  var bridge = window.YftOwnSolverBridge;
  var finished = false;
  // Standard classes a worker error event's message may start with ("Uncaught TypeError: …").
  var WORKER_CLASSES = ["TypeError", "RangeError", "ReferenceError", "SyntaxError", "EvalError", "AggregateError", "InternalError"];

  function finish(text) {
    if (finished || !bridge) {
      return;
    }
    finished = true;
    try {
      bridge.post(text);
    } catch (ignored) {
      // The app times the run out instead.
    }
  }

  // Only an error class goes back, never a message that could quote player code or a value.
  function errorClass(error) {
    var name = error && typeof error.name === "string" ? error.name : "Error";
    return /^[A-Za-z]{1,40}$/.test(name) ? name : "Error";
  }

  // A worker error event's message can quote player code or a value, so it never goes back: only
  // one of a fixed set of classes, chosen from it. A stack overflow or an out of memory first, then
  // a standard class the message starts with; anything else is "WorkerError". The "Worker" prefix
  // keeps the class apart from the core's own: the app never takes it for an environment error
  // (OwnSolverEnvironmentErrors), so it never sends the job to the page environment.
  function workerErrorClass(event) {
    var message = event && typeof event.message === "string" ? event.message : "";
    if (/maximum call stack size exceeded|too much recursion/i.test(message)) {
      return "WorkerStackOverflow";
    }
    if (/out of memory|allocation failed/i.test(message)) {
      return "WorkerOutOfMemory";
    }
    var named = /^(?:uncaught\s+)?([A-Za-z]{1,40})(?::|$)/i.exec(message);
    if (named && WORKER_CLASSES.indexOf(named[1]) >= 0) {
      return "Worker" + named[1];
    }
    return "WorkerError";
  }

  function fail(reason) {
    finish(JSON.stringify({ type: "error", error: reason }));
  }

  function load(name) {
    return fetch(name, { cache: "no-store", credentials: "omit" }).then(function (response) {
      if (!response.ok) {
        throw new Error("load " + response.status);
      }
      return response.text();
    });
  }

  if (!bridge) {
    return;
  }
  if (typeof Worker !== "function" || typeof Blob !== "function" || typeof URL !== "function") {
    fail("NoWorker");
    return;
  }

  Promise.all([
    load("own-solver-worker.js"),
    load("meriyah.umd.min.js"),
    load("own.solver.core.js"),
    load("own-solver-input.json"),
  ]).then(function (parts) {
    // The worker is built from a blob so it makes no request of its own.
    var address = URL.createObjectURL(new Blob([parts[0]], { type: "text/javascript" }));
    var worker = new Worker(address);
    URL.revokeObjectURL(address);
    worker.onmessage = function (event) {
      worker.terminate();
      if (typeof event.data === "string") {
        finish(event.data);
      } else {
        fail("NoText");
      }
    };
    worker.onerror = function (event) {
      if (event && typeof event.preventDefault === "function") {
        event.preventDefault();
      }
      var reason = workerErrorClass(event);
      worker.terminate();
      fail(reason);
    };
    // A reply that could not be deserialised: the job failed, and the page says so at once.
    worker.onmessageerror = function () {
      worker.terminate();
      fail("WorkerMessageError");
    };
    worker.postMessage({ lib: parts[1], core: parts[2], input: parts[3] });
  }).catch(function (error) {
    fail(errorClass(error));
  });
})();
