const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const template = fs.readFileSync(
  require("node:path").join(__dirname, "../../main/assets/yft-master-codecs.js"), "utf8"
);

/** The asset with [policy] put in place the way CodecSteering.script does. */
function withPolicy(policy) {
  const start = template.indexOf("/*YFT_CODEC_POLICY*/");
  const end = template.indexOf("/*END*/", start + 1);
  assert.ok(start >= 0 && end > start);
  return template.slice(0, start) + `{ vp9: ${policy.vp9}, av1: ${policy.av1} }` +
    template.slice(end + "/*END*/".length);
}

function page(policy, options = {}) {
  const native = options.native || (() => true);
  const calls = [];
  class MediaSource {}
  MediaSource.isTypeSupported = type => { calls.push(type); return native(type); };
  const decoded = [];
  const env = {
    location: { hostname: options.host || "www.example.test" },
    MediaSource,
    navigator: { mediaCapabilities: {
      decodingInfo(config) { decoded.push(config); return Promise.resolve({ supported: true }); },
    } },
    Promise,
  };
  env.window = env;
  vm.runInNewContext(withPolicy(policy), env);
  return { env, calls, decoded, MediaSource };
}

const OLD = { vp9: false, av1: false }; // Android 9 and older
const NEW = { vp9: true, av1: false };  // Android 10 and newer

test("AVC/AAC is always allowed; AV1 and HEVC never while their merges are off", () => {
  const { env } = page(NEW);
  const ms = env.MediaSource;
  assert.equal(ms.isTypeSupported('video/mp4; codecs="avc1.640028"'), true);
  assert.equal(ms.isTypeSupported('audio/mp4; codecs="mp4a.40.2"'), true);
  assert.equal(ms.isTypeSupported('video/mp4; codecs="av01.0.08M.08"'), false);
  assert.equal(ms.isTypeSupported('video/mp4; codecs="hvc1.1.6.L120.90"'), false);
  assert.equal(ms.isTypeSupported('audio/mp4; codecs="ec-3"'), false);
});

test("VP9/Opus WebM is allowed only where WebM merges are on", () => {
  const vp9 = 'video/webm; codecs="vp09.00.51.08"';
  const opus = 'audio/webm; codecs="opus"';
  assert.equal(page(NEW).env.MediaSource.isTypeSupported(vp9), true);
  assert.equal(page(NEW).env.MediaSource.isTypeSupported(opus), true);
  assert.equal(page(OLD).env.MediaSource.isTypeSupported(vp9), false);
  assert.equal(page(OLD).env.MediaSource.isTypeSupported(opus), false);
  assert.equal(page(NEW).env.MediaSource.isTypeSupported('video/webm; codecs="vp8"'), false);
  assert.equal(page(OLD).env.MediaSource.isTypeSupported("video/webm"), false);
});

test("never claims support the browser lacks and still asks the browser", () => {
  const { env, calls } = page(NEW, { native: () => false });
  assert.equal(env.MediaSource.isTypeSupported('video/mp4; codecs="avc1.4d401f"'), false);
  assert.deepEqual(calls, ['video/mp4; codecs="avc1.4d401f"']);
});

test("decodingInfo is narrowed the same way and DRM queries pass through", async () => {
  const { env, decoded } = page(NEW);
  const caps = env.navigator.mediaCapabilities;
  const av1 = { type: "media-source", video: { contentType: 'video/mp4; codecs="av01.0.05M.08"' } };
  assert.equal((await caps.decodingInfo(av1)).supported, false);
  assert.equal(decoded.length, 0);
  const avc = { type: "media-source", video: { contentType: 'video/mp4; codecs="avc1.64001f"' } };
  assert.equal((await caps.decodingInfo(avc)).supported, true);
  const drm = { ...av1, keySystemConfiguration: { keySystem: "com.widevine.alpha" } };
  assert.equal((await caps.decodingInfo(drm)).supported, true);
  assert.equal(decoded.length, 2);
});

test("off on YouTube hosts, installs once and can be undone", () => {
  for (const host of ["www.youtube.com", "m.youtube.com", "youtu.be", "www.youtube-nocookie.com"]) {
    const { env } = page(NEW, { host });
    assert.equal(env.__yftMasterCodecsV1, undefined);
    assert.equal(env.MediaSource.isTypeSupported('video/mp4; codecs="av01.0.08M.08"'), true);
  }
  const { env } = page(NEW, { host: "notyoutube.com" });
  assert.ok(env.__yftMasterCodecsV1);
  const steered = env.MediaSource.isTypeSupported;
  vm.runInNewContext(withPolicy(NEW), env);
  assert.equal(env.MediaSource.isTypeSupported, steered);
  env.__yftMasterCodecsV1.dispose();
  assert.equal(env.MediaSource.isTypeSupported('video/mp4; codecs="av01.0.08M.08"'), true);
});
