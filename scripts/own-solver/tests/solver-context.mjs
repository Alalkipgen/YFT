// Loads the own solver core the way its worker does: a bare V8 context holding only the vendored
// meriyah parser and the core (no require, no process, no network).
import { readFileSync } from "node:fs";
import vm from "node:vm";

const assets = new URL("../../../extractor-master-android/src/main/assets/yft-own-solver/", import.meta.url);
const lib = readFileSync(new URL("meriyah.umd.min.js", assets), "utf8");
const core = readFileSync(new URL("own.solver.core.js", assets), "utf8");

/** A fresh context with the parser and the core loaded. */
export function solverContext() {
  const context = vm.createContext({});
  vm.runInContext(lib, context);
  vm.runInContext(core, context);
  return context;
}

/** Players are at least 1000 characters long; the fake one is padded with a comment. */
export function padded(source) {
  return `${source}\n/*${" ".repeat(1000)}*/\n`;
}

/** yftOwnSolve in a fresh context, with JSON in and out like the worker protocol. */
export function solve(request, context = solverContext()) {
  context.input = JSON.stringify(request);
  return JSON.parse(vm.runInContext("JSON.stringify(yftOwnSolve(JSON.parse(input)))", context, { timeout: 30_000 }));
}

/**
 * Runs [fn] inside [context] (it may use `meriyah` and `yftOwnSolver`, nothing from the test
 * scope) with JSON arguments, and returns its JSON result.
 */
export function inside(context, fn, ...args) {
  const code = `JSON.stringify((${fn.toString()}).apply(null, ${JSON.stringify(args)}))`;
  const text = vm.runInContext(code, context, { timeout: 30_000 });
  return text === undefined ? undefined : JSON.parse(text);
}
