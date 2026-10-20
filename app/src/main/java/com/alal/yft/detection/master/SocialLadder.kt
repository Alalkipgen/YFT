package com.alal.yft.detection.master

import com.alal.yft.BuildConfig
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.social.MainPageRecorder
import com.alal.yft.extractor.master.social.MainPages
import com.alal.yft.extractor.master.social.SiteWays
import kotlin.coroutines.CoroutineContext
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
}
