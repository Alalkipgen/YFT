const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const source = fs.readFileSync(
  require("node:path").join(__dirname, "../../main/assets/yft-master-capture.js"), "utf8"
);

function environment(options = {}) {
  class Media {}
  class Xhr {
    open() { return "open-result"; }
    send() { return "send-result"; }
    setRequestHeader() { return "header-result"; }
    addEventListener(name, listener) { this.listener = listener; }
    getResponseHeader() { return "application/json"; }
  }
  const originalFetch = options.fetch || (() => Promise.resolve(response("{}")));
  const document = {
    listeners: {},
    addEventListener(name, fn) { this.listeners[name] = fn; },
    removeEventListener(name) { delete this.listeners[name]; },
    querySelectorAll(selector) {
      if (selector === "video") return options.videos || [];
      if (selector === "*") return options.elements || [];
      if (selector.startsWith("script")) return options.scripts || [];
      return [];
    }
  };
  const env = {
    location: { href: options.page || "https://page.test/watch" }, document,
    XMLHttpRequest: Xhr, HTMLMediaElement: Media, URL, TextDecoder,
    setTimeout, clearTimeout, innerWidth: 400, innerHeight: 600,
    performance: { getEntriesByType: () => [] }, fetch: originalFetch,
  };
  env.window = env; env.top = env;
  vm.runInNewContext(source, env);
  if (options.bind !== false) env.__yftMasterCaptureV1.bind(1, env.location.href);
  return { env, originalFetch, originalOpen: Xhr.prototype.open, document, Media };
}

function response(text, url = "https://api.test/data", mime = "application/json") {
  return {
    url, headers: { get: name => name === "content-type" ? mime : String(text.length) },
    clone() {
      let sent = false;
      return { body: { getReader: () => ({
        read: async () => sent ? { done: true } :
          (sent = true, { done: false, value: new TextEncoder().encode(text) }),
        cancel: async () => {},
      }) } };
    }
  };
}

function packet(env) {
  return JSON.parse(env.__yftMasterCaptureV1.sample(
    env.location.href.endsWith("/next") ? 2 : 1, env.location.href
  ));
}
const settle = () => new Promise(resolve => setTimeout(resolve, 30));

test("fetch retains the exact original promise and response", async () => {
  const result = response('{"video_url":"https://cdn.test/main.mp4"}');
  const original = Promise.resolve(result);
  const { env } = environment({ fetch: () => original });
  assert.equal(env.fetch("https://api.test/data"), original);
  assert.equal(await original, result);
  await settle();
  assert.equal(packet(env).payloads.length, 1);
});

test("XHR preserves open and send return values while reading delivered JSON", () => {
  const { env } = environment();
  const xhr = new env.XMLHttpRequest();
  assert.equal(xhr.open("GET", "https://api.test/data"), "open-result");
  assert.equal(xhr.send(), "send-result");
  xhr.responseURL = "https://api.test/data"; xhr.responseType = "";
  xhr.responseText = '{"video_url":"https://cdn.test/main.mp4"}';
  xhr.listener.call(xhr);
  assert.equal(packet(env).payloads.length, 1);
});

test("a response started before SPA navigation cannot populate the new scope", async () => {
  let complete;
  const pending = new Promise(resolve => { complete = resolve; });
  const { env } = environment({ fetch: () => pending });
  env.fetch("https://api.test/data");
  env.location.href = "https://page.test/watch/next";
  env.__yftMasterCaptureV1.bind(2, env.location.href);
  complete(response('{"video_url":"https://cdn.test/old.mp4"}'));
  await settle();
  assert.equal(packet(env).payloads.length, 0);
  assert.equal(packet(env).requests.length, 0);
});

test("oversized JSON is not cloned or returned as a truncated invented payload", async () => {
  let clones = 0;
  const huge = response("x".repeat(65537));
  huge.clone = () => { clones++; throw new Error("must not clone"); };
  const { env } = environment({ fetch: () => Promise.resolve(huge) });
  await env.fetch("https://api.test/data"); await settle();
  assert.equal(clones, 0);
  assert.equal(packet(env).payloads.length, 0);
});

test("at most two JSON response clones can read concurrently", async () => {
  let clones = 0;
  const release = [];
  const result = response("{}");
  result.clone = () => {
    clones++;
    return { body: { getReader: () => ({
      read: () => new Promise(resolve => release.push(() => resolve({ done: true }))),
      cancel: async () => {},
    }) } };
  };
  const { env } = environment({ fetch: () => Promise.resolve(result) });
  await Promise.all(Array.from({ length: 6 }, () => env.fetch("https://api.test/data")));
  assert.equal(clones, 2);
  release.forEach(fn => fn()); await settle();
});

test("equally visible playing videos do not fabricate a selected player", () => {
  const video = url => ({
    currentSrc: url, src: url, paused: false, ended: false, readyState: 4, currentTime: 1,
    outerHTML: `<video src="${url}"></video>`,
    getBoundingClientRect: () => ({ left: 0, top: 0, right: 200, bottom: 100 }),
    getAttribute: () => null, querySelectorAll: () => [],
  });
  const { env } = environment({
    videos: [video("https://cdn.test/a.mp4"), video("https://cdn.test/b.mp4")]
  });
  const value = packet(env);
  assert.equal(value.player, null);
  assert.equal(value.requests.length, 2);
});

test("only explicit preview evidence marks a playing loop as preview", () => {
  const make = preview => ({
    currentSrc: "https://cdn.test/main.mp4", src: "", loop: true, muted: true,
    paused: false, ended: false, readyState: 4, currentTime: 1,
    outerHTML: '<video src="https://cdn.test/main.mp4"></video>',
    getBoundingClientRect: () => ({ left: 0, top: 0, right: 300, bottom: 200 }),
    getAttribute: key => key === "data-player-role" && preview ? "preview" : null,
    querySelectorAll: () => [],
  });
  assert.equal(packet(environment({ videos: [make(false)] }).env).requests[0].preview, false);
  assert.equal(packet(environment({ videos: [make(true)] }).env).requests[0].preview, true);
});

function playingVideo(url, extra = {}) {
  return Object.assign({
    currentSrc: url, src: url, paused: false, ended: false, readyState: 4, currentTime: 1,
    outerHTML: `<video src="${url}"></video>`,
    getBoundingClientRect: () => ({ left: 0, top: 0, right: 300, bottom: 200 }),
    getAttribute: () => null, querySelectorAll: () => [],
  }, extra);
}

test("R2: EME keys on the playing video mark the page protected", () => {
  const plain = packet(environment({ videos: [playingVideo("https://cdn.test/a.mp4")] }).env);
  assert.equal(plain.protected, false);
  const keyed = playingVideo("https://cdn.test/a.mp4", { mediaKeys: {} });
  assert.equal(packet(environment({ videos: [keyed] }).env).protected, true);
});

test("R2: an encrypted event on any visible video marks the page protected", () => {
  const options = {};
  const { env, document, Media } = environment(options);
  const make = url => Object.assign(new Media(), playingVideo(url));
  const first = make("https://cdn.test/a.mp4"), second = make("https://cdn.test/b.mp4");
  options.videos = [first, second];
  assert.equal(packet(env).protected, false);
  document.listeners.encrypted({ target: second });
  const value = packet(env);
  assert.equal(value.player, null); // Two equal players: no selection, still protected.
  assert.equal(value.protected, true);
});

test("R2: an encrypted event on a hidden video does not stop a visible one", () => {
  const options = {};
  const { env, document, Media } = environment(options);
  const shown = Object.assign(new Media(), playingVideo("https://cdn.test/a.mp4"));
  const hidden = Object.assign(new Media(), playingVideo("https://cdn.test/b.mp4", {
    getBoundingClientRect: () => ({ left: 0, top: 0, right: 0, bottom: 0 }),
  }));
  options.videos = [shown, hidden];
  document.listeners.encrypted({ target: hidden });
  assert.equal(packet(env).protected, false);
});

test("disposal restores owned hooks and removes the collector", () => {
  const { env, originalFetch, document } = environment();
  env.__yftMasterCaptureV1.dispose();
  assert.equal(env.fetch, originalFetch);
  assert.equal(env.__yftMasterCaptureV1, undefined);
  assert.equal(document.listeners.encrypted, undefined);
});

test("the collector has no credential reader native bridge or automatic player", () => {
  assert.doesNotMatch(source, /document\.cookie|localStorage|addJavascriptInterface|\.play\s*\(/);
});

test("POST media responses are not invented as replayable GET observations", async () => {
  const result = response("", "https://cdn.test/main.mp4", "video/mp4");
  const { env } = environment({ fetch: () => Promise.resolve(result) });
  await env.fetch("https://cdn.test/main.mp4", { method: "POST" });
  await settle();
  assert.equal(packet(env).requests.length, 0);
});

test("page-global getters are never invoked by payload collection", () => {
  const { env } = environment();
  let calls = 0;
  Object.defineProperty(env, "ytInitialPlayerResponse", {
    get() { calls++; return { video_url: "https://cdn.test/main.mp4" }; },
  });
  packet(env);
  assert.equal(calls, 0);
});

test("a large delivered TikTok state retains only the real focused item's video", () => {
  const page = "https://www.tiktok.com/@fixture/video/1234567890123456789";
  const id = "1234567890123456789";
  const state = { __DEFAULT_SCOPE__: {
    "webapp.video-detail": { statusCode: 0, itemInfo: { itemStruct: {
      id, desc: "offline transport fixture",
      video: { duration: 26, playAddr: "https://cdn.test/focused.mp4" }
    } } },
    unrelated: "x".repeat(70000)
  } };
  const scripts = [{
    id: "__UNIVERSAL_DATA_FOR_REHYDRATION__",
    textContent: JSON.stringify(state)
  }];
  const value = packet(environment({ page, scripts }).env);
  const bodies = value.payloads.map(text => JSON.parse(text));
  assert.equal(bodies.some(body => {
    const detail = body.__DEFAULT_SCOPE__?.["webapp.video-detail"];
    const item = detail?.itemInfo?.itemStruct;
    return item?.id === id && item.video.playAddr === "https://cdn.test/focused.mp4" &&
      detail.statusCode === 0;
  }), true);
  assert.equal(value.payloads.every(text => text.length <= 65536), true);
  assert.equal(value.player, null); // Payload discovery is never fabricated playback evidence.
});

test("focused-state compaction retains actual refusal and DRM fields", () => {
  const page = "https://www.tiktok.com/@fixture/video/1234567890123456789";
  const state = { __DEFAULT_SCOPE__: {
    "webapp.video-detail": { statusCode: 10101, itemInfo: { itemStruct: {
      id: "1234567890123456789", is_private: true, isDrm: true,
      video: { duration: 26, playAddr: "https://cdn.test/protected.mp4" }
    } } }, unrelated: "x".repeat(70000)
  } };
  const scripts = [{ id: "__UNIVERSAL_DATA_FOR_REHYDRATION__", textContent: JSON.stringify(state) }];
  const value = packet(environment({ page, scripts }).env);
  assert.equal(value.payloads.some(text => {
    const detail = JSON.parse(text).__DEFAULT_SCOPE__?.["webapp.video-detail"];
    return detail?.statusCode === 10101 && detail.itemInfo.itemStruct.is_private === true &&
      detail.itemInfo.itemStruct.isDrm === true;
  }), true);
  assert.equal(value.player, null);
});

test("focused TikTok state after eight unrelated JSON scripts is still captured", () => {
  const page = "https://www.tiktok.com/@fixture/video/1234567890123456789";
  const id = "1234567890123456789";
  const state = { __DEFAULT_SCOPE__: {
    "webapp.video-detail": { statusCode: 0, itemInfo: { itemStruct: {
      id, video: { duration: 26, playAddr: "https://cdn.test/focused.mp4" }
    } } }, unrelated: "x".repeat(70000)
  } };
  const scripts = Array.from({ length: 8 }, (_, index) => ({
    id: "unrelated-" + index, textContent: JSON.stringify({ unrelated: index })
  }));
  scripts.push({ id: "__UNIVERSAL_DATA_FOR_REHYDRATION__", textContent: JSON.stringify(state) });
  const value = packet(environment({ page, scripts }).env);
  assert.equal(value.payloads.some(text => {
    const item = JSON.parse(text).__DEFAULT_SCOPE__?.["webapp.video-detail"]?.itemInfo?.itemStruct;
    return item?.id === id && item.video.playAddr === "https://cdn.test/focused.mp4";
  }), true);
  assert.equal(value.payloads.length <= 8, true);
  assert.equal(value.payloads.every(text => text.length <= 65536), true);
  assert.equal(value.player, null);
});

// VK's pack proof (2026-10-26): m.vkvideo.ru plays in a <video> inside an open shadow root.
function shadowHost(videos) {
  const root = {
    listeners: {},
    addEventListener(name, fn) { this.listeners[name] = fn; },
    removeEventListener(name) { delete this.listeners[name]; },
    querySelectorAll: selector => selector === "video" ? videos : [],
  };
  return { shadowRoot: root };
}

test("VK: a player in an open shadow root is the page's player", () => {
  const preview = playingVideo("https://cdn.test/preview.mp4", {
    getBoundingClientRect: () => ({ left: 0, top: 0, right: 60, bottom: 40 }),
  });
  const player = playingVideo("blob:https://page.test/player", { duration: 4651.78 });
  const host = shadowHost([player]);
  const value = packet(environment({ videos: [preview], elements: [{}, host] }).env);
  assert.equal(value.player.key, "video:1");
  assert.equal(value.player.duration, 4651.78);
  assert.equal(value.player.paused, false);
});

test("VK: an encrypted event in a shadow root marks the page protected", () => {
  const options = {};
  const { env, Media } = environment(options);
  const player = Object.assign(new Media(), playingVideo("blob:https://page.test/player"));
  const host = shadowHost([player]);
  options.elements = [host];
  assert.equal(packet(env).protected, false);
  host.shadowRoot.listeners.encrypted({ target: player });
  assert.equal(packet(env).protected, true);
  env.__yftMasterCaptureV1.dispose();
  assert.equal(host.shadowRoot.listeners.encrypted, undefined);
});

const MPD = "https://media.test/?type=4&id=1";
const piece = n => "https://media.test/?type=2&id=1&bytes=" + n + "-" + (n + 9);

test("VK: a manifest fetched before the first bind is that page's, its JSON is not read", async () => {
  const answers = {
    [MPD]: response("<MPD/>", MPD, "application/dash+xml"),
    "https://api.test/data": response('{"video_url":"https://cdn.test/main.mp4"}'),
  };
  const { env } = environment({ bind: false, fetch: url => Promise.resolve(answers[url]) });
  await env.fetch(MPD);
  await env.fetch("https://api.test/data");
  await settle();
  assert.equal(env.__yftMasterCaptureV1.sample(1, env.location.href), null);
  assert.equal(env.__yftMasterCaptureV1.bind(1, env.location.href), true);
  const value = packet(env);
  assert.deepEqual(value.requests.map(r => r.url), [MPD]);
  assert.equal(value.payloads.length, 0);
});

test("VK: a request made on another address before the first bind is not taken", async () => {
  const answer = response("<MPD/>", MPD, "application/dash+xml");
  const { env } = environment({
    bind: false, page: "https://page.test/first", fetch: () => Promise.resolve(answer),
  });
  await env.fetch(MPD);
  await settle();
  env.location.href = "https://page.test/watch";
  assert.equal(env.__yftMasterCaptureV1.bind(1, env.location.href), true);
  assert.equal(packet(env).requests.length, 0);
});

test("VK: a request begun before the first bind counts for that bind only", async () => {
  const done = [];
  const { env } = environment({
    bind: false, fetch: () => new Promise(resolve => done.push(resolve)),
  });
  env.fetch(MPD);
  env.fetch(MPD + "&second");
  env.__yftMasterCaptureV1.bind(1, env.location.href);
  done[0](response("<MPD/>", MPD, "application/dash+xml"));
  await settle();
  assert.deepEqual(packet(env).requests.map(r => r.url), [MPD]);
  env.location.href = "https://page.test/watch/next";
  env.__yftMasterCaptureV1.bind(2, env.location.href);
  done[1](response("<MPD/>", MPD + "&second", "application/dash+xml"));
  await settle();
  assert.equal(packet(env).requests.length, 0);
});

test("VK: the player's manifest stays in the packet after a long run of its pieces", async () => {
  const { env } = environment({
    fetch: url => Promise.resolve(url === MPD ? response("<MPD/>", MPD, "application/dash+xml") :
      response("", url, "video/webm")),
  });
  await env.fetch(MPD);
  for (let n = 0; n < 100; n++) await env.fetch(piece(n * 10));
  await env.fetch(MPD);
  await settle();
  const urls = packet(env).requests.map(r => r.url);
  assert.equal(urls[0], MPD);
  assert.equal(urls.filter(url => url === MPD).length, 1);
  assert.equal(urls.length, 65);
  assert.equal(urls[64], piece(990));
});

// V3's retest (2026-10-26): Try again binds the same document and address with a new generation;
// VK's player fetched its manifest once, before the first bind. [carry]: VK's pages only.
const pieceAnswer = (url, mime, status, range) => ({
  url, status, headers: { get: name => name === "content-type" ? mime :
    name === "content-range" ? range || null : null },
  clone() { return { body: null }; },
});
const sampled = (env, generation) =>
  JSON.parse(env.__yftMasterCaptureV1.sample(generation, env.location.href));

test("VK: a later bind of the same page gets the manifests fetched before the first bind and during a bind", async () => {
  const MPD2 = MPD + "&quality=2";
  const answers = {
    [MPD]: response("<MPD/>", MPD, "application/dash+xml"),
    [MPD2]: response("<MPD/>", MPD2, "application/dash+xml"),
    "https://api.test/data": response('{"video_url":"https://cdn.test/main.mp4"}'),
  };
  const { env } = environment({
    bind: false, fetch: url => Promise.resolve(answers[url] || response("", url, "video/webm")),
  });
  await env.fetch(MPD);
  await settle();
  assert.equal(env.__yftMasterCaptureV1.bind(1, env.location.href, true), true);
  await env.fetch(MPD2);
  await env.fetch(piece(0));
  await env.fetch("https://api.test/data");
  await settle();
  const first = sampled(env, 1);
  assert.deepEqual(first.requests.map(r => r.url), [MPD, MPD2, piece(0)]);
  assert.equal(first.payloads.length, 1);
  // Try again: a new generation on the same page and document. Only the manifests come along.
  assert.equal(env.__yftMasterCaptureV1.bind(2, env.location.href, true), true);
  const again = sampled(env, 2);
  assert.deepEqual(again.requests.map(r => r.url), [MPD, MPD2]);
  assert.equal(again.payloads.length, 0);
  assert.equal(env.__yftMasterCaptureV1.bind(3, env.location.href, true), true);
  assert.deepEqual(sampled(env, 3).requests.map(r => r.url), [MPD, MPD2]);
  // A reload is a new document: its collector starts with nothing.
  env.__yftMasterCaptureV1.dispose();
  const reloaded = environment({ bind: false }).env;
  assert.equal(reloaded.__yftMasterCaptureV1.bind(4, reloaded.location.href, true), true);
  assert.equal(sampled(reloaded, 4).requests.length, 0);
});

test("VK: another address's manifest is never offered, its own address gets it back", async () => {
  const answer = response("<MPD/>", MPD, "application/dash+xml");
  const { env } = environment({
    bind: false, page: "https://page.test/first", fetch: () => Promise.resolve(answer),
  });
  assert.equal(env.__yftMasterCaptureV1.bind(1, env.location.href, true), true);
  await env.fetch(MPD);
  await settle();
  assert.deepEqual(sampled(env, 1).requests.map(r => r.url), [MPD]);
  env.location.href = "https://page.test/watch";
  assert.equal(env.__yftMasterCaptureV1.bind(2, env.location.href, true), true);
  assert.equal(sampled(env, 2).requests.length, 0);
  env.location.href = "https://page.test/first";
  assert.equal(env.__yftMasterCaptureV1.bind(3, env.location.href, true), true);
  assert.deepEqual(sampled(env, 3).requests.map(r => r.url), [MPD]);
});

test("VK: a carried bind takes its own address's manifest answered late, never another's", async () => {
  const done = [];
  const { env } = environment({
    bind: false, fetch: () => new Promise(resolve => done.push(resolve)),
  });
  env.fetch(MPD);
  env.__yftMasterCaptureV1.bind(1, env.location.href, true);
  env.fetch(MPD + "&second");
  assert.equal(env.__yftMasterCaptureV1.bind(2, env.location.href, true), true);
  done[0](response("<MPD/>", MPD, "application/dash+xml"));
  done[1](response("<MPD/>", MPD + "&second", "application/dash+xml"));
  await settle();
  assert.deepEqual(sampled(env, 2).requests.map(r => r.url), [MPD, MPD + "&second"]);
  env.fetch(MPD + "&third");
  env.location.href = "https://page.test/watch/next";
  assert.equal(env.__yftMasterCaptureV1.bind(3, env.location.href, true), true);
  done[2](response("<MPD/>", MPD + "&third", "application/dash+xml"));
  await settle();
  assert.equal(sampled(env, 3).requests.length, 0);
});

test("every other site: a later bind gets no manifest of an earlier one (as before)", async () => {
  const answer = response("<MPD/>", MPD, "application/dash+xml");
  const { env } = environment({ fetch: () => Promise.resolve(answer) });
  await env.fetch(MPD);
  await settle();
  assert.deepEqual(sampled(env, 1).requests.map(r => r.url), [MPD]);
  assert.equal(env.__yftMasterCaptureV1.bind(2, env.location.href), true);
  assert.equal(sampled(env, 2).requests.length, 0);
  assert.equal(env.__yftMasterCaptureV1.bind(3, env.location.href, true), true);
  assert.equal(sampled(env, 3).requests.length, 0);
});

test("VK: at most 16 manifests are kept for later binds, the newest", async () => {
  const urls = Array.from({ length: 20 }, (_, n) => MPD + "&n=" + n);
  const { env } = environment({
    bind: false, fetch: url => Promise.resolve(response("<MPD/>", url, "application/dash+xml")),
  });
  for (const url of urls.slice(0, 10)) await env.fetch(url);
  await settle();
  env.__yftMasterCaptureV1.bind(1, env.location.href, true);
  for (const url of urls.slice(10)) await env.fetch(url);
  await settle();
  env.__yftMasterCaptureV1.bind(2, env.location.href, true);
  assert.deepEqual(sampled(env, 2).requests.map(r => r.url), urls.slice(4));
});

test("F1: a piece answered to a request that named no range is marked a piece", async () => {
  const file = n => "https://cdn.test/file-" + n + ".mp4";
  const answers = {
    [file(1)]: pieceAnswer(file(1), "video/mp4", 206, null),
    [file(2)]: pieceAnswer(file(2), "video/mp4", 206, null),
    [file(3)]: pieceAnswer(file(3), "video/mp4", 200, "bytes 0-1427/5000000"),
    [file(4)]: pieceAnswer(file(4), "video/mp4", 206, "bytes 0-4999/5000"),
    [file(5)]: pieceAnswer(file(5), "video/mp4", 200, null),
    [MPD]: pieceAnswer(MPD, "application/dash+xml", 206, null),
  };
  const { env } = environment({ fetch: url => Promise.resolve(answers[url]) });
  await env.fetch(file(1));
  await env.fetch(file(2), { headers: { Range: "bytes=0-99" } });
  await env.fetch(file(3));
  await env.fetch(file(4));
  await env.fetch(file(5));
  await env.fetch(MPD);
  await settle();
  const marked = Object.fromEntries(packet(env).requests.map(r => [r.url, r.piece === true]));
  assert.deepEqual(marked, {
    [MPD]: false, [file(1)]: true, [file(2)]: false, [file(3)]: true, [file(4)]: false,
    [file(5)]: false,
  });
});

test("F1: an XHR piece is marked unless the page asked for a range", () => {
  const { env } = environment();
  function load(url, header) {
    const xhr = new env.XMLHttpRequest();
    xhr.open("GET", url);
    if (header) assert.equal(xhr.setRequestHeader("Range", "bytes=0-99"), "header-result");
    xhr.send();
    Object.assign(xhr, {
      responseURL: url, status: 206, responseType: "arraybuffer",
      getResponseHeader: name => name === "content-type" ? "video/mp4" : null,
      getAllResponseHeaders: () => "content-type: video/mp4\r\n",
    });
    xhr.listener.call(xhr);
  }
  load("https://cdn.test/unasked.mp4", false);
  load("https://cdn.test/asked.mp4", true);
  const marked = Object.fromEntries(packet(env).requests.map(r => [r.url, r.piece === true]));
  assert.deepEqual(marked, {
    "https://cdn.test/unasked.mp4": true, "https://cdn.test/asked.mp4": false,
  });
  const wrapped = env.XMLHttpRequest.prototype.setRequestHeader;
  env.__yftMasterCaptureV1.dispose();
  assert.notEqual(env.XMLHttpRequest.prototype.setRequestHeader, wrapped);
  assert.equal(new env.XMLHttpRequest().setRequestHeader("Range", "x"), "header-result");
});
