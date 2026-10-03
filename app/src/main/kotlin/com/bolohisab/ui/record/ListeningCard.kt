package com.bolohisab.ui.record

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bolohisab.R

/** Live transcript while the mic is held, so the shopkeeper sees they are being heard. */
@Composable
fun ListeningCard(partial: String, level: Float, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LevelBars(level)
            Text(
                text = partial.ifBlank { stringResource(R.string.mic_listening) },
                style = if (partial.isBlank()) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                color = if (partial.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 14.dp).heightIn(min = 26.dp),
            )
        }
    }
}

@Composable
private fun LevelBars(level: Float) {
    val weights = listOf(0.45f, 0.75f, 1f, 0.75f, 0.45f)
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        weights.forEach { w ->
            val h by animateFloatAsState((6f + 30f * level * w), tween(90), label = "bar")
            Box(
                Modifier
                    .width(6.dp)
                    .height(h.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
            )
        }
    }
}
