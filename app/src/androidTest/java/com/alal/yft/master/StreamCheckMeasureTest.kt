package com.alal.yft.master

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 1.2 N2 phone measurement (MASTER_KEY_PHASE1_2_PLAN.md §2 "Live check V in more detail"),
 * run through [StreamCheckMeasurement]: right n, unchanged n, wrong n and wrong sig on one public,
 * embeddable video; status class, bytes and time for the first 1 MiB of each.
 *
 * Opt-in only (`-e yft.streamCheckMeasure 1`), never in default CI;
 * `scripts/stream-check-measure.sh` runs it from a computer. Without a computer the owner opens
 * the debug app's "YFT stream check" entry instead. `-e yft.streamCheckMeasure.video <ID>` picks
 * another video. The report (app files `own-solver/stream-check-measure-*.json`) never holds an
 * address, a header or a value.
 */
@RunWith(AndroidJUnit4::class)
class StreamCheckMeasureTest {
    private val args get() = InstrumentationRegistry.getArguments()

    @Test
    fun measureHowYouTubeAnswersWrongValues() = runBlocking {
        assumeTrue("Opt-in only: -e yft.streamCheckMeasure 1", args.getString("yft.streamCheckMeasure") == "1")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val video = args.getString("yft.streamCheckMeasure.video")
            ?.takeIf(StreamCheckMeasurement.VIDEO_ID::matches)
            ?: StreamCheckMeasurement.DEFAULT_VIDEO

        val outcome = StreamCheckMeasurement(context).run(video)

        // The measurement only counts when the right values open the stream, before and after.
        assertTrue(outcome.report, outcome.rightOpened)
    }
}
