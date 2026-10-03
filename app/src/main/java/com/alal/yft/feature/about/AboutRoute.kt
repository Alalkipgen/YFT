package com.alal.yft.feature.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.alal.yft.core.model.logging.DiagnosticTextSanitizer
import com.alal.yft.diagnostics.CrashReportStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AboutRoute(onNavigateBack: () -> Unit, onOpenLicenses: () -> Unit = {}) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val store = remember(context) { CrashReportStore(context) }
    val scope = rememberCoroutineScope()
    var report by remember(store) { mutableStateOf<String?>(null) }
    LaunchedEffect(store) {
        report = withContext(Dispatchers.IO) { store.read() }
    }
    AboutScreen(
        onNavigateBack = onNavigateBack,
        onOpenLicenses = onOpenLicenses,
        crashReport = report,
        onCopyCrash = { report?.let { clipboard.setText(AnnotatedString(it)) } },
        onShareCrash = { report?.let { shareCrashReport(context, it) } },
        onDeleteCrash = {
            scope.launch {
                if (withContext(Dispatchers.IO) { store.delete() }) {
                    report = null
                } else {
                    Toast.makeText(
                        context, "Could not delete the report", Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        },
    )
}

/** The chooser is opened only by the explicit Share action; YFT never uploads a report itself. */
internal fun shareCrashReport(context: Context, report: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "YFT local crash report")
        .putExtra(Intent.EXTRA_TEXT, DiagnosticTextSanitizer.redact(report))
    try {
        context.startActivity(
            Intent.createChooser(send, "Share crash report")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app can share text on this device", Toast.LENGTH_SHORT).show()
    }
}
