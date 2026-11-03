#!/usr/bin/env python3
"""Fail unless code files changed only in their comments.

python3 scripts/check-comment-only.py BASE [HEAD] [--allow PATH]... [-- PATHSPEC...]
No HEAD: compare BASE with the working tree. Kotlin/Java: same tokens outside comments (strings,
chars and `names` exact; whitespace and blank lines may differ). Python: same syntax tree, docstrings
ignored. Shell, YAML, .pro, .properties, .toml: only whole-line # comments may change. .md skipped.
"""
import ast, re, subprocess, sys

def git(*a):
    r = subprocess.run(["git", *a], capture_output=True, text=True)
    if r.returncode: sys.exit(f"git {' '.join(a)} failed: {r.stderr.strip()}")
    return r.stdout

def skip_block(s, i, nested):
    depth, j = 1, i + 2
    while j < len(s):
        if nested and s.startswith("/*", j): depth, j = depth + 1, j + 2
        elif s.startswith("*/", j):
            depth, j = depth - 1, j + 2
            if depth == 0: return j, True
        else: j += 1
    return len(s), False

def scan_char(s, i):
    j = i + 1
    while j < len(s):
        if s[j] == "\\": j += 2
        elif s[j] == "'": return j + 1, True
        elif s[j] == "\n": return j, False
        else: j += 1
    return len(s), False

def scan_template(s, j, kt):
    depth = 1
    while j < len(s):
        c = s[j]
        if c == '"': j = scan_string(s, j, kt)[0]
        elif c == "'": j = scan_char(s, j)[0]
        elif s.startswith("//", j): j = s.find("\n", j) % (len(s) + 1)
        elif s.startswith("/*", j): j = skip_block(s, j, kt)[0]
        else:
            depth += (c == "{") - (c == "}")
            j += 1
            if depth == 0: return j
    return len(s)

def scan_string(s, i, kt):
    raw, j = s.startswith('"""', i), i + (3 if s.startswith('"""', i) else 1)
    while j < len(s):
        if kt and s.startswith("${", j): j = scan_template(s, j + 2, kt)
        elif raw and s.startswith('"""', j):
            j += 3
            while j < len(s) and s[j] == '"': j += 1
            return j, True
        elif raw: j += 1
        elif s[j] == "\\": j += 2
        elif s[j] == '"': return j + 1, True
        elif s[j] == "\n": return j, False
        else: j += 1
    return len(s), False

def c_tokens(src, kt):
    """(line, token) outside comments; "\\n" ends a code line; blank lines collapse."""
    s = src.replace("\r\n", "\n").lstrip("\ufeff")
    out, warn, code, i, line, start = [], [], [], 0, 1, 1
    def flush():
        ln = start
        for k, part in enumerate("".join(code).split("\n")):
            if k: out.append((ln, "\n")); ln += 1
            out.extend((ln, w) for w in part.split())
        code.clear()
    while i < len(s):
        if not code: start = line
        c = s[i]
        if s.startswith("//", i):
            i = s.find("\n", i) % (len(s) + 1); code.append(" ")
        elif s.startswith("/*", i):
            j, ok = skip_block(s, i, kt)
            if not ok: warn.append(f"line {line}: comment never closed")
            line += s.count("\n", i, j); code.append(" \n" if "\n" in s[i:j] else " "); i = j
        elif c == '"' or c == "'" or (c == "`" and kt):
            flush()
            if c == '"': j, ok = scan_string(s, i, kt)
            elif c == "'": j, ok = scan_char(s, i)
            else:
                k = s.find("`", i + 1); j, ok = (len(s), False) if k < 0 else (k + 1, True)
            if not ok: warn.append(f"line {line}: literal never closed")
            out.append((line, s[i:j])); line += s.count("\n", i, j); i = j
        else:
            code.append(c); line += c == "\n"; i += 1
    flush()
    toks = []
    for t in out:
        if t[1] != "\n" or (toks and toks[-1][1] != "\n"): toks.append(t)
    while toks and toks[-1][1] == "\n": toks.pop()
    return toks, warn

def py_tree(src):
    tree = ast.parse(src)
    for n in ast.walk(tree):
        if isinstance(n, (ast.Module, ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef)):
            b = n.body
            if b and isinstance(b[0], ast.Expr) and isinstance(b[0].value, ast.Constant) and isinstance(b[0].value.value, str):
                n.body = b[1:] or [ast.Pass()]
    return ast.dump(tree)

def hash_lines(src, shell):
    out, heredoc = [], None
    for no, raw in enumerate(src.replace("\r\n", "\n").split("\n"), 1):
        st = raw.strip()
        if heredoc is not None:
            out.append((no, raw)); heredoc = None if st == heredoc else heredoc
            continue
        if not st or (st.startswith("#") and not (shell and no == 1 and st.startswith("#!"))): continue
        out.append((no, raw.rstrip()))
        m = re.search(r"<<-?\s*['\"]?([A-Za-z_]\w*)", raw) if shell and "<<<" not in raw else None
        if m: heredoc = m.group(1)
    return out

def first_diff(a, b):
    k = next((x for x in range(min(len(a), len(b))) if a[x][1] != b[x][1]), min(len(a), len(b)))
    show = lambda t: (" ".join(x[1].replace("\n", "\\n") for x in t[k:k + 6]) or "(end)")[:100]
    at = lambda t: t[k][0] if k < len(t) else "end"
    return f"base line {at(a)}: {show(a)} | new line {at(b)}: {show(b)}"

def compare(path, before, after):
    ext = path.rsplit(".", 1)[-1].lower()
    if ext in ("kt", "kts", "java"):
        (a, _), (b, wb) = c_tokens(before, ext != "java"), c_tokens(after, ext != "java")
        if wb: return "FAIL " + "; ".join(wb[:2])
        return "OK" if [t for _, t in a] == [t for _, t in b] else "FAIL code changed: " + first_diff(a, b)
    if ext == "py":
        try: return "OK" if py_tree(before) == py_tree(after) else "FAIL code changed (syntax tree differs)"
        except SyntaxError as e: return f"FAIL syntax error: {e}"
    if ext in ("sh", "bash", "yml", "yaml", "pro", "properties", "toml"):
        a, b = hash_lines(before, ext in ("sh", "bash")), hash_lines(after, ext in ("sh", "bash"))
        return "OK" if [t for _, t in a] == [t for _, t in b] else "FAIL only whole-line comments may change: " + first_diff(a, b)
    return "FAIL this file type must stay unchanged"

def main(argv):
    specs = argv[argv.index("--") + 1:] if "--" in argv else []
    argv = argv[:argv.index("--")] if "--" in argv else argv
    allow = {argv[k + 1] for k, x in enumerate(argv) if x == "--allow"}
    args = [x for k, x in enumerate(argv) if x != "--allow" and (k == 0 or argv[k - 1] != "--allow")]
    if not args: sys.exit(__doc__)
    base, head = args[0], (args[1] if len(args) > 1 else None)
    rows = [l.split("\t") for l in git("diff", "--name-status", "--no-renames", base, *([head] if head else []), "--", *specs).splitlines()]
    if not head: rows += [["A", p] for p in git("ls-files", "--others", "--exclude-standard", "--", *specs).splitlines()]
    ok = bad = 0
    for st, path in ((r[0], r[-1]) for r in rows if r):
        if path.lower().endswith(".md"): continue
        if path in allow: print(f"ALLOWED {path}"); continue
        if st != "M": res = "FAIL code file " + {"A": "added", "D": "deleted"}.get(st[0], st)
        else:
            after = git("show", f"{head}:{path}") if head else open(path, encoding="utf-8").read()
            res = compare(path, git("show", f"{base}:{path}"), after)
        if res == "OK": ok += 1
        else: bad += 1; print(f"FAIL {path}: {res[5:]}")
    print(f"{ok} code files changed only in comments; {bad} failed.")
    sys.exit(1 if bad else 0)

if __name__ == "__main__": main(sys.argv[1:])
