package com.alal.yft.ui.navigation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.SpannableString
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.MainActivity
import com.alal.yft.ui.navigation.SharedLinks.Shared
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v1.0.2 (item 4): text shared to YFT from another app; made-up share texts of each site. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedLinksTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun share(text: CharSequence?) = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .apply { if (text != null) putExtra(Intent.EXTRA_TEXT, text) }

    private fun linkIn(text: CharSequence?): String? =
        (SharedLinks.sharedOf(share(text)) as? Shared.Link)?.url

    @Test
    fun eachSitesShareTextGivesItsLinkWithoutTheTitle() {
        val shares = listOf(
            // YouTube's app: the link alone (the title goes as the subject).
            "https://youtu.be/Ab3dEf6hIjK?si=q1W2e3R4t5Y6u7I8" to
                "https://youtu.be/Ab3dEf6hIjK?si=q1W2e3R4t5Y6u7I8",
            // Facebook: a caption, then the share link on its own line.
            "Sunset at the lake 🌅\nhttps://www.facebook.com/share/v/1AbCdEfGhJ/" to
                "https://www.facebook.com/share/v/1AbCdEfGhJ/",
            // TikTok: a line of text, then the short link.
            "Check out Mya's video! #TikTok https://vt.tiktok.com/ZSj1a2b3c/" to
                "https://vt.tiktok.com/ZSj1a2b3c/",
            // Instagram: the reel with its share parameter.
            "https://www.instagram.com/reel/C1a2B3c4D5e/?igsh=MTc4MmM1YmI2Ng==" to
                "https://www.instagram.com/reel/C1a2B3c4D5e/?igsh=MTc4MmM1YmI2Ng==",
            // X: "title + link".
            "Rain over the river https://x.com/someone/status/1790000000000000001?s=46" to
                "https://x.com/someone/status/1790000000000000001?s=46",
            // VK: the clip's page after its name.
            "Concert, part 2 https://vk.com/video-12345678_456239017" to
                "https://vk.com/video-12345678_456239017",
            // A browser: the page's address (the title goes as the subject).
            "https://m.youtube.com/watch?v=Ab3dEf6hIjK&t=42s" to
                "https://m.youtube.com/watch?v=Ab3dEf6hIjK&t=42s",
            // A chat: the link in brackets with a full stop, then another link.
            "Look (https://www.dailymotion.com/video/x8abcde). Also https://b.example/two" to
                "https://www.dailymotion.com/video/x8abcde",
        )

        shares.forEach { (text, link) -> assertEquals(text, link, linkIn(text)) }
        // Styled text (a Spanned) counts the same.
        assertEquals("https://vt.tiktok.com/ZSj1a2b3c/", linkIn(SpannableString(shares[2].first)))
    }

    @Test
    fun textWithoutAnHttpLinkIsNoLink() {
        listOf(
            null,
            "",
            "   ",
            "Just words, no link",
            "www.youtube.com/watch?v=Ab3dEf6hIjK",
            "ftp://files.example/clip.mp4",
            "file:///sdcard/Download/clip.mp4",
            "content://media/external/video/media/1",
            "intent://watch#Intent;scheme=https;end",
            "javascript:alert(1)",
        ).forEach { text ->
            assertEquals("$text", Shared.NoLink, SharedLinks.sharedOf(share(text)))
        }
    }

    @Test
    fun onlyAShareCountsAndNotItsRepeatFromRecents() {
        val link = "https://youtu.be/Ab3dEf6hIjK"
        assertNull(SharedLinks.sharedOf(null))
        assertNull(SharedLinks.sharedOf(Intent(context, MainActivity::class.java)))
        assertNull(
            SharedLinks.sharedOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            ),
        )
        assertNull(
            SharedLinks.sharedOf(Intent(Intent.ACTION_VIEW).putExtra(Intent.EXTRA_TEXT, link)),
        )
        assertNull(
            SharedLinks.sharedOf(
                share(link).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY),
            ),
        )
        assertEquals(Shared.Link(link), SharedLinks.sharedOf(share(link)))
    }

    @Test
    fun aVeryLongLinkIsCutAsOnHome() {
        val long = "https://a.test/" + "x".repeat(5_000)
        assertEquals(long.take(2_048), linkIn("See $long"))
    }

    @Test
    fun aShareWaitsUntilItIsHandedOnAndALaunchDoesNotDropIt() {
        val links = SharedLinks()
        links.take(share("First https://a.test/one"))
        links.take(Intent(Intent.ACTION_MAIN))
        assertEquals(Shared.Link("https://a.test/one"), links.shared.value)

        // A newer share replaces it; handing on an older one leaves the newer.
        links.take(share("Then https://a.test/two"))
        links.handled(Shared.Link("https://a.test/one"))
        assertEquals(Shared.Link("https://a.test/two"), links.shared.value)
        links.handled(Shared.Link("https://a.test/two"))
        assertNull(links.shared.value)

        links.take(share("no link"))
        assertEquals(Shared.NoLink, links.shared.value)
    }

    @Test
    fun androidOffersYftForSharedTextOnly() {
        fun targets(type: String) = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_SEND).setType(type).setPackage(context.packageName),
            0,
        ).map { it.activityInfo.name }

        assertEquals(listOf(MainActivity::class.java.name), targets("text/plain"))
        assertEquals(emptyList<String>(), targets("video/mp4"))
        assertEquals(emptyList<String>(), targets("image/png"))
    }

    @Test
    fun yftsOwnShareMenusLeaveYftOut() {
        val chooser = SharedLinks.leaveOutYft(Intent.createChooser(share("x"), "Share"))

        @Suppress("DEPRECATION")
        val left = chooser.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS)!!.toList()
        assertEquals(listOf(ComponentName(context, MainActivity::class.java)), left)
    }
}
