import importlib.util
import pathlib
import tempfile
import unittest
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "release_debug_actions", ROOT / "verify-release-debug-actions.py"
)
GUARD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GUARD)


class ReleaseDebugActionsTest(unittest.TestCase):
    def apk(self, entries):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        path = pathlib.Path(temporary.name) / "fixture.apk"
        with zipfile.ZipFile(path, "w") as archive:
            for name, data in entries.items():
                archive.writestr(name, data)
        return path

    def test_release_code_without_debug_actions_is_accepted(self):
        GUARD.verify(self.apk({"classes.dex": b"production-only-fixture"}))

    def test_each_debug_marker_is_rejected_even_in_secondary_dex(self):
        for marker in GUARD.DEBUG_MARKERS:
            with self.subTest(marker=marker):
                path = self.apk({"classes.dex": b"fixture", "classes2.dex": marker})
                with self.assertRaisesRegex(ValueError, "Debug crash action"):
                    GUARD.verify(path)

    def test_a_trial_tool_is_rejected_but_in_the_trial(self):
        for marker in GUARD.TRIAL_TOOL_MARKERS:
            with self.subTest(marker=marker):
                path = self.apk({"classes.dex": b"fixture", "classes2.dex": b"x" + marker + b"y"})
                with self.assertRaisesRegex(ValueError, "trial tool"):
                    GUARD.verify(path)
                with self.assertRaisesRegex(ValueError, "trial tool"):
                    GUARD.main([str(path)])
                GUARD.verify(path, allow_trial_tools=True)
                self.assertIn("allowed", GUARD.main(["--allow-trial-tools", str(path)]))

    def test_the_trial_flag_never_allows_a_debug_action(self):
        path = self.apk({"classes.dex": GUARD.DEBUG_MARKERS[0] + GUARD.TRIAL_TOOL_MARKERS[0]})
        with self.assertRaisesRegex(ValueError, "Debug crash action"):
            GUARD.verify(path, allow_trial_tools=True)

    def test_the_apk_check_allows_trial_tools_only_for_the_trial_package(self):
        script = (ROOT / "verify-release-apk.sh").read_text()
        self.assertIn('if [[ "$expected_package" == "com.alal.yft.trial" ]]; then', script)
        self.assertIn("trial_tools=(--allow-trial-tools)", script)
        self.assertEqual(1, script.count("--allow-trial-tools"))
        self.assertIn('verify-release-debug-actions.py" ${trial_tools[@]+"${trial_tools[@]}"} "$apk"', script)

    def test_one_apk_is_required(self):
        with self.assertRaises(IndexError):
            GUARD.main(["--allow-trial-tools"])

    def test_missing_dex_is_not_mistaken_for_a_safe_release(self):
        with self.assertRaisesRegex(ValueError, "no DEX"):
            GUARD.verify(self.apk({"assets/fixture": b"fixture"}))