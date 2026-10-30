package com.alal.yft.ui.navigation

import android.content.ComponentName
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.alal.yft.BuildConfig
import com.alal.yft.MainActivity
import com.alal.yft.feature.home.HomeLinks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v1.0.2 (item 4): text another app shared to YFT (Android's Share, `ACTION_SEND` text/plain).
 * Apps send "title + link", so only its first http(s) link counts, as on Home's Paste.
 * `MainActivity` hands its intents to [take]; [LookUpSharedLink] gives the link to Home's lookup,
 * so the download sheet opens as for a pasted link (nothing downloads by itself), or says that
 * the text had no link.
 */
class SharedLinks {
    private val pending = MutableStateFlow<Shared?>(null)

    /** The share to handle next; null when there is none. */
    val shared: StateFlow<Shared?> = pending.asStateFlow()

    /** Takes [intent]'s shared text, if it is a share ([sharedOf]). */
    fun take(intent: Intent?) {
        sharedOf(intent)?.let { pending.value = it }
    }

    /** [share] was handed on. */
    fun handled(share: Shared) {
        pending.compareAndSet(share, null)
    }

    /** What a share brought: a link to look up, or text without one. */
    sealed interface Shared {
        data class Link(val url: String) : Shared

        data object NoLink : Shared
    }

    companion object {
        const val NO_LINK_MESSAGE = "No link in the shared text"

        /**
         * The share [intent] brings, or null when it is none: another action (a plain launch, a
         * notification's tap) or a start from Recents, which repeats the intent that first opened
         * the app and must not look the link up again. Only an http(s) link counts; text with
         * none, or no text, is [Shared.NoLink].
         */
        fun sharedOf(intent: Intent?): Shared? {
            if (intent?.action != Intent.ACTION_SEND) return null
            if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return null
            val link = HomeLinks.firstWebLink(intent.getCharSequenceExtra(Intent.EXTRA_TEXT))
            return link?.let(Shared::Link) ?: Shared.NoLink
        }

        /**
         * [chooser] (one of YFT's own Share menus) without YFT in it: YFT's report or file shared
         * to YFT would only say it has no link.
         */
        fun leaveOutYft(chooser: Intent): Intent = chooser.putExtra(
            Intent.EXTRA_EXCLUDE_COMPONENTS,
            arrayOf(ComponentName(BuildConfig.APPLICATION_ID, MainActivity::class.java.name)),
        )
    }
}

/**
 * Hands the link [links] holds to Home's lookup ([lookUpOnHome]), as the browser's Download does,
 * once the NavHost has its graph; text without a link gets a short message and the app stays
 * where it is. Before `UpdateAutoOpen`, which waits while Home has a link to look up.
 */
@Composable
fun LookUpSharedLink(navController: NavHostController, links: SharedLinks) {
    val shared by links.shared.collectAsStateWithLifecycle()
    val entry by navController.currentBackStackEntryAsState()
    val context = LocalContext.current
    LaunchedEffect(shared, entry == null) {
        val share = shared ?: return@LaunchedEffect
        // The NavHost sets its graph while it composes; this runs again once it has.
        if (navController.currentBackStackEntry == null) return@LaunchedEffect
        when (share) {
            is SharedLinks.Shared.Link -> navController.lookUpOnHome(share.url)
            SharedLinks.Shared.NoLink ->
                Toast.makeText(context, SharedLinks.NO_LINK_MESSAGE, Toast.LENGTH_SHORT).show()
        }
        links.handled(share)
    }
}
