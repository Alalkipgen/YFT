import contextlib
import importlib.util
import io
import json
import pathlib
import types
import unittest
from unittest import mock

ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("live_check", ROOT / "live-check.py")
CHECK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECK)


class LiveCheckTest(unittest.TestCase):
    def test_public_https_query_is_allowed_but_unsafe_addresses_are_rejected(self):
        CHECK.public_url("https://www.youtube.com/watch?v=fixture")
        for value in (
            "http://example.org/", "https://name:fixture@example.org/",
            "https://localhost/", "https://127.0.0.1/", "https://[::1]/",
            "https://192.168.1.1/", "https://10.0.0.1/", "https://host.local/",
            "https://example.org/\nfixture", "https://example.org:invalid/",
        ):
            with self.subTest(value=value), self.assertRaises(ValueError):
                CHECK.public_url(value)

    def test_summary_never_echoes_queries_credentials_or_body_text(self):
        body = b"private-fixture signature=fixture Cookie: fixture browser_native_hd_url"
        text = CHECK.safe_summary(
            {"http_code": 200, "url_effective": "https://www.facebook.com/reel/123/?signed=fixture#x"},
            body, 0,
        )
        self.assertIn("status: 200", text)
        self.assertIn("host: www.facebook.com", text)
        self.assertIn("path: /reel/123/", text)
        self.assertIn("marker browser_native_hd_url: yes", text)
        for forbidden in ("?", "signed", "fixture", "Cookie", "signature", "#x"):
            self.assertNotIn(forbidden, text)

    def test_malformed_metadata_is_safe_and_does_not_echo_raw_values(self):
        for metadata in (None, ["private-fixture"], "private-fixture", {"url_effective": None}):
            with self.subTest(metadata=metadata):
                text = CHECK.safe_summary(metadata, b"private-fixture", 6)
                self.assertIn("host: unavailable", text)
                self.assertNotIn("private-fixture", text)

    def test_all_markers_and_actual_bounded_byte_count_are_reported(self):
        body = " ".join(CHECK.MARKERS).encode()
        text = CHECK.safe_summary({"http_code": 200, "url_effective": "https://example.org/"}, body, 0)
        self.assertIn(f"bytes: {len(body)}", text)
        for marker in CHECK.MARKERS:
            self.assertIn(f"marker {marker}: yes", text)

    def test_untrusted_effective_url_is_omitted(self):
        text = CHECK.safe_summary({"http_code": "bad", "url_effective": "https://user:fixture@x/"}, b"", 3)
        self.assertIn("host: unavailable", text)
        self.assertIn("status: 0", text)
        self.assertNotIn("fixture", text)
        self.assertIn("transport: curl exit 3", text)

    def transport(self, command, **kwargs):
        self.command = command
        self.assertTrue(kwargs["capture_output"])
        pathlib.Path(command[command.index("--output") + 1]).write_bytes(b"playabilityStatus")
        return types.SimpleNamespace(
            returncode=0,
            stdout=json.dumps({"http_code": 200, "url_effective": "https://example.org/page?x=fixture"}).encode(),
            stderr=b"never-echo-this-fixture",
        )

    def test_curl_ignores_user_config_uses_honest_identity_and_never_adds_cookies(self):
        output = io.StringIO()
        with mock.patch.object(CHECK.subprocess, "run", side_effect=self.transport), contextlib.redirect_stdout(output):
            self.assertEqual(0, CHECK.check("https://example.org/page?x=fixture"))
        self.assertEqual(["curl", "--disable"], self.command[:2])
        agent = self.command[self.command.index("--user-agent") + 1]
        self.assertTrue(agent.startswith("Mozilla/5.0 (X11; Linux x86_64) YFT/"))
        self.assertNotIn("Android", agent)
        for name, value in CHECK.NAVIGATION_HEADERS.items():
            self.assertIn(f"{name}: {value}", self.command)
        self.assertNotIn("--insecure", self.command)
        self.assertNotIn("--cookie", self.command)
        self.assertNotIn("--cookie-jar", self.command)
        self.assertNotIn("fixture", output.getvalue())
        body_file = pathlib.Path(self.command[self.command.index("--output") + 1])
        self.assertFalse(body_file.exists())

    def test_transport_failure_stays_nonzero_without_echoing_raw_stderr(self):
        output = io.StringIO()
        answer = types.SimpleNamespace(returncode=6, stdout=b"", stderr=b"signed=fixture Cookie: fixture")
        with mock.patch.object(CHECK.subprocess, "run", return_value=answer), contextlib.redirect_stdout(output):
            self.assertEqual(1, CHECK.check("https://example.org/"))
        self.assertIn("transport: curl exit 6", output.getvalue())
        self.assertNotIn("fixture", output.getvalue())
        self.assertNotIn("Cookie", output.getvalue())

    def test_http_error_stays_nonzero(self):
        output = io.StringIO()
        answer = types.SimpleNamespace(
            returncode=0, stdout=b'{"http_code":403,"url_effective":"https://example.org/"}', stderr=b"",
        )
        with mock.patch.object(CHECK.subprocess, "run", return_value=answer), contextlib.redirect_stdout(output):
            self.assertEqual(1, CHECK.check("https://example.org/"))
        self.assertIn("status: 403", output.getvalue())

    def test_script_header_values_match_the_shared_android_defaults(self):
        source = (ROOT.parent / "core-model/src/main/kotlin/com/alal/yft/core/model/media/PageNavigationHeaders.kt").read_text()
        for value in CHECK.NAVIGATION_HEADERS.values():
            self.assertIn(f'"{value}"', source)
