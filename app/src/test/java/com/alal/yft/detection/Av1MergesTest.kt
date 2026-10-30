package com.alal.yft.detection

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.download.AudioVideoMuxCompatibility
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.feature.downloads.DownloadPlanFactory
import com.alal.yft.feature.downloads.DownloadPlanResult
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures
import com.alal.yft.feature.quickdownload.SheetOption
import com.alal.yft.ui.format.YftQualityNames
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.0.2 item 26: AV1 merges as a debug build's test switch (the census screen's box), off by
 * default and absent from the release and the previews. While it is on, Android 14 or newer keeps
 * AV1 + AAC rows on the sheet (named with "AV1"), plans them as one MP4 merge and names the phone's
 * AV1 decoders in a merged download's Details; switched off, or in a build without it, everything
 * is as before. Each case passes the build's switch explicitly, so it holds in any build.
 * Synthetic rows and made-up decoder names; no live calls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Av1MergesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun switchOff() = reset()

    @After
    fun switchOffAgain() = reset()

    @Test
    fun theSwitchIsOffByDefaultAndOnlyInABuildThatHasIt() {
        assertFalse(AudioVideoMuxCompatibility.AV1_MP4_ENABLED) // the release: AV1 merges off
        Av1MergeSwitch.install(context, built = true)
        assertFalse(Av1MergeSwitch.on(context, built = true)) // off by default
        assertFalse(Av1MergeSwitch.merges())
        assertNull(Av1MergeSwitch.detailLine())

        Av1MergeSwitch.set(context, true)
        assertTrue(Av1MergeSwitch.on(context, built = true))
        assertTrue(Av1MergeSwitch.merges()) // read at once: no restart needed
        assertTrue(Av1MergeSwitch.detailLine().orEmpty().startsWith(Av1Decoders.LABEL))
        // A build without the switch (the release, the previews): never on, whatever is stored.
        assertFalse(Av1MergeSwitch.on(context, built = false))
        Av1MergeSwitch.install(context, built = false)
        assertFalse(Av1MergeSwitch.merges())
        assertNull(Av1MergeSwitch.detailLine())
        // A debug build again: on while the box is ticked, off as soon as it is cleared.
        Av1MergeSwitch.install(context, built = true)
        assertTrue(Av1MergeSwitch.merges())
        Av1MergeSwitch.set(context, false)
        assertFalse(Av1MergeSwitch.merges())
        assertEquals("av1_merges_test", Av1MergeSwitch.KEY)
    }

    @Test
    fun whileOnAndroid14KeepsAv1MergesNamesThemWithAv1AndPlansOneMp4() {
        val av1 = QuickDownloadFixtures.video(1080, 80 * QuickDownloadFixtures.MIB, merged = true)
            .copy(codecs = listOf(AV1_CODEC))
        val avc = QuickDownloadFixtures.video(720, 42 * QuickDownloadFixtures.MIB, merged = true)
        val android16 = DeviceMergeSupport(sdkInt = 36, hasDecoder = { true })
        val android13 = DeviceMergeSupport(sdkInt = 33, hasDecoder = { true })
        val noDecoder = DeviceMergeSupport(sdkInt = 36, hasDecoder = { false })
        Av1MergeSwitch.install(context, built = true)

        // Off: the release's sheet (no AV1 merge) and names, the merge refused.
        assertFalse(android16.canMerge(av1))
        assertTrue(android16.canMerge(avc))
        assertEquals(listOf("1080p · Full HD", "720p · HD"), titles(listOf(av1, avc)))
        assertEquals(DownloadFailureReason.INCOMPATIBLE_TRACKS, (plan(av1, sdkInt = 36) as DownloadPlanResult.Rejected).reason)

        Av1MergeSwitch.set(context, true)
        assertTrue(android16.canMerge(av1))
        assertFalse(android13.canMerge(av1)) // Android 13 or older: never
        assertFalse(noDecoder.canMerge(av1)) // nothing to play it with: never
        assertEquals(listOf("1080p · Full HD · AV1", "720p · HD"), titles(listOf(av1, avc)))
        assertTrue(plan(av1, sdkInt = 34) is DownloadPlanResult.Ready)
        assertEquals(DownloadFailureReason.INCOMPATIBLE_TRACKS, (plan(av1, sdkInt = 33) as DownloadPlanResult.Rejected).reason)

        Av1MergeSwitch.set(context, false)
        assertFalse(android16.canMerge(av1))
        assertEquals(listOf("1080p · Full HD", "720p · HD"), titles(listOf(av1, avc)))
    }

    @Test
    fun whileOnAnAv1RowShowsOnlyAtASizeNoOtherFileHas() {
        val mib = QuickDownloadFixtures.MIB
        // 1080p in AVC and in AV1 (the AV1 at the higher bitrate); 1440p in AV1 only.
        val avc1080 = QuickDownloadFixtures.video(1080, 80 * mib, merged = true, index = 1)
            .copy(bitrateBitsPerSecond = 2_000_000)
        val av11080 = QuickDownloadFixtures.video(1080, 60 * mib, merged = true, index = 2)
            .copy(codecs = listOf(AV1_CODEC), bitrateBitsPerSecond = 3_000_000)
        val av11440 = QuickDownloadFixtures.video(1440, 120 * mib, merged = true, index = 3)
            .copy(codecs = listOf(AV1_CODEC))
        Av1MergeSwitch.install(context, built = true)
        Av1MergeSwitch.set(context, true)

        val rows = QuickDownloadFixtures.choices(listOf(av11440, av11080, avc1080))!!.video

        assertEquals(listOf("1440p · 2K · AV1", "1080p · Full HD"), rows.map(SheetOption::title))
        assertEquals(avc1080.mediaUrl, rows[1].variant.playbackUrl) // the size's AVC file, as before
        assertEquals(av11440.mediaUrl, rows[0].variant.playbackUrl)
    }

    @Test
    fun anAv1MergesNameAndOnlyAMergesName() {
        assertEquals("1080p · Full HD · AV1", YftQualityNames.videoName(1080, 30.0, av1 = true))
        assertEquals("1440p60 · 2K · AV1", YftQualityNames.videoName(1440, 60.0, av1 = true))
        assertEquals("1080p · Full HD", YftQualityNames.videoName(1080, 30.0))
        assertTrue(YftQualityNames.isAv1Merge(listOf(" AV01.0.08M.08 "), merged = true, mergesOn = true))
        // Not merged (a whole AV1 file), not AV1, or AV1 merges off: named as before.
        assertFalse(YftQualityNames.isAv1Merge(listOf(AV1_CODEC), merged = false, mergesOn = true))
        assertFalse(YftQualityNames.isAv1Merge(listOf("avc1.4d401f"), merged = true, mergesOn = true))
        assertFalse(YftQualityNames.isAv1Merge(listOf(AV1_CODEC), merged = true, mergesOn = false))
        assertFalse(YftQualityNames.isAv1Merge(listOf(AV1_CODEC), merged = true)) // the switch: off
    }

    @Test
    fun theDecoderLineSaysHardwareSoftwareBothOrNone() {
        val hardware = Av1Decoders.Decoder("c2.qti.av1.decoder", hardware = true)
        val software = Av1Decoders.Decoder("c2.android.av1.decoder", hardware = false)

        assertEquals("AV1 decoder: hardware (c2.qti.av1.decoder)", Av1Decoders.line(listOf(hardware)))
        assertEquals("AV1 decoder: software (c2.android.av1.decoder)", Av1Decoders.line(listOf(software)))
        assertEquals(
            "AV1 decoder: hardware (c2.qti.av1.decoder), software (c2.android.av1.decoder)",
            Av1Decoders.line(listOf(software, hardware, hardware.copy(name = "c2.qti.av1.decoder.secure"))),
        )
        assertEquals("AV1 decoder: none", Av1Decoders.line(emptyList()))
        assertEquals("hardware/software", Av1Decoders.kinds(listOf(software, hardware)))
        assertEquals("hardware", Av1Decoders.kinds(listOf(hardware)))
        assertEquals("software", Av1Decoders.kinds(listOf(software)))
        assertEquals("none", Av1Decoders.kinds(emptyList()))
        // By name, where the phone does not say: Android's own decoders are software.
        assertTrue(Av1Decoders.isSoftwareName("c2.android.av1.decoder"))
        assertTrue(Av1Decoders.isSoftwareName("OMX.google.av1.decoder"))
        assertTrue(Av1Decoders.isSoftwareName("c2.google.av1.decoder"))
        assertFalse(Av1Decoders.isSoftwareName("c2.qti.av1.decoder"))
        assertFalse(Av1Decoders.isSoftwareName("c2.mtk.av1.decoder"))
        // This (test) phone's list is read without failing; its line has the label.
        assertTrue(Av1Decoders.line.startsWith(Av1Decoders.LABEL))
    }

    private fun titles(candidates: List<MediaCandidate>): List<String> =
        QuickDownloadFixtures.choices(candidates)!!.video.map(SheetOption::title)

    private fun plan(candidate: MediaCandidate, sdkInt: Int): DownloadPlanResult {
        val asset = QuickDownloadFixtures.resolvedAsset(candidate)
        // The default: the switch as the app holds it.
        return DownloadPlanFactory.create(asset, asset.variants.single(), "task-1", 1_000L, sdkInt = sdkInt)
    }

    private fun reset() {
        Av1MergeSwitch.set(context, false)
        Av1MergeSwitch.install(context, built = false)
    }

    private companion object {
        const val AV1_CODEC = "av01.0.08M.08"
    }
}
