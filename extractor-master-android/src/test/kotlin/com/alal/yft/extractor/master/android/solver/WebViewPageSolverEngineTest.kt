package com.alal.yft.extractor.master.android.solver

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WebViewPageSolverEngineTest {
    @Test
    fun `without WebView's message listener feature the engine is unavailable and runs nothing`() {
        val engine = WebViewPageSolverEngine(ApplicationProvider.getApplicationContext())
        assertFalse(engine.isAvailable)
        assertNull(runBlocking { engine.run("{\"type\":\"player\",\"player\":\"x\",\"n\":[\"a\"],\"sig\":[]}") })
    }
}
