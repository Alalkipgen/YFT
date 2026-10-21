"""N6 plan adapted (owner 2026-10-13): manual spike runs are labels on draft PR #2.

GitHub shows "Run workflow" only for workflows on the default branch, so the owner starts the
canary with the label run-canary and the opt-in APK with its ladder on (ownSolverNext) with the
label build-optin-apk (MASTER_KEY_PHASE1_2_PLAN.md §3 N6). The workflows' expressions are
evaluated for every event they take: a push, a manual run, each label and any other label.

Phase 1.3 D7 (owner 2026-10-15): the opt-in APK workflow's second job, d7-matrix, builds the phone
check's debug APKs (D5c's h-only the seventh; routeb and fb-trial since: nine) after the build job, only for a push whose commit
message carries [d7].
"""

import pathlib
import re
import unittest

try:
    import yaml
except ImportError:  # optional: the expression checks below need only the workflow text
    yaml = None

ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOWS = ROOT / ".github" / "workflows"
CANARY = (WORKFLOWS / "own-solver-canary.yml").read_text(encoding="utf-8")
APK = (WORKFLOWS / "master-optin-debug-apk.yml").read_text(encoding="utf-8")
CHECKPOINT = (WORKFLOWS / "checkpoint-validation.yml").read_text(encoding="utf-8")
SPIKE = "refs/heads/spike/master-extractor-backup"
TOKEN = re.compile(r"\s*(\|\||&&|==|!=|!|\(|\)|,|'(?:[^']|'')*'|[A-Za-z_][\w.-]*)")


def truthy(value):
    return not (value is None or value is False or value == "" or value == 0)


def text(value):
    return "" if value is None else str(value).lower() if isinstance(value, bool) else str(value)


class Expression:
    """The GitHub Actions expression subset these workflows use: string, boolean and null
    literals, context paths, !, ==, !=, &&, || (values, not booleans), parentheses, format() and
    contains() (a string: any case; an array: an equal item)."""

    def __init__(self, source, context):
        source = source.strip()
        if source.startswith("${{") and source.endswith("}}"):
            source = source[3:-2]
        self.tokens, position = [], 0
        while source[position:].strip():
            match = TOKEN.match(source, position)
            if not match:
                raise AssertionError(f"unsupported expression: {source[position:]!r}")
            self.tokens.append(match.group(1))
            position = match.end()
        self.index, self.context = 0, context

    def peek(self):
        return self.tokens[self.index] if self.index < len(self.tokens) else None

    def take(self, expected=None):
        token = self.peek()
        if token is None or expected not in (None, token):
            raise AssertionError(f"expected {expected!r}, got {token!r}")
        self.index += 1
        return token

    def value(self):
        result = self.either()
        if self.peek() is not None:
            raise AssertionError(f"unexpected {self.peek()!r}")
        return result

    def either(self):
        result = self.both()
        while self.peek() == "||":
            self.take()
            right = self.both()
            result = result if truthy(result) else right
        return result

    def both(self):
        result = self.compare()
        while self.peek() == "&&":
            self.take()
            right = self.compare()
            result = right if truthy(result) else result
        return result

    def compare(self):
        left = self.unary()
        if self.peek() not in ("==", "!="):
            return left
        equal_operator = self.take() == "=="
        right = self.unary()
        if isinstance(left, str) and isinstance(right, str):
            equal = left.casefold() == right.casefold()
        else:
            equal = left == right
        return equal == equal_operator

    def unary(self):
        if self.peek() == "!":
            self.take()
            return not truthy(self.unary())
        return self.primary()

    def primary(self):
        token = self.take()
        if token == "(":
            result = self.either()
            self.take(")")
            return result
        if token.startswith("'"):
            return token[1:-1].replace("''", "'")
        if token in ("true", "false", "null"):
            return {"true": True, "false": False, "null": None}[token]
        if token in ("format", "contains"):
            self.take("(")
            arguments = [self.either()]
            while self.peek() == ",":
                self.take()
                arguments.append(self.either())
            self.take(")")
            if token == "contains":
                search, item = arguments
                if isinstance(search, list):
                    return any(text(entry).lower() == text(item).lower() for entry in search)
                return text(item).lower() in text(search).lower()
            return re.sub(r"\{(\d+)\}", lambda m: text(arguments[1 + int(m.group(1))]), arguments[0])
        result = self.context
        for part in token.split("."):
            result = result.get(part) if isinstance(result, dict) else None
        return result


def evaluate(source, context):
    return Expression(source, context).value()


def render(template, context):
    """A YAML value with ${{ }} parts, as the runner turns it into a string."""
    return re.sub(r"\$\{\{(.*?)\}\}", lambda m: text(evaluate(m.group(1), context)), template)


def push(message=None):
    event = {} if message is None else {"head_commit": {"message": message}}
    return {"github": {"event_name": "push", "ref": SPIKE, "sha": "pushed-commit", "event": event}}


def manual(own_solver=True, own_solver_next=False):
    context = {"github": {"event_name": "workflow_dispatch", "ref": SPIKE, "sha": "branch-commit"}}
    context["inputs"] = {"own_solver": own_solver, "own_solver_next": own_solver_next}
    return context


def label(name):
    event = {"action": "labeled", "label": {"name": name}}
    event["pull_request"] = {"number": 2, "head": {"sha": "spike-head-commit"}}
    github = {"event_name": "pull_request", "ref": "refs/pull/2/merge", "sha": "merge-commit"}
    return {"github": {**github, "event": event}}


EVENTS = {
    "push": push(),
    "manual": manual(),
    "run-canary": label("run-canary"),
    "build-optin-apk": label("build-optin-apk"),
    "another label": label("documentation"),
}


def one(pattern, source):
    found = re.findall(pattern, source, re.M)
    if len(found) != 1:
        raise AssertionError(f"{len(found)} matches for {pattern!r}")
    return found[0]


def on_block(source):
    return one(r"^on:\n((?:[ #].*\n|\n)+)", source)


def trigger(source, name):
    """One trigger's lines in the on: block (up to the next trigger)."""
    return one(rf"^  {name}:\n((?:    .*\n|  #.*\n)*)", on_block(source))


def job(source, name):
    """One job's lines in the jobs: block (up to the next job or a 2-space comment)."""
    return one(rf"^  {name}:\n((?:    .*\n|\n)+)", source)


BUILD = job(APK, "build")
D7 = job(APK, "d7-matrix")


def job_if(source):
    return one(r"^    if: (.+)$", source)


def checkout_ref(source):
    return one(r"- uses: actions/checkout@v5\n        with:\n(?: +#.*\n)*          ref: (.+)$", source)


def group(source):
    return one(r"^concurrency:\n(?:  #.*\n)*  group: (.+)$", source)


class LabelTriggerTest(unittest.TestCase):
    def assert_label_trigger(self, source):
        pull_request = trigger(source, "pull_request")
        self.assertEqual("    types: [labeled]\n", pull_request)  # no paths or branches filter
        self.assertIn("  workflow_dispatch:\n", on_block(source))
        self.assertIn('    branches: ["spike/master-extractor-backup"]\n', trigger(source, "push"))

    def assert_runs(self, source, expected):
        for name, context in EVENTS.items():
            with self.subTest(event=name):
                self.assertEqual(expected[name], truthy(evaluate(job_if(source), context)))

    def assert_pr_head_checkout(self, source):
        ref = checkout_ref(source)
        self.assertEqual("spike-head-commit", render(ref, label("run-canary")))
        self.assertEqual("pushed-commit", render(ref, push()))
        self.assertEqual("branch-commit", render(ref, manual()))

    def assert_label_groups(self, source, prefix):
        self.assertEqual(prefix + SPIKE, render(group(source), push()))  # pushes keep their group
        groups = {render(group(source), EVENTS[name]) for name in EVENTS if name != "manual"}
        self.assertEqual(4, len(groups))  # no label run shares a group with another label's run
        self.assertIn("  cancel-in-progress: true\n", source)


class CanaryLabelTest(LabelTriggerTest):
    def test_run_canary_label_starts_the_canary_without_a_paths_filter(self):
        self.assert_label_trigger(CANARY)
        self.assertIn('      - "scripts/own-solver/**"\n', trigger(CANARY, "push"))

    def test_the_job_runs_for_a_push_a_manual_run_and_run_canary_only(self):
        expected = {"push": True, "manual": True, "run-canary": True}
        self.assert_runs(CANARY, {**dict.fromkeys(EVENTS, False), **expected})

    def test_a_label_run_tests_the_spike_head_not_its_merge_with_main(self):
        self.assert_pr_head_checkout(CANARY)

    def test_another_label_never_cancels_a_running_canary(self):
        self.assert_label_groups(CANARY, "own-solver-canary-")


class OptInApkLabelTest(LabelTriggerTest):
    def flags(self, context):
        steps = [
            (render(own, context), render(next_, context))
            for own, next_ in zip(
                re.findall(r"^          OWN_SOLVER: (.+)$", BUILD, re.M),
                re.findall(r"^          OWN_SOLVER_NEXT: (.+)$", BUILD, re.M),
            )
        ]
        self.assertEqual(2, len(steps))  # the build step and the summary step
        self.assertEqual(steps[0], steps[1])
        return steps[0]

    def build_context(self, context):
        """The context after the "Build commit" step, which keeps BUILD_SHA's first 7 characters."""
        self.assertEqual(
            'run: echo "short=${BUILD_SHA::7}" >> "$GITHUB_OUTPUT"',
            one(r"^        id: commit\n        shell: bash\n        (run: .+)$", BUILD),
        )
        sha = render(one(r"^      BUILD_SHA: (.+)$", BUILD), context)
        return {**context, "steps": {"commit": {"outputs": {"short": sha[:7]}}}}, sha

    def test_build_optin_apk_label_starts_the_build_without_a_paths_filter(self):
        self.assert_label_trigger(APK)
        self.assertIn('      - "docs/**"\n', trigger(APK, "push"))

    def test_the_job_runs_for_a_push_a_manual_run_and_build_optin_apk_only(self):
        expected = {"push": True, "manual": True, "build-optin-apk": True}
        self.assert_runs(BUILD, {**dict.fromkeys(EVENTS, False), **expected})

    def test_a_label_build_has_the_own_solver_and_its_ladder_on(self):
        self.assertEqual(("true", "true"), self.flags(label("build-optin-apk")))
        self.assertEqual(("true", "false"), self.flags(push()))  # pushes stay as they were
        self.assertEqual(("true", "false"), self.flags(manual()))
        self.assertEqual(("false", "true"), self.flags(manual(False, True)))

    def test_a_label_build_is_the_spike_head_and_its_artifact_name_shows_the_ladder(self):
        self.assert_pr_head_checkout(BUILD)
        artifact = one(r"^        uses: actions/upload-artifact@v4\n        with:\n          name: (.+)$", BUILD)
        context, sha = self.build_context(label("build-optin-apk"))
        self.assertEqual("spike-head-commit", sha)
        self.assertEqual("yft-master-optin-debug-apk-next-spike-h", render(artifact, context))
        for unchanged in (push(), manual(), manual(False, True)):
            context, _ = self.build_context(unchanged)
            self.assertEqual("yft-master-optin-debug-apk", render(artifact, context))
        self.assertIn('echo "Commit: ${BUILD_SHA}"', APK)
        self.assertNotIn("GITHUB_SHA", APK)

    def test_the_toolkit_drift_check_is_unchanged(self):
        self.assertIn('git fetch --no-tags --depth=1 origin "$base" +refs/heads/main:refs/remotes/origin/main', APK)
        self.assertIn('python3 scripts/master-toolkit-drift.py --ref "$base"\n', APK)
        self.assertIn("python3 scripts/master-toolkit-drift.py --ref origin/main --warn-only\n", APK)

    def test_another_label_never_cancels_a_running_build(self):
        self.assert_label_groups(APK, "master-optin-debug-apk-")


class D7MatrixTest(unittest.TestCase):
    """Phase 1.3 D7 (owner 2026-10-15): the phone-check APKs, one per mode, all flags on (D5c: h-only;
    then routeb, route B's trial, and fb-trial, the social ladder's trial: nine)."""

    MODES = {
        "normal": ("", ""),
        "skip-visionos": ("visionos", ""),
        "skip-visionos-adaptive": ("visionos-adaptive", ""),
        "mutate-L1": ("", "L1"),
        "mutate-L3": ("", "L3"),
        "mutate-L5": ("", "L5"),
        "h-only": ("h-only", ""),
        "routeb": ("", ""),
        "fb-trial": ("", ""),
    }

    def test_the_seven_apks_build_only_after_a_push_whose_message_carries_d7(self):
        self.assertEqual("build", one(r"^    needs: (.+)$", D7))  # after the build job's tests
        for name, context in EVENTS.items():
            with self.subTest(event=name):  # a push without [d7], a manual run, any label
                self.assertFalse(truthy(evaluate(job_if(D7), context)))
        self.assertTrue(truthy(evaluate(job_if(D7), push("Phase 1.3 D7: six APKs [d7]"))))
        self.assertTrue(truthy(evaluate(job_if(D7), push("run 2 [D7]"))))  # contains(): any case
        self.assertFalse(truthy(evaluate(job_if(D7), push("Phase 1.3 D7 docs"))))
        self.assertTrue(truthy(evaluate(job_if(BUILD), push("run 2 [d7]"))))  # build runs as before

    def test_each_mode_has_its_switch_and_its_own_14_day_artifact(self):
        found = re.findall(r"^          - mode: (.+)\n            skip: (.*)\n            mutate: (.*)$", D7, re.M)
        self.assertEqual(self.MODES, {mode: (skip.strip('"'), mutate.strip('"')) for mode, skip, mutate in found})
        self.assertEqual(9, len(found))
        self.assertIn("          D7_SKIP: ${{ matrix.skip }}\n          D7_MUTATE: ${{ matrix.mutate }}\n", D7)
        name = one(r"^        uses: actions/upload-artifact@v4\n        with:\n          name: (.+)$", D7)
        names = {render(name, {"matrix": {"mode": mode}}) for mode in self.MODES}
        self.assertEqual({"yft-d7-" + mode for mode in self.MODES}, names)
        self.assertIn("          retention-days: 14\n          if-no-files-found: error\n", D7)

    def test_it_only_assembles_with_every_flag_on(self):
        command = " ".join(one(r"(\./gradlew [^\n\\]*(?:\\\n[^\n\\]*)*)", D7).replace("\\\n", " ").split())
        for flag in ("masterCapture", "ownSolver", "ownSolverNext", "clientLadder"):
            self.assertIn(f" -Pyft.{flag}=true ", command)
        self.assertIn(' -Pyft.ladderSkip="$D7_SKIP" -Pyft.ladderMutate="$D7_MUTATE" ', command)
        self.assertEqual([":app:assembleDebug"], [word for word in command.split() if word.startswith(":")])

    def test_a_wrong_key_package_or_version_code_uploads_nothing(self):
        check = D7.index("      - name: Key, package and versionCode")
        upload = D7.index("      - name: Upload D7 APK")
        step = D7[check:upload]
        self.assertIn("test -f app/keystore/spike-debug.keystore.b64\n", D7[:check])  # the key is required
        self.assertIn('"$digest" != "$expected"', step)
        self.assertIn('"$package" != "com.alal.yft.debug"', step)
        self.assertIn("s/^yft.versionCode=//p", step)
        self.assertTrue(step.endswith('          test "$ok" -eq 1\n'))
        self.assertNotIn("always()", D7)
        self.assertNotIn("continue-on-error", D7)

    def test_the_sdk_install_tries_three_times_before_the_job_fails(self):
        # 0cf7b61's routeb job: a runner's SDK download failed; the other eight built.
        for name, source, steps in (("apk", APK, 2), ("checkpoint", CHECKPOINT, 1)):
            with self.subTest(workflow=name):
                parts = source.split("      - name: Install Android SDK 35, NDK and CMake\n")[1:]
                self.assertEqual(steps, len(parts))
                for part in parts:
                    step = part.split("      - uses: gradle/actions/setup-gradle@v4")[0]
                    self.assertIn("for try in 1 2 3; do\n", step)
                    self.assertIn('"ndk;27.3.13750724" "cmake;3.22.1"; then\n', step)
                    self.assertTrue(step.endswith("          exit 1\n"))

    def test_the_trigger_file_is_not_a_doc_the_push_ignores(self):
        ignored = re.findall(r'^      - "(.+)"$', trigger(APK, "push"), re.M)
        self.assertEqual(["docs/**", "**/*.md"], ignored)
        self.assertTrue((ROOT / ".github" / "d7-apks.txt").is_file())


class ExpressionTest(unittest.TestCase):
    def test_the_evaluator_follows_github_rules(self):
        context = {"github": {"event_name": "push"}}
        self.assertEqual("b", evaluate("false || 'b'", context))
        self.assertEqual(False, evaluate("false && 'b'", context))
        self.assertTrue(evaluate("github.event_name == 'PUSH'", context))  # strings: any case
        self.assertIsNone(evaluate("github.event.label.name", context))
        self.assertTrue(evaluate("!(github.event.label.name == 'run-canary')", context))
        self.assertEqual("x-1", evaluate("format('x-{0}', '1')", context))
        self.assertTrue(evaluate("contains('Phase 1.3 [D7] run', '[d7]')", context))  # any case
        self.assertFalse(evaluate("contains(github.event.head_commit.message, '[d7]')", context))


@unittest.skipUnless(yaml, "PyYAML is not installed")
class WorkflowYamlTest(unittest.TestCase):
    def test_both_workflows_parse_with_the_label_trigger(self):
        for name, source in (("canary", CANARY), ("apk", APK)):
            with self.subTest(workflow=name):
                document = yaml.safe_load(source)
                triggers = document.get("on", document.get(True))  # YAML 1.1 reads `on` as true
                self.assertEqual({"types": ["labeled"]}, triggers["pull_request"])
                self.assertEqual({"push", "workflow_dispatch", "pull_request"}, set(triggers))
                jobs = document["jobs"]
                self.assertEqual(["build", "d7-matrix"] if name == "apk" else list(jobs)[:1], list(jobs))
                self.assertIn("github.event.label.name", next(iter(jobs.values()))["if"])
        d7 = yaml.safe_load(APK)["jobs"]["d7-matrix"]
        self.assertEqual("build", d7["needs"])
        self.assertEqual(list(D7MatrixTest.MODES), [entry["mode"] for entry in d7["strategy"]["matrix"]["include"]])


if __name__ == "__main__":
    unittest.main()
