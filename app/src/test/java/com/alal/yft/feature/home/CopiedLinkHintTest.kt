package com.alal.yft.feature.home

import android.content.ClipDescription
import android.view.textclassifier.TextClassifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CopiedLinkHintTest {
    @Test
    fun onlyTextClipsSuggestALink() {
        assertFalse((null as ClipDescription?).suggestsLink())
        assertTrue(description(ClipDescription.MIMETYPE_TEXT_PLAIN).suggestsLink())
        assertTrue(description(ClipDescription.MIMETYPE_TEXT_URILIST).suggestsLink())
        assertFalse(description("image/png").suggestsLink())
    }

    @Test
    fun classifiedTextMustLookLikeAUrl() {
        val prose = description(ClipDescription.MIMETYPE_TEXT_PLAIN)
            .classified(urlConfidence = 0.1f)
        val link = description(ClipDescription.MIMETYPE_TEXT_PLAIN)
            .classified(urlConfidence = 0.9f)

        assertFalse(prose.suggestsLink())
        assertTrue(link.suggestsLink())
    }

    private fun description(mimeType: String) = ClipDescription("copied", arrayOf(mimeType))

    /** The system sets these after classifying a clip; the setters are hidden platform API. */
    private fun ClipDescription.classified(urlConfidence: Float): ClipDescription = apply {
        ClipDescription::class.java
            .getDeclaredMethod("setConfidenceScores", Map::class.java)
            .apply { isAccessible = true }
            .invoke(this, mapOf(TextClassifier.TYPE_URL to urlConfidence))
        ClipDescription::class.java
            .getDeclaredMethod("setClassificationStatus", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(this, ClipDescription.CLASSIFICATION_COMPLETE)
    }
}
