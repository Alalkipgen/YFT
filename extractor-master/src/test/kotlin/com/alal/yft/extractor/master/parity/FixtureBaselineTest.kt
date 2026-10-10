package com.alal.yft.extractor.master.parity

import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.NOW
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.layers.LayerStack
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * R1 offline parity: Master's standard stack over main's own site fixtures
 * (`extractor-sites/src/test/resources/fixtures`, passed in by Gradle). Each fixture must keep at
 * least its baseline rows and heights and the same terminal verdict. A new or removed fixture
 * after a main sync fails until the baseline is rewritten on purpose:
 * `./gradlew :extractor-master:test -Pyft.parityBaseline=write`.
 */
class FixtureBaselineTest {
    private val fixtures = System.getProperty("yft.siteFixtures")?.let(::File)
    private val baselineFile = System.getProperty("yft.parityBaselineFile")?.let(::File)

    @Test
    fun `Master keeps at least its baseline on main's site fixtures`() {
        assumeTrue("site fixtures not supplied", fixtures?.isDirectory == true)
        val current = checkNotNull(fixtures).walkTopDown().filter { it.isFile }
            .sortedBy { it.relativeTo(checkNotNull(fixtures)).path }
            .associate { it.relativeTo(checkNotNull(fixtures)).invariantSeparatorsPath to read(it) }
        assertTrue("no site fixtures found", current.isNotEmpty())
        if (System.getProperty("yft.parityBaseline") == "write") {
            checkNotNull(baselineFile).writeText(json(current))
            return
        }
        val baseline = parse(
            checkNotNull(javaClass.getResource("/parity/fixture-baseline.json")).readText(),
        )
        val problems = mutableListOf<String>()
        (baseline.keys - current.keys).forEach { problems += "$it: fixture removed" }
        current.forEach { (name, now) ->
            val then = baseline[name]
            if (then == null) {
                problems += "$name: new fixture without baseline"
                return@forEach
            }
            if (now.rows < then.rows) problems += "$name: rows ${now.rows} < ${then.rows}"
            if (!now.heights.containsAll(then.heights)) {
                problems += "$name: heights ${now.heights} lack ${then.heights - now.heights}"
            }
            if (now.terminal != then.terminal) {
                problems += "$name: terminal ${now.terminal} != ${then.terminal}"
            }
        }
        assertTrue(
            "Offline parity regressed (rewrite only on purpose with " +
                "-Pyft.parityBaseline=write):\n" + problems.joinToString("\n"),
            problems.isEmpty(),
        )
    }

    private data class Result(val rows: Int, val heights: Set<Int>, val terminal: String?)

    private fun read(file: File): Result {
        val site = file.parentFile.name
        val page = PAGES[site] ?: "https://$site.example.test/fixture"
        val body = file.readText()
        val snapshot = if (file.extension == "json") {
            PageSnapshot(page, 1, apiResponses = listOf(body))
        } else {
            PageSnapshot(page, 1, html = body)
        }
        val request = MasterRequest(page, 1, NOW, SiteExtractionFailure.RESPONSE_CHANGED, null)
        val evidence = LayerStack.standard().collect(request, snapshot)
        val media = evidence.candidates.distinctBy { it.mediaUrl }
        return Result(
            media.size,
            media.mapNotNull { it.height }.toSortedSet(),
            evidence.terminalFailure?.name,
        )
    }

    private fun json(results: Map<String, Result>): String = results.entries.joinToString(
        prefix = "{\n", separator = ",\n", postfix = "\n}\n",
    ) { (name, it) ->
        "  \"$name\": {\"rows\": ${it.rows}, \"heights\": ${it.heights.toList()}, " +
            "\"terminal\": ${it.terminal?.let { t -> "\"$t\"" } ?: "null"}}"
    }

    private fun parse(text: String): Map<String, Result> {
        val root = checkNotNull(BoundedJsonParser.parse(text)) as JsonValue.Object
        return root.entries.mapValues { (_, it) ->
            Result(
                checkNotNull(it["rows"].asLongOrNull).toInt(),
                it["heights"].asArrayOrEmpty.mapNotNull { h -> h.asLongOrNull?.toInt() }.toSet(),
                it["terminal"].asStringOrNull,
            )
        }
    }

    private companion object {
        /** Neutral page addresses per site directory: the host is what the layers see. */
        val PAGES = mapOf(
            "facebook" to "https://www.facebook.com/fixture/videos/1/",
            "tiktok" to "https://www.tiktok.com/@fixture/video/1",
            "instagram" to "https://www.instagram.com/reel/fixture/",
            "x" to "https://x.com/fixture/status/1",
            "vimeo" to "https://vimeo.com/1",
            "youtube" to "https://www.youtube.com/watch?v=fixture",
        )
    }
}
