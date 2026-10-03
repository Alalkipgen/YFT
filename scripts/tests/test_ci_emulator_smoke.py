import os
import pathlib
import shutil
import subprocess
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]


class EmulatorCollectorTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = pathlib.Path(self.temporary.name)
        (self.root / "scripts").mkdir()
        for name in ["ci-emulator-smoke.sh", "ci-smoke-diagnostics.py"]:
            shutil.copyfile(ROOT / "scripts" / name, self.root / "scripts" / name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.executable(self.bin / "git", '#!/bin/sh\nprintf "%s\\n" "$SMOKE_FAKE_ROOT"\n')
        self.executable(
            self.root / "gradlew",
            '''#!/usr/bin/env python3
import os
import pathlib
import shutil
import sys

root = pathlib.Path.cwd()
directory = root / "device-smoke"
directory.mkdir()
for name in ["01-browser-empty", "02-browser-page", "03-found"]:
    (directory / (name + ".png")).write_bytes(b"collector fixture, not a real bitmap")
    (directory / (name + ".bounds.txt")).write_text(
        "browser-address bounds=[12,48][940,110]\\nWebView bounds=[0,120][1080,1800]\\n"
    )
report = root / "app/build/outputs/androidTest-results/connected"
report.mkdir(parents=True)
(report / "TEST-fixture.xml").write_text(
    '<testsuite tests="3" failures="0" errors="0" skipped="0"/>'
)
if "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true" not in sys.argv:
    # Match AGP's default APK uninstall, which removes app-owned external files.
    shutil.rmtree(directory)
sys.exit(int(os.environ.get("SMOKE_TEST_RESULT", "0")))
''',
        )
        self.executable(
            self.bin / "adb",
            '''#!/usr/bin/env python3
import os
import pathlib
import shutil
import sys

args = sys.argv[1:]
root = pathlib.Path(os.environ["SMOKE_FAKE_ROOT"])
if args[:2] == ["logcat", "-d"]:
    if os.environ.get("SMOKE_LOG_FAILURE"):
        sys.exit(7)
    print("YFTSmoke clean log")
elif args and args[0] == "pull":
    source = root / "device-smoke"
    if not source.is_dir() or os.environ.get("SMOKE_PULL_FAILURE"):
        print("Remote screenshot folder is unavailable", file=sys.stderr)
        sys.exit(1)
    shutil.copytree(source, pathlib.Path(args[-1]), dirs_exist_ok=True)
sys.exit(0)
''',
        )
        self.env = {
            **os.environ,
            "PATH": str(self.bin) + os.pathsep + os.environ["PATH"],
            "SMOKE_FAKE_ROOT": str(self.root),
            "RUNNER_TEMP": str(self.root / "runner-temp"),
            "PYTHONDONTWRITEBYTECODE": "1",
        }

    def executable(self, path, text):
        path.write_text(text)
        path.chmod(0o755)

    def collect(self, **env):
        return subprocess.run(
            ["bash", "scripts/ci-emulator-smoke.sh"],
            cwd=self.root,
            env={**self.env, **env},
            text=True,
            capture_output=True,
            timeout=10,
        )

    def test_app_external_screenshots_survive_gradle_cleanup_for_collection(self):
        result = self.collect()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        output = self.root / "runner-temp/yft-smoke"
        self.assertEqual(3, len(list(output.rglob("*.png"))))
        self.assertEqual(3, len(list(output.rglob("*.bounds.txt"))))
        self.assertIn("Instrumentation results: tests=3 failures=0", result.stdout)
        self.assertFalse((self.root / "runner-temp/yft-smoke-raw-logcat.txt").exists())

    def test_failed_instrumentation_still_collects_and_preserves_failure(self):
        result = self.collect(SMOKE_TEST_RESULT="23")
        self.assertEqual(23, result.returncode, result.stdout + result.stderr)
        self.assertEqual(3, len(list((self.root / "runner-temp/yft-smoke").rglob("*.png"))))

    def test_failed_pull_is_not_silently_ignored(self):
        result = self.collect(SMOKE_PULL_FAILURE="1")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("::error::Could not collect emulator screenshots", result.stdout)

    def test_logcat_capture_failure_is_not_hidden_by_passing_tests(self):
        result = self.collect(SMOKE_LOG_FAILURE="1")
        self.assertEqual(7, result.returncode)
        self.assertIn("::error::Could not capture emulator logcat", result.stdout)


if __name__ == "__main__":
    unittest.main()