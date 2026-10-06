package com.alal.yft.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.download.DownloadApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Small pictures of downloads (P19). When a download starts its video's picture is saved as
 * `filesDir/thumbnails/<downloadId>.jpg`; Downloads and the Library show it until the file's own
 * frame exists, and it goes with the download's record.
 */
interface DownloadThumbnails {
    /** The picture of [downloadId] already in memory. */
    fun cached(downloadId: String): ImageBitmap?

    /** The saved picture of [downloadId]; null when none was saved. */
    suspend fun load(downloadId: String): ImageBitmap?

    /** The saved picture of the download whose published file is [uri]. */
    suspend fun loadForFile(uri: String): ImageBitmap?

    /** Saves the picture at [thumbnailUrl] for [downloadId] in the background. */
    fun saveFrom(downloadId: String, thumbnailUrl: String)

    /** Deletes the picture of [downloadId], with its record. */
    fun delete(downloadId: String)

    /** Deletes the pictures, older than [olderThanEpochMs], of downloads not in [downloadIds]. */
    fun keepOnly(downloadIds: Set<String>, olderThanEpochMs: Long = Long.MAX_VALUE)

    /** No pictures, for previews and tests. */
    object None : DownloadThumbnails {
        override fun cached(downloadId: String): ImageBitmap? = null

        override suspend fun load(downloadId: String): ImageBitmap? = null

        override suspend fun loadForFile(uri: String): ImageBitmap? = null

        override fun saveFrom(downloadId: String, thumbnailUrl: String) = Unit

        override fun delete(downloadId: String) = Unit

        override fun keepOnly(downloadIds: Set<String>, olderThanEpochMs: Long) = Unit
    }
}

/** The store screens read download pictures from; the activity provides the app's one. */
val LocalDownloadThumbnails = staticCompositionLocalOf<DownloadThumbnails> {
    DownloadThumbnails.None
}

/** The saved picture of [downloadId]; null until it is read or when there is none. */
@Composable
fun rememberDownloadThumbnail(downloadId: String?): ImageBitmap? {
    val store = LocalDownloadThumbnails.current
    val image = remember(downloadId, store) { mutableStateOf(downloadId?.let(store::cached)) }
    LaunchedEffect(downloadId, store) {
        if (downloadId != null && image.value == null) image.value = store.load(downloadId)
    }
    return image.value
}

/** The saved picture of the download that made the file at [uri], for the Library. */
@Composable
fun rememberFileThumbnail(uri: String?, wanted: Boolean = true): ImageBitmap? {
    val store = LocalDownloadThumbnails.current
    val image = remember(uri, store) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri, store, wanted) {
        if (uri != null && wanted && image.value == null) image.value = store.loadForFile(uri)
    }
    return image.value
}

/**
 * Pictures saved as small JPEGs (at most [MAX_WIDTH] px wide, about [TARGET_BYTES]). A picture
 * goes when its download leaves [recordIds], whichever way the record was deleted.
 */
@Singleton
class SavedDownloadThumbnails internal constructor(
    private val directory: File,
    private val remote: RemoteThumbnails,
    private val fileOwner: (uri: String) -> String?,
    recordIds: Flow<Set<String>>,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val encoder: (ImageBitmap) -> ByteArray? = ::encodeThumbnail,
    private val decoder: (File) -> ImageBitmap? = ::decodeThumbnail,
) : DownloadThumbnails {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        remote: RemoteThumbnails,
        queue: DownloadQueue,
        @DownloadApplicationScope scope: CoroutineScope,
    ) : this(
        directory = File(context.filesDir, DIRECTORY),
        remote = remote,
        fileOwner = { uri ->
            queue.tasks.value.firstOrNull { it.destinationUri == uri }?.id
        },
        recordIds = queue.tasks.map { tasks -> tasks.mapTo(mutableSetOf()) { it.id } },
        scope = scope,
        ioDispatcher = Dispatchers.IO,
    )

    private val memory = object : LruCache<String, ImageBitmap>(MEMORY_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.width * value.height * BYTES_PER_PIXEL
    }

    init {
        scope.launch {
            var listed: Set<String>? = null
            recordIds.distinctUntilChanged().collect { ids ->
                listed?.minus(ids)?.forEach(::delete)
                listed = ids
            }
        }
    }

    /** Where the picture of [downloadId] is saved; null for an ID that is not a plain name. */
    internal fun file(downloadId: String): File? =
        File(directory, "$downloadId.jpg").takeIf { SAFE_ID.matches(downloadId) }

    override fun cached(downloadId: String): ImageBitmap? = memory.get(downloadId)

    override suspend fun load(downloadId: String): ImageBitmap? {
        memory.get(downloadId)?.let { return it }
        val file = file(downloadId) ?: return null
        return withContext(ioDispatcher) {
            if (!file.isFile) return@withContext null
            runCatching { decoder(file) }.getOrNull()?.also { memory.put(downloadId, it) }
        }
    }

    override suspend fun loadForFile(uri: String): ImageBitmap? =
        fileOwner(uri)?.let { load(it) }

    override fun saveFrom(downloadId: String, thumbnailUrl: String) {
        val file = file(downloadId) ?: return
        scope.launch {
            try {
                val image = remote.load(thumbnailUrl) ?: return@launch
                val bytes = withContext(ioDispatcher) { encoder(image) } ?: return@launch
                withContext(ioDispatcher) {
                    directory.mkdirs()
                    val partial = File(directory, "${file.name}.part")
                    partial.writeBytes(bytes)
                    if (!partial.renameTo(file)) partial.delete()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Silent: Downloads keeps its placeholder.
            }
        }
    }

    override fun delete(downloadId: String) {
        memory.remove(downloadId)
        file(downloadId)?.delete()
    }

    override fun keepOnly(downloadIds: Set<String>, olderThanEpochMs: Long) {
        directory.listFiles().orEmpty()
            .filter { it.isFile && it.lastModified() < olderThanEpochMs }
            .filter { it.name.removeSuffix(".part").removeSuffix(".jpg") !in downloadIds }
            .forEach { file ->
                memory.remove(file.name.removeSuffix(".jpg"))
                file.delete()
            }
    }

    internal companion object {
        const val DIRECTORY = "thumbnails"
        const val MAX_WIDTH = 320
        const val TARGET_BYTES = 50 * 1024
        private const val MEMORY_CACHE_BYTES = 4 * 1024 * 1024
        private const val BYTES_PER_PIXEL = 4
        private val SAFE_ID = Regex("^[A-Za-z0-9_-]{1,128}$")
    }
}

/** A JPEG at most 320 px wide, the quality lowered until it is about 50 KB. */
internal fun encodeThumbnail(image: ImageBitmap): ByteArray? {
    val source = image.asAndroidBitmap()
    if (source.width <= 0 || source.height <= 0) return null
    val scaled = if (source.width > SavedDownloadThumbnails.MAX_WIDTH) {
        val height = (source.height.toLong() * SavedDownloadThumbnails.MAX_WIDTH / source.width)
            .toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(source, SavedDownloadThumbnails.MAX_WIDTH, height, true)
    } else {
        source
    }
    var bytes: ByteArray? = null
    for (quality in JPEG_QUALITIES) {
        val out = ByteArrayOutputStream()
        if (!scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) return null
        bytes = out.toByteArray()
        if (bytes.size <= SavedDownloadThumbnails.TARGET_BYTES) break
    }
    return bytes
}

internal fun decodeThumbnail(file: File): ImageBitmap? =
    BitmapFactory.decodeFile(file.path)?.asImageBitmap()

private val JPEG_QUALITIES = listOf(80, 65, 50)
