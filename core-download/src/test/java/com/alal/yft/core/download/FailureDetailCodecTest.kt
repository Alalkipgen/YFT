package com.alal.yft.core.download

import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FailureDetailCodecTest {
    @Test
    fun aFailureWithItsDetailsSurvivesTheRoundTrip() {
        val failure = DownloadFailure(
            reason = DownloadFailureReason.ACCESS_DENIED,
            httpStatusCode = 403,
            stage = DownloadFailureStage.CONNECT,
            detail = "Forbidden | try again",
        )

        val payload = FailureDetailCodec.encode(failure)

        assertEquals("CONNECT|403|Forbidden | try again", payload)
        assertEquals(
            failure,
            FailureDetailCodec.decode(DownloadFailureReason.ACCESS_DENIED, payload),
        )
    }

    @Test
    fun aStageAloneIsKept() {
        val failure = DownloadFailure(
            reason = DownloadFailureReason.NETWORK,
            stage = DownloadFailureStage.READ_SOURCE,
        )

        val payload = FailureDetailCodec.encode(failure)

        assertEquals("READ_SOURCE||", payload)
        assertEquals(failure, FailureDetailCodec.decode(DownloadFailureReason.NETWORK, payload))
    }

    @Test
    fun aFailureWithoutDetailsStoresNothing() {
        assertNull(FailureDetailCodec.encode(null))
        assertNull(FailureDetailCodec.encode(DownloadFailure(DownloadFailureReason.NETWORK)))
    }

    @Test
    fun aDetailIsCleanedWhenItIsStoredAndAgainWhenItIsRead() {
        assertEquals(
            "||token=[hidden] refused",
            FailureDetailCodec.encode(
                DownloadFailure(DownloadFailureReason.NETWORK, detail = "token=abc123 refused"),
            ),
        )
        assertEquals(
            "IOException: reset by [link] at [ip]",
            FailureDetailCodec.decode(
                DownloadFailureReason.NETWORK,
                "READ_SOURCE||IOException: reset by https://rr1.example.test/v?sig=1 at 10.0.0.2",
            )?.detail,
        )
    }

    @Test
    fun anUnreadablePayloadIsIgnored() {
        val reason = DownloadFailureReason.NETWORK

        assertNull(FailureDetailCodec.decode(reason, null))
        assertNull(FailureDetailCodec.decode(reason, " "))
        assertNull(FailureDetailCodec.decode(reason, "garbage"))
        assertNull(FailureDetailCodec.decode(reason, "LATER|9999|"))
        assertNull(FailureDetailCodec.decode(null, "READ_SOURCE||"))
    }
}
