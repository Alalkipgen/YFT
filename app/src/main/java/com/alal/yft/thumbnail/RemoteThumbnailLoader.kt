package com.alal.yft.thumbnail

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer

/** Pictures of videos on the web (P19): the sheet header, the found list, running downloads. */
interface RemoteThumbnails {
    /** The picture already in memory for [url], without any work. */
    fun cached(url: String): ImageBitmap?

    /** The picture of [url], from memory, the disk cache or the web; null when it can't load. */
    suspend fun load(url: String): ImageBitmap?

    /** No pictures, for previews and tests: every tile keeps its placeholder. */
    object None : RemoteThumbnails {
        override fun cached(url: String): ImageBitmap? = null

        override suspend fun load(url: String): ImageBitmap? = null
    }
}

/** The loader screens read pictures from; the activity provides the app's one. */
val LocalRemoteThumbnails = staticCompositionLocalOf<RemoteThumbnails> { RemoteThumbnails.None }

/** The picture at [url], loaded once in the background; null (the placeholder) until then. */
@Composable
fun rememberRemoteThumbnail(url: String?): ImageBitmap? {
    val source = LocalRemoteThumbnails.current
    val image = remember(url, source) { mutableStateOf(url?.let(source::cached)) }
    LaunchedEffect(url, source) {
        if (url != null && image.value == null) image.value = source.load(url)
    }
    return image.value
}

/** Turns image bytes into a picture at most [maxWidth] pixels wide. */
fun interface ThumbnailDecoder {
    fun decode(bytes: ByteArray, maxWidth: Int): ImageBitmap?
}

/**
 * Loads video pictures without any new library (P19). HTTPS only, also after redirects; no
 * cookies or session; an `Accept` header for images only; at most [MAX_BYTES] are read, so a
 * larger answer stops the read; two downloads at a time. Pictures are decoded with
 * `inSampleSize` to at most [MAX_WIDTH] px wide and kept in a memory cache (about 8 MB) and a
 * disk cache under `cacheDir/thumbnails/` (at most 20 MB, the oldest removed first) named by a
 * hash of the address. Failures stay silent; logs name the host only, never the address.
 */
@Singleton
class RemoteThumbnailLoader internal constructor(
    client: OkHttpClient,
    private val directory: File,
    private val ioDispatcher: CoroutineDispatcher,
    private val decoder: ThumbnailDecoder,
    memoryCacheBytes: Int = MEMORY_CACHE_BYTES,
    private val diskCacheBytes: Long = DISK_CACHE_BYTES,
    private val log: (String) -> Unit = {},
) : RemoteThumbnails {
    @Inject
    constructor(client: OkHttpClient, @ApplicationContext context: Context) : this(
        client = client,
        directory = File(context.cacheDir, DIRECTORY),
        ioDispatcher = Dispatchers.IO,
        decoder = BitmapThumbnailDecoder,
        log = { message -> Log.i(TAG, message) },
    )

    private val client = client.newBuilder()
        .cookieJar(CookieJar.NO_COOKIES)
        .followSslRedirects(false)
        .cache(null)
        .build()
    private val memory = object : LruCache<String, ImageBitmap>(memoryCacheBytes) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.width * value.height * BYTES_PER_PIXEL
    }
    private val downloads = Semaphore(MAX_PARALLEL_DOWNLOADS)
    private val diskLock = Any()

    override fun cached(url: String): ImageBitmap? = memory.get(url)

    override suspend fun load(url: String): ImageBitmap? {
        memory.get(url)?.let { return it }
        val address = url.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return null
        return try {
            withContext(ioDispatcher) {
                val file = File(directory, fileName(url))
                val image = fromDisk(file) ?: downloads.withPermit {
                    memory.get(url) ?: fetch(address)?.let { bytes ->
                        decoder.decode(bytes, MAX_WIDTH)?.also { store(file, bytes) }
                    }
                }
                image?.also { memory.put(url, it) }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            log("Thumbnail from ${address.host} not loaded: ${failure.javaClass.simpleName}")
            null
        }
    }

    private fun fromDisk(file: File): ImageBitmap? {
        val bytes = synchronized(diskLock) {
            if (!file.isFile) return null
            file.setLastModified(System.currentTimeMillis())
            runCatching { file.readBytes() }.getOrNull()
        } ?: return null
        return decoder.decode(bytes, MAX_WIDTH)
    }

    private fun fetch(address: HttpUrl): ByteArray? {
        val request = Request.Builder().url(address).header("Accept", "image/*").get().build()
        client.newCall(request).execute().use { response ->
            val type = response.header("Content-Type")?.trim()?.lowercase()
            val refused = when {
                !response.isSuccessful -> "HTTP ${response.code}"
                !response.request.url.isHttps -> "not HTTPS"
                type != null && !type.startsWith("image/") -> "not an image"
                else -> null
            }
            val body = response.body
            if (refused != null || body == null) {
                log("Thumbnail from ${address.host} not loaded: ${refused ?: "no body"}")
                return null
            }
            if (body.contentLength() > MAX_BYTES) {
                log("Thumbnail from ${address.host} not loaded: too large")
                return null
            }
            val source = body.source()
            val buffer = Buffer()
            while (buffer.size <= MAX_BYTES) {
                if (source.read(buffer, READ_STEP) == -1L) return buffer.readByteArray()
            }
            log("Thumbnail from ${address.host} not loaded: too large")
            return null
        }
    }

    private fun store(file: File, bytes: ByteArray) = synchronized(diskLock) {
        try {
            directory.mkdirs()
            val partial = File(directory, "${file.name}.part")
            partial.writeBytes(bytes)
            if (!partial.renameTo(file)) partial.delete()
            trim()
        } catch (_: IOException) {
            // A full disk only costs the cache.
        }
    }

    /** Removes the oldest pictures until the cache fits [diskCacheBytes]. */
    private fun trim() {
        val files = directory.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        for (oldest in files.sortedBy { it.lastModified() }) {
            if (total <= diskCacheBytes) return
            val length = oldest.length()
            if (oldest.delete()) total -= length
        }
    }

    internal companion object {
        const val DIRECTORY = "thumbnails"
        const val MAX_BYTES = 2L * 1024 * 1024
        const val MAX_WIDTH = 480
        const val MAX_PARALLEL_DOWNLOADS = 2
        const val MEMORY_CACHE_BYTES = 8 * 1024 * 1024
        const val DISK_CACHE_BYTES = 20L * 1024 * 1024
        private const val BYTES_PER_PIXEL = 4
        private const val READ_STEP = 8_192L
        private const val TAG = "YftThumbnails"

        /** A file name that does not reveal the address: its SHA-256. */
        fun fileName(url: String): String = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

/** The smallest power of two that brings [width] to at most [maxWidth] pixels. */
internal fun thumbnailSampleSize(width: Int, maxWidth: Int): Int {
    var sample = 1
    while (width / sample > maxWidth) sample *= 2
    return sample
}

/** Android's own decoder: the bounds first, then the picture sampled down to the width. */
internal object BitmapThumbnailDecoder : ThumbnailDecoder {
    override fun decode(bytes: ByteArray, maxWidth: Int): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = thumbnailSampleSize(bounds.outWidth, maxWidth)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }
}
