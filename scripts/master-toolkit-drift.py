#!/usr/bin/env python3
"""Master toolkit drift check (R3).

The Master toolkit (extractor-master/.../toolkit) copies pure helpers from main's site
extractors instead of moving them, so main stays untouched. Each copy is listed in
extractor-master/toolkit-provenance.tsv with the main file, the symbol it was copied from and
the SHA-256 of that symbol's text at the base commit. This script extracts the same symbols
from another ref (default origin/main) and reports every symbol whose text changed or vanished,
so the copy can be reviewed before the next main merge. R6: the symbol `*` marks a whole-file
copy (the YouTube module); its text after the package line is hashed, and the Master copy
must still hash the same.

  python3 scripts/master-toolkit-drift.py                 # compare with origin/main
  python3 scripts/master-toolkit-drift.py --ref 34a41890  # self-check: must report no drift
  python3 scripts/master-toolkit-drift.py --write         # re-record hashes at the base ref
  python3 scripts/master-toolkit-drift.py --warn-only     # report as CI warnings, exit 0

Exit status: 0 no drift (or --warn-only), 1 drift, 2 usage or git error.
"""
import argparse
import hashlib
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TABLE = os.path.join(ROOT, "extractor-master", "toolkit-provenance.tsv")
MODIFIERS = (
    "private|internal|public|protected|override|suspend|inline|const|operator|infix|"
    "tailrec|data|enum|sealed|abstract|open|lateinit|@JvmStatic"
)
WHOLE_FILE = "*"
CONTINUATION = (".", "?.", "?:", ")", "]", "}", "=", "+", "-", "&&", "||", "->", ",")


def git_show(ref, path):
    try:
        return subprocess.run(
            ["git", "-C", ROOT, "show", f"{ref}:{path}"],
            check=True, capture_output=True, text=True,
        ).stdout
    except subprocess.CalledProcessError:
        return None


def code_only(line):
    """The line without string/char literals and line comments, for bracket counting."""
    line = re.sub(r'"(?:\\.|[^"\\])*"', '""', line)
    line = re.sub(r"'(?:\\.|[^'\\])'", "''", line)
    return line.split("//", 1)[0]


def indent(line):
    return len(line) - len(line.lstrip(" "))


def extent(lines, start):
    """Lines of the declaration starting at `start`: bracket-balanced, then up to the next
    line at the same or a lower indent that does not continue the expression."""
    base = indent(lines[start])
    depth = 0
    end = start
    for i in range(start, len(lines)):
        stripped = code_only(lines[i])
        depth += sum(stripped.count(c) for c in "([{") - sum(stripped.count(c) for c in ")]}")
        end = i
        if depth > 0:
            continue
        nxt = next((j for j in range(i + 1, len(lines)) if lines[j].strip()), None)
        if nxt is None:
            break
        following = lines[nxt]
        if indent(following) > base:
            continue
        if indent(following) == base and following.strip().startswith(CONTINUATION):
            continue
        break
    return [l.rstrip() for l in lines[start:end + 1]]


def body_after_package(source):
    """A whole-file copy's text after its package line (R6: only the package differs)."""
    lines = source.splitlines()
    for index, line in enumerate(lines):
        if line.startswith("package "):
            return "\n".join(lines[index + 1:])
    return None


def symbol_text(source, name):
    """Every outermost declaration of `name` (fun, val, var, class, object), joined.

    The symbol `*` stands for the whole file after its package line.
    """
    if name == WHOLE_FILE:
        return body_after_package(source)
    pattern = re.compile(
        rf"^(\s*)(?:(?:{MODIFIERS})\s+)*(?:fun|val|var|class|object|interface)\s+"
        rf"(?:<[^>]+>\s*)?(?:[A-Za-z_][\w<>?, ]*\.)?{re.escape(name)}\b"
    )
    lines = source.splitlines()
    hits = [i for i, l in enumerate(lines) if pattern.match(l)]
    if not hits:
        return None
    outer = min(indent(lines[i]) for i in hits)
    blocks = ["\n".join(extent(lines, i)) for i in hits if indent(lines[i]) == outer]
    return "\n\n".join(blocks)


def digest(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def read_table():
    base, rows = None, []
    with open(TABLE, encoding="utf-8") as handle:
        for line in handle:
            line = line.rstrip("\n")
            if line.startswith("# base "):
                base = line.split()[2]
            if not line or line.startswith("#"):
                continue
            cells = line.split("\t")
            if cells[0] == "master_file":
                continue
            if len(cells) != 4:
                sys.exit(f"bad row in {TABLE}: {line!r}")
            rows.append(cells)
    if base is None:
        sys.exit(f"{TABLE} has no '# base <commit>' line")
    return base, rows


def write_table(base, rows):
    header = []
    with open(TABLE, encoding="utf-8") as handle:
        for line in handle:
            if line.startswith("#"):
                header.append(line)
            else:
                break
    cache, out = {}, []
    for master, main, symbol, _ in rows:
        source = cache.setdefault(main, git_show(base, main))
        text = symbol_text(source or "", symbol)
        if text is None:
            sys.exit(f"{main}: {symbol} not found at {base}")
        out.append("\t".join([master, main, symbol, digest(text)]))
    with open(TABLE, "w", encoding="utf-8") as handle:
        handle.writelines(header)
        handle.write("master_file\tmain_file\tsymbol\tsha256\n")
        handle.write("\n".join(out) + "\n")
    print(f"recorded {len(out)} symbols at {base}")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--ref", default="origin/main")
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--warn-only", action="store_true")
    parser.add_argument("--show", metavar="SYMBOL", help="print a symbol's extracted text")
    args = parser.parse_args()
    base, rows = read_table()
    if args.write:
        write_table(base, rows)
        return 0
    cache, drift = {}, []
    for master, main, symbol, expected in rows:
        if main not in cache:
            cache[main] = git_show(args.ref, main)
        source = cache[main]
        if source is None:
            drift.append((master, main, symbol, "file missing"))
            continue
        text = symbol_text(source, symbol)
        if args.show == symbol:
            print(f"--- {main}: {symbol}\n{text}\n")
        if text is None:
            drift.append((master, main, symbol, "symbol missing"))
        elif digest(text) != expected:
            drift.append((master, main, symbol, "changed"))
        if symbol == WHOLE_FILE:
            # The copy itself must still be main's text: a local edit is drift too.
            try:
                with open(os.path.join(ROOT, master), encoding="utf-8") as handle:
                    copy = body_after_package(handle.read())
            except OSError:
                copy = None
            if copy is None or digest(copy) != expected:
                drift.append((master, main, symbol, "copy edited"))
    for master, main, symbol, why in drift:
        message = f"{main}: {symbol} {why} since {base[:8]}; review {master}"
        print(f"::warning::{message}" if args.warn_only else f"DRIFT {message}")
    print(f"{len(rows)} copied symbols checked against {args.ref}: {len(drift)} drifted")
    return 0 if args.warn_only or not drift else 1


if __name__ == "__main__":
    sys.exit(main())
