const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const assets = path.join(__dirname, "../../main/assets");
const hooks = fs.readFileSync(path.join(assets, "yft-master-mse.js"), "utf8");
const capture = fs.readFileSync(path.join(assets, "yft-master-capture.js"), "utf8");

// A page with MSE, XHR, fetch and EME, recording what the page's own calls received.
function environment(options = {}) {
  const calls = [];
  let blobs = 0;
  class SourceBuffer {
    appendBuffer(data) { calls.push(["append", data]); return "append-result"; }
  }
  class MediaSource {
    addSourceBuffer(mime) { calls.push(["add", mime]); return new SourceBuffer(); }
  }
  class PageUrl extends URL {
    static createObjectURL(object) {
      calls.push(["blob", object]); return `blob:https://page.test/${++blobs}`;
    }
  }
  class Xhr {
    open() {} send() { return "send-result"; } addEventListener() {}
  }
  Object.defineProperty(Xhr.prototype, "response", {
    get() { return this.body; }, enumerable: true, configurable: true
  });
  class Response {
    constructor(url, body) { this.url = url; this.body = body; }
    arrayBuffer() { return Promise.resolve(this.body); }
  }
  class HTMLMediaElement {
    setMediaKeys(keys) { calls.push(["keys", keys]); return Promise.resolve("keys-result"); }
  }
  class MediaKeySession {
    generateRequest(type) { calls.push(["licence", type]); return Promise.resolve("licence"); }
  }
  const navigator = {
    requestMediaKeySystemAccess(system) { calls.push(["query", system]); return "query-result"; }
  };
  const document = {
    addEventListener() {}, removeEventListener() {},
    querySelectorAll(selector) { return selector === "video" ? options.videos || [] : []; }
  };
  const page = options.page || "https://page.test/watch";
  const env = {
    location: { href: page, hostname: new URL(page).hostname }, document, navigator,
    URL: PageUrl, MediaSource, SourceBuffer, XMLHttpRequest: Xhr, Response,
    HTMLMediaElement, MediaKeySession, ArrayBuffer, Uint8Array, TextDecoder,
    setTimeout, clearTimeout, innerWidth: 400, innerHeight: 600,
    performance: { getEntriesByType: () => [] }, fetch: () => Promise.resolve(null),
  };
  env.window = env; env.top = env;
  vm.runInNewContext(hooks, env);
  if (options.capture) {
    vm.runInNewContext(capture, env);
    env.__yftMasterCaptureV1.bind(1, page);
  }
  return { env, calls, classes: { SourceBuffer, MediaSource, PageUrl, Xhr, Response } };
}

function box(type, ...parts) {
  const body = Buffer.concat(parts.map(part => Buffer.from(part)));
  const head = Buffer.alloc(8);
  head.writeUInt32BE(8 + body.length, 0); head.write(type, 4, "ascii");
  return Buffer.concat([head, body]);
}
function tkhd(width, height) {
  const body = Buffer.alloc(84);
  body.writeUInt32BE(width * 65536, 76); body.writeUInt32BE(height * 65536, 80);
  return box("tkhd", body);
}
function mp4Init(width, height) {
  const bytes = Buffer.concat([
    box("ftyp", Buffer.from("isom0000")),
    box("moov", box("mvhd", Buffer.alloc(100)), box("trak", tkhd(width, height))),
  ]);
  return new Uint8Array(bytes).buffer;
}
function ebml(id, body) {
  const size = body.length;
  return Buffer.concat([Buffer.from(id), Buffer.from([0x40 | (size >> 8), size & 0xff]), body]);
}
function webmInit(width, height) {
  const video = ebml([0xE0], Buffer.concat([
    Buffer.from([0xB0, 0x82, width >> 8, width & 0xff]),
    Buffer.from([0xBA, 0x82, height >> 8, height & 0xff]),
  ]));
  const tracks = ebml([0x16, 0x54, 0xAE, 0x6B], ebml([0xAE], Buffer.concat([
    Buffer.from([0xD7, 0x81, 0x01]), video,
  ])));
  const unknown = Buffer.from([0x18, 0x53, 0x80, 0x67, 0x01, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff]);
  const bytes = Buffer.concat([
    ebml([0x1A, 0x45, 0xDF, 0xA3], Buffer.from([0x42, 0x82, 0x84, 0x77, 0x65, 0x62, 0x6D])),
    unknown, tracks, Buffer.from([0x1F, 0x43, 0xB6, 0x75, 0x80]),
  ]);
  return new Uint8Array(bytes).buffer;
}
function playing(url) {
  return {
    currentSrc: url, src: url, paused: false, ended: false, readyState: 4, currentTime: 1,
    outerHTML: "<video></video>", getAttribute: () => null, querySelectorAll: () => [],
    getBoundingClientRect: () => ({ left: 0, top: 0, right: 300, bottom: 200 }),
  };
}
const settle = () => new Promise(resolve => setTimeout(resolve, 10));

test("the player's buffer types, init-segment size and feeding addresses are recorded", async () => {
  const { env } = environment();
  const source = new env.MediaSource();
  const blob = env.URL.createObjectURL(source);
  const video = source.addSourceBuffer('video/mp4; codecs="avc1.64001f"');
  const audio = source.addSourceBuffer('audio/mp4; codecs="mp4a.40.2"');
  const xhr = new env.XMLHttpRequest();
  xhr.responseURL = "https://cdn.test/v/720.mp4"; xhr.responseType = "arraybuffer";
  xhr.body = mp4Init(1280, 720);
  assert.equal(video.appendBuffer(xhr.response), "append-result");
  const response = new env.Response("https://cdn.test/a/audio.m4a", new ArrayBuffer(32));
  audio.appendBuffer(new Uint8Array(await response.arrayBuffer()));

  const facts = env.__yftMasterMseV1.facts({ currentSrc: blob, src: blob });
  assert.deepEqual(JSON.parse(JSON.stringify(facts)), [
    { mime: 'video/mp4; codecs="avc1.64001f"', kind: "video", width: 1280, height: 720,
      urls: ["https://cdn.test/v/720.mp4"] },
    { mime: 'audio/mp4; codecs="mp4a.40.2"', kind: "audio", width: null, height: null,
      urls: ["https://cdn.test/a/audio.m4a"] },
  ]);
  assert.equal(env.__yftMasterMseV1.facts({ currentSrc: "blob:https://page.test/x" }), null);
});

test("a WebM init segment's picture size is read from its video track", () => {
  const { env } = environment();
  const source = new env.MediaSource();
  const blob = env.URL.createObjectURL(source);
  source.addSourceBuffer('video/webm; codecs="vp9"').appendBuffer(webmInit(1920, 1080));
  const [fact] = env.__yftMasterMseV1.facts({ currentSrc: blob });
  assert.equal(fact.width, 1920);
  assert.equal(fact.height, 1080);
});

test("originals get their own arguments and results; plain-HTTP bytes are not remembered", () => {
  const { env, calls } = environment();
  const source = new env.MediaSource();
  const blob = env.URL.createObjectURL(source);
  assert.equal(blob, "blob:https://page.test/1");
  const buffer = source.addSourceBuffer("video/mp4");
  const xhr = new env.XMLHttpRequest();
  xhr.responseURL = "http://cdn.test/v.mp4"; xhr.body = new ArrayBuffer(8);
  const bytes = xhr.response;
  assert.equal(bytes, xhr.body);
  buffer.appendBuffer(bytes);
  assert.deepEqual(calls.map(call => call[0]), ["blob", "add", "append"]);
  assert.equal(calls[2][1], bytes);
  assert.equal(env.__yftMasterMseV1.facts({ currentSrc: blob })[0].urls.length, 0);
  assert.equal(env.navigator.requestMediaKeySystemAccess("com.widevine.alpha"), "query-result");
});

test("EME: a capability query alone is counted; keys on a video or a licence request protect", async () => {
  const { env } = environment();
  env.navigator.requestMediaKeySystemAccess("com.widevine.alpha", []);
  assert.deepEqual({ ...env.__yftMasterMseV1.eme() }, { probes: 1, licensed: false });
  const element = new env.HTMLMediaElement();
  assert.equal(env.__yftMasterMseV1.keyed(element), false);
  assert.equal(await element.setMediaKeys({}), "keys-result");
  assert.equal(env.__yftMasterMseV1.keyed(element), true);
  assert.equal(await new env.MediaKeySession().generateRequest("cenc", new ArrayBuffer(4)), "licence");
  assert.equal(env.__yftMasterMseV1.eme().licensed, true);
});

test("capture samples carry fed addresses with their size and stop on a licence request", async () => {
  const { env } = environment({ capture: true });
  const source = new env.MediaSource();
  const blob = env.URL.createObjectURL(source);
  const buffer = source.addSourceBuffer('video/mp4; codecs="avc1.4d401f"');
  const xhr = new env.XMLHttpRequest();
  xhr.responseURL = "https://cdn.test/media/main"; xhr.responseType = "arraybuffer";
  xhr.body = mp4Init(854, 480);
  buffer.appendBuffer(xhr.response);
  env.document.querySelectorAll = selector => selector === "video" ? [playing(blob)] : [];

  const first = JSON.parse(env.__yftMasterCaptureV1.sample(1, "https://page.test/watch"));
  assert.deepEqual(first.requests, [{
    url: "https://cdn.test/media/main", mime: 'video/mp4; codecs="avc1.4d401f"',
    preview: false, fed: true, width: 854, height: 480,
  }]);
  assert.equal(first.protected, false);

  await new env.MediaKeySession().generateRequest("cenc", new ArrayBuffer(4));
  const second = JSON.parse(env.__yftMasterCaptureV1.sample(1, "https://page.test/watch"));
  assert.equal(second.protected, true);
});

test("nothing is hooked on YouTube, and dispose restores every original", () => {
  const youtube = environment({ page: "https://m.youtube.com/watch?v=x" });
  assert.equal(youtube.env.__yftMasterMseV1, undefined);
  assert.equal(youtube.env.URL.createObjectURL, youtube.classes.PageUrl.createObjectURL);

  const { env, classes } = environment();
  const before = Object.getOwnPropertyDescriptor(classes.Xhr.prototype, "response").get;
  assert.notEqual(classes.MediaSource.prototype.addSourceBuffer.name, "addSourceBuffer");
  env.__yftMasterMseV1.dispose();
  assert.equal(env.__yftMasterMseV1, undefined);
  assert.equal(classes.MediaSource.prototype.addSourceBuffer.name, "addSourceBuffer");
  assert.equal(classes.SourceBuffer.prototype.appendBuffer.name, "appendBuffer");
  assert.equal(classes.Response.prototype.arrayBuffer.name, "arrayBuffer");
  assert.notEqual(Object.getOwnPropertyDescriptor(classes.Xhr.prototype, "response").get, before);
  assert.equal(env.HTMLMediaElement.prototype.setMediaKeys.name, "setMediaKeys");
});
