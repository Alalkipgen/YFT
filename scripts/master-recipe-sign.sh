#!/usr/bin/env bash
# R9: keys and signing for Master's data-only recipe config (MASTER_KEY_PHASE1_PLAN.md R9).
# The app carries only the public key (-Pyft.masterRecipeKey=<hex>); keep the private key off
# the repo. Schema: extractor-master/recipes/master-recipes.schema.json.
#
#   bash scripts/master-recipe-sign.sh keygen ~/yft-recipe-key.pem    # once; prints the public key
#   bash scripts/master-recipe-sign.sh pubkey ~/yft-recipe-key.pem
#   bash scripts/master-recipe-sign.sh sign ~/yft-recipe-key.pem config.json recipes/master-recipes.json
#
# The signed file goes to the address in yft.masterRecipeUrl (default: recipes/master-recipes.json
# on the spike branch, raw.githubusercontent.com). Raise "version" for every new config.
set -euo pipefail

pubkey_hex() {
  openssl ec -in "$1" -pubout -outform DER 2>/dev/null | od -An -v -tx1 | tr -d ' \n'
  echo
}

case "${1:-}" in
  keygen)
    key=${2:?usage: keygen <private-key.pem>}
    if [[ -e "$key" ]]; then echo "$key exists; not overwritten" >&2; exit 2; fi
    (umask 077 && openssl ecparam -name prime256v1 -genkey -noout -out "$key")
    echo "private key: $key (never commit it)" >&2
    pubkey_hex "$key"
    ;;
  pubkey)
    pubkey_hex "${2:?usage: pubkey <private-key.pem>}"
    ;;
  sign)
    key=${2:?usage: sign <private-key.pem> <config.json> <out.json>}
    config=${3:?config}
    out=${4:?out}
    python3 -c '
import json, re, sys
text = open(sys.argv[1], encoding="utf-8", newline="").read()
config = json.loads(text)
assert isinstance(config, dict), "config must be an object"
extra = set(config) - {"schema", "version", "expires", "sites"}
assert not extra, f"unknown keys {sorted(extra)}"
assert config.get("schema") == 1, "schema must be 1"
assert isinstance(config.get("version"), int) and config["version"] >= 1, "version must be >= 1"
assert re.fullmatch(r"2[0-9]{3}-[0-9]{2}-[0-9]{2}", str(config.get("expires"))), "expires: YYYY-MM-DD"
assert isinstance(config.get("sites"), dict), "sites must be an object"
assert len(text) <= 65536, "config larger than 64 KiB"
' "$config"
    sig=$(openssl dgst -sha256 -sign "$key" "$config" | od -An -v -tx1 | tr -d ' \n')
    python3 -c '
import json, sys
text = open(sys.argv[1], encoding="utf-8", newline="").read()
with open(sys.argv[3], "w", encoding="utf-8") as out:
    json.dump({"alg": "ES256", "payload": text, "signature": sys.argv[2]}, out)
    out.write("\n")
' "$config" "$sig" "$out"
    echo "signed $config -> $out" >&2
    ;;
  *)
    sed -n '2,11p' "$0"
    exit 2
    ;;
esac