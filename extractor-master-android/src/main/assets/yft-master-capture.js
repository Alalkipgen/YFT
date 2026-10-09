(function () {
  "use strict";
  if (window.top !== window || window.__yftMasterCaptureV1) return;
  const MAX_BODY = 65536, MAX_PACKET = 262144, MAX_URL = 8192;
  const state = { generation: -1, page: "", requests: [], payloads: [], activeReads: 0 };
  const originalFetch = window.fetch;
  const originalOpen = XMLHttpRequest.prototype.open;
  const originalSend = XMLHttpRequest.prototype.send;
  const xhrState = new WeakMap(), xhrListening = new WeakSet(), encrypted = new WeakSet();
  let disposed = false;
  function address(value) {
    try {
      const u = new URL(value, location.href);
      return u.protocol === "https:" && !u.username && !u.password &&
        u.href.length <= MAX_URL ? u.href : null;
    } catch (_) { return null; }
  }
  function media(url, mime) {
    return /^(video|audio)\//i.test(mime || "") ||
      /mpegurl|dash\+xml/i.test(mime || "") ||
      /\.(mp4|webm|m4a|mp3|m3u8|mpd)(?:[?#]|$)/i.test(url || "") ||
      /[?&]mime=(video|audio)(?:%2f|\/)/i.test(url || "");
  }
  function note(url, mime, generation, preview) {
    url = address(url);
    if (disposed || generation !== state.generation || !url || !media(url, mime)) return;
    state.requests.push({ url: url, mime: (mime || "").slice(0, 128), preview: !!preview });
    if (state.requests.length > 200) state.requests.shift();
  }
  function payload(text, generation) {
    if (disposed || generation !== state.generation || typeof text !== "string") return;
    if (text.length > MAX_BODY || state.payloads.length >= 8) return;
    if (state.payloads.indexOf(text) < 0) state.payloads.push(text);
  }
  // Copy data descriptors only: no getters/toJSON, bounded depth, nodes and string lengths.
  function boundedJson(value) {
    let nodes = 0, characters = 0;
    function copy(v, depth) {
      if (++nodes > 4000 || depth > 20) throw new Error("bounded");
      if (v == null || typeof v === "boolean") return v;
      if (typeof v === "number") return Number.isFinite(v) ? v : null;
      if (typeof v === "string") {
        characters += v.length;
        if (v.length > MAX_URL || characters > MAX_BODY) throw new Error("bounded");
        return v;
      }
      if (typeof v !== "object") return undefined;
      const out = Array.isArray(v) ? [] : {};
      const keys = Object.keys(v);
      if (keys.length > 200) throw new Error("bounded");
      keys.forEach(function (key) {
        const d = Object.getOwnPropertyDescriptor(v, key);
        if (d && Object.prototype.hasOwnProperty.call(d, "value")) {
          const item = copy(d.value, depth + 1);
          if (item !== undefined) out[key] = item;
        }
      });
      return out;
    }
    try {
      const text = JSON.stringify(copy(value, 0));
      return text && text.length <= MAX_BODY ? text : null;
    } catch (_) { return null; }
  }
  async function readClone(response, generation) {
    if (!/application\/(?:[^;]*\+)?json/i.test(response.headers.get("content-type") || "")) return;
    const length = Number(response.headers.get("content-length"));
    if (length > MAX_BODY || generation !== state.generation) return;
    const clone = response.clone();
    if (!clone.body || !clone.body.getReader || typeof TextDecoder === "undefined") return;
    const reader = clone.body.getReader(), decoder = new TextDecoder();
    let bytes = 0, text = "";
    const timer = setTimeout(function () { reader.cancel().catch(function () {}); }, 1500);
    try {
      while (generation === state.generation && !disposed) {
        const part = await reader.read();
        if (part.done) { text += decoder.decode(); payload(text, generation); break; }
        bytes += part.value.byteLength;
        if (bytes > MAX_BODY) break;
        text += decoder.decode(part.value, { stream: true });
        if (text.length > MAX_BODY) break;
      }
    } finally {
      clearTimeout(timer);
      reader.cancel().catch(function () {});
    }
  }
  function observedFetch() {
    const generation = state.generation;
    const promise = originalFetch.apply(this, arguments);
    let method = "GET";
    try {
      method = String((arguments[1] && arguments[1].method) ||
        (arguments[0] && arguments[0].method) || "GET").toUpperCase();
    } catch (_) { method = ""; }
    promise.then(function (response) {
      try {
        if (method === "GET") {
          note(response.url, response.headers.get("content-type"), generation, false);
        }
        if (state.payloads.length < 8 && state.activeReads < 2) {
          state.activeReads++;
          readClone(response, generation).finally(function () {
            state.activeReads--;
          }).catch(function () {});
        }
      } catch (_) {}
    }, function () {});
    return promise; // Preserve the page's original promise and response stream.
  }
  function observedOpen(method, url) {
    let verb = "";
    try { verb = String(method).toUpperCase(); } catch (_) {}
    xhrState.set(this, { url: url, method: verb, generation: state.generation });
    return originalOpen.apply(this, arguments);
  }
  function observedSend() {
    if (!xhrListening.has(this)) {
      xhrListening.add(this);
      this.addEventListener("load", function () {
        try {
          const request = xhrState.get(this);
          if (!request || request.generation !== state.generation) return;
          const mime = this.getResponseHeader("content-type") || "";
          if (request.method === "GET") {
            note(this.responseURL || request.url, mime, request.generation, false);
          }
          if (!/application\/(?:[^;]*\+)?json/i.test(mime)) return;
          if (this.responseType === "json") {
            const text = boundedJson(this.response);
            if (text) payload(text, request.generation);
          } else if (this.responseType === "" || this.responseType === "text") {
            const text = this.responseText;
            if (text.length <= MAX_BODY) payload(text, request.generation);
          }
        } catch (_) {}
      });
    }
    return originalSend.apply(this, arguments);
  }
  function onEncrypted(event) {
    if (event.target instanceof HTMLMediaElement) encrypted.add(event.target);
  }
  if (typeof originalFetch === "function") window.fetch = observedFetch;
  XMLHttpRequest.prototype.open = observedOpen;
  XMLHttpRequest.prototype.send = observedSend;
  document.addEventListener("encrypted", onEncrypted, true);
  function bind(generation, page) {
    if (disposed || location.href !== page) return false;
    if (state.generation === generation && state.page === page) return true;
    state.generation = generation; state.page = page;
    state.requests = []; state.payloads = [];
    return true;
  }
  function sample(generation, page) {
    if (disposed || generation !== state.generation || location.href !== page) return null;
    const videos = Array.from(document.querySelectorAll("video")).slice(0, 20);
    const visible = videos.map(function (v, index) {
      const r = v.getBoundingClientRect();
      const width = Math.max(0, Math.min(r.right, innerWidth) - Math.max(r.left, 0));
      const height = Math.max(0, Math.min(r.bottom, innerHeight) - Math.max(r.top, 0));
      return { v: v, index: index, area: width * height };
    }).filter(function (item) { return item.area >= 1600; });
    const playing = visible.filter(function (item) {
      return !item.v.paused && !item.v.ended && item.v.readyState >= 2;
    });
    const ranked = (playing.length ? playing : visible).sort(function (a, b) {
      return b.area - a.area;
    });
    const selected = ranked[0] && (!ranked[1] || ranked[0].area > ranked[1].area * 1.5)
      ? ranked[0] : null;
    let html = "";
    const chosen = selected ? [selected] : visible;
    chosen.forEach(function (item) {
      const v = item.v, text = v.outerHTML;
      if (text.length + html.length <= MAX_BODY) html += text;
      const preview = v.getAttribute("data-player-role") === "preview" ||
        v.getAttribute("data-ad") === "true";
      note(v.currentSrc || v.src, v.getAttribute("type"), generation, preview);
      Array.from(v.querySelectorAll("source")).slice(0, 16).forEach(function (source) {
        note(source.src, source.type, generation, preview);
      });
    });
    Array.from(document.querySelectorAll('meta[property^="og:video"]')).slice(0, 16)
      .forEach(function (meta) {
        if (html.length + meta.outerHTML.length <= MAX_BODY) html += meta.outerHTML;
      });
    Array.from(document.querySelectorAll(
      'script[type="application/json"],#__UNIVERSAL_DATA_FOR_REHYDRATION__,#SIGI_STATE'
    )).slice(0, 8).forEach(function (node) {
      if (node.textContent.length <= MAX_BODY) payload(node.textContent, generation);
    });
    ["ytInitialPlayerResponse", "_sharedData", "__INITIAL_STATE__"].forEach(function (name) {
      const d = Object.getOwnPropertyDescriptor(window, name);
      if (d && Object.prototype.hasOwnProperty.call(d, "value")) {
        const text = boundedJson(d.value);
        if (text) payload(text, generation);
      }
    });
    performance.getEntriesByType("resource").slice(-200).forEach(function (entry) {
      note(entry.name, null, generation, false);
    });
    const v = selected && selected.v;
    const packet = {
      generation: generation, pageUrl: page, html: html, payloads: [], requests: [],
      protected: !!(v && (v.mediaKeys || encrypted.has(v))),
      player: v ? {
        key: "video:" + selected.index, url: v.currentSrc || v.src || null,
        time: Number.isFinite(v.currentTime) ? v.currentTime : 0,
        ready: v.readyState, paused: v.paused, visible: true
      } : null
    };
    state.payloads.splice(0, 4).forEach(function (text) {
      packet.payloads.push(text);
      if (JSON.stringify(packet).length > MAX_PACKET) packet.payloads.pop();
    });
    state.requests.slice(-64).forEach(function (request) {
      packet.requests.push(request);
      if (JSON.stringify(packet).length > MAX_PACKET) packet.requests.pop();
    });
    const encoded = JSON.stringify(packet);
    return encoded.length <= MAX_PACKET ? encoded : null;
  }
  function dispose() {
    disposed = true; state.requests = []; state.payloads = [];
    if (window.fetch === observedFetch) window.fetch = originalFetch;
    if (XMLHttpRequest.prototype.open === observedOpen) {
      XMLHttpRequest.prototype.open = originalOpen;
    }
    if (XMLHttpRequest.prototype.send === observedSend) {
      XMLHttpRequest.prototype.send = originalSend;
    }
    document.removeEventListener("encrypted", onEncrypted, true);
    delete window.__yftMasterCaptureV1;
  }
  Object.defineProperty(window, "__yftMasterCaptureV1", {
    value: { bind: bind, sample: sample, dispose: dispose }, configurable: true
  });
})();
