(function () {
  "use strict";
  if (window.top !== window || window.__yftMasterCaptureV1) return;
  const MAX_BODY = 65536, MAX_PACKET = 262144, MAX_URL = 8192;
  // VK's pack proof (2026-10-26): VK's player keeps the page's first `fetch` and fetches its
  // manifest as it starts, so on VK's pages this collector is also installed at document start
  // (WebViewPlaybackCapture.EARLY_ORIGINS). Until the first bind (generation -1) it keeps at
  // most MAX_EARLY media requests with the address the page had then; the document's first bind
  // takes those of its own page only, and nothing is read from a body before a bind. A
  // manifest (HLS/DASH) is kept apart from the pieces after it (at most MAX_MANIFESTS), so a
  // player's long run of pieces never pushes its manifest out of the packet.
  // V3's retest (2026-10-26): VK's player fetches its manifest once a document, so a later
  // lookup's bind on the same page found its pieces only. On VK's pages (bind's [carry]:
  // WebViewPlaybackCapture.EARLY_ORIGINS) the document's manifests, those fetched before the
  // first bind and those of any bind, are kept with the address they were fetched on (at most
  // MAX_MANIFESTS) and offered to every later bind of that address; nothing else is carried
  // over, and a reload is a new document. Every other site keeps none.
  const MAX_EARLY = 64, MAX_MANIFESTS = 16;
  const state = {
    generation: -1, page: "", requests: [], manifests: [], payloads: [], activeReads: 0,
    first: null, early: [], kept: [], carry: false
  };
  const originalFetch = window.fetch;
  const originalOpen = XMLHttpRequest.prototype.open;
  const originalSend = XMLHttpRequest.prototype.send;
  const originalSetHeader = XMLHttpRequest.prototype.setRequestHeader;
  const xhrState = new WeakMap(), xhrListening = new WeakSet(), encrypted = new WeakSet();
  // VK's pack proof (2026-10-26): m.vkvideo.ru's player is a <video> in an open shadow root,
  // which document.querySelectorAll never enters. The page's videos are the document's, then
  // those of its open shadow roots (nested ones too): at most MAX_VIDEOS, with at most
  // MAX_SCAN elements and MAX_ROOTS roots looked at per sample. A closed root stays unseen.
  const MAX_VIDEOS = 20, MAX_SCAN = 20000, MAX_ROOTS = 32;
  const shadowRoots = [], listened = new WeakSet();
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
  function manifest(url, mime) {
    return /mpegurl|dash\+xml/i.test(mime || "") || /\.(m3u8|mpd)(?:[?#]|$)/i.test(url || "");
  }
  // V3's retest 2 (2026-10-27): on a bind that carries manifests (VK's pages) a request counts
  // for the bind's page when it began on the same page: the same address, or one that differs in
  // its query only (VK adds `list=` and the like as the page settles), never another video (VK's
  // overlay address `?z=video…` names its video in its query). Every other bind: the same address.
  function samePage(href, page) {
    if (href === page) return true;
    if (!state.carry || typeof href !== "string" || typeof page !== "string") return false;
    try {
      const a = new URL(href), b = new URL(page);
      return a.origin === b.origin && a.pathname === b.pathname &&
        a.searchParams.get("z") === b.searchParams.get("z");
    } catch (_) { return false; }
  }
  // A manifest the page fetched on [href] (V3's retest): kept before the first bind (VK's
  // pages only have one then) and on a bind that carries them; at most MAX_MANIFESTS.
  function keep(entry, href) {
    if (!(state.generation === -1 || state.carry)) return;
    if (state.kept.some(function (item) {
      return item.href === href && item.entry.url === entry.url;
    })) return;
    state.kept.push({ entry: entry, href: href });
    if (state.kept.length > MAX_MANIFESTS) state.kept.shift();
  }
  function record(entry) {
    if (manifest(entry.url, entry.mime)) {
      keep(entry, state.page);
      if (state.manifests.some(function (item) { return item.url === entry.url; })) return;
      state.manifests.push(entry);
      if (state.manifests.length > MAX_MANIFESTS) state.manifests.shift();
      return;
    }
    state.requests.push(entry);
    if (state.requests.length > 200) state.requests.shift();
  }
  // [href]: the page's address when the request began (only a request begun before the first
  // bind needs it: it counts for that bind's page alone). [piece] (F1): the answer was a piece
  // of a file to a request that named no range (partial), so its address names that piece: it
  // is still the player's request, never a file row (MasterMainSelection).
  function note(url, mime, generation, preview, href, piece) {
    url = address(url);
    if (disposed || !url || !media(url, mime)) return;
    const entry = { url: url, mime: (mime || "").slice(0, 128), preview: !!preview };
    if (piece === true && !manifest(url, mime)) entry.piece = true;
    if (state.generation === -1) {
      if (generation !== -1 || typeof href !== "string") return;
      if (manifest(url, mime)) keep(entry, href);
      state.early.push({ entry: entry, href: href });
      if (state.early.length > MAX_EARLY) state.early.shift();
      return;
    }
    // A bind that carries manifests (VK's pages) also takes a manifest of its own address
    // that an earlier bind's request (or one begun before the first bind) answered late.
    if (generation === state.generation ||
        (generation === -1 && state.first === state.generation && samePage(href, state.page)) ||
        (state.carry && samePage(href, state.page) && manifest(url, mime))) {
      record(entry);
    }
  }
  function payload(text, generation, priority) {
    if (disposed || generation === -1 || generation !== state.generation) return;
    if (typeof text !== "string") return;
    if (text.length > MAX_BODY || state.payloads.indexOf(text) >= 0) return;
    if (priority) {
      state.payloads.unshift(text);
      if (state.payloads.length > 8) state.payloads.pop();
    } else if (state.payloads.length < 8) state.payloads.push(text);
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
  // A delivered DOM state may be large. Project only the actual permalink's item, <=64 KiB.
  // No new endpoint, ID, media URL, getter or toJSON execution is used.
  function focusedDomState(node, generation) {
    if (node.id !== "__UNIVERSAL_DATA_FOR_REHYDRATION__" && node.id !== "SIGI_STATE") return;
    const page = new URL(location.href), match = page.pathname.match(/\/video\/(\d+)/);
    if (!/(^|\.)tiktok\.com$/i.test(page.hostname) || !match) return;
    const text = node.textContent;
    if (typeof text !== "string" || text.length > 2 * 1024 * 1024) return;
    try {
      const root = JSON.parse(text), id = match[1];
      if (node.id === "SIGI_STATE") {
        const item = root.ItemModule && root.ItemModule[id];
        if (item && String(item.id) === id) {
          const compact = boundedJson(item);
          if (compact) payload(compact, generation, true);
        }
        return;
      }
      const scope = root.__DEFAULT_SCOPE__;
      if (!scope) return;
      ["webapp.video-detail", "webapp.reflow.video.detail"].forEach(function (field) {
        const detail = scope[field], item = detail && detail.itemInfo && detail.itemInfo.itemStruct;
        if (!detail || (item && String(item.id) !== id)) return;
        const projected = item ? {
          id: item.id, desc: item.desc, video: item.video,
          is_private: item.is_private, isDrm: item.isDrm,
          is_drm_protected: item.is_drm_protected
        } : null;
        const out = { __DEFAULT_SCOPE__: {} };
        out.__DEFAULT_SCOPE__[field] = {
          statusCode: detail.statusCode, statusCodeV2: detail.statusCodeV2,
          itemInfo: { itemStruct: projected }
        };
        const compact = boundedJson(out);
        if (compact) payload(compact, generation, true);
      });
    } catch (_) {}
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
  // F1 (V3's retest): an answer that is a piece of a file: a 206, unless the Content-Range it
  // shows is the whole file, or a Content-Range short of its total (a cross-origin answer shows
  // none unless its server exposes it). Only a request that named no range is asked about.
  function partial(status, range) {
    const m = /^\s*bytes\s+(\d+)-(\d+)\/(\d+)\s*$/i.exec(typeof range === "string" ? range : "");
    if (m) return Number(m[2]) - Number(m[1]) + 1 < Number(m[3]);
    return status === 206;
  }
  // Whether the page's fetch named a range (a Range header); unreadable counts as named.
  function rangeAsked(input, init) {
    try {
      const headers = init && init.headers !== undefined ? init.headers : input && input.headers;
      if (!headers) return false;
      if (typeof headers.has === "function") return headers.has("range");
      if (Array.isArray(headers)) {
        return headers.some(function (pair) {
          return Array.isArray(pair) && String(pair[0]).toLowerCase() === "range";
        });
      }
      return Object.keys(headers).some(function (name) { return name.toLowerCase() === "range"; });
    } catch (_) { return true; }
  }
  function observedFetch() {
    const generation = state.generation, href = String(location.href);
    const asked = rangeAsked(arguments[0], arguments[1]);
    const promise = originalFetch.apply(this, arguments);
    let method = "GET";
    try {
      method = String((arguments[1] && arguments[1].method) ||
        (arguments[0] && arguments[0].method) || "GET").toUpperCase();
    } catch (_) { method = ""; }
    promise.then(function (response) {
      try {
        if (method === "GET") {
          const piece = !asked && partial(response.status, response.headers.get("content-range"));
          note(response.url, response.headers.get("content-type"), generation, false, href, piece);
        }
        if (generation !== -1 && state.payloads.length < 8 && state.activeReads < 2) {
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
    xhrState.set(this, {
      url: url, method: verb, generation: state.generation, href: String(location.href),
      range: false
    });
    return originalOpen.apply(this, arguments);
  }
  function observedSetHeader(name) {
    try {
      const request = xhrState.get(this);
      if (request && String(name).toLowerCase() === "range") request.range = true;
    } catch (_) {}
    return originalSetHeader.apply(this, arguments);
  }
  // A cross-origin answer's unexposed Content-Range is left out here (no console noise).
  function xhrRange(xhr) {
    if (typeof xhr.getAllResponseHeaders !== "function") return null;
    const match = /(?:^|\n)\s*content-range\s*:\s*([^\r\n]*)/i.exec(xhr.getAllResponseHeaders() || "");
    return match ? match[1] : null;
  }
  function observedSend() {
    if (!xhrListening.has(this)) {
      xhrListening.add(this);
      this.addEventListener("load", function () {
        try {
          const request = xhrState.get(this);
          if (!request) return;
          const mime = this.getResponseHeader("content-type") || "";
          if (request.method === "GET") {
            const piece = !request.range && partial(this.status, xhrRange(this));
            note(this.responseURL || request.url, mime, request.generation, false, request.href,
              piece);
          }
          if (request.generation === -1 || request.generation !== state.generation) return;
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
  if (typeof originalSetHeader === "function") {
    XMLHttpRequest.prototype.setRequestHeader = observedSetHeader;
  }
  document.addEventListener("encrypted", onEncrypted, true);
  // [carry] (VK's pages, V3's retest): this bind is offered the manifests kept for [page].
  function bind(generation, page, carry) {
    if (disposed || location.href !== page) return false;
    if (state.generation === generation && state.page === page) return true;
    state.generation = generation; state.page = page; state.carry = carry === true;
    state.requests = []; state.manifests = []; state.payloads = [];
    const early = state.early;
    state.early = [];
    if (state.first === null) {
      state.first = generation;
      early.forEach(function (item) { if (samePage(item.href, page)) record(item.entry); });
    }
    if (state.carry) {
      state.kept.slice().forEach(function (item) { if (samePage(item.href, page)) record(item.entry); });
    } else {
      state.kept = [];
    }
    return true;
  }
  // A shadow root's media events (R2's "encrypted") do not reach the document's listener.
  function listen(root) {
    if (listened.has(root) || shadowRoots.length >= MAX_ROOTS) return;
    listened.add(root);
    shadowRoots.push(root);
    root.addEventListener("encrypted", onEncrypted, true);
  }
  function pageVideos() {
    const found = Array.from(document.querySelectorAll("video")).slice(0, MAX_VIDEOS);
    const queue = [document];
    let scanned = 0, roots = 0;
    for (let i = 0; i < queue.length && found.length < MAX_VIDEOS; i++) {
      const nodes = queue[i].querySelectorAll("*");
      for (let j = 0; j < nodes.length && scanned < MAX_SCAN && roots < MAX_ROOTS; j++) {
        scanned++;
        const root = nodes[j].shadowRoot;
        if (!root) continue;
        roots++;
        listen(root);
        queue.push(root);
        Array.from(root.querySelectorAll("video")).forEach(function (v) {
          if (found.length < MAX_VIDEOS && found.indexOf(v) < 0) found.push(v);
        });
      }
    }
    return found;
  }
  function sample(generation, page) {
    if (disposed || generation !== state.generation || location.href !== page) return null;
    const videos = pageVideos();
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
    )).sort(function (a, b) {
      function focused(node) {
        return node.id === "__UNIVERSAL_DATA_FOR_REHYDRATION__" || node.id === "SIGI_STATE";
      }
      return Number(focused(b)) - Number(focused(a));
    }).slice(0, 8).forEach(function (node) {
      focusedDomState(node, generation);
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
      // R2: EME keys or an encrypted event on any visible video stop Master for this page.
      // R7: so do keys attached at document start and any licence request on the page.
      protected: visible.some(function (item) {
        return !!(item.v.mediaKeys || encrypted.has(item.v) || hook("keyed", item.v));
      }) || !!(hook("eme") || {}).licensed,
      player: v ? {
        key: "video:" + selected.index, url: v.currentSrc || v.src || null,
        time: Number.isFinite(v.currentTime) ? v.currentTime : 0,
        ready: v.readyState, paused: v.paused, visible: true,
        duration: Number.isFinite(v.duration) && v.duration > 0 ? v.duration : null,
        width: v.videoWidth || null, height: v.videoHeight || null
      } : null
    };
    function add(request) {
      packet.requests.push(request);
      if (JSON.stringify(packet).length > MAX_PACKET) packet.requests.pop();
    }
    fedRequests(v).forEach(add);
    state.manifests.forEach(add);
    state.payloads.splice(0, 4).forEach(function (text) {
      packet.payloads.push(text);
      if (JSON.stringify(packet).length > MAX_PACKET) packet.payloads.pop();
    });
    state.requests.slice(-64).forEach(add);
    const encoded = JSON.stringify(packet);
    return encoded.length <= MAX_PACKET ? encoded : null;
  }
  // R7: the document-start MSE/EME hooks (yft-master-mse.js), when the WebView installed them.
  function hook(name, argument) {
    try {
      const hooks = window.__yftMasterMseV1;
      return hooks && typeof hooks[name] === "function" ? hooks[name](argument) : null;
    } catch (_) { return null; }
  }
  function fedRequests(video) {
    const facts = video ? hook("facts", video) : null;
    const found = [];
    if (!Array.isArray(facts)) return found;
    facts.slice(0, 6).forEach(function (fact) {
      if (!fact || !Array.isArray(fact.urls)) return;
      const mime = typeof fact.mime === "string" ? fact.mime.slice(0, 128) : "";
      const size = fact.kind === "video" && fact.width > 0 && fact.height > 0;
      fact.urls.slice(0, 16).forEach(function (value) {
        const url = address(value);
        if (!url || !media(url, mime) || found.length >= 32) return;
        if (found.some(function (item) { return item.url === url; })) return;
        found.push({
          url: url, mime: mime, preview: false, fed: true,
          width: size ? fact.width : null, height: size ? fact.height : null
        });
      });
    });
    return found;
  }
  function dispose() {
    disposed = true;
    state.requests = []; state.manifests = []; state.payloads = []; state.early = [];
    state.kept = [];
    if (window.fetch === observedFetch) window.fetch = originalFetch;
    if (XMLHttpRequest.prototype.open === observedOpen) {
      XMLHttpRequest.prototype.open = originalOpen;
    }
    if (XMLHttpRequest.prototype.send === observedSend) {
      XMLHttpRequest.prototype.send = originalSend;
    }
    if (XMLHttpRequest.prototype.setRequestHeader === observedSetHeader) {
      XMLHttpRequest.prototype.setRequestHeader = originalSetHeader;
    }
    document.removeEventListener("encrypted", onEncrypted, true);
    shadowRoots.splice(0).forEach(function (root) {
      root.removeEventListener("encrypted", onEncrypted, true);
    });
    delete window.__yftMasterCaptureV1;
  }
  Object.defineProperty(window, "__yftMasterCaptureV1", {
    value: { bind: bind, sample: sample, dispose: dispose }, configurable: true
  });
})();
