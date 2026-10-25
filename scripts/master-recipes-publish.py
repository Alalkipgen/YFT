#!/usr/bin/env python3
"""Social plan S4 (MASTER_KEY_SOCIAL_PLAN.md section 7.3; R9 in MASTER_KEY_PHASE1_PLAN.md): the
master-recipes-publish workflow's helper. Data only, and it never sees the private key: a
committed config (extractor-master/recipes/configs/<name>.json: "schema", "sites" and, optional,
"rungsOff") becomes the payloads the workflow signs, with the next version and its expiry date.

VK Master plan section 9 (R9 schema 2): a committed config is schema 2 (schema 1 plus site "vk"
and rungs V1-V3) and becomes two payloads with the same version and expiry: master-recipes-2.json's
(schema 2, all five sites, for v1.0.1) and master-recipes.json's (schema 1, for v1.0.0: "vk" left
out of "sites", V1-V3 left out of "rungsOff" and "rungsOff" itself when that empties it; for a
config without VK, byte for byte what this helper composed before schema 2).

  compose <config.json> --version N --expires YYYY-MM-DD --out <payload.json> --out2 <payload-2.json>
  check <payload.json> --schema <master-recipes[-2].schema.json>
                                        the schema (1 or 2, its "schema" const) and the reader's limits
  verify <envelope.json> --key <public-key.hex> [--payload <payload.json>]
                                        signed by that key (openssl), and that very payload
  pair <master-recipes.json> <master-recipes-2.json> --key <public-key.hex>
                                        both signed by that key, schemas 1 and 2, one config
  live-version <envelope.json>          the published payload's version (0: no file, or empty)

The readers (RemoteRecipeConfig and RemoteRecipeConfig2, in the app) check again what no schema
can say: an endpoint keeps the bundled host, a header name matches case-insensitively, paths are
only added.
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
VK = "vk"
VK_RUNGS = ("V1", "V2", "V3")
CONFIG_SCHEMA = 2  # a committed config; master-recipes.json is its schema-1 projection
SITES_OF = {1: SITES, 2: SITES | {VK}}
RUNGS_OF = {1: RUNGS, 2: RUNGS + VK_RUNGS}
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


def schema_1(payload):
    """A schema-2 payload as the schema-1 file carries it (the reader's RemoteRecipeConfig2.schema1):
    the same keys in the same order, "schema" 1, "vk" left out of "sites", V1-V3 left out of
    "rungsOff" and "rungsOff" itself when that empties it."""
    out = {}
    for key, value in payload.items():
        if key == "schema":
            out[key] = 1
        elif key == "sites" and isinstance(value, dict):
            out[key] = {site: overlay for site, overlay in value.items() if site != VK}
        elif key == "rungsOff" and isinstance(value, list):
            kept = [name for name in value if name not in VK_RUNGS]
            if kept or not value:
                out[key] = kept
        else:
            out[key] = value
    return out


def dump(payload):
    return json.dumps(payload, indent=2, ensure_ascii=True) + "\n"


def problems(payload_text, schema=1):
    """What the schema-[schema] reader would reject in this payload text (empty: none found here)."""
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
    if payload.get("schema") != schema or isinstance(payload.get("schema"), bool):
        found.append(f"schema must be {schema}")
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
        unknown = sorted(set(sites) - SITES_OF[schema])
        if unknown:
            found.append(f"unknown sites {unknown}")
    if "rungsOff" in payload:
        off = payload["rungsOff"]
        if not isinstance(off, list) or not all(isinstance(name, str) for name in off):
            found.append("rungsOff must be a list of rung names")
        else:
            unknown = sorted(set(off) - set(RUNGS_OF[schema]))
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
    """The two payload texts of one committed schema-2 config: (schema 1, schema 2)."""
    config = json.loads(read_text(config_path))
    if not isinstance(config, dict):
        fail("a config must be an object")
    extra = sorted(set(config) - CONFIG_KEYS)
    if extra:
        fail(f"a config holds only {sorted(CONFIG_KEYS)} (version and expires are the workflow's): {extra}")
    if config.get("schema") != CONFIG_SCHEMA or isinstance(config.get("schema"), bool):
        fail(f"a committed config is schema {CONFIG_SCHEMA} (both files come from it)")
    if date_of(expires) is None:
        fail("expires must be a date, YYYY-MM-DD")
    payload = {"schema": config.get("schema"), "version": version, "expires": expires, "sites": config.get("sites")}
    if "rungsOff" in config:
        payload["rungsOff"] = config["rungsOff"]
    return dump(schema_1(payload)), dump(payload)


def schema_number(schema_path):
    """The schema a JSON schema file describes (its "schema" const: 1 or 2)."""
    schema = json.loads(read_text(schema_path))
    number = schema.get("properties", {}).get("schema", {}).get("const")
    if number not in SITES_OF or isinstance(number, bool):
        fail(f"{schema_path} describes neither schema 1 nor schema 2")
    return number


def check(payload_path, schema_path):
    text = pathlib.Path(payload_path).read_text(encoding="utf-8")
    found = problems(text, schema_number(schema_path))
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


def verified(envelope_path, key_path):
    """The payload text of an envelope signed by that key (it fails otherwise)."""
    envelope = json.loads(read_text(envelope_path))
    if not isinstance(envelope, dict) or set(envelope) != {"alg", "payload", "signature"}:
        fail("an envelope is {alg, payload, signature}")
    if envelope["alg"] != "ES256":
        fail("alg must be ES256")
    payload = envelope["payload"]
    if not isinstance(payload, str):
        fail("an envelope's payload is a text")
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
    return payload


def verify(envelope_path, key_path, payload_path):
    payload = verified(envelope_path, key_path)
    if payload_path is not None:
        wanted = pathlib.Path(payload_path).read_bytes().decode("utf-8")
        if payload != wanted:
            fail("the envelope's payload is not the composed payload")
    print("recipes: the envelope verifies with the committed public key")


def pair(first_path, second_path, key_path):
    """master-recipes.json and master-recipes-2.json: both signed by that key, schemas 1 and 2, the
    reader's limits, and the first the schema-1 projection of the second (one config, one version)."""
    first = verified(first_path, key_path)
    second = verified(second_path, key_path)
    found = [f"master-recipes.json: {problem}" for problem in problems(first, 1)]
    found += [f"master-recipes-2.json: {problem}" for problem in problems(second, 2)]
    if not found and json.loads(first) != schema_1(json.loads(second)):
        found.append("master-recipes.json is not master-recipes-2.json without vk and V1-V3")
    if found:
        for problem in found:
            print(f"recipes: {problem}", file=sys.stderr)
        sys.exit(1)
    print("recipes: both files verify with the committed public key and carry one config")


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
    composing.add_argument("--out", required=True, help="the schema-1 payload (master-recipes.json)")
    composing.add_argument("--out2", required=True, help="the schema-2 payload (master-recipes-2.json)")
    checking = commands.add_parser("check")
    checking.add_argument("payload")
    checking.add_argument("--schema", required=True)
    verifying = commands.add_parser("verify")
    verifying.add_argument("envelope")
    verifying.add_argument("--key", required=True)
    verifying.add_argument("--payload")
    pairing = commands.add_parser("pair")
    pairing.add_argument("first")
    pairing.add_argument("second")
    pairing.add_argument("--key", required=True)
    live = commands.add_parser("live-version")
    live.add_argument("envelope")
    args = parser.parse_args(argv)
    if args.command == "compose":
        first, second = compose(args.config, args.version, args.expires)
        found = problems(second, 2) + problems(first, 1)
        if found:
            fail("; ".join(found))
        for path, text in ((args.out, first), (args.out2, second)):
            with open(path, "w", encoding="utf-8", newline="") as out:
                out.write(text)
        print(f"recipes: composed version {args.version}, expires {args.expires} (schema 1 and schema 2)")
    elif args.command == "check":
        check(args.payload, args.schema)
    elif args.command == "verify":
        verify(args.envelope, args.key, args.payload)
    elif args.command == "pair":
        pair(args.first, args.second, args.key)
    else:
        print(live_version(args.envelope))


if __name__ == "__main__":
    main()
