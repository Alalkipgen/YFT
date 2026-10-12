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
 *   request.page === true (Phase 1.2 N4, strategy E): the same run in the app's sandboxed page
 *   frame, on a real window whose own browser objects stay (see installEnvironment)
 *   report.topLevelErrors / topLevelErrorClasses (N4b): the init errors (guarded top-level
 *   statements that threw) and their counts by error class, e.g. {"TypeError": 2}
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
  // N5b environment tripwire (request.tripwire, worker mode only): once per solve a second
  // instance gets other environment values (another page, user agent, language, platform, cookie
  // setting, ...) and must give the same answers. YouTube's n and sig functions are pure string
  // functions; one whose answer follows the environment would follow the worker's stubs, not a
  // real browser on youtube.com (M9c), so it is not trusted: the trip counts as an init error.
  var TRIPWIRE_PAGE = "https://m.youtube.com/embed/yft-check";
  var TRIPWIRE_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0";
  var TRIPWIRE_INPUTS = 3;
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

  // Each guarded top-level statement that throws is an init error: counted in registry.e and, by
  // error class, by registry.k (Phase 1.2 N4b; a program prepared before N4b only counts).
  function guard(text) {
    return "try{" + text + "\n}catch(" + CAUGHT + "){" + REGISTRY + ".e++;" + REGISTRY + ".k&&" + REGISTRY + ".k(" + CAUGHT + ")}\n";
  }

  var MAX_INIT_ERROR_CLASSES = 8;

  // Init errors by class (names like TypeError only, never a message): at most 8 classes, the
  // rest as "other". It never throws, so it cannot change what the program does.
  function initErrorRecorder(registry) {
    registry.x = Object.create(null);
    var classes = 0;
    return function (error) {
      try {
        var name = errorName(error);
        if (!has.call(registry.x, name)) {
          if (classes >= MAX_INIT_ERROR_CLASSES) name = "other";
          else classes += 1;
        }
        registry.x[name] = (registry.x[name] || 0) + 1;
      } catch (ignored) {
        // A class is only a report detail.
      }
    };
  }

  function initErrorClasses(registry) {
    var out = {};
    Object.keys(registry.x || {}).sort().forEach(function (name) {
      out[name] = registry.x[name];
    });
    return out;
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
  // Page mode (Phase 1.2 N4, strategy E: request.page in the app's sandboxed page frame) runs on a
  // real window: its own browser objects stay (window, document, location and top cannot be
  // redefined there) and only names it lacks are added.

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
    var alt = Boolean(env && env.tripwire === true);
    var page = alt ? TRIPWIRE_PAGE : TEST_PAGE;
    var location = makeLocation(page);
    var language = alt ? "de-DE" : (env && env.language) || "en-US";
    return {
      window: scope,
      self: scope,
      location: location,
      navigator: {
        userAgent: alt ? TRIPWIRE_USER_AGENT : (env && env.userAgent) || DEFAULT_USER_AGENT,
        language: language,
        languages: [language, alt ? "de" : "en"],
        platform: alt ? "Win32" : "Linux armv8l",
        vendor: alt ? "" : "Google Inc.",
        cookieEnabled: alt,
        onLine: !alt,
        hardwareConcurrency: alt ? 8 : 4,
        maxTouchPoints: alt ? 0 : 5,
      },
      document: {
        location: location,
        URL: page,
        documentURI: page,
        referrer: alt ? "https://www.google.com/" : "",
        domain: location.hostname,
        cookie: "",
        title: alt ? "YouTube" : "",
        readyState: alt ? "interactive" : "complete",
        visibilityState: alt ? "hidden" : "visible",
        hidden: alt,
      },
    };
  }

  // A page's own global (page mode keeps it); a getter that throws counts as present.
  function pageHas(scope, name) {
    try {
      return typeof scope[name] !== "undefined";
    } catch (ignored) {
      return true;
    }
  }

  // Worker mode defines exactly as before; page mode never fails on a name the window guards.
  function defineGlobal(scope, name, descriptor, page) {
    if (!page) {
      Object.defineProperty(scope, name, descriptor);
      return;
    }
    try {
      Object.defineProperty(scope, name, descriptor);
    } catch (ignored) {
      // A real window's own property that cannot be redefined: it stays.
    }
  }

  function installEnvironment(scope, env, omit, page) {
    var globals = environmentGlobals(scope, env);
    Object.keys(globals).forEach(function (name) {
      if (omit && omit.indexOf(name) >= 0) return;
      if (page && pageHas(scope, name)) return;
      defineGlobal(scope, name, {
        value: globals[name],
        writable: true,
        configurable: true,
        enumerable: true,
      }, page);
    });
    if (typeof scope.XMLHttpRequest === "undefined" && !(omit && omit.indexOf("XMLHttpRequest") >= 0)) {
      // Read (never used) by some top-level tables; a stand-in without any network.
      defineGlobal(scope, "XMLHttpRequest", {
        value: function XMLHttpRequest() {},
        writable: true,
        configurable: true,
      }, page);
    }
  }

  // N5b tripwire helpers. The second instance's request: other environment values, same faults.
  function tripwireRequest(request) {
    var copy = {};
    Object.keys(request).forEach(function (key) {
      copy[key] = request[key];
    });
    var env = {};
    Object.keys(request.env || {}).forEach(function (key) {
      env[key] = request.env[key];
    });
    env.tripwire = true;
    copy.env = env;
    return copy;
  }

  // Page mode keeps the window's own objects, which cannot be changed: worker mode only.
  function tripwireWanted(request) {
    return request.tripwire === true && request.page !== true;
  }

  function restoreEnvironment(request) {
    var faults = request.faults || {};
    installEnvironment(globalScope(), request.env, faults.omit, request.page === true);
  }

  function tripwireSample(answers) {
    return Object.keys(answers || {}).slice(0, TRIPWIRE_INPUTS);
  }

  // A trip: one more init error (class "Tripwire"), and the tripped kinds give no answer.
  function tripwireResult(output, failed, kinds, tripped) {
    output.report.tripwire = { checked: kinds.length, tripped: tripped.slice() };
    if (!tripped.length) return;
    output.report.topLevelErrors = (output.report.topLevelErrors || 0) + 1;
    var classes = output.report.topLevelErrorClasses || {};
    classes.Tripwire = (classes.Tripwire || 0) + 1;
    output.report.topLevelErrorClasses = classes;
    tripped.forEach(function (kind) {
      output[kind] = {};
      failed.push(kind + ":environment");
    });
  }

  // P: the answered kinds again on a second instance with other environment values.
  function pTripwire(prepared, request, output, failed) {
    var kinds = KINDS.filter(function (kind) {
      return tripwireSample(output[kind]).length > 0;
    });
    if (!kinds.length) return;
    var tripped = [];
    try {
      var other = instantiate(prepared, tripwireRequest(request));
      var builders = [];
      other.c.forEach(function (fn, index) {
        if (typeof fn === "function") builders.push({ index: index, fn: fn });
      });
      if (request.faults && (request.faults.decoy === "n" || request.faults.decoy === "sig") && builders.length) {
        builders.push({ index: other.c.length, fn: decoyBuilder(builders[0].fn, request.faults.decoy) });
      }
      kinds.forEach(function (kind) {
        var same = tripwireSample(output[kind]).every(function (input) {
          var values = initWrongValues(request, kind, multiTry(kind, builders, input, request.faults || {}).values);
          return values.length === 1 && values[0] === output[kind][input];
        });
        if (!same) tripped.push(kind);
      });
    } catch (error) {
      tripped = kinds.slice();
    } finally {
      restoreEnvironment(request);
    }
    tripwireResult(output, failed, kinds, tripped);
  }

  function globalScope() {
    return typeof globalThis !== "undefined" ? globalThis : Function("return this")();
  }

  function instantiate(prepared, request) {
    var faults = request.faults || {};
    installEnvironment(globalScope(), request.env, faults.omit, request.page === true);
    var registry = { c: [], e: 0 };
    registry.k = initErrorRecorder(registry);
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
    faultInitError(registry, request);
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
      results[input] = initWrongValues(request, kind, outcome.values);
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
      var again = initWrongValues(request, kind, multiTry(kind, builders, probes[0], faults).values);
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

  // Fault injection (tests only, Phase 1.2 N4b) "initWrong" ("n" or "sig"): an init error (a
  // TypeError, as from a DOM call the worker lacks) after which every candidate agrees on a
  // wrong value of that kind, each value rotated by one character. SelfCheck cannot see it; the
  // faults suite counts these values as wrong until the ladder keeps such answers unverified.
  // Inert in page mode, whose DOM the init error is about.
  function faultInitWrong(request) {
    var kind = request && request.page !== true && request.faults && request.faults.initWrong;
    return kind === "n" || kind === "sig" ? kind : null;
  }

  function faultInitError(registry, request) {
    if (!faultInitWrong(request)) return;
    registry.e += 1;
    registry.k({ name: "TypeError" });
  }

  function rotated(value) {
    return typeof value === "string" && value.length > 1 ? value.slice(1) + value.charAt(0) : value;
  }

  function initWrongValues(request, kind, values) {
    return faultInitWrong(request) === kind ? values.map(rotated) : values;
  }

  // ===============================================================================================
  // Strategy B: behaviour discovery (Phase 1.2 N3, MASTER_KEY_PHASE1_2_PLAN.md §2 "Strategy B").
  //
  // P finds the stream-URL builder by its shape and reads n/sig through the URL object. B runs the
  // player's top level in any wrapper shape (also one P does not know, M3), registers every
  // function the player creates, and calls each like a transform. Functions that behave like n or
  // like sig are kept by behaviour, never by name or shape. Screening uses one instance; every
  // shortlisted function is confirmed on its own fresh instances (T2). Candidates with the same
  // outputs form one group; groups are ranked by evidence and capped (T1). B answers alone only
  // with one group per kind that passes SelfCheck; otherwise the kind fails (never a guess).
  //
  // Registration is text splicing at AST positions (no code generator): each function expression
  // or arrow becomes `(R.r(fn,id))`, each function declaration registers its name after itself,
  // and each loop body counts steps, so no probe can hang.
  // ===============================================================================================

  var B_INIT_STEPS = 50000000;
  var B_PROBE_STEPS = 400000;
  var B_MAX_FUNCTIONS = 20000;
  var B_MAX_GROUPS = 3;
  var B_SCREEN_MS = 40000;
  var B_MAX_FN_CHARS = 250000;
  var B_MAX_TEMPLATES = 6000;
  var B_MAX_TEMPLATES_PER_NAME = 96;
  var B_MAX_BINDINGS = 4000;
  var B_MAX_CHAINS = 400;
  var B_SLOW_MS = 25;
  var B_N_PROBES = ["Qx7rTb2LmNc9Pd4s", "Hk3VwZ8yAe1GfJ6uRq0", "aB0cD1eF2gH3iJ4kL5mN"];
  var B_FAILED_N = /^enhanced_except|_w8_/;
  var B_LOOPS = {
    ForStatement: true,
    ForInStatement: true,
    ForOfStatement: true,
    WhileStatement: true,
    DoWhileStatement: true,
  };
  var B_STATEMENT_LISTS = { Program: true, BlockStatement: true, SwitchCase: true, StaticBlock: true };
  var B_STUBS = ["postMessage", "close", "importScripts", "fetch", "WebSocket", "EventSource", "setTimeout", "setInterval", "queueMicrotask"];

  // The statements to guard: P's known wrappers first; otherwise the function with the most
  // statements near the top of the program (any wrapper form); otherwise the flat top level.
  function bBody(ast) {
    try {
      return { statements: unwrap(ast), wrapper: "known" };
    } catch (ignored) {
      // Not a known wrapper.
    }
    var best = null;
    ast.body.forEach(function (statement) {
      var depth = Object.create(null);
      walk(statement, function (node) {
        var level = depth[node.start + ":" + node.end + ":" + node.type] || 0;
        if (level > 8) return false;
        if (
          (node.type === "FunctionExpression" || node.type === "ArrowFunctionExpression") &&
          node.body.type === "BlockStatement" &&
          !node.async &&
          !node.generator &&
          (!best || node.body.body.length > best.body.body.length)
        ) {
          best = node;
        }
        (CHILD_KEYS[node.type] || []).forEach(function (key) {
          var child = node[key];
          (Array.isArray(child) ? child : [child]).forEach(function (item) {
            if (item && typeof item.type === "string") depth[item.start + ":" + item.end + ":" + item.type] = level + 1;
          });
        });
        return !isFunctionNode(node);
      });
    });
    if (best && best.body.body.length >= 10) return { statements: best.body.body, wrapper: "found" };
    var flat = ast.body.filter(function (statement) {
      return statement.type !== "EmptyStatement" && !isDirective(statement);
    });
    if (flat.length >= 10) return { statements: flat, wrapper: "flat" };
    throw new Failure("unexpected structure");
  }

  // The running B report (counts and classes only), readable after a host timeout.
  var bProgressReport = null;

  function bProgress() {
    return bProgressReport;
  }

  function bNow() {
    return typeof Date === "function" && typeof Date.now === "function" ? Date.now() : 0;
  }

  // `a.b`, `a["b"]` and `a['b']` are one name; anything else is no name.
  function bRef(node) {
    var parts = [];
    var cursor = node;
    while (cursor && cursor.type === "MemberExpression") {
      if (!cursor.computed && cursor.property.type === "Identifier") parts.unshift(cursor.property.name);
      else if (cursor.computed && isStringLiteral(cursor.property)) parts.unshift(cursor.property.value);
      else return null;
      cursor = cursor.object;
    }
    if (!cursor || cursor.type !== "Identifier") return null;
    parts.unshift(cursor.name);
    return parts.join(".");
  }

  function bName(node, parent, key) {
    if (node.type === "FunctionDeclaration") return node.id ? node.id.name : null;
    if (!parent) return null;
    if (parent.type === "VariableDeclarator" && key === "init" && parent.id.type === "Identifier") return parent.id.name;
    if (parent.type === "AssignmentExpression" && key === "right") return bRef(parent.left);
    if (parent.type === "Property" && key === "value" && !parent.computed && parent.key.type === "Identifier") return parent.key.name;
    return null;
  }

  // A number from literals and the names in [env] (a dispatcher's bound keys and its constant
  // locals): literals, unary - ~ +, and the integer operators dispatchers use for their keys.
  function bConstant(node, env) {
    if (!node) return null;
    if (node.type === "Literal") return typeof node.value === "number" ? { value: node.value } : null;
    if (node.type === "Identifier") return env && has.call(env, node.name) ? { value: env[node.name] } : null;
    if (node.type === "UnaryExpression") {
      var inner = bConstant(node.argument, env);
      if (!inner) return null;
      if (node.operator === "-") return { value: -inner.value };
      if (node.operator === "~") return { value: ~inner.value };
      if (node.operator === "+") return inner;
      return null;
    }
    if (node.type === "BinaryExpression") {
      var left = bConstant(node.left, env);
      var right = left && bConstant(node.right, env);
      if (!right) return null;
      var l = left.value;
      var r = right.value;
      switch (node.operator) {
        case "^": return { value: l ^ r };
        case "|": return { value: l | r };
        case "&": return { value: l & r };
        case "<<": return { value: l << r };
        case ">>": return { value: l >> r };
        case ">>>": return { value: l >>> r };
        case "+": return { value: l + r };
        case "-": return { value: l - r };
        case "*": return { value: Math.imul(l, r) };
        default: return null;
      }
    }
    return null;
  }

  // Names a function body changes (assignments, ++/--, for-in/of targets, repeated
  // declarations), nested functions skipped: such a name never holds a constant key.
  function bReassigned(fn) {
    var changed = Object.create(null);
    var declared = Object.create(null);
    function target(node) {
      if (!node) return;
      if (node.type === "Identifier") changed[node.name] = true;
      else if (node.type === "VariableDeclaration") node.declarations.forEach(function (d) { target(d.id); });
      else if (node.type === "ArrayPattern") node.elements.forEach(target);
      else if (node.type === "ObjectPattern") node.properties.forEach(function (p) { target(p.type === "RestElement" ? p.argument : p.value); });
      else if (node.type === "AssignmentPattern") target(node.left);
      else if (node.type === "RestElement") target(node.argument);
    }
    walk(fn.body, function (node) {
      if (node !== fn.body && isFunctionNode(node)) return false;
      if (node.type === "AssignmentExpression") target(node.left);
      else if (node.type === "UpdateExpression") target(node.argument);
      else if (node.type === "ForInStatement" || node.type === "ForOfStatement") target(node.left);
      else if (node.type === "VariableDeclarator" && node.id.type === "Identifier") {
        if (declared[node.id.name] || !node.init) changed[node.id.name] = true;
        declared[node.id.name] = true;
      }
      return true;
    });
    return changed;
  }

  // Call shapes: `f(6, 120, x)` gives f the shape [6, 120, <probe>] (dispatcher functions pick
  // their job from constant arguments). Plain aliases (`var a = f`, `a = f`) lead to f's name.
  // Literal shapes are not enough when a dispatcher derives the keys it passes on: `d(F, w, a)`
  // does `var v = w ^ F` and calls `e(v ^ 12, v ^ 34, a)`. So the keys are propagated
  // statically (never run): each call with constant keys binds the callee's parameters, its
  // constant locals are evaluated in order (nested functions skipped; a name the body changes is
  // never constant), and every call inside with constant keys yields a shape (one hole) and/or a
  // further binding (worklist, capped).
  // Chains: when a shape's hole is itself a shape (`h(1, 2, k(3, 4, x))`, directly or through a
  // local assigned once), the whole chain is one shape of the outer function, probed in order.
  function bTemplates(ast) {
    var aliases = Object.create(null);
    var definitionsByName = Object.create(null);
    var functions = [];
    var scans = typeof Map === "function" ? new Map() : null;
    var seeds = { calls: [], locals: null, changed: null };
    function nameOf(node, parent, key) {
      if (node.type === "FunctionDeclaration") return node.id ? node.id.name : null;
      return parent ? bName(node, parent, key) : null;
    }
    // Per function body (nested functions apart): its calls in source order, and the locals
    // assigned exactly once from a call (for chains through a local).
    function local(scope, name, value) {
      if (!scope.locals) return;
      if (has.call(scope.locals, name) || scope.changed[name]) {
        scope.changed[name] = true;
        delete scope.locals[name];
      } else if (value && value.type === "CallExpression") scope.locals[name] = value;
      else scope.changed[name] = true;
    }
    // One walk: names, aliases, and each call and local under its innermost function.
    var stack = [{ node: ast, parent: null, key: null, scope: seeds }];
    while (stack.length) {
      var item = stack.pop();
      var node = item.node;
      var scope = item.scope;
      if (isFunctionNode(node)) {
        functions.push(node);
        scope = { calls: [], locals: Object.create(null), changed: Object.create(null) };
        if (scans) scans.set(node, scope);
        var defined = nameOf(node, item.parent, item.key);
        if (defined && !definitionsByName[defined]) definitionsByName[defined] = node;
      }
      if (node.type === "VariableDeclarator" && node.id.type === "Identifier" && node.init) {
        var target = bRef(node.init);
        if (target && target !== node.id.name) aliases[node.id.name] = target;
        local(scope, node.id.name, node.init);
      } else if (node.type === "AssignmentExpression" && node.operator === "=") {
        var left = bRef(node.left);
        var right = bRef(node.right);
        if (left && right && left !== right) aliases[left] = right;
        if (node.left.type === "Identifier") local(scope, node.left.name, node.right);
      } else if (node.type === "UpdateExpression" && node.argument.type === "Identifier") {
        if (scope.changed) {
          scope.changed[node.argument.name] = true;
          delete scope.locals[node.argument.name];
        }
      } else if (node.type === "CallExpression") {
        scope.calls.push(node);
      }
      var keys = CHILD_KEYS[node.type] || [];
      for (var k = keys.length - 1; k >= 0; k -= 1) {
        var child = node[keys[k]];
        var list = Array.isArray(child) ? child : [child];
        for (var c = list.length - 1; c >= 0; c -= 1) {
          if (list[c] && typeof list[c].type === "string") stack.push({ node: list[c], parent: node, key: keys[k], scope: scope });
        }
      }
    }
    function resolve(name) {
      for (var depth = 0; depth < 4 && aliases[name]; depth += 1) name = aliases[name];
      return name;
    }
    var byName = Object.create(null);
    var seen = Object.create(null);
    var total = 0;
    var propagated = 0;
    var chains = 0;
    function add(name, key, template) {
      if (seen[key] || total >= B_MAX_TEMPLATES) return;
      var list = byName[name] || (byName[name] = []);
      if (list.length >= B_MAX_TEMPLATES_PER_NAME) return;
      seen[key] = true;
      total += 1;
      list.push(template);
    }
    function stepKey(step) {
      return step.name + "(" + step.args.join(",") + ")";
    }
    var bound = Object.create(null);
    var queue = [];
    function bind(name, constants) {
      if (!definitionsByName[name] || queue.length >= B_MAX_BINDINGS) return;
      var key = name + "|" + constants.join(",");
      if (bound[key]) return;
      bound[key] = true;
      queue.push({ name: name, constants: constants });
    }
    // One call under [env] (null: literals only): its step when it has two or three arguments and
    // exactly one is not constant ({ name, args, hole, inner: the non-constant argument }); a
    // binding when two or more are constant.
    function stepOf(call, env, binding) {
      var name = bRef(call.callee);
      if (!name) return null;
      name = resolve(name);
      var constants = [];
      var holes = [];
      var count = 0;
      for (var i = 0; i < call.arguments.length; i += 1) {
        var argument = call.arguments[i];
        if (argument.type === "SpreadElement") return null;
        var constant = bConstant(argument, env);
        constants.push(constant ? constant.value : null);
        if (constant) count += 1;
        else holes.push(i);
      }
      if (binding && count >= 2) bind(name, constants);
      if (call.arguments.length < 2 || call.arguments.length > 3 || holes.length !== 1) return null;
      return { name: name, args: constants, hole: holes[0], inner: call.arguments[holes[0]] };
    }
    // The chain ending in [step]: inner steps first, up to four. Inner calls go into [consumed].
    function chainOf(step, env, locals, consumed) {
      var chain = [step];
      var inner = step.inner;
      while (chain.length < 4 && inner) {
        if (inner.type === "Identifier" && locals && locals[inner.name]) inner = locals[inner.name];
        if (inner.type !== "CallExpression") break;
        var next = stepOf(inner, env, false);
        if (!next) break;
        consumed.push(inner);
        chain.unshift(next);
        inner = next.inner;
      }
      return chain;
    }
    // The calls of one function body: every step is a shape; a chain is a shape only when its
    // outer call is not itself an inner part of a longer chain (that value is intermediate).
    function visitCalls(calls, env, locals) {
      var consumed = [];
      var built = [];
      calls.forEach(function (call) {
        var step = stepOf(call, env, true);
        if (!step) return;
        if (env) propagated += 1;
        add(step.name, stepKey(step), env ? { args: step.args, hole: step.hole, derived: true } : { args: step.args, hole: step.hole });
        var chain = chainOf(step, env, locals, consumed);
        if (chain.length > 1) built.push({ call: call, step: step, chain: chain });
      });
      built.forEach(function (entry) {
        if (consumed.indexOf(entry.call) >= 0 || chains >= B_MAX_CHAINS) return;
        chains += 1;
        add(entry.step.name, entry.chain.map(stepKey).join(">"), {
          args: entry.step.args,
          hole: entry.step.hole,
          chain: entry.chain.map(function (link) {
            return { name: link.name, args: link.args, hole: link.hole };
          }),
        });
      });
    }
    functions.forEach(function (fn) {
      var found = scans ? scans.get(fn) : null;
      if (found) visitCalls(found.calls, null, found.locals);
    });
    // Calls outside every function (the top level, e.g. arguments of the wrapper call).
    visitCalls(seeds.calls, null, null);
    for (var head = 0; head < queue.length; head += 1) {
      var job = queue[head];
      var fn = definitionsByName[job.name];
      var changed = bReassigned(fn);
      var env = Object.create(null);
      for (var p = 0; p < fn.params.length && p < job.constants.length; p += 1) {
        var param = fn.params[p];
        if (param.type === "Identifier" && job.constants[p] !== null && !changed[param.name]) env[param.name] = job.constants[p];
      }
      var known = (scans && scans.get(fn)) || { calls: [], locals: null };
      walk(fn.body, function (inner) {
        if (inner !== fn.body && isFunctionNode(inner)) return false;
        if (inner.type === "VariableDeclarator" && inner.id.type === "Identifier" && inner.init && !changed[inner.id.name] && !has.call(env, inner.id.name)) {
          var local = bConstant(inner.init, env);
          if (local) env[inner.id.name] = local.value;
        }
        return true;
      });
      visitCalls(known.calls, env, known.locals);
    }
    return { byName: byName, total: total, propagated: propagated, bindings: queue.length, chains: chains, aliases: aliases };
  }

  // Inserts are zero-length and applied by position. At one position closes come before opens,
  // deeper closes first and shallower opens first, so nested hooks always nest correctly.
  function bInstrument(source, ast) {
    var inserts = [];
    var meta = [];
    function visit(node, parent, key, depth) {
      if ((node.type === "FunctionExpression" || node.type === "ArrowFunctionExpression") && !node.async && !node.generator) {
        var value =
          parent &&
          parent.type !== "MethodDefinition" &&
          parent.type !== "PropertyDefinition" &&
          !(parent.type === "Property" && key === "value" && (parent.method || parent.kind !== "init"));
        if (value) {
          meta.push({ name: bName(node, parent, key), pos: node.start, size: node.end - node.start, params: node.params.length });
          inserts.push({ pos: node.start, order: 100000 + depth, text: "(" + REGISTRY + ".r(" });
          inserts.push({ pos: node.end, order: -depth, text: "," + (meta.length - 1) + "))" });
        }
      } else if (node.type === "FunctionDeclaration" && node.id && !node.async && !node.generator && parent && B_STATEMENT_LISTS[parent.type]) {
        meta.push({ name: node.id.name, pos: node.start, size: node.end - node.start, params: node.params.length });
        inserts.push({ pos: node.end, order: -depth, text: "\n;" + REGISTRY + ".r(" + node.id.name + "," + (meta.length - 1) + ");\n" });
      } else if (B_LOOPS[node.type] && node.body) {
        inserts.push({ pos: node.body.start, order: 100000 + depth + 1, text: "{" + REGISTRY + ".s();" });
        inserts.push({ pos: node.body.end, order: -(depth + 1), text: "}" });
      }
    }
    var stack = [{ node: ast, parent: null, key: null, depth: 0 }];
    while (stack.length) {
      var item = stack.pop();
      var node = item.node;
      visit(node, item.parent, item.key, item.depth);
      var keys = CHILD_KEYS[node.type] || [];
      for (var k = keys.length - 1; k >= 0; k -= 1) {
        var child = node[keys[k]];
        var list = Array.isArray(child) ? child : [child];
        for (var c = list.length - 1; c >= 0; c -= 1) {
          if (list[c] && typeof list[c].type === "string") {
            stack.push({ node: list[c], parent: node, key: keys[k], depth: item.depth + 1 });
          }
        }
      }
    }
    inserts.sort(function (a, b) {
      return a.pos - b.pos || a.order - b.order;
    });
    var parts = [];
    var at = 0;
    inserts.forEach(function (insert) {
      parts.push(source.slice(at, insert.pos), insert.text);
      at = insert.pos;
    });
    parts.push(source.slice(at));
    return { text: parts.join(""), meta: meta };
  }

  // Names the stream-URL builders mention (P's two patterns, on B's statements): evidence for T1.
  function bBuilderNames(statements, source) {
    var names = Object.create(null);
    var called = Object.create(null);
    try {
      var tables = stringTables(statements);
      var found = definitions(statements, source);
      var byReference = Object.create(null);
      found.forEach(function (definition) {
        byReference[definition.ref] = definition.fn;
      });
      var refs = builderDefinitions(found, tables).concat(builderCallSites(statements, byReference, tables, source));
      refs.forEach(function (ref) {
        if (!byReference[ref]) return;
        walk(byReference[ref], function (node) {
          if (node.type === "Identifier") names[node.name] = true;
          else if (node.type === "MemberExpression") {
            var text = bRef(node);
            if (text) names[text] = true;
          }
          if (node.type === "CallExpression" && node.callee.type === "Identifier") called[node.callee.name] = true;
          return true;
        });
      });
    } catch (ignored) {
      // No evidence; ranking falls back to behaviour.
    }
    return { names: names, called: called };
  }

  // The builder may call a function through a plain alias (`var a = f; ... a(x)`, mutation M5),
  // so its evidence names `a`: a name the builder calls also marks what it stands for (up to 4
  // links). Only called names: parameters and locals keep their own meaning.
  function bAliasBuilders(builders, called, aliases) {
    if (!aliases) return;
    Object.keys(called).forEach(function (name) {
      for (var depth = 0; depth < 4 && has.call(aliases, name); depth += 1) {
        name = aliases[name];
        builders[name] = true;
      }
    });
  }

  function bPrepare(playerText) {
    try {
      var ms = {};
      var t0 = bNow();
      var ast = parseProgram(playerText);
      var t1 = bNow();
      ms.parse = t1 - t0;
      var instrumented = bInstrument(playerText, ast);
      var body = bBody(ast);
      var evidence = bBuilderNames(body.statements, playerText);
      var builders = evidence.names;
      var tt = bNow();
      ms.instrument = tt - t1;
      var templates = bTemplates(ast);
      bAliasBuilders(builders, evidence.called, templates.aliases);
      var t2 = bNow();
      ms.templates = t2 - tt;
      var ast2;
      try {
        ast2 = meriyah.parse(instrumented.text, { ranges: true, webcompat: true });
      } catch (error) {
        throw new Failure("instrument");
      }
      var t3 = bNow();
      ms.reparse = t3 - t2;
      var body2 = bBody(ast2);
      var program = buildProgram(instrumented.text, body2.statements, []);
      ms.build = bNow() - t3;
      var named = Object.create(null);
      instrumented.meta.forEach(function (entry) {
        if (entry.name) named[entry.name] = (named[entry.name] || 0) + 1;
      });
      var first = body.statements.length ? body.statements[0].start : 0;
      var last = body.statements.length ? body.statements[body.statements.length - 1].end : 0;
      instrumented.meta.forEach(function (entry) {
        entry.builder = !!(entry.name && builders[entry.name]);
        entry.unique = !!entry.name && named[entry.name] === 1;
        // The wrapper that holds the whole program (re-running it is not a probe) and huge functions.
        entry.skip = (entry.pos <= first && entry.pos + entry.size >= last) || entry.size > B_MAX_FN_CHARS;
      });
      return {
        ok: true,
        strategy: "B",
        version: VERSION,
        program: program.text,
        meta: instrumented.meta,
        templates: templates.byName,
        info: {
          wrapper: body.wrapper,
          functions: instrumented.meta.length,
          templates: templates.total,
          propagated: templates.propagated,
          bindings: templates.bindings,
          chains: templates.chains,
          kept: program.kept,
          dropped: program.dropped,
          ms: ms,
        },
      };
    } catch (error) {
      return { ok: false, failed: errorName(error) };
    }
  }

  function bFreezeBuiltins(scope) {
    ["String", "Array", "Function", "Number", "Boolean", "RegExp", "Date"].forEach(function (name) {
      var ctor = scope[name];
      if (ctor && ctor.prototype) Object.freeze(ctor.prototype);
    });
  }

  function bInstantiate(prepared, request) {
    var scope = globalScope();
    var faults = request.faults || {};
    var page = request.page === true;
    installEnvironment(scope, request.env, faults.omit, page);
    B_STUBS.forEach(function (name) {
      defineGlobal(scope, name, { value: function () {}, writable: true, configurable: true, enumerable: false }, page);
    });
    var registry = { c: [], e: 0, f: [], seen: Object.create(null), steps: 0, max: B_INIT_STEPS, closed: false, meta: prepared.meta || [], named: null };
    registry.k = initErrorRecorder(registry);
    registry.r = function (fn, id) {
      if (!registry.closed && typeof fn === "function" && !registry.seen[id] && registry.f.length < B_MAX_FUNCTIONS) {
        registry.seen[id] = true;
        registry.f.push({ id: id, fn: fn, order: registry.f.length });
      }
      return fn;
    };
    registry.s = function () {
      registry.steps += 1;
      if (registry.steps > registry.max) throw new RangeError("steps");
    };
    var factory;
    try {
      factory = Function(REGISTRY, prepared.program);
    } catch (error) {
      throw new Failure("compile:" + errorName(error));
    }
    try {
      factory(registry);
    } catch (error) {
      // A late throw keeps everything registered before it.
      registry.e += 1;
      registry.k(error);
    }
    faultInitError(registry, request);
    registry.closed = true;
    registry.max = B_PROBE_STEPS;
    return registry;
  }

  // A shape for debug lines: dispatcher keys only ("_" is the probe), never an input or output.
  function bShapeLabel(shape) {
    function args(list) {
      return list.map(function (a) { return a === null ? "_" : a; }).join(",");
    }
    if (!shape) return "-";
    if (shape.chain) return "c:" + shape.chain.map(function (link) { return args(link.args); }).join(">");
    return (shape.derived ? "d:" : "") + args(shape.args);
  }

  // A registered function by its (unique) name, for the inner links of a chain.
  function bByName(registry, name) {
    if (!registry.named) {
      registry.named = Object.create(null);
      registry.f.forEach(function (entry) {
        var info = registry.meta[entry.id];
        if (info && info.unique && info.name) registry.named[info.name] = entry.fn;
      });
    }
    return has.call(registry.named, name) ? registry.named[name] : null;
  }

  // [shape]: null for f(input), else constant arguments with the input at [shape.hole]; a chain
  // runs its links in order (each link's result is the next one's input, [fn] is the last link).
  function bCall(registry, fn, input, shape) {
    registry.steps = 0;
    registry.stepsUsed = 0;
    try {
      var value;
      if (!shape) value = fn(input);
      else if (shape.chain) {
        value = input;
        for (var i = 0; i < shape.chain.length; i += 1) {
          var link = shape.chain[i];
          var target = i === shape.chain.length - 1 ? fn : bByName(registry, link.name);
          if (typeof target !== "function") return null;
          var linkArgs = link.args.slice();
          linkArgs[link.hole] = value;
          value = target.apply(undefined, linkArgs);
        }
      } else {
        var args = shape.args.slice();
        args[shape.hole] = input;
        value = fn.apply(undefined, args);
      }
      registry.stepsUsed = registry.steps;
      return typeof value === "string" ? value : null;
    } catch (error) {
      return null;
    }
  }

  function bIsSigMap(probe, output) {
    if (typeof output !== "string" || output.length > probe.length || output.length < probe.length - Math.max(16, probe.length >> 2)) return false;
    var seen = Object.create(null);
    for (var i = 0; i < output.length; i += 1) {
      if (probe.indexOf(output[i]) < 0 || seen[output[i]]) return false;
      seen[output[i]] = true;
    }
    return true;
  }

  // One primitive alone is never sig (T1): identity, reverse, a rotation, a cut, one swap.
  function bTrivialMap(probe, output) {
    var n = probe.length;
    var map = [];
    for (var i = 0; i < output.length; i += 1) map.push(probe.indexOf(output[i]));
    function every(test) {
      for (var j = 0; j < map.length; j += 1) if (!test(map[j], j)) return false;
      return true;
    }
    for (var k = 0; k <= n - map.length; k += 1) {
      if (every(function (from, j) { return from === j + k; })) return map.length === n ? "identity" : "cut";
      if (every(function (from, j) { return from === n - 1 - j - k; })) return "reverse";
    }
    if (map.length === n) {
      for (var r = 1; r < n; r += 1) {
        if (every(function (from, j) { return from === (j + r) % n; })) return "rotation";
      }
      var moved = 0;
      for (var m = 0; m < n; m += 1) if (map[m] !== m) moved += 1;
      if (moved <= 2) return "swap";
    }
    return false;
  }

  function bTrivialN(input, output) {
    if (output.length < 4 || output.indexOf(input) >= 0) return true;
    var reversed = input.split("").reverse().join("");
    if (output.indexOf(reversed) >= 0) return true;
    for (var r = 1; r < input.length; r += 1) {
      if (output === input.slice(r) + input.slice(0, r)) return true;
    }
    if (output === input.toUpperCase() || output === input.toLowerCase()) return true;
    try {
      if (typeof btoa === "function") {
        var encoded = btoa(input).replace(/=+$/, "");
        if (output.indexOf(encoded) >= 0 || output.indexOf(encoded.replace(/\+/g, "-").replace(/\//g, "_")) >= 0) return true;
      }
    } catch (ignored) {
      // Not encodable.
    }
    return false;
  }

  function bChange(input, at) {
    var replacement = input[at] === "x" ? "y" : "x";
    return input.slice(0, at) + replacement + input.slice(at + 1);
  }

  // Screening on one instance: a profile of one call shape's outputs ({ kind, outputs }), or the
  // class of the first rule it breaks ({ reject }).
  // [weakOk]: the function is named by the stream-URL builder code (T1 evidence #2).
  function bProfile(registry, fn, shape, weakOk) {
    var map = bCall(registry, fn, SIG_MAP_PROBE, shape);
    if (map !== null && bIsSigMap(SIG_MAP_PROBE, map)) {
      var trivial = bTrivialMap(SIG_MAP_PROBE, map);
      // A real sig can net to one swap (measured: 74edf1a3, its steps leave two positions
      // changed), so a swap is kept as a weak candidate when the builder code names the
      // function; weak candidates count only when no other sig candidate exists.
      if (trivial && (trivial !== "swap" || !weakOk)) return { reject: "sig:trivial-" + trivial };
      var edge = bCall(registry, fn, SIG_EDGE_PROBE, shape);
      if (edge === null || edge !== applyIndexMap(SIG_MAP_PROBE, map, SIG_EDGE_PROBE)) return { reject: "sig:edge" };
      if (bCall(registry, fn, SIG_MAP_PROBE, shape) !== map) return { reject: "sig:unstable" };
      return { kind: "sig", outputs: [map, edge], steps: registry.stepsUsed, weak: !!trivial };
    }
    var outputs = [];
    for (var i = 0; i < B_N_PROBES.length; i += 1) {
      var input = B_N_PROBES[i];
      var value = bCall(registry, fn, input, shape);
      if (value === null) return { reject: "n:none" + i };
      if (!URL_SAFE.test(value)) return { reject: "n:unsafe" + i };
      if (value === input) return { reject: "n:same" + i };
      if (B_FAILED_N.test(value)) return { reject: "n:failed" + i };
      if (bTrivialN(input, value)) return { reject: "n:trivial" + i };
      if (value.length > input.length * 2 + 8 || value.length * 2 < input.length) return { reject: "n:length" + i };
      if (outputs.indexOf(value) >= 0) return { reject: "n:repeat" + i };
      outputs.push(value);
    }
    // The output length follows the input length (n works character by character): a hash has
    // one length for every input and an encoding grows by a factor.
    var shift = outputs[0].length - B_N_PROBES[0].length;
    for (var j = 1; j < outputs.length; j += 1) {
      if (outputs[j].length - B_N_PROBES[j].length !== shift) return { reject: "n:length-follow" };
    }
    var first = B_N_PROBES[0];
    // Not a streaming encoding: the output for the input's first characters is not the start of
    // the whole output.
    var prefix = bCall(registry, fn, first.slice(0, 12), shape);
    if (prefix === null) return { reject: "n:prefix" };
    if (prefix.length > 0 && outputs[0].indexOf(prefix) === 0) return { reject: "n:encoding" };
    // It depends on most of the input: changing one character changes the output at least at
    // half of the positions (measured: 11-16 of 16 for the real transforms).
    var moved = 0;
    for (var at = 0; at < first.length; at += 1) {
      var changed = bCall(registry, fn, bChange(first, at), shape);
      if (changed === null) return { reject: "n:partial" };
      if (changed !== outputs[0]) moved += 1;
    }
    if (moved * 2 < first.length) return { reject: "n:partial" };
    if (bCall(registry, fn, first, shape) !== outputs[0]) return { reject: "n:unstable" };
    return { kind: "n", outputs: outputs, steps: registry.stepsUsed };
  }

  function bProbesFor(kind) {
    return kind === "sig" ? [SIG_MAP_PROBE, SIG_EDGE_PROBE] : B_N_PROBES;
  }

  function bOutputs(registry, fn, shape, kind) {
    var probes = bProbesFor(kind);
    var out = [];
    for (var i = 0; i < probes.length; i += 1) {
      var value = bCall(registry, fn, probes[i], shape);
      if (value === null) return null;
      out.push(value);
    }
    return out;
  }

  function bSame(a, b) {
    if (!a || !b || a.length !== b.length) return false;
    for (var i = 0; i < a.length; i += 1) if (a[i] !== b[i]) return false;
    return true;
  }

  function bFind(registry, id) {
    for (var i = 0; i < registry.f.length; i += 1) if (registry.f[i].id === id) return registry.f[i].fn;
    return null;
  }

  // T2: each shortlisted function on its own fresh instance, called twice, and once more on a
  // second fresh instance; only outputs equal to the screening outputs count.
  function bConfirm(prepared, request, candidate) {
    for (var round = 0; round < 2; round += 1) {
      var fresh = bInstantiate(prepared, request);
      var fn = bFind(fresh, candidate.id);
      if (!fn) return false;
      var calls = round === 0 ? 2 : 1;
      for (var c = 0; c < calls; c += 1) {
        if (!bSame(bOutputs(fresh, fn, candidate.shape, candidate.kind), candidate.outputs)) return false;
      }
    }
    return true;
  }

  function bScore(entry) {
    return (entry.builder ? 1000 : 0) + (entry.params === 1 ? 10 : 0);
  }

  // A composed transform runs a step-counted loop (over the character array) at least once; a
  // single primitive (one reverse, one swap) does not. Measured during screening.
  function bComposed(candidate) {
    return candidate && candidate.steps > 0 ? 100 : 0;
  }

  // T1: candidates with the same outputs are one group; groups are ranked by evidence; at most
  // B_MAX_GROUPS go on, and more equally ranked groups than that is ambiguous.
  function bGroups(candidates, meta, hintOutputs, all) {
    var groups = [];
    candidates.forEach(function (candidate) {
      var group = null;
      for (var i = 0; i < groups.length && !group; i += 1) if (bSame(groups[i].outputs, candidate.outputs)) group = groups[i];
      if (!group) {
        group = { outputs: candidate.outputs, members: [], score: 0, first: candidate.order };
        groups.push(group);
      }
      group.members.push(candidate);
      group.score = Math.max(group.score, bScore(meta[candidate.id] || {}) + bComposed(candidate));
      group.first = Math.min(group.first, candidate.order);
    });
    groups.forEach(function (group) {
      group.hint = !!(hintOutputs && bSame(group.outputs, hintOutputs));
      if (group.hint) group.score += 1000000;
    });
    groups.sort(function (a, b) {
      return b.score - a.score || a.first - b.first;
    });
    var ambiguous = groups.length > B_MAX_GROUPS && groups[B_MAX_GROUPS].score === groups[0].score;
    return { groups: all ? groups : groups.slice(0, B_MAX_GROUPS), total: groups.length, ambiguous: ambiguous };
  }

  // SelfCheck on one group: every member agrees on every input, values have their kind's shape,
  // n outputs are distinct, sig keeps the probes' index map, vectors match, the first is stable.
  function bSolveKind(kind, group, registry, inputs, request) {
    var vectors = (request.vectors && request.vectors[kind]) || [];
    // Fault S4 "raw-encoding": sig gets its input as it sits in a URL (not decoded).
    var raw = kind === "sig" && request.faults && request.faults.rawEncoding;
    var probes = bProbesFor(kind);
    var all = [];
    probes.concat(vectors.map(function (vector) { return vector.input; })).concat(inputs).forEach(function (input) {
      if (typeof input === "string" && input.length > 0 && all.indexOf(input) < 0) all.push(input);
    });
    var fns = group.members.map(function (member) {
      return { fn: bFind(registry, member.id), shape: member.shape };
    });
    // Fault "initWrong" (tests only): every member's value of this kind rotated alike.
    var wrong = faultInitWrong(request) === kind;
    function call(fn, input, shape) {
      var out = bCall(registry, fn, input, shape);
      return wrong ? rotated(out) : out;
    }
    var reason = null;
    var solved = Object.create(null);
    var outputs = [];
    all.forEach(function (input) {
      if (reason) return;
      var value = null;
      fns.forEach(function (member) {
        var out = member.fn ? call(member.fn, raw ? encodeURIComponent(input) : input, member.shape) : null;
        if (out === null) reason = reason || "missing";
        else if (value === null) value = out;
        else if (value !== out) reason = reason || "disagree";
      });
      if (reason) return;
      if (!shapeOk(kind, input, value)) reason = "shape";
      else if (kind === "n" && outputs.indexOf(value) >= 0) reason = "not-distinct";
      else {
        solved[input] = value;
        outputs.push(value);
      }
    });
    if (!reason && kind === "sig") {
      var expected = applyIndexMap(SIG_MAP_PROBE, solved[SIG_MAP_PROBE], SIG_EDGE_PROBE);
      if (expected === null || expected !== solved[SIG_EDGE_PROBE]) reason = "encoding";
    }
    if (!reason) {
      vectors.forEach(function (vector) {
        if (!reason && solved[vector.input] !== vector.expected) reason = "vector";
      });
    }
    if (!reason && fns[0].fn && call(fns[0].fn, probes[0], fns[0].shape) !== solved[probes[0]]) reason = "unstable";
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
    return { answers: answers, reason: reason, solved: answered };
  }

  // B: each solved kind's group again on a second instance with other environment values.
  function bTripwire(prepared, request, output, failed, groups) {
    var kinds = KINDS.filter(function (kind) {
      return groups[kind] && tripwireSample(output[kind]).length > 0;
    });
    if (!kinds.length) return;
    var tripped = [];
    try {
      var other = bInstantiate(prepared, tripwireRequest(request));
      kinds.forEach(function (kind) {
        var sample = tripwireSample(output[kind]);
        var result = bSolveKind(kind, groups[kind], other, sample, request);
        var same = !result.reason && sample.every(function (input) {
          return result.answers[input] === output[kind][input];
        });
        if (!same) tripped.push(kind);
      });
    } catch (error) {
      tripped = kinds.slice();
    } finally {
      restoreEnvironment(request);
    }
    tripwireResult(output, failed, kinds, tripped);
  }

  // Rank signal #1: P's own answer on B's probes (P ran, but its SelfCheck refused one condition).
  // P is the proven path; when it answers, B prefers the group that agrees with it. When P fails
  // (the shapes P does not know, e.g. a re-wrapped top level), there is no hint and B ranks by
  // data flow, composition and creation order instead. A hint never forces an answer: the group
  // still has to pass confirmation and SelfCheck.
  function bPHint(playerText) {
    try {
      var preparedP = prepare(playerText);
      if (!preparedP.ok) return null;
      var out = run(preparedP, { n: B_N_PROBES.slice(), sig: [SIG_MAP_PROBE, SIG_EDGE_PROBE] });
      return { n: (out && out.n) || {}, sig: (out && out.sig) || {} };
    } catch (ignored) {
      return null;
    }
  }

  function bHintOutputs(hint, kind) {
    if (!hint || !hint[kind]) return null;
    var probes = bProbesFor(kind);
    var out = [];
    for (var i = 0; i < probes.length; i += 1) {
      var value = hint[kind][probes[i]];
      if (typeof value !== "string") return null;
      out.push(value);
    }
    return out;
  }

  function bRun(prepared, request) {
    request = request || {};
    var output = { n: {}, sig: {}, report: { version: VERSION, strategy: "B", stage: "instantiate" } };
    if (prepared && prepared.info) output.report.prepare = prepared.info;
    bProgressReport = output.report;
    try {
      if (!prepared || !prepared.ok || typeof prepared.program !== "string") throw new Failure("not prepared");
      var ms = {};
      output.report.ms = ms;
      var t0 = bNow();
      var registry = bInstantiate(prepared, request);
      bFreezeBuiltins(globalScope());
      var t1 = bNow();
      ms.instantiate = t1 - t0;
      var meta = prepared.meta || [];
      var screened = 0;
      var skipped = 0;
      var slow = [];
      var slowCount = 0;
      var found = { n: [], sig: [] };
      output.report.topLevelErrors = registry.e;
      output.report.topLevelErrorClasses = initErrorClasses(registry);
      output.report.registered = registry.f.length;
      output.report.slowest = slow;
      output.report.stage = "screen";
      var templates = prepared.templates || {};
      var shapesTried = 0;
      for (var i = 0; i < registry.f.length; i += 1) {
        var entry = registry.f[i];
        var params = entry.fn.length;
        var info = meta[entry.id] || {};
        var shapes = params >= 1 && params <= 3 ? [null] : [];
        if (info.unique && templates[info.name]) shapes = shapes.concat(templates[info.name]);
        if (!shapes.length) continue;
        if (info.skip) {
          skipped += 1;
          continue;
        }
        if (bNow() - t1 > B_SCREEN_MS) {
          output.report.screened = screened;
          output.report.slow = slowCount;
          output.report.slowest = slow;
          ms.screen = bNow() - t1;
          throw new Failure("budget");
        }
        screened += 1;
        var s0 = bNow();
        output.report.screened = screened;
        output.report.current = [entry.id, params, s0 - t1];
        var derivedShapes = false;
        for (var ds = 0; ds < shapes.length; ds += 1) if (shapes[ds] && shapes[ds].derived) derivedShapes = true;
        var why = request.debug && info.unique && (info.builder || derivedShapes) ? [] : null;
        for (var sh = 0; sh < shapes.length; sh += 1) {
          shapesTried += 1;
          var profile = bProfile(registry, entry.fn, shapes[sh], !!info.builder);
          if (profile.kind) {
            found[profile.kind].push({
              id: entry.id,
              order: entry.order,
              kind: profile.kind,
              outputs: profile.outputs,
              shape: shapes[sh],
              steps: profile.steps,
              weak: !!profile.weak,
            });
          }
          if (why && why.length < 8) why.push(bShapeLabel(shapes[sh]) + "=" + (profile.kind || profile.reject));
        }
        if (why) {
          output.debug = output.debug || {};
          (output.debug.builders = output.debug.builders || []).push([entry.id, info.name, info.size, params, shapes.length, why.join(" ")]);
        }
        var took = bNow() - s0;
        if (took >= B_SLOW_MS) {
          slowCount += 1;
          output.report.slow = slowCount;
          slow.push([entry.id, took, params, registry.steps]);
          slow.sort(function (a, b) {
            return b[1] - a[1];
          });
          if (slow.length > 6) slow.length = 6;
        }
      }
      output.report.shapes = shapesTried;
      var t2 = bNow();
      ms.screen = t2 - t1;
      output.report.screened = screened;
      output.report.skipped = skipped;
      output.report.slow = slowCount;
      output.report.slowest = slow;
      var failed = [];
      var solvedGroups = Object.create(null);
      KINDS.forEach(function (kind) {
        var inputs = (request[kind] || []).filter(function (input) {
          return typeof input === "string" && input.length > 0;
        });
        if (!inputs.length) return;
        output.report.stage = "confirm_" + kind;
        var c0 = bNow();
        var confirmed = found[kind].filter(function (candidate) {
          return bConfirm(prepared, request, candidate);
        });
        ms["confirm_" + kind] = bNow() - c0;
        // Fault S4 "decoy": one more confirmed candidate with other outputs (it must make the
        // kind fail, never win).
        if (request.faults && request.faults.decoy === kind && confirmed.length) {
          confirmed.push({
            id: -1,
            order: registry.f.length,
            kind: kind,
            outputs: confirmed[0].outputs.map(function (value) {
              return value.split("").reverse().join("");
            }),
            shape: null,
            steps: 0,
            weak: confirmed[0].weak,
          });
        }
        var strong = confirmed.filter(function (candidate) {
          return !candidate.weak;
        });
        var weakOnly = !strong.length && confirmed.length > 0;
        if (!weakOnly) confirmed = strong;
        var hintOutputs = bHintOutputs(request.pHint, kind);
        var grouped = bGroups(confirmed, meta, hintOutputs);
        var kindReport = { shortlisted: found[kind].length, confirmed: confirmed.length, groups: grouped.total, hint: !!hintOutputs };
        if (weakOnly) kindReport.weak = true;
        output.report[kind] = kindReport;
        if (request.debug) {
          // Local diagnosis only (the harness's --verbose): each group's answers and members.
          output.debug = output.debug || {};
          output.debug[kind] = bGroups(confirmed, meta, hintOutputs, true).groups.map(function (group) {
            var answer = bSolveKind(kind, group, bInstantiate(prepared, request), inputs, request);
            return {
              score: group.score,
              hint: group.hint,
              first: group.first,
              reason: answer.reason,
              answers: answer.answers,
              members: group.members.slice(0, 4).map(function (member) {
                var info = meta[member.id] || {};
                return [member.id, info.name || "?", info.size || 0, bShapeLabel(member.shape)];
              }),
              size: group.members.length,
            };
          });
        }
        var reason = null;
        if (!confirmed.length) reason = "no candidates";
        else if (grouped.ambiguous) reason = "ambiguous";
        else if (grouped.total > 1) reason = "disagree";
        if (!reason) {
          output.report.stage = "solve_" + kind;
          var v0 = bNow();
          var result = bSolveKind(kind, grouped.groups[0], bInstantiate(prepared, request), inputs, request);
          ms["solve_" + kind] = bNow() - v0;
          reason = result.reason;
          if (!reason) {
            output[kind] = result.answers;
            solvedGroups[kind] = grouped.groups[0];
          }
          kindReport.solved = result.solved;
        }
        kindReport.status = reason ? "failed" : "ok";
        kindReport.reason = reason;
        if (reason) failed.push(kind + ":" + reason);
      });
      if (tripwireWanted(request)) {
        output.report.stage = "tripwire";
        bTripwire(prepared, request, output, failed, solvedGroups);
      }
      if (failed.length) output.failed = failed.join(",");
      output.report.stage = "done";
      delete output.report.current;
    } catch (error) {
      output.failed = errorName(error);
      output.n = {};
      output.sig = {};
    }
    return output;
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
      output.report.topLevelErrorClasses = initErrorClasses(registry);
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
      if (tripwireWanted(request)) pTripwire(prepared, request, output, failed);
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
    if (request.strategy === "B") {
      bProgressReport = { version: VERSION, strategy: "B", stage: "prepare" };
      var preparedB = bPrepare(request.player);
      if (!preparedB.ok) return { n: {}, sig: {}, failed: preparedB.failed, report: { version: VERSION, strategy: "B" } };
      // B alone (request.hint === false) never runs P; the ladder may pass the hint on.
      request.pHint = request.hint === false ? null : bPHint(request.player);
      return bRun(preparedB, request);
    }
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
    prepareB: bPrepare,
    runB: bRun,
    progress: bProgress,
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
      bBody: bBody,
      bInstrument: bInstrument,
      bTrivialMap: bTrivialMap,
      bTrivialN: bTrivialN,
      bIsSigMap: bIsSigMap,
      bGroups: bGroups,
    },
  };
})();

function yftOwnSolve(request) {
  return yftOwnSolver.solve(request);
}
