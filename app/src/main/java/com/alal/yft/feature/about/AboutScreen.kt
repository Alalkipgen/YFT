package com.alal.yft.feature.about

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.alal.yft.BuildConfig
import com.alal.yft.R
import com.alal.yft.ui.components.YftCard
import com.alal.yft.ui.components.YftDivider
import com.alal.yft.ui.components.YftGroupLabel
import com.alal.yft.ui.components.YftIcon
import com.alal.yft.ui.components.YftTextButton
import com.alal.yft.ui.components.YftTopBar
import com.alal.yft.ui.navigation.YftDestination
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftTheme

/**
 * About YFT, opened from Settings → Version: who the app is, what it does and does not do, and
 * its privacy promises, in the same grouped cards as Settings. Licenses have their own page.
 */
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    onOpenLicenses: () -> Unit = {},
    versionName: String = BuildConfig.VERSION_NAME,
    versionCode: Int = BuildConfig.VERSION_CODE,
    crashReport: String? = null,
    onCopyCrash: () -> Unit = {},
    onShareCrash: () -> Unit = {},
    onDeleteCrash: () -> Unit = {},
) {
    val colors = YftTheme.colors
    var viewingCrash by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            YftTopBar(
                title = YftDestination.ABOUT.title,
                canNavigateBack = true,
                onNavigateBack = onNavigateBack,
            )
        },
        containerColor = colors.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)
                .testTag("about-content"),
        ) {
            Identity(versionName = versionName, versionCode = versionCode)
            if (!crashReport.isNullOrBlank()) {
                CrashReportCard(
                    onView = { viewingCrash = true },
                    onCopy = onCopyCrash,
                    onShare = onShareCrash,
                    onDelete = onDeleteCrash,
                )
            }
            PromiseGroup(
                title = "What YFT does",
                lines = DOES,
                icon = YftIcons.Check,
                tag = "about-does",
            )
            PromiseGroup(
                title = "What YFT does not do",
                lines = DOES_NOT,
                icon = YftIcons.Block,
                tag = "about-does-not",
            )
            PromiseGroup(
                title = "Privacy",
                lines = PRIVACY,
                icon = YftIcons.Shield,
                tag = "about-privacy",
            )
            YftGroupLabel(text = "Open source")
            YftCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("about-open-licenses"),
                onClick = onOpenLicenses,
                contentPadding = PaddingValues(0.dp),
            ) {
                LineRow(
                    icon = YftIcons.Document,
                    text = "Licenses",
                    trailingIcon = YftIcons.ChevronRight,
                )
            }
        }
    }
    if (viewingCrash && !crashReport.isNullOrBlank()) {
        CrashReportDialog(report = crashReport, onDismiss = { viewingCrash = false })
    }
}

@Composable
private fun Identity(versionName: String, versionCode: Int) {
    val colors = YftTheme.colors
    YftCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.yft_logo),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )
            Column(
                modifier = Modifier.padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "YFT · Video Downloader",
                    modifier = Modifier.semantics { heading() },
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
                CrashTestTrigger { trigger ->
                    Text(
                        text = "Version $versionName ($versionCode)",
                        modifier = trigger.testTag("about-version"),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Text(
            text = "YFT is an ad-free downloader for media you are allowed to save. It works " +
                "with ordinary, non-DRM media and never tries to get around protection.",
            modifier = Modifier.padding(top = 12.dp),
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CrashReportCard(
    onView: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = YftTheme.colors
    YftGroupLabel(text = "Diagnostics")
    YftCard(modifier = Modifier.fillMaxWidth().testTag("about-crash-report")) {
        Text(
            text = "Last crash report",
            modifier = Modifier.semantics { heading() },
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Stored on this device. Nothing is sent automatically.",
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            YftTextButton("View", onView, Modifier.testTag("about-crash-view"))
            YftTextButton("Copy", onCopy, Modifier.testTag("about-crash-copy"))
            YftTextButton("Share", onShare, Modifier.testTag("about-crash-share"))
            YftTextButton("Delete", onDelete, Modifier.testTag("about-crash-delete"))
        }
    }
}

@Composable
internal fun CrashReportDialog(report: String, onDismiss: () -> Unit) {
    val colors = YftTheme.colors
    val reportHeight = (LocalConfiguration.current.screenHeightDp * 0.4f).dp
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("about-crash-dialog"),
        title = { Text("Last crash report", color = colors.textPrimary) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = reportHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                SelectionContainer {
                    Text(
                        text = report,
                        modifier = Modifier.testTag("about-crash-text"),
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        },
        confirmButton = {
            YftTextButton("Close", onDismiss, Modifier.testTag("about-crash-close"))
        },
        containerColor = colors.card,
    )
}

@Composable
private fun PromiseGroup(
    title: String,
    lines: List<String>,
    @DrawableRes icon: Int,
    tag: String,
) {
    Column(modifier = Modifier.testTag(tag)) {
        YftGroupLabel(text = title)
        YftCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(0.dp),
        ) {
            lines.forEachIndexed { index, line ->
                if (index > 0) YftDivider(modifier = Modifier.padding(start = LINE_TEXT_START))
                LineRow(icon = icon, text = line)
            }
        }
    }
}

/** One line of a card: a small leading icon and text that wraps, as in the Settings rows. */
@Composable
private fun LineRow(
    @DrawableRes icon: Int,
    text: String,
    @DrawableRes trailingIcon: Int? = null,
) {
    val colors = YftTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YftIcon(icon = icon, contentDescription = null, tint = colors.icon, size = 20.dp)
        Text(
            text = text,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (trailingIcon != null) {
            YftIcon(
                icon = trailingIcon,
                contentDescription = null,
                tint = colors.textSecondary,
                size = 20.dp,
            )
        }
    }
}

private val LINE_TEXT_START = 44.dp

private val DOES = listOf(
    "Opens HTTPS pages in a private in-app browser.",
    "Finds media the page plays or links to and previews the real variants.",
    "Downloads direct files, HLS and DASH streams with pause, resume and retry.",
    "Saves to Download/YFT or app storage, and plays finished files in the Library.",
)

private val DOES_NOT = listOf(
    "Bypass DRM: protected media is reported as unsupported.",
    "Get around payment walls, sign-ins or other private access controls.",
    "Show ads, use analytics, require an account or send crash reports automatically.",
)

private val PRIVACY = listOf(
    "YFT only contacts the sites you open and the media servers they point to.",
    "Cookies and site data stay in the in-app browser until you clear them in Settings.",
    "Detected media and the preview selection live in memory and are never saved.",
    "Logs never contain cookies, tokens or signed links.",
    "The clipboard is read only when you tap Paste or Use copied link.",
    "App data is excluded from cloud backup and device-to-device transfer.",
    "On a secure lock screen, download notifications hide media titles.",
    "One redacted crash report stays on this device, outside backups, until you delete it.",
    "Lookup failure details stay in memory. Copy or Share only happens when you ask.",
)
