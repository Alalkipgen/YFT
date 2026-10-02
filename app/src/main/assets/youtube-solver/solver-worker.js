/*
 * YFT player-script worker.
 *
 * Evaluates the bundled yt-dlp ejs solver and runs it over one job. A worker has no document
 * and no navigable location, so nothing the site's player functions do here can navigate the
 * host page.
 */
"use strict";

// The solver's setup assigns `location` before running the player's functions. An own property
// keeps that assignment inside this worker.
try {
  Object.defineProperty(self, "location", {
    value: new URL("https://www.youtube.com/watch"),
    writable: true,
    configurable: true,
    enumerable: true,
  });
} catch (ignored) {
  // The assignment then fails silently in the solver's non-strict setup code.
}

self.onmessage = function (event) {
  var job = event.data || {};
  var reply;
  try {
    // Indirect eval runs each bundle as global code, exactly as a script tag would.
    var evaluate = eval;
    evaluate(String(job.lib));
    evaluate("var meriyah = lib.meriyah, astring = lib.astring;");
    evaluate(String(job.core));
    reply = JSON.stringify(jsc(JSON.parse(String(job.input))));
  } catch (error) {
    var text = error && error.message ? error.message : String(error);
    reply = JSON.stringify({ type: "error", error: text.slice(0, 500) });
  }
  self.postMessage(reply);
};
