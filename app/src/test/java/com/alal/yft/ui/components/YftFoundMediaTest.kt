package com.alal.yft.ui.components

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.ui.format.YftFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class YftFoundMediaTest {
    @Test
    fun aFoundFileReadsWithTheSheetsQualityNameAndSize() {
        // P25: the found list names a file's quality and size as the Download sheet does.
        assertEquals(
            listOf("MP4", "720p · HD", "25 MB", "0:25"),
            file(height = 720, bytes = 25 * MIB).factLabels(),
        )
        // A merge is its two files, as the sheet adds them up.
        assertEquals(
            listOf("MP4", "1080p · Full HD", "Video + audio", "~84 MB", "0:25"),
            file(height = 1_080, bytes = 80 * MIB, sound = 4 * MIB).factLabels(),
        )
        // The owner's 848 × 478 reel is "480p"; 60 s at 2 Mbit/s is about 15 MB.
        assertEquals(
            listOf("MP4", "480p", "~${YftFormat.bytes(15_000_000)}", "1:00"),
            file(height = 478, width = 848, bitrate = 2_000_000, length = 60_000).factLabels(),
        )
        assertEquals(listOf("MP4", "0:25"), file(height = null).factLabels())
        assertEquals(
            listOf("HLS", "Auto quality", "0:25"),
            file(height = 720, bitrate = 2_000_000, kind = MediaKind.HLS).factLabels(),
        )
        assertEquals(
            listOf("M4A", "4 MB", "0:25"),
            file(height = null, bytes = 4 * MIB, mimeType = "audio/mp4").factLabels(),
        )
    }

    private fun file(
        height: Int?,
        width: Int? = height?.let { it * 16 / 9 },
        bytes: Long? = null,
        sound: Long? = null,
        bitrate: Long? = null,
        length: Long = 25_000,
        kind: MediaKind = MediaKind.DIRECT,
        mimeType: String? = if (kind == MediaKind.DIRECT) "video/mp4" else null,
    ) = MediaCandidate(
        pageUrl = PAGE,
        mediaUrl = if (kind == MediaKind.HLS) "$MEDIA.m3u8" else "$MEDIA.mp4",
        sources = setOf(CandidateSource.PASTED_URL),
        kind = kind,
        mimeType = mimeType,
        title = "Sunrise",
        durationMillis = length,
        contentLengthBytes = bytes,
        audioCompanion = sound?.let {
            CompanionAudio(
                mediaUrl = "https://media.example.test/sunrise.m4a",
                mimeType = "audio/mp4",
                codecs = listOf("mp4a.40.2"),
                requestContext = BrowserRequestContext(PAGE, null, null),
                contentLengthBytes = it,
            )
        },
        width = width,
        height = height,
        bitrateBitsPerSecond = bitrate,
    )

    private companion object {
        const val PAGE = "https://videos.example.test/watch/sunrise"
        const val MEDIA = "https://media.example.test/sunrise"
        const val MIB = 1_048_576L
    }
}
