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

    def test_missing_dex_is_not_mistaken_for_a_safe_release(self):
        with self.assertRaisesRegex(ValueError, "no DEX"):
            GUARD.verify(self.apk({"assets/fixture": b"fixture"}))