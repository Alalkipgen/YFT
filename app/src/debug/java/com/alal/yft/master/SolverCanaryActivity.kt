package com.alal.yft.master

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Typeface
import android.os.Bundle
import android.os.Process
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.alal.yft.BuildConfig
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owner tool, debug build only (Phase 1.2 N7): runs [SolverCanary] on the phone without a
 * computer. Its launcher entry ("YFT solver canary") exists only when the build has
 * `yft.masterCapture`, `yft.ownSolver` and `yft.ownSolverNext` on (manifest placeholder
 * `yftOwnerToolsNext`); it starts only on a tap and shows the report, which never holds an address
 * or a value, with a copy button. "Close app" ends the app's process, so the next run measures
 * the after-restart numbers.
 */
class SolverCanaryActivity : ComponentActivity() {
    private lateinit var column: LinearLayout
    private var ticker: WebView? = null
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = dp(16)
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun add(view: View) = column.addView(view, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val intro = TextView(this).apply {
            text = INTRO
            textSize = 15f
        }
        val start = Button(this).apply { text = "Run" }
        val status = TextView(this).apply { textSize = 15f }
        val result = TextView(this).apply {
            setTextIsSelectable(true)
            typeface = Typeface.MONOSPACE
            textSize = 12f
        }
        val copy = Button(this).apply {
            text = "Copy report"
            isEnabled = false
        }
        val close = Button(this).apply { text = "Close app" }
        listOf(intro, start, status, result, copy, close).forEach(::add)
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(column)
        })

        close.setOnClickListener {
            if (running) return@setOnClickListener
            // A new process for the next run: the after-restart numbers (no job is running).
            finishAffinity()
            Process.killProcess(Process.myPid())
        }
        copy.setOnClickListener {
            getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText(SolverCanaryReport.TITLE, result.text))
            Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show()
        }
        if (!(BuildConfig.MASTER_CAPTURE_ENABLED && BuildConfig.OWN_SOLVER_ENABLED && BuildConfig.OWN_SOLVER_NEXT_ENABLED)) {
            status.text = "This build has the own solver ladder off: it needs yft.masterCapture, yft.ownSolver " +
                "and yft.ownSolverNext on."
            start.isEnabled = false
            return
        }
        start.setOnClickListener {
            if (running) return@setOnClickListener
            running = true
            start.isEnabled = false
            copy.isEnabled = false
            close.isEnabled = false
            result.text = ""
            status.text = "Starting…"
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            lifecycleScope.launch {
                val report = try {
                    val canary = SolverCanary(
                        context = applicationContext,
                        stall = stallMeter,
                        onStep = { step -> runOnUiThread { status.text = step } },
                    )
                    val outcome = withContext(Dispatchers.Default) { canary.run() }
                    status.text = if (SolverCanaryReport.passes(outcome)) {
                        "Done: PASS. Copy the report; then Close app, open the tool again and Run once more."
                    } else {
                        "Done: FAIL. Copy the report and send it."
                    }
                    SolverCanaryReport.render(outcome)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Exception) {
                    status.text = "Stopped by an error. Copy the report and send it."
                    "${SolverCanaryReport.TITLE}: FAIL · error " +
                        SolverCanaryReport.safe(failure.javaClass.simpleName.ifEmpty { "Exception" })
                } finally {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    running = false
                    start.isEnabled = true
                    close.isEnabled = true
                }
                result.text = report
                copy.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        ticker?.destroy()
        ticker = null
        super.onDestroy()
    }

    /**
     * A visible WebView with a 50 ms timer that keeps its longest gap: while a page job runs, how
     * long a page in the app's browser would stand still. Made only before the first page step,
     * so the lookup's engine start stays cold. It loads no address. After the steps it also gives
     * the renderer's JS heap limit (N7 round 2).
     */
    private val stallMeter = object : SolverCanary.StallMeter {
        @SuppressLint("SetJavaScriptEnabled")
        override suspend fun prepare() {
            withContext(Dispatchers.Main) {
                if (ticker != null) return@withContext
                val loaded = CompletableDeferred<Unit>()
                val view = WebView(this@SolverCanaryActivity)
                view.settings.javaScriptEnabled = true
                view.settings.blockNetworkLoads = true
                view.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        loaded.complete(Unit)
                    }
                }
                column.addView(view, 0, LinearLayout.LayoutParams(MATCH_PARENT, dp(TICKER_HEIGHT_DP)))
                ticker = view
                view.loadDataWithBaseURL(null, TICKER_PAGE, "text/html", "utf-8", null)
                withTimeoutOrNull(TICKER_LOAD_MILLIS) { loaded.await() }
            }
        }

        override suspend fun reset() {
            evaluate("yftReset()")
        }

        override suspend fun read(): Long? = evaluate("yftMax()")?.trim('"')?.toDoubleOrNull()?.toLong()

        // Same renderer as the solver's pages (one per app); asked after the steps.
        override suspend fun heapLimitBytes(): Long? {
            prepare()
            return evaluate("yftHeapLimit()")?.trim('"')?.toDoubleOrNull()?.takeIf { it > 0 }?.toLong()
        }
    }

    private suspend fun evaluate(script: String): String? = withContext(Dispatchers.Main) {
        val view = ticker ?: return@withContext null
        withTimeoutOrNull(EVALUATE_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                view.evaluateJavascript(script) { value -> if (continuation.isActive) continuation.resume(value) }
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val TICKER_HEIGHT_DP = 40
        const val TICKER_LOAD_MILLIS = 10_000L
        const val EVALUATE_TIMEOUT_MILLIS = 5_000L

        const val INTRO = "Owner tool (Phase 1.2 N7). Runs today's YouTube player through ejs and the own " +
            "solver's steps (P, B and the page) on this phone, and one app lookup of a public video with " +
            "the live check V. Data: about 2.5 MB the first time, about 1 MB after. Keep this screen " +
            "open while it runs (1 to 3 minutes).\n\n" +
            "1. Run, then Copy report.\n" +
            "2. Close app, open YFT solver canary again, Run, Copy report.\n\n" +
            "The report holds no addresses and no values. A step that times out stops the run " +
            "(\"stopped at ...\") and stays off for this player version until the app restarts " +
            "(after two crashes in a row: until YouTube's next player)."

        const val TICKER_PAGE = "<!doctype html><html><body style=\"margin:0;font:12px monospace\">" +
            "<div id=\"t\">stall meter</div><script>" +
            "var last = Date.now(), worst = 0;" +
            "setInterval(function () { var now = Date.now(); var gap = now - last; if (gap > worst) worst = gap;" +
            " last = now; document.getElementById('t').textContent = 'stall meter: longest gap ' + worst + ' ms'; }, 50);" +
            "function yftReset() { last = Date.now(); worst = 0; return 0; }" +
            "function yftMax() { var gap = Date.now() - last; return gap > worst ? gap : worst; }" +
            "function yftHeapLimit() { try { var m = performance.memory;" +
            " return m && typeof m.jsHeapSizeLimit === 'number' ? m.jsHeapSizeLimit : -1; } catch (e) { return -1; } }" +
            "</script></body></html>"
    }
}
