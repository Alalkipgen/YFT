package com.alal.yft.core.media.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaVariant
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

@UnstableApi
@Singleton
class PreviewSourceFactory @Inject constructor(
    private val client: OkHttpClient,
) {
    fun create(variant: MediaVariant): MediaSource {
        require(variant.isPreviewable) { "Unsupported variants cannot be previewed" }
        val credentialOrigin = variant.playbackUrl.toHttpUrlOrNull()
            ?.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() }
            ?: throw IllegalArgumentException("Preview requires a credential-free HTTPS URL")
        val headerPolicy = PreviewHttpPolicy(
            credentialOrigin = credentialOrigin,
            requestContext = variant.requestContext,
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
            .setDefaultRequestProperties(variant.requestContext.replayHeaders())
        val mediaItem = MediaItem.Builder()
            .setUri(credentialOrigin.toString())
            .setMimeType(variant.kind.media3MimeType(variant.mimeType))
            .build()

        return when (variant.kind) {
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
}