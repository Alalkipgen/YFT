package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.master.social.SiteCensusDiff.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan 0.4 (b): the site census diff ([SiteCensusDiff]) over two made-up exports: one line
 * per slot that changed, BREAKING for a worsening and CHANGED for any other difference; notes for
 * what was not compared; an older export's missing fields are not compared. No live calls.
 */
class SiteCensusDiffTest {
    @Test
    fun `a first run, a file that is no site census export, and the same run twice`() {
        val run = export(site("x", "X", row(1)))

        assertEquals("FIRST RUN — nothing to compare yet", SiteCensusDiff.compare(null, run).text())
        assertEquals(
            "NOT COMPARED — the earlier file is not a site census export",
            SiteCensusDiff.compare("{", run).headline,
        )
        assertEquals(
            "a YouTube census export is not one",
            "NOT COMPARED — the earlier file is not a site census export",
            SiteCensusDiff.compare(run.replace("YFT site census", "YFT client census"), run).headline,
        )
        assertEquals(
            "another format is not one either",
            "NOT COMPARED — the earlier file is not a site census export",
            SiteCensusDiff.compare(run.replace("\"fmt\":1", "\"fmt\":2"), run).headline,
        )
        assertEquals("NOT COMPARED — the later file is not a site census export", SiteCensusDiff.compare(run, "[]").headline)
        val same = SiteCensusDiff.compare(run, run)
        assertEquals("NO CHANGE — against the run of $DATE", same.headline)
        assertTrue(same.lines.isEmpty() && same.notes.isEmpty())
    }

    @Test
    fun `a worsening is BREAKING - main's rows gone, as expected no longer, V pass to fail, Master's recovery lost, a site stop`() {
        val before = export(
            site(
                "x", "X",
                row(1),
                row(2),
                row(3, cls = "recoverable", expected = false, note = "rows wanted", answer = "failed", reason = "RESPONSE_CHANGED",
                    v = null, master = master("route B", rows = 1, v = "pass")),
                row(8, kind = "removed", cls = "final", answer = "failed", reason = "PRIVATE_OR_UNAVAILABLE", v = null,
                    master = master("trial", answer = "failed", reason = "NO_MEDIA_FOUND")),
            ),
        )
        val after = export(
            site(
                "x", "X",
                row(1, cls = "recoverable", expected = false, note = "rows wanted", answer = "failed", reason = "RESPONSE_CHANGED",
                    v = null, master = master("route B", answer = "failed", reason = "NO_MEDIA_FOUND")),
                row(2, v = "fail"),
                row(3, cls = "recoverable", expected = false, note = "rows wanted", answer = "failed", reason = "RESPONSE_CHANGED",
                    v = null, master = master("route B", rows = 1, v = "fail")),
                row(8, kind = "removed", cls = "rows", expected = false, note = "rows found", v = "pass"),
                stop = "429",
            ),
        )

        val diff = SiteCensusDiff.compare(before, after)

        assertEquals("BREAKING (5) — against the run of $DATE", diff.headline)
        assertEquals(5, diff.breaking)
        assertEquals(
            listOf(
                "BREAKING X: stopped (429)",
                "BREAKING X slot 1 (video): main rows 3 (tallest 1280) [rows] → failed RESPONSE_CHANGED [recoverable]; " +
                    "as expected → not as expected (rows wanted); V pass → not run; " +
                    "Master trial: rows 3, V pass → route B: failed NO_MEDIA_FOUND",
                "BREAKING X slot 2 (video): V pass → fail",
                "BREAKING X slot 3 (video): Master route B: rows 1, V pass → route B: rows 1, V fail",
                "BREAKING X slot 8 (removed): main failed PRIVATE_OR_UNAVAILABLE [final] → rows 3 (tallest 1280) [rows]; " +
                    "as expected → not as expected (rows found); V not run → pass; " +
                    "Master trial: failed NO_MEDIA_FOUND → trial: rows 3, V pass",
            ),
            diff.lines.map(SiteCensusDiff.Line::render),
        )
    }

    @Test
    fun `any other difference is CHANGED, one line per slot`() {
        val before = export(
            site(
                "x", "X",
                row(3, cls = "recoverable", expected = false, note = "rows wanted", answer = "failed", reason = "RESPONSE_CHANGED",
                    v = null, master = master("route B", answer = "failed", reason = "NO_MEDIA_FOUND")),
                row(6, kind = "several videos", rows = 2, tallest = 720, bestS = "12.0", item = 1 to 2),
                row(5, kind = "quoted post's video"),
                stop = "budget",
            ),
            site(
                "instagram", "Instagram",
                row(10, kind = "removed", cls = "browser", expected = false, note = "another answer", answer = "failed",
                    reason = "LOGIN_REQUIRED", status = 401, v = null, master = master("not run", why = "browser-required")),
            ),
        )
        val after = export(
            site(
                "x", "X",
                row(3, master = master("trial", rows = 3, v = "pass")),
                row(6, kind = "several videos", rows = 2, tallest = 1280, bestS = "9.0", item = 2 to 2),
                row(5, kind = "quoted post's video", session = true),
            ),
            site(
                "instagram", "Instagram",
                row(10, kind = "removed", cls = "browser", expected = null, ambiguous = true, note = SiteCensus.AMBIGUOUS,
                    answer = "failed", reason = "LOGIN_REQUIRED", status = 401, v = null,
                    master = master("not run", why = "browser-required")),
            ),
        )

        val diff = SiteCensusDiff.compare(before, after)

        assertEquals("CHANGED (5) — against the run of $DATE", diff.headline)
        assertEquals(0, diff.breaking)
        assertTrue(diff.lines.all { it.level == Level.CHANGED })
        assertEquals(
            listOf(
                "CHANGED X: no stop (the earlier run stopped: budget)",
                "CHANGED X slot 3 (video): main failed RESPONSE_CHANGED [recoverable] → rows 3 (tallest 1280) [rows]; " +
                    "not as expected (rows wanted) → as expected; V not run → pass; " +
                    "Master route B: failed NO_MEDIA_FOUND → trial: rows 3, V pass",
                "CHANGED X slot 5 (quoted post's video): session no → yes",
                "CHANGED X slot 6 (several videos): main rows 2 (tallest 720) [rows] → rows 2 (tallest 1280) [rows]; " +
                    "item 1 of 2 → 2 of 2; best 12.0 s → 9.0 s",
                "CHANGED Instagram slot 10 (removed): not as expected (another answer) → ambiguous (no session)",
            ),
            diff.lines.map(SiteCensusDiff.Line::render),
        )
    }

    @Test
    fun `what was not compared is a note - a site or slot in one run only, a skipped site or row`() {
        val before = export(
            site("facebook", "Facebook", row(1)),
            site("tiktok", "TikTok", row(1), row(2)),
            site("x", "X", row(1), row(4, kind = "GIF")),
        )
        val after = export(
            site("tiktok", "TikTok", skipped = "cooldown 20 min (bot check)"),
            site("x", "X", row(1, skipped = "site stopped (429)"), row(2)),
            site("instagram", "Instagram", row(1)),
        )

        val diff = SiteCensusDiff.compare(before, after)

        assertEquals("NO CHANGE — against the run of $DATE", diff.headline)
        assertEquals(
            listOf(
                "TikTok: skipped in the later run (cooldown 20 min (bot check))",
                "X slot 1 (video): not looked up in the later run (site stopped (429))",
                "X slot 2 (video): only in the later run",
                "X slot 4 (GIF): only in the earlier run",
                "Instagram: only in the later run",
                "Facebook: only in the earlier run",
            ),
            diff.notes,
        )
        assertTrue(diff.text(), diff.text().lines().drop(1).all { it.startsWith("note: ") })
    }

    @Test
    fun `an older export without the 0_4 fields compares what both hold`() {
        val older = export(site("x", "X", row(6, kind = "several videos"), row(1)))
        val newer = export(
            site(
                "x", "X",
                row(6, kind = "several videos", bestS = "9.0", item = 2 to 2, setupMs = 40, ms = 1_240),
                row(1, rows = 4),
            ),
            date = "2026-10-27 10:00 UTC",
        )

        val diff = SiteCensusDiff.compare(older, newer)

        assertEquals("CHANGED (1) — against the run of $DATE", diff.headline)
        assertEquals(
            listOf("CHANGED X slot 1 (video): main rows 3 (tallest 1280) [rows] → rows 4 (tallest 1280) [rows]"),
            diff.lines.map(SiteCensusDiff.Line::render),
        )
        assertTrue(diff.notes.isEmpty())
    }

    private fun export(vararg sites: String, date: String = DATE): String = SiteCensusJson.obj {
        field("tool", SiteCensus.TOOL_LABEL)
        field("fmt", SiteCensusState.FORMAT)
        field("at", 1_791_000_000_000L)
        field("date", date)
        field("ms", 1_000)
        strings("notes", emptyList())
        list("sites", sites.toList())
    }

    private fun site(id: String, name: String, vararg rows: String, stop: String? = null, skipped: String? = null): String =
        SiteCensusJson.obj {
            field("site", id)
            field("name", name)
            field("skipped", skipped)
            field("stop", stop)
            field("req", 10)
            list("rows", rows.toList())
        }

    private fun row(
        slot: Int,
        kind: String = "video",
        session: Boolean = false,
        cls: String = "rows",
        expected: Boolean? = true,
        ambiguous: Boolean = false,
        note: String? = null,
        answer: String = "rows",
        reason: String? = null,
        status: Int? = null,
        rows: Int = 3,
        tallest: Int? = 1280,
        bestS: String? = null,
        item: Pair<Int, Int>? = null,
        v: String? = "pass",
        master: String? = master("trial", rows = 3, v = "pass"),
        skipped: String? = null,
        setupMs: Long? = null,
        ms: Long? = null,
    ): String = SiteCensusJson.obj {
        field("slot", slot)
        field("kind", kind)
        field("session", session)
        field("skipped", skipped)
        if (skipped != null) return@obj
        field("class", cls)
        field("expected", expected)
        if (ambiguous) field("ambiguous", true)
        field("note", note)
        field("req", 5)
        field("setupMs", setupMs)
        field("ms", ms)
        raw(
            "main",
            SiteCensusJson.obj {
                field("answer", answer)
                field("reason", reason)
                field("status", status)
                field("rows", if (answer == "rows") rows else 0)
                field("tallest", tallest.takeIf { answer == "rows" })
                bestS?.let { raw("bestS", it) }
                field("item", item?.first)
                field("items", item?.second)
                field("ms", 900)
            },
        )
        v?.let { raw("v", SiteCensusJson.obj { field("verdict", it) }) }
        master?.let { raw("master", it) }
    }

    private fun master(run: String, answer: String = "rows", rows: Int = 0, reason: String? = null, v: String? = null, why: String? = null) =
        SiteCensusJson.obj {
            field("run", run)
            field("why", why)
            if (run == "not run") return@obj
            field("answer", answer)
            field("reason", reason)
            field("rows", rows)
            v?.let { raw("v", SiteCensusJson.obj { field("verdict", it) }) }
        }

    private companion object {
        const val DATE = "2026-10-20 10:00 UTC"
    }
}
