// R5 codec steering — Master opt-in builds only, installed at document start.
// Narrows what the page's player is told the browser can decode, so a site with a choice picks
// files this phone can merge (the DeviceMergeSupport rules): AVC/AAC always, VP9/Opus/Vorbis only
// when WebM merges are on, AV1 only when AV1 merges are on, HEVC/Dolby never. It never claims
// support the browser lacks, never touches DRM (EME) queries and is off on YouTube hosts, whose
// player Master does not answer. Unknown codecs are passed through unchanged.
(function () {
  "use strict";
  if (window.__yftMasterCodecsV1) return;
  const policy = /*YFT_CODEC_POLICY*/ { vp9: false, av1: false } /*END*/;
  const host = String((window.location && window.location.hostname) || "").toLowerCase();
  const walled = ["youtube.com", "youtu.be", "youtube-nocookie.com"];
  if (walled.some(function (d) { return host === d || host.endsWith("." + d); })) return;

  const BLOCK_ALWAYS = /^(hev1|hvc1|dvh1|dvhe|dva1|dvav|ec-3|ac-3|ac-4|mhm1|mha1)/;
  const WEBM_FAMILY = /^(vp09|vp9$|vp9\.|vp8$|vp8\.|opus$|vorbis$)/;
  const AV1 = /^av01/;

  function codecsOf(type) {
    const text = String(type || "");
    const match = /codecs\s*=\s*("([^"]*)"|'([^']*)'|([^;]*))/i.exec(text);
    if (!match) return [];
    const list = match[2] !== undefined ? match[2] : match[3] !== undefined ? match[3] : match[4];
    return list.split(",").map(function (c) { return c.trim().toLowerCase(); })
      .filter(function (c) { return c.length > 0; });
  }

  /** Whether the merge rules allow every codec [type] names (a bare container is allowed). */
  function allowed(type) {
    const mime = String(type || "").split(";")[0].trim().toLowerCase();
    const codecs = codecsOf(type);
    if (codecs.length === 0) {
      if (/^(video|audio)\/webm$/.test(mime)) return policy.vp9 === true;
      return true;
    }
    return codecs.every(function (codec) {
      if (BLOCK_ALWAYS.test(codec)) return false;
      if (AV1.test(codec)) return policy.av1 === true;
      if (WEBM_FAMILY.test(codec)) return policy.vp9 === true && !/^vp8/.test(codec);
      return true;
    });
  }

  const wrapped = [];
  function steerIsTypeSupported(owner) {
    if (!owner || typeof owner.isTypeSupported !== "function") return;
    const original = owner.isTypeSupported;
    const steered = function isTypeSupported(type) {
      const answer = original.call(this, type);
      return answer === true && allowed(type);
    };
    try {
      Object.defineProperty(owner, "isTypeSupported", {
        value: steered, configurable: true, writable: true,
      });
      wrapped.push(function () {
        Object.defineProperty(owner, "isTypeSupported", {
          value: original, configurable: true, writable: true,
        });
      });
    } catch (_) { /* a frozen object keeps its own answer */ }
  }
  steerIsTypeSupported(window.MediaSource);
  steerIsTypeSupported(window.ManagedMediaSource);
  steerIsTypeSupported(window.WebKitMediaSource);

  const capabilities = window.navigator && window.navigator.mediaCapabilities;
  if (capabilities && typeof capabilities.decodingInfo === "function") {
    const originalInfo = capabilities.decodingInfo;
    const steeredInfo = function decodingInfo(config) {
      const configuration = config || {};
      if (configuration.keySystemConfiguration) return originalInfo.apply(this, arguments);
      const video = configuration.video && configuration.video.contentType;
      const audio = configuration.audio && configuration.audio.contentType;
      if ((video && !allowed(video)) || (audio && !allowed(audio))) {
        return Promise.resolve({ supported: false, smooth: false, powerEfficient: false });
      }
      return originalInfo.apply(this, arguments);
    };
    try {
      Object.defineProperty(capabilities, "decodingInfo", {
        value: steeredInfo, configurable: true, writable: true,
      });
      wrapped.push(function () {
        Object.defineProperty(capabilities, "decodingInfo", {
          value: originalInfo, configurable: true, writable: true,
        });
      });
    } catch (_) { /* keep the browser's answer */ }
  }

  Object.defineProperty(window, "__yftMasterCodecsV1", {
    value: Object.freeze({
      allowed: allowed,
      policy: Object.freeze({ vp9: policy.vp9 === true, av1: policy.av1 === true }),
      dispose: function () { while (wrapped.length) wrapped.pop()(); },
    }),
    configurable: true,
  });
})();
