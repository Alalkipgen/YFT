package com.alal.yft.extractor.master.modules.youtube.ladder

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.3 D0, guardrail 11: the census and the weekly check run only while their owner tool is
 * open and after a tap. A static check that their code (the ladder sources and the app's debug
 * census tool) names no API that schedules or keeps work running in the background. Social plan
 * S0c: the site census (Master's `social` sources and the app's debug screen) is held to the same.
 */
class CensusNoBackgroundTest {
    @Test
    fun `the census code uses no background or scheduling API`() {
        val module = File(System.getProperty("user.dir"))
        val ladder = File(module, "src/main/kotlin/com/alal/yft/extractor/master/modules/youtube/ladder")
        val tool = File(module.parentFile, "app/src/debug/java/com/alal/yft/master")
        val files = ladder.listFiles { file -> file.extension == "kt" }.orEmpty().toList() +
            tool.listFiles { file -> file.name.startsWith("ClientCensus") && file.extension == "kt" }.orEmpty()

        assertTrue("census sources not found under $ladder", files.any { it.name == "ClientCensus.kt" })
        assertTrue("census tool not found under $tool", files.any { it.name == "ClientCensusActivity.kt" })
        for (file in files) {
            val code = codeOf(file.readText())
            for (api in FORBIDDEN) assertTrue("${file.name} uses ${api.pattern}", !api.containsMatchIn(code))
        }
        val manifest = File(module.parentFile, "app/src/debug/AndroidManifest.xml").readText()
        for (tag in listOf("<service", "<receiver", "<provider")) assertTrue("debug manifest has $tag", tag !in manifest)
    }

    @Test
    fun `the site census code uses no background or scheduling API`() {
        val module = File(System.getProperty("user.dir"))
        val social = File(module, "src/main/kotlin/com/alal/yft/extractor/master/social")
        val tool = File(module.parentFile, "app/src/debug/java/com/alal/yft/master")
        val files = social.listFiles { file -> file.extension == "kt" }.orEmpty().toList() +
            tool.listFiles { file -> file.name.startsWith("SiteCensus") && file.extension == "kt" }.orEmpty()

        assertTrue("site census sources not found under $social", files.any { it.name == "SiteCensus.kt" })
        assertTrue("site census screen not found under $tool", files.any { it.name == "SiteCensusActivity.kt" })
        assertTrue("its lookup not found under $tool", files.any { it.name == "SiteCensusLookup.kt" })
        for (file in files) {
            val code = codeOf(file.readText())
            for (api in FORBIDDEN) assertTrue("${file.name} uses ${api.pattern}", !api.containsMatchIn(code))
        }
    }

    @Test
    fun `the check sees through comments only`() {
        assertTrue(FORBIDDEN.none { it.containsMatchIn(codeOf("// WorkManager\n/* AlarmManager\n GlobalScope */ val x = 1")) })
        assertTrue(FORBIDDEN.any { it.containsMatchIn(codeOf("val timer = java.util.Timer()")) })
        assertTrue(FORBIDDEN.none { it.containsMatchIn(codeOf("runOnUiThread { }")) })
    }

    /** [text] without comments; string contents are kept (a scheduling call is code either way). */
    private fun codeOf(text: String): String =
        text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ").lines().joinToString("\n") { line ->
            var comment = line.indexOf("//")
            while (comment > 0 && line[comment - 1] == ':') comment = line.indexOf("//", comment + 2)
            if (comment >= 0) line.substring(0, comment) else line
        }

    private companion object {
        val FORBIDDEN: List<Regex> = listOf(
            "WorkManager", "PeriodicWorkRequest", "OneTimeWorkRequest", "CoroutineWorker", "ListenableWorker",
            "AlarmManager", "JobScheduler", "JobService", "JobIntentService", "startForeground", "ForegroundService",
            "BroadcastReceiver", "android\\.app\\.Service", "SyncAdapter", "java\\.util\\.Timer", "\\bTimer\\(",
            "ScheduledExecutor", "scheduleAtFixedRate", "scheduleWithFixedDelay", "GlobalScope", "postDelayed",
            "\\bThread\\(", "setRepeating", "setExactAndAllowWhileIdle", "ProcessLifecycleOwner",
        ).map(::Regex)
    }
}
