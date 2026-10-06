package com.alal.yft.core.model.download

import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFailureDetailsTest {
    @Test
    fun anExceptionIsDescribedByItsClassAndMessage() {
        assertEquals(
            "SocketTimeoutException: timeout",
            DownloadFailureDetails.of(SocketTimeoutException("timeout")),
        )
        assertEquals("IOException", DownloadFailureDetails.of(IOException()))
    }

    @Test
    fun theFirstCauseIsNamedOnce() {
        val cause = IllegalStateException("codec released")

        assertEquals(
            "IOException; cause IllegalStateException: codec released",
            DownloadFailureDetails.of(IOException(cause)),
        )
        assertEquals(
            "IOException: write failed; cause IllegalStateException: codec released",
            DownloadFailureDetails.of(IOException("write failed", cause)),
        )
    }

    @Test
    fun aCopyOfTheErrorMadeByCoroutinesIsNamedOnce() {
        val original = IOException("EACCES (Permission denied)", IllegalStateException("revoked"))
        val copy = IOException("EACCES (Permission denied)", original)

        assertEquals(
            "IOException: EACCES (Permission denied); cause IllegalStateException: revoked",
            DownloadFailureDetails.of(copy),
        )
    }

    @Test
    fun linksAddressesAndHostNamesAreRemoved() {
        val detail = DownloadFailureDetails.of(
            SocketTimeoutException(
                "failed to connect to rr3---sn-abc.googlevideo.com/142.250.1.1 (port 443) " +
                    "from /[2001:db8::1] (port 4321) after https://rr3.example.test/v?id=1",
            ),
        )

        assertEquals(
            "SocketTimeoutException: failed to connect to [host]/[ip] (port 443) " +
                "from /[[ip]] (port 4321) after [link]",
            detail,
        )
        for (leak in listOf("googlevideo", "142.250", "2001:db8", "example.test", "http")) {
            assertFalse("$leak must not be copied", detail.contains(leak))
        }
    }

    @Test
    fun secretsAndTokenLikeValuesAreHidden() {
        val detail = DownloadFailureDetails.sanitize(
            "Authorization: Bearer abc.def-123 token=QWERTY12 api_key: zz9 " +
                "id 4f9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c",
        )!!

        for (secret in listOf("abc.def", "QWERTY12", "zz9", "4f9a8b7c")) {
            assertFalse("$secret must not be copied", detail.contains(secret))
        }
        assertTrue(detail, detail.contains("token=[hidden]"))
        assertTrue(detail, detail.endsWith("id [token]"))
    }

    @Test
    fun longWordsWithoutDigitsAndFileNamesStayReadable() {
        assertEquals(
            "ArrayIndexOutOfBoundsException: open failed: ENOSPC for video.webm.part",
            DownloadFailureDetails.sanitize(
                "ArrayIndexOutOfBoundsException: open failed: ENOSPC for video.webm.part",
            ),
        )
    }

    @Test
    fun aContentUriIsNamedOnlyByItsScheme() {
        assertEquals(
            "FileNotFoundException: [content uri] (No such file)",
            DownloadFailureDetails.sanitize(
                "FileNotFoundException: content://media/external/downloads/42 (No such file)",
            ),
        )
    }

    @Test
    fun controlCharactersAndLineBreaksBecomeOneLine() {
        assertEquals(
            "IOException: first line second line",
            DownloadFailureDetails.sanitize("IOException: first line\n\tsecond line\u0000"),
        )
    }

    @Test
    fun aLongDetailIsCutWithAnEllipsis() {
        val detail = DownloadFailureDetails.sanitize("word ".repeat(60))!!

        assertEquals(DownloadFailureDetails.MAX_CHARS, detail.length)
        assertTrue(detail.endsWith("\u2026"))
    }

    @Test
    fun nothingLeftMeansNoDetail() {
        assertNull(DownloadFailureDetails.sanitize(null))
        assertNull(DownloadFailureDetails.sanitize(" \n\t "))
    }

    @Test
    fun aFailureHasNoStageOrDetailUnlessOneIsGiven() {
        val failure = DownloadFailure(DownloadFailureReason.NETWORK)

        assertNull(failure.httpStatusCode)
        assertNull(failure.stage)
        assertNull(failure.detail)
    }
}
