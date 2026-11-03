import pathlib
import unittest


class ReleaseSocialFillTest(unittest.TestCase):
    def test_release_and_previews_enable_fill_without_trials(self):
        root = pathlib.Path(__file__).resolve().parents[2]
        build = (root / "app/build.gradle.kts").read_text()
        release_master = build.split("fun com.android.build.api.dsl.ApplicationBuildType.releaseMaster(", 1)[1].split("\n}\n", 1)[0]
        self.assertIn('buildConfigField("boolean", "SOCIAL_FILL", "true")', release_master)
        self.assertIn('buildConfigField("boolean", "SOCIAL_TRIAL", trials.toString())', release_master)
        self.assertEqual(2, build.count('releaseMaster(committedRecipeKey.get(), releaseRecipeUrl, trials = false)'))
        self.assertEqual(1, build.count('releaseMaster(committedRecipeKey.get(), releaseRecipeUrl, trials = true)'))


if __name__ == "__main__":
    unittest.main()
