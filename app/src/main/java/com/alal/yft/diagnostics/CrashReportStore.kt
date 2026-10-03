package com.alal.yft.diagnostics

import android.content.Context
import android.os.Build
import com.alal.yft.BuildConfig
import com.alal.yft.core.model.logging.DiagnosticTextSanitizer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale
import java.util.TimeZone

/** One bounded report in no-backup storage. This class has no network or logging capability. */
class CrashReportStore internal constructor(
    noBackupDir: File,
    private val environment: Environment,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    constructor(context: Context) : this(
        noBackupDir = context.applicationContext.noBackupFilesDir,
        environment = Environment(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            sdk = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
        ),
    )

    internal data class Environment(
        val versionName: String,
        val versionCode: Int,
        val sdk: Int,
        val manufacturer: String,
        val model: String,
    )

    private val directory = File(noBackupDir, "diagnostics")
    private val reportFile = File(directory, "last-crash.txt")
    private val pendingFile = File(directory, "last-crash.tmp")

    @Synchronized
    fun write(thread: Thread, error: Throwable) {
        val report = ReportBuffer()
        val time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(clock())
        report.line("YFT local crash report")
        report.line("UTC: $time")
        report.field("Version", "${environment.versionName} (${environment.versionCode})")
        report.field("Android SDK", "${environment.sdk}")
        report.field("Device", "${environment.manufacturer} ${environment.model}")
        report.field("Thread", thread.name)
        report.line("")

        val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        fun appendException(failure: Throwable, prefix: String, depth: Int) {
            if (report.full) return
            if (depth >= MAX_CHAIN_DEPTH || !visited.add(failure)) {
                report.line("$prefix[exception chain omitted]")
                return
            }
            val message = DiagnosticTextSanitizer.redact(
                failure.message.orEmpty().take(MAX_REPORT_BYTES),
            )
            report.line("$prefix${failure.javaClass.name}: $message")
            failure.stackTrace.take(MAX_STACK_FRAMES).forEach { report.line("    at $it") }
            failure.suppressed.forEach { appendException(it, "Suppressed: ", depth + 1) }
            failure.cause?.let { appendException(it, "Caused by: ", depth + 1) }
        }
        appendException(error, "", 0)

        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Crash report directory is unavailable")
        }
        pendingFile.outputStream().use { it.write(report.bytes()) }
        if (!pendingFile.renameTo(reportFile)) {
            pendingFile.delete()
            throw IOException("Crash report could not be saved")
        }
    }

    @Synchronized
    fun read(): String? = try {
        if (!reportFile.isFile) {
            null
        } else {
            val bytes = reportFile.inputStream().use { it.readNBytesCompat(MAX_REPORT_BYTES) }
            // Re-sanitize even an old or manually altered report before exposing it to the UI.
            val safe = DiagnosticTextSanitizer.redact(bytes.toString(Charsets.UTF_8))
            safe.toByteArray(Charsets.UTF_8).boundedUtf8(MAX_REPORT_BYTES)
                .toString(Charsets.UTF_8).takeIf(String::isNotBlank)
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    @Synchronized
    fun delete(): Boolean = try {
        val removed = !reportFile.exists() || reportFile.delete()
        pendingFile.delete()
        removed
    } catch (_: SecurityException) {
        false
    }

    private class ReportBuffer {
        private val output = ByteArrayOutputStream(MAX_REPORT_BYTES)
        var full: Boolean = false
            private set

        fun line(message: String) {
            if (full) return
            val safe = DiagnosticTextSanitizer.redact(message.take(MAX_REPORT_BYTES)) + "\n"
            val bytes = safe.toByteArray(Charsets.UTF_8)
            val remaining = MAX_REPORT_BYTES - output.size()
            output.write(bytes.boundedUtf8(remaining))
            full = bytes.size >= remaining
        }

        fun field(name: String, value: String) {
            line("$name: ${DiagnosticTextSanitizer.redact(value.take(MAX_REPORT_BYTES))}")
        }

        fun bytes(): ByteArray = output.toByteArray()
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4_096)
        while (output.size() < limit) {
            val length = read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (length < 0) break
            output.write(buffer, 0, length)
        }
        return output.toByteArray()
    }

    internal companion object {
        const val MAX_REPORT_BYTES = 64 * 1_024
        private const val MAX_CHAIN_DEPTH = 32
        private const val MAX_STACK_FRAMES = 256

        /** Never leave half a multibyte code point at the byte limit. */
        private fun ByteArray.boundedUtf8(limit: Int): ByteArray {
            if (size <= limit) return this
            var end = limit
            while (end > 0 && (this[end].toInt() and 0xC0) == 0x80) end--
            return copyOf(end)
        }
    }
}
