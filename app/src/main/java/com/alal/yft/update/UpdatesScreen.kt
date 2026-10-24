package com.alal.yft.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alal.yft.R
import com.alal.yft.ui.components.YftCard
import com.alal.yft.ui.components.YftIcon
import com.alal.yft.ui.components.YftOutlinedButton
import com.alal.yft.ui.components.YftPrimaryButton
import com.alal.yft.ui.components.YftSwitch
import com.alal.yft.ui.components.YftTopBar
import com.alal.yft.ui.navigation.YftDestination
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun UpdatesRoute(onNavigateBack: () -> Unit, viewModel: UpdatesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    UpdatesScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onCheckNow = viewModel::checkNow,
        onAutoCheckChanged = viewModel::setAutoCheck,
        times = UpdateTimes(
            now = System.currentTimeMillis(),
            is24Hour = DateFormat.is24HourFormat(context),
        ),
    )
}

/**
 * Settings → About → Updates (§4.5): the installed version; a found update with its notes and
 * **Download APK to update**, or the latest-version line with **Check now**; the switch
 * **Check automatically** at the bottom. The button opens the APK's GitHub link in the browser
 * (U1), whose download Android's installer takes over; every line says only what is checked.
 */
@Composable
fun UpdatesScreen(
    state: UpdateState,
    onNavigateBack: () -> Unit,
    onCheckNow: () -> Unit,
    onAutoCheckChanged: (Boolean) -> Unit,
    times: UpdateTimes,
    onDownload: (FoundUpdate) -> Boolean = rememberLinkOpener(),
) {
    val colors = YftTheme.colors
    Scaffold(
        topBar = {
            YftTopBar(
                title = YftDestination.UPDATES.title,
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
                .testTag("updates-content"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Installed(state.installedName)
            val update = state.found
            if (update != null) {
                Available(update = update, testMode = state.testMode, onDownload = onDownload)
            } else {
                Latest(state = state, times = times, onCheckNow = onCheckNow)
            }
            AutoCheck(checked = state.autoCheck, onCheckedChange = onAutoCheckChanged)
        }
    }
}

@Composable
private fun Installed(installedName: String) {
    val colors = YftTheme.colors
    YftCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.yft_logo),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    text = "YFT $installedName",
                    modifier = Modifier
                        .semantics { heading() }
                        .testTag("updates-installed"),
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Installed",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun Available(
    update: FoundUpdate,
    testMode: Boolean,
    onDownload: (FoundUpdate) -> Boolean,
) {
    val colors = YftTheme.colors
    var linkFailed by remember(update.apkUrl) { mutableStateOf(false) }
    YftCard(modifier = Modifier.fillMaxWidth().testTag("updates-available")) {
        Text(
            text = "New version ${update.version.label}" + if (testMode) TEST_SUFFIX else "",
            modifier = Modifier
                .semantics { heading() }
                .testTag("updates-new-version"),
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
        )
        // The release's body as it is: plain text, Markdown signs shown, no link opened.
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .heightIn(max = NOTES_MAX_HEIGHT)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.background)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
                .testTag("updates-notes"),
        ) {
            Text(
                text = update.notes.ifBlank { "No release notes." },
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        YftPrimaryButton(
            text = "Download APK to update",
            onClick = { linkFailed = !onDownload(update) },
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .testTag("updates-download"),
            icon = YftIcons.Download,
            large = true,
        )
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YftIcon(
                icon = YftIcons.Shield,
                contentDescription = null,
                tint = colors.success,
                size = 16.dp,
            )
            Text(
                text = "Signed by YFT · checked before install",
                modifier = Modifier
                    .padding(start = 6.dp)
                    .testTag("updates-security"),
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (linkFailed) {
            Text(
                text = "No app can open this link.",
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("updates-link-failed"),
                color = colors.coralText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Latest(state: UpdateState, times: UpdateTimes, onCheckNow: () -> Unit) {
    val colors = YftTheme.colors
    YftCard(modifier = Modifier.fillMaxWidth().testTag("updates-latest")) {
        val lastGoodAt = state.lastGoodAt
        if (lastGoodAt != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                YftIcon(
                    icon = YftIcons.CheckCircle,
                    contentDescription = null,
                    tint = colors.success,
                    size = 20.dp,
                )
                Text(
                    text = "You have the latest version",
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .semantics { heading() }
                        .testTag("updates-latest-title"),
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = times.lastChecked(lastGoodAt),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .testTag("updates-last-checked"),
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                text = "Not checked yet",
                modifier = Modifier
                    .semantics { heading() }
                    .testTag("updates-not-checked"),
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        YftOutlinedButton(
            text = "Check now",
            onClick = onCheckNow,
            modifier = Modifier
                .padding(top = 12.dp)
                .testTag("updates-check-now"),
            icon = YftIcons.Refresh,
            enabled = !state.checking,
        )
        val line = if (state.checking) "Checking…" else state.line?.text(times)
        if (line != null) {
            Text(
                text = line,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("updates-check-line"),
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AutoCheck(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = YftTheme.colors
    YftCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .testTag("updates-auto-check"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Check automatically",
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Once a day when YFT opens. Asks GitHub (api.github.com).",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            YftSwitch(checked = checked, onCheckedChange = null)
        }
    }
}

internal fun CheckLine.text(times: UpdateTimes): String = when (this) {
    CheckLine.NoConnection -> "No connection."
    is CheckLine.Busy -> "GitHub is busy. Try again after ${times.clock(until)}."
    CheckLine.CouldNotCheck -> "Couldn't check. Try again later."
}

/** How the screen writes times: the phone's 12- or 24-hour clock, in its time zone. */
data class UpdateTimes(
    val now: Long,
    val is24Hour: Boolean,
    val zone: TimeZone = TimeZone.getDefault(),
) {
    /** "21:35" or "9:35 PM". */
    fun clock(at: Long): String = format(if (is24Hour) "HH:mm" else "h:mm a", at)

    /** "Last checked today, 21:35"; "yesterday", or the date, for older answers. */
    fun lastChecked(at: Long): String {
        val day = when (daysAgo(at)) {
            0 -> "today"
            1 -> "yesterday"
            else -> format(if (sameYear(at)) "d MMM" else "d MMM yyyy", at)
        }
        return "Last checked $day, ${clock(at)}"
    }

    private fun format(pattern: String, at: Long): String =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = zone }.format(Date(at))

    private fun calendar(at: Long): Calendar =
        Calendar.getInstance(zone, Locale.US).apply { timeInMillis = at }

    private fun daysAgo(at: Long): Int {
        val then = calendar(at)
        val today = calendar(now)
        if (then.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
            return today.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
        }
        then.add(Calendar.DAY_OF_YEAR, 1)
        val sameAsToday = then.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            then.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        return if (sameAsToday) 1 else Int.MAX_VALUE
    }

    private fun sameYear(at: Long): Boolean =
        calendar(at).get(Calendar.YEAR) == calendar(now).get(Calendar.YEAR)
}

@Composable
private fun rememberLinkOpener(): (FoundUpdate) -> Boolean {
    val context = LocalContext.current
    return remember(context) { { update -> openUpdateLink(context, update.apkUrl) } }
}

/**
 * Opens the APK's GitHub link with `ACTION_VIEW` (U1): the user's browser downloads it and
 * Android's installer takes over. No selector: on a phone with two browsers it would ask which
 * one every time. YFT has no web-link filter, so its own browser never opens. False: no app can.
 */
internal fun openUpdateLink(context: Context, url: String): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
    return try {
        context.startActivity(intent)
        true
    } catch (missing: ActivityNotFoundException) {
        false
    }
}

private val NOTES_MAX_HEIGHT = 240.dp
