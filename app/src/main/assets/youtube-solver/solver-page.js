/*
 * YFT player-script host page.
 *
 * Loads the bundled solver and the job input from the app's own origin, runs the solver in a
 * dedicated worker and hands the solver's JSON output to the app's bridge. The page makes no
 * other request; the app refuses every address it does not serve itself.
 */
(function () {
  "use strict";

  var bridge = window.YftSolverBridge;
  var finished = false;

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

  function describe(error) {
    var text = error && error.message ? error.message : String(error);
    return text.slice(0, 500);
  }

  function fail(error) {
    finish(JSON.stringify({ type: "error", error: describe(error) }));
  }

  function load(name) {
    return fetch(name, { cache: "no-store", credentials: "omit" }).then(function (response) {
      if (!response.ok) {
        throw new Error("Could not load " + name + " (" + response.status + ")");
      }
      return response.text();
    });
  }

  if (!bridge) {
    return;
  }
  if (typeof Worker !== "function" || typeof Blob !== "function" || typeof URL !== "function") {
    fail("This WebView cannot run a worker");
    return;
  }

  Promise.all([
    load("solver-worker.js"),
    load("yt.solver.lib.min.js"),
    load("yt.solver.core.min.js"),
    load("solver-input.json"),
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
        fail("The solver returned no text");
      }
    };
    worker.onerror = function (event) {
      if (event && typeof event.preventDefault === "function") {
        event.preventDefault();
      }
      worker.terminate();
      fail(event && event.message ? event.message : "The solver worker failed");
    };
    worker.postMessage({ lib: parts[1], core: parts[2], input: parts[3] });
  }).catch(fail);
})();
