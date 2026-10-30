package com.alal.yft.core.model.media

import com.alal.yft.core.model.download.FileNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.0.2 item 51: a post's video items, their names, and the sheet's groups of them. */
class PostVideoItemTest {
    private val page = "https://www.instagram.com/p/C9fixtSIDE1/?img_index=3"
    private val canonical = "https://www.instagram.com/p/C9fixtSIDE1/"

    private fun row(
        url: String,
        title: String? = "Fixture Creator on Instagram",
        videoId: String? = "instagram:C9fixtSIDE1",
        pageUrl: String = page,
        postItem: PostVideoItem? = null,
        drm: Boolean = false,
    ) = MediaCandidate(
        pageUrl = pageUrl,
        mediaUrl = url,
        sources = setOf(CandidateSource.MANIFEST),
        kind = MediaKind.DIRECT,
        title = title,
        videoId = videoId,
        drmHint = drm,
        postItem = postItem,
    )

    @Test
    fun anItemIsNamedByItsPlaceAmongThePostsVideos() {
        val item = PostVideoItem(number = 2, count = 4)

        assertEquals("Video 2 of 4", item.label)
        assertEquals("Morning swim (Video 2 of 4)", item.title("Morning swim"))
        assertEquals("Video 2 of 4", item.title(null))
        assertEquals("Video 2 of 4", item.title("  "))
        assertEquals("a title named once", "Swim (Video 2 of 4)", item.title("Swim (Video 2 of 4)"))
    }

    @Test
    fun aLongTitleIsCutSoTheFileNameKeepsTheItemsNumber() {
        val long = "A very long caption about a morning swim by the river with friends and family"
        val title = PostVideoItem(3, 12).title(long)

        assertTrue(title, title.endsWith("… (Video 3 of 12)"))
        assertTrue(title, title.length <= PostVideoItem.MAX_TITLE_CHARS + " (Video 3 of 12)".length + 1)
        // Myanmar's letters are three bytes each: the cut keeps whole syllables within 150 bytes.
        val burmese = "မင်္ဂလာပါ".repeat(12)
        val cut = PostVideoItem(1, 2).title(burmese).removeSuffix("… (Video 1 of 2)")
        assertTrue(burmese.startsWith(cut))
        assertTrue(FileNames.utf8Bytes(cut) <= PostVideoItem.MAX_TITLE_BYTES)
        assertFalse("never ends on a stacking virama", cut.endsWith("\u1039"))
        assertEquals("a short title is whole", "Swim (Video 1 of 2)", PostVideoItem(1, 2).title("Swim"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aPostItemIsOneOfSeveral() {
        PostVideoItem(number = 1, count = 1)
    }

    @Test
    fun theGroupsTitleNamesItsItemAndItsRowsOnly() {
        val item = PostVideoItem(2, 3)
        val captioned = MediaGroups.of(
            listOf(row("https://cdn.example.test/a.mp4", title = "Caption — with a dash", postItem = item)),
        ).single()
        val untitled = MediaGroups.of(listOf(row("https://cdn.example.test/b.mp4", title = null, postItem = item)))
            .single()
        val alone = MediaGroups.of(listOf(row("https://cdn.example.test/c.mp4"))).single()

        assertEquals("Caption (Video 2 of 3)", captioned.title)
        assertEquals("Video 2 of 3", untitled.title)
        assertEquals("a video of its own keeps its title", "Fixture Creator on Instagram", alone.title)
    }

    @Test
    fun theSheetOffersTheLinkedItemFirstThenTheOthersInThePostsOrder() {
        val first = PostVideoItem(1, 3)
        val third = PostVideoItem(3, 3)
        val others = listOf(
            row("https://cdn.example.test/v1_720.mp4", videoId = "instagram:C9fixtSIDE1:1", pageUrl = canonical, postItem = first),
            row("https://cdn.example.test/v1_480.mp4", videoId = "instagram:C9fixtSIDE1:1", pageUrl = canonical, postItem = first),
            row("https://cdn.example.test/v3_drm.mp4", videoId = "instagram:C9fixtSIDE1:4", pageUrl = canonical, postItem = third, drm = true),
            row("https://cdn.example.test/v3_720.mp4", videoId = "instagram:C9fixtSIDE1:4", pageUrl = canonical, postItem = third),
        )
        val linked = PostVideoItem(2, 3, others)
        val group = MediaGroups.of(
            listOf(
                row("https://cdn.example.test/v2_1080.mp4", postItem = linked),
                row("https://cdn.example.test/v2_720.mp4", postItem = linked),
            ),
        ).single()

        val items = MediaGroups.postItems(group) { it.drmHint != true }

        assertEquals(
            listOf("Fixture Creator on Instagram (Video 2 of 3)", "Fixture Creator on Instagram (Video 1 of 3)", "Fixture Creator on Instagram (Video 3 of 3)"),
            items.map { it.title },
        )
        assertEquals(group, items.first())
        assertEquals(listOf(2, 2, 1), items.map { it.candidates.size })
        assertTrue("each item on the sheet's page", items.flatMap { it.candidates }.all { it.pageUrl == page })
        assertEquals(listOf("id:instagram:C9fixtSIDE1:1", "id:instagram:C9fixtSIDE1:4"), items.drop(1).map { it.key })
        assertFalse("a row the phone may not offer is left out", items.flatMap { it.candidates }.any { "drm" in it.mediaUrl })

        val nothingKept = MediaGroups.postItems(group) { it.videoId == "instagram:C9fixtSIDE1" }
        assertEquals("an item with no row left is not offered", listOf(group), nothingKept)
        val single = MediaGroups.of(listOf(row("https://cdn.example.test/only.mp4"))).single()
        assertEquals(listOf(single), MediaGroups.postItems(single))
    }

    @Test
    fun aFreshAnswerHoldsTheOtherItemsRowsForTheirDownloads() {
        val other = row(
            "https://cdn.example.test/v1_720.mp4",
            videoId = "instagram:C9fixtSIDE1:1",
            pageUrl = canonical,
            postItem = PostVideoItem(1, 2),
        )
        val rows = listOf(row("https://cdn.example.test/v2.mp4", postItem = PostVideoItem(2, 2, listOf(other))))

        val all = MediaGroups.withPostItems(rows)

        assertEquals(listOf("instagram:C9fixtSIDE1", "instagram:C9fixtSIDE1:1"), all.map { it.videoId })
        assertEquals(page, all.last().pageUrl)
        val video = MediaGroups.pageVideos(all, adapterSite = true)
            .single { group -> group.candidates.any { it.videoId == "instagram:C9fixtSIDE1:1" } }
        assertEquals("Fixture Creator on Instagram (Video 1 of 2)", video.title)
        val plain = listOf(row("https://cdn.example.test/one.mp4"))
        assertEquals(plain, MediaGroups.withPostItems(plain))
        assertNull(plain.single().postItem)
    }

    @Test
    fun itsTextNamesNoLink() {
        val other = row("https://cdn.example.test/v1.mp4?sig=secret", videoId = "instagram:C9fixtSIDE1:1")
        val text = PostVideoItem(2, 2, listOf(other)).toString()

        assertEquals("PostVideoItem(number=2, count=2, others=1)", text)
        assertFalse(text.contains("secret"))
    }
}
