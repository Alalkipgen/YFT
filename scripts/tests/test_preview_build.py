"""P7: the owner's preview build is the release build under its own app ID and a test key only."""

import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
BUILD = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github" / "workflows" / "preview-apk.yml").read_text(encoding="utf-8")
VERIFY = (ROOT / "scripts" / "verify-release-apk.sh").read_text(encoding="utf-8")


def block(text, start):
    """The brace-balanced block that begins at the first line containing [start]."""
    index = text.index(start)
    depth = 0
    for position in range(text.index("{", index), len(text)):
        depth += {"{": 1, "}": -1}.get(text[position], 0)
        if depth == 0:
            return text[index : position + 1]
    raise AssertionError(f"unbalanced block {start!r}")


class PreviewBuildTypeTest(unittest.TestCase):
    def test_preview_is_the_release_build_under_its_own_app_id(self):
        preview = block(BUILD, 'create("preview") {\n            initWith')
        self.assertIn('initWith(getByName("release"))', preview)
        self.assertIn('applicationIdSuffix = ".preview"', preview)
        self.assertIn('versionNameSuffix = "-preview"', preview)
        self.assertIn("isDebuggable = false", preview)
        self.assertIn('matchingFallbacks += listOf("release")', preview)
        # Shrinking and the release rules come from initWith(release) and are not switched off.
        self.assertNotIn("isMinifyEnabled = false", preview)

    def test_preview_is_signed_only_with_the_preview_test_key(self):
        preview = block(BUILD, 'create("preview") {\n            initWith')
        self.assertIn('signingConfig = signingConfigs.findByName("preview")', preview)
        self.assertNotIn('findByName("release")', preview)
        signing = block(BUILD, "val previewSigning")
        self.assertEqual(
            {"STORE_FILE", "STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD"},
            set(re.findall(r'setting\("([A-Z_]+)"\)', signing)),
        )
        self.assertIn('"YFT_PREVIEW_$name"', signing)
        for release_source in ("YFT_RELEASE_", "keystore.properties", "local.getProperty"):
            self.assertNotIn(release_source, signing)
        self.assertIn("must never use the release keystore", signing)

    def test_preview_uses_the_release_code_without_debug_actions(self):
        self.assertIn('getByName("preview").java.srcDir("src/release/java")', BUILD)
        label = (ROOT / "app" / "src" / "preview" / "res" / "values" / "strings.xml").read_text(
            encoding="utf-8"
        )
        self.assertIn('<string name="app_name">YFT Preview</string>', label)


class PreviewWorkflowTest(unittest.TestCase):
    def test_ci_makes_a_test_key_and_never_reads_secrets(self):
        self.assertIn("keytool -genkeypair", WORKFLOW)
        self.assertIn('trap \'rm -rf "$key_dir"\' EXIT', WORKFLOW)
        self.assertIn("::add-mask::", WORKFLOW)
        self.assertNotIn("secrets.", WORKFLOW)
        self.assertNotIn("YFT_RELEASE_", WORKFLOW)
        self.assertNotIn("keystore.properties", WORKFLOW)

    def test_the_artifact_is_verified_against_the_test_key_and_uploaded(self):
        self.assertIn(":app:assemblePreview", WORKFLOW)
        self.assertIn("--package com.alal.yft.preview", WORKFLOW)
        self.assertIn('--expected-cert-sha256 "$certificate"', WORKFLOW)
        self.assertIn('--expected-version "$version-preview.$GITHUB_RUN_NUMBER"', WORKFLOW)
        self.assertIn("app/build/outputs/apk/preview/app-preview.apk", WORKFLOW)
        self.assertRegex(WORKFLOW, r"name: yft-preview-apk\n")
        self.assertIn("if-no-files-found: error", WORKFLOW)

    def test_the_apk_check_takes_the_preview_package(self):
        self.assertIn('expected_package="com.alal.yft"', VERIFY)
        self.assertIn('--package) [[ $# -ge 2 ]] || usage; expected_package="$2"', VERIFY)
        self.assertIn('[[ "$package_name" == "$expected_package" ]]', VERIFY)


if __name__ == "__main__":
    unittest.main()
