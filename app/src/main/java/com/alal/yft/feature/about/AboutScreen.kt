package com.alal.yft.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alal.yft.BuildConfig
import com.alal.yft.ui.components.YftTopBar
import com.alal.yft.ui.navigation.YftDestination

@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    versionName: String = BuildConfig.VERSION_NAME,
    versionCode: Int = BuildConfig.VERSION_CODE,
) {
    Scaffold(
        topBar = {
            YftTopBar(
                title = YftDestination.ABOUT.title,
                canNavigateBack = true,
                onNavigateBack = onNavigateBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .testTag("about-content"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Video Downloader",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Version $versionName ($versionCode)",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("about-version"),
            )
            Text(
                text = "YFT is an ad-free downloader for media you are allowed to save. It works " +
                    "with ordinary, non-DRM media and never tries to get around protection.",
            )
            BulletSection(title = "What YFT does", lines = DOES, tag = "about-does")
            BulletSection(title = "What YFT does not do", lines = DOES_NOT, tag = "about-does-not")
            BulletSection(title = "Privacy", lines = PRIVACY, tag = "about-privacy")
            SectionTitle("Open-source licenses")
            Text(
                text = "Bundled code",
                style = MaterialTheme.typography.titleSmall,
            )
            OpenSourceNotices.bundled.forEach { NoticeCard(it) }
            Text(
                text = "Libraries",
                style = MaterialTheme.typography.titleSmall,
            )
            OpenSourceNotices.libraries.forEach { NoticeCard(it) }
            Text(
                text = "The solver files keep their original license headers. Each library " +
                    "remains under its own license.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun BulletSection(title: String, lines: List<String>, tag: String) {
    Column(
        modifier = Modifier.testTag(tag),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SectionTitle(title)
        lines.forEach { line ->
            Text(text = "• $line", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NoticeCard(notice: OpenSourceNotice) {
    var expanded by rememberSaveable(notice.id) { mutableStateOf(false) }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = listOfNotNull(notice.name, notice.version).joinToString(" "),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(text = notice.license, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = notice.usage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (expanded) {
                Text(
                    text = notice.noticeText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("about-license-text-${notice.id}"),
                )
            }
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.testTag("about-license-${notice.id}"),
            ) {
                Text(if (expanded) "Hide license" else "Show license")
            }
        }
    }
}

private val DOES = listOf(
    "Opens HTTPS pages in a private in-app browser.",
    "Finds media the page plays or links to and previews the real variants.",
    "Downloads direct files, HLS and DASH streams with pause, resume and retry.",
    "Saves to Download/YFT or app storage, and plays finished files in the Library.",
)

private val DOES_NOT = listOf(
    "Bypass DRM: protected media is reported as unsupported.",
    "Get around payment walls, sign-ins or other private access controls.",
    "Show ads, use analytics, require an account or send crash reports.",
)

private val PRIVACY = listOf(
    "YFT only contacts the sites you open and the media servers they point to.",
    "Cookies and site data stay in the in-app browser until you clear them in Settings.",
    "Detected media and the preview selection live in memory and are never saved.",
    "Logs never contain cookies, tokens or signed links.",
    "The clipboard is read only when you tap Paste.",
    "App data is excluded from cloud backup and device-to-device transfer.",
    "On a secure lock screen, download notifications hide media titles.",
)
