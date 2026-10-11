// Phase 1.2 N1 (MASTER_KEY_PHASE1_2_PLAN.md): behaviour-keeping mutations of a player, used by
// scripts/verify-own-solver-mutations.mjs to simulate YouTube changing the SHAPE of its code.
//
// Each mutation keeps what the program DOES; only how it is written changes. meriyah parses and
// astring prints (both vendored in main's yt.solver.lib.min.js; used only at test time, never
// shipped). No player text is ever committed. A scope model (T3) is built before any rename so a
// fresh name can never capture or shadow another binding; identifiers that are globals, or reached
// through eval/with/arguments, are left alone. The caller verifies behaviour with an independent
// truth hook, so a mutation that would change behaviour is skipped, never counted as a wrong value.

export const CLASSES = ["M1", "M1w", "M2", "M3", "M6", "M10"];

export const CLASS_NOTE = {
  M1: "rename every function/block/catch binding; globals and the wrapper parameter stay (a new build)",
  M1w: "as M1, and rename the wrapper parameter too (`g` in `(function(g){...})(_yt_player)`)",
  M2: "toggle dotted <-> computed member access, where the key is a valid name (code generation)",
  M3: "re-wrap the top level in another wrapper form (#53)",
  M4: "hoist a marked builder behind a local alias object (helper reshuffle)",
  M5: "call the URL builder through a fresh local alias (indirect call site)",
  M6: "read fixed string literals from a shuffled table (obfuscation)",
  M10: "append decoy functions that look like n/sig but return other values (false positives)",
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
  M4: () => 0, // defined in the runner (needs builder refs) — placeholder
  M5: () => 0, // defined in the runner (needs builder refs) — placeholder
  M6: (p, r) => tableLiterals(p, r),
  M10: (p, r) => appendDecoys(p, r),
};

export function parseWith(meriyah, text) {
  return meriyah.parse(text, { ranges: false, webcompat: true, loc: false });
}
export function generateWith(astring, ast) {
  return astring.generate(ast);
}
