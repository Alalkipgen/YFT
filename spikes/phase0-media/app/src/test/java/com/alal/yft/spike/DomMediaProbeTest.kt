package com.alal.yft.spike

import org.junit.Assert.assertTrue
import org.junit.Test

class DomMediaProbeTest {
    @Test fun probeIsReadOnlyAndTargetsHtmlMedia() {
        val script = DomMediaProbe.script
        assertTrue(script.contains("video, audio"))
        assertTrue(script.contains("video source, audio source"))
        assertTrue(script.contains("JSON.stringify"))
        assertTrue(!script.contains("document.write"))
    }
}
