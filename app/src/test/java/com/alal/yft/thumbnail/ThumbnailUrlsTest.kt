package com.alal.yft.thumbnail

import com.alal.yft.feature.quickdownload.QuickDownloadFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThumbnailUrlsTest {
    @Test
    fun aYouTubeVideosPictureFollowsFromItsIdBeforeAnyLookup() {
        assertEquals(
            "https://i.ytimg.com/vi/AAAAAAAAAA1/hqdefault.jpg",
            ThumbnailUrls.youTube("youtube:AAAAAAAAAA1"),
        )
        assertNull(ThumbnailUrls.youTube("youtube:short"))
        assertNull(ThumbnailUrls.youTube("youtube:AAAAAAAAAA1/../x"))
        assertNull(ThumbnailUrls.youTube("facebook:1603698891196107"))
        assertNull(ThumbnailUrls.youTube(null))
    }

    @Test
    fun otherSitesUseThePictureTheLookupFoundOverHttpsOnly() {
        val reel = QuickDownloadFixtures.video(height = 720, videoId = "facebook:1")
            .copy(thumbnailUrl = "https://scontent.example.com/t.jpg")
        assertEquals("https://scontent.example.com/t.jpg", ThumbnailUrls.of(listOf(reel)))
        val plain = reel.copy(thumbnailUrl = "http://scontent.example.com/t.jpg")
        assertNull(ThumbnailUrls.of(listOf(plain)))
        // YouTube's own picture wins over whatever the page offered.
        val youtube = QuickDownloadFixtures.youtube()
        assertEquals(
            "https://i.ytimg.com/vi/fixture0001/hqdefault.jpg",
            ThumbnailUrls.of(youtube.map { it.copy(thumbnailUrl = "https://x.example/p.jpg") }),
        )
    }
}
