#!/usr/bin/env python3
"""Emit safe emulator annotations; raw logs and UI hierarchies are never artifacts."""

import argparse
import pathlib
import re
import urllib.parse
import xml.etree.ElementTree as ET

URL = re.compile(r"""https?://[^\s<>"']+""", re.IGNORECASE)
SENSITIVE = re.compile(
    r"\b(cookie|set-cookie|authorization|proxy-authorization|password|"
    r"access[_-]?token|refresh[_-]?token|visitor[_-]?data|x-goog-visitor-id|"
    r"x-yt-identity-token|x-csrf-token|token)[\"']?\s*[:=].*",
    re.IGNORECASE,
)
DIAGNOSTIC = re.compile(
    r"YFT-DIAG ([a-z0-9-]{1,24} [a-z]{1,8}"
    r"(?: [A-Za-z]{1,16}=[A-Za-z0-9._/:>-]{0,240}){0,40})\s*$"
)
CONSOLE_ERROR = re.compile(r"CONSOLE\(\d+\)\] \"(Uncaught [A-Za-z]*Error)")
BOUNDS = re.compile(
    r"^(browser-address|WebView) bounds="
    r"(\[-?\d+,-?\d+\]\[-?\d+,-?\d+\]|missing|absent)$"
)


def sanitize_logcat(text):
    def safe_origin(match):
        try:
            url = urllib.parse.urlsplit(match.group())
            return f"{url.scheme}://{url.hostname or '[invalid-host]'}/[redacted]"
        except ValueError:
            return "[redacted-url]"

    text = URL.sub(safe_origin, text)
    text = SENSITIVE.sub(lambda match: match.group(1) + ": [REDACTED]", text)
    text = re.sub(r"\bBearer\s+\S+", "Bearer [REDACTED]", text, flags=re.IGNORECASE)
    return re.sub(r"\bgh[pousr]_[A-Za-z0-9_]+", "[REDACTED]", text)


def safe_bounds(text):
    return [line for line in text.splitlines() if BOUNDS.fullmatch(line)]


def escape_annotation(text):
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def site_diagnostics(sanitized):
    """P2 page diagnostics: only whitelisted key=value lines and console error types."""
    lines = []
    for line in sanitized.splitlines():
        match = DIAGNOSTIC.search(line)
        if match:
            lines.append(match.group(1))
    errors = {}
    for line in sanitized.splitlines():
        match = CONSOLE_ERROR.search(line)
        if match:
            errors[match.group(1)] = errors.get(match.group(1), 0) + 1
    return lines, errors


def sanitize_reports(folders):
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for folder in folders:
        for path in sorted(folder.rglob("*")):
            if not path.is_file():
                continue
            if path.suffix == ".xml":
                tree = ET.parse(path)
                root = tree.getroot()
                if root.tag == "testsuite":
                    for key in totals:
                        totals[key] += int(root.get(key, "0"))
                for node in root.iter():
                    for key, value in list(node.attrib.items()):
                        node.set(key, sanitize_logcat(value))
                    if node.text:
                        node.text = sanitize_logcat(node.text)
                    if node.tail:
                        node.tail = sanitize_logcat(node.tail)
                tree.write(path, encoding="utf-8", xml_declaration=True)
            elif path.suffix in {".txt", ".log", ".html", ".json", ".js", ".css"}:
                path.write_text(sanitize_logcat(path.read_text(errors="replace")))
    return totals


def diagnostics(raw, output, emit=print, require_screenshots=False):
    output.mkdir(parents=True, exist_ok=True)
    try:
        sanitized = sanitize_logcat(raw.read_text(errors="replace"))
        (output / "logcat.txt").write_text(sanitized)
    finally:
        raw.unlink(missing_ok=True)
    fatal = [line for line in sanitized.splitlines() if "FATAL EXCEPTION" in line]
    for _ in fatal:
        emit("::error::FATAL EXCEPTION detected in emulator logcat")
    for path in sorted(output.rglob("*.bounds.txt")):
        bounds = safe_bounds(path.read_text(errors="replace"))
        if bounds:
            emit("::notice::" + path.stem + ": " + "; ".join(bounds))
    if "slow-site warning" in sanitized:
        emit("::warning::Public HTML5 page was slow; media-found assertion is best effort")
    emit(f"::notice::Emulator logcat FATAL EXCEPTION count: {len(fatal)}")
    site_lines, console_errors = site_diagnostics(sanitized)
    if site_lines:
        emit("::notice::Site page diagnostics%0A" + escape_annotation("\n".join(site_lines[:40])))
    if console_errors:
        emit("::notice::Page console errors: " + ", ".join(
            f"{name}={count}" for name, count in sorted(console_errors.items())
        ))
    missing = []
    if require_screenshots:
        for name in ["01-browser-empty.png", "02-browser-page.png", "03-found.png"]:
            if not any(output.rglob(name)):
                missing.append(name)
                emit(f"::error::Emulator screenshot missing: {name}")
    return 1 if fatal or missing else 0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw-log", type=pathlib.Path, required=True)
    parser.add_argument("--output-dir", type=pathlib.Path, required=True)
    parser.add_argument("--reports", type=pathlib.Path, action="append", default=[])
    args = parser.parse_args()
    totals = sanitize_reports(args.reports)
    print("::notice::Instrumentation results: " + " ".join(
        f"{key}={value}" for key, value in totals.items()
    ))
    return diagnostics(args.raw_log, args.output_dir, require_screenshots=True)


if __name__ == "__main__":
    raise SystemExit(main())