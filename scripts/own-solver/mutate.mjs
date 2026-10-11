// Phase 1.2 N1 (MASTER_KEY_PHASE1_2_PLAN.md): behaviour-keeping mutations of a player, used by
// scripts/verify-own-solver-mutations.mjs to simulate YouTube changing the SHAPE of its code.
//
// Each mutation keeps what the program DOES; only how it is written changes. meriyah parses and
// astring prints (both vendored in main's yt.solver.lib.min.js; used only at test time, never
// shipped). No player text is ever committed. A scope model (T3) is built before any rename so a
// fresh name can never capture or shadow another binding; identifiers that are globals, or reached
// through eval/with/arguments, are left alone. The caller verifies behaviour with an independent
// truth hook, so a mutation that would change behaviour is skipped, never counted as a wrong value.

export const CLASSES = ["M1", "M1w", "M2", "M3", "M4", "M5", "M6", "M7", "M8", "M8t", "M9", "M10", "M11", "M11f"];

export const CLASS_NOTE = {
  M1: "rename every function/block/catch binding; globals and the wrapper parameter stay (a new build)",
  M1w: "as M1, and rename the wrapper parameter too (`g` in `(function(g){...})(_yt_player)`)",
  M2: "toggle dotted <-> computed member access, where the key is a valid name (code generation)",
  M3: "re-wrap the top level in another wrapper form (#53)",
  M4: "split small helper objects (the sig helper) into separate functions (helper reshuffle)",
  M5: "call every top-level function with 3+ call sites through a fresh alias (indirect call sites)",
  M6: "read fixed string literals from a shuffled table (obfuscation)",
  M7: "flatten plain function bodies into a switch-in-loop dispatcher (heavier obfuscation)",
  M8: "global encode/decodeURIComponent reached through fresh wrapper functions (#54 class)",
  M8t: "as M8, with the wrappers first in the wrapper body (new encode helpers met first)",
  M9: "read navigator/location/document/window in the middle of init, unused (#56 class)",
  M10: "append decoy functions that look like n/sig but return other values (false positives)",
  M11: "add harmless var declarations at the top of the wrapper body (a new prelude)",
  M11f: "add harmless function declarations at the top of the wrapper body",
};

const FRESH = "_m$";

// A deterministic PRNG so a (player, class, seed) always makes the same mutation.
export function rng(seedText) {
  let seed = 2166136261 >>> 0;
  for (const ch of String(seedText)) seed = Math.imul(seed ^ ch.charCodeAt(0), 16777619) >>> 0;
  return () => {
    seed = (seed + 0x6d2b79f5) >>> 0;
    let t = seed;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// ------------------------------------------------------------------------------------------------
// Walk

const SKIP = new Set(["loc", "range", "start", "end", "leadingComments", "trailingComments"]);

function childEntries(node) {
  const out = [];
  for (const key of Object.keys(node)) {
    if (SKIP.has(key)) continue;
    const value = node[key];
    if (Array.isArray(value)) {
      value.forEach((child, index) => {
        if (child && typeof child.type === "string") out.push({ parent: node, key, index, node: child });
      });
    } else if (value && typeof value.type === "string") {
      out.push({ parent: node, key, index: null, node: value });
    }
  }
  return out;
}

export function walk(root, enter) {
  const stack = [{ parent: null, key: null, index: null, node: root }];
  while (stack.length) {
    const item = stack.pop();
    if (enter(item) === false) continue;
    const kids = childEntries(item.node);
    for (let i = kids.length - 1; i >= 0; i -= 1) stack.push(kids[i]);
  }
}

// Identifier target nodes of a binding pattern.
function bindingTargets(pattern) {
  const out = [];
  const visit = (node) => {
    if (!node) return;
    if (node.type === "Identifier") out.push(node);
    else if (node.type === "AssignmentPattern") visit(node.left);
    else if (node.type === "RestElement") visit(node.argument);
    else if (node.type === "ArrayPattern") node.elements.forEach(visit);
    else if (node.type === "ObjectPattern") node.properties.forEach((p) => visit(p.type === "Property" ? p.value : p.argument));
  };
  visit(pattern);
  return out;
}

// ------------------------------------------------------------------------------------------------
// Scope model (T3)
//
// Each binding keeps every Identifier node that denotes it: the declaration site(s) and the
// references. Renaming then sets one fresh name on all of them at once. `ok` is false when the
// program uses `with`, direct `eval` or `arguments`, in which case the caller skips renaming.

function makeScope(kind, parent) {
  return { kind, parent, names: new Map() };
}

function newBinding(scope, id) {
  return { name: id.name, scope, nodes: [id] };
}

export function resolveScopes(program) {
  const programScope = makeScope("function", null);
  const bindings = [];
  let ok = true;

  const define = (scope, id) => {
    if (scope.names.has(id.name)) {
      scope.names.get(id.name).nodes.push(id);
    } else {
      const binding = newBinding(scope, id);
      bindings.push(binding);
      scope.names.set(id.name, binding);
    }
  };

  // var + function-declaration hoisting into one function/program scope.
  const hoist = (nodes, fnScope) => {
    const stack = nodes.slice().reverse();
    while (stack.length) {
      const node = stack.pop();
      if (!node || typeof node.type !== "string") continue;
      const t = node.type;
      if (t === "FunctionDeclaration") { if (node.id) define(fnScope, node.id); continue; }
      if (t === "FunctionExpression" || t === "ArrowFunctionExpression" || t === "ClassDeclaration" || t === "ClassExpression") continue;
      if (t === "VariableDeclaration" && node.kind === "var") {
        node.declarations.forEach((d) => bindingTargets(d.id).forEach((id) => define(fnScope, id)));
      }
      for (const { node: child } of childEntries(node)) stack.push(child);
    }
  };

  // let/const/class at the top of a block or program scope.
  const blockDecls = (nodes, scope) => {
    for (const node of nodes) {
      if (!node) continue;
      if (node.type === "VariableDeclaration" && node.kind !== "var") {
        node.declarations.forEach((d) => bindingTargets(d.id).forEach((id) => define(scope, id)));
      } else if (node.type === "ClassDeclaration" && node.id) {
        define(scope, node.id);
      }
    }
  };

  const reference = (id, scope) => {
    let cursor = scope;
    while (cursor) {
      if (cursor.names.has(id.name)) { cursor.names.get(id.name).nodes.push(id); return; }
      cursor = cursor.parent;
    }
  };

  const isRef = (parent, key, node) => {
    if (!parent) return false;
    if (parent.type === "MemberExpression" && key === "property" && !parent.computed) return false;
    if (parent.type === "Property" && key === "key" && !parent.computed) return false;
    if ((parent.type === "MethodDefinition" || parent.type === "PropertyDefinition") && key === "key" && !parent.computed) return false;
    if (parent.type === "LabeledStatement" || parent.type === "BreakStatement" || parent.type === "ContinueStatement") return false;
    if (parent.type === "MetaProperty") return false;
    return true;
  };

  // Walk an arbitrary subtree that is not itself a scope boundary, feeding identifiers as refs and
  // recursing into nested functions/blocks/catch as new scopes. Binding ids inside declarators are
  // skipped (already defined); their initialisers are visited.
  const walkInto = (node, scope) => {
    if (!node || typeof node.type !== "string") return;
    switch (node.type) {
      case "FunctionDeclaration":
      case "FunctionExpression":
      case "ArrowFunctionExpression":
        return enterFunction(node, scope);
      case "BlockStatement": {
        const inner = makeScope("block", scope);
        blockDecls(node.body, inner);
        node.body.forEach((s) => walkInto(s, inner));
        return;
      }
      case "CatchClause": {
        const inner = makeScope("block", scope);
        if (node.param) bindingTargets(node.param).forEach((id) => define(inner, id));
        blockDecls(node.body.body, inner);
        node.body.body.forEach((s) => walkInto(s, inner));
        return;
      }
      case "ForStatement":
      case "ForInStatement":
      case "ForOfStatement": {
        const inner = makeScope("block", scope);
        const head = node.init || node.left;
        if (head && head.type === "VariableDeclaration") {
          if (head.kind === "var") hoist([head], functionOf(scope));
          else head.declarations.forEach((d) => bindingTargets(d.id).forEach((id) => define(inner, id)));
          head.declarations.forEach((d) => { if (d.init) walkInto(d.init, inner); });
        } else if (head) {
          walkInto(head, inner);
        }
        if (node.test) walkInto(node.test, inner);
        if (node.update) walkInto(node.update, inner);
        if (node.right) walkInto(node.right, inner);
        walkInto(node.body, inner);
        return;
      }
      case "VariableDeclaration":
        node.declarations.forEach((d) => { if (d.init) walkInto(d.init, scope); });
        return;
      case "MemberExpression":
        walkInto(node.object, scope);
        if (node.computed) walkInto(node.property, scope);
        return;
      case "Property":
        if (node.computed) walkInto(node.key, scope);
        walkInto(node.value, scope);
        return;
      case "MethodDefinition":
      case "PropertyDefinition":
        if (node.computed && node.key) walkInto(node.key, scope);
        if (node.value) walkInto(node.value, scope);
        return;
      case "WithStatement":
        ok = false;
        walkInto(node.object, scope);
        walkInto(node.body, scope);
        return;
      case "Identifier":
        reference(node, scope); // only reached in expression position (callers skip binding/key spots)
        return;
      case "SwitchStatement": {
        walkInto(node.discriminant, scope);
        const inner = makeScope("block", scope);
        const all = [];
        node.cases.forEach((c) => all.push(...c.consequent));
        blockDecls(all, inner);
        node.cases.forEach((c) => { if (c.test) walkInto(c.test, inner); c.consequent.forEach((st) => walkInto(st, inner)); });
        return;
      }
      case "ClassDeclaration":
      case "ClassExpression": {
        let inner = scope;
        if (node.type === "ClassExpression" && node.id) { inner = makeScope("block", scope); define(inner, node.id); }
        if (node.superClass) walkInto(node.superClass, inner);
        walkInto(node.body, inner);
        return;
      }
      default: {
        if (node.type === "CallExpression" && node.callee.type === "Identifier" && node.callee.name === "eval") ok = false;
        for (const { parent, key, node: child } of childEntries(node)) {
          if (child.type === "Identifier") {
            if (isRef(parent, key, child)) reference(child, scope);
          } else {
            walkInto(child, scope);
          }
        }
      }
    }
  };

  const functionOf = (scope) => { let c = scope; while (c && c.kind === "block") c = c.parent; return c; };

  const enterFunction = (fn, scope) => {
    const fnScope = makeScope("function", scope);
    if (fn.type === "FunctionExpression" && fn.id) define(fnScope, fn.id);
    fn.params.forEach((p) => {
      bindingTargets(p).forEach((id) => define(fnScope, id));
      if (p.type === "AssignmentPattern") walkInto(p.right, fnScope);
    });
    if (fn.body.type === "BlockStatement") {
      hoist(fn.body.body, fnScope);
      blockDecls(fn.body.body, fnScope);
      fn.body.body.forEach((s) => walkInto(s, fnScope));
    } else {
      walkInto(fn.body, fnScope);
    }
  };

  hoist(program.body, programScope);
  blockDecls(program.body, programScope);
  program.body.forEach((s) => walkInto(s, programScope));
  return { bindings, ok, programScope };
}

// ------------------------------------------------------------------------------------------------
// Mutators (each returns a small count of edits; 0 means it did nothing)

// M1: rename every resolved local binding to a fresh unique name.
export function renameLocals(program, random, options = {}) {
  const keepWrapperParams = options.keepWrapperParams !== false;
  // `{a}` prints only its value; after a rename that would change the key. Spell it `{a: a}`.
  walk(program, ({ node }) => {
    if (node.type === "Property" && node.shorthand) {
      node.shorthand = false;
      node.key = { type: "Identifier", name: node.key.name };
    }
    return true;
  });
  const { bindings, ok, programScope } = resolveScopes(program);
  if (!ok) return 0;
  // Program-level names are globals the page outside the player reads (`_yt_player`); a new build
  // never renames them. Only function, block and catch bindings are renamed.
  const wrapper = keepWrapperParams ? topWrapper(program) : null;
  const kept = new Set(wrapper ? wrapper.callee.params.filter((p) => p.type === "Identifier") : []);
  // A local named after a browser global (`var window = this;` at the top of the wrapper) is a
  // deliberate convention, not a minifier's choice; a new build keeps it.
  const locals = bindings.filter((binding) =>
    binding.scope !== programScope &&
    !BROWSER_NAMES.has(binding.name) &&
    !binding.nodes.some((node) => kept.has(node)));
  // Minifier-style names (short, like a real build), each one unused anywhere in the program and
  // never a reserved word or a common global, so a new name cannot capture another binding.
  const used = new Set();
  walk(program, ({ node }) => { if (node.type === "Identifier") used.add(node.name); return true; });
  const next = minifiedNames(used, random);
  for (const binding of locals) {
    const fresh = next();
    binding.nodes.forEach((node) => { node.name = fresh; });
  }
  return locals.length;
}

const FIRST = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ$_";
const REST = FIRST + "0123456789";
const COMMON_GLOBALS = new Set(["window", "self", "top", "parent", "document", "location", "navigator", "name", "length", "status", "event", "external", "frames", "opener", "origin", "screen", "history", "performance", "console", "globalThis", "undefined", "NaN", "Infinity", "Object", "Array", "String", "Number", "Boolean", "Symbol", "Function", "Math", "JSON", "Date", "RegExp", "Error", "Map", "Set", "Promise", "Proxy", "Reflect", "URL", "isNaN", "isFinite", "parseInt", "parseFloat", "escape", "unescape", "eval", "arguments", "of", "as", "get", "set", "async", "await", "yield", "let", "static"]);

const BROWSER_NAMES = new Set(["window", "self", "globalThis", "document", "location", "navigator", "top", "parent", "frames"]);

// Short names in creation order from a random start: a, b, ..., $, _, aa, ab, ... (skipping used).
function minifiedNames(used, random) {
  let counter = Math.floor(random() * 2000);
  const encode = (n) => {
    let out = FIRST[n % FIRST.length];
    n = Math.floor(n / FIRST.length);
    while (n > 0) { n -= 1; out += REST[n % REST.length]; n = Math.floor(n / REST.length); }
    return out;
  };
  return () => {
    for (;;) {
      const name = encode(counter++);
      if (!used.has(name) && !RESERVED.has(name) && !COMMON_GLOBALS.has(name)) { used.add(name); return name; }
    }
  };
}

const NAME_RE = /^[A-Za-z_$][A-Za-z0-9_$]*$/;
const RESERVED = new Set(["do", "if", "in", "for", "let", "new", "try", "var", "case", "else", "enum", "eval", "null", "this", "true", "void", "with", "break", "catch", "class", "const", "false", "super", "throw", "while", "yield", "delete", "export", "import", "public", "return", "static", "switch", "typeof", "default", "extends", "finally", "package", "private", "continue", "debugger", "function", "arguments", "interface", "protected", "implements", "instanceof"]);

// M2: toggle member access between dotted and computed where the key is a plain name.
export function toggleMemberAccess(program, random) {
  let edits = 0;
  walk(program, ({ node }) => {
    if (node.type === "MemberExpression") {
      if (!node.computed && node.property.type === "Identifier" && random() < 0.7) {
        node.computed = true;
        node.property = { type: "Literal", value: node.property.name };
        edits += 1;
      } else if (node.computed && node.property.type === "Literal" && typeof node.property.value === "string" && NAME_RE.test(node.property.value) && !RESERVED.has(node.property.value) && random() < 0.7) {
        node.computed = false;
        node.property = { type: "Identifier", name: node.property.value };
        edits += 1;
      }
    }
    if (node.type === "Property" && !node.computed && node.shorthand) return true;
    return true;
  });
  return edits;
}

// Find the program's single top-level wrapper function (namespace or IIFE forms), or null.
function topWrapper(program) {
  const body = program.body.filter((s) => s.type !== "EmptyStatement");
  let i = 0;
  while (i < body.length && body[i].type === "ExpressionStatement" && body[i].expression.type === "Literal") i += 1;
  const rest = body.slice(i);
  const unwrapCall = (st) => {
    if (!st || st.type !== "ExpressionStatement") return null;
    let e = st.expression;
    if (e.type === "UnaryExpression" && (e.operator === "!" || e.operator === "void")) e = e.argument;
    if (e.type !== "CallExpression") return null;
    let callee = e.callee;
    if (callee.type === "MemberExpression" && !callee.computed && (callee.property.name === "call" || callee.property.name === "apply")) callee = callee.object;
    const isFn = callee.type === "FunctionExpression" || (callee.type === "ArrowFunctionExpression" && callee.body.type === "BlockStatement");
    return isFn ? { statement: st, call: e, callee } : null;
  };
  if (rest.length === 1) return unwrapCall(rest[0]) ? { ...unwrapCall(rest[0]), firstIndex: i } : null;
  if (rest.length === 2) { const w = unwrapCall(rest[1]); return w ? { ...w, firstIndex: i, hasNamespace: true } : null; }
  return null;
}

// M3: re-wrap the top-level call as `void (fn).apply(receiver, [args])`. The receiver is exactly
// what the original call gave: `.call(X, ...)` -> X; a plain call -> void 0 (the same `this` in
// strict and sloppy code). The function node itself is kept, so an arrow stays an arrow.
export function rewrapTopLevel(program) {
  const w = topWrapper(program);
  if (!w) return 0;
  const call = w.call;
  let receiver = { type: "UnaryExpression", operator: "void", prefix: true, argument: { type: "Literal", value: 0 } };
  let args = call.arguments.slice();
  const viaMember = call.callee.type === "MemberExpression" && !call.callee.computed;
  if (viaMember && call.callee.property.name === "apply") return 0; // already this form
  if (viaMember && call.callee.property.name === "call") {
    if (args.length) receiver = args[0];
    args = args.slice(1);
  }
  w.statement.expression = {
    type: "UnaryExpression", operator: "void", prefix: true,
    argument: {
      type: "CallExpression", optional: false,
      callee: { type: "MemberExpression", computed: false, optional: false, object: w.callee, property: { type: "Identifier", name: "apply" } },
      arguments: [receiver, { type: "ArrayExpression", elements: args }],
    },
  };
  return 1;
}

// M6: move fixed string literals into one shuffled table read by index. Only inside the wrapper
// body (the table is declared there), never a bare string statement (a directive such as
// "use strict" must stay one), never a non-computed key. The table goes after the directives.
export function tableLiterals(program, random) {
  const w = topWrapper(program);
  const container = w && w.callee.body.type === "BlockStatement" ? w.callee.body.body : program.body;
  const slots = [];
  const index = new Map();
  const edits = [];
  for (const statement of container) {
    walk(statement, ({ parent, key, index: ai, node }) => {
      if (node.type !== "Literal" || typeof node.value !== "string") return true;
      if (!parent || node.value.length > 60) return true;
      if (parent.type === "ExpressionStatement") return true;
      const keySpot = parent.type === "Property" || parent.type === "MethodDefinition" || parent.type === "PropertyDefinition";
      if (keySpot && key === "key" && !parent.computed) return true;
      if (random() < 0.2) return true;
      if (!index.has(node.value)) { index.set(node.value, slots.length); slots.push(node.value); }
      edits.push({ parent, key, ai, slot: index.get(node.value) });
      return true;
    });
  }
  if (!edits.length) return 0;
  const tableName = FRESH + "tbl";
  const order = slots.map((_, i) => i);
  for (let i = order.length - 1; i > 0; i -= 1) { const j = Math.floor(random() * (i + 1)); [order[i], order[j]] = [order[j], order[i]]; }
  const position = new Map();
  order.forEach((orig, pos) => position.set(orig, pos));
  for (const e of edits) {
    const ref = { type: "MemberExpression", computed: true, optional: false, object: { type: "Identifier", name: tableName }, property: { type: "Literal", value: position.get(e.slot) } };
    if (e.ai === null) e.parent[e.key] = ref; else e.parent[e.key][e.ai] = ref;
  }
  // The form real players use: one string split on a separator no entry contains.
  const values = order.map((orig) => slots[orig]);
  const separator = [";", "}", "|", "~", "\u0001"].find((sep) => values.every((v) => !v.includes(sep)));
  const init = separator
    ? { type: "CallExpression", optional: false, callee: { type: "MemberExpression", computed: false, optional: false, object: { type: "Literal", value: values.join(separator) }, property: { type: "Identifier", name: "split" } }, arguments: [{ type: "Literal", value: separator }] }
    : { type: "ArrayExpression", elements: values.map((v) => ({ type: "Literal", value: v })) };
  const decl = { type: "VariableDeclaration", kind: "var", declarations: [{ type: "VariableDeclarator", id: { type: "Identifier", name: tableName }, init }] };
  let at = 0;
  while (at < container.length && container[at].type === "ExpressionStatement" && container[at].expression.type === "Literal" && typeof container[at].expression.value === "string") at += 1;
  container.splice(at, 0, decl);
  return edits.length;
}

// M10: append decoy functions that look like n/sig but return other values. They are unreachable
// (never called), so behaviour is unchanged; the solver must not pick them.
export function appendDecoys(program, random) {
  const w = topWrapper(program);
  const body = w && w.callee.body.type === "BlockStatement" ? w.callee.body.body : program.body;
  const mk = (name, src) => {
    const fnAst = parseStatements(src.replace("NAME", name));
    body.push(...fnAst);
  };
  const tag = FRESH + "dc" + Math.floor(random() * 1000).toString(36);
  // a reverse (sig-like), a rotate (sig-like), an identity-ish (n-like but wrong)
  pendingDecoys(tag).forEach((s, i) => mk(`${tag}_${i}`, s));
  return 3;
}

function pendingDecoys(tag) {
  return [
    `function NAME(s){var a=(''+s).split('');a.reverse();return a.join('')}`,
    `function NAME(s){var a=(''+s).split('');a.push(a.shift());return a.join('')}`,
    `function NAME(n){return (''+n).split('').map(function(c){return c}).join('')}`,
  ];
}

// Parser hook: set by the runner so mutators can build sub-ASTs from source.
let PARSE_STATEMENTS = null;
export function setStatementParser(fn) { PARSE_STATEMENTS = fn; }
function parseStatements(src) {
  if (!PARSE_STATEMENTS) throw new Error("statement parser not set");
  return PARSE_STATEMENTS(src);
}

// ------------------------------------------------------------------------------------------------
export const MUTATORS = {
  M1: (p, r) => renameLocals(p, r),
  M1w: (p, r) => renameLocals(p, r, { keepWrapperParams: false }),
  M2: (p, r) => toggleMemberAccess(p, r),
  M3: (p) => rewrapTopLevel(p),
  M4: (p, r) => splitHelperObjects(p, r),
  M5: (p, r) => aliasCallSites(p, r),
  M6: (p, r) => tableLiterals(p, r),
  M7: (p, r) => flattenFunctions(p, r),
  M8: (p, r) => wrapEncoding(p, r),
  M8t: (p, r) => wrapEncoding(p, r, { atTop: true }),
  M9: (p, r) => readBrowserObjects(p, r),
  M10: (p, r) => appendDecoys(p, r),
  M11: (p, r) => addPrelude(p, r),
  M11f: (p, r) => addPrelude(p, r, { functions: true }),
};

export function parseWith(meriyah, text) {
  return meriyah.parse(text, { ranges: false, webcompat: true, loc: false });
}
export function generateWith(astring, ast) {
  return astring.generate(ast);
}

// ------------------------------------------------------------------------------------------------
// M5: call sites through a fresh alias. Every call `f(...)` of a function declared at the top of
// the wrapper body (one with three or more call sites, like the stream-URL builder) becomes
// `alias(...)`, with `var alias = f;` added right after f's declaration. The alias holds the same
// function and the receiver of a plain call is unchanged, so behaviour is the same.
export function aliasCallSites(program, random) {
  const w = topWrapper(program);
  const body = w && w.callee.body.type === "BlockStatement" ? w.callee.body.body : null;
  if (!body) return 0;
  const declared = new Map(); // name -> index in body
  body.forEach((statement, index) => {
    if (statement.type === "FunctionDeclaration" && statement.id) declared.set(statement.id.name, index);
    if (statement.type === "VariableDeclaration") {
      statement.declarations.forEach((d) => {
        if (d.id.type === "Identifier" && d.init && (d.init.type === "FunctionExpression" || d.init.type === "ArrowFunctionExpression")) declared.set(d.id.name, index);
      });
    }
    if (statement.type === "ExpressionStatement" && statement.expression.type === "AssignmentExpression" && statement.expression.left.type === "Identifier") {
      const right = statement.expression.right;
      if (right.type === "FunctionExpression" || right.type === "ArrowFunctionExpression") declared.set(statement.expression.left.name, index);
    }
  });
  const { bindings, ok } = resolveScopes(program);
  if (!ok) return 0;
  // One pass: every Identifier that is a call's callee.
  const callees = new Set();
  walk(program, ({ parent, key, node }) => {
    if (parent && parent.type === "CallExpression" && key === "callee" && node.type === "Identifier") callees.add(node);
    return true;
  });
  // Declaration nodes of the wrapper body, so only names bound in the wrapper's own scope count.
  const declNodes = new Set();
  for (const st of body) {
    if (st.type === "FunctionDeclaration" && st.id) declNodes.add(st.id);
    if (st.type === "VariableDeclaration") st.declarations.forEach((d) => { if (d.id.type === "Identifier") declNodes.add(d.id); });
    if (st.type === "ExpressionStatement" && st.expression.type === "AssignmentExpression" && st.expression.left.type === "Identifier") declNodes.add(st.expression.left);
  }
  const used = new Set();
  walk(program, ({ node }) => { if (node.type === "Identifier") used.add(node.name); return true; });
  const next = minifiedNames(used, random);
  const inserts = [];
  let edits = 0;
  for (const binding of bindings) {
    if (!declared.has(binding.name)) continue;
    if (!binding.nodes.some((node) => declNodes.has(node))) continue;
    const calls = binding.nodes.filter((node) => callees.has(node));
    if (calls.length < 3) continue;
    const alias = next();
    calls.forEach((node) => { node.name = alias; });
    edits += calls.length;
    inserts.push({ at: declared.get(binding.name), alias, name: binding.name });
  }
  // Insert `var alias = f;` after each declaration (highest index first keeps positions valid).
  inserts.sort((a, b) => b.at - a.at).forEach((ins) => {
    body.splice(ins.at + 1, 0, { type: "VariableDeclaration", kind: "var", declarations: [{ type: "VariableDeclarator", id: { type: "Identifier", name: ins.alias }, init: { type: "Identifier", name: ins.name } }] });
  });
  return edits;
}

// ------------------------------------------------------------------------------------------------
// M4: helper reshuffle. A wrapper-level object whose 2-4 properties are all small functions that
// do not use `this` (the sig helper: splice / reverse / swap) is split: each method becomes its
// own wrapper-level function declared just before the object, and the object keeps the same keys
// pointing at those functions. Calls like `H.k(a, b)` then reach the same code (no `this` used),
// so behaviour is the same while the helper's shape changes.
export function splitHelperObjects(program, random) {
  const w = topWrapper(program);
  const body = w && w.callee.body.type === "BlockStatement" ? w.callee.body.body : null;
  if (!body) return 0;
  const used = new Set();
  walk(program, ({ node }) => { if (node.type === "Identifier") used.add(node.name); return true; });
  const next = minifiedNames(used, random);
  const usesThis = (fn) => {
    let found = false;
    walk(fn.body, ({ node }) => {
      if (found) return false;
      if (node.type === "ThisExpression" || node.type === "Super" || (node.type === "Identifier" && node.name === "arguments")) { found = true; return false; }
      return !(node.type === "FunctionExpression" || node.type === "FunctionDeclaration"); // arrows inherit this
    });
    return found;
  };
  const small = (v) => v && (v.type === "FunctionExpression" || v.type === "ArrowFunctionExpression") && !v.async && !v.generator && v.params.length >= 1 && v.params.length <= 3 && !usesThis(v) && countNodes(v) < 80;
  let edits = 0;
  for (let i = body.length - 1; i >= 0; i -= 1) {
    const st = body[i];
    const objects = [];
    if (st.type === "VariableDeclaration" && st.declarations.length === 1 && st.declarations[0].init) objects.push(st.declarations[0].init);
    if (st.type === "ExpressionStatement" && st.expression.type === "AssignmentExpression" && st.expression.operator === "=") objects.push(st.expression.right);
    const o = objects[0];
    if (!o || o.type !== "ObjectExpression" || o.properties.length < 2 || o.properties.length > 4) continue;
    if (!o.properties.every((p) => p.type === "Property" && p.kind === "init" && !p.computed && !p.method && small(p.value))) continue;
    const decls = [];
    for (const p of o.properties) {
      const name = next();
      const fn = p.value;
      const fnBody = fn.body.type === "BlockStatement" ? fn.body : { type: "BlockStatement", body: [{ type: "ReturnStatement", argument: fn.body }] };
      decls.push({ type: "FunctionDeclaration", id: { type: "Identifier", name }, params: fn.params, body: fnBody, generator: false, async: false });
      p.value = { type: "Identifier", name };
      p.shorthand = false;
      edits += 1;
    }
    body.splice(i, 0, ...decls);
  }
  return edits;
}

function countNodes(root) {
  let n = 0;
  walk(root, () => { n += 1; return n < 200; });
  return n;
}

// ------------------------------------------------------------------------------------------------
// M7: control-flow flattening. A function whose body is a plain statement list is turned into a
// dispatcher: `var c = 0; while (c < N) switch (c) { case 0: c = 1; s0; continue; ... }`. The same
// statements run in the same order. Refused (left alone): async/generator functions, bodies with
// function or class declarations or let/const at the top (a switch case block is re-entered on each
// pass, so those would change scope), and any label. Leading directives ("use strict") stay first.
// A plain `break`/`continue` inside a statement targets a loop or switch inside that statement, and
// `return` still leaves the function, so control flow is unchanged.
export function flattenFunctions(program, random) {
  const w = topWrapper(program);
  const container = w && w.callee.body.type === "BlockStatement" ? w.callee.body.body : program.body;
  const used = new Set();
  walk(program, ({ node }) => { if (node.type === "Identifier") used.add(node.name); return true; });
  const next = minifiedNames(used, random);
  const targets = [];
  const isFlatFn = (fn) => fn && fn.type === "FunctionExpression" && fn.body.type === "BlockStatement";
  for (const st of container) {
    if (st.type === "FunctionDeclaration" && st.body.type === "BlockStatement") targets.push(st);
    if (st.type === "VariableDeclaration") st.declarations.forEach((d) => { if (isFlatFn(d.init)) targets.push(d.init); });
    if (st.type === "ExpressionStatement" && st.expression.type === "AssignmentExpression" && isFlatFn(st.expression.right)) targets.push(st.expression.right);
  }
  let edits = 0;
  for (const fn of targets) {
    if (random() < 0.3) continue;
    if (flattenOne(fn, next)) edits += 1;
  }
  return edits;
}

function flattenOne(fn, next) {
  if (fn.async || fn.generator) return false;
  const all = fn.body.body;
  let d = 0;
  while (d < all.length && all[d].type === "ExpressionStatement" && all[d].expression.type === "Literal" && typeof all[d].expression.value === "string") d += 1;
  const directives = all.slice(0, d);
  const stmts = all.slice(d);
  if (stmts.length < 3 || stmts.length > 40) return false;
  for (const st of stmts) {
    if (st.type === "FunctionDeclaration" || st.type === "ClassDeclaration" || st.type === "LabeledStatement") return false;
    if (st.type === "VariableDeclaration" && st.kind !== "var") return false;
  }
  let labelled = false;
  walk(fn.body, ({ node }) => {
    if (labelled) return false;
    if (node.type === "LabeledStatement" || ((node.type === "BreakStatement" || node.type === "ContinueStatement") && node.label)) { labelled = true; return false; }
    return true;
  });
  if (labelled) return false;
  const ctrl = next();
  const id = () => ({ type: "Identifier", name: ctrl });
  const cases = stmts.map((st, i) => ({
    type: "SwitchCase",
    test: { type: "Literal", value: i },
    consequent: [
      { type: "ExpressionStatement", expression: { type: "AssignmentExpression", operator: "=", left: id(), right: { type: "Literal", value: i + 1 } } },
      st,
      { type: "ContinueStatement", label: null },
    ],
  }));
  fn.body.body = [
    ...directives,
    { type: "VariableDeclaration", kind: "var", declarations: [{ type: "VariableDeclarator", id: id(), init: { type: "Literal", value: 0 } }] },
    {
      type: "WhileStatement",
      test: { type: "BinaryExpression", operator: "<", left: id(), right: { type: "Literal", value: stmts.length } },
      body: { type: "SwitchStatement", discriminant: id(), cases },
    },
  ];
  return true;
}

// Names a function binds in its own scope: parameters, hoisted `var`s and function declarations
// (not inside nested functions), and top-level let/const/class.
function declaredInFunction(fn) {
  const names = new Set();
  fn.params.forEach((p) => bindingTargets(p).forEach((id) => names.add(id.name)));
  if (fn.body.type !== "BlockStatement") return names;
  const stack = fn.body.body.slice();
  while (stack.length) {
    const node = stack.pop();
    if (!node || typeof node.type !== "string") continue;
    if (node.type === "FunctionDeclaration") { if (node.id) names.add(node.id.name); continue; }
    if (node.type === "FunctionExpression" || node.type === "ArrowFunctionExpression" || node.type === "ClassExpression") continue;
    if (node.type === "ClassDeclaration" && node.id && fn.body.body.includes(node)) names.add(node.id.name);
    if (node.type === "VariableDeclaration" && (node.kind === "var" || fn.body.body.includes(node))) {
      node.declarations.forEach((d) => bindingTargets(d.id).forEach((id) => names.add(id.name)));
    }
    for (const { node: child } of childEntries(node)) stack.push(child);
  }
  return names;
}

function afterDirectives(body) {
  let at = 0;
  while (at < body.length && body[at].type === "ExpressionStatement" && body[at].expression.type === "Literal" && typeof body[at].expression.value === "string") at += 1;
  return at;
}

// ------------------------------------------------------------------------------------------------
// M8: encoding form. Inside the wrapper, every reference to the GLOBAL encodeURIComponent /
// decodeURIComponent (not a local of that name, not a property) goes through a fresh wrapper-level
// function `function x(s) { return encodeURIComponent(s); }`. Both use only their first argument,
// so calls and values passed around (`.map(...)`) behave the same; only the shape of how a value
// is encoded changes (ejs fix #54 was about this step).
export function wrapEncoding(program, random, options = {}) {
  const w = topWrapper(program);
  const body = w && w.callee.body.type === "BlockStatement" ? w.callee.body.body : null;
  if (!body) return 0;
  const { bindings } = resolveScopes(program);
  const local = new Set();
  bindings.forEach((b) => b.nodes.forEach((n) => local.add(n)));
  const used = new Set();
  walk(program, ({ node }) => { if (node.type === "Identifier") used.add(node.name); return true; });
  const next = minifiedNames(used, random);
  const wrappers = new Map();
  let edits = 0;
  for (const statement of body) {
    walk(statement, ({ parent, key, node }) => {
      if (node.type !== "Identifier" || (node.name !== "encodeURIComponent" && node.name !== "decodeURIComponent")) return true;
      if (local.has(node)) return true;
      if (parent && parent.type === "MemberExpression" && key === "property" && !parent.computed) return true;
      if (parent && (parent.type === "Property" || parent.type === "MethodDefinition") && key === "key" && !parent.computed) return true;
      if (!wrappers.has(node.name)) wrappers.set(node.name, next());
      node.name = wrappers.get(node.name);
      edits += 1;
      return true;
    });
  }
  const decls = [...wrappers.entries()].map(([global, name]) => ({
    type: "FunctionDeclaration", id: { type: "Identifier", name }, params: [{ type: "Identifier", name: "s" }], generator: false, async: false,
    body: { type: "BlockStatement", body: [{ type: "ReturnStatement", argument: { type: "CallExpression", optional: false, callee: { type: "Identifier", name: global }, arguments: [{ type: "Identifier", name: "s" }] } }] },
  }));
  // At the end of the wrapper body by default: declarations are hoisted, so they exist from the
  // first statement on, and the player's own prelude keeps its place. `atTop` (M8t) puts them
  // first instead, so they are the first encode/decode helpers a reader meets.
  if (options.atTop) body.splice(afterDirectives(body), 0, ...decls);
  else body.push(...decls);
  return edits;
}

// ------------------------------------------------------------------------------------------------
// M9: browser objects read at init (the #56 class: a player starts reading a browser global in its
// setup). At the top of the wrapper, values are read from navigator, location, document and window
// and stored in fresh variables nothing else uses. In a browser these globals exist, so behaviour
// is unchanged; a sandbox that lacks one throws there. A read is added only when its root name is a
// global at that point (not a parameter or local of the wrapper, such as `var window = this`).
export function readBrowserObjects(program, random) {
  const w = topWrapper(program);
  const body = w && w.callee.body.type === "BlockStatement" ? w.callee.body.body : null;
  if (!body) return 0;
  const declared = declaredInFunction(w.callee);
  const used = new Set();
  walk(program, ({ node }) => { if (node.type === "Identifier") used.add(node.name); return true; });
  const next = minifiedNames(used, random);
  const member = (root, prop) => ({ type: "MemberExpression", computed: false, optional: false, object: { type: "Identifier", name: root }, property: { type: "Identifier", name: prop } });
  const reads = [
    ["navigator", member("navigator", "userAgent")],
    ["location", member("location", "hostname")],
    ["document", { type: "CallExpression", optional: false, callee: member("document", "createElement"), arguments: [{ type: "Literal", value: "div" }] }],
    ["window", member("window", "top")],
  ].filter(([root]) => !declared.has(root));
  const decls = reads.map(([, init]) => ({ type: "VariableDeclaration", kind: "var", declarations: [{ type: "VariableDeclarator", id: { type: "Identifier", name: next() }, init }] }));
  // In the middle of the init code, as a real change would add it (M11 covers the top).
  body.splice(Math.max(afterDirectives(body), Math.floor(body.length / 2)), 0, ...decls);
  return decls.length;
}

// ------------------------------------------------------------------------------------------------
// M11: a new prelude. A few harmless declarations (literal values nothing reads) are added at the
// top of the wrapper body, before the player's own first statements, as a new build adding config
// or flags at the top would. Behaviour is unchanged; it tests whether a solver depends on the
// position of the player's first statements.
export function addPrelude(program, random, options = {}) {
  const w = topWrapper(program);
  const body = w && w.callee.body.type === "BlockStatement" ? w.callee.body.body : null;
  if (!body) return 0;
  const used = new Set();
  walk(program, ({ node }) => { if (node.type === "Identifier") used.add(node.name); return true; });
  const next = minifiedNames(used, random);
  const values = [{ type: "Literal", value: 0 }, { type: "Literal", value: "" }, { type: "ArrayExpression", elements: [] }];
  const decls = options.functions
    ? values.map((init) => ({ type: "FunctionDeclaration", id: { type: "Identifier", name: next() }, params: [{ type: "Identifier", name: "v" }], generator: false, async: false, body: { type: "BlockStatement", body: [{ type: "ReturnStatement", argument: init }] } }))
    : values.map((init) => ({ type: "VariableDeclaration", kind: "var", declarations: [{ type: "VariableDeclarator", id: { type: "Identifier", name: next() }, init }] }));
  body.splice(afterDirectives(body), 0, ...decls);
  return decls.length;
}
