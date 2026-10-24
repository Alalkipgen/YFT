package com.alal.yft.extractor.master.android

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R5: the policy put into the shipped asset; the JS behaviour is covered by codecs.test.cjs. */
class CodecSteeringTest {
    private val asset = File("src/main/assets/${CodecSteering.ASSET}").readText()

    @Test
    fun thePolicyReplacesTheDefaultOnceAndNothingElse() {
        val script = CodecSteering(vp9 = true).script(asset)
        assertTrue(script.contains("const policy = { vp9: true, av1: false };"))
        assertFalse(script.contains("/*YFT_CODEC_POLICY*/"))
        val old = CodecSteering(vp9 = false).script(asset)
        assertTrue(old.contains("const policy = { vp9: false, av1: false };"))
        assertEquals(
            asset.substringAfter("/*END*/"),
            old.substringAfter("{ vp9: false, av1: false }"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun aTemplateWithoutTheMarkerIsRefused() {
        CodecSteering(vp9 = true).script("(function(){})();")
    }

    @Test
    fun theScriptStaysOffOnYouTubeAndNeverTouchesDrmQueries() {
        assertTrue(asset.contains("\"youtube.com\", \"youtu.be\", \"youtube-nocookie.com\""))
        assertTrue(asset.contains("if (configuration.keySystemConfiguration) return originalInfo"))
        assertEquals(setOf("*"), CodecSteering.ORIGINS)
    }
}
