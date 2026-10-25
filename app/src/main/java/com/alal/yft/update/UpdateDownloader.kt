package com.alal.yft.update

import android.util.Log
import com.alal.yft.update.UpdatePolicy.toHex
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** What the release's `SHA256SUMS` gave for its APK (§4.4 step 3). */
sealed interface SumsResult {
    /** The APK's SHA-256, uppercase hex. */
    data class Found(val sha256: String) : SumsResult

    /** No file, or no single line for the APK: nothing to check it against. */
    data object Missing : SumsResult

    /** No connection or a timeout. */
    data object Offline : SumsResult

    /** Any other answer: another status, an oversize file, a refused address. */
    data object Failed : SumsResult
}

/** What downloading the APK gave (§4.4 step 4). */
sealed interface DownloadResult {
    /** The file, its size and SHA-256 those of the release. */
    data class Done(val file: File) : DownloadResult

    /** Another size or hash: the file is deleted. */
    data object Damaged : DownloadResult

    /** The connection broke or timed out: the file is deleted. */
    data object Offline : DownloadResult

    /** Any other answer or a refused address: the file is deleted. */
    data object Failed : DownloadResult
}

/** The release's checksum and APK; [UpdateDownloader] in the app, fakes in tests. */
interface ApkSource {
    suspend fun checksum(update: FoundUpdate): SumsResult

    /** Cancelling the caller stops the download and deletes the file. */
    suspend fun download(
        update: FoundUpdate,
        sha256: String,
        onProgress: (percent: Int) -> Unit,
    ): DownloadResult

    /** Deletes the downloaded APK, if any (a failed check, a finished session, a new start). */
    fun discard()
}

/**
 * Steps 3 and 4 of §4.4: the release's `SHA256SUMS` (at most [Sha256Sums.MAX_BYTES]) and its APK
 * (exactly the asset's size, at most [MAX_APK_BYTES]) over [UpdateHttp] — HTTPS to GitHub's hosts
 * only, redirects checked one by one, no cookie, no cache. The APK goes to one fixed file in the
 * app's cache, its SHA-256 computed while it is written; another size or hash deletes it. Logs
 * carry the host and the status only, never an address.
 */
class UpdateDownloader internal constructor(
    client: OkHttpClient,
    allowedHosts: Set<String>,
    private val directory: File,
    private val ioDispatcher: CoroutineDispatcher,
    private val log: (String) -> Unit,
) : ApkSource {
    constructor(client: OkHttpClient, cacheDir: File) : this(
        client = client,
        allowedHosts = UpdateHttp.HOSTS,
        directory = File(cacheDir, DIRECTORY),
        ioDispatcher = Dispatchers.IO,
        log = { message -> Log.i(TAG, message) },
    )

    private val sumsHttp = UpdateHttp(
        client = client.newBuilder()
            .connectTimeout(UpdateChecker.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(UpdateChecker.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build(),
        allowedHosts = allowedHosts,
    )

    // No limit on the whole call (a slow line may need minutes); a stalled read gives up.
    private val apkHttp = UpdateHttp(
        client = client.newBuilder()
            .connectTimeout(UpdateChecker.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build(),
        allowedHosts = allowedHosts,
    )

    /** The one place the APK is kept: `cacheDir/updates/yft-update.apk`. */
    val file: File
        get() = File(directory, FILE_NAME)

    override suspend fun checksum(update: FoundUpdate): SumsResult {
        val url = update.sumsUrl?.toHttpUrlOrNull() ?: return SumsResult.Missing
        return withContext(ioDispatcher) {
            try {
                sumsHttp.execute(Request.Builder().url(url).get().build()).use { response ->
                    log("Update checksum: ${response.request.url.host} ${response.code}")
                    when (response.code) {
                        HTTP_OK -> response.body
                            ?.let { readCapped(it, Sha256Sums.MAX_BYTES) }
                            ?.let { text ->
                                Sha256Sums.find(text, update.version.apkName)
                                    ?.let { SumsResult.Found(it) }
                                    ?: SumsResult.Missing
                            }
                            ?: SumsResult.Failed
                        HTTP_NOT_FOUND -> SumsResult.Missing
                        else -> SumsResult.Failed
                    }
                }
            } catch (refused: UpdateHttp.Refused) {
                log("Update checksum refused: ${refused.message}")
                SumsResult.Failed
            } catch (failure: IOException) {
                log("Update checksum offline: ${failure.javaClass.simpleName}")
                SumsResult.Offline
            }
        }
    }

    override suspend fun download(
        update: FoundUpdate,
        sha256: String,
        onProgress: (percent: Int) -> Unit,
    ): DownloadResult {
        val url = update.apkUrl.toHttpUrlOrNull() ?: return DownloadResult.Failed
        val size = update.apkSize
        if (size <= 0 || size > MAX_APK_BYTES) return DownloadResult.Failed
        val request = Request.Builder().url(url).get().build()
        var result: DownloadResult? = null
        try {
            val done = coroutineScope {
                val call = AtomicReference<Call?>(null)
                // A blocked read does not see cancellation; cancelling its call ends it at once.
                val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        call.get()?.cancel()
                    }
                }
                try {
                    withContext(ioDispatcher) { fetch(request, size, sha256, call, onProgress) }
                } finally {
                    watcher.cancel()
                }
            }
            result = done
            return done
        } finally {
            // A failure, another hash or a cancel: no file is left.
            if (result !is DownloadResult.Done) discard()
        }
    }

    private suspend fun fetch(
        request: Request,
        size: Long,
        sha256: String,
        call: AtomicReference<Call?>,
        onProgress: (Int) -> Unit,
    ): DownloadResult = try {
        apkHttp.execute(request) { call.set(it) }.use { response ->
            log("Update download: ${response.request.url.host} ${response.code}")
            val body = response.body
            val declared = body?.contentLength() ?: -1L
            when {
                response.code != HTTP_OK || body == null -> DownloadResult.Failed
                declared != -1L && declared != size -> DownloadResult.Damaged
                else -> write(body.byteStream(), size, sha256, onProgress)
            }
        }
    } catch (refused: UpdateHttp.Refused) {
        log("Update download refused: ${refused.message}")
        DownloadResult.Failed
    } catch (failure: IOException) {
        log("Update download offline: ${failure.javaClass.simpleName}")
        DownloadResult.Offline
    }

    /** Writes the body to [file], hashing it on the way; exactly [size] bytes or it is damaged. */
    private suspend fun write(
        input: InputStream,
        size: Long,
        sha256: String,
        onProgress: (Int) -> Unit,
    ): DownloadResult {
        directory.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_BYTES)
        var written = 0L
        var percent = -1
        FileOutputStream(file).use { output ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read == -1) break
                written += read
                if (written > size) return DownloadResult.Damaged
                digest.update(buffer, 0, read)
                output.write(buffer, 0, read)
                val now = (written * PERCENT / size).toInt()
                if (now != percent) {
                    percent = now
                    onProgress(now)
                }
            }
            output.fd.sync()
        }
        if (written != size) return DownloadResult.Damaged
        val hash = digest.digest().toHex()
        return if (hash.equals(sha256, ignoreCase = true)) {
            DownloadResult.Done(file)
        } else {
            DownloadResult.Damaged
        }
    }

    override fun discard() {
        file.delete()
    }

    companion object {
        const val DIRECTORY = "updates"
        const val FILE_NAME = "yft-update.apk"
        const val MAX_APK_BYTES = 100L * 1024 * 1024
        const val READ_TIMEOUT_SECONDS = 30L
        private const val HTTP_OK = 200
        private const val HTTP_NOT_FOUND = 404
        private const val BUFFER_BYTES = 64 * 1024
        private const val PERCENT = 100
        private const val TAG = "YftUpdate"
    }
}
