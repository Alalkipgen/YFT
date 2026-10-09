package com.alal.yft.extractor.master.android

import com.alal.yft.core.browser.detection.FocusedVideoProbe
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue

/** Reuse the production focus parser; a requested feed ID cannot label a different video. */
internal object CaptureFocusGuard {
    fun matches(
        encoded: String?,
        pageUrl: String,
        expectedId: String,
        contentIdOf: (String) -> String?,
    ): Boolean {
        if (encoded == null || encoded.length > 64 * 1024) return false
        val outer = BoundedJsonParser.parse(encoded, maxDepth = 8, maxNodes = 64) ?: return false
        val raw = (outer as? JsonValue.Text)?.value ?: encoded
        if (raw.length > 16 * 1024) return false
        if (BoundedJsonParser.parse(raw, maxDepth = 8, maxNodes = 64) !is JsonValue.Object) {
            return false
        }
        val focused = FocusedVideoProbe.parse(encoded, pageUrl) ?: return false
        return contentIdOf(focused.url) == expectedId
    }
}