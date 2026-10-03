package com.alal.yft.extractor.sites.facebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FacebookQualityMetadataTest {
    @Test
    fun conflictingOrUnrelatedHeightsNeverSupplyAnHdGuess() {
        val manifest = mpd(
            representation(720, "https://cdn.test/hd.mp4") +
                representation(1080, "https://cdn.test/hd.mp4?x=REDACTED") +
                representation(360, "https://cdn.test/unrelated.mp4"),
        )
        val heights = FacebookQualityMetadata.heightsByUrl(listOf(manifest))
        assertEquals("HD", FacebookQualityMetadata.label("HD", "https://cdn.test/hd.mp4", heights))
        assertEquals("SD", FacebookQualityMetadata.label("SD", "https://cdn.test/sd.mp4", heights))
    }

    @Test
    fun malformedOversizedEncryptedAndExternalEntityManifestsCannotSupplyHeights() {
        val invalid = listOf(
            "not XML", "x".repeat(262_145),
            mpd("<ContentProtection/>" + representation(720, "https://cdn.test/hd.mp4")),
            """<!DOCTYPE MPD [<!ENTITY xxe SYSTEM "file:///private-fixture">]>
                <MPD><Representation height="720"><BaseURL>&xxe;</BaseURL>
                </Representation></MPD>""".trimIndent(),
        )
        invalid.forEach { assertTrue(FacebookQualityMetadata.heightsByUrl(listOf(it)).isEmpty()) }
    }

    @Test
    fun relativeAddressesAndNonPositiveHeightsAreNotAppliedToAnotherRendition() {
        val manifest = mpd(representation(720, "../hd.mp4") +
            representation(0, "https://cdn.test/hd.mp4"))
        assertTrue(FacebookQualityMetadata.heightsByUrl(listOf(manifest)).isEmpty())
    }

    private fun mpd(representations: String): String =
        """<MPD xmlns="urn:mpeg:dash:schema:mpd:2011"><Period><AdaptationSet>
            $representations</AdaptationSet></Period></MPD>""".trimIndent()

    private fun representation(height: Int, url: String): String =
        """<Representation height="$height"><BaseURL>$url</BaseURL></Representation>"""
}
