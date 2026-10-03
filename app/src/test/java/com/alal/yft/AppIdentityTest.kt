package com.alal.yft

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppIdentityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun usesTheVideoDownloaderNameAndTheYftLauncherIcon() {
        val info = context.applicationInfo

        assertEquals("Video Downloader", context.getString(info.labelRes))
        assertEquals(R.mipmap.ic_launcher, info.icon)
        assertTrue(context.getDrawable(R.mipmap.ic_launcher) is AdaptiveIconDrawable)
        assertTrue(context.getDrawable(R.mipmap.ic_launcher_round) is AdaptiveIconDrawable)
    }

    @Test
    @Config(sdk = [28])
    fun adaptiveIconIsUsedFromAndroid8() {
        assertTrue(context.getDrawable(R.mipmap.ic_launcher) is AdaptiveIconDrawable)
    }

    @Test
    fun legacyLauncherPngsExistForAndroid7AtEveryDensity() {
        // Android 7.x launchers cannot draw adaptive icons; scripts/generate-launcher-icons.py
        // renders these from the same geometry. Unit tests run from the app module directory.
        val sizes = mapOf(
            "mdpi" to 48,
            "hdpi" to 72,
            "xhdpi" to 96,
            "xxhdpi" to 144,
            "xxxhdpi" to 192,
        )
        sizes.forEach { (density, size) ->
            listOf("ic_launcher.png", "ic_launcher_round.png").forEach { name ->
                val file = File("src/main/res/mipmap-$density/$name")
                assertTrue("missing ${file.path}", file.isFile)
                assertEquals("${file.path} size", size to size, pngSize(file))
            }
        }
    }

    private fun pngSize(file: File): Pair<Int, Int> {
        // A PNG starts with an 8-byte signature and the IHDR chunk: width and height follow at 16.
        val header = ByteBuffer.wrap(file.readBytes())
        assertEquals(0x89504E47.toInt(), header.getInt(0))
        return header.getInt(16) to header.getInt(20)
    }

    @Test
    fun mainActivityStartsOnTheLaunchThemeWhileTheAppUsesTheYftTheme() {
        val activity = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            0,
        )

        assertEquals(R.style.Theme_Yft_Launch, activity.theme)
        assertEquals(R.style.Theme_Yft, context.applicationInfo.theme)
    }

    @Test
    fun versionFollowsTheReleaseScheme() {
        // Unit tests run on the debug variant, whose versionName carries the "-debug" suffix.
        assertTrue(BuildConfig.VERSION_CODE >= 1)
        assertTrue(
            BuildConfig.VERSION_NAME,
            Regex("""\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?""").matches(BuildConfig.VERSION_NAME),
        )
    }
}
