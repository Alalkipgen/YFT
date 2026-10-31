package com.alal.yft.feature.quickdownload

import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.media.resolver.DefaultVariantResolver
import com.alal.yft.core.media.resolver.VariantResolver
import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.detection.VideoPlaybackSupport
import com.alal.yft.download.EnqueueResult
import com.alal.yft.download.PreviewDownloadStarter
import com.alal.yft.download.policy.NetworkSnapshot
import com.alal.yft.download.policy.NetworkStatusSource
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.feature.downloads.DownloadPlanFactory
import com.alal.yft.feature.downloads.DownloadPlanResult
import com.alal.yft.feature.downloads.DownloadRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * v1.0.2 fix R2: the download sheet of a pasted DASH manifest, as on the phone: the site serves
 * [served] (a test may change it between the sheet and its Download), the app's own resolver
 * reads it and the sheet pairs its sound ([com.alal.yft.detection.DashSoundPairing]); Download
 * queues on [starter], and [plan] is what the download then fetches. v1.0.2 fix R3: or the
 * manifest as the browser found it ([candidate]).
 */
internal class DashManifestSheet(
    val manifestUrl: String,
    var served: String,
    val candidate: MediaCandidate = pasted(manifestUrl),
) {
    val starter = Starter()
    private val store = DetectedMediaStore().apply {
        publish(manifestUrl, null, listOf(candidate))
        select(MediaGroups.of(listOf(candidate)).single())
    }
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val body = if (request.method == "HEAD") "" else served
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Content-Type", DASH)
                .body(body.toResponseBody(DASH.toMediaType()))
                .build()
        }
        .build()
    private val reader = DefaultVariantResolver(client, DefaultVariantResolver.Policy(), { NOW })

    /** The app's resolver, answered before the test's dispatcher goes on. */
    val resolver = object : VariantResolver {
        override suspend fun resolve(candidate: MediaCandidate) =
            runBlocking { reader.resolve(candidate) }
    }

    fun open() = QuickDownloadViewModel(
        store = store,
        selectionStore = PreviewSelectionStore(),
        resolver = resolver,
        downloadStarter = starter,
        downloadPreferences = object : DownloadPreferencesRepository {
            override val preferences: Flow<DownloadPreferences> =
                MutableStateFlow(DownloadPreferences(confirmOnMeteredNetwork = false))

            override suspend fun update(
                transform: (DownloadPreferences) -> DownloadPreferences,
            ) = Unit
        },
        network = object : NetworkStatusSource {
            override val snapshot: StateFlow<NetworkSnapshot> = MutableStateFlow(
                NetworkSnapshot(connected = true, validated = true, unmetered = true),
            )
        },
        playback = VideoPlaybackSupport.ANY,
    )

    /** The manifest's qualities as the resolver reads them, before any sound is paired. */
    fun read(): MediaAsset = runBlocking {
        (reader.resolve(candidate) as VariantResolutionResult.Success).asset
    }

    /** Queues whatever the sheet's Download prepared. */
    class Starter : PreviewDownloadStarter {
        val queued = mutableListOf<Pair<MediaAsset, MediaVariant>>()

        override suspend fun enqueue(asset: MediaAsset, variant: MediaVariant): EnqueueResult {
            queued += asset to variant
            return EnqueueResult.Started("task-${queued.size}", "Video.mp4")
        }
    }

    companion object {
        const val NOW = 1_000L
        private const val DASH = "application/dash+xml"

        /** [url] pasted into the app. */
        fun pasted(url: String) = MediaCandidate(
            pageUrl = url,
            mediaUrl = url,
            sources = setOf(CandidateSource.PASTED_URL),
            kind = MediaKind.DASH,
        )

        /** A manifest of `src/test/resources/dash`, as its site served it. */
        fun text(name: String): String = requireNotNull(
            DashManifestSheet::class.java.getResourceAsStream("/dash/$name"),
        ) { name }.bufferedReader().use { it.readText() }

        /** What the download of [variant] fetches, on an Android 14 phone. */
        fun plan(asset: MediaAsset, variant: MediaVariant, av1: Boolean = false): DownloadRequest =
            when (
                val result = DownloadPlanFactory.create(
                    asset = asset,
                    variant = variant,
                    taskId = "task",
                    nowEpochMs = NOW,
                    sdkInt = 34,
                    av1Enabled = av1,
                )
            ) {
                is DownloadPlanResult.Ready -> result.request
                else -> throw AssertionError("not planned: $result")
            }
    }
}
