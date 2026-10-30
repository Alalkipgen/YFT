package com.alal.yft.core.media.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaVariant
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

@UnstableApi
@Singleton
class PreviewSourceFactory @Inject constructor(
    private val client: OkHttpClient,
) {
    /**
     * Builds the player source for [variant]. A video-only variant with a companion audio track
     * plays both files together, each fetched with its own request context.
     */
    fun create(variant: MediaVariant): MediaSource {
        val sources = sources(variant)
        return sources.singleOrNull() ?: MergingMediaSource(*sources.toTypedArray())
    }

    /**
     * The video's source, then its companion audio's when [variant] has one. The companion plays
     * as HLS when it is a playlist (an HLS video's audio rendition), else as a progressive file
     * (P50: an HLS audio rendition read as one file did not play). A DASH page's sound, a
     * representation of the same manifest ([CompanionAudio.representationId], v1.0.2 item 59),
     * adds no source: the manifest plays its own sound.
     */
    internal fun sources(variant: MediaVariant): List<MediaSource> {
        require(variant.isPreviewable) { "Unsupported variants cannot be previewed" }
        val video = source(
            url = variant.playbackUrl,
            requestContext = variant.requestContext,
            kind = variant.kind,
            mimeType = variant.mimeType,
        )
        val companion = variant.audioCompanion ?: return listOf(video)
        if (companion.representationId != null) return listOf(video)
        val audio = source(
            url = companion.mediaUrl,
            requestContext = companion.requestContext,
            kind = companionKind(companion.mediaUrl, companion.mimeType),
            mimeType = companion.mimeType,
        )
        return listOf(video, audio)
    }

    private fun source(
        url: String,
        requestContext: BrowserRequestContext,
        kind: MediaKind,
        mimeType: String?,
    ): MediaSource {
        val credentialOrigin = url.toHttpUrlOrNull()
            ?.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() }
            ?: throw IllegalArgumentException("Preview requires a credential-free HTTPS URL")
        val headerPolicy = PreviewHttpPolicy(
            credentialOrigin = credentialOrigin,
            requestContext = requestContext,
        )
        val previewClient = client.newBuilder()
            .followRedirects(true)
            .followSslRedirects(false)
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                if (!request.url.isHttps) {
                    throw IOException("Insecure preview request blocked")
                }
                val builder = request.newBuilder()
                headerPolicy.contextHeaderNames.forEach(builder::removeHeader)
                headerPolicy.headersFor(request.url).forEach { (name, value) ->
                    builder.header(name, value)
                }
                chain.proceed(builder.build())
            }
            .build()
        val dataSourceFactory = OkHttpDataSource.Factory(previewClient)
            .setDefaultRequestProperties(requestContext.replayHeaders())
        val mediaItem = MediaItem.Builder()
            .setUri(credentialOrigin.toString())
            .setMimeType(kind.media3MimeType(mimeType))
            .build()

        return when (kind) {
            MediaKind.HLS -> HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
            MediaKind.DASH -> DashMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
            MediaKind.DIRECT,
            MediaKind.UNKNOWN,
            -> ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
        }
    }

    private fun MediaKind.media3MimeType(observedMimeType: String?): String? = when (this) {
        MediaKind.HLS -> MimeTypes.APPLICATION_M3U8
        MediaKind.DASH -> MimeTypes.APPLICATION_MPD
        MediaKind.DIRECT,
        MediaKind.UNKNOWN,
        -> observedMimeType
    }

    internal companion object {
        /** Playlist MIME types, as the variant resolver reads them (parameters dropped). */
        private val HLS_MIME_TYPES = setOf(
            "application/vnd.apple.mpegurl",
            "application/x-mpegurl",
            "audio/mpegurl",
            "audio/x-mpegurl",
        )

        /**
         * HLS when the companion is a playlist, by its MIME type or its path's `.m3u8` (the rule
         * the variant resolver and the download plan use); else a progressive file.
         */
        fun companionKind(url: String, mimeType: String?): MediaKind {
            val mime = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.US)
            val playlist = mime in HLS_MIME_TYPES ||
                url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
            return if (playlist) MediaKind.HLS else MediaKind.DIRECT
        }
    }
}