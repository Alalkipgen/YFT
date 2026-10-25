#!/usr/bin/env python3
"""Social plan S4 (MASTER_KEY_SOCIAL_PLAN.md section 7.3; R9 in MASTER_KEY_PHASE1_PLAN.md): the
master-recipes-publish workflow's helper. Data only, and it never sees the private key: a
committed config (extractor-master/recipes/configs/<name>.json: "schema", "sites" and, optional,
"rungsOff") becomes the payload the workflow signs, with the next version and its expiry date.

  compose <config.json> --version N --expires YYYY-MM-DD --out <payload.json>
  check <payload.json> --schema <master-recipes.schema.json>   the schema and the reader's limits
  verify <envelope.json> --key <public-key.hex> [--payload <payload.json>]
                                        signed by that key (openssl), and that very payload
  live-version <envelope.json>          the published payload's version (0: no file, or empty)

The reader (RemoteRecipeConfig, in the app) checks again what no schema can say: an endpoint
keeps the bundled host, a header name matches case-insensitively, paths are only added.
"""

import argparse
import datetime
import json
import pathlib
import re
import subprocess
import sys
import tempfile

TOP_KEYS = ("schema", "version", "expires", "sites", "rungsOff")
CONFIG_KEYS = {"schema", "sites", "rungsOff"}
SITES = {"vimeo", "x", "facebook", "tiktok", "instagram"}
RUNGS = ("F1", "F2", "F3", "T1", "T2", "T3", "X1", "X3", "I2", "probe", "I3")
MAX_CHARS = 64 * 1024
MAX_DEPTH = 7  # the reader's bound is 8 (BoundedJsonParser maxDepth); one level spare
MAX_NODES = 7000  # the reader's bound is 8000; some spare
DATE = re.compile(r"(2[0-9]{3})-([0-9]{2})-([0-9]{2})")


def fail(message):
    print(f"recipes: {message}", file=sys.stderr)
    sys.exit(1)


def read_text(path):
    return pathlib.Path(path).read_text(encoding="utf-8")


def date_of(text):
    match = DATE.fullmatch(text or "")
    if not match:
        return None
    try:
        return datetime.date(*map(int, match.groups()))
    except ValueError:
        return None


def shape(value, depth=1):
    """The value's nesting depth (containers) and node count, as the reader bounds them."""
    if isinstance(value, dict):
        children = [shape(item, depth + 1) for item in value.values()]
    elif isinstance(value, list):
        children = [shape(item, depth + 1) for item in value]
    else:
        return depth, 1
    return max([depth] + [d for d, _ in children]), 1 + sum(n for _, n in children)


def problems(payload_text):
    """What the reader would reject in this payload text (empty: none found here)."""
    found = []
    if len(payload_text) > MAX_CHARS:
        found.append(f"payload larger than {MAX_CHARS} characters")
    try:
        payload = json.loads(payload_text)
    except ValueError as error:
        return found + [f"not JSON: {error}"]
    if not isinstance(payload, dict):
        return found + ["payload must be an object"]
    extra = sorted(set(payload) - set(TOP_KEYS))
    if extra:
        found.append(f"unknown keys {extra}")
    missing = [key for key in ("schema", "version", "expires", "sites") if key not in payload]
    if missing:
        found.append(f"missing keys {missing}")
    if payload.get("schema") != 1 or isinstance(payload.get("schema"), bool):
        found.append("schema must be 1")
    version = payload.get("version")
    if not isinstance(version, int) or isinstance(version, bool) or not 1 <= version <= 2**31 - 1:
        found.append("version must be a whole number from 1")
    expires = date_of(payload.get("expires") if isinstance(payload.get("expires"), str) else None)
    if expires is None:
        found.append("expires must be a date, YYYY-MM-DD")
    sites = payload.get("sites")
    if not isinstance(sites, dict):
        found.append("sites must be an object")
    else:
        unknown = sorted(set(sites) - SITES)
        if unknown:
            found.append(f"unknown sites {unknown}")
    if "rungsOff" in payload:
        off = payload["rungsOff"]
        if not isinstance(off, list) or not all(isinstance(name, str) for name in off):
            found.append("rungsOff must be a list of rung names")
        else:
            unknown = sorted(set(off) - set(RUNGS))
            if unknown:
                found.append(f"rungsOff: unknown rungs {unknown}")
            if len(set(off)) != len(off):
                found.append("rungsOff names a rung twice")
    depth, nodes = shape(payload)
    if depth > MAX_DEPTH:
        found.append(f"nested deeper than {MAX_DEPTH}")
    if nodes > MAX_NODES:
        found.append(f"more than {MAX_NODES} values")
    return found


def compose(config_path, version, expires):
    config = json.loads(read_text(config_path))
    if not isinstance(config, dict):
        fail("a config must be an object")
    extra = sorted(set(config) - CONFIG_KEYS)
    if extra:
        fail(f"a config holds only {sorted(CONFIG_KEYS)} (version and expires are the workflow's): {extra}")
    if date_of(expires) is None:
        fail("expires must be a date, YYYY-MM-DD")
    payload = {"schema": config.get("schema"), "version": version, "expires": expires, "sites": config.get("sites")}
    if "rungsOff" in config:
        payload["rungsOff"] = config["rungsOff"]
    return json.dumps(payload, indent=2, ensure_ascii=True) + "\n"


def check(payload_path, schema_path):
    text = pathlib.Path(payload_path).read_text(encoding="utf-8")
    found = problems(text)
    try:
        import jsonschema
    except ImportError:
        fail("the schema check needs jsonschema (pip install jsonschema==4.23.0)")
    schema = json.loads(read_text(schema_path))
    validator = jsonschema.Draft202012Validator(schema)
    if not found:
        for error in sorted(validator.iter_errors(json.loads(text)), key=lambda e: list(e.path)):
            where = "/".join(str(part) for part in error.path) or "(top)"
            found.append(f"schema: {where}: {error.message}")
    if found:
        for problem in found:
            print(f"recipes: {problem}", file=sys.stderr)
        sys.exit(1)
    print("recipes: the payload passes the schema and the reader's limits")


def verify(envelope_path, key_path, payload_path):
    envelope = json.loads(read_text(envelope_path))
    if not isinstance(envelope, dict) or set(envelope) != {"alg", "payload", "signature"}:
        fail("an envelope is {alg, payload, signature}")
    if envelope["alg"] != "ES256":
        fail("alg must be ES256")
    payload = envelope["payload"]
    if payload_path is not None:
        wanted = pathlib.Path(payload_path).read_bytes().decode("utf-8")
        if payload != wanted:
            fail("the envelope's payload is not the composed payload")
    try:
        signature = bytes.fromhex(envelope["signature"])
        key = bytes.fromhex(read_text(key_path).strip())
    except ValueError:
        fail("the signature and the key are hex")
    with tempfile.TemporaryDirectory() as work:
        work = pathlib.Path(work)
        (work / "key.der").write_bytes(key)
        (work / "payload").write_bytes(payload.encode("utf-8"))
        (work / "signature").write_bytes(signature)
        subprocess.run(
            ["openssl", "pkey", "-pubin", "-inform", "DER", "-in", work / "key.der", "-out", work / "key.pem"],
            check=True, capture_output=True,
        )
        result = subprocess.run(
            ["openssl", "dgst", "-sha256", "-verify", work / "key.pem", "-signature", work / "signature", work / "payload"],
            capture_output=True, text=True,
        )
    if result.returncode != 0:
        fail("the signature does not verify with this key")
    print("recipes: the envelope verifies with the committed public key")


def live_version(envelope_path):
    path = pathlib.Path(envelope_path)
    if not path.is_file() or not path.read_text(encoding="utf-8").strip():
        return 0
    envelope = json.loads(path.read_text(encoding="utf-8"))
    version = json.loads(envelope["payload"])["version"]
    if not isinstance(version, int) or isinstance(version, bool) or version < 1:
        fail("the live payload has no version")
    return version


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    composing = commands.add_parser("compose")
    composing.add_argument("config")
    composing.add_argument("--version", type=int, required=True)
    composing.add_argument("--expires", required=True)
    composing.add_argument("--out", required=True)
    checking = commands.add_parser("check")
    checking.add_argument("payload")
    checking.add_argument("--schema", required=True)
    verifying = commands.add_parser("verify")
    verifying.add_argument("envelope")
    verifying.add_argument("--key", required=True)
    verifying.add_argument("--payload")
    live = commands.add_parser("live-version")
    live.add_argument("envelope")
    args = parser.parse_args(argv)
    if args.command == "compose":
        text = compose(args.config, args.version, args.expires)
        found = problems(text)
        if found:
            fail("; ".join(found))
        with open(args.out, "w", encoding="utf-8", newline="") as out:
            out.write(text)
        print(f"recipes: composed version {args.version}, expires {args.expires}")
    elif args.command == "check":
        check(args.payload, args.schema)
    elif args.command == "verify":
        verify(args.envelope, args.key, args.payload)
    else:
        print(live_version(args.envelope))


if __name__ == "__main__":
    main()
