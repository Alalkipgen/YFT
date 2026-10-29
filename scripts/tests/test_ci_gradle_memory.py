"""v1.0.2 item 0 (the owner, 2026-10-29): the CI runner's Gradle memory, from one place.

gradle.properties keeps the agents' 4 GiB sizes; on CI every workflow that runs Gradle runs
scripts/ci-gradle-memory.sh right after its setup-gradle step. The script writes the two values
below to the runner's Gradle user home gradle.properties (its values win over the project's) and
changes nothing else; checkpoint validation notes each Gradle and Kotlin daemon the job starts and
reports its memory flags, so a run's annotations show whether the values reached the build.
"""

import os
import pathlib
import re
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "ci-gradle-memory.sh"
WORKFLOWS = ROOT / ".github" / "workflows"
GRADLE_JVMARGS = "-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8"
KOTLIN_DAEMON_JVMARGS = "-Xmx2g -XX:MaxMetaspaceSize=512m"
WRITTEN = f"org.gradle.jvmargs={GRADLE_JVMARGS}\nkotlin.daemon.jvmargs={KOTLIN_DAEMON_JVMARGS}\n"
RUN = "bash scripts/ci-gradle-memory.sh"
SETUP_GRADLE = re.compile(r"^(\s*)- uses: gradle/actions/setup-gradle@", re.MULTILINE)
# Every use the owner named: the four a push to a work branch starts and the three it does not
# (release-draft.yml, phase0-validation.yml, master-optin-debug-apk.yml's two jobs).
USES = {
    "checkpoint-validation.yml": 1,
    "emulator-smoke.yml": 1,
    "preview-apk.yml": 1,
    "minified-check.yml": 1,
    "release-draft.yml": 1,
    "phase0-validation.yml": 1,
    "master-optin-debug-apk.yml": 2,
}

GRADLE_CLIENT = ["java", "-Xmx64m", "-Xms64m", "-classpath", "gradle-wrapper.jar",
                 "org.gradle.wrapper.GradleWrapperMain", "--no-daemon", "testDebugUnitTest"]
GRADLE_DAEMON = ["/usr/lib/jvm/temurin-17/bin/java", "-XX:MaxMetaspaceSize=1g",
                 "-XX:+HeapDumpOnOutOfMemoryError", "-Xmx4g", "-Dfile.encoding=UTF-8",
                 "-Duser.country=US", "-cp", "gradle-daemon-main-8.9.jar",
                 "org.gradle.launcher.daemon.bootstrap.GradleDaemon", "8.9"]
KOTLIN_DAEMON = ["java", "-cp", "kotlin-compiler-embeddable.jar", "-Djava.awt.headless=true",
                 "-Xmx2048m", "-XX:MaxMetaspaceSize=512m", "-XX:ReservedCodeCacheSize=320m",
                 "org.jetbrains.kotlin.daemon.KotlinCompileDaemon", "--daemon-runFilesPath", "x"]
TEST_WORKER = ["java", "-Xmx512m", "-cp", "gradle-worker.jar",
               "worker.org.gradle.process.internal.worker.GradleWorkerMain", "'Gradle Test Executor 1'"]


def workflow(name):
    return (WORKFLOWS / name).read_text(encoding="utf-8")


def steps_after_setup_gradle(source):
    """The step that follows each setup-gradle step, as text."""
    lines = source.splitlines()
    found = []
    for index, line in enumerate(lines):
        match = SETUP_GRADLE.match(line)
        if not match:
            continue
        start = f"{match.group(1)}- "
        following = [i for i in range(index + 1, len(lines)) if lines[i].startswith(start)]
        if not following:
            found.append("")
            continue
        begin = following[0]
        end = next(
            (i for i in range(begin + 1, len(lines))
             if lines[i].startswith(start) or (lines[i].strip() and not lines[i].startswith(match.group(1) + " "))),
            len(lines),
        )
        found.append("\n".join(lines[begin:end]))
    return found


def step(source, name):
    """A step by its name, as text (up to the next step)."""
    lines = source.splitlines()
    for index, line in enumerate(lines):
        if line.strip() == f"- name: {name}":
            indent = line[: line.index("-")]
            end = next((i for i in range(index + 1, len(lines)) if lines[i].startswith(indent + "- ")), len(lines))
            return "\n".join(lines[index:end])
    raise AssertionError(f"no step {name!r}")


class Script:
    def __init__(self, test):
        temporary = tempfile.TemporaryDirectory()
        test.addCleanup(temporary.cleanup)
        self.dir = pathlib.Path(temporary.name)
        self.home = self.dir / "home"
        self.home.mkdir()

    def env(self, **extra):
        env = {key: value for key, value in os.environ.items() if key != "GRADLE_USER_HOME"}
        env["HOME"] = str(self.home)
        env.update(extra)
        return env

    def run(self, *args, **extra):
        return subprocess.run(["bash", str(SCRIPT), *args], capture_output=True, text=True,
                              env=self.env(**extra), check=False, timeout=60)

    def proc(self, *processes):
        proc = self.dir / "proc"
        for pid, args in processes:
            (proc / str(pid)).mkdir(parents=True)
            (proc / str(pid) / "cmdline").write_bytes(b"\0".join(arg.encode() for arg in args) + b"\0")
        (proc / "self").mkdir(parents=True, exist_ok=True)
        return proc

    def watch(self, proc, polls=1):
        log = self.dir / "daemons.tsv"
        result = self.run("--watch", str(log), CI_GRADLE_MEMORY_PROC=str(proc), CI_GRADLE_MEMORY_POLLS=str(polls))
        return result, log


class WriteTest(unittest.TestCase):
    def test_it_writes_the_two_values_to_the_gradle_user_home(self):
        script = Script(self)
        user_home = script.dir / "gradle-home"
        result = script.run(GRADLE_USER_HOME=str(user_home))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(WRITTEN, (user_home / "gradle.properties").read_text())
        self.assertIn("::notice title=CI Gradle memory::", result.stdout)
        self.assertIn(f"org.gradle.jvmargs={GRADLE_JVMARGS}", result.stdout)
        self.assertIn(f"kotlin.daemon.jvmargs={KOTLIN_DAEMON_JVMARGS}", result.stdout)

    def test_without_gradle_user_home_it_is_the_runners_home_dot_gradle(self):
        script = Script(self)
        result = script.run()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(WRITTEN, (script.home / ".gradle" / "gradle.properties").read_text())

    def test_it_keeps_every_other_line_and_replaces_an_older_value_once(self):
        script = Script(self)
        properties = script.home / ".gradle" / "gradle.properties"
        properties.parent.mkdir()
        properties.write_text(
            "# written by another step\n"
            "org.gradle.caching=true\n"
            "org.gradle.jvmargs=-Xmx512m \\\n"
            "    -Dold.value=1\n"
            "kotlin.daemon.jvmargs : -Xmx256m\n"
            "org.gradle.jvmargsextra=kept\n"
            "org.gradle.workers.max=2"
        )
        for _ in range(2):
            result = script.run()
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(
                "# written by another step\n"
                "org.gradle.caching=true\n"
                "org.gradle.jvmargsextra=kept\n"
                "org.gradle.workers.max=2\n" + WRITTEN,
                properties.read_text(),
            )

    def test_the_values_are_the_owners_and_only_the_script_has_them(self):
        source = SCRIPT.read_text(encoding="utf-8")
        self.assertIn(f'GRADLE_JVMARGS="{GRADLE_JVMARGS}"', source)
        self.assertIn(f'KOTLIN_DAEMON_JVMARGS="{KOTLIN_DAEMON_JVMARGS}"', source)
        for path in sorted(WORKFLOWS.glob("*.yml")):
            with self.subTest(workflow=path.name):
                text = path.read_text(encoding="utf-8")
                self.assertNotIn("jvmargs", text)
                self.assertNotIn("GRADLE_OPTS", text)
                self.assertNotIn("-Xmx", text)

    def test_a_wrong_argument_is_refused(self):
        script = Script(self)
        for args in (("--watch",), ("--report",), ("--other",), ("extra",)):
            with self.subTest(args=args):
                self.assertEqual(2, script.run(*args).returncode)


class WorkflowTest(unittest.TestCase):
    def test_each_setup_gradle_is_followed_by_the_script(self):
        seen = {}
        for path in sorted(WORKFLOWS.glob("*.yml")):
            following = steps_after_setup_gradle(path.read_text(encoding="utf-8"))
            if following:
                seen[path.name] = len(following)
            for index, text in enumerate(following):
                with self.subTest(workflow=path.name, use=index):
                    self.assertIn("- name: CI Gradle memory", text)
                    self.assertIn(RUN, text)
        self.assertEqual(USES, seen)

    def test_every_workflow_that_runs_gradle_sets_it_up(self):
        runs_gradle = set()
        for path in sorted(WORKFLOWS.glob("*.yml")):
            text = path.read_text(encoding="utf-8")
            called = [ROOT / script for script in set(re.findall(r"scripts/[\w.-]+\.sh", text))]
            if re.search(r"\./gradlew|\bgradle -p\b", text) or any(
                "./gradlew" in script.read_text(encoding="utf-8") for script in called if script.exists()
            ):
                runs_gradle.add(path.name)
        self.assertEqual(set(USES), runs_gradle)

    def test_a_workflow_with_a_paths_filter_starts_when_the_script_changes(self):
        for name in ("emulator-smoke.yml", "preview-apk.yml", "minified-check.yml", "phase0-validation.yml"):
            with self.subTest(workflow=name):
                text = workflow(name)
                self.assertEqual(text.count("    paths:\n"), text.count('      - "scripts/ci-gradle-memory.sh"\n'))

    def test_an_older_label_build_gets_the_script_from_the_workflow_commit(self):
        text = workflow("master-optin-debug-apk.yml")
        label, d7 = steps_after_setup_gradle(text)
        self.assertIn("if [[ -f scripts/ci-gradle-memory.sh ]]; then", label)
        self.assertIn("CI_MEMORY_SHA: ${{ github.workflow_sha }}", label)
        self.assertIn('git fetch --no-tags --depth=1 origin "$CI_MEMORY_SHA"', label)
        self.assertIn('git show "$CI_MEMORY_SHA:scripts/ci-gradle-memory.sh"', label)
        self.assertIn('bash "$RUNNER_TEMP/ci-gradle-memory.sh"', label)
        self.assertNotIn("::warning", label)
        self.assertNotIn("if [[", d7)
        for name, count in USES.items():
            if name != "master-optin-debug-apk.yml":
                with self.subTest(workflow=name):
                    self.assertEqual(count, workflow(name).count(f"        run: {RUN}\n"))

    def test_checkpoint_validation_notes_the_daemons_and_reports_them_last(self):
        text = workflow("checkpoint-validation.yml")
        watch = step(text, "Note the Gradle and Kotlin daemons' memory (background)")
        self.assertIn(f'nohup {RUN} --watch "$RUNNER_TEMP/gradle-daemons.tsv"', watch)
        self.assertIn(">/dev/null 2>&1 </dev/null &", watch)
        self.assertLess(text.index(watch), text.index("- name: Validate production checkpoint"))
        report = step(text, "The Gradle and Kotlin daemons' memory")
        self.assertIn("if: always()", report)
        self.assertIn(f'{RUN} --report "$RUNNER_TEMP/gradle-daemons.tsv"', report)
        self.assertTrue(text.rstrip("\n").endswith(report.rstrip("\n")))


class WatchAndReportTest(unittest.TestCase):
    def test_each_daemon_is_noted_once_with_its_memory_flags(self):
        script = Script(self)
        proc = script.proc((101, GRADLE_CLIENT), (202, GRADLE_DAEMON), (303, KOTLIN_DAEMON), (404, TEST_WORKER))
        result, log = script.watch(proc, polls=2)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(
            "Gradle daemon\t202\t-XX:MaxMetaspaceSize=1g -Xmx4g -Dfile.encoding=UTF-8\n"
            "Kotlin daemon\t303\t-Xmx2048m -XX:MaxMetaspaceSize=512m\n",
            log.read_text(),
        )
        report = script.run("--report", str(log))
        self.assertEqual(0, report.returncode, report.stdout)
        notices = [line for line in report.stdout.splitlines() if line.startswith("::notice")]
        self.assertEqual(2, len(notices), report.stdout)
        self.assertIn("Gradle daemon 202 ran with -XX:MaxMetaspaceSize=1g -Xmx4g -Dfile.encoding=UTF-8", notices[0])
        self.assertIn("Kotlin daemon 303 ran with -Xmx2048m -XX:MaxMetaspaceSize=512m", notices[1])
        self.assertNotIn("::error", report.stdout)
        self.assertNotIn("::warning", report.stdout)

    def test_a_daemon_with_the_repositorys_sizes_fails_the_report(self):
        script = Script(self)
        repository = [arg.replace("-Xmx4g", "-Xmx1536m").replace("=1g", "=512m") for arg in GRADLE_DAEMON]
        kotlin = [arg.replace("-Xmx2048m", "-Xmx768m") for arg in KOTLIN_DAEMON]
        result, log = script.watch(script.proc((7, repository), (8, kotlin)))
        self.assertEqual(0, result.returncode, result.stderr)
        report = script.run("--report", str(log))
        self.assertEqual(1, report.returncode, report.stdout)
        errors = [line for line in report.stdout.splitlines() if line.startswith("::error")]
        self.assertEqual(2, len(errors), report.stdout)
        self.assertIn("-Xmx1536m instead of -Xmx4g, -XX:MaxMetaspaceSize=512m instead of -XX:MaxMetaspaceSize=1g", errors[0])
        self.assertIn("-Xmx768m instead of -Xmx2g", errors[1])

    def test_the_last_flag_counts_and_a_missing_one_differs(self):
        script = Script(self)
        later = GRADLE_DAEMON[:1] + ["-Xmx4g"] + GRADLE_DAEMON[1:4] + ["-Xmx1g"] + GRADLE_DAEMON[4:]
        bare = ["java", "org.jetbrains.kotlin.daemon.KotlinCompileDaemon"]
        result, log = script.watch(script.proc((11, later), (12, bare)))
        report = script.run("--report", str(log))
        self.assertEqual(1, report.returncode, report.stdout)
        self.assertIn("-Xmx1g instead of -Xmx4g", report.stdout)
        self.assertIn("-Xmx(none) instead of -Xmx2g", report.stdout)

    def test_nothing_noted_is_a_warning_not_a_failure(self):
        script = Script(self)
        result, log = script.watch(script.proc((5, GRADLE_CLIENT)))
        self.assertEqual("", log.read_text())
        report = script.run("--report", str(log))
        self.assertEqual(0, report.returncode, report.stdout)
        self.assertEqual(2, report.stdout.count("::warning"), report.stdout)
        missing = script.run("--report", str(script.dir / "never-written.tsv"))
        self.assertEqual(0, missing.returncode, missing.stdout)


if __name__ == "__main__":
    unittest.main()
