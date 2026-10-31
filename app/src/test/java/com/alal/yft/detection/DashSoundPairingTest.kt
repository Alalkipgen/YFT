package com.alal.yft.detection

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.VariantResolutionFailure
import com.alal.yft.core.model.media.VariantResolutionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * v1.0.2 item 59: on a page without a site reader, a DASH manifest's one-file video qualities
 * (`SegmentBase`) are offered with the manifest's sound, as far as the phone merges them. v1.0.2
 * fix R3: so are its qualities in pieces (`SegmentTemplate` or `SegmentList`).
 */
class DashSoundPairingTest {
    private val phone = DeviceMergeSupport(sdkInt = 34, hasDecoder = { false }, av1Enabled = { false })
    private val merges: (MediaVariant, CompanionAudio) -> Boolean = phone::canMerge

    @Test
    fun `a generic page's one-file video takes its manifest's sound`() {
        val high = video("v5", bitrate = 750_000, size = 61_312_500)
        val low = video("v2", bitrate = 150_000, size = 12_262_500)
        val sound = audio("a1", bitrate = 72_000, size = 5_886_000, language = "en")

        val paired = pair(listOf(sound, high, low))

        val variants = paired.asset.variants
        assertEquals(sound, variants[0])
        val withSound = variants[1]
        assertEquals(MediaTrackType.AUDIO_VIDEO, withSound.trackType)
        val companion = requireNotNull(withSound.audioCompanion)
        assertEquals(MANIFEST, companion.mediaUrl)
        assertEquals("a1", companion.representationId)
        assertEquals("audio/mp4", companion.mimeType)
        assertEquals(listOf("mp4a.40.5"), companion.codecs)
        assertEquals(72_000L, companion.bitrateBitsPerSecond)
        assertNull(companion.contentLengthBytes)
        // The sheet's size is the picture's and the sound's, still an estimate.
        assertEquals(67_198_500L, withSound.sizeBytes)
        assertEquals(MediaSizeAccuracy.ESTIMATED, withSound.sizeAccuracy)
        assertEquals("v5", withSound.manifestVariantId)
        assertEquals(MediaTrackType.AUDIO_VIDEO, variants[2].trackType)
        assertEquals("a1", variants[2].audioCompanion?.representationId)
        assertEquals(3, variants.size)
    }

    @Test
    fun `a manifest's qualities in pieces take its sound as one-file ones do`() {
        // R3: bbb_30fps.mpd, one SegmentTemplate for each adaptation set; its sound HE-AAC.
        val high = video("v2160", bitrate = 14_931_538, size = null, oneFile = false, pieces = true)
        val sound = audio("bbb_a64k", bitrate = 67_071, size = null, oneFile = false, pieces = true)

        val variants = pair(listOf(high, sound)).asset.variants

        assertEquals(MediaTrackType.AUDIO_VIDEO, variants[0].trackType)
        assertEquals("v2160", variants[0].manifestVariantId)
        val companion = requireNotNull(variants[0].audioCompanion)
        assertEquals("bbb_a64k", companion.representationId)
        assertEquals(listOf("mp4a.40.5"), companion.codecs)
        assertEquals(sound, variants[1])
        // A one-file video with a sound in pieces, and the other way round.
        val mixed = pair(
            listOf(
                video("one"),
                video("pieces", oneFile = false, pieces = true),
                audio("a1", oneFile = false, pieces = true),
            ),
        ).asset.variants
        assertEquals(listOf("a1", "a1"), mixed.take(2).map { it.audioCompanion?.representationId })
        val fileSound = pair(listOf(video("v1", oneFile = false, pieces = true), audio("a1")))
        assertEquals("a1", fileSound.asset.variants[0].audioCompanion?.representationId)
    }

    @Test
    fun `a site reader's rows, a live manifest's and failures stay as they are`() {
        val result = success(listOf(video("v1"), audio("a1")))
        // A site's adapter names its video: its rows are its own (VK's check refuses them).
        assertSame(result, DashSoundPairing.pair(result, candidate(videoId = "vk:1_2"), merges))
        // A live manifest's tracks are neither one file nor pieces the download reads.
        val live = success(listOf(video("v1", oneFile = false), audio("a1")))
        assertSame(live, DashSoundPairing.pair(live, candidate(), merges))
        val liveSound = success(listOf(video("v1"), audio("a1", oneFile = false)))
        assertSame(liveSound, DashSoundPairing.pair(liveSound, candidate(), merges))
        // Another manifest's sound is not this video's.
        val other = success(listOf(video("v1"), audio("a1").copy(playbackUrl = "$MANIFEST?other")))
        assertEquals(
            MediaTrackType.VIDEO,
            (DashSoundPairing.pair(other, candidate(), merges) as VariantResolutionResult.Success)
                .asset.variants[0].trackType,
        )
        val failure = VariantResolutionResult.Failure(VariantResolutionFailure.NETWORK)
        assertSame(failure, DashSoundPairing.pair(failure, candidate(), merges))
    }

    @Test
    fun `the manifest's first language comes first, then the highest bitrate`() {
        val variants = pair(
            listOf(
                video("v1"),
                audio("en-low", bitrate = 64_000, language = "en"),
                audio("de-high", bitrate = 192_000, language = "de"),
                audio("en-high", bitrate = 128_000, language = "EN"),
            ),
        ).asset.variants
        assertEquals("en-high", variants[0].audioCompanion?.representationId)

        // A sound in no language: the highest bitrate.
        val unnamed = pair(
            listOf(video("v1"), audio("a-low", bitrate = 64_000), audio("a-high", bitrate = 96_000)),
        ).asset.variants
        assertEquals("a-high", unnamed[0].audioCompanion?.representationId)
    }

    @Test
    fun `a sound the phone cannot merge leaves the video silent or takes the next one`() {
        // VP9 WebM video: only an Opus WebM sound, and only from Android 10.
        val vp9 = video("vp9", mimeType = "video/webm", codecs = listOf("vp09.00.21.08"))
        val aac = audio("aac", bitrate = 128_000, language = "en")
        val opus = audio(
            "opus",
            bitrate = 96_000,
            language = "en",
            mimeType = "audio/webm",
            codecs = listOf("opus"),
        )
        val android10 = DeviceMergeSupport(sdkInt = 29, hasDecoder = { false })
        val android9 = DeviceMergeSupport(sdkInt = 28, hasDecoder = { false })

        val merged = pair(listOf(vp9, aac, opus), android10::canMerge).asset.variants[0]
        assertEquals("opus", merged.audioCompanion?.representationId)
        val old = pair(listOf(vp9, aac, opus), android9::canMerge).asset.variants[0]
        assertEquals(MediaTrackType.VIDEO, old.trackType)
        assertNull(old.audioCompanion)

        // AV1: only with the test switch on, from Android 14 and with a decoder (item 26).
        val av1 = video("av1", codecs = listOf("av01.0.08M.08"))
        fun av1With(switch: Boolean, decoder: Boolean) = pair(
            listOf(av1, aac),
            DeviceMergeSupport(sdkInt = 34, hasDecoder = { decoder }, av1Enabled = { switch })::canMerge,
        ).asset.variants[0].audioCompanion
        assertNull(av1With(switch = false, decoder = true))
        assertNull(av1With(switch = true, decoder = false))
        assertEquals("aac", av1With(switch = true, decoder = true)?.representationId)

        // A sound whose manifest names no codecs is no merge.
        val unnamed = pair(listOf(video("v1"), audio("a1", codecs = emptyList()))).asset.variants
        assertNull(unnamed[0].audioCompanion)
    }

    @Test
    fun `a size is both files' only when both are known`() {
        val unknown = pair(listOf(video("v1", size = null), audio("a1", size = 1_000)))
            .asset.variants[0]
        assertNull(unknown.sizeBytes)
        assertNull(unknown.sizeAccuracy)
        val exact = pair(
            listOf(
                video("v1", size = 2_000).copy(sizeAccuracy = MediaSizeAccuracy.EXACT),
                audio("a1", size = 1_000).copy(sizeAccuracy = MediaSizeAccuracy.EXACT),
            ),
        ).asset.variants[0]
        assertEquals(3_000L, exact.sizeBytes)
        assertEquals(MediaSizeAccuracy.EXACT, exact.sizeAccuracy)
    }

    private fun pair(
        variants: List<MediaVariant>,
        gate: (MediaVariant, CompanionAudio) -> Boolean = merges,
    ): VariantResolutionResult.Success =
        DashSoundPairing.pair(success(variants), candidate(), gate) as VariantResolutionResult.Success

    private fun success(variants: List<MediaVariant>) = VariantResolutionResult.Success(
        MediaAsset(
            sourcePageUrl = PAGE,
            title = "Stream",
            thumbnailUrl = null,
            durationMillis = 654_000,
            variants = variants,
            resolvedAtEpochMs = 1,
        ),
    )

    private fun candidate(videoId: String? = null) = MediaCandidate(
        pageUrl = PAGE,
        mediaUrl = MANIFEST,
        sources = setOf(CandidateSource.PASTED_URL),
        kind = MediaKind.DASH,
        videoId = videoId,
    )

    private fun video(
        id: String,
        bitrate: Long = 400_000,
        size: Long? = 32_700_000,
        oneFile: Boolean = true,
        pieces: Boolean = false,
        mimeType: String = "video/mp4",
        codecs: List<String> = listOf("avc1.42401e"),
    ) = variant(id, MediaTrackType.VIDEO, bitrate, size, oneFile, mimeType, codecs, null)
        .copy(width = 480, height = 360, dashPieces = pieces)

    private fun audio(
        id: String,
        bitrate: Long = 72_000,
        size: Long? = 5_886_000,
        language: String? = null,
        oneFile: Boolean = true,
        pieces: Boolean = false,
        mimeType: String = "audio/mp4",
        codecs: List<String> = listOf("mp4a.40.5"),
    ) = variant(id, MediaTrackType.AUDIO, bitrate, size, oneFile, mimeType, codecs, language)
        .copy(dashPieces = pieces)

    private fun variant(
        id: String,
        trackType: MediaTrackType,
        bitrate: Long,
        size: Long?,
        oneFile: Boolean,
        mimeType: String,
        codecs: List<String>,
        language: String?,
    ) = MediaVariant(
        id = "dash-${trackType.name.lowercase()}-$id",
        playbackUrl = MANIFEST,
        kind = MediaKind.DASH,
        trackType = trackType,
        requestContext = BrowserRequestContext(PAGE, null, null),
        manifestVariantId = id,
        label = id,
        mimeType = mimeType,
        container = "DASH",
        codecs = codecs,
        bitrateBitsPerSecond = bitrate,
        durationMillis = 654_000,
        sizeBytes = size,
        sizeAccuracy = size?.let { MediaSizeAccuracy.ESTIMATED },
        language = language,
        dashOneFile = oneFile,
    )

    private companion object {
        const val PAGE = "https://dash.example.test/stream.mpd"
        const val MANIFEST = "https://dash.example.test/stream.mpd"
    }
}
