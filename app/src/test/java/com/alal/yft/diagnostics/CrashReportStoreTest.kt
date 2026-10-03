package com.alal.yft.diagnostics

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashReportStoreTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private fun store(root: File = temporary.root) = CrashReportStore(
        root,
        CrashReportStore.Environment("1.2.3-debug", 4, 35, "Test maker", "Test model"),
        clock = { 0L },
    )

    private fun reportFile() = File(temporary.root, "diagnostics/last-crash.txt")

    @Test
    fun writesMetadataChainAndFramesBeforeDelegatingTheOriginalError() {
        val store = store()
        val thread = Thread("worker")
        val cause = IllegalArgumentException("cause fixture")
        val error = IllegalStateException("crash fixture", cause).apply {
            stackTrace = arrayOf(StackTraceElement("Fixture", "run", "Fixture.kt", 12))
            addSuppressed(IllegalStateException("suppressed fixture"))
        }
        var delegated = false
        val handler = CrashReportHandler(store) { passedThread, passedError ->
            assertSame(thread, passedThread)
            assertSame(error, passedError)
            assertTrue(reportFile().isFile)
            delegated = true
        }

        handler.uncaughtException(thread, error)

        val report = requireNotNull(store.read())
        listOf(
            "1970-01-01T00:00:00.000Z", "1.2.3-debug (4)", "Android SDK: 35",
            "Test maker Test model", "Thread: worker", "Fixture.kt:12",
            "Caused by:", "cause fixture", "Suppressed:", "suppressed fixture",
        ).forEach { assertTrue(report.contains(it)) }
        assertTrue(delegated)
    }

    @Test
    fun overwritesOnlyTheLastReportAndCanDeleteIt() {
        val store = store()
        assertNull(store.read())
        store.write(Thread.currentThread(), IllegalStateException("first fixture"))
        store.write(Thread.currentThread(), IllegalStateException("second fixture"))
        assertFalse(requireNotNull(store.read()).contains("first fixture"))
        assertTrue(requireNotNull(store.read()).contains("second fixture"))
        assertEquals(listOf("last-crash.txt"), reportFile().parentFile!!.list()!!.toList())
        assertTrue(store.delete())
        assertNull(store.read())
        assertFalse(reportFile().exists())
    }

    @Test
    fun reportRedactsMessagesThreadNamesAndUnknownQueryValues() {
        val store = store()
        val error = IllegalStateException(
            "GET https://a.test/private?opaque=redaction-fixture\n" +
                """{"Cookie":"redaction-fixture","pot":"redaction-fixture"}""",
        )
        store.write(Thread("Authorization: redaction-fixture"), error)
        val report = requireNotNull(store.read())
        assertFalse(report.contains("redaction-fixture"))
        assertFalse(report.contains("?"))
        assertFalse(report.contains("/private"))
        assertTrue(report.contains("[sensitive data omitted]"))
        assertTrue(report.contains("java.lang.IllegalStateException:"))
        assertTrue(report.contains("Thread: [sensitive data omitted]"))
    }

    @Test
    fun capsUtf8BytesWithoutBreakingMultibyteCharacters() {
        store().write(Thread.currentThread(), IllegalStateException("😀".repeat(40_000)))
        val bytes = reportFile().readBytes()
        assertTrue(bytes.size <= CrashReportStore.MAX_REPORT_BYTES)
        assertTrue(bytes.size > 60_000)
        assertFalse(bytes.toString(Charsets.UTF_8).contains('\uFFFD'))
    }

    @Test
    fun cyclesInExceptionChainsAreBounded() {
        val first = IllegalStateException("cycle one")
        val second = IllegalStateException("cycle two", first)
        first.initCause(second)
        store().write(Thread.currentThread(), first)
        assertTrue(requireNotNull(store().read()).contains("[exception chain omitted]"))
    }

    @Test
    fun ioFailureStillDelegates() {
        val blocked = temporary.newFile("not-a-directory")
        var delegated = false
        CrashReportHandler(store(blocked)) { _, _ -> delegated = true }
            .uncaughtException(Thread.currentThread(), IllegalStateException("fixture"))
        assertTrue(delegated)
    }

    @Test
    fun oldOrAlteredFilesAreBoundedAndSanitizedAgainOnRead() {
        reportFile().parentFile!!.mkdirs()
        reportFile().writeText("Cookie: redaction-fixture\nsafe line\n" + "x".repeat(80_000))
        val report = requireNotNull(store().read())
        assertFalse(report.contains("redaction-fixture"))
        assertTrue(report.toByteArray(Charsets.UTF_8).size <= CrashReportStore.MAX_REPORT_BYTES)
        assertTrue(report.contains("safe line"))
    }

    @Test
    fun installationKeepsThePreviousHandlerAndDoesNotWrapItself() {
        val original = Thread.getDefaultUncaughtExceptionHandler()
        var delegated = false
        try {
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> delegated = true }
            CrashReportHandler.install(store())
            val installed = Thread.getDefaultUncaughtExceptionHandler()
            assertNotNull(installed)
            CrashReportHandler.install(store())
            assertSame(installed, Thread.getDefaultUncaughtExceptionHandler())
            installed!!.uncaughtException(Thread.currentThread(), IllegalStateException("fixture"))
            assertTrue(delegated)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }
}
