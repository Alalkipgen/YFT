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
      if (selector.startsWith("script")) return options.scripts || [];
      return [];
    }
  };
  const env = {
    location: { href: "https://page.test/watch" }, document,
    XMLHttpRequest: Xhr, HTMLMediaElement: Media, URL, TextDecoder,
    setTimeout, clearTimeout, innerWidth: 400, innerHeight: 600,
    performance: { getEntriesByType: () => [] }, fetch: originalFetch,
  };
  env.window = env; env.top = env;
  vm.runInNewContext(source, env);
  env.__yftMasterCaptureV1.bind(1, env.location.href);
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
