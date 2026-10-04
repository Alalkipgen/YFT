import importlib.util
import pathlib
import tempfile
import unittest
import xml.etree.ElementTree as ET

path = pathlib.Path(__file__).resolve().parents[1] / "ci-smoke-diagnostics.py"
spec = importlib.util.spec_from_file_location("smoke_diagnostics", path)
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)


class SmokeDiagnosticsTest(unittest.TestCase):
    def test_signed_links_credentials_and_headers_are_not_preserved(self):
        original = (
            "https://name:fixture@example.test/private/path?signature=fixture#token\n"
            "Cookie: fixture-session\nAuthorization: Bearer fixture-value\n"
            "access_token=fixture-token\n"
            'JSON {"token":"fixture-json"}\n'
        )
        sanitized = smoke.sanitize_logcat(original)
        self.assertIn("https://example.test/[redacted]", sanitized)
        for value in ["fixture", "signature", "private/path", "#token"]:
            self.assertNotIn(value, sanitized)

    def test_only_valid_coordinate_lines_can_be_annotations(self):
        lines = smoke.safe_bounds(
            "browser-address bounds=[12,48][940,110]\n"
            "WebView bounds=absent\n"
            "::error::untrusted annotation\n"
            "browser-address bounds=https://example.test/?signature=fixture\n"
        )
        self.assertEqual(
            ["browser-address bounds=[12,48][940,110]", "WebView bounds=absent"], lines
        )

    def test_site_diagnostics_keep_only_whitelisted_pairs_and_console_error_types(self):
        sanitized = smoke.sanitize_logcat(
            "I YFTSmoke: YFT-DIAG fb-share load chain=www.facebook.com/share/v/x dark=97pc\n"
            "I YFTSmoke: YFT-DIAG fb-share tap title=::error::injected text\n"
            "I YFTSmoke: YFT-DIAG tt-video load url=https://www.tiktok.com/@a/video/1?sig=s\n"
            'I chromium: [INFO:CONSOLE(3)] "Uncaught TypeError: secret detail", source: x\n'
            'I chromium: [INFO:CONSOLE(9)] "Uncaught TypeError: other", source: x\n'
        )
        lines, errors = smoke.site_diagnostics(sanitized)
        self.assertEqual(["fb-share load chain=www.facebook.com/share/v/x dark=97pc"], lines)
        self.assertEqual({"Uncaught TypeError": 2}, errors)

    def test_site_diagnostics_become_one_escaped_notice(self):
        with tempfile.TemporaryDirectory() as folder:
            root = pathlib.Path(folder)
            raw = root / "raw.txt"
            output = root / "artifact"
            raw.write_text(
                "YFT-DIAG fb-share load dark=97pc\nYFT-DIAG fb-share tap dark=10pc\n"
            )
            messages = []
            self.assertEqual(0, smoke.diagnostics(raw, output, messages.append))
            self.assertIn(
                "::notice::Site page diagnostics%0Afb-share load dark=97pc%0Afb-share tap dark=10pc",
                messages,
            )

    def test_fatal_log_is_redacted_removed_and_returns_failure(self):
        with tempfile.TemporaryDirectory() as folder:
            root = pathlib.Path(folder)
            raw = root / "raw.txt"
            output = root / "artifact"
            raw.write_text("FATAL EXCEPTION: main\nCookie: fixture-value\n")
            messages = []
            self.assertEqual(1, smoke.diagnostics(raw, output, messages.append))
            self.assertFalse(raw.exists())
            self.assertNotIn("fixture-value", (output / "logcat.txt").read_text())
            self.assertIn("::error::FATAL EXCEPTION detected in emulator logcat", messages)

    def test_clean_log_emits_bounds_and_slow_site_warning_without_failure(self):
        with tempfile.TemporaryDirectory() as folder:
            root = pathlib.Path(folder)
            raw = root / "raw.txt"
            output = root / "artifact"
            output.mkdir()
            (output / "01-browser-empty.bounds.txt").write_text(
                "browser-address bounds=[12,48][940,110]\nWebView bounds=[0,120][1080,1800]\n"
            )
            raw.write_text("YFTSmoke slow-site warning\n")
            messages = []
            self.assertEqual(0, smoke.diagnostics(raw, output, messages.append))
            self.assertTrue(any("browser-address bounds=[12,48][940,110]" in m for m in messages))
            self.assertTrue(any(m.startswith("::warning::") for m in messages))

    def test_report_redaction_keeps_junit_xml_valid_and_preserves_counts(self):
        with tempfile.TemporaryDirectory() as folder:
            root = pathlib.Path(folder)
            report = root / "TEST-fixture.xml"
            report.write_text(
                '<testsuite tests="1" failures="1" errors="0" skipped="0">'
                '<testcase><failure message="Cookie: fixture-value">'
                "https://example.test/private?signature=fixture"
                "</failure></testcase></testsuite>"
            )
            totals = smoke.sanitize_reports([root])
            self.assertEqual(dict(tests=1, failures=1, errors=0, skipped=0), totals)
            self.assertNotIn("fixture-value", report.read_text())
            self.assertNotIn("signature", report.read_text())
            self.assertEqual("1", ET.parse(report).getroot().get("tests"))

    def test_cli_screenshot_gate_rejects_missing_captures(self):
        with tempfile.TemporaryDirectory() as folder:
            root = pathlib.Path(folder)
            raw = root / "raw.txt"
            raw.write_text("clean log\n")
            output = root / "artifact"
            messages = []
            self.assertEqual(
                1, smoke.diagnostics(raw, output, messages.append, require_screenshots=True)
            )
            self.assertEqual(3, sum("screenshot missing" in line for line in messages))


if __name__ == "__main__":
    unittest.main()