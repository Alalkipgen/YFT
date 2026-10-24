package com.alal.yft.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import com.alal.yft.ui.components.YftProgressBar
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
    val install by viewModel.install.collectAsStateWithLifecycle()
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
        install = install,
        onDownload = viewModel::download,
        onCancel = viewModel::cancelInstall,
        onReinstall = viewModel::reinstallThisBuild,
    )
}

/**
 * Settings → About → Updates (§4.5): the installed version; a found update with its notes and
 * **Download APK to update**, or the latest-version line with **Check now**; the switch
 * **Check automatically** at the bottom, and in test mode **Reinstall this build (test)**. The
 * button runs the in-app update (U2: checksum, signature, package and version, then Android's
 * installer); every failure offers **Download in browser** (the APK's GitHub link). Every line says
 * only what was checked.
 */
@Composable
fun UpdatesScreen(
    state: UpdateState,
    onNavigateBack: () -> Unit,
    onCheckNow: () -> Unit,
    onAutoCheckChanged: (Boolean) -> Unit,
    times: UpdateTimes,
    install: InstallState = InstallState.Idle,
    onDownload: (FoundUpdate) -> Unit = {},
    onCancel: () -> Unit = {},
    onReinstall: () -> Unit = {},
    onOpenInstallSettings: () -> Unit = rememberInstallSettingsOpener(),
    onOpenLink: (FoundUpdate) -> Boolean = rememberLinkOpener(),
) {
    val colors = YftTheme.colors
    val busy = install is InstallState.Downloading || install is InstallState.Installing
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
                Available(
                    update = update,
                    testMode = state.testMode,
                    install = if (install.test) InstallState.Idle else install,
                    busy = busy,
                    actions = InstallActions(
                        onDownload = onDownload,
                        onCancel = onCancel,
                        onOpenInstallSettings = onOpenInstallSettings,
                        onOpenLink = onOpenLink,
                    ),
                )
            } else {
                Latest(state = state, times = times, onCheckNow = onCheckNow)
            }
            AutoCheck(checked = state.autoCheck, onCheckedChange = onAutoCheckChanged)
            if (state.testMode) {
                TestReinstall(
                    install = if (install.test) install else InstallState.Idle,
                    busy = busy,
                    onReinstall = onReinstall,
                    onCancel = onCancel,
                    onOpenInstallSettings = onOpenInstallSettings,
                )
            }
        }
    }
}

/** What the update card's buttons do. */
private class InstallActions(
    val onDownload: (FoundUpdate) -> Unit,
    val onCancel: () -> Unit,
    val onOpenInstallSettings: () -> Unit,
    val onOpenLink: (FoundUpdate) -> Boolean,
)

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
    install: InstallState,
    busy: Boolean,
    actions: InstallActions,
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
        when (install) {
            is InstallState.Downloading -> Downloading(install.percent, actions.onCancel)
            is InstallState.Installing -> {
                SecurityLine(
                    text = "Verified: checksum and signature match",
                    verified = true,
                    modifier = Modifier.padding(top = 16.dp),
                )
                CancelButton(actions.onCancel)
            }
            else -> {
                YftPrimaryButton(
                    text = "Download APK to update",
                    onClick = { actions.onDownload(update) },
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .testTag("updates-download"),
                    icon = YftIcons.Download,
                    enabled = !busy,
                    large = true,
                )
                SecurityLine(
                    text = "Signed by YFT · checked before install",
                    verified = false,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        when (install) {
            is InstallState.NotAllowed -> NotAllowed(actions.onOpenInstallSettings)
            is InstallState.Failed -> Problem(
                text = install.failure.text(),
                onOpenLink = { linkFailed = !actions.onOpenLink(update) },
            )
            is InstallState.Cancelled -> Problem(
                text = CANCELLED,
                onOpenLink = { linkFailed = !actions.onOpenLink(update) },
            )
            else -> Unit
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

/** The security line under the button: only what is checked (§4.5's table). */
@Composable
private fun SecurityLine(text: String, verified: Boolean, modifier: Modifier = Modifier) {
    val colors = YftTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YftIcon(
            icon = if (verified) YftIcons.CheckCircle else YftIcons.Shield,
            contentDescription = null,
            tint = colors.success,
            size = 16.dp,
        )
        Text(
            text = text,
            modifier = Modifier
                .padding(start = 6.dp)
                .testTag("updates-security"),
            color = if (verified) colors.textPrimary else colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun Downloading(percent: Int, onCancel: () -> Unit) {
    val colors = YftTheme.colors
    Column(modifier = Modifier.padding(top = 16.dp).testTag("updates-downloading")) {
        YftProgressBar(progress = percent / PERCENT)
        Text(
            text = "Downloading… $percent%",
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag("updates-progress"),
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    CancelButton(onCancel)
}

@Composable
private fun CancelButton(onCancel: () -> Unit) {
    YftOutlinedButton(
        text = "Cancel",
        onClick = onCancel,
        modifier = Modifier
            .padding(top = 8.dp)
            .testTag("updates-cancel"),
        icon = YftIcons.Close,
    )
}

/** Step 1: Android does not let YFT install yet; nothing was downloaded. */
@Composable
private fun NotAllowed(onOpenInstallSettings: () -> Unit) {
    val colors = YftTheme.colors
    Text(
        text = "Allow YFT to install updates, then tap Download APK to update again.",
        modifier = Modifier
            .padding(top = 12.dp)
            .testTag("updates-not-allowed"),
        color = colors.textPrimary,
        style = MaterialTheme.typography.bodyMedium,
    )
    YftOutlinedButton(
        text = "Open settings",
        onClick = onOpenInstallSettings,
        modifier = Modifier
            .padding(top = 8.dp)
            .testTag("updates-open-settings"),
        icon = YftIcons.Settings,
    )
}

/** A failed step or a cancel: which, "Nothing was installed." and, for a release, the link. */
@Composable
private fun Problem(text: String, onOpenLink: (() -> Unit)?) {
    val colors = YftTheme.colors
    Column(modifier = Modifier.padding(top = 12.dp).testTag("updates-problem")) {
        Text(
            text = text,
            modifier = Modifier.testTag("updates-problem-text"),
            color = colors.coralText,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = NOTHING_INSTALLED,
            modifier = Modifier.testTag("updates-nothing-installed"),
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (onOpenLink != null) {
        YftOutlinedButton(
            text = "Download in browser",
            onClick = onOpenLink,
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag("updates-browser"),
            icon = YftIcons.OpenInNew,
        )
    }
}

/**
 * Test mode only (§4.4 step 9): this build's own APK through checks 2 and 3 and Android's real
 * confirmation. It is no release, so no checksum is claimed. Never in the release.
 */
@Composable
private fun TestReinstall(
    install: InstallState,
    busy: Boolean,
    onReinstall: () -> Unit,
    onCancel: () -> Unit,
    onOpenInstallSettings: () -> Unit,
) {
    val colors = YftTheme.colors
    YftCard(modifier = Modifier.fillMaxWidth().testTag("updates-test")) {
        Text(
            text = "Tries Android's installer with this build's own APK.",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        if (install is InstallState.Installing) {
            SecurityLine(
                text = "Test: this build's own APK, signature matches",
                verified = true,
                modifier = Modifier.padding(top = 12.dp),
            )
            CancelButton(onCancel)
        } else {
            YftOutlinedButton(
                text = "Reinstall this build (test)",
                onClick = onReinstall,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("updates-reinstall"),
                icon = YftIcons.Replay,
                enabled = !busy,
            )
        }
        when (install) {
            is InstallState.NotAllowed -> NotAllowed(onOpenInstallSettings)
            is InstallState.Failed -> Problem(text = install.failure.text(), onOpenLink = null)
            is InstallState.Cancelled -> Problem(text = CANCELLED, onOpenLink = null)
            else -> Unit
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

/** Which step failed (§4.4); "Nothing was installed." follows each. */
internal fun InstallFailure.text(): String = when (this) {
    InstallFailure.NoSpace -> "Not enough free space for the update."
    InstallFailure.NoChecksum ->
        "This release has no checksum for its APK, so YFT can't check it."
    InstallFailure.DownloadStopped -> "The download stopped. Check the connection."
    InstallFailure.DownloadFailed -> "Couldn't download the update."
    InstallFailure.ChecksumMismatch ->
        "Checksum doesn't match — the download was damaged or changed."
    is InstallFailure.Check -> when (val verdict = verdict) {
        ApkVerdict.SignatureMismatch -> "Signature doesn't match your YFT."
        ApkVerdict.OtherApp -> "This file is a different app."
        is ApkVerdict.NotThatVersion -> "This file isn't YFT ${verdict.label}."
        ApkVerdict.NotNewer -> "This file isn't newer than your YFT."
        // Never shown: a passed check is no failure.
        ApkVerdict.Passed -> "Checked."
    }
    is InstallFailure.Android ->
        "Android couldn't install the update (${installStatusName(status)})."
    InstallFailure.NoInstaller -> "Android's installer didn't open."
}

/** `PackageInstaller.STATUS_*` in words, for "Android couldn't install the update (…)". */
internal fun installStatusName(status: Int): String = when (status) {
    PackageInstaller.STATUS_FAILURE -> "failed"
    PackageInstaller.STATUS_FAILURE_BLOCKED -> "blocked"
    PackageInstaller.STATUS_FAILURE_INVALID -> "invalid APK"
    PackageInstaller.STATUS_FAILURE_CONFLICT -> "conflict"
    PackageInstaller.STATUS_FAILURE_STORAGE -> "not enough storage"
    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "incompatible"
    STATUS_FAILURE_TIMEOUT -> "timed out"
    else -> "status $status"
}

internal const val NOTHING_INSTALLED = "Nothing was installed."
internal const val CANCELLED = "Update cancelled."

// PackageInstaller.STATUS_FAILURE_TIMEOUT, Android 14.
private const val STATUS_FAILURE_TIMEOUT = 8
private const val PERCENT = 100f

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

@Composable
private fun rememberInstallSettingsOpener(): () -> Unit {
    val context = LocalContext.current
    return remember(context) { { openInstallSettings(context) } }
}

/** Android's "Install unknown apps" page for YFT (Android 8+); false when none opens. */
internal fun openInstallSettings(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    )
    return try {
        context.startActivity(intent)
        true
    } catch (missing: ActivityNotFoundException) {
        false
    }
}

/**
 * **Download in browser**: the APK's GitHub link with `ACTION_VIEW` (U1's button, U2's way out):
 * the user's browser downloads it and Android's installer takes over. No selector: on a phone
 * with two browsers it would ask which one every time. YFT has no web-link filter, so its own
 * browser never opens. False: no app can.
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
