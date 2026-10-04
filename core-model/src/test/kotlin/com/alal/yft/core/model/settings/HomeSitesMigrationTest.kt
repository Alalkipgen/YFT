package com.alal.yft.core.model.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeSitesMigrationTest {
    private val youtube = HomeSite("YouTube", "https://m.youtube.com")
    private val facebook = HomeSite("Facebook", "https://m.facebook.com")
    private val tiktok = HomeSite("TikTok", "https://www.tiktok.com")

    @Test
    fun defaultsAreYouTubeFacebookAndTikTok() {
        assertEquals(listOf(youtube, facebook, tiktok), HomeSites.DEFAULTS)
        assertEquals(2, HomeSites.DEFAULTS_VERSION)
    }

    @Test
    fun untouchedOldDefaultsBecomeTheNewDefaults() {
        assertEquals(HomeSites.DEFAULTS, HomeSites.migrateDefaults(HomeSites.LEGACY_DEFAULTS))
    }

    @Test
    fun ownersCaseKeepsHisYouTubeWithoutADuplicate() {
        val own = HomeSite("YouTube", "https://www.youtube.com")
        val stored = HomeSites.LEGACY_DEFAULTS + own

        assertEquals(listOf(own, facebook, tiktok), HomeSites.migrateDefaults(stored))
    }

    @Test
    fun userSitesAreKeptAfterTheNewDefaults() {
        val docs = HomeSite("Docs", "https://docs.test")
        val stored = listOf(HomeSites.LEGACY_DEFAULTS[0], docs, HomeSites.LEGACY_DEFAULTS[2])

        assertEquals(listOf(youtube, facebook, tiktok, docs), HomeSites.migrateDefaults(stored))
    }

    @Test
    fun aRenamedOldDefaultCountsAsTheUsersOwnSite() {
        val renamed = HomeSite("Old films", "https://archive.org")
        val stored = listOf(renamed, HomeSites.LEGACY_DEFAULTS[1])

        assertEquals(
            listOf(youtube, facebook, tiktok, renamed),
            HomeSites.migrateDefaults(stored),
        )
    }

    @Test
    fun aCustomListAndAnEmptyListStayAsTheyAre() {
        val custom = listOf(
            HomeSite("Docs", "https://docs.test"),
            HomeSite("Vimeo", "https://vimeo.com"),
        )

        assertEquals(custom, HomeSites.migrateDefaults(custom))
        assertEquals(emptyList<HomeSite>(), HomeSites.migrateDefaults(emptyList()))
    }

    @Test
    fun aSecondRunChangesNothing() {
        val stored = HomeSites.LEGACY_DEFAULTS + HomeSite("Docs", "https://docs.test")
        val once = HomeSites.migrateDefaults(stored)

        assertEquals(once, HomeSites.migrateDefaults(once))
    }

    @Test
    fun defaultsAreOnlyAddedWhileThereIsRoomAndUserSitesAreNeverDropped() {
        val users = (1..10).map { HomeSite("Site $it", "https://s$it.test") }
        val stored = listOf(HomeSites.LEGACY_DEFAULTS[0]) + users

        val migrated = HomeSites.migrateDefaults(stored)

        assertEquals(HomeSites.MAX_SITES, migrated.size)
        assertEquals(listOf(youtube, facebook) + users, migrated)
    }

    @Test
    fun brandsMatchTheirHostsAndSubdomains() {
        assertEquals(SiteBrand.YOUTUBE, SiteBrand.of("https://m.youtube.com/watch?v=1"))
        assertEquals(SiteBrand.YOUTUBE, SiteBrand.of("https://youtu.be/abc"))
        assertEquals(SiteBrand.FACEBOOK, SiteBrand.of("https://fb.watch/xyz/"))
        assertEquals(SiteBrand.FACEBOOK, SiteBrand.of("https://www.facebook.com/reel/1"))
        assertEquals(SiteBrand.TIKTOK, SiteBrand.of("https://vm.tiktok.com/ZM/"))
        assertEquals(SiteBrand.INSTAGRAM, SiteBrand.of("https://www.instagram.com/p/1/"))
        assertEquals(SiteBrand.X, SiteBrand.of("https://twitter.com/a/status/1"))
        assertEquals(SiteBrand.X, SiteBrand.of("https://x.com/a"))
        assertNull(SiteBrand.of("https://notyoutube.com"))
        assertNull(SiteBrand.of("https://youtube.com.evil.test"))
        assertNull(SiteBrand.of("not a url"))
    }
}
