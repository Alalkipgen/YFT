"""Social plan S4 (MASTER_KEY_SOCIAL_PLAN.md section 7.3): the master-recipes-publish workflow.

The owner's label recipes:<name> on draft PR #2 (or a manual run, once on the default branch)
signs one committed config and publishes it on the branch "recipes". The job runs only for the
owner, a recipes: label and the repository's own pull request; the helper composes the payload
(the next version, the expiry date), checks it as the reader would and verifies the envelope.
Throwaway keys only (made per run in a temporary directory, never kept).
"""

import json
import pathlib
import re
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


class HelperTest(unittest.TestCase):
    def setUp(self):
        self.work = pathlib.Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.work, True)

    def compose(self, config, version=3, expires="2027-12-31", check=True):
        out = self.work / "payload.json"
        return run("compose", config, "--version", version, "--expires", expires, "--out", out, check=check), out

    def test_compose_adds_the_version_and_expiry_and_keeps_the_configs_data(self):
        for config in sorted((RECIPES / "configs").glob("*.json")):
            with self.subTest(config.name):
                result, out = self.compose(config)
                self.assertEqual(result.returncode, 0, result.stderr)
                text = out.read_text(encoding="utf-8")
                payload = json.loads(text)
                source = json.loads(config.read_text(encoding="utf-8"))
                self.assertEqual(list(payload)[:4], ["schema", "version", "expires", "sites"])
                self.assertEqual((payload["version"], payload["expires"]), (3, "2027-12-31"))
                self.assertEqual(payload["sites"], source["sites"])
                self.assertEqual(payload.get("rungsOff"), source.get("rungsOff"))
                self.assertTrue(text.endswith("}\n"))
                self.assertTrue(text.isascii())

    def test_compose_refuses_what_the_reader_would_reject(self):
        bad = {
            "version": {"schema": 1, "version": 9, "sites": {}},
            "unknown rung": {"schema": 1, "sites": {}, "rungsOff": ["F9"]},
            "a rung twice": {"schema": 1, "sites": {}, "rungsOff": ["F1", "F1"]},
            "unknown site": {"schema": 1, "sites": {"vine": {}}},
            "schema 2": {"schema": 2, "sites": {}},
            "too deep": {"schema": 1, "sites": {"x": {"a": {"b": {"c": {"d": {"e": {"f": {}}}}}}}}},
        }
        for name, config in bad.items():
            with self.subTest(name):
                path = self.work / "config.json"
                path.write_text(json.dumps(config), encoding="utf-8")
                result, out = self.compose(path, check=False)
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse(out.exists())
        result, _ = self.compose(RECIPES / "configs" / "defaults.json", expires="2027-02-30", check=False)
        self.assertNotEqual(result.returncode, 0)

    def test_every_committed_config_passes_the_schema(self):
        try:
            import jsonschema  # noqa: F401
        except ImportError:
            self.skipTest("jsonschema not installed")
        for config in sorted((RECIPES / "configs").glob("*.json")):
            with self.subTest(config.name):
                _, out = self.compose(config)
                result = run("check", out, "--schema", RECIPES / "master-recipes.schema.json", check=False)
                self.assertEqual(result.returncode, 0, result.stderr)

    @unittest.skipUnless(shutil.which("openssl"), "openssl not installed")
    def test_the_signed_envelope_verifies_with_its_key_only_and_the_live_version_reads(self):
        _, payload = self.compose(RECIPES / "configs" / "drill-rung-off.json", version=2)
        key = self.work / "key.pem"
        public = self.work / "public.hex"
        envelope = self.work / "master-recipes.json"
        try:
            keygen = subprocess.run(["bash", str(SIGNER), "keygen", str(key)], capture_output=True, text=True, check=True)
            public.write_text(keygen.stdout, encoding="utf-8")
            subprocess.run(["bash", str(SIGNER), "sign", str(key), str(payload), str(envelope)], capture_output=True, check=True)
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


if __name__ == "__main__":
    unittest.main()
