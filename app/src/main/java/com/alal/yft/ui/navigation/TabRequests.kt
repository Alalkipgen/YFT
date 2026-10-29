package com.alal.yft.ui.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A tab YFT was asked to show from outside the app (v1.0.2): a download notification's tap opens
 * Downloads. `MainActivity` hands its intents to [take]; [ShowRequestedTab] shows the tab once.
 */
class TabRequests {
    private val pending = MutableStateFlow<YftDestination?>(null)

    /** The tab to show next; null when there is none. */
    val requested: StateFlow<YftDestination?> = pending.asStateFlow()

    /** Asks for [intent]'s tab, if it names one ([tabOf]). */
    fun take(intent: Intent?) {
        tabOf(intent)?.let { pending.value = it }
    }

    /** [tab] is in front now. */
    fun shown(tab: YftDestination) {
        pending.compareAndSet(tab, null)
    }

    companion object {
        /** The route of the tab to show; only the four tabs are accepted. */
        const val EXTRA_TAB = "com.alal.yft.extra.TAB"

        /**
         * [launch] (an intent for YFT's activity) set to show [tab]: the YFT already open gets it
         * (`onNewIntent`) instead of a second copy of the app starting over it.
         */
        fun showing(launch: Intent, tab: YftDestination): Intent {
            require(tab.isTopLevel) { "Only a tab can be asked for." }
            return launch
                .putExtra(EXTRA_TAB, tab.route)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        /**
         * The tab [intent] asks for, or null: a plain launch, an unknown route, or a start from
         * Recents, which repeats the intent that first opened the app and must not switch tabs.
         */
        fun tabOf(intent: Intent?): YftDestination? {
            if (intent == null) return null
            if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return null
            return YftDestination.topLevelFor(intent.getStringExtra(EXTRA_TAB))
        }
    }
}

/**
 * Shows the tab [requests] holds as a bottom-bar tap would, while the app is in front: at a cold
 * start right after the NavHost set its graph, before `UpdateAutoOpen` sees Home, so a tab asked
 * for wins and the Updates screen waits (it never opens over another screen).
 */
@Composable
fun ShowRequestedTab(navController: NavHostController, requests: TabRequests) {
    val requested by requests.requested.collectAsStateWithLifecycle()
    val entry by navController.currentBackStackEntryAsState()
    LaunchedEffect(requested, entry == null) {
        val tab = requested ?: return@LaunchedEffect
        // The NavHost sets its graph while it composes; this runs again once it has.
        if (navController.currentBackStackEntry == null) return@LaunchedEffect
        navController.navigateToTab(tab)
        requests.shown(tab)
    }
}
