#!/usr/bin/env python3
"""Fetch public HTML without a browser session; never emit its body or raw effective URL."""
import ipaddress
import json
import pathlib
import re
import subprocess
import sys
import tempfile
import urllib.parse

ROOT = pathlib.Path(__file__).resolve().parents[1]
MAX_PAGE_BYTES = 8 * 1024 * 1024
NAVIGATION_HEADERS = {
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9",
    "Sec-Fetch-Mode": "navigate",
}
MARKERS = (
    "browser_native_hd_url",
    "__UNIVERSAL_DATA_FOR_REHYDRATION__",
    "playabilityStatus",
)


def public_url(value):
    if not isinstance(value, str):
        raise ValueError("Use a public HTTPS page without embedded credentials")
    try:
        parsed = urllib.parse.urlsplit(value)
        host = parsed.hostname
        _ = parsed.port
        if (
            parsed.scheme != "https"
            or not host
            or parsed.username is not None
            or parsed.password is not None
            or any(ord(char) < 32 or ord(char) == 127 for char in value)
            or host == "localhost"
            or host.endswith((".localhost", ".local", ".internal"))
        ):
            raise ValueError
        try:
            address = ipaddress.ip_address(host)
        except ValueError:
            address = None
        if address is not None and not address.is_global:
            raise ValueError
    except (ValueError, UnicodeError):
        raise ValueError("Use a public HTTPS page without embedded credentials") from None
    return parsed


def version():
    text = (ROOT / "gradle.properties").read_text()
    match = re.search(r"^yft\.versionName=([0-9A-Za-z][0-9A-Za-z.+-]*)$", text, re.M)
    if not match:
        raise ValueError("Repository version is unavailable")
    return match.group(1)


def safe_summary(metadata, body, transport_exit):
    if not isinstance(metadata, dict):
        metadata = {}
    try:
        parsed = public_url(metadata.get("url_effective", ""))
        host = parsed.hostname
        # Encode control/unusual characters; omit all user-info, query and fragment fields.
        path = urllib.parse.quote(parsed.path or "/", safe="/%-._~")
    except ValueError:
        host, path = "unavailable", "/"
    try:
        status = int(metadata.get("http_code", 0))
    except (ValueError, TypeError):
        status = 0
    if status not in range(100, 600):
        status = 0
    lines = [
        f"status: {status}",
        f"host: {host}",
        f"path: {path}",
        f"bytes: {len(body)}",
    ]
    lines.extend(f"marker {marker}: {'yes' if marker.encode() in body else 'no'}" for marker in MARKERS)
    if transport_exit:
        lines.append(f"transport: curl exit {transport_exit}")
    return "\n".join(lines)


def check(url):
    public_url(url)
    agent = f"Mozilla/5.0 (X11; Linux x86_64) YFT/{version()}"
    with tempfile.TemporaryDirectory(prefix="yft-live-check-") as temporary:
        body_file = pathlib.Path(temporary) / "page"
        command = [
            "curl", "--disable", "--silent", "--show-error", "--location",
            "--proto", "=https", "--proto-redir", "=https", "--max-redirs", "5",
            "--connect-timeout", "10", "--max-time", "30",
            "--max-filesize", str(MAX_PAGE_BYTES), "--compressed",
            "--user-agent", agent, "--output", str(body_file), "--write-out", "%{json}",
        ]
        for name, value in NAVIGATION_HEADERS.items():
            command.extend(("--header", f"{name}: {value}"))
        command.extend(("--url", url))
        # curl stderr may mention a raw URL: capture it, never echo it on errors.
        result = subprocess.run(command, capture_output=True, timeout=40, check=False)
        try:
            metadata = json.loads(result.stdout)
        except (json.JSONDecodeError, UnicodeDecodeError):
            metadata = {}
        if not isinstance(metadata, dict):
            metadata = {}
        body = body_file.read_bytes()[:MAX_PAGE_BYTES] if body_file.exists() else b""
        print(safe_summary(metadata, body, result.returncode))
        status = metadata.get("http_code", 0)
        return 0 if result.returncode == 0 and isinstance(status, int) and 200 <= status < 300 else 1


def main():
    if len(sys.argv) != 2:
        print("Usage: bash scripts/live-check.sh <public-https-url>", file=sys.stderr)
        return 2
    try:
        return check(sys.argv[1])
    except ValueError as error:
        print(f"ERROR: {error}", file=sys.stderr)
    except (OSError, subprocess.TimeoutExpired):
        print("ERROR: Public page check could not finish", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
