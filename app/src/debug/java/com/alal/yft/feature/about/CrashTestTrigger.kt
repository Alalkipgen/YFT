package com.alal.yft.feature.about

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alal.yft.ui.components.YftTextButton
import com.alal.yft.ui.theme.YftTheme

/** Debug source set only: a hidden, confirmed long press, never an automatic or release crash. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CrashTestTrigger(content: @Composable (Modifier) -> Unit) {
    var requested by remember { mutableStateOf(false) }
    content(
        Modifier.heightIn(min = 48.dp).combinedClickable(
            onClick = {},
            onLongClickLabel = "Crash now",
            onLongClick = { requested = true },
        ),
    )
    if (requested) {
        AlertDialog(
            onDismissRequest = { requested = false },
            title = { Text("Crash now?") },
            text = {
                Text("Close this debug app to test its local crash report. Nothing is uploaded.")
            },
            confirmButton = {
                YftTextButton(
                    text = "Crash now",
                    onClick = { throw IllegalStateException("YFT debug test crash") },
                )
            },
            dismissButton = {
                YftTextButton(text = "Cancel", onClick = { requested = false })
            },
            containerColor = YftTheme.colors.card,
        )
    }
}
