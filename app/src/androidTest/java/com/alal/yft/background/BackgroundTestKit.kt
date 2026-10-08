package com.alal.yft.background

import android.Manifest
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.core.download.AudioVideoMuxRunner
import com.alal.yft.core.download.DashTransferRunner
import com.alal.yft.core.download.DefaultDownloadTransferDispatcher
import com.alal.yft.core.download.DirectTransferRunner
import com.alal.yft.core.download.DownloadDestination
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.download.DownloadTaskStore
import com.alal.yft.core.download.DownloadTransferDispatcher
import com.alal.yft.core.download.HlsTransferRunner
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.AudioVideoMuxCheckpoint
import com.alal.yft.core.model.download.AudioVideoMuxDownloadPlan
import com.alal.yft.core.model.download.AudioVideoMuxResult
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DashTransferCheckpoint
import com.alal.yft.core.model.download.DashTransferResult
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DirectTransferResult
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.HlsDownloadPlan
import com.alal.yft.core.model.download.HlsTransferCheckpoint
import com.alal.yft.core.model.download.HlsTransferResult
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.download.DownloadForegroundService
import com.alal.yft.download.DownloadNotificationFactory
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.fail

/**
 * What P34's background tests share: a queue the download service runs on instead of the app's
 * ([DownloadForegroundService.testQueue]), an in-process "server" that sends a file slowly (the
 * app's network rules forbid a local cleartext or self-signed server), and readers for the
 * service and its notifications.
 */
internal object BackgroundTestKit {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context: Context = instrumentation.targetContext

    /** Android 13+: the notifications must be allowed for the test to read them. */
    fun allowNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }

    fun queue(
        dispatcher: DownloadTransferDispatcher,
        scope: kotlinx.coroutines.CoroutineScope,
    ): DownloadQueue = DownloadQueue(
        store = MemoryTaskStore(),
        transferDispatcher = dispatcher,
        scope = scope,
    )

    fun dispatcher(
        direct: DirectTransferRunner = Unused,
        mux: AudioVideoMuxRunner = Unused,
    ): DownloadTransferDispatcher = DefaultDownloadTransferDispatcher(
        direct = direct,
        hls = Unused,
        dash = Unused,
        mux = mux,
    )

    fun task(queue: DownloadQueue, id: String): StoredDownloadTask? =
        queue.tasks.value.firstOrNull { it.id == id }

    /** The title and text of the notification with [id] (and [tag]), or null when none shows. */
    fun notificationText(id: Int, tag: String? = null): String? =
        notification(id, tag)?.let { notification ->
            listOfNotNull(
                notification.extras.getCharSequence(Notification.EXTRA_TITLE),
                notification.extras.getCharSequence(Notification.EXTRA_TEXT),
            ).joinToString(" | ")
        }

    private fun notification(id: Int, tag: String?): Notification? =
        context.getSystemService(NotificationManager::class.java)
            .activeNotifications
            .firstOrNull { it.id == id && it.tag == tag }
            ?.notification

    /** The download service runs in the foreground. */
    @Suppress("DEPRECATION")
    fun serviceInForeground(): Boolean =
        context.getSystemService(ActivityManager::class.java)
            .getRunningServices(MAX_SERVICES)
            .any { info ->
                info.service.className == DownloadForegroundService::class.java.name &&
                    info.foreground
            }

    /** Stops the service and removes what the test posted, also after a failure. */
    fun cleanUp(queue: DownloadQueue?) {
        DownloadForegroundService.testQueue = null
        queue?.let { kotlinx.coroutines.runBlocking { it.pauseAll() } }
        context.stopService(Intent(context, DownloadForegroundService::class.java))
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications
            .filter { it.id != DownloadNotificationFactory.NOTIFICATION_ID }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    fun <T : Any> waitFor(timeoutMs: Long, what: String, probe: () -> T?): T {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            probe()?.let { return it }
            if (SystemClock.elapsedRealtime() > end) fail("Timed out after $timeoutMs ms: $what")
            Thread.sleep(POLL_MS)
        }
    }

    fun diag(name: String, vararg values: Pair<String, String>) {
        Log.i("YFT-DIAG", "p34-$name " + values.joinToString(" ") { (k, v) -> "$k=$v" })
    }

    /** Answers every request with [length] zero bytes at [bytesPerSecond]; no ranges. */
    fun slowClient(length: Long, bytesPerSecond: Long): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", VIDEO_MP4)
                    .header("Content-Length", length.toString())
                    .body(SlowBody(length, bytesPerSecond))
                    .build()
            },
        )
        .build()

    fun metadata(url: String, length: Long, name: String) = RemoteFileMetadata(
        finalUrl = url,
        totalBytes = length,
        supportsByteRanges = false,
        entityTag = null,
        lastModified = null,
        contentType = VIDEO_MP4,
        suggestedFileName = name,
    )

    const val VIDEO_MP4 = "video/mp4"
    private const val MAX_SERVICES = 100
    private const val POLL_MS = 250L
}

/** A body that trickles out: [bytesPerSecond] in small chunks, like a slow connection. */
private class SlowBody(private val length: Long, private val bytesPerSecond: Long) :
    ResponseBody() {
    private val slow: BufferedSource by lazy {
        object : Source {
            private var left = length

            override fun read(sink: Buffer, byteCount: Long): Long {
                if (left <= 0L) return -1L
                val count = minOf(byteCount, CHUNK_BYTES, left)
                Thread.sleep(count * MILLIS_PER_SECOND / bytesPerSecond)
                sink.write(ByteArray(count.toInt()))
                left -= count
                return count
            }

            override fun timeout(): Timeout = Timeout.NONE

            override fun close() = Unit
        }.buffer()
    }

    override fun contentType() = BackgroundTestKit.VIDEO_MP4.toMediaType()

    override fun contentLength(): Long = length

    override fun source(): BufferedSource = slow

    private companion object {
        const val CHUNK_BYTES = 16L * 1_024
        const val MILLIS_PER_SECOND = 1_000L
    }
}

private class MemoryTaskStore : DownloadTaskStore {
    private val tasks = java.util.concurrent.ConcurrentHashMap<String, StoredDownloadTask>()

    override suspend fun loadAll(): List<StoredDownloadTask> = tasks.values.toList()

    override suspend fun save(task: StoredDownloadTask) {
        tasks[task.id] = task
    }

    override suspend fun delete(id: String) {
        tasks.remove(id)
    }
}

/** The kinds of transfer a test does not use. */
private object Unused :
    DirectTransferRunner,
    HlsTransferRunner,
    DashTransferRunner,
    AudioVideoMuxRunner {
    override suspend fun transfer(
        plan: DirectDownloadPlan,
        metadata: RemoteFileMetadata,
        destination: DownloadDestination,
        resumeFrom: DirectTransferCheckpoint?,
        onProgress: suspend (DownloadProgress) -> Unit,
        onCheckpoint: suspend (DirectTransferCheckpoint) -> Unit,
    ): DirectTransferResult = error("Not used in this test")

    override suspend fun transfer(
        plan: HlsDownloadPlan,
        destination: DownloadDestination,
        resumeFrom: HlsTransferCheckpoint?,
        onProgress: suspend (DownloadProgress) -> Unit,
        onCheckpoint: suspend (HlsTransferCheckpoint) -> Unit,
    ): HlsTransferResult = error("Not used in this test")

    override suspend fun discard(plan: HlsDownloadPlan) = Unit

    override suspend fun transfer(
        plan: DashDownloadPlan,
        destination: DownloadDestination,
        resumeFrom: DashTransferCheckpoint?,
        onProgress: suspend (DownloadProgress) -> Unit,
        onCheckpoint: suspend (DashTransferCheckpoint) -> Unit,
    ): DashTransferResult = error("Not used in this test")

    override suspend fun discard(plan: DashDownloadPlan) = Unit

    override suspend fun transfer(
        plan: AudioVideoMuxDownloadPlan,
        destination: DownloadDestination,
        resumeFrom: AudioVideoMuxCheckpoint?,
        onProgress: suspend (DownloadProgress) -> Unit,
        onCheckpoint: suspend (AudioVideoMuxCheckpoint) -> Unit,
    ): AudioVideoMuxResult = error("Not used in this test")

    override suspend fun discard(plan: AudioVideoMuxDownloadPlan) = Unit
}
