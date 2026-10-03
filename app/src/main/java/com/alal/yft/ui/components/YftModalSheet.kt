package com.alal.yft.ui.components

import android.view.WindowManager
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.DialogWindowProvider
import com.alal.yft.ui.theme.YftShapes
import com.alal.yft.ui.theme.YftTheme
import kotlinx.coroutines.launch

/**
 * A modal YFT sheet (28dp top corners, card colour, drag handle) that slides up over whatever
 * screen is showing, with a 32% black scrim like the browser's "Found on this page" sheet.
 *
 * It is meant to be the content of a navigation `dialog` destination: the destination owns the
 * back stack entry (so its ViewModel is cleared when the sheet closes) while Material's sheet
 * window draws the scrim edge to edge. The host dialog's own window dim is switched off so the
 * page behind is dimmed once, by the sheet.
 *
 * Swiping down, tapping the scrim and Back animate the sheet away and then call
 * [onDismissRequest]. [content] receives `hide`, which does the same for buttons inside the
 * sheet and then runs the given action (for example, closing or opening another screen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YftModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(hide: (andThen: () -> Unit) -> Unit) -> Unit,
) {
    val hostWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { hostWindow?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val hide: (() -> Unit) -> Unit = { andThen ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { andThen() }
    }
    val colors = YftTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.testTag("modal-sheet"),
        sheetState = sheetState,
        shape = YftShapes.sheet,
        containerColor = colors.card,
        contentColor = colors.textPrimary,
        scrimColor = Color.Black.copy(alpha = SHEET_SCRIM_ALPHA),
        dragHandle = {
            YftSheetHandle(modifier = Modifier.semantics { contentDescription = "Drag handle" })
        },
        // The sheet stops below the status bar on its own (see the content's height cap), so
        // only the navigation bar and side cutouts pad the content.
        contentWindowInsets = {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
        },
    ) {
        content(hide)
    }
}

/** The scrim strength shared by YFT sheets: 32% black, the Material sheet default. */
const val SHEET_SCRIM_ALPHA: Float = 0.32f
