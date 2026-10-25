"""Social plan S4 (MASTER_KEY_SOCIAL_PLAN.md section 7.3): the master-recipes-publish workflow.

The owner's label recipes:<name> on draft PR #2 (or a manual run, once on the default branch)
signs one committed config and publishes it on the branch "recipes". The job runs only for the
owner, a recipes: label and the repository's own pull request; the helper composes the payload
(the next version, the expiry date), checks it as the reader would and verifies the envelope.
Throwaway keys only (made per run in a temporary directory, never kept).

VK Master plan section 9 (R9 schema 2): a committed config is schema 2, and one run publishes two
files from it with one version and one key: master-recipes.json (schema 1, vk and V1-V3 left out,
for v1.0.0) and master-recipes-2.json (schema 2, for v1.0.1). The workflow's own step scripts are
dry-run here against a temporary git remote (never the repository's, never its recipes branch).
"""

import importlib.util
import json
import os
import pathlib
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

from test_spike_label_triggers import Expression, render, text  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOW = (ROOT / ".github" / "workflows" / "master-recipes-publish.yml").read_text(encoding="utf-8")
HELPER = ROOT / "scripts" / "master-recipes-publish.py"
SIGNER = ROOT / "scripts" / "master-recipe-sign.sh"
RECIPES = ROOT / "extractor-master" / "recipes"
OWNER = "Alalkipgen"
REPOSITORY = f"{OWNER}/YFT"
SPEC = importlib.util.spec_from_file_location("master_recipes_publish", HELPER)
PUBLISH = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PUBLISH)


def has_jsonschema():
    try:
        import jsonschema  # noqa: F401
    except ImportError:
        return False
    return True


def legacy_compose(config, version, expires):
    """What the helper composed before schema 2 (the schema-1 file's bytes, which must not change)."""
    payload = {"schema": 1, "version": version, "expires": expires, "sites": config["sites"]}
    if "rungsOff" in config:
        payload["rungsOff"] = config["rungsOff"]
    return json.dumps(payload, indent=2, ensure_ascii=True) + "\n"


class Expr(Expression):
    """The label trigger test's expressions, with startsWith() (any case, as GitHub's)."""

    def primary(self):
        if self.peek() == "startsWith":
            self.take()
            self.take("(")
            arguments = [self.either()]
            while self.peek() == ",":
                self.take()
                arguments.append(self.either())
            self.take(")")
            search, prefix = arguments
            return text(search).lower().startswith(text(prefix).lower())
        return super().primary()


def job_if():
    """The publish job's if: (a folded block: its lines joined with spaces)."""
    block = re.search(r"^    if: >-\n((?:      .*\n)+)", WORKFLOW, re.M)
    if not block:
        raise AssertionError("no folded if: in the publish job")
    return " ".join(line.strip() for line in block.group(1).splitlines())


def env(name):
    return re.search(rf"^      {name}: (.+)$", WORKFLOW, re.M).group(1)


def event(name, label=None, actor=OWNER, head=REPOSITORY, config="defaults"):
    github = {
        "event_name": name, "triggering_actor": actor, "repository_owner": OWNER,
        "repository": REPOSITORY, "sha": "branch-commit", "event": {},
    }
    if name == "pull_request":
        github["event"] = {
            "action": "labeled", "label": {"name": label},
            "pull_request": {"number": 2, "head": {"sha": "spike-head", "repo": {"full_name": head}}},
        }
    return {"github": github, "inputs": {"config": config}}


def run(*args, check=True):
    return subprocess.run([sys.executable, str(HELPER), *map(str, args)], capture_output=True, text=True, check=check)


class TriggerTest(unittest.TestCase):
    def test_only_the_owners_recipes_label_on_the_repositorys_own_pr_or_a_manual_run_publishes(self):
        cases = {
            "the owner's label": (event("pull_request", "recipes:defaults"), True),
            "the owner's drill label": (event("pull_request", "recipes:drill-rung-off"), True),
            "a manual run": (event("workflow_dispatch"), True),
            "another label": (event("pull_request", "run-canary"), False),
            "a label with the word inside": (event("pull_request", "not-recipes:defaults"), False),
            "another person's label": (event("pull_request", "recipes:defaults", actor="someone-else"), False),
            "another repository's pull request": (event("pull_request", "recipes:defaults", head="someone-else/YFT"), False),
            "another person's manual run": (event("workflow_dispatch", actor="someone-else"), False),
            "a push": (event("push"), False),
        }
        for name, (context, expected) in cases.items():
            with self.subTest(name):
                self.assertIs(bool(Expr(job_if(), context).value()), expected)

    def test_the_config_name_and_the_source_commit(self):
        label = event("pull_request", "recipes:drill-key-change")
        self.assertEqual(render(env("CONFIG_NAME"), label), "recipes:drill-key-change")
        self.assertEqual(render(env("SOURCE_SHA"), label), "spike-head")
        manual = event("workflow_dispatch", config="defaults")
        self.assertEqual(render(env("CONFIG_NAME"), manual), "defaults")
        self.assertEqual(render(env("SOURCE_SHA"), manual), "branch-commit")

    def test_the_workflow_keeps_its_guards(self):
        self.assertIn("types: [labeled]", WORKFLOW)
        self.assertIn("cancel-in-progress: false", WORKFLOW)
        self.assertRegex(WORKFLOW, r"(?m)^permissions:\n  contents: read$")
        self.assertIn('git push origin "$commit:refs/heads/recipes"', WORKFLOW)
        self.assertNotRegex(WORKFLOW, r"push[^\n]*(--force|-f |\+)")
        self.assertIn("(umask 077 && printf '%s\\n' \"$YFT_RECIPE_KEY\" > \"$key\")", WORKFLOW)
        self.assertIn("trap 'shred -u \"$key\"", WORKFLOW)
        self.assertNotIn("echo \"$YFT_RECIPE_KEY", WORKFLOW)
        self.assertIn("extractor-master/recipes/master-recipes.pub.hex", WORKFLOW)
        self.assertIn("if: always() && github.event_name == 'pull_request'", WORKFLOW)

    def test_the_workflow_publishes_both_files_from_one_config_with_one_key(self):
        self.assertIn('--out "$RUNNER_TEMP/recipes/payload.json" --out2 "$RUNNER_TEMP/recipes/payload-2.json"', WORKFLOW)
        self.assertIn("--schema extractor-master/recipes/master-recipes.schema.json", WORKFLOW)
        self.assertIn("--schema extractor-master/recipes/master-recipes-2.schema.json", WORKFLOW)
        signs = re.findall(r'master-recipe-sign\.sh sign "\$key" "\$RUNNER_TEMP/recipes/(payload(?:-2)?\.json)"', WORKFLOW)
        self.assertEqual(signs, ["payload.json", "payload-2.json"])
        self.assertEqual(WORKFLOW.count("master-recipes-publish.py verify"), 2)
        self.assertIn("master-recipes-publish.py pair", WORKFLOW)
        self.assertIn('add "$out/master-recipes-2.json" master-recipes-2.json', WORKFLOW)
        self.assertIn('add "$out/master-recipes-2.json" "published/v$VERSION-$NAME.schema2.json"', WORKFLOW)
        self.assertIn('git cat-file -e "$parent:master-recipes-2.json"', WORKFLOW)
        self.assertIn("if (( live2 > live )); then", WORKFLOW)


class HelperTest(unittest.TestCase):
    def setUp(self):
        self.work = pathlib.Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.work, True)

    def compose(self, config, version=3, expires="2027-12-31", check=True):
        out = self.work / "payload.json"
        out2 = self.work / "payload-2.json"
        result = run(
            "compose", config, "--version", version, "--expires", expires, "--out", out, "--out2", out2, check=check,
        )
        return result, out, out2

    def config(self, data):
        path = self.work / "config.json"
        path.write_text(json.dumps(data), encoding="utf-8")
        return path

    def test_compose_adds_the_version_and_expiry_and_keeps_the_configs_data(self):
        for config in sorted((RECIPES / "configs").glob("*.json")):
            with self.subTest(config.name):
                result, out, out2 = self.compose(config)
                self.assertEqual(result.returncode, 0, result.stderr)
                source = json.loads(config.read_text(encoding="utf-8"))
                self.assertEqual(source["schema"], 2, "a committed config is schema 2")
                for path, schema in ((out, 1), (out2, 2)):
                    text = path.read_text(encoding="utf-8")
                    payload = json.loads(text)
                    self.assertEqual(list(payload)[:4], ["schema", "version", "expires", "sites"])
                    self.assertEqual((payload["schema"], payload["version"], payload["expires"]), (schema, 3, "2027-12-31"))
                    self.assertEqual(payload["sites"], source["sites"])
                    self.assertEqual(payload.get("rungsOff"), source.get("rungsOff"))
                    self.assertTrue(text.endswith("}\n"))
                    self.assertTrue(text.isascii())
                # Without vk the schema-1 file is, byte for byte, what the helper composed before schema 2.
                self.assertEqual(out.read_text(encoding="utf-8"), legacy_compose(source, 3, "2027-12-31"))

    def test_the_schema_1_file_leaves_out_vk_and_its_rungs_only(self):
        config = {
            "schema": 2,
            "sites": {"vk": {"agent": "VK agent"}, "x": {"agent": "X agent"}},
            "rungsOff": ["V1", "X1", "V3", "probe"],
        }
        result, out, out2 = self.compose(self.config(config))
        self.assertEqual(result.returncode, 0, result.stderr)
        first, second = json.loads(out.read_text(encoding="utf-8")), json.loads(out2.read_text(encoding="utf-8"))
        self.assertEqual(second["sites"], config["sites"])
        self.assertEqual(second["rungsOff"], config["rungsOff"])
        self.assertEqual(list(first), ["schema", "version", "expires", "sites", "rungsOff"])
        self.assertEqual(first["sites"], {"x": {"agent": "X agent"}})
        self.assertEqual(first["rungsOff"], ["X1", "probe"])
        self.assertEqual(PUBLISH.schema_1(second), first)
        cases = {
            "only VK's rungs: no rungsOff at all": (["V2"], None),
            "an empty list stays": ([], []),
            "schema 1's rungs stay in order": (["I3", "V1", "F1"], ["I3", "F1"]),
        }
        for name, (off, expected) in cases.items():
            with self.subTest(name):
                result, out, _ = self.compose(self.config({"schema": 2, "sites": {}, "rungsOff": off}))
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(json.loads(out.read_text(encoding="utf-8")).get("rungsOff"), expected)

    def test_the_golden_pair_the_kotlin_reader_reads_too(self):
        example2 = json.loads((RECIPES / "master-recipes-2.example.json").read_text(encoding="utf-8"))
        example1 = json.loads((RECIPES / "master-recipes.example.json").read_text(encoding="utf-8"))
        self.assertEqual(PUBLISH.schema_1(example2), example1)
        config = {key: example2[key] for key in ("schema", "sites", "rungsOff")}
        result, out, out2 = self.compose(self.config(config), version=1)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(out.read_text(encoding="utf-8")), example1)
        self.assertEqual(json.loads(out2.read_text(encoding="utf-8")), example2)

    def test_compose_refuses_what_the_reader_would_reject(self):
        bad = {
            "version": {"schema": 2, "version": 9, "sites": {}},
            "unknown rung": {"schema": 2, "sites": {}, "rungsOff": ["F9"]},
            "unknown VK rung": {"schema": 2, "sites": {}, "rungsOff": ["V4"]},
            "a rung twice": {"schema": 2, "sites": {}, "rungsOff": ["F1", "F1"]},
            "a VK rung twice": {"schema": 2, "sites": {}, "rungsOff": ["V2", "V2"]},
            "unknown site": {"schema": 2, "sites": {"vine": {}}},
            "schema 1 (a committed config is schema 2)": {"schema": 1, "sites": {}},
            "schema 3": {"schema": 3, "sites": {}},
            "schema true": {"schema": True, "sites": {}},
            "too deep": {"schema": 2, "sites": {"x": {"a": {"b": {"c": {"d": {"e": {"f": {}}}}}}}}},
            "too deep for VK": {"schema": 2, "sites": {"vk": {"a": {"b": {"c": {"d": {"e": {"f": {}}}}}}}}},
        }
        for name, config in bad.items():
            with self.subTest(name):
                result, out, out2 = self.compose(self.config(config), check=False)
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse(out.exists())
                self.assertFalse(out2.exists())
        result, _, _ = self.compose(RECIPES / "configs" / "defaults.json", expires="2027-02-30", check=False)
        self.assertNotEqual(result.returncode, 0)

    def test_the_readers_limits_per_schema(self):
        two = json.dumps({"schema": 2, "version": 1, "expires": "2027-12-31", "sites": {"vk": {}}, "rungsOff": ["V1"]})
        self.assertEqual(PUBLISH.problems(two, 2), [])
        self.assertEqual(
            PUBLISH.problems(two, 1),
            ["schema must be 1", "unknown sites ['vk']", "rungsOff: unknown rungs ['V1']"],
        )
        one = json.dumps({"schema": 1, "version": 1, "expires": "2027-12-31", "sites": {}})
        self.assertEqual(PUBLISH.problems(one), [])
        self.assertEqual(PUBLISH.problems(one, 2), ["schema must be 2"])
        self.assertEqual(PUBLISH.schema_number(RECIPES / "master-recipes.schema.json"), 1)
        self.assertEqual(PUBLISH.schema_number(RECIPES / "master-recipes-2.schema.json"), 2)

    def test_the_two_json_schemas_and_the_helper_name_the_same_sites_and_rungs(self):
        one = json.loads((RECIPES / "master-recipes.schema.json").read_text(encoding="utf-8"))
        two = json.loads((RECIPES / "master-recipes-2.schema.json").read_text(encoding="utf-8"))
        self.assertEqual(one["$defs"], two["$defs"])
        self.assertEqual(set(one["properties"]["sites"]["properties"]), PUBLISH.SITES)
        self.assertEqual(set(two["properties"]["sites"]["properties"]), PUBLISH.SITES | {"vk"})
        self.assertEqual(tuple(one["properties"]["rungsOff"]["items"]["enum"]), PUBLISH.RUNGS)
        self.assertEqual(tuple(two["properties"]["rungsOff"]["items"]["enum"]), PUBLISH.RUNGS + ("V1", "V2", "V3"))
        self.assertEqual(two["properties"]["rungsOff"]["maxItems"], 14)
        for key in ("version", "expires"):
            self.assertEqual(one["properties"][key], two["properties"][key])

    def test_every_committed_config_passes_the_schema(self):
        if not has_jsonschema():
            self.skipTest("jsonschema not installed")
        for config in sorted((RECIPES / "configs").glob("*.json")):
            with self.subTest(config.name):
                _, out, out2 = self.compose(config)
                result = run("check", out, "--schema", RECIPES / "master-recipes.schema.json", check=False)
                self.assertEqual(result.returncode, 0, result.stderr)
                result = run("check", out2, "--schema", RECIPES / "master-recipes-2.schema.json", check=False)
                self.assertEqual(result.returncode, 0, result.stderr)
                # Each payload only against its own schema.
                self.assertNotEqual(run("check", out2, "--schema", RECIPES / "master-recipes.schema.json", check=False).returncode, 0)
                self.assertNotEqual(run("check", out, "--schema", RECIPES / "master-recipes-2.schema.json", check=False).returncode, 0)
        example = RECIPES / "master-recipes-2.example.json"
        result = run("check", example, "--schema", RECIPES / "master-recipes-2.schema.json", check=False)
        self.assertEqual(result.returncode, 0, result.stderr)
        bad = self.work / "bad-2.json"
        bad.write_text(json.dumps({"schema": 2, "version": 1, "expires": "2027-12-31", "sites": {"vk": {"clientSecret": "x"}}}))
        self.assertNotEqual(run("check", bad, "--schema", RECIPES / "master-recipes-2.schema.json", check=False).returncode, 0)

    @unittest.skipUnless(shutil.which("openssl"), "openssl not installed")
    def test_the_signed_envelope_verifies_with_its_key_only_and_the_live_version_reads(self):
        _, payload, payload2 = self.compose(RECIPES / "configs" / "drill-rung-off.json", version=2)
        key = self.work / "key.pem"
        public = self.work / "public.hex"
        envelope = self.work / "master-recipes.json"
        envelope2 = self.work / "master-recipes-2.json"
        try:
            keygen = subprocess.run(["bash", str(SIGNER), "keygen", str(key)], capture_output=True, text=True, check=True)
            public.write_text(keygen.stdout, encoding="utf-8")
            subprocess.run(["bash", str(SIGNER), "sign", str(key), str(payload), str(envelope)], capture_output=True, check=True)
            subprocess.run(["bash", str(SIGNER), "sign", str(key), str(payload2), str(envelope2)], capture_output=True, check=True)
        finally:
            key.unlink(missing_ok=True)
        self.assertEqual(len(public.read_text(encoding="utf-8").strip()), 182)
        verified = run("verify", envelope, "--key", public, "--payload", payload, check=False)
        self.assertEqual(verified.returncode, 0, verified.stderr)
        self.assertEqual(run("live-version", envelope).stdout.strip(), "2")
        self.assertEqual(run("live-version", self.work / "none.json").stdout.strip(), "0")

        # Another key, another payload or a changed byte: refused.
        committed = RECIPES / "master-recipes.pub.hex"
        self.assertNotEqual(run("verify", envelope, "--key", committed, check=False).returncode, 0)
        other = self.work / "other.json"
        other.write_text(payload.read_text(encoding="utf-8").replace('"version": 2', '"version": 3'), encoding="utf-8")
        self.assertNotEqual(run("verify", envelope, "--key", public, "--payload", other, check=False).returncode, 0)
        changed = json.loads(envelope.read_text(encoding="utf-8"))
        changed["payload"] = changed["payload"].replace("probe", "I3")
        tampered = self.work / "tampered.json"
        tampered.write_text(json.dumps(changed), encoding="utf-8")
        self.assertNotEqual(run("verify", tampered, "--key", public, check=False).returncode, 0)

        # Schema 2: the same key signs both files, both verify, and they carry one config.
        verified2 = run("verify", envelope2, "--key", public, "--payload", payload2, check=False)
        self.assertEqual(verified2.returncode, 0, verified2.stderr)
        self.assertEqual(run("live-version", envelope2).stdout.strip(), "2")
        paired = run("pair", envelope, envelope2, "--key", public, check=False)
        self.assertEqual(paired.returncode, 0, paired.stderr)
        self.assertNotEqual(run("pair", envelope2, envelope, "--key", public, check=False).returncode, 0)
        self.assertNotEqual(run("pair", envelope, envelope2, "--key", committed, check=False).returncode, 0)
        self.assertNotEqual(run("pair", tampered, envelope2, "--key", public, check=False).returncode, 0)

    @unittest.skipUnless(shutil.which("openssl"), "openssl not installed")
    def test_pair_refuses_two_files_that_are_not_one_config(self):
        key = self.work / "key.pem"
        public = self.work / "public.hex"
        other_key = self.work / "other.pem"

        def signed(name, payload, with_key=key):
            path = self.work / f"{name}.payload.json"
            path.write_text(payload, encoding="utf-8")
            out = self.work / f"{name}.json"
            subprocess.run(["bash", str(SIGNER), "sign", str(with_key), str(path), str(out)], capture_output=True, check=True)
            return out

        def payload(schema, version=4, sites=None, off=None):
            data = {"schema": schema, "version": version, "expires": "2027-12-31", "sites": sites or {}}
            if off is not None:
                data["rungsOff"] = off
            return json.dumps(data, indent=2) + "\n"

        try:
            keygen = subprocess.run(["bash", str(SIGNER), "keygen", str(key)], capture_output=True, text=True, check=True)
            public.write_text(keygen.stdout, encoding="utf-8")
            subprocess.run(["bash", str(SIGNER), "keygen", str(other_key)], capture_output=True, text=True, check=True)
            second = signed("two", payload(2, sites={"vk": {"agent": "a"}, "x": {"agent": "b"}}, off=["V1", "X1"]))
            good = signed("one", payload(1, sites={"x": {"agent": "b"}}, off=["X1"]))
            bad = {
                "another version": signed("v5", payload(1, version=5, sites={"x": {"agent": "b"}}, off=["X1"])),
                "vk left in": signed("vk", payload(1, sites={"vk": {"agent": "a"}, "x": {"agent": "b"}}, off=["X1"])),
                "another site's data": signed("x", payload(1, sites={"x": {"agent": "c"}}, off=["X1"])),
                "a rung lost": signed("off", payload(1, sites={"x": {"agent": "b"}})),
                "another key": signed("key", payload(1, sites={"x": {"agent": "b"}}, off=["X1"]), other_key),
            }
        finally:
            key.unlink(missing_ok=True)
            other_key.unlink(missing_ok=True)
        self.assertEqual(run("pair", good, second, "--key", public, check=False).returncode, 0)
        for name, first in bad.items():
            with self.subTest(name):
                self.assertNotEqual(run("pair", first, second, "--key", public, check=False).returncode, 0)


@unittest.skipUnless(shutil.which("openssl") and shutil.which("git"), "openssl and git needed")
class WorkflowDryRunTest(unittest.TestCase):
    """The workflow's own step scripts, run in order against a temporary git remote with a
    throwaway key (its public half in a temporary copy of the recipes folder)."""

    STEPS = (
        "The config and the next version",
        "Compose and check the payloads (schema 1 and schema 2)",
        "Sign with the repository's recipe key",
        "Verify both envelopes with the committed public key",
        "Publish on the recipes branch (one new commit, never forced)",
    )

    def setUp(self):
        if not has_jsonschema():
            self.skipTest("jsonschema not installed")
        self.tmp = pathlib.Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp, True)
        self.origin = self.tmp / "origin.git"
        self.repo = self.tmp / "repo"
        self.bin = self.tmp / "bin"
        self.bin.mkdir()
        for name in ("python", "python3"):  # the runner's setup-python: this interpreter (and its packages)
            shim = self.bin / name
            shim.write_text(f"#!/bin/sh\nexec {shlex.quote(sys.executable)} \"$@\"\n", encoding="utf-8")
            shim.chmod(0o755)
        self.git_env = {
            **{k: v for k, v in os.environ.items() if not k.startswith("GIT_")},
            "HOME": str(self.tmp), "GIT_CONFIG_NOSYSTEM": "1", "GIT_TERMINAL_PROMPT": "0",
            "PATH": f"{self.bin}{os.pathsep}{os.environ.get('PATH', '')}",
        }
        self.git("init", "--bare", "-q", str(self.origin), cwd=self.tmp)
        self.repo.mkdir()
        self.git("init", "-q", cwd=self.repo)
        self.git("remote", "add", "origin", self.origin.as_uri(), cwd=self.repo)
        (self.repo / "scripts").mkdir()
        shutil.copy2(HELPER, self.repo / "scripts" / HELPER.name)
        shutil.copy2(SIGNER, self.repo / "scripts" / SIGNER.name)
        shutil.copytree(RECIPES, self.repo / "extractor-master" / "recipes")
        key = self.tmp / "key.pem"
        keygen = subprocess.run(["bash", str(SIGNER), "keygen", str(key)], capture_output=True, text=True, check=True)
        self.public = self.repo / "extractor-master" / "recipes" / "master-recipes.pub.hex"
        self.public.write_text(keygen.stdout, encoding="utf-8")
        self.secret = key.read_text(encoding="utf-8")
        key.unlink()

    def git(self, *args, cwd=None):
        return subprocess.run(["git", *args], cwd=cwd or self.repo, env=self.git_env, capture_output=True, text=True, check=True).stdout

    def step(self, name):
        block = re.search(rf"^      - name: {re.escape(name)}\n(?:        .*\n)*?        run: \|\n((?:          .*\n|\n)+)", WORKFLOW, re.M)
        self.assertIsNotNone(block, name)
        return "".join(line[10:] if line.strip() else "\n" for line in block.group(1).splitlines(True))

    def publish(self, config, label=True):
        runner = self.tmp / f"runner-{config}"
        shutil.rmtree(runner, True)
        runner.mkdir()
        output = runner / "output"
        output.touch()
        summary = runner / "summary.md"
        env = {
            **self.git_env, "RUNNER_TEMP": str(runner), "GITHUB_OUTPUT": str(output), "GITHUB_STEP_SUMMARY": str(summary),
            "GITHUB_REPOSITORY": REPOSITORY, "CONFIG_NAME": f"recipes:{config}" if label else config,
            "SOURCE_SHA": "0123456789abcdef0123", "YFT_RECIPE_KEY": self.secret,
        }
        for name in self.STEPS:
            plan = dict(line.split("=", 1) for line in output.read_text(encoding="utf-8").splitlines() if "=" in line)
            env.update({
                "CONFIG": plan.get("config", ""), "VERSION": plan.get("version", ""), "EXPIRES": plan.get("expires", ""),
                "NAME": plan.get("name", ""), "PARENT": plan.get("parent", ""),
            })
            script = runner / "step.sh"
            script.write_text(self.step(name), encoding="utf-8")
            result = subprocess.run(
                ["bash", "--noprofile", "--norc", "-eo", "pipefail", str(script)],
                cwd=self.repo, env=env, capture_output=True, text=True,
            )
            self.assertEqual(result.returncode, 0, f"{name}: {result.stderr}")
            self.assertNotIn("BEGIN EC PRIVATE KEY", result.stdout + result.stderr)
        self.assertFalse((runner / "recipe-key.pem").exists(), "the key file is shredded")
        return runner

    def published(self, path):
        return self.git("show", f"refs/remotes/origin/recipes:{path}") if path else None

    def fetch(self):
        self.git("fetch", "-q", "origin", "+refs/heads/recipes:refs/remotes/origin/recipes")

    def seed(self, files):
        """A recipes branch as v1.0.0's workflow left it (only master-recipes.json)."""
        index = self.tmp / "seed.index"
        env = {**self.git_env, "GIT_INDEX_FILE": str(index)}
        for path, text in files.items():
            blob = subprocess.run(["git", "hash-object", "-w", "--stdin"], cwd=self.repo, env=env, input=text,
                                  capture_output=True, text=True, check=True).stdout.strip()
            subprocess.run(["git", "update-index", "--add", "--cacheinfo", f"100644,{blob},{path}"], cwd=self.repo, env=env, check=True)
        tree = subprocess.run(["git", "write-tree"], cwd=self.repo, env=env, capture_output=True, text=True, check=True).stdout.strip()
        commit_env = {**env, "GIT_AUTHOR_NAME": "seed", "GIT_AUTHOR_EMAIL": "seed@example.invalid",
                      "GIT_COMMITTER_NAME": "seed", "GIT_COMMITTER_EMAIL": "seed@example.invalid"}
        commit = subprocess.run(["git", "commit-tree", tree, "-m", "seed"], cwd=self.repo, env=commit_env,
                                capture_output=True, text=True, check=True).stdout.strip()
        self.git("push", "-q", "origin", f"{commit}:refs/heads/recipes")

    def check_pair(self, version):
        self.fetch()
        first, second = self.tmp / "first.json", self.tmp / "second.json"
        first.write_text(self.published("master-recipes.json"), encoding="utf-8")
        second.write_text(self.published("master-recipes-2.json"), encoding="utf-8")
        for path in (first, second):
            self.assertEqual(run("verify", path, "--key", self.public, check=False).returncode, 0)
            self.assertEqual(run("live-version", path).stdout.strip(), str(version))
        paired = run("pair", first, second, "--key", self.public, check=False)
        self.assertEqual(paired.returncode, 0, paired.stderr)
        return json.loads(json.loads(first.read_text())["payload"]), json.loads(json.loads(second.read_text())["payload"])

    def test_the_first_run_after_v1_0_0_publishes_both_files_with_the_next_version(self):
        old = {"alg": "ES256", "payload": json.dumps({"schema": 1, "version": 5}), "signature": "00"}
        self.seed({"master-recipes.json": json.dumps(old) + "\n", "history.tsv": "version\tconfig\tsource\tpublished\texpires\n"})
        runner = self.publish("drill-rung-off")
        one, two = self.check_pair(6)
        self.assertEqual((one["schema"], two["schema"]), (1, 2))
        self.assertEqual(one["rungsOff"], ["probe"])
        files = set(self.git("ls-tree", "-r", "--name-only", "refs/remotes/origin/recipes").split())
        self.assertEqual(files, {
            "history.tsv", "master-recipes.json", "master-recipes-2.json",
            "published/v6-drill-rung-off.json", "published/v6-drill-rung-off.schema2.json",
        })
        self.assertEqual(self.published("published/v6-drill-rung-off.json"), self.published("master-recipes.json"))
        self.assertEqual(self.published("published/v6-drill-rung-off.schema2.json"), self.published("master-recipes-2.json"))
        history = self.published("history.tsv").splitlines()
        self.assertEqual(len(history), 2)
        self.assertTrue(history[1].startswith("6\tdrill-rung-off\t0123456789ab\t"))
        summary = (runner / "summary.md").read_text(encoding="utf-8")
        self.assertIn("recipes/master-recipes.json", summary)
        self.assertIn("recipes/master-recipes-2.json", summary)

        # The next run: one version for both, the higher live one plus one, a new commit on top.
        before = self.git("rev-parse", "refs/remotes/origin/recipes").strip()
        self.publish("defaults", label=False)
        one, two = self.check_pair(7)
        self.assertNotIn("rungsOff", one)
        self.assertEqual(self.git("rev-parse", "refs/remotes/origin/recipes~1").strip(), before)

    def test_the_next_version_follows_the_higher_of_the_two_live_files(self):
        def envelope(schema, version):
            return json.dumps({"alg": "ES256", "payload": json.dumps({"schema": schema, "version": version}), "signature": "00"}) + "\n"

        self.seed({"master-recipes.json": envelope(1, 5), "master-recipes-2.json": envelope(2, 7)})
        self.publish("defaults")
        self.check_pair(8)

    def test_an_empty_remote_starts_both_files_at_version_1(self):
        self.publish("drill-key-change")
        one, two = self.check_pair(1)
        self.assertEqual(one["sites"], two["sites"])
        self.assertEqual(self.git("rev-list", "--count", "refs/remotes/origin/recipes").strip(), "1")


if __name__ == "__main__":
    unittest.main()
