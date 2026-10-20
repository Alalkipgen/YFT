package com.alal.yft.detection.master

import com.alal.yft.BuildConfig
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.social.FacebookLadder
import com.alal.yft.extractor.master.social.MainPageRecorder
import com.alal.yft.extractor.master.social.MainPages
import com.alal.yft.extractor.master.social.SiteWays
import com.alal.yft.extractor.master.social.SocialStage
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import okhttp3.OkHttpClient

/**
 * Social plan S1a (`MASTER_KEY_SOCIAL_PLAN.md` §7.3): the build's social-ladder switch and its app
 * wiring, Facebook first (the pilot). On only in a debug build made with `-Pyft.socialLadder=true`
 * and `-Pyft.masterCapture=true`; release, preview and every other build: off, and each piece here
 * is exactly today's.
 */
internal object SocialLadder {
    val enabled: Boolean get() = BuildConfig.SOCIAL_LADDER

    /** The pilot's site ID (main's Facebook adapter; its video key's prefix). */
    const val FACEBOOK_SITE_ID = "facebook"

    /**
     * S1a (2): the client main's site adapters share. In a social-ladder build it records each
     * request into the lookup's record (route B's recorder, [YouTubeRouteB.mainClient]) and keeps
     * main's pages in the lookup's [MainPages] ([MainPageRecorder]); a lookup without them only
     * passes through. Otherwise it is today's client (route B's, as before).
     */
    fun mainClient(client: OkHttpClient, social: Boolean = enabled): ExtractorHttpClient =
        pages(YouTubeRouteB.mainClient(client, routeB = YouTubeRouteB.enabled || social), social)

    /** S1a (2): [http] keeping main's pages when [social]; else [http] itself. */
    fun pages(http: ExtractorHttpClient, social: Boolean = enabled): ExtractorHttpClient =
        if (social) MainPageRecorder(http) else http

    /** S1a (2): whether [videoKey] ("site:contentId") is the pilot's: every Facebook link form. */
    fun claims(videoKey: String?): Boolean = videoKey?.substringBefore(':') == FACEBOOK_SITE_ID

    /**
     * S1a (2): a new lookup's context for a Facebook page — its own request record with Facebook's
     * labels (each ask's way and its answer's mark: the S7 stops read them) and its own
     * [MainPages]; memory only, dropped with the lookup.
     */
    fun lookupContext(): CoroutineContext = LookupRecord(labels = SiteWays.FACEBOOK.labels()) + MainPages()

    /**
     * S1a (3): Master's social rung in a social-ladder build: Facebook's ladder ([FacebookLadder]:
     * F1, the plugin player, judged) over Master's own client [http], claiming only a lookup with
     * its own record ([lookupContext]); [identify]: main's adapters. Its recipes come from the same
     * source as the engine's ([MasterRecipeConfig]). Every other build: null, the engine as before.
     */
    fun stage(
        http: ExtractorHttpClient,
        identify: (String) -> SitePageIdentity?,
        social: Boolean = enabled,
    ): SocialStage? = if (social) FacebookLadder(http, identify, MasterRecipeConfig::contracts) else null

    /**
     * S1a (3): the video key the ladder's rows carry in [lookupUrl]'s lookup: for a link main
     * resolves first (a share link, a post), the one video main's resolved link names (the
     * lookup's [MainPages]); else the lookup's [key].
     */
    suspend fun rowsKey(sites: SiteAdapterCoordinator, lookupUrl: String, key: String?): String? {
        val identity = sites.identity(lookupUrl) ?: return key
        val resolved = currentCoroutineContext()[MainPages]?.resolved(identity, sites::identity) ?: return key
        return "${resolved.siteId}:${resolved.contentId}"
    }

    /**
     * S1a (3): main's words for the ladder's final answer (the page's route: private or removed),
     * so a Facebook lookup says what main says for it; null for every other reason (as before).
     */
    fun finalMessage(reason: SiteExtractionFailure): String? = when (reason) {
        SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE -> "This Facebook post is private or no longer available."
        else -> null
    }
}
