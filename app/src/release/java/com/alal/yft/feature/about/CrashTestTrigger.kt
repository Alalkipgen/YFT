package com.alal.yft.feature.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Release source set: no long-press action, dialog, label or deliberately throwing code. */
@Composable
internal fun CrashTestTrigger(content: @Composable (Modifier) -> Unit) = content(Modifier)
