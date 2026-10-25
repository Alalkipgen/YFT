package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.RecordingValidator
import com.alal.yft.extractor.master.contract.CountingCapture
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.contract.answering
import com.alal.yft.extractor.master.contract.contractRequest
import com.alal.yft.extractor.master.contract.mainRequest
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S1a (7): the rows bound to the Facebook video ID only when the answer holds one
 * video under it ([FacebookIdentity]). Real shapes (the plugin player, a comet page's video
 * object with its delivery copies and a copy in another script, related videos with their own
 * IDs) keep the video's rows; a missing or renamed ID, a related video's files under the video's
 * ID (no ID, a plain or a numeric ID, renamed keys) and a page with two videos under the ID give
 * no rows, from F1 (the plugin player) and from F2 (main's pages); a post's rows are bound to the
 * video main resolved, a post main did not resolve is not asked about; over the walk's bound the
 * video is not proven. Main's real Facebook adapter over synthetic pages; no live calls.
 */
class FacebookIdentityTest {
    @Test
    fun `real shapes keep the video's rows`() = runTest {
        // F1: the plugin player; its keys renamed (read by shape), another video's item beside.
        val players = listOf(
            plugin(item("video_id" to VIDEO, "hd_src" to hd(VIDEO), "sd_src" to sd(VIDEO))),
            plugin(
                item("video_id" to VIDEO, "hd_src_v2" to hd(VIDEO), "sd_src_v2" to sd(VIDEO)),
                item("video_id" to OTHER_VIDEO, "hd_src_v2" to hd(OTHER_VIDEO), "sd_src_v2" to sd(OTHER_VIDEO)),
            ),
        )
        for (player in players) {
            val validator = RecordingValidator()
            val result = pluginLookup(player, validator) as MasterResult.Success
            assertEquals(MasterStage.CONTRACT, result.stage)
            assertRows(VIDEO, result)
            assertEquals(2, validator.seen.size)
            assertOneVideo(result.result.details)
        }

        // F2: a comet page (keys main no longer knows): the video's object by its type and plain
        // ID, its delivery copies (the legacy fields with the ID, a progressive list), the object
        // again in another script, related videos by their own IDs inside it and beside it.
        val comet = page(
            mapOf(
                "video" to mapOf(
                    "__typename" to "Video",
                    "id" to VIDEO,
                    "browser_native_hd_url_v2" to hd(VIDEO),
                    "browser_native_sd_url_v2" to null,
                    "videoDeliveryLegacyFields" to mapOf(
                        "id" to VIDEO,
                        "browser_native_hd_url_v2" to hd(VIDEO),
                        "browser_native_sd_url_v2" to sd(VIDEO),
                    ),
                    "videoDeliveryResponseFragment" to mapOf(
                        "videoDeliveryResponseResult" to mapOf(
                            "progressive_urls_v2" to listOf(
                                mapOf("progressive_url_v2" to hd(VIDEO), "failure_reason" to null, "metadata" to mapOf("quality" to "HD")),
                                mapOf("progressive_url_v2" to sd(VIDEO), "failure_reason" to null, "metadata" to mapOf("quality" to "SD")),
                            ),
                        ),
                    ),
                    "owner" to mapOf("__typename" to "User", "id" to OWNER, "name" to "Fixture"),
                    "related" to listOf(
                        mapOf("__typename" to "Video", "id" to OTHER_VIDEO, "browser_native_hd_url_v2" to hd(OTHER_VIDEO)),
                    ),
                ),
                "related" to listOf(
                    mapOf("video_id" to OTHER_VIDEO, "browser_native_hd_url_v2" to hd(OTHER_VIDEO), "browser_native_sd_url_v2" to sd(OTHER_VIDEO)),
                ),
            ),
            mapOf(
                "video" to mapOf(
                    "__typename" to "Video",
                    "id" to VIDEO,
                    "browser_native_hd_url_v2" to hd(VIDEO),
                    "browser_native_sd_url_v2" to sd(VIDEO),
                ),
            ),
        )
        val validator = RecordingValidator()
        val result = pageLookup(comet, validator) as MasterResult.Success
        assertEquals(MasterStage.PAGE_DATA, result.stage)
        assertRows(VIDEO, result)
        assertEquals(2, validator.seen.size)
        assertTrue(result.result.details.toString(), "$F2: next: CHECK_ROWS" in result.result.details)
        assertOneVideo(result.result.details)
    }

    @Test
    fun `a missing or renamed video ID gives no rows`() = runTest {
        // F1: no ID at all; the ID's key renamed; the ID naming another video.
        val players = listOf(
            plugin(item("hd_src" to hd(VIDEO), "sd_src" to sd(VIDEO)), config = emptyMap()),
            plugin(item("videoID" to VIDEO, "hd_src" to hd(VIDEO), "sd_src" to sd(VIDEO)), config = mapOf("videoID" to VIDEO)),
            plugin(item("video_id" to OTHER_VIDEO, "hd_src" to hd(VIDEO), "sd_src" to sd(VIDEO)), config = mapOf("video_id" to OTHER_VIDEO)),
        )
        for (player in players) {
            val validator = RecordingValidator()
            assertNoRows(pluginLookup(player, validator), "$F1: SHAPE_CHANGED (no rows; no route); next: NEXT_RUNG")
            assertTrue(validator.seen.isEmpty())
        }
        // Only the item's ID missing, the player naming the video: two files under no ID of
        // their own are not proven the video's.
        val validator = RecordingValidator()
        assertNoRows(
            pluginLookup(plugin(item("hd_src" to hd(VIDEO), "sd_src" to sd(VIDEO))), validator),
            "$F1: $TWO",
            "$F1: SHAPE_CHANGED (no rows; no route); next: NEXT_RUNG",
        )
        assertTrue(validator.seen.isEmpty())

        // F2: main's page with the video's object without its ID, its key renamed, naming another video.
        val pages = listOf(
            mapOf("browser_native_hd_url_v2" to hd(VIDEO), "browser_native_sd_url_v2" to sd(VIDEO)),
            mapOf("videoID" to VIDEO, "browser_native_hd_url_v2" to hd(VIDEO), "browser_native_sd_url_v2" to sd(VIDEO)),
            mapOf("video_id" to OTHER_VIDEO, "browser_native_hd_url_v2" to hd(VIDEO), "browser_native_sd_url_v2" to sd(VIDEO)),
        )
        for (video in pages) {
            val checked = RecordingValidator()
            assertNoRows(
                pageLookup(page(mapOf("video" to video)), checked),
                "$F2: page 1: SHAPE_CHANGED (no rows; no route)",
                "$F2: next: PLAYBACK",
            )
            assertTrue(checked.seen.isEmpty())
        }
    }

    @Test
    fun `a related video's files under the video's ID give no rows`() = runTest {
        // F1: the player's item names the video, a related video's files inside it: without an
        // ID (one file, two), with a plain ID, with a numeric video ID, under renamed keys.
        val related = listOf(
            listOf(mapOf("hd_src" to hd(OTHER_VIDEO))),
            listOf(mapOf("hd_src" to hd(OTHER_VIDEO), "sd_src" to sd(OTHER_VIDEO))),
            listOf(mapOf("id" to OTHER_VIDEO, "hd_src" to hd(OTHER_VIDEO), "sd_src" to sd(OTHER_VIDEO))),
            listOf(mapOf("video_id" to OTHER_VIDEO.toLong(), "hd_src" to hd(OTHER_VIDEO), "sd_src" to sd(OTHER_VIDEO))),
            listOf(mapOf("hd_src_v2" to hd(OTHER_VIDEO), "sd_src_v2" to sd(OTHER_VIDEO))),
        )
        for (list in related) {
            val validator = RecordingValidator()
            val player = plugin(item("video_id" to VIDEO, "hd_src" to null, "sd_src" to null, "related" to list))
            assertNoRows(
                pluginLookup(player, validator),
                "$F1: $TWO",
                "$F1: SHAPE_CHANGED (no rows; no route); next: NEXT_RUNG",
            )
            assertTrue(validator.seen.isEmpty())
        }

        // F2: main's page (keys main no longer knows), the video's object with a related video's
        // files inside it: without an ID beside an empty key, with a plain ID, with a numeric
        // video ID, under renamed keys, in an object under the video's own key.
        val videos = listOf(
            mapOf("video_id" to VIDEO, "browser_native_hd_url_v2" to null, "related" to listOf(mapOf("browser_native_hd_url_v2" to hd(OTHER_VIDEO)))),
            mapOf("video_id" to VIDEO, "browser_native_hd_url_v2" to hd(VIDEO), "related" to listOf(mapOf("id" to OTHER_VIDEO, "browser_native_hd_url_v2" to hd(OTHER_VIDEO)))),
            mapOf("video_id" to VIDEO, "browser_native_hd_url_v2" to hd(VIDEO), "related" to listOf(mapOf("video_id" to OTHER_VIDEO.toLong(), "browser_native_hd_url_v2" to hd(OTHER_VIDEO)))),
            mapOf("video_id" to VIDEO, "browser_native_hd_url_v2" to hd(VIDEO), "related" to listOf(mapOf("hd_v3" to hd(OTHER_VIDEO), "sd_v3" to sd(OTHER_VIDEO)))),
            mapOf("video_id" to VIDEO, "browser_native_hd_url_v2" to hd(VIDEO), "next" to mapOf("browser_native_hd_url_v2" to hd(OTHER_VIDEO))),
        )
        for (video in videos) {
            val validator = RecordingValidator()
            assertNoRows(
                pageLookup(page(mapOf("video" to video)), validator),
                "$F2: page 1: $TWO",
                "$F2: page 1: SHAPE_CHANGED (no rows; no route)",
                "$F2: next: PLAYBACK",
            )
            assertTrue(validator.seen.isEmpty())
        }
    }

    @Test
    fun `a page with two videos under the video ID gives no rows`() = runTest {
        // F1: two items claim the video with other files; whole videos listed in its item; its
        // own files with a list of another video's two files.
        val players = listOf(
            plugin(
                item("video_id" to VIDEO, "hd_src" to hd(VIDEO), "sd_src" to sd(VIDEO)),
                item("video_id" to VIDEO, "hd_src" to hd(OTHER_VIDEO), "sd_src" to sd(OTHER_VIDEO)),
            ),
            plugin(
                item(
                    "video_id" to VIDEO, "hd_src" to null, "sd_src" to null,
                    "items" to listOf(
                        mapOf("hd_src" to hd(OTHER_VIDEO), "sd_src" to sd(OTHER_VIDEO)),
                        mapOf("hd_src" to hd(THIRD_VIDEO), "sd_src" to sd(THIRD_VIDEO)),
                    ),
                ),
            ),
            plugin(
                item(
                    "video_id" to VIDEO, "hd_src" to hd(VIDEO), "sd_src" to sd(VIDEO),
                    "related" to listOf(mapOf("hd_src" to hd(OTHER_VIDEO), "sd_src" to sd(OTHER_VIDEO))),
                ),
            ),
        )
        for (player in players) {
            val validator = RecordingValidator()
            assertNoRows(
                pluginLookup(player, validator),
                "$F1: $TWO",
                "$F1: SHAPE_CHANGED (no rows; no route); next: NEXT_RUNG",
            )
            assertTrue(validator.seen.isEmpty())
        }

        // F2: two objects claim the video with other files (in one script, in two); whole videos
        // listed under its object.
        val pages = listOf(
            page(
                mapOf(
                    "a" to mapOf("video_id" to VIDEO, "browser_native_hd_url_v2" to hd(VIDEO)),
                    "b" to mapOf("video_id" to VIDEO, "browser_native_hd_url_v2" to hd(OTHER_VIDEO)),
                ),
            ),
            page(
                mapOf("video" to mapOf("video_id" to VIDEO, "browser_native_hd_url_v2" to hd(VIDEO))),
                mapOf("video" to mapOf("video_id" to VIDEO, "browser_native_hd_url_v2" to hd(OTHER_VIDEO))),
            ),
            page(
                mapOf(
                    "video" to mapOf(
                        "video_id" to VIDEO,
                        "items" to listOf(
                            mapOf("browser_native_hd_url_v2" to hd(OTHER_VIDEO), "browser_native_sd_url_v2" to sd(OTHER_VIDEO)),
                            mapOf("browser_native_hd_url_v2" to hd(THIRD_VIDEO), "browser_native_sd_url_v2" to sd(THIRD_VIDEO)),
                        ),
                    ),
                ),
            ),
        )
        for (body in pages) {
            val validator = RecordingValidator()
            assertNoRows(
                pageLookup(body, validator),
                "$F2: page 1: $TWO",
                "$F2: page 1: SHAPE_CHANGED (no rows; no route)",
                "$F2: next: PLAYBACK",
            )
            assertTrue(validator.seen.isEmpty())
        }
    }

    @Test
    fun `a post's rows are bound to the video main resolved, and its decoys give none`() = runTest {
        val share = identify(SHARE)
        val reel = identify(REEL)
        // Main's page for the share link redirected to the reel (keys main no longer knows).
        fun mainPage(body: String) = FakeExtractorHttpClient(
            responses = mapOf(share.canonicalPageUrl to ExtractorHttpResult.Success(200, body, REEL, HTML)),
        )
        val own = page(
            mapOf(
                "video" to mapOf(
                    "video_id" to REEL_VIDEO,
                    "browser_native_hd_url_v2" to hd(REEL_VIDEO),
                    "browser_native_sd_url_v2" to sd(REEL_VIDEO),
                ),
            ),
        )
        val noData = answering(embedUrl(reel) to NO_DATA, contentType = HTML)
        val found = lookup(SHARE, mainPage(own), noData) as MasterResult.Success
        assertEquals(MasterStage.PAGE_DATA, found.stage)
        assertRows(REEL_VIDEO, found)
        assertEquals(listOf(embedUrl(reel)), noData.requestedUrls)
        assertOneVideo(found.result.details)

        // The reel's object with a related video's files inside it: on main's page, in the
        // plugin player's answer for the reel.
        val decoy = page(
            mapOf(
                "video" to mapOf(
                    "video_id" to REEL_VIDEO,
                    "browser_native_hd_url_v2" to null,
                    "related" to listOf(mapOf("browser_native_hd_url_v2" to hd(OTHER_VIDEO), "browser_native_sd_url_v2" to sd(OTHER_VIDEO))),
                ),
            ),
        )
        val validator = RecordingValidator()
        assertNoRows(
            lookup(SHARE, mainPage(decoy), answering(embedUrl(reel) to NO_DATA, contentType = HTML), validator),
            "$F2: page 1: $TWO",
            "$F2: next: PLAYBACK",
        )
        val player = plugin(
            item(
                "video_id" to REEL_VIDEO, "hd_src" to null, "sd_src" to null,
                "related" to listOf(mapOf("hd_src" to hd(OTHER_VIDEO), "sd_src" to sd(OTHER_VIDEO))),
            ),
            config = mapOf("video_id" to REEL_VIDEO),
        )
        assertNoRows(
            lookup(SHARE, mainPage(NO_DATA), answering(embedUrl(reel) to player, contentType = HTML), validator),
            "$F1: $TWO",
            "$F1: SHAPE_CHANGED (no rows; no route); next: NEXT_RUNG",
        )
        assertTrue(validator.seen.isEmpty())
    }

    @Test
    fun `a post page with two videos is not asked about and gives no rows`() = runTest {
        val post = identify(POST)
        // The page stays on the post's own address and lists two videos (keys main no longer knows).
        val twoVideos = page(
            mapOf("video" to mapOf("video_id" to VIDEO, "browser_native_hd_url_v2" to hd(VIDEO), "browser_native_sd_url_v2" to sd(VIDEO))),
            mapOf("video" to mapOf("video_id" to OTHER_VIDEO, "browser_native_hd_url_v2" to hd(OTHER_VIDEO), "browser_native_sd_url_v2" to sd(OTHER_VIDEO))),
        )
        val main = answering(post.canonicalPageUrl to twoVideos, contentType = HTML)
        val plugin = answering(
            embedUrl(identify(WATCH)) to plugin(item("video_id" to VIDEO, "hd_src" to hd(VIDEO), "sd_src" to sd(VIDEO))),
            contentType = HTML,
        )
        val validator = RecordingValidator()
        val capture = CountingCapture()

        val result = lookup(POST, main, plugin, validator, capture)

        assertNoRows(
            result,
            "$LADDER: a link main resolves first, no video in main's answer; not asked",
            "$F2: next: PLAYBACK",
        )
        assertTrue(plugin.requestedUrls.isEmpty())
        assertTrue(validator.seen.isEmpty())
        assertEquals(1, capture.calls)
    }

    @Test
    fun `over the walk's bound the video is not proven, and JSON texts inside are walked`() {
        val document = json(mapOf("video" to mapOf("video_id" to VIDEO, "hd_src" to hd(VIDEO), "sd_src" to sd(VIDEO))))
        val rows = listOf(hd(VIDEO), sd(VIDEO))
        assertEquals(FacebookIdentity.Reading.ONE_VIDEO, FacebookIdentity.read(listOf(document), VIDEO, rows))
        assertEquals(FacebookIdentity.Reading.OVER_BOUND, FacebookIdentity.read(listOf(document), VIDEO, rows, maxNodes = 4))
        assertEquals("the video ID not proven (over the walk's bound); no rows", FacebookIdentity.Reading.OVER_BOUND.line)
        assertEquals(TWO, FacebookIdentity.Reading.TWO_VIDEOS.line)

        // A related list inside a JSON text the video's object holds: walked too.
        val inner = json(mapOf("related" to listOf(mapOf("hd_src" to hd(OTHER_VIDEO), "sd_src" to sd(OTHER_VIDEO)))))
        val nested = json(mapOf("video" to mapOf("video_id" to VIDEO, "hd_src" to hd(VIDEO), "extra" to inner)))
        assertEquals(FacebookIdentity.Reading.TWO_VIDEOS, FacebookIdentity.read(listOf(nested), VIDEO, listOf(hd(VIDEO))))

        // A file is a media file's https address, compared by its path; a manifest or an image is none.
        assertEquals("/v/$VIDEO-hd.mp4", FacebookIdentity.filePath(hd(VIDEO)))
        assertNull(FacebookIdentity.filePath("https://video.example-cdn.test/v/$VIDEO.mpd?oe=F2A52380"))
        assertNull(FacebookIdentity.filePath("https://video.example-cdn.test/v/$VIDEO.jpg"))
        assertNull(FacebookIdentity.filePath("http://video.example-cdn.test/v/$VIDEO-hd.mp4"))
    }

    /** F1 alone: the plugin player's [answer] for the watch link's video; main read no page. */
    private suspend fun pluginLookup(answer: String, validator: RecordingValidator): MasterResult {
        val watch = identify(WATCH)
        val http = answering(embedUrl(watch) to answer, contentType = HTML)
        val result = withContext(record() + MainPages()) {
            engine(http, validator).extract(contractRequest(WATCH, watch, cookie = COOKIE))
        }
        assertEquals(listOf(embedUrl(watch)), http.requestedUrls)
        return result
    }

    /** F2: main's page [body] for the watch link on its own address; the plugin player has no data. */
    private suspend fun pageLookup(body: String, validator: RecordingValidator): MasterResult {
        val watch = identify(WATCH)
        val main = answering(watch.canonicalPageUrl to body, contentType = HTML)
        val plugin = answering(embedUrl(watch) to NO_DATA, contentType = HTML)
        val result = lookup(WATCH, main, plugin, validator)
        assertEquals(listOf(watch.canonicalPageUrl), main.requestedUrls)
        assertEquals(listOf(embedUrl(watch)), plugin.requestedUrls)
        return result
    }

    /** One lookup: main's real adapter over [main] (its pages kept in the lookup), then Master's engine. */
    private suspend fun lookup(
        link: String,
        main: FakeExtractorHttpClient,
        plugin: FakeExtractorHttpClient,
        validator: RecordingValidator = RecordingValidator(),
        capture: CountingCapture = CountingCapture(),
    ): MasterResult {
        val identity = identify(link)
        return withContext(record() + MainPages()) {
            val answer = FacebookExtractor(MainPageRecorder(LookupRecorder(main))).extract(mainRequest(identity, COOKIE))
            val failure = (answer as SiteExtractionResult.Failure).reason
            engine(plugin, validator, capture).extract(contractRequest(link, identity, failure, COOKIE))
        }
    }

    private fun engine(
        http: FakeExtractorHttpClient,
        validator: RecordingValidator,
        capture: CountingCapture = CountingCapture(),
    ) = MasterFallbackEngine(
        validator, capture, MasterPolicy(enabled = true),
        contracts = SiteContracts(http),
        social = FacebookLadder(http, ::identifyOrNull),
    )

    private fun record() = LookupRecord(labels = SiteWays.FACEBOOK.labels())

    private fun assertRows(video: String, result: MasterResult.Success) {
        val rows = result.result.candidates
        assertEquals(setOf(hd(video), sd(video)), rows.map { it.mediaUrl }.toSet())
        rows.forEach { assertEquals("facebook:$video", it.videoId) }
    }

    private fun assertOneVideo(details: List<String>) {
        assertFalse(details.toString(), details.any { it.endsWith(TWO) || it.endsWith(OVER) })
        assertBare(details)
    }

    /** No Master row (the tab's own playback next), with [lines] among the lines. */
    private fun assertNoRows(result: MasterResult, vararg lines: String) {
        val details = (result as MasterResult.NeedsPlayback).details
        lines.forEach { assertTrue(details.toString(), it in details) }
        assertBare(details)
    }

    /** No ID, link or page words in the lines. */
    private fun assertBare(details: List<String>) {
        details.forEach { line ->
            listOf(VIDEO, REEL_VIDEO, OTHER_VIDEO, THIRD_VIDEO, OWNER, POST_ID, SHARE_CODE, "facebook.com", "example-cdn", "Fixture").forEach {
                assertFalse("$line has $it", line.contains(it))
            }
        }
    }

    private companion object {
        const val VIDEO = "1000000000000003"
        const val REEL_VIDEO = "1000000000000004"
        const val OTHER_VIDEO = "1000000000000009"
        const val THIRD_VIDEO = "1000000000000008"
        const val OWNER = "1000000000000077"
        const val POST_ID = "1000000000000006"
        const val SHARE_CODE = "FixShare7"
        const val WATCH = "https://www.facebook.com/watch/?v=$VIDEO"
        const val REEL = "https://www.facebook.com/reel/$REEL_VIDEO"
        const val SHARE = "https://www.facebook.com/share/v/$SHARE_CODE/"
        const val POST = "https://www.facebook.com/fixturepage/posts/$POST_ID"
        const val COOKIE = "c_user=0; xs=fixture-cookie"
        const val HTML = "text/html"
        const val LADDER = "master: facebook ladder"
        const val F1 = "master: facebook F1 plugin player"
        const val F2 = "master: facebook F2 main's pages"
        const val TWO = "two videos under the video ID; no rows"
        const val OVER = "the video ID not proven (over the walk's bound); no rows"
        const val NO_DATA = "<html><head><title>Facebook</title></head><body>no data</body></html>"

        fun identifyOrNull(url: String): SitePageIdentity? = FacebookExtractor(FakeExtractorHttpClient()).identify(url)

        fun identify(url: String): SitePageIdentity = checkNotNull(identifyOrNull(url))

        fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

        fun embedUrl(identity: SitePageIdentity) =
            "https://www.facebook.com/plugins/video.php?href=" + encode(identity.canonicalPageUrl)

        fun hd(video: String) = "https://video.example-cdn.test/v/$video-hd.mp4?oe=F2A52380&oh=fixture"

        fun sd(video: String) = "https://video.example-cdn.test/v/$video-sd.mp4?oe=F2A52380&oh=fixture"

        /** [value] as JSON text: maps (in their order), lists, strings, numbers, booleans, null. */
        fun json(value: Any?): String = when (value) {
            null -> "null"
            is String -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
            is Number, is Boolean -> value.toString()
            is Map<*, *> -> value.entries.joinToString(",", "{", "}") { json(it.key.toString()) + ":" + json(it.value) }
            is List<*> -> value.joinToString(",", "[", "]") { json(it) }
            else -> error("not JSON: $value")
        }

        /** One of the plugin player's `videoData` items (FacebookContractTest's shape) with [fields]. */
        fun item(vararg fields: Pair<String, Any?>): Map<String, Any?> = linkedMapOf<String, Any?>("is_hls" to false).apply {
            putAll(fields)
            put("dash_manifest", null)
            put("videoLicenseUriMap", emptyMap<String, Any?>())
            put("graphApiVideoLicenseUri", null)
        }

        /** The plugin player's page: its loader call with the video's config ([config]) and [items]. */
        fun plugin(vararg items: Map<String, Any?>, config: Map<String, Any?> = mapOf("video_id" to VIDEO)): String {
            val instance = linkedMapOf<String, Any?>("ad_client_token" to null).apply {
                putAll(config)
                put("videoData", items.toList())
            }
            return "<!DOCTYPE html><html><head><title>Facebook</title></head><body>" +
                "<script nonce=\"fixture\">requireLazy([\"TimeSliceImpl\",\"ServerJS\"],function(TimeSlice,ServerJS){" +
                "var s=(new ServerJS());s.handle({\"instances\":[[\"__inst_fixture\",[\"VideoConfig\"],[" +
                json(instance).replace("/", "\\/") + "],1]]});});</script></body></html>"
        }

        /** Main's page: one data script for each of [payloads]. */
        fun page(vararg payloads: Map<String, Any?>): String =
            "<html><head><title>Facebook</title></head><body>" +
                payloads.joinToString("") {
                    "<script type=\"application/json\" data-sjs>{\"require\":[[\"VideoPlayerRelay\",null,null,[" +
                        json(it) + "]]]}</script>"
                } +
                "</body></html>"
    }
}
