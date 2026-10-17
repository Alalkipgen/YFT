/*
 * YFT own solver page environment, host page (Master, Phase 1.2 N4, strategy E).
 *
 * Strategy E runs the own core in a real page (a real DOM) for players whose code needs browser
 * objects a worker lacks. The player's code never runs here: it runs in a sandboxed iframe
 * (page-frame.html, sandbox="allow-scripts": an opaque origin with no cookies, no storage and no
 * access to this page). This page
 *   1. loads the vendored parser, the own core and the job from the app's own origin,
 *   2. hands them, with a fresh random token, to the frame once the frame says it is ready, and
 *   3. passes the frame's first reply that carries the token to the app.
 * The app's reply object (window.yftPageSolver, androidx.webkit addWebMessageListener) exists
 * only in frames of the app's own origin, so the frame never sees it; the app also accepts
 * replies only from this main frame. Job and reply follow OwnSolverProtocol.kt (same as the
 * worker); only error classes go back, never a message.
 */
(function () {
  "use strict";

  var READY = "yft-page-frame-ready";
  var MAX_REPLY_CHARS = 12 * 1024 * 1024;

  var bridge = window.yftPageSolver;
  var finished = false;

  function finish(text) {
    if (finished) {
      return;
    }
    finished = true;
    try {
      bridge.postMessage(text);
    } catch (ignored) {
      // The app times the run out instead.
    }
  }

  // Only an error class goes back, never a message that could quote player code or a value.
  function errorClass(error) {
    var name = error && typeof error.name === "string" ? error.name : "Error";
    return /^[A-Za-z]{1,40}$/.test(name) ? name : "Error";
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

  function token() {
    var bytes = new Uint8Array(16);
    window.crypto.getRandomValues(bytes);
    var text = "";
    for (var i = 0; i < bytes.length; i += 1) {
      text += (bytes[i] < 16 ? "0" : "") + bytes[i].toString(16);
    }
    return text;
  }

  if (!bridge || typeof bridge.postMessage !== "function") {
    return;
  }
  if (typeof fetch !== "function" || !window.crypto || typeof window.crypto.getRandomValues !== "function") {
    fail("NoPage");
    return;
  }

  var secret = token();
  var parts = null;
  var frameReady = false;
  var sent = false;
  var frame = document.createElement("iframe");
  // Sandboxed before it has an address or a parent, so no document ever runs in it unsandboxed.
  frame.setAttribute("sandbox", "allow-scripts");
  frame.setAttribute("referrerpolicy", "no-referrer");
  frame.style.display = "none";

  function send() {
    if (sent || finished || !frameReady || !parts) {
      return;
    }
    sent = true;
    // The frame's origin is opaque, so "*" is the only target that reaches it; it receives only
    // the job, which is the frame's to run anyway.
    frame.contentWindow.postMessage({ token: secret, lib: parts[0], core: parts[1], input: parts[2] }, "*");
    parts = null;
  }

  function onMessage(event) {
    if (finished || !frame.contentWindow || event.source !== frame.contentWindow) {
      return;
    }
    var data = event.data;
    if (!sent) {
      if (data === READY) {
        frameReady = true;
        send();
      }
      return;
    }
    // Player code in the frame can post too; it never holds the token.
    if (!data || typeof data !== "object" || data.token !== secret) {
      return;
    }
    window.removeEventListener("message", onMessage);
    if (typeof data.reply !== "string") {
      fail("NoText");
    } else if (data.reply.length > MAX_REPLY_CHARS) {
      fail("TooLong");
    } else {
      finish(data.reply);
    }
  }

  window.addEventListener("message", onMessage);
  frame.src = "page-frame.html";
  document.body.appendChild(frame);

  Promise.all([
    load("meriyah.umd.min.js"),
    load("own.solver.core.js"),
    load("page-env-input.json"),
  ]).then(function (loaded) {
    parts = loaded;
    send();
  }).catch(function (error) {
    fail(errorClass(error));
  });
})();
