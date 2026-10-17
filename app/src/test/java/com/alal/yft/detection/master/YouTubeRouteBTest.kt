package com.alal.yft.detection.master

import com.alal.yft.detection.OkHttpExtractorClient
import com.alal.yft.extractor.master.lookup.LookupRecorder
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 1.4 item 7 (YouTube routing B) R1: the app's wiring of the per-lookup recorder. */
class YouTubeRouteBTest {
    @Test
    fun theSharedAdapterClientRecordsOnlyInARouteBBuild() {
        val client = OkHttpClient()
        // Every other build: today's client, exactly.
        val plain = YouTubeRouteB.mainClient(client, routeB = false)
        assertTrue(plain.toString(), plain is OkHttpExtractorClient)
        // A route-B build: the same client inside the recorder (it passes through without a record).
        val recording = YouTubeRouteB.mainClient(client, routeB = true)
        assertTrue(recording.toString(), recording is LookupRecorder)
    }
}
