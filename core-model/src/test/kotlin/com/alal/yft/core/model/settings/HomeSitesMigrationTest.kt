package com.alal.yft.core.model.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeSitesMigrationTest {
    private val youtube = HomeSite("YouTube", "https://m.youtube.com")
    private val facebook = HomeSite("Facebook", "https://m.facebook.com")
    private val tiktok = HomeSite("TikTok", "https://www.tiktok.com")
    private val instagram = HomeSite("Instagram", "https://www.instagram.com")
    private val x = HomeSite("X", "https://x.com")

    @Test
    fun defaultsAreYouTubeFacebookTikTokInstagramAndX() {
        // D4: Instagram and X join the first three, as defaults version 3.
        assertEquals(listOf(youtube, facebook, tiktok, instagram, x), HomeSites.DEFAULTS)
        assertEquals(3, HomeSites.DEFAULTS_VERSION)
        assertEquals(listOf(instagram, x), HomeSites.ADDED_IN_VERSION_3)
    }

    @Test
    fun untouchedOldDefaultsBecomeTheNewDefaults() {
        assertEquals(HomeSites.DEFAULTS, HomeSites.migrateDefaults(HomeSites.LEGACY_DEFAULTS, 1))
    }

    @Test
    fun ownersCaseKeepsHisYouTubeWithoutADuplicate() {
        val own = HomeSite("YouTube", "https://www.youtube.com")
        val stored = HomeSites.LEGACY_DEFAULTS + own

        assertEquals(
            listOf(own, facebook, tiktok, instagram, x),
            HomeSites.migrateDefaults(stored, 1),
        )
    }

    @Test
    fun userSitesAreKeptAfterTheNewDefaults() {
        val docs = HomeSite("Docs", "https://docs.test")
        val stored = listOf(HomeSites.LEGACY_DEFAULTS[0], docs, HomeSites.LEGACY_DEFAULTS[2])

        assertEquals(
            listOf(youtube, facebook, tiktok, instagram, x, docs),
            HomeSites.migrateDefaults(stored, 1),
        )
    }

    @Test
    fun aRenamedOldDefaultCountsAsTheUsersOwnSite() {
        val renamed = HomeSite("Old films", "https://archive.org")
        val stored = listOf(renamed, HomeSites.LEGACY_DEFAULTS[1])

        assertEquals(
            listOf(youtube, facebook, tiktok, instagram, x, renamed),
            HomeSites.migrateDefaults(stored, 1),
        )
    }

    @Test
    fun aCustomListAndAnEmptyListStayAsTheyAre() {
        val custom = listOf(
            HomeSite("Docs", "https://docs.test"),
            HomeSite("Vimeo", "https://vimeo.com"),
        )

        // A version 1 list without an old default is moved as before: not at all.
        assertEquals(custom, HomeSites.migrateDefaults(custom, 1))
        assertEquals(emptyList<HomeSite>(), HomeSites.migrateDefaults(emptyList(), 1))
    }

    @Test
    fun aSecondRunChangesNothing() {
        val stored = HomeSites.LEGACY_DEFAULTS + HomeSite("Docs", "https://docs.test")
        val once = HomeSites.migrateDefaults(stored, 1)

        assertEquals(once, HomeSites.migrateDefaults(once, 1))
        assertEquals(once, HomeSites.migrateDefaults(once, 2))
        assertEquals(once, HomeSites.migrateDefaults(once, HomeSites.DEFAULTS_VERSION))
    }

    @Test
    fun defaultsAreOnlyAddedWhileThereIsRoomAndUserSitesAreNeverDropped() {
        val users = (1..10).map { HomeSite("Site $it", "https://s$it.test") }
        val stored = listOf(HomeSites.LEGACY_DEFAULTS[0]) + users

        val migrated = HomeSites.migrateDefaults(stored, 1)

        assertEquals(HomeSites.MAX_SITES, migrated.size)
        assertEquals(listOf(youtube, facebook) + users, migrated)
    }

    @Test
    fun aVersion2ListGetsInstagramAndXAtItsEnd() {
        // D4: the owner's list, VK a site he added himself (no VK code), gets the two after it.
        val vk = HomeSite("VK", "https://vk.com")
        val stored = listOf(youtube, facebook, tiktok, vk)

        val migrated = HomeSites.migrateDefaults(stored, 2)

        assertEquals(listOf(youtube, facebook, tiktok, vk, instagram, x), migrated)
        assertEquals(migrated, HomeSites.migrateDefaults(migrated, HomeSites.DEFAULTS_VERSION))
    }

    @Test
    fun aVersion2ListWithASiteOfTheSameBrandIsNotGivenASecondOne() {
        val twitter = HomeSite("Twitter", "https://twitter.com/home")
        val reels = HomeSite("Reels", "https://m.instagram.com/reels/")

        assertEquals(
            listOf(youtube, twitter, instagram),
            HomeSites.migrateDefaults(listOf(youtube, twitter), 2),
        )
        assertEquals(listOf(reels, x), HomeSites.migrateDefaults(listOf(reels), 2))
        assertEquals(listOf(reels, twitter), HomeSites.migrateDefaults(listOf(reels, twitter), 2))
    }

    @Test
    fun aVersion2ListGetsOnlyWhatFitsUnderTheLimit() {
        val users = (1..HomeSites.MAX_SITES).map { HomeSite("Site $it", "https://s$it.test") }

        assertEquals(users, HomeSites.migrateDefaults(users, 2))
        assertEquals(users.take(11) + instagram, HomeSites.migrateDefaults(users.take(11), 2))
        assertEquals(users.take(10) + instagram + x, HomeSites.migrateDefaults(users.take(10), 2))
    }

    @Test
    fun anEmptyVersion2ListStaysEmptyAndACurrentListAsItIs() {
        val own = listOf(youtube, HomeSite("Docs", "https://docs.test"))

        assertEquals(emptyList<HomeSite>(), HomeSites.migrateDefaults(emptyList(), 2))
        assertEquals(own, HomeSites.migrateDefaults(own, HomeSites.DEFAULTS_VERSION))
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
