package com.alal.yft.extractor.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FailureDetailsTest {
    @Test
    fun oldConstructionKeepsAnEmptyOptionalDetailsList() {
        val failure = SiteExtractionResult.Failure(SiteExtractionFailure.NETWORK, 503)
        assertEquals(emptyList<String>(), failure.details)
        assertEquals(503, failure.httpStatusCode)
    }

    @Test
    fun incidentalRenderingNeverExposesUnsafeAdapterDetails() {
        val failure = SiteExtractionResult.Failure(
            SiteExtractionFailure.HTTP_STATUS,
            403,
            listOf("Cookie: redaction-fixture", "https://a.test/v?opaque=redaction-fixture"),
        )
        assertFalse(failure.toString().contains("redaction-fixture"))
        assertFalse(failure.toString().contains("?"))
        assertFalse(failure.toString().contains("Cookie"))
    }
}
