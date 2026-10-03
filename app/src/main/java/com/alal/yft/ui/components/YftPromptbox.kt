package com.alal.yft.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftShapes
import com.alal.yft.ui.theme.YftTheme

/** What the Promptbox shows: the editable link field or the result of looking at a link. */
@Immutable
sealed interface PromptboxStatus {
    /** Empty, clipboard and typing states: the editable field. */
    data object Editing : PromptboxStatus

    data object Searching : PromptboxStatus

    data class Found(val count: Int) : PromptboxStatus

    /** [canOpenInBrowser] is false when the address itself is unusable. */
    data class NotFound(
        val message: String = NO_MEDIA_MESSAGE,
        val canOpenInBrowser: Boolean = true,
    ) : PromptboxStatus

    companion object {
        const val NO_MEDIA_MESSAGE = "No downloadable media on this page"
    }
}

/**
 * The Home link box with the six states from the design (`09-promptbox-states`): Empty,
 * Clipboard, Typing, Searching, Found and Error. Found and Error rows are tappable to edit the
 * link again. The clipboard row is only a hint; this component never reads the clipboard.
 */
@Composable
fun YftPromptbox(
    text: String,
    onTextChange: (String) -> Unit,
    status: PromptboxStatus,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    showClipboardSuggestion: Boolean = false,
    onUseClipboard: () -> Unit = {},
    onView: () -> Unit = {},
    onOpenInBrowser: () -> Unit = {},
    onCancelSearch: () -> Unit = {},
    onEdit: () -> Unit = {},
    placeholder: String = "Paste a page or media link",
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val colors = YftTheme.colors
    var focused by remember { mutableStateOf(false) }
    val editing = status == PromptboxStatus.Editing
    val outlineWidth = if (editing && focused) 2.dp else 1.5.dp
    val outlineColor = if (editing && focused) colors.accent else colors.fieldOutline

    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(YftShapes.promptbox)
                .background(colors.card)
                .border(outlineWidth, outlineColor, YftShapes.promptbox),
        ) {
            if (editing && showClipboardSuggestion) {
                ClipboardSuggestion(onUseClipboard = onUseClipboard)
                YftDivider()
            }
            when (status) {
                PromptboxStatus.Editing -> EditingRow(
                    text = text,
                    onTextChange = onTextChange,
                    onSubmit = onSubmit,
                    onClear = onClear,
                    placeholder = placeholder,
                    focusRequester = focusRequester,
                    onFocusChanged = { focused = it },
                )
                PromptboxStatus.Searching -> SearchingRow(onCancelSearch = onCancelSearch)
                is PromptboxStatus.Found -> FoundRow(
                    count = status.count,
                    onView = onView,
                    onEdit = onEdit,
                )
                is PromptboxStatus.NotFound -> NotFoundRow(
                    message = status.message,
                    onOpenInBrowser = onOpenInBrowser.takeIf { status.canOpenInBrowser },
                    onEdit = onEdit,
                )
            }
        }
        if (status == PromptboxStatus.Searching) {
            YftProgressBar(
                progress = null,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .testTag("home-search-progress"),
            )
        }
    }
}

@Composable
private fun ClipboardSuggestion(onUseClipboard: () -> Unit) {
    val colors = YftTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.chip)
            .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YftIcon(
            icon = YftIcons.Paste,
            contentDescription = null,
            tint = colors.icon,
            size = 20.dp,
        )
        Text(
            text = "Use copied link",
            modifier = Modifier.weight(1f),
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        YftPrimaryButton(
            text = "Use",
            onClick = onUseClipboard,
            compact = true,
            modifier = Modifier.testTag("home-use-clipboard"),
        )
    }
}

@Composable
private fun EditingRow(
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
) {
    val colors = YftTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .padding(start = 12.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LinkGlyph()
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 4.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChanged(it.isFocused) }
                .testTag("home-link"),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
            singleLine = true,
            cursorBrush = SolidColor(colors.textPrimary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            decorationBox = { innerTextField ->
                // Inside the decoration box the placeholder merges into the field's semantics,
                // so accessibility services read it as the field's hint.
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (text.isNotEmpty()) {
            YftIconButton(
                icon = YftIcons.Close,
                contentDescription = "Clear link",
                onClick = onClear,
                modifier = Modifier.testTag("home-link-clear"),
            )
        }
        YftCircleButton(
            icon = YftIcons.ArrowForward,
            contentDescription = "Find media",
            size = 40.dp,
            onClick = {
                if (text.isBlank()) focusRequester.requestFocus() else onSubmit()
            },
            modifier = Modifier.testTag("home-open-link"),
        )
    }
}

@Composable
private fun SearchingRow(onCancelSearch: () -> Unit) {
    val colors = YftTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LinkGlyph()
        Text(
            text = "Looking for media…",
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyLarge,
        )
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.accent)
                .clickable(role = Role.Button, onClick = onCancelSearch)
                .semantics { contentDescription = "Stop looking" }
                .testTag("home-search-cancel"),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = colors.onAccent,
                strokeWidth = 2.5.dp,
                trackColor = colors.onAccent.copy(alpha = 0.2f),
            )
        }
    }
}

@Composable
private fun FoundRow(count: Int, onView: () -> Unit, onEdit: () -> Unit) {
    val colors = YftTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .clickable(onClickLabel = "Edit link", onClick = onEdit)
            .padding(start = 14.dp, end = 4.dp)
            .testTag("home-found"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YftIcon(icon = YftIcons.CheckCircle, contentDescription = null, tint = colors.success)
        Text(
            text = if (count == 1) "1 media found" else "$count media found",
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        )
        YftPrimaryButton(
            text = "View",
            onClick = onView,
            modifier = Modifier.testTag("home-view-media"),
        )
    }
}

@Composable
private fun NotFoundRow(message: String, onOpenInBrowser: (() -> Unit)?, onEdit: () -> Unit) {
    val colors = YftTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Edit link", onClick = onEdit)
            .padding(
                start = 14.dp,
                end = 8.dp,
                top = 12.dp,
                bottom = if (onOpenInBrowser == null) 14.dp else 4.dp,
            )
            .testTag("home-not-found"),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YftIcon(icon = YftIcons.Warning, contentDescription = null, tint = colors.coralText)
            Text(
                text = message,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = colors.coralText,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (onOpenInBrowser != null) {
            YftTextButton(
                text = "Open in browser",
                onClick = onOpenInBrowser,
                underline = true,
                modifier = Modifier
                    .padding(start = 24.dp)
                    .testTag("home-open-in-browser"),
            )
        }
    }
}

/** The chain-link glyph, tilted 45° as in the design and tinted like the chip icons. */
@Composable
private fun LinkGlyph() {
    YftIcon(
        icon = YftIcons.Link,
        contentDescription = null,
        modifier = Modifier.rotate(-45f),
        tint = YftTheme.colors.icon,
    )
}

/** One Promptbox row: the design's 44dp field, raised to the 48dp touch target. */
private val ROW_HEIGHT = 48.dp
