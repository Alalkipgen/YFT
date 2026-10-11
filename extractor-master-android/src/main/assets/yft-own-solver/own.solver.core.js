/*
 * YFT own YouTube n/sig solver core (Master, Phase 1.1 option A; MASTER_KEY_PHASE1_1_PLAN.md).
 *
 * YFT's own code. Its one library is the meriyah JavaScript parser (ISC), loaded first as the
 * global `meriyah`. No part of a third-party extractor is used.
 *
 * It never cuts one internal function out of the player. It runs the player's whole top-level
 * code inside this worker (G1), finds every function that builds a stream URL with structure
 * patterns over a normalised view of the code (G2), and asks each of them to apply the n and sig
 * transforms to a test URL (multiTry), with encoding-aware input and output (G3), in a
 * browser-like global environment (G4). SelfCheck accepts the values of a kind only when every
 * candidate agrees and every value has the expected shape; otherwise the kind FAILs and its
 * streams are dropped. Nothing is ever guessed.
 *
 *   yftOwnSolver.prepare(playerText)      -> {ok: true, program, info} | {ok: false, failed}
 *   yftOwnSolver.run(prepared, request)   -> {n: {input: output}, sig: {...}, failed?, report}
 *   yftOwnSolve({player, n: [], sig: []}) -> prepare + run in one call
 *
 * Everything is synchronous and stays in this worker: no network, no cookies, no storage, no
 * host page. Reports and failure reasons name kinds, candidate indexes, counts and error classes
 * only, never a solver input, an output or player text.
 */
var yftOwnSolver = (function () {
  "use strict";

  var VERSION = 1;
  var TEST_PAGE = "https://www.youtube.com/watch?v=yft-test";
  var DEFAULT_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/129.0.0.0 Mobile Safari/537.36";
  var REGISTRY = "__yftOwnRegistry$";
  var CAUGHT = "__yftOwnCaught$";
  var MAX_CANDIDATES = 16;
  var KINDS = ["n", "sig"];
  var URL_SAFE = /^[A-Za-z0-9_-]+$/;
  var has = Object.prototype.hasOwnProperty;

  // Fixed probes (never session values). 64 distinct URL-safe characters reveal the sig index map;
  // the edge probe has the same length and carries the characters that encoding bugs mangle.
  var SIG_MAP_PROBE = "0HrxAZWdNDCIYsVzBaQXGSqvf-Mhm14kFbj2giOwPeUEu7l5LcJ_R3t6yK9o8nTp";
  var SIG_EDGE_PROBE = "%41/=+&?# \t%zz\nabcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVW";
  var N_PROBES = ["Qx7rTb2LmNc9Pd4s", "Hk3VwZ8yAe1GfJ6uRq0"];

  function Failure(reason) {
    this.reason = reason;
  }

  function errorName(error) {
    if (error instanceof Failure) return error.reason;
    var name = error && typeof error.name === "string" ? error.name : typeof error;
    return /^[A-Za-z]{1,40}$/.test(name) ? name : "Error";
  }

  // ---------------------------------------------------------------------------------------------
  // Parse

  function parseProgram(text) {
    var parser = typeof meriyah !== "undefined" ? meriyah : null;
    if (!parser || typeof parser.parse !== "function") throw new Failure("no parser");
    if (typeof text !== "string" || text.length < 1000) throw new Failure("no player");
    try {
      return parser.parse(text, { ranges: true, webcompat: true });
    } catch (error) {
      throw new Failure("parse");
    }
  }

  // ---------------------------------------------------------------------------------------------
  // G1 unwrap: the known top-level wrappers of a player build, and nothing else.
  //   var _yt_player = {}; (function (g) { ... })(_yt_player);
  //   (function () { ... }).call(this);      (function () { ... })();      ((g) => { ... })(ns);

  function unwrap(ast) {
    var body = ast.body.filter(function (statement) {
      return statement.type !== "EmptyStatement";
    });
    var index = 0;
    while (index < body.length && isDirective(body[index])) index += 1;
    var rest = body.slice(index);
    var wrapper = null;
    if (rest.length === 2 && isNamespaceDeclaration(rest[0])) wrapper = wrappedFunction(rest[1]);
    else if (rest.length === 1) wrapper = wrappedFunction(rest[0]);
    if (!wrapper || wrapper.body.body.length < 10) throw new Failure("unexpected structure");
    return wrapper.body.body;
  }

  function isDirective(statement) {
    return (
      statement.type === "ExpressionStatement" &&
      statement.expression.type === "Literal" &&
      typeof statement.expression.value === "string"
    );
  }

  function isNamespaceDeclaration(statement) {
    return (
      statement.type === "VariableDeclaration" &&
      statement.declarations.length > 0 &&
      statement.declarations.every(function (declarator) {
        var init = declarator.init;
        return (
          declarator.id.type === "Identifier" &&
          !!init &&
          (init.type === "ObjectExpression" ||
            (init.type === "LogicalExpression" && init.right.type === "ObjectExpression"))
        );
      })
    );
  }

  function wrappedFunction(statement) {
    if (statement.type !== "ExpressionStatement") return null;
    var expression = statement.expression;
    if (expression.type === "UnaryExpression" && (expression.operator === "!" || expression.operator === "void")) {
      expression = expression.argument;
    }
    if (expression.type !== "CallExpression") return null;
    var callee = expression.callee;
    if (
      callee.type === "MemberExpression" &&
      !callee.computed &&
      callee.property.name === "call" &&
      expression.arguments.length >= 1 &&
      expression.arguments[0].type === "ThisExpression"
    ) {
      callee = callee.object;
    }
    var isFunction =
      callee.type === "FunctionExpression" ||
      (callee.type === "ArrowFunctionExpression" && callee.body.type === "BlockStatement");
    if (!isFunction || callee.async || callee.generator || callee.params.length > 2) return null;
    return callee;
  }

  // ---------------------------------------------------------------------------------------------
  // G2 normalisation, for matching only (the program is never rewritten from it).

  function isFunctionNode(node) {
    return (
      !!node &&
      (node.type === "FunctionExpression" ||
        node.type === "ArrowFunctionExpression" ||
        node.type === "FunctionDeclaration")
    );
  }

  // Flattened `a = b`, `a = b = c` and `a = 1, b = 2`.
  function assignments(expression) {
    var out = [];
    (function visit(node) {
      if (node.type === "SequenceExpression") node.expressions.forEach(visit);
      else if (node.type === "AssignmentExpression" && node.operator === "=") {
        out.push(node);
        visit(node.right);
      }
    })(expression);
    return out;
  }

  // String tables: `T = "a;b;c".split(";")` or an array of string literals, assigned once at the
  // top level. They let `x[T[3]]` match like `x.name`.
  function stringTables(statements) {
    var tables = Object.create(null);
    var seen = Object.create(null);
    function consider(target, value) {
      if (!target || target.type !== "Identifier") return;
      var name = target.name;
      if (seen[name]) {
        delete tables[name];
        return;
      }
      seen[name] = true;
      var strings = tableValue(value);
      if (strings) tables[name] = strings;
    }
    statements.forEach(function (statement) {
      if (statement.type === "VariableDeclaration") {
        statement.declarations.forEach(function (declarator) {
          if (declarator.init) consider(declarator.id, declarator.init);
        });
      } else if (statement.type === "ExpressionStatement") {
        assignments(statement.expression).forEach(function (assignment) {
          consider(assignment.left, assignment.right);
        });
      }
    });
    return tables;
  }

  function isStringLiteral(node) {
    return !!node && node.type === "Literal" && typeof node.value === "string";
  }

  function tableValue(node) {
    if (
      node.type === "CallExpression" &&
      node.arguments.length === 1 &&
      node.callee.type === "MemberExpression" &&
      plainPropertyName(node.callee) === "split" &&
      isStringLiteral(node.callee.object) &&
      isStringLiteral(node.arguments[0])
    ) {
      return node.callee.object.value.split(node.arguments[0].value);
    }
    if (node.type === "ArrayExpression" && node.elements.length >= 8 && node.elements.every(isStringLiteral)) {
      return node.elements.map(function (element) {
        return element.value;
      });
    }
    return null;
  }

  function plainPropertyName(member) {
    if (!member.computed) return member.property.type === "Identifier" ? member.property.name : null;
    return isStringLiteral(member.property) ? member.property.value : null;
  }

  // A string a node always evaluates to (literal, plain template, table entry, concatenation).
  function stringValue(node, tables, locals) {
    if (!node) return null;
    if (isStringLiteral(node)) return node.value;
    if (node.type === "TemplateLiteral" && node.expressions.length === 0) return node.quasis[0].value.cooked;
    if (
      node.type === "MemberExpression" &&
      node.computed &&
      node.object.type === "Identifier" &&
      node.object.name in tables &&
      !(locals && locals[node.object.name]) &&
      node.property.type === "Literal" &&
      typeof node.property.value === "number"
    ) {
      var value = tables[node.object.name][node.property.value];
      return typeof value === "string" ? value : null;
    }
    if (node.type === "BinaryExpression" && node.operator === "+") {
      var left = stringValue(node.left, tables, locals);
      var right = left === null ? null : stringValue(node.right, tables, locals);
      return left !== null && right !== null ? left + right : null;
    }
    return null;
  }

  // `x.name`, `x["name"]` and `x[T[3]]` alike.
  function propertyName(member, tables, locals) {
    if (!member || member.type !== "MemberExpression") return null;
    if (!member.computed) return member.property.type === "Identifier" ? member.property.name : null;
    return stringValue(member.property, tables, locals);
  }

  // Names a function binds itself (parameters and declarations outside nested functions).
  function localNames(fn) {
    var names = Object.create(null);
    function bindPattern(pattern) {
      if (!pattern) return;
      if (pattern.type === "Identifier") names[pattern.name] = true;
      else if (pattern.type === "AssignmentPattern") bindPattern(pattern.left);
      else if (pattern.type === "RestElement") bindPattern(pattern.argument);
      else if (pattern.type === "ArrayPattern") pattern.elements.forEach(bindPattern);
      else if (pattern.type === "ObjectPattern") {
        pattern.properties.forEach(function (property) {
          bindPattern(property.type === "RestElement" ? property.argument : property.value);
        });
      }
    }
    fn.params.forEach(bindPattern);
    walk(fn.body, function (node) {
      if (node.type === "VariableDeclarator") bindPattern(node.id);
      else if (node.type === "FunctionDeclaration" && node.id) names[node.id.name] = true;
      return !isFunctionNode(node) || node === fn.body;
    });
    return names;
  }

  // A function body as flat steps: sequences split (also in a return), declarators as
  // assignments, arrow expression bodies as a return. Only unconditional steps are listed.
  function flatSteps(fn) {
    var steps = [];
    function pushExpression(expression) {
      if (expression.type === "SequenceExpression") expression.expressions.forEach(pushExpression);
      else steps.push({ expr: expression });
    }
    var statements = fn.body.type === "BlockStatement" ? fn.body.body : [{ type: "ReturnStatement", argument: fn.body }];
    statements.forEach(function (statement) {
      if (statement.type === "ExpressionStatement") pushExpression(statement.expression);
      else if (statement.type === "VariableDeclaration") {
        statement.declarations.forEach(function (declarator) {
          if (declarator.init) steps.push({ expr: declarator.init, binds: declarator.id });
        });
      } else if (statement.type === "ReturnStatement" && statement.argument) {
        var argument = statement.argument;
        if (argument.type === "SequenceExpression") {
          argument.expressions.slice(0, -1).forEach(pushExpression);
          argument = argument.expressions[argument.expressions.length - 1];
        }
        steps.push({ ret: argument });
      }
    });
    return steps;
  }

  function lastOfSequence(node) {
    return node.type === "SequenceExpression" ? node.expressions[node.expressions.length - 1] : node;
  }

  // Source text that names a binding: `f`, `a.b.c`, `a["b"]`. Anything else has no stable name.
  function referenceText(node, source) {
    var cursor = node;
    while (cursor.type === "MemberExpression") {
      if (cursor.computed ? !isStringLiteral(cursor.property) : cursor.property.type !== "Identifier") return null;
      cursor = cursor.object;
    }
    if (cursor.type !== "Identifier") return null;
    return source.slice(node.start, node.end);
  }

  // Every top-level function definition, in any of its forms.
  function definitions(statements, source) {
    var found = [];
    function add(target, value) {
      if (!isFunctionNode(value)) return;
      var reference = referenceText(target, source);
      if (reference) found.push({ ref: reference, fn: value });
    }
    statements.forEach(function (statement) {
      if (statement.type === "FunctionDeclaration" && statement.id) {
        found.push({ ref: statement.id.name, fn: statement });
      } else if (statement.type === "VariableDeclaration") {
        statement.declarations.forEach(function (declarator) {
          if (declarator.init) add(declarator.id, declarator.init);
        });
      } else if (statement.type === "ExpressionStatement") {
        assignments(statement.expression).forEach(function (assignment) {
          add(assignment.left, assignment.right);
        });
      }
    });
    return found;
  }

  // Child keys per ESTree node type (identifiers in binding or label position are skipped).
  var CHILD_KEYS = {
    Program: ["body"],
    ExpressionStatement: ["expression"],
    BlockStatement: ["body"],
    StaticBlock: ["body"],
    WithStatement: ["object", "body"],
    ReturnStatement: ["argument"],
    LabeledStatement: ["body"],
    IfStatement: ["test", "consequent", "alternate"],
    SwitchStatement: ["discriminant", "cases"],
    SwitchCase: ["test", "consequent"],
    ThrowStatement: ["argument"],
    TryStatement: ["block", "handler", "finalizer"],
    CatchClause: ["param", "body"],
    WhileStatement: ["test", "body"],
    DoWhileStatement: ["body", "test"],
    ForStatement: ["init", "test", "update", "body"],
    ForInStatement: ["left", "right", "body"],
    ForOfStatement: ["left", "right", "body"],
    FunctionDeclaration: ["params", "body"],
    FunctionExpression: ["params", "body"],
    ArrowFunctionExpression: ["params", "body"],
    VariableDeclaration: ["declarations"],
    VariableDeclarator: ["id", "init"],
    ClassDeclaration: ["superClass", "body"],
    ClassExpression: ["superClass", "body"],
    ClassBody: ["body"],
    MethodDefinition: ["key", "value"],
    PropertyDefinition: ["key", "value"],
    ArrayExpression: ["elements"],
    ObjectExpression: ["properties"],
    Property: ["key", "value"],
    UnaryExpression: ["argument"],
    UpdateExpression: ["argument"],
    BinaryExpression: ["left", "right"],
    LogicalExpression: ["left", "right"],
    AssignmentExpression: ["left", "right"],
    ConditionalExpression: ["test", "consequent", "alternate"],
    CallExpression: ["callee", "arguments"],
    NewExpression: ["callee", "arguments"],
    MemberExpression: ["object", "property"],
    SequenceExpression: ["expressions"],
    TemplateLiteral: ["expressions"],
    TaggedTemplateExpression: ["tag", "quasi"],
    SpreadElement: ["argument"],
    RestElement: ["argument"],
    YieldExpression: ["argument"],
    AwaitExpression: ["argument"],
    AssignmentPattern: ["left", "right"],
    ArrayPattern: ["elements"],
    ObjectPattern: ["properties"],
    ChainExpression: ["expression"],
    ParenthesizedExpression: ["expression"],
    ImportExpression: ["source"],
    Identifier: [],
    Literal: [],
    ThisExpression: [],
    Super: [],
    PrivateIdentifier: [],
    TemplateElement: [],
    EmptyStatement: [],
    DebuggerStatement: [],
    BreakStatement: [],
    ContinueStatement: [],
    MetaProperty: [],
  };

  function pushChild(stack, child) {
    if (!child || typeof child !== "object") return;
    if (Array.isArray(child)) {
      for (var i = child.length - 1; i >= 0; i -= 1) {
        if (child[i] && typeof child[i].type === "string") stack.push(child[i]);
      }
    } else if (typeof child.type === "string") {
      stack.push(child);
    }
  }

  // Iterative walk over ESTree nodes. [visit] returns false to skip a node's children.
  function walk(root, visit) {
    var stack = [root];
    while (stack.length) {
      var node = stack.pop();
      if (!node || typeof node.type !== "string") continue;
      if (visit(node) === false) continue;
      var keys = CHILD_KEYS[node.type];
      if (keys) {
        for (var k = keys.length - 1; k >= 0; k -= 1) pushChild(stack, node[keys[k]]);
      } else {
        for (var key in node) {
          if (key !== "start" && key !== "end" && key !== "range" && key !== "loc") pushChild(stack, node[key]);
        }
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // G2 candidates: every function that builds a stream URL, found two independent ways.

  // Pattern A, definitions: the function makes a URL object, marks it with
  // `<local>.<any>("alr", "yes")` as an unconditional step and returns that object.
  function builderDefinitions(found, tables) {
    var out = [];
    found.forEach(function (definition) {
      var fn = definition.fn;
      if (fn.params.length < 1 || fn.params.length > 4 || fn.async || fn.generator) return;
      var steps = flatSteps(fn);
      var marker = null;
      for (var i = 0; i < steps.length && !marker; i += 1) {
        var expression = steps[i].expr;
        if (
          expression &&
          expression.type === "CallExpression" &&
          expression.arguments.length === 2 &&
          expression.callee.type === "MemberExpression" &&
          expression.callee.object.type === "Identifier" &&
          stringValue(expression.arguments[0], tables, null) === "alr"
        ) {
          marker = expression;
        }
        if (marker) {
          // Only now is the scope worth computing: the object must be the function's own binding.
          var locals = localNames(fn);
          if (
            !locals[marker.callee.object.name] ||
            stringValue(marker.arguments[0], tables, locals) !== "alr" ||
            stringValue(marker.arguments[1], tables, locals) !== "yes"
          ) {
            marker = null;
          }
        }
      }
      if (!marker) return;
      var marked = marker.callee.object.name;
      var returnsIt = steps.some(function (step) {
        if (!step.ret) return false;
        var value = lastOfSequence(step.ret);
        return value.type === "Identifier" && value.name === marked;
      });
      if (returnsIt) out.push(definition.ref);
    });
    return out;
  }

  // Pattern B, call sites: `f(<...url...>, <sp>, <x.s>)`, the way a signatureCipher becomes a URL.
  function builderCallSites(statements, byReference, tables, source) {
    var out = [];
    statements.forEach(function (statement) {
      walk(statement, function (node) {
        if (node.type !== "CallExpression" || node.arguments.length < 3) return true;
        var third = node.arguments[2];
        if (third.type !== "MemberExpression" || propertyName(third, tables, null) !== "s") return true;
        if (!mentionsProperty(node.arguments[0], "url", tables)) return true;
        var callee = lastOfSequence(node.callee);
        var reference = callee.type === "Identifier" || callee.type === "MemberExpression" ? referenceText(callee, source) : null;
        if (reference && byReference[reference] && out.indexOf(reference) < 0) out.push(reference);
        return true;
      });
    });
    return out;
  }

  function mentionsProperty(node, name, tables) {
    var found = false;
    var budget = 24;
    walk(node, function (child) {
      if (found || (budget -= 1) < 0) return false;
      if (child.type === "MemberExpression" && propertyName(child, tables, null) === name) found = true;
      return !isFunctionNode(child);
    });
    return found;
  }

  // ---------------------------------------------------------------------------------------------
  // G1 program: the player's top level with only declarations, assignments and control
  // statements kept. Other expression statements (calls, `new`, `a && b()`) are side effects
  // and are dropped. Everything that can throw runs in its own `try`, one declarator or one
  // assignment at a time, so a single failing statement (a browser API the worker lacks) cannot
  // stop the rest; failures are only counted. The candidates are read at the end of the top level.

  var GUARDED_STATEMENTS = {
    IfStatement: true,
    TryStatement: true,
    ForStatement: true,
    ForInStatement: true,
    ForOfStatement: true,
    WhileStatement: true,
    DoWhileStatement: true,
    LabeledStatement: true,
    BlockStatement: true,
    SwitchStatement: true,
  };

  function guard(text) {
    return "try{" + text + "\n}catch(" + CAUGHT + "){" + REGISTRY + ".e++}\n";
  }

  function buildProgram(source, statements, references) {
    var parts = [source.slice(0, statements[0].start)];
    var kept = 0;
    var dropped = 0;
    statements.forEach(function (statement) {
      var text = source.slice(statement.start, statement.end);
      if (statement.type === "FunctionDeclaration" || statement.type === "ClassDeclaration") {
        parts.push(text, "\n");
      } else if (statement.type === "VariableDeclaration") {
        if (statement.kind !== "var") parts.push(text, "\n;\n");
        else {
          statement.declarations.forEach(function (declarator) {
            var part = "var " + source.slice(declarator.start, declarator.end) + ";";
            parts.push(declarator.init ? guard(part) : part + "\n");
          });
        }
      } else if (statement.type === "ExpressionStatement") {
        var expression = statement.expression;
        if (isStringLiteral(expression)) parts.push(text, "\n;\n");
        else if (expression.type === "AssignmentExpression") parts.push(guard(text));
        else if (expression.type === "SequenceExpression" && expression.expressions.every(isAssignment)) {
          expression.expressions.forEach(function (part) {
            parts.push(guard(source.slice(part.start, part.end) + ";"));
          });
        } else {
          dropped += 1;
          return;
        }
      } else if (GUARDED_STATEMENTS[statement.type]) {
        parts.push(guard(text));
      } else {
        dropped += 1;
        return;
      }
      kept += 1;
    });
    references.forEach(function (reference, index) {
      parts.push("try{" + REGISTRY + ".c[" + index + "]=" + reference + "}catch(" + CAUGHT + "){}\n");
    });
    parts.push(source.slice(statements[statements.length - 1].end));
    return { text: parts.join(""), kept: kept, dropped: dropped };
  }

  function isAssignment(node) {
    return node.type === "AssignmentExpression";
  }

  function prepare(playerText) {
    try {
      var ast = parseProgram(playerText);
      var statements = unwrap(ast);
      var tables = stringTables(statements);
      var found = definitions(statements, playerText);
      var byReference = Object.create(null);
      found.forEach(function (definition) {
        byReference[definition.ref] = true;
      });
      var fromDefinitions = builderDefinitions(found, tables);
      var fromCallSites = builderCallSites(statements, byReference, tables, playerText);
      var references = fromDefinitions.slice();
      fromCallSites.forEach(function (reference) {
        if (references.indexOf(reference) < 0) references.push(reference);
      });
      if (!references.length) throw new Failure("no candidates");
      references = references.slice(0, MAX_CANDIDATES);
      var program = buildProgram(playerText, statements, references);
      return {
        ok: true,
        version: VERSION,
        program: program.text,
        info: {
          statements: statements.length,
          kept: program.kept,
          dropped: program.dropped,
          tables: Object.keys(tables).length,
          candidates: references.length,
          fromDefinitions: fromDefinitions.length,
          fromCallSites: fromCallSites.length,
        },
      };
    } catch (error) {
      return { ok: false, failed: errorName(error) };
    }
  }

  // ---------------------------------------------------------------------------------------------
  // G4 environment: browser-like globals, each an own property of this worker's global object,
  // so nothing reaches the host page. The page URL is a test URL; nothing navigates.

  function makeLocation(href) {
    var match = /^(https?:)\/\/([^/?#]+)([^?#]*)(\?[^#]*)?(#.*)?$/.exec(href);
    var location = {
      href: href,
      protocol: match[1],
      host: match[2],
      hostname: match[2],
      port: "",
      pathname: match[3] || "/",
      search: match[4] || "",
      hash: match[5] || "",
      origin: match[1] + "//" + match[2],
      ancestorOrigins: [],
    };
    location.toString = function () {
      return href;
    };
    location.assign = location.replace = location.reload = function () {};
    return location;
  }

  function environmentGlobals(scope, env) {
    var location = makeLocation(TEST_PAGE);
    var language = (env && env.language) || "en-US";
    return {
      window: scope,
      self: scope,
      location: location,
      navigator: {
        userAgent: (env && env.userAgent) || DEFAULT_USER_AGENT,
        language: language,
        languages: [language, "en"],
        platform: "Linux armv8l",
        vendor: "Google Inc.",
        cookieEnabled: false,
        onLine: true,
        hardwareConcurrency: 4,
        maxTouchPoints: 5,
      },
      document: {
        location: location,
        URL: TEST_PAGE,
        documentURI: TEST_PAGE,
        referrer: "",
        domain: location.hostname,
        cookie: "",
        title: "",
        readyState: "complete",
        visibilityState: "visible",
        hidden: false,
      },
    };
  }

  function installEnvironment(scope, env, omit) {
    var globals = environmentGlobals(scope, env);
    Object.keys(globals).forEach(function (name) {
      if (omit && omit.indexOf(name) >= 0) return;
      Object.defineProperty(scope, name, {
        value: globals[name],
        writable: true,
        configurable: true,
        enumerable: true,
      });
    });
    if (typeof scope.XMLHttpRequest === "undefined" && !(omit && omit.indexOf("XMLHttpRequest") >= 0)) {
      // Read (never used) by some top-level tables; a stand-in without any network.
      Object.defineProperty(scope, "XMLHttpRequest", {
        value: function XMLHttpRequest() {},
        writable: true,
        configurable: true,
      });
    }
  }

  function globalScope() {
    return typeof globalThis !== "undefined" ? globalThis : Function("return this")();
  }

  function instantiate(prepared, request) {
    var faults = request.faults || {};
    installEnvironment(globalScope(), request.env, faults.omit);
    var registry = { c: [], e: 0 };
    var factory;
    try {
      factory = Function(REGISTRY, prepared.program);
    } catch (error) {
      throw new Failure("compile:" + errorName(error));
    }
    try {
      factory(registry);
    } catch (error) {
      throw new Failure("program:" + errorName(error));
    }
    return registry;
  }

  // ---------------------------------------------------------------------------------------------
  // G1 multiTry with G3 encoding: every candidate builds a URL for the test page; n is set on it
  // and read back through every path the URL object offers; sig goes in URI-encoded and comes
  // out decoded exactly once.

  function methodNames(object) {
    var names = [];
    for (var cursor = object; cursor && cursor !== Object.prototype; cursor = Object.getPrototypeOf(cursor)) {
      Object.getOwnPropertyNames(cursor).forEach(function (name) {
        if (name === "constructor" || names.indexOf(name) >= 0) return;
        var descriptor = Object.getOwnPropertyDescriptor(cursor, name);
        if (descriptor && typeof descriptor.value === "function") names.push(name);
      });
    }
    return names;
  }

  // The URL object's parameter setter and getter: `set`/`get` when they round-trip a marker,
  // else any two-argument / one-argument method pair that does.
  function urlAccess(object) {
    if (!object || (typeof object !== "object" && typeof object !== "function")) return null;
    var names = methodNames(object);
    var pairs = [];
    if (names.indexOf("set") >= 0 && names.indexOf("get") >= 0) pairs.push(["set", "get"]);
    names.forEach(function (setter) {
      if (object[setter].length !== 2) return;
      names.forEach(function (getter) {
        if (object[getter].length === 1 && getter !== setter && !(setter === "set" && getter === "get")) {
          pairs.push([setter, getter]);
        }
      });
    });
    for (var i = 0; i < pairs.length && i < 12; i += 1) {
      try {
        object[pairs[i][0]]("yftmark", "1");
        if (object[pairs[i][1]]("yftmark") === "1") {
          return {
            set: pairs[i][0],
            get: pairs[i][1],
            others: names.filter(function (name) {
              return name !== pairs[i][0] && name !== pairs[i][1] && object[name].length === 0;
            }),
          };
        }
      } catch (ignored) {
        // Not a parameter store.
      }
    }
    return null;
  }

  function decodeOnce(value) {
    if (typeof value !== "string") return null;
    try {
      return decodeURIComponent(value);
    } catch (ignored) {
      return null;
    }
  }

  function queryParam(url, key) {
    if (typeof url !== "string") return null;
    var query = url.indexOf("?") >= 0 ? url.slice(url.indexOf("?") + 1).split("#")[0] : "";
    var found = null;
    var count = 0;
    query.split("&").forEach(function (pair) {
      var cut = pair.indexOf("=");
      if (cut > 0 && pair.slice(0, cut) === key) {
        count += 1;
        found = pair.slice(cut + 1);
      }
    });
    return count === 1 ? decodeOnce(found) : null;
  }

  // Values one candidate produces for one input, through each read path: the getter right after
  // building (and setting n), then every zero-argument method of the URL object on a fresh
  // object (the getter again, and the query of a returned URL string). A failing path only
  // loses its own values; failures name the candidate and path indexes.
  function attempt(kind, candidate, input, faults, errors) {
    var key = kind === "sig" ? "s" : "n";
    var raw = kind === "sig" && faults.rawEncoding;
    var encoded = kind === "sig" && !raw ? encodeURIComponent(input) : input;
    var make = function () {
      var object = kind === "sig" ? candidate.fn(TEST_PAGE, "s", encoded) : candidate.fn(TEST_PAGE);
      if (kind === "n") object[access.set]("n", input);
      return object;
    };
    var read = function (object) {
      var value = object[access.get](key);
      return raw ? value : decodeOnce(value);
    };
    var first = kind === "sig" ? candidate.fn(TEST_PAGE, "s", encoded) : candidate.fn(TEST_PAGE);
    var access = urlAccess(first);
    if (!access) throw new Failure("no-url-object");
    if (kind === "n") first[access.set]("n", input);
    var values = [read(first)];
    access.others.forEach(function (name, path) {
      try {
        var object = make();
        var returned = object[name]();
        values.push(read(object));
        if (typeof returned === "string" && !raw) values.push(queryParam(returned, key));
      } catch (error) {
        errors.push(candidate.index + "/" + (path + 1) + ":" + errorName(error));
      }
    });
    return values.filter(function (value) {
      return typeof value === "string" && value.length > 0 && value !== input;
    });
  }

  // One input through every candidate: the distinct values and the failures.
  function multiTry(kind, candidates, input, faults) {
    var values = [];
    var errors = [];
    candidates.forEach(function (candidate) {
      try {
        attempt(kind, candidate, input, faults, errors).forEach(function (value) {
          if (values.indexOf(value) < 0) values.push(value);
        });
      } catch (error) {
        errors.push(candidate.index + ":" + errorName(error));
      }
    });
    return { values: values, errors: errors };
  }

  // ---------------------------------------------------------------------------------------------
  // SelfCheck: all candidates agree, values have their kind's shape, distinct n inputs give
  // distinct outputs, sig keeps the index map of the probes (G3), known vectors match, and the
  // first value is stable at the end. Any failure drops the whole kind.

  function charCounts(text) {
    var counts = Object.create(null);
    for (var i = 0; i < text.length; i += 1) counts[text[i]] = (counts[text[i]] || 0) + 1;
    return counts;
  }

  function shapeOk(kind, input, value) {
    if (kind === "n") {
      return URL_SAFE.test(value) && value !== input && value.length <= input.length * 2 + 8;
    }
    if (value.length > input.length || value.length < input.length - Math.max(16, input.length >> 2)) return false;
    var available = charCounts(input);
    for (var i = 0; i < value.length; i += 1) {
      if (!available[value[i]]) return false;
      available[value[i]] -= 1;
    }
    return true;
  }

  // The positions of the map probe's characters in its output, applied to another input.
  function applyIndexMap(probe, probeOutput, input) {
    var out = "";
    for (var i = 0; i < probeOutput.length; i += 1) {
      var from = probe.indexOf(probeOutput[i]);
      if (from < 0) return null;
      out += input[from];
    }
    return out;
  }

  function solveKind(kind, builders, inputs, request) {
    var faults = request.faults || {};
    var vectors = (request.vectors && request.vectors[kind]) || [];
    var probes = kind === "n" ? N_PROBES.slice() : [SIG_MAP_PROBE, SIG_EDGE_PROBE];
    var all = [];
    probes
      .concat(vectors.map(function (vector) {
        return vector.input;
      }))
      .concat(inputs)
      .forEach(function (input) {
        if (typeof input === "string" && input.length > 0 && all.indexOf(input) < 0) all.push(input);
      });
    var results = Object.create(null);
    var errors = [];
    all.forEach(function (input) {
      var outcome = multiTry(kind, builders, input, faults);
      results[input] = outcome.values;
      outcome.errors.forEach(function (error) {
        if (errors.indexOf(error) < 0) errors.push(error);
      });
    });
    var reason = null;
    var solved = Object.create(null);
    var outputs = [];
    all.forEach(function (input) {
      var values = results[input];
      if (reason || !values.length) return;
      if (values.length > 1) reason = "disagree";
      else if (!shapeOk(kind, input, values[0])) reason = "shape";
      else if (kind === "n" && outputs.indexOf(values[0]) >= 0) reason = "not-distinct";
      else {
        solved[input] = values[0];
        outputs.push(values[0]);
      }
    });
    if (!reason) {
      for (var p = 0; p < probes.length; p += 1) {
        if (!has.call(solved, probes[p])) reason = "probe";
      }
    }
    if (!reason && kind === "sig") {
      var expected = applyIndexMap(SIG_MAP_PROBE, solved[SIG_MAP_PROBE], SIG_EDGE_PROBE);
      if (expected === null || expected !== solved[SIG_EDGE_PROBE]) reason = "encoding";
    }
    if (!reason) {
      vectors.forEach(function (vector) {
        if (!reason && solved[vector.input] !== vector.expected) reason = "vector";
      });
    }
    if (!reason) {
      var again = multiTry(kind, builders, probes[0], faults).values;
      if (again.length !== 1 || again[0] !== solved[probes[0]]) reason = "unstable";
    }
    var answers = {};
    var answered = 0;
    if (!reason) {
      inputs.forEach(function (input) {
        if (has.call(solved, input)) {
          answers[input] = solved[input];
          answered += 1;
        }
      });
    }
    return {
      answers: answers,
      report: {
        status: reason ? "failed" : "ok",
        reason: reason,
        inputs: inputs.length,
        solved: answered,
        errors: errors.slice(0, 8),
      },
    };
  }

  // Fault injection (tests only): a wrong candidate. It wraps the first real candidate and
  // rotates the value of one kind by one character; SelfCheck must see the disagreement.
  function decoyBuilder(real, kind) {
    var key = kind === "sig" ? "s" : "n";
    return function (url, sp, sig) {
      var object = real(url, sp, sig);
      var access = urlAccess(object);
      if (!access) return object;
      var wrapper = {};
      wrapper[access.set] = function (name, value) {
        return object[access.set](name, value);
      };
      wrapper[access.get] = function (name) {
        var value = object[access.get](name);
        if (name !== key || typeof value !== "string" || value.length < 2 || value.indexOf("%") >= 0) return value;
        return value.slice(1) + value.charAt(0);
      };
      return wrapper;
    };
  }

  function run(prepared, request) {
    request = request || {};
    var output = { n: {}, sig: {}, report: { version: VERSION } };
    try {
      if (!prepared || !prepared.ok || typeof prepared.program !== "string") throw new Failure("not prepared");
      var registry = instantiate(prepared, request);
      var builders = [];
      registry.c.forEach(function (fn, index) {
        if (typeof fn === "function") builders.push({ index: index, fn: fn });
      });
      if (request.faults && (request.faults.decoy === "n" || request.faults.decoy === "sig") && builders.length) {
        builders.push({ index: registry.c.length, fn: decoyBuilder(builders[0].fn, request.faults.decoy) });
      }
      output.report.topLevelErrors = registry.e;
      output.report.candidates = builders.length;
      if (!builders.length) throw new Failure("no candidate functions");
      var failed = [];
      KINDS.forEach(function (kind) {
        var inputs = (request[kind] || []).filter(function (input) {
          return typeof input === "string" && input.length > 0;
        });
        if (!inputs.length) return;
        var result = solveKind(kind, builders, inputs, request);
        output[kind] = result.answers;
        output.report[kind] = result.report;
        if (result.report.reason) failed.push(kind + ":" + result.report.reason);
      });
      if (failed.length) output.failed = failed.join(",");
    } catch (error) {
      output.failed = errorName(error);
      output.n = {};
      output.sig = {};
    }
    return output;
  }

  function solve(request) {
    request = request || {};
    var prepared = prepare(request.player);
    if (!prepared.ok) return { n: {}, sig: {}, failed: prepared.failed, report: { version: VERSION } };
    var output = run(prepared, request);
    output.report.prepare = prepared.info;
    return output;
  }

  return {
    version: VERSION,
    prepare: prepare,
    run: run,
    solve: solve,
    internals: {
      unwrap: unwrap,
      stringTables: stringTables,
      definitions: definitions,
      builderDefinitions: builderDefinitions,
      builderCallSites: builderCallSites,
      stringValue: stringValue,
      propertyName: propertyName,
      flatSteps: flatSteps,
      queryParam: queryParam,
      shapeOk: shapeOk,
      applyIndexMap: applyIndexMap,
      environmentGlobals: environmentGlobals,
      installEnvironment: installEnvironment,
      probes: { n: N_PROBES, sigMap: SIG_MAP_PROBE, sigEdge: SIG_EDGE_PROBE },
    },
  };
})();

function yftOwnSolve(request) {
  return yftOwnSolver.solve(request);
}
