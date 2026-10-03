package com.alal.yft.feature.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** What a saved file says about itself: its length, picture size and a frame or cover art. */
data class MediaDetails(
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val image: ImageBitmap? = null,
) {
    /** "4K", "1080p" or "720p" from the picture's short side; null for audio or unknown. */
    val qualityLabel: String?
        get() = qualityLabel(width, height)

    companion object {
        /** Nothing could be read; remembered so a broken file is not opened again and again. */
        val Unknown = MediaDetails()
    }
}

/** Reads [MediaDetails] of saved files. The app's source uses MediaMetadataRetriever. */
interface MediaDetailsSource {
    /** Details already read for [uri], without doing any work. */
    fun cached(uri: String): MediaDetails?

    /** Reads, or recalls, the details of [uri]; [MediaDetails.Unknown] when nothing is readable. */
    suspend fun load(uri: String, isAudio: Boolean): MediaDetails

    /** No details at all, for previews and tests: every tile keeps its gradient. */
    object None : MediaDetailsSource {
        override fun cached(uri: String): MediaDetails? = null

        override suspend fun load(uri: String, isAudio: Boolean): MediaDetails =
            MediaDetails.Unknown
    }
}

/** The source screens read details from; the activity provides the app's one. */
val LocalMediaDetailsSource = staticCompositionLocalOf<MediaDetailsSource> {
    MediaDetailsSource.None
}

/** The details of [uri], read once in the background; null until they are known. */
@Composable
fun rememberMediaDetails(uri: String?, isAudio: Boolean): MediaDetails? {
    val source = LocalMediaDetailsSource.current
    val details = remember(uri, source) { mutableStateOf(uri?.let(source::cached)) }
    LaunchedEffect(uri, source) {
        if (uri != null && details.value == null) details.value = source.load(uri, isAudio)
    }
    return details.value
}

/**
 * Details read with MediaMetadataRetriever, two files at a time on the IO dispatcher, and kept in
 * a memory cache sized by the pictures it holds. Nothing is written to disk.
 */
@Singleton
class RetrieverMediaDetailsSource internal constructor(
    private val read: (uri: String, isAudio: Boolean) -> MediaDetails,
    private val ioDispatcher: CoroutineDispatcher,
    maxCacheBytes: Int,
) : MediaDetailsSource {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        read = { uri, isAudio -> readMediaDetails(context, uri, isAudio) },
        ioDispatcher = Dispatchers.IO,
        maxCacheBytes = defaultCacheBytes(),
    )

    private val cache = object : LruCache<String, MediaDetails>(maxCacheBytes) {
        override fun sizeOf(key: String, value: MediaDetails): Int = value.cacheBytes()
    }
    private val permits = Semaphore(MAX_PARALLEL_READS)

    override fun cached(uri: String): MediaDetails? = cache.get(uri)

    override suspend fun load(uri: String, isAudio: Boolean): MediaDetails {
        cache.get(uri)?.let { return it }
        return permits.withPermit {
            // Cached inside the read, so a tile scrolled away mid-read still keeps the result.
            cache.get(uri) ?: withContext(ioDispatcher) {
                read(uri, isAudio).also { cache.put(uri, it) }
            }
        }
    }
}

internal fun qualityLabel(width: Int?, height: Int?): String? {
    if (width == null || height == null || width <= 0 || height <= 0) return null
    val shortSide = minOf(width, height)
    return when {
        shortSide >= EIGHT_K_SHORT_SIDE -> "8K"
        shortSide >= FOUR_K_SHORT_SIDE -> "4K"
        else -> "${shortSide}p"
    }
}

/** A tenth of the way in, at most 10 s, so the frame is past any black lead-in. */
internal fun previewFrameTimeUs(durationMs: Long?): Long {
    val atMs = durationMs
        ?.takeIf { it > 0 }
        ?.let { (it / FRAME_AT_FRACTION).coerceAtMost(MAX_FRAME_AT_MS) }
    return (atMs ?: 0L) * MICROS_PER_MILLI
}

internal fun MediaDetails.cacheBytes(): Int =
    image?.let { it.width * it.height * BYTES_PER_PIXEL } ?: SMALL_ENTRY_BYTES

private fun readMediaDetails(context: Context, uri: String, isAudio: Boolean): MediaDetails {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, Uri.parse(uri))
        val durationMs = retriever.number(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val rotation = retriever.number(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) ?: 0L
        val rawWidth = retriever.number(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt()
        val rawHeight = retriever.number(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt()
        val turned = rotation % HALF_TURN_DEGREES != 0L
        val width = if (turned) rawHeight else rawWidth
        val height = if (turned) rawWidth else rawHeight
        val picture = if (isAudio || width == null) {
            retriever.embeddedPicture?.let(::decodeCover)
        } else {
            frameOf(retriever, previewFrameTimeUs(durationMs))
        }
        MediaDetails(
            durationMs = durationMs?.takeIf { it > 0 },
            width = width?.takeIf { it > 0 },
            height = height?.takeIf { it > 0 },
            image = picture?.asImageBitmap(),
        )
    } catch (_: RuntimeException) {
        // Unreadable, missing or no longer permitted: the tile keeps its gradient.
        MediaDetails.Unknown
    } finally {
        runCatching { retriever.release() }
    }
}

private fun MediaMetadataRetriever.number(key: Int): Long? = extractMetadata(key)?.toLongOrNull()

private fun frameOf(retriever: MediaMetadataRetriever, timeUs: Long): Bitmap? {
    val option = MediaMetadataRetriever.OPTION_CLOSEST_SYNC
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        retriever.getScaledFrameAtTime(timeUs, option, MAX_IMAGE_PX, MAX_IMAGE_PX)
    } else {
        retriever.getFrameAtTime(timeUs, option)?.let(::scaledDown)
    }
}

private fun scaledDown(bitmap: Bitmap): Bitmap {
    val longSide = maxOf(bitmap.width, bitmap.height)
    if (longSide <= MAX_IMAGE_PX) return bitmap
    val scale = MAX_IMAGE_PX.toFloat() / longSide
    val scaled = Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).toInt().coerceAtLeast(1),
        (bitmap.height * scale).toInt().coerceAtLeast(1),
        true,
    )
    if (scaled !== bitmap) bitmap.recycle()
    return scaled
}

private fun decodeCover(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_PX) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let(::scaledDown)
}

private fun defaultCacheBytes(): Int =
    (Runtime.getRuntime().maxMemory() / CACHE_SHARE_OF_HEAP)
        .coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES)
        .toInt()

private const val MAX_PARALLEL_READS = 2
private const val MAX_IMAGE_PX = 512
private const val BYTES_PER_PIXEL = 4
private const val SMALL_ENTRY_BYTES = 256
private const val FRAME_AT_FRACTION = 10L
private const val MAX_FRAME_AT_MS = 10_000L
private const val MICROS_PER_MILLI = 1_000L
private const val HALF_TURN_DEGREES = 180L
private const val FOUR_K_SHORT_SIDE = 2_160
private const val EIGHT_K_SHORT_SIDE = 4_320
private const val CACHE_SHARE_OF_HEAP = 16L
private const val MIN_CACHE_BYTES = 4L * 1024 * 1024
private const val MAX_CACHE_BYTES = 32L * 1024 * 1024
