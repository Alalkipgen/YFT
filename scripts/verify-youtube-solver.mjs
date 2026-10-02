#!/usr/bin/env node
// Verifies the bundled YouTube solver against public test vectors and today's player.
//
// Usage: node scripts/verify-youtube-solver.mjs
//
// Needs network access to www.youtube.com. The solver runs in a bare V8 context with no
// require, no process and no network, the same shape the app's sandboxed worker gives it.
// The vectors in youtube-solver-vectors.json come from the yt-dlp ejs test suite (Unlicense).
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import vm from "node:vm";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const assets = join(root, "app/src/main/assets/youtube-solver");
const lib = readFileSync(join(assets, "yt.solver.lib.min.js"), "utf8");
const core = readFileSync(join(assets, "yt.solver.core.min.js"), "utf8");
const vectors = JSON.parse(readFileSync(join(root, "scripts/youtube-solver-vectors.json"), "utf8"));
const variants = ["player_ias.vflset/en_US/base.js", "player-plasma-ias-phone-en_US.vflset/base.js"];

async function fetchText(url) {
  const response = await fetch(url, { headers: { "Accept-Language": "en-US,en;q=0.9" } });
  if (!response.ok) throw new Error(`${url} answered ${response.status}`);
  return response.text();
}

function solve(player, n, sig) {
  const context = vm.createContext({});
  vm.runInContext(lib, context);
  vm.runInContext("var meriyah = lib.meriyah, astring = lib.astring;", context);
  vm.runInContext(core, context);
  context.input = JSON.stringify({
    type: "player",
    player,
    output_preprocessed: false,
    requests: [
      { type: "n", challenges: n },
      { type: "sig", challenges: sig },
    ],
  });
  return JSON.parse(vm.runInContext("JSON.stringify(jsc(JSON.parse(input)))", context));
}

let passed = 0;
let failed = 0;
for (const test of vectors) {
  for (const variant of variants) {
    const player = await fetchText(`https://www.youtube.com/s/player/${test.player}/${variant}`);
    const n = (test.n ?? []).map((step) => step.input);
    const sig = (test.sig ?? []).map((step) => step.input);
    const output = solve(player, n, sig);
    const [nResult, sigResult] = output.responses ?? [];
    const check = (steps, result, kind) => {
      for (const step of steps ?? []) {
        const actual = result?.type === "result" ? result.data[step.input] : undefined;
        if (actual === step.expected) {
          passed += 1;
        } else {
          failed += 1;
          console.log(`MISMATCH ${test.player} ${variant} ${kind}`);
        }
      }
    };
    check(test.n, nResult, "n");
    check(test.sig, sigResult, "sig");
  }
}
console.log(`Public vectors: ${passed} passed, ${failed} failed`);

// Today's player: the embed API names the version every embedded player loads right now.
const widget = await fetchText("https://www.youtube.com/iframe_api");
const current = /\\\/s\\\/player\\\/([A-Za-z0-9_-]{4,32})\\\//.exec(widget)?.[1];
if (!current) {
  console.log("Could not read today's player version from the embed API");
  process.exit(1);
}
const player = await fetchText(
  `https://www.youtube.com/s/player/${current}/player-plasma-ias-phone-en_US.vflset/base.js`,
);
const probe = "YftProbeValue0123";
const output = solve(player, [probe], []);
const solved = output.responses?.[0];
const value = solved?.type === "result" ? solved.data[probe] : undefined;
const plausible = typeof value === "string" && value !== probe && /^[A-Za-z0-9_-]{2,128}$/.test(value);
console.log(`Today's player ${current}: rate parameter ${plausible ? "solved" : "NOT solved"}`);
process.exit(failed === 0 && plausible ? 0 : 1);
