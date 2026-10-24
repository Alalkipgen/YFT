/* R7: MSE/EME metadata hooks, document start, top frame only, opt-in builds only.
 * Records what the page's own player declares and feeds: each SourceBuffer's type
 * (addSourceBuffer), the picture size of its init segment, and which delivered addresses
 * (XHR `response` / fetch `arrayBuffer()`) were appended to it. EME: a key attached to a video
 * (setMediaKeys) or a licence request (generateRequest) marks the page protected, so Master
 * stops with DRM_PROTECTED before any media check. Only observes: every original is called with
 * its own arguments and its own result is returned; nothing is blocked, changed or sent. */
(function () {
  "use strict";
  if (window.top !== window || window.__yftMasterMseV1) return;
  const host = String(location.hostname || "").toLowerCase().replace(/\.$/, "");
  // YouTube is Master's own module's (R6); nothing is hooked there.
  if (["youtube.com", "youtu.be", "youtube-nocookie.com"].some(function (d) {
    return host === d || host.endsWith("." + d);
  })) return;
  const MAX_URL = 8192, MAX_SOURCES = 8, MAX_BUFFERS = 6, MAX_URLS = 16, MAX_SCAN = 1048576;
  const sources = new Map();     // blob: address -> MediaSource
  const owned = new WeakMap();   // MediaSource -> [buffer facts]
  const facts = new WeakMap();   // SourceBuffer -> buffer facts
  const delivered = new WeakMap(); // ArrayBuffer -> address it came from
  const keyed = new WeakSet();
  const eme = { probes: 0, licensed: false };
  const restore = [];
  let disposed = false;

  function address(value) {
    try {
      const u = new URL(value, location.href);
      return u.protocol === "https:" && !u.username && !u.password &&
        u.href.length <= MAX_URL ? u.href : null;
    } catch (_) { return null; }
  }
  function wrap(target, name, make) {
    if (!target) return;
    const original = target[name];
    if (typeof original !== "function") return;
    const replaced = make(original);
    target[name] = replaced;
    restore.push(function () { if (target[name] === replaced) target[name] = original; });
  }
  function quietly(fn) { try { fn(); } catch (_) {} }

  // ---- init segment picture size (ISO-BMFF tkhd, WebM PixelWidth/PixelHeight) ----
  function u32(b, i) { return b[i] * 16777216 + (b[i + 1] << 16) + (b[i + 2] << 8) + b[i + 3]; }
  function boxes(b, from, to, visit) {
    let at = from;
    while (at + 8 <= to) {
      let size = u32(b, at), head = 8;
      const type = String.fromCharCode(b[at + 4], b[at + 5], b[at + 6], b[at + 7]);
      if (size === 1) {
        if (at + 16 > to) return;
        size = u32(b, at + 8) * 4294967296 + u32(b, at + 12); head = 16;
      } else if (size === 0) size = to - at;
      if (size < head || at + size > to) return;
      if (visit(type, at + head, at + size) === false) return;
      at += size;
    }
  }
  function mp4Size(b, limit) {
    let best = null;
    boxes(b, 0, limit, function (type, from, to) {
      if (type !== "moov") return true;
      boxes(b, from, to, function (t, f, e) {
        if (t !== "trak") return true;
        boxes(b, f, e, function (k, s, end) {
          if (k !== "tkhd" || end - s < 84) return true;
          const width = Math.floor(u32(b, end - 8) / 65536);
          const height = Math.floor(u32(b, end - 4) / 65536);
          if (width > 0 && height > 0 && (!best || width * height > best.width * best.height)) {
            best = { width: width, height: height };
          }
          return true;
        });
        return true;
      });
      return false;
    });
    return best;
  }
  function vint(b, at, keepMarker) {
    const first = b[at];
    if (first === undefined || first === 0) return null;
    let length = 1, mask = 0x80;
    while (!(first & mask)) { mask >>= 1; length++; }
    if (length > 8 || at + length > b.length) return null;
    let value = keepMarker ? first : first & (mask - 1), unknown = value === mask - 1;
    for (let i = 1; i < length; i++) {
      value = value * 256 + b[at + i];
      if (b[at + i] !== 0xff) unknown = false;
    }
    return { value: value, length: length, unknown: !keepMarker && unknown };
  }
  function elements(b, from, to, visit) {
    let at = from;
    while (at < to) {
      const id = vint(b, at, true); if (!id) return;
      const size = vint(b, at + id.length, false); if (!size) return;
      const start = at + id.length + size.length;
      const end = size.unknown ? to : start + size.value;
      if (end > to && !size.unknown) return;
      if (visit(id.value, start, Math.min(end, to)) === false) return;
      if (size.unknown) return;
      at = end;
    }
  }
  function uint(b, from, to) {
    if (to - from < 1 || to - from > 4) return 0;
    let value = 0;
    for (let i = from; i < to; i++) value = value * 256 + b[i];
    return value;
  }
  function webmSize(b, limit) {
    if (u32(b, 0) !== 0x1A45DFA3) return null;
    let found = null;
    elements(b, 0, limit, function (id, from, to) {
      if (id !== 0x18538067) return true; // Segment
      elements(b, from, to, function (child, f, e) {
        if (child === 0x1F43B675) return false; // Cluster: the init part is over
        if (child !== 0x1654AE6B) return true; // Tracks
        elements(b, f, e, function (entry, s, end) {
          if (entry !== 0xAE) return true; // TrackEntry
          elements(b, s, end, function (part, ps, pe) {
            if (part !== 0xE0) return true; // Video
            let width = 0, height = 0;
            elements(b, ps, pe, function (field, vs, ve) {
              if (field === 0xB0) width = uint(b, vs, ve);
              if (field === 0xBA) height = uint(b, vs, ve);
              return true;
            });
            if (width > 0 && height > 0 && width <= 16384 && height <= 16384 &&
                (!found || width * height > found.width * found.height)) {
              found = { width: width, height: height };
            }
            return true;
          });
          return true;
        });
        return false;
      });
      return false;
    });
    return found;
  }
  function pictureSize(data) {
    let bytes = null;
    if (data instanceof ArrayBuffer) bytes = new Uint8Array(data);
    else if (data && data.buffer instanceof ArrayBuffer) {
      bytes = new Uint8Array(data.buffer, data.byteOffset || 0, data.byteLength);
    }
    if (!bytes || bytes.length < 16) return null;
    const limit = Math.min(bytes.length, MAX_SCAN);
    return mp4Size(bytes, limit) || webmSize(bytes, limit);
  }

  // ---- MediaSource / SourceBuffer ----
  function kinds() {
    return ["MediaSource", "ManagedMediaSource", "WebKitMediaSource"]
      .map(function (name) { return window[name]; })
      .filter(function (type) { return typeof type === "function"; });
  }
  function isSource(value) {
    return kinds().some(function (type) { return value instanceof type; });
  }
  wrap(window.URL, "createObjectURL", function (original) {
    return function (object) {
      const url = original.apply(this, arguments);
      quietly(function () {
        if (disposed || !isSource(object) || typeof url !== "string") return;
        sources.set(url, object);
        if (sources.size > MAX_SOURCES) sources.delete(sources.keys().next().value);
      });
      return url;
    };
  });
  kinds().forEach(function (type) {
    wrap(type.prototype, "addSourceBuffer", function (original) {
      return function (mime) {
        const buffer = original.apply(this, arguments);
        const source = this;
        quietly(function () {
          if (disposed || !buffer) return;
          const text = String(mime).slice(0, 256);
          const list = owned.get(source) || [];
          if (list.length >= MAX_BUFFERS) return;
          const fact = {
            mime: text,
            kind: /^video\//i.test(text) ? "video" : /^audio\//i.test(text) ? "audio" : "",
            width: null, height: null, urls: []
          };
          list.push(fact); owned.set(source, list); facts.set(buffer, fact);
        });
        return buffer;
      };
    });
  });
  if (typeof window.SourceBuffer === "function") {
    wrap(window.SourceBuffer.prototype, "appendBuffer", function (original) {
      return function (data) {
        const buffer = this;
        quietly(function () {
          const fact = !disposed && facts.get(buffer);
          if (!fact) return;
          const whole = data instanceof ArrayBuffer ? data : data && data.buffer;
          const url = whole && delivered.get(whole);
          if (url && fact.urls.indexOf(url) < 0 && fact.urls.length < MAX_URLS) {
            fact.urls.push(url);
          }
          if (fact.kind === "video" && !fact.width) {
            const size = pictureSize(data);
            if (size) { fact.width = size.width; fact.height = size.height; }
          }
        });
        return original.apply(this, arguments);
      };
    });
  }

  // ---- which address delivered the bytes (never read, only remembered by identity) ----
  // XHR: remembered when the page itself reads `response`, before it can append it.
  if (typeof XMLHttpRequest === "function") {
    const proto = XMLHttpRequest.prototype;
    const original = Object.getOwnPropertyDescriptor(proto, "response");
    if (original && typeof original.get === "function" && original.configurable) {
      const getter = function () {
        const value = original.get.call(this);
        const xhr = this;
        quietly(function () {
          if (disposed || !(value instanceof ArrayBuffer) || delivered.has(value)) return;
          const url = address(xhr.responseURL);
          if (url) delivered.set(value, url);
        });
        return value;
      };
      Object.defineProperty(proto, "response", {
        get: getter, set: original.set, enumerable: original.enumerable, configurable: true
      });
      restore.push(function () {
        const now = Object.getOwnPropertyDescriptor(proto, "response");
        if (now && now.get === getter) Object.defineProperty(proto, "response", original);
      });
    }
  }
  if (typeof Response === "function") {
    wrap(Response.prototype, "arrayBuffer", function (original) {
      return function () {
        const promise = original.apply(this, arguments);
        const url = address(this.url);
        quietly(function () {
          if (!url || !promise || typeof promise.then !== "function") return;
          promise.then(function (buffer) {
            if (!disposed && buffer instanceof ArrayBuffer) delivered.set(buffer, url);
          }, function () {});
        });
        return promise;
      };
    });
  }

  // ---- EME: a key on a video or a licence request is protection; a bare query is not ----
  if (window.navigator && typeof navigator.requestMediaKeySystemAccess === "function") {
    wrap(navigator, "requestMediaKeySystemAccess", function (original) {
      return function () {
        if (!disposed && eme.probes < 1000) eme.probes++;
        return original.apply(this, arguments);
      };
    });
  }
  if (typeof window.HTMLMediaElement === "function") {
    wrap(window.HTMLMediaElement.prototype, "setMediaKeys", function (original) {
      return function (keys) {
        const element = this;
        quietly(function () { if (!disposed && keys) keyed.add(element); });
        return original.apply(this, arguments);
      };
    });
  }
  if (typeof window.MediaKeySession === "function") {
    wrap(window.MediaKeySession.prototype, "generateRequest", function (original) {
      return function () {
        if (!disposed) eme.licensed = true;
        return original.apply(this, arguments);
      };
    });
  }

  function videoFacts(video) {
    if (disposed || !video) return null;
    const source = sources.get(video.currentSrc || video.src || "");
    const list = source && owned.get(source);
    if (!list) return null;
    return list.map(function (fact) {
      return {
        mime: fact.mime, kind: fact.kind, width: fact.width, height: fact.height,
        urls: fact.urls.slice()
      };
    });
  }
  function dispose() {
    disposed = true;
    restore.splice(0).reverse().forEach(function (undo) { quietly(undo); });
    sources.clear();
    delete window.__yftMasterMseV1;
  }
  Object.defineProperty(window, "__yftMasterMseV1", {
    value: {
      facts: videoFacts,
      keyed: function (video) { return !disposed && !!video && keyed.has(video); },
      eme: function () { return { probes: eme.probes, licensed: eme.licensed }; },
      dispose: dispose
    },
    configurable: true
  });
})();
