package com.alal.yft.master

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.alal.yft.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Owner tool, debug build only (Phase 1.2 N2): runs [StreamCheckMeasurement] on the phone without
 * a computer. Its launcher entry exists only when the build has `yft.masterCapture` and
 * `yft.ownSolver` on (manifest placeholder `yftOwnerTools`); it starts only on a tap and shows the
 * report, which never holds an address or a value, with a copy button.
 */
class StreamCheckMeasureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun add(view: android.view.View) = column.addView(view, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val intro = TextView(this).apply {
            text = "YFT stream check (owner tool, debug build)\n\n" +
                "Measures how YouTube answers one stream with the right n, an unchanged n, a wrong n " +
                "and a wrong signature. Sends 1 page request, 1-2 player requests (embedded, then mobile web), 2 player script " +
                "downloads and 5 stream reads of at most 1 MiB each (about 10-15 MB of data). " +
                "The result holds no address and no value.\n\nVideo ID (public, embeddable):"
            textSize = 15f
        }
        val video = EditText(this).apply {
            setText(StreamCheckMeasurement.DEFAULT_VIDEO)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        val start = Button(this).apply { text = "Start" }
        val status = TextView(this).apply { textSize = 15f }
        val result = TextView(this).apply {
            setTextIsSelectable(true)
            typeface = Typeface.MONOSPACE
            textSize = 13f
        }
        val copy = Button(this).apply {
            text = "Copy result"
            isEnabled = false
        }
        listOf(intro, video, start, status, result, copy).forEach(::add)
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(column)
        })

        if (!(BuildConfig.MASTER_CAPTURE_ENABLED && BuildConfig.OWN_SOLVER_ENABLED)) {
            status.text = "Turned off in this build (needs yft.masterCapture and yft.ownSolver)."
            start.isEnabled = false
            return
        }
        start.setOnClickListener {
            val id = video.text.toString().trim()
            if (!StreamCheckMeasurement.VIDEO_ID.matches(id)) {
                status.text = "Not a video ID (11 letters, digits, - or _)."
                return@setOnClickListener
            }
            start.isEnabled = false
            copy.isEnabled = false
            result.text = ""
            status.text = "Starting…"
            lifecycleScope.launch {
                val report = try {
                    val outcome = StreamCheckMeasurement(applicationContext).run(id) { step -> status.text = step }
                    status.text = if (outcome.rightOpened) {
                        "Done (n rule ${outcome.nRule}). Tap Copy result and send it."
                    } else {
                        "Done, but the right values did not open the stream; send the result anyway."
                    }
                    outcome.report
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: StreamCheckMeasurement.Failure) {
                    status.text = "Stopped. Tap Copy result and send it."
                    errorReport(failure.message.orEmpty())
                } catch (failure: Exception) {
                    status.text = "Stopped. Tap Copy result and send it."
                    errorReport(failure.javaClass.simpleName.ifEmpty { "Exception" })
                }
                result.text = report
                copy.isEnabled = true
                start.isEnabled = true
            }
        }
        copy.setOnClickListener {
            getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("YFT stream check", result.text))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }
    }

    /** Only letters, digits and a few signs: never an address or a value. */
    private fun errorReport(reason: String): String =
        "{\"measure\":\"stream-check\",\"error\":\"" +
            reason.filter { it.isLetterOrDigit() || it in " :-_.()/;," }.take(400) + "\"}"
}
