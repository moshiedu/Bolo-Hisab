package com.bolohisab.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Caps a form's width and lets it self-center once the parent gives it more room than a
 * phone screen (tablets, foldables, landscape) — plain `fillMaxWidth` would otherwise
 * stretch fields edge to edge and hurt readability.
 */
fun Modifier.formWidth(max: Dp = 480.dp): Modifier = fillMaxWidth().widthIn(max = max)
