package com.alal.yft.feature.quickdownload

import com.alal.yft.core.model.media.ResolutionStep
import com.alal.yft.core.model.media.VariantResolutionFailure
import com.alal.yft.core.model.media.VariantResolutionResult
import java.io.IOException
import java.net.MalformedURLException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** P24 (plan step 6): the sheet's failure follows what went wrong, and Details say where. */
class QuickDownloadFailuresTest {
    @Test
    fun aSitesAnswerIsNamedByItsStatus() {
        val expected = mapOf(
            401 to "The site asks you to sign in for this video (HTTP 401).",
            403 to "The site refused this video (HTTP 403).",
            404 to "The site no longer has this video (HTTP 404).",
            410 to "The site no longer has this video (HTTP 410).",
            429 to "The site is busy (HTTP 429). Try again in a minute.",
            503 to "The site had a problem (HTTP 503). Try again later.",
            418 to "The site answered HTTP 418. Try again or pick another format.",
        )
        expected.forEach { (status, message) ->
            val failure = VariantResolutionResult.Failure(
                VariantResolutionFailure.HTTP_STATUS,
                httpStatusCode = status,
            )
            assertEquals(message, QuickDownloadFailures.message(failure))
        }
    }

    @Test
    fun onlyANetworkProblemSaysTheMediaCouldNotBeReached() {
        assertEquals(
            VariantResolutionFailure.NETWORK,
            QuickDownloadFailures.reasonOf(IOException("reset")),
        )
        assertEquals(
            VariantResolutionFailure.INVALID_URL,
            QuickDownloadFailures.reasonOf(MalformedURLException("no protocol")),
        )
        assertEquals(
            VariantResolutionFailure.MALFORMED_MANIFEST,
            QuickDownloadFailures.reasonOf(IllegalStateException("bad tag")),
        )
        assertEquals(
            "The site's list of qualities could not be read.",
            QuickDownloadFailures.message(VariantResolutionFailure.MALFORMED_MANIFEST),
        )
        assertEquals(
            "The media could not be reached. Check the connection and try again.",
            QuickDownloadFailures.message(VariantResolutionFailure.NETWORK),
        )
    }

    @Test
    fun detailsNameTheStepTheHostAndTheStatusButNoPath() {
        val refused = VariantResolutionResult.Failure(
            VariantResolutionFailure.HTTP_STATUS,
            httpStatusCode = 403,
            step = ResolutionStep.MEDIA_PLAYLIST,
            host = "stream.example.test",
        )
        assertEquals(
            listOf("Step: quality playlist", "Host: stream.example.test", "Status: HTTP 403"),
            QuickDownloadFailures.details(refused),
        )
        val unread = VariantResolutionResult.Failure(
            VariantResolutionFailure.MALFORMED_MANIFEST,
            step = ResolutionStep.MANIFEST,
        )
        assertEquals(
            listOf("Step: list of qualities (manifest)", "Status: manifest not readable"),
            QuickDownloadFailures.details(unread),
        )
        assertEquals(
            "cdn.example.test",
            QuickDownloadFailures.hostOf("https://CDN.example.test/v/1.m3u8?token=secret"),
        )
        assertNull(QuickDownloadFailures.hostOf("blob:https://videos.example.test/3f2a"))
    }
}
