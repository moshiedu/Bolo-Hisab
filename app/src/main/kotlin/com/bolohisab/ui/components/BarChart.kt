package com.bolohisab.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

data class BarSlice(val label: String, val value: String, val fraction: Float, val color: Color)

/**
 * A plain, dependency-free bar comparison — no charting library, since this app targets
 * budget phones and a single row of animated bars is all a totals comparison needs.
 */
@Composable
fun BarChart(slices: List<BarSlice>, modifier: Modifier = Modifier) {
    val maxFraction = (slices.maxOfOrNull { it.fraction } ?: 0f).coerceAtLeast(0.001f)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        slices.forEach { slice ->
            val animated by animateFloatAsState(
                targetValue = slice.fraction / maxFraction,
                animationSpec = spring(dampingRatio = 0.8f),
                label = "barChartSlice",
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(slice.label, style = MaterialTheme.typography.labelLarge)
                    Text(slice.value, style = MaterialTheme.typography.labelLarge)
                }
                Canvas(Modifier.fillMaxWidth().height(14.dp).padding(vertical = 1.dp)) {
                    drawTrack(this)
                    drawBar(this, animated, slice.color)
                }
            }
        }
    }
}

private fun drawTrack(scope: DrawScope) = with(scope) {
    drawRoundRect(
        color = Color(0x14000000),
        size = Size(size.width, size.height),
        cornerRadius = CornerRadius(size.height / 2),
    )
}

private fun drawBar(scope: DrawScope, fraction: Float, color: Color) = with(scope) {
    val width = size.width * fraction.coerceIn(0f, 1f)
    if (width <= 0f) return@with
    drawRoundRect(
        color = color,
        size = Size(width, size.height),
        cornerRadius = CornerRadius(size.height / 2),
    )
}
