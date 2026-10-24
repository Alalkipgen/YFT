package com.alal.yft.feature.settings

import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.detection.SiteLookupCache
import com.alal.yft.detection.SiteLookupKey
import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 7 Track B3: "Reset YouTube session" forgets YouTube's cookies, site storage and
 * remembered lookups, and nothing of any other site (Clear browsing data signs the user out of
 * every site). The fake jar keeps cookies as a browser does: a cookie of a host only and one of
 * a domain are different cookies even with one name, and only an expired `Set-Cookie` with the
 * cookie's own name, domain and path deletes it.
 */
class YouTubeSessionResetTest {
    private val jar = FakeCookieJar(
        listOf(
            // YouTube's visitor ID, session and preferences, for youtube.com and its subdomains.
            JarCookie("VISITOR_INFO1_LIVE", "youtube.com", hostOnly = false),
            JarCookie("YSC", "youtube.com", hostOnly = false),
            JarCookie("__Secure-YEC", "youtube.com", hostOnly = false),
            JarCookie("PREF", "youtube.com", hostOnly = false),
            // Cookies of one YouTube host only, and one for a subdomain and its own subdomains.
            JarCookie("__Host-watch", "www.youtube.com", hostOnly = true),
            JarCookie("PREF", "m.youtube.com", hostOnly = true),
            JarCookie("music_session", "music.youtube.com", hostOnly = false),
            JarCookie("root_only", "youtube.com", hostOnly = true),
            // Other sites: Google's sign-in, Instagram's and a look-alike of YouTube's domain.
            JarCookie("SID", "google.com", hostOnly = false),
            JarCookie("__Secure-3PSID", "google.com", hostOnly = false),
            JarCookie("LSID", "accounts.google.com", hostOnly = true),
            JarCookie("sessionid", "instagram.com", hostOnly = false),
            JarCookie("VISITOR_INFO1_LIVE", "notyoutube.com", hostOnly = false),
        ),
    )
    private val storage = FakeStorage()

    @Test
    fun youTubesCookiesGoAndOtherSitesKeepTheirs() = runTest {
        val others = jar.cookies.filterNot { it.isYouTubes() }

        val result = CookieYouTubeSessionReset(jar, storage).reset()

        CookieYouTubeSessionReset.YOUTUBE_PAGES.forEach { page ->
            assertEquals("$page still gets cookies", emptySet<String>(), jar.names(page))
        }
        assertEquals(others, jar.cookies)
        assertEquals(setOf("SID", "__Secure-3PSID"), jar.names("https://www.google.com/"))
        assertEquals(
            setOf("SID", "__Secure-3PSID", "LSID"),
            jar.names("https://accounts.google.com/"),
        )
        assertEquals(setOf("sessionid"), jar.names("https://www.instagram.com/"))
        assertEquals(setOf("VISITOR_INFO1_LIVE"), jar.names("https://www.notyoutube.com/"))
        // VISITOR_INFO1_LIVE, YSC, __Secure-YEC, PREF, __Host-watch, music_session, root_only.
        assertEquals(YouTubeSessionResetResult(cookiesRemoved = 7, cookiesLeft = 0), result)
        assertEquals(1, jar.flushes)
    }

    @Test
    fun theResetAsksOnlyYouTubesPagesAndSendsExpiredSecureCookiesForTheRoot() = runTest {
        CookieYouTubeSessionReset(jar, storage).reset()

        assertTrue(jar.sets.isNotEmpty())
        jar.sets.forEach { (url, setCookie) ->
            assertTrue(url, url in CookieYouTubeSessionReset.YOUTUBE_PAGES)
            val attributes = setCookie.split(';').map(String::trim)
            assertTrue(setCookie, attributes.first().endsWith("="))
            assertTrue(setCookie, "Max-Age=0" in attributes)
            assertTrue(setCookie, "Path=/" in attributes)
            assertTrue(setCookie, "Secure" in attributes)
            attributes.firstOrNull { it.startsWith("Domain=") }?.removePrefix("Domain=")?.let {
                assertTrue(setCookie, it == "youtube.com" || it.endsWith(".youtube.com"))
            }
        }
    }

    @Test
    fun youTubesSiteStorageGoesAndNoOtherOrigins() = runTest {
        CookieYouTubeSessionReset(jar, storage).reset()

        assertEquals(
            listOf(
                "https://www.youtube.com",
                "https://m.youtube.com",
                "https://music.youtube.com",
                "https://youtube.com",
            ),
            storage.deleted,
        )
    }

    @Test
    fun youTubesRememberedLookupsGoAndOtherSitesStay() = runTest {
        val lookups = SiteLookupCache()
        val youTube = SiteLookupKey("youtube", "dQw4w9WgXcQ", session = true)
        val youTubeWithout = SiteLookupKey("youtube", "jNQXAC9IVRw", session = false)
        val other = SiteLookupKey("fixture", "1", session = true)
        listOf(youTube, youTubeWithout, other).forEach { key ->
            lookups.put(key, SiteAdapterOutcome.Detected(key.siteId, emptyList()), NOW)
        }

        CookieYouTubeSessionReset(jar, storage, lookups).reset()

        assertNull(lookups.get(youTube, NOW + 1))
        assertNull(lookups.get(youTubeWithout, NOW + 1))
        assertNotNull(lookups.get(other, NOW + 1))
    }

    @Test
    fun aCookieTheBrowserKeepsIsCountedAsLeft() = runTest {
        jar.kept += "YSC"

        val result = CookieYouTubeSessionReset(jar, storage).reset()

        assertEquals(YouTubeSessionResetResult(cookiesRemoved = 6, cookiesLeft = 1), result)
        assertEquals(setOf("YSC"), jar.names("https://www.youtube.com/"))
    }

    @Test
    fun withoutYouTubeCookiesNothingIsRemovedAndOtherSitesStay() = runTest {
        val empty = FakeCookieJar(jar.cookies.filterNot { it.isYouTubes() })
        val before = empty.cookies.toList()

        val result = CookieYouTubeSessionReset(empty, storage).reset()

        assertEquals(YouTubeSessionResetResult(cookiesRemoved = 0, cookiesLeft = 0), result)
        assertEquals(before, empty.cookies)
        assertEquals(4, storage.deleted.size)
    }

    private fun JarCookie.isYouTubes(): Boolean =
        domain == "youtube.com" || domain.endsWith(".youtube.com")

    /** A cookie as a browser keeps it: name, domain (of a host only or not) and path "/". */
    private data class JarCookie(val name: String, val domain: String, val hostOnly: Boolean) {
        fun sentTo(host: String): Boolean =
            if (hostOnly) host == domain else host == domain || host.endsWith(".$domain")
    }

    private class FakeCookieJar(initial: List<JarCookie>) : SiteCookieJar {
        val cookies = initial.toMutableList()
        val sets = mutableListOf<Pair<String, String>>()
        val kept = mutableSetOf<String>()
        var flushes = 0

        override suspend fun names(url: String): Set<String> {
            val host = URI(url).host
            return cookies.filter { it.sentTo(host) }.mapTo(mutableSetOf()) { it.name }
        }

        override suspend fun set(url: String, setCookie: String) {
            sets += url to setCookie
            val host = URI(url).host
            val parts = setCookie.split(';').map(String::trim)
            val name = parts.first().substringBefore('=')
            val domain = parts.firstOrNull { it.startsWith("Domain=") }
                ?.removePrefix("Domain=")
                ?.removePrefix(".")
            // A browser refuses a cookie for a domain the page is not part of.
            if (domain != null && host != domain && !host.endsWith(".$domain")) return
            val cookie = JarCookie(name, domain ?: host, hostOnly = domain == null)
            if ("Max-Age=0" in parts) {
                if (name !in kept) cookies.remove(cookie)
            } else if (cookie !in cookies) {
                cookies += cookie
            }
        }

        override suspend fun flush() {
            flushes += 1
        }
    }

    private class FakeStorage : SiteStorage {
        val deleted = mutableListOf<String>()

        override suspend fun deleteOrigin(origin: String) {
            deleted += origin
        }
    }

    private companion object {
        const val NOW = 1_000_000L
    }
}
