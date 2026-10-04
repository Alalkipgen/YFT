/*
 * YFT proof-of-origin host page.
 *
 * Runs YouTube's own BotGuard interpreter and program, exactly as YouTube's web player runs
 * them before it streams, and hands back only the values the app asks for: the BotGuard
 * answer, then proof-of-origin tokens for single video IDs. The challenge is served by the app
 * from memory on this page's own origin; the page makes no other request, and the app refuses
 * every address it does not serve itself.
 *
 * Replies are JSON {"id": n, "value": string|null, "error": code|null}. Error codes name the
 * step that failed and never carry page or script text.
 */
(function () {
  "use strict";

  var bridge = window.YftPoTokenBridge;
  var SETUP_TIMEOUT_MS = 10000;
  var SNAPSHOT_TIMEOUT_MS = 20000;
  var MINT_TIMEOUT_MS = 10000;
  var state = { signals: null, minter: null };

  function reply(id, value, error) {
    if (!bridge) {
      return;
    }
    try {
      bridge.post(JSON.stringify({ id: id, value: value, error: error }));
    } catch (ignored) {
      // The app times the step out instead.
    }
  }

  function step(id, code, task) {
    Promise.resolve()
      .then(task)
      .then(
        function (value) {
          if (typeof value === "string" && value.length > 0) {
            reply(id, value, null);
          } else {
            reply(id, null, code);
          }
        },
        function () {
          reply(id, null, code);
        }
      );
  }

  function withTimeout(promise, millis) {
    return new Promise(function (resolve, reject) {
      var timer = setTimeout(function () {
        reject(new Error("timeout"));
      }, millis);
      promise.then(
        function (value) {
          clearTimeout(timer);
          resolve(value);
        },
        function (error) {
          clearTimeout(timer);
          reject(error);
        }
      );
    });
  }

  function bytesFromBase64(text) {
    var normal = text.replace(/-/g, "+").replace(/_/g, "/").replace(/\./g, "=");
    var binary = atob(normal);
    var bytes = new Uint8Array(binary.length);
    for (var i = 0; i < binary.length; i += 1) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
  }

  function webSafeBase64(bytes) {
    var binary = "";
    for (var i = 0; i < bytes.length; i += 1) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_");
  }

  function noop() {}

  function snapshot() {
    return fetch("challenge.json", { cache: "no-store" })
      .then(function (response) {
        return response.json();
      })
      .then(function (challenge) {
        if (
          typeof challenge.interpreter !== "string" ||
          typeof challenge.program !== "string" ||
          typeof challenge.globalName !== "string"
        ) {
          throw new Error("challenge");
        }
        // YouTube's own interpreter defines the BotGuard machine under the challenge's name.
        new Function(challenge.interpreter)();
        var machine = window[challenge.globalName];
        if (!machine || typeof machine.a !== "function") {
          throw new Error("machine");
        }
        var functions = new Promise(function (resolve) {
          machine.a(
            challenge.program,
            function (asyncSnapshot) {
              resolve(asyncSnapshot);
            },
            true,
            undefined,
            noop,
            [[], []],
            undefined,
            false,
            [noop, noop, noop, noop, noop]
          );
        });
        return withTimeout(functions, SETUP_TIMEOUT_MS);
      })
      .then(function (asyncSnapshot) {
        if (typeof asyncSnapshot !== "function") {
          throw new Error("machine");
        }
        state.signals = [];
        var answer = new Promise(function (resolve) {
          asyncSnapshot(resolve, [undefined, undefined, state.signals, undefined]);
        });
        return withTimeout(answer, SNAPSHOT_TIMEOUT_MS);
      });
  }

  function createMinter(integrityToken) {
    var getMinter = state.signals && state.signals[0];
    if (typeof getMinter !== "function") {
      return Promise.reject(new Error("minter"));
    }
    return withTimeout(Promise.resolve(getMinter(bytesFromBase64(integrityToken))), MINT_TIMEOUT_MS)
      .then(function (minter) {
        if (typeof minter !== "function") {
          throw new Error("minter");
        }
        state.minter = minter;
        return "ready";
      });
  }

  function mint(binding) {
    if (typeof state.minter !== "function") {
      return Promise.reject(new Error("minter"));
    }
    var minted = state.minter(new TextEncoder().encode(binding));
    return withTimeout(Promise.resolve(minted), MINT_TIMEOUT_MS).then(function (bytes) {
      if (!(bytes instanceof Uint8Array) || bytes.length === 0) {
        throw new Error("mint");
      }
      return webSafeBase64(bytes);
    });
  }

  window.yftPoToken = Object.freeze({
    snapshot: function (id) {
      step(id, "snapshot", snapshot);
    },
    createMinter: function (id, integrityToken) {
      step(id, "minter", function () {
        return createMinter(String(integrityToken));
      });
    },
    mint: function (id, binding) {
      step(id, "mint", function () {
        return mint(String(binding));
      });
    },
  });

  reply(0, "loaded", null);
})();
