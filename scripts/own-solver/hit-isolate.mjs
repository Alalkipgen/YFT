// Phase 1.2 N5b A.4: one hitWithOwn in this worker thread's own, fresh V8 isolate (harness.mjs
// hitInIsolate): the app runs every job in a new Worker, so nothing compiled or run before is warm.
import { parentPort, workerData } from "node:worker_threads";
import { hitWithOwn } from "./harness.mjs";

const { own, player, inputs, extra } = workerData;
parentPort.postMessage(hitWithOwn(own, player, inputs, extra));
