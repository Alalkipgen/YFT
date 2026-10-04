package com.alal.yft.feature.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alal.yft.ui.components.YftCard
import com.alal.yft.ui.components.YftDivider
import com.alal.yft.ui.components.YftGroupLabel
import com.alal.yft.ui.components.YftIcon
import com.alal.yft.ui.components.YftTopBar
import com.alal.yft.ui.navigation.YftDestination
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftTheme

/**
 * Open-source licenses, opened from Settings → Licenses or from About. Bundled code comes first
 * because YFT ships those files itself; every row opens its full license text on demand.
 */
@Composable
fun LicensesScreen(onNavigateBack: () -> Unit) {
    val colors = YftTheme.colors
    Scaffold(
        topBar = {
            YftTopBar(
                title = YftDestination.LICENSES.title,
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
                .testTag("licenses-content"),
        ) {
            Text(
                text = "YFT is built with open-source code. Tap an entry to read its license.",
                modifier = Modifier.padding(horizontal = 4.dp),
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            NoticeGroup(title = "Bundled code", notices = OpenSourceNotices.bundled)
            NoticeGroup(title = "Libraries", notices = OpenSourceNotices.libraries)
            Text(
                text = "The solver files keep their original license headers. Each library " +
                    "remains under its own license.",
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp),
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun NoticeGroup(title: String, notices: List<OpenSourceNotice>) {
    YftGroupLabel(text = title)
    YftCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        notices.forEachIndexed { index, notice ->
            if (index > 0) YftDivider(modifier = Modifier.padding(start = 12.dp))
            NoticeRow(notice)
        }
    }
}

/** Name, license and use of one notice; tapping the row shows or hides the license text. */
@Composable
private fun NoticeRow(notice: OpenSourceNotice) {
    val colors = YftTheme.colors
    var expanded by rememberSaveable(notice.id) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(
                    onClickLabel = if (expanded) "Hide license" else "Show license",
                    role = Role.Button,
                ) { expanded = !expanded }
                .semantics {
                    stateDescription = if (expanded) "License shown" else "License hidden"
                }
                .testTag("license-${notice.id}")
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = listOfNotNull(notice.name, notice.version).joinToString(" "),
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = notice.license,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = notice.usage,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            YftIcon(
                icon = YftIcons.ExpandMore,
                contentDescription = null,
                modifier = Modifier.graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                tint = colors.textSecondary,
                size = 20.dp,
            )
        }
        if (expanded) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                shape = MaterialTheme.shapes.small,
                color = colors.background,
            ) {
                val context = LocalContext.current
                val fullText = remember(notice.id) {
                    val license = notice.noticeAsset?.let { asset ->
                        runCatching {
                            context.assets.open(asset).bufferedReader().use { it.readText() }
                        }.getOrNull()
                    }
                    listOfNotNull(notice.noticeText, license?.trim()).joinToString("\n\n")
                }
                Text(
                    text = fullText,
                    modifier = Modifier
                        .padding(12.dp)
                        .testTag("license-text-${notice.id}"),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
