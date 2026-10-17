package com.alal.yft.extractor.master

import com.alal.yft.extractor.master.capture.InMemoryCaptureStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryCaptureStoreTest {
    @Test
    fun `store is unavailable before a browser producer supplies evidence`() = runTest {
        assertEquals(CaptureResult.Unavailable, InMemoryCaptureStore().capture(request()))
    }

    @Test
    fun `navigation clears media and rejects delayed old responses`() = runTest {
        val store = InMemoryCaptureStore()
        store.navigate(PAGE, 0)
        store.recordRequest(0, CapturedRequest(MEDIA))
        store.navigate(PAGE, 1)
        assertFalse(store.recordRequest(0, CapturedRequest(MEDIA)))
        assertFalse(store.recordPayload(0, "{}"))
        val result = store.capture(request()) as CaptureResult.Available
        assertTrue(result.snapshot.requests.isEmpty())
    }

    @Test
    fun `requests are bounded and only HTTPS GET observations enter`() = runTest {
        val store = InMemoryCaptureStore(maxRequests = 2)
        store.navigate(PAGE, 1)
        assertFalse(store.recordRequest(1, CapturedRequest("blob:fixture")))
        assertFalse(store.recordRequest(1, CapturedRequest("http://example.test/v.mp4")))
        assertFalse(store.recordRequest(1, CapturedRequest(MEDIA, method = "POST")))
        (1..3).forEach { store.recordRequest(1, CapturedRequest("$MEDIA?piece=$it")) }
        val result = store.capture(request()) as CaptureResult.Available
        assertEquals(2, result.snapshot.requests.size)
        assertEquals("$MEDIA?piece=2", result.snapshot.requests.first().url)
    }

    @Test
    fun `payload and HTML share one memory budget`() {
        val store = InMemoryCaptureStore(maxPayloadChars = 10)
        store.navigate(PAGE, 1)
        assertTrue(store.recordPayload(1, "123456"))
        assertFalse(store.recordHtml(1, "12345"))
        assertTrue(store.recordHtml(1, "1234"))
        assertFalse(store.recordPayload(1, "x"))
    }

    @Test
    fun `another page cannot consume this tab's session evidence`() = runTest {
        val store = InMemoryCaptureStore()
        store.navigate(PAGE, 1)
        assertEquals(
            CaptureResult.Unavailable,
            store.capture(request().copy(pageUrl = "$PAGE/other")),
        )
    }

    @Test
    fun `clear drops session payloads and playback state`() = runTest {
        val store = InMemoryCaptureStore()
        store.navigate(PAGE, 1)
        store.recordPayload(1, "SENSITIVE_BODY")
        store.playing(1, MEDIA, true)
        store.clear()
        assertEquals(CaptureResult.Unavailable, store.capture(request()))
        assertEquals("InMemoryCaptureStore(memoryOnly=true)", store.toString())
    }
}