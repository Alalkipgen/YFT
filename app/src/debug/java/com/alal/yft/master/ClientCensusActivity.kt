package com.alal.yft.master

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.webkit.WebSettings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.alal.yft.BuildConfig
import com.alal.yft.core.browser.policy.BrowserUserAgent
import com.alal.yft.core.data.network.NetworkConfiguration
import com.alal.yft.detection.OkHttpExtractorClient
import com.alal.yft.extractor.master.android.solver.WebViewOwnSolverEngine
import com.alal.yft.extractor.master.modules.youtube.ladder.ClientCensus
import com.alal.yft.extractor.master.solver.OwnPlayerScriptRunner
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Request
import okhttp3.Response

/**
 * Owner tool, debug build only (Phase 1.3 D0): "YFT client census" runs [ClientCensus] on the
 * phone without a computer, in two modes — **Full census** (videos 1–10, every client, the version
 * sources; ≤ 6 MB) and **Weekly check** (≤ 60 s, ≤ 2 MB, ≤ 20 requests). Its launcher entry exists
 * only when the build has `yft.masterCapture` and `yft.ownSolver` on (manifest placeholder
 * `yftOwnerTools`). A run starts only on a tap and belongs to this screen (guardrail 11: nothing
 * is scheduled, closing the tool ends it); the report, which never holds an address, a value or
 * a video ID, is shown with a copy button, and the run's summary is kept in
 * `files/master/census-last.json` for the next diff.
 */
class ClientCensusActivity : ComponentActivity() {
    private var running = false
    private var job: Job? = null
    private var status: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        fun add(view: View) = column.addView(view, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        add(TextView(this).apply {
            text = INTRO
            textSize = 15f
        })
        val preferences = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        for ((slot, id) in ClientCensus.FIXED_VIDEOS) {
            add(TextView(this).apply {
                text = "$slot · ${ClientCensus.SLOT_HINTS[slot]}: $id"
                textSize = 14f
            })
        }
        val fields = (ClientCensus.FIXED_VIDEOS.size + 1..ClientCensus.MAX_VIDEOS).associateWith { slot ->
            EditText(this).apply {
                hint = "$slot · ${ClientCensus.SLOT_HINTS[slot]} (ID or link)"
                isSingleLine = true
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                setText(
                    preferences.getString(key(slot), null)?.takeIf(String::isNotBlank)
                        ?: ClientCensus.DEFAULT_VIDEOS[slot].orEmpty(),
                )
            }.also(::add)
        }
        val full = Button(this).apply { text = "Full census" }
        val weekly = Button(this).apply { text = "Weekly check" }
        val status = TextView(this).apply { textSize = 15f }
        this.status = status
        val result = TextView(this).apply {
            setTextIsSelectable(true)
            typeface = Typeface.MONOSPACE
            textSize = 12f
        }
        val copy = Button(this).apply {
            text = "Copy result"
            isEnabled = false
        }
        listOf(full, weekly, status, result, copy).forEach(::add)
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(column)
        })

        val stateFile = File(filesDir, STATE_PATH)
        status.text = ClientCensus.statusOf(readState(stateFile), System.currentTimeMillis()) + networkWarning()
        if (!(BuildConfig.MASTER_CAPTURE_ENABLED && BuildConfig.OWN_SOLVER_ENABLED)) {
            status.text = "Turned off in this build (needs yft.masterCapture and yft.ownSolver)."
            full.isEnabled = false
            weekly.isEnabled = false
            return
        }

        fun start(mode: ClientCensus.Mode) {
            if (running) return
            val videos = LinkedHashMap(ClientCensus.FIXED_VIDEOS)
            for ((slot, field) in fields) {
                val text = field.text.toString().trim()
                if (text.isEmpty()) continue
                val id = ClientCensus.videoIdOf(text)
                if (id == null) {
                    status.text = "Video $slot: not a video ID or a YouTube link."
                    return
                }
                videos[slot] = id
            }
            preferences.edit().apply {
                fields.forEach { (slot, field) -> putString(key(slot), field.text.toString().trim()) }
            }.apply()
            running = true
            full.isEnabled = false
            weekly.isEnabled = false
            copy.isEnabled = false
            result.text = ""
            status.text = "Starting…"
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            job = lifecycleScope.launch {
                try {
                    val network = networkNote()
                    val outcome = withContext(Dispatchers.Default) {
                        census().run(mode, videos, readState(stateFile), listOf(network)) { step ->
                            runOnUiThread { status.text = step }
                        }
                    }
                    if (outcome.stopped != "cooldown") writeState(stateFile, outcome.stateJson)
                    status.text = outcome.firstLine + "\nTap Copy result and send it."
                    result.text = outcome.report
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Exception) {
                    status.text = "Stopped (${failure.javaClass.simpleName}). Nothing was saved."
                    result.text = "{\"tool\":\"${ClientCensus.TOOL_LABEL}\",\"error\":\"${failure.javaClass.simpleName}\"}"
                } finally {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    running = false
                    full.isEnabled = true
                    weekly.isEnabled = true
                    copy.isEnabled = result.text.isNotEmpty()
                }
            }
        }
        full.setOnClickListener { start(ClientCensus.Mode.FULL) }
        weekly.setOnClickListener { start(ClientCensus.Mode.WEEKLY) }
        copy.setOnClickListener {
            getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText(ClientCensus.TOOL_LABEL, result.text))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }
    }

    /** A run belongs to this screen (guardrail 11): leaving it ends the run; turning the phone does not. */
    override fun onStop() {
        super.onStop()
        if (running && !isChangingConfigurations) {
            job?.cancel()
            status?.text = "Stopped: the tool was left before the end; nothing was saved. Start again."
        }
    }

    /**
     * The census's own client: the HTTP client class and settings of Master's lookups (the app's
     * [NetworkConfiguration] client under [OkHttpExtractorClient]: no cookie, no redirect followed
     * blindly, the same timeouts), but no retry (a retry would only repeat a failed request; the
     * census records the failure), with its wire bytes counted for the budget. The browser's
     * User-Agent is the one YFT's browser tab sends ([BrowserUserAgent]: the WebView's without
     * `; wv` and `Version/4.0`), on the watch page, the embedded player and the media checks.
     */
    private fun census(): ClientCensus {
        val bytes = AtomicLong()
        val meter = object : EventListener() {
            override fun requestHeadersEnd(call: Call, request: Request) {
                bytes.addAndGet(request.headers.byteCount())
            }

            override fun requestBodyEnd(call: Call, byteCount: Long) {
                bytes.addAndGet(byteCount)
            }

            override fun responseHeadersEnd(call: Call, response: Response) {
                bytes.addAndGet(response.headers.byteCount())
            }

            override fun responseBodyEnd(call: Call, byteCount: Long) {
                bytes.addAndGet(byteCount)
            }
        }
        val http = OkHttpExtractorClient(
            client = NetworkConfiguration.createClient().newBuilder().eventListener(meter).build(),
            policy = OkHttpExtractorClient.Policy(callTimeoutSeconds = 60, retryDelaysMillis = emptyList()),
        )
        return ClientCensus(
            http = http,
            scripts = OwnPlayerScriptRunner(http, WebViewOwnSolverEngine(applicationContext)),
            browserUserAgent = runCatching { BrowserUserAgent.from(WebSettings.getDefaultUserAgent(applicationContext)) }
                .getOrDefault(""),
            meter = ClientCensus.Meter { bytes.get() },
        )
    }

    private fun readState(file: File): String? = runCatching { file.takeIf(File::isFile)?.readText() }.getOrNull()

    /** Written whole, then moved over the last state, so a stopped write never leaves half a file. */
    private fun writeState(file: File, text: String) {
        runCatching {
            file.parentFile?.mkdirs()
            val next = File(file.parentFile, file.name + ".tmp")
            next.writeText(text)
            if (!next.renameTo(file)) {
                file.delete()
                next.renameTo(file)
            }
        }
    }

    private fun onWifi(): Boolean = runCatching {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }.getOrDefault(false)

    private fun networkNote(): String = if (onWifi()) "network: Wi-Fi (the census is measured on mobile data)" else "network: mobile data"

    private fun networkWarning(): String = if (onWifi()) "\nWi-Fi is on: turn it off, the census runs on mobile data." else ""

    private fun key(slot: Int) = "video-$slot"

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val PREFERENCES = "client-census"
        const val STATE_PATH = "master/census-last.json"
        const val INTRO = "YFT client census (owner tool, debug build)\n\n" +
            "Full census: for each video, YouTube's visionOS, Android, embedded and mobile web answers, " +
            "a one-byte check of one stream each, the HLS playlist, and the public version sources once " +
            "(at most 6 MB). Run it 3 times on mobile data (no Wi-Fi), at least an hour apart.\n" +
            "Weekly check: videos 1 and 5 and one answer per client (at most 60 s, 2 MB, 20 requests); " +
            "the first line says NO CHANGE, CHANGED or BROKEN.\n" +
            "After a bot check on a video it sends only what YFT's normal lookup sends next (that video's " +
            "page and visionOS once more with the page's visitor data), then goes on with the next video. " +
            "A bot check on a request that already has visitor data, or HTTP 429, stops the run, and no run " +
            "starts for 30 min.\n" +
            "Nothing runs unless you tap, and only while this screen stays open (leaving it stops the run). " +
            "The result holds no " +
            "address, no value and no video ID.\n\nVideos (1-3 are fixed):"
    }
}
