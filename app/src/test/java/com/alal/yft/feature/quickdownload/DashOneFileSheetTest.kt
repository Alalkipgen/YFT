package com.alal.yft.feature.quickdownload

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.detection.DashSoundPairing
import com.alal.yft.detection.DeviceMergeSupport
import com.alal.yft.feature.downloads.DownloadPlanFactory
import com.alal.yft.feature.downloads.DownloadPlanResult
import com.alal.yft.feature.downloads.DownloadRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.2 item 59: the sheet of a page's DASH stream whose qualities are one file each, shaped as
 * the phone test's stream (DASH-IF's on-demand test: four 480 × 360 AVC qualities at 24 fps,
 * 150–750 kbps, one HE-AAC sound at 72 kbps in English, 654 s), as the resolver reads it.
 */
class DashOneFileSheetTest {
    private val phone = DeviceMergeSupport(sdkInt = 34, hasDecoder = { false }, av1Enabled = { false })
    private val candidate = MediaCandidate(
        pageUrl = MANIFEST,
        mediaUrl = MANIFEST,
        sources = setOf(CandidateSource.PASTED_URL),
        kind = MediaKind.DASH,
    )

    @Test
    fun `the video row has its sound and the M4A row is the stream's sound`() {
        // Before item 59: the video was a silent row whose download failed.
        val before = choices(asset())
        assertEquals(listOf("360p"), before.video.map(SheetOption::title))
        assertEquals(listOf(QuickDownloadChoices.NO_SOUND), before.video.single().chips)

        val paired = (
            DashSoundPairing.pair(VariantResolutionResult.Success(asset()), candidate, phone::canMerge)
                as VariantResolutionResult.Success
            ).asset
        val choices = choices(paired)

        val row = choices.video.single()
        assertEquals("360p", row.title)
        assertEquals("480 × 360 · 24 fps · MP4", row.detail)
        assertEquals(emptyList<String>(), row.chips)
        // 750 + 72 kbps over 654 s, an estimate.
        assertEquals("~64 MB", row.size)
        assertEquals("5", row.variant.manifestVariantId)
        assertEquals("1", row.variant.audioCompanion?.representationId)
        val sound = choices.audio.single()
        assertEquals("M4A", sound.title)
        assertEquals("72 kbps · en", sound.detail)
        assertEquals("~5.6 MB", sound.size)

        // The video row downloads both representations into one MP4; the M4A row the sound.
        val merge = (plan(paired, row.variant) as DownloadPlanResult.Ready).request
            as DownloadRequest.Mux
        assertEquals("5", merge.plan.video.representationId)
        assertEquals("1", merge.plan.audio.representationId)
        assertEquals("video/mp4", merge.plan.outputMimeType)
        assertTrue(merge.fileName, merge.fileName.endsWith(".mp4"))
        val m4a = (plan(paired, sound.variant) as DownloadPlanResult.Ready).request
            as DownloadRequest.Dash
        assertEquals("1", m4a.plan.representationId)
        assertTrue(m4a.fileName, m4a.fileName.endsWith(".m4a"))
    }

    private fun choices(asset: MediaAsset): QuickChoices = requireNotNull(
        QuickDownloadChoices.of(
            MediaGroups.of(listOf(candidate)).single(),
            listOf(SheetSource(candidate, asset, resolved = true)),
        ),
    )

    private fun plan(asset: MediaAsset, variant: MediaVariant) = DownloadPlanFactory.create(
        asset = asset,
        variant = variant,
        taskId = "task-${variant.id}",
        nowEpochMs = 1_000L,
        sdkInt = 34,
        av1Enabled = false,
    )

    /** What the resolver reads from the manifest: the sound first, then the four qualities. */
    private fun asset(): MediaAsset {
        val sound = representation(0, "1", MediaTrackType.AUDIO, 72_000).copy(
            mimeType = "audio/mp4",
            codecs = listOf("mp4a.40.5"),
            language = "en",
        )
        val videos = listOf(150_000L, 250_000L, 350_000L, 750_000L).mapIndexed { index, bitrate ->
            representation(index + 1, "${index + 2}", MediaTrackType.VIDEO, bitrate).copy(
                mimeType = "video/mp4",
                codecs = listOf("avc1.42401E"),
                width = 480,
                height = 360,
                framesPerSecond = 24.0,
            )
        }
        return MediaAsset(
            sourcePageUrl = MANIFEST,
            title = null,
            thumbnailUrl = null,
            durationMillis = LENGTH,
            variants = listOf(sound) + videos,
            resolvedAtEpochMs = 1,
        )
    }

    private fun representation(
        index: Int,
        id: String,
        trackType: MediaTrackType,
        bitrate: Long,
    ) = MediaVariant(
        id = "dash-${trackType.name.lowercase()}-$index",
        playbackUrl = MANIFEST,
        kind = MediaKind.DASH,
        trackType = trackType,
        requestContext = BrowserRequestContext(MANIFEST, null, null),
        manifestVariantId = id,
        label = id,
        container = "DASH",
        bitrateBitsPerSecond = bitrate,
        durationMillis = LENGTH,
        sizeBytes = bitrate * LENGTH / 8_000,
        sizeAccuracy = MediaSizeAccuracy.ESTIMATED,
        dashOneFile = true,
    )

    private companion object {
        const val MANIFEST = "https://dash.example.test/TestCases/1a/stream.mpd"
        const val LENGTH = 654_000L
    }
}
