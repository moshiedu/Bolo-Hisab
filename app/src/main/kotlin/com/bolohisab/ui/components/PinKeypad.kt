package com.bolohisab.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bolohisab.R
import com.bolohisab.ui.format.Bn

/**
 * A full-screen 4-digit PIN entry: title, dot progress, number pad. Used both to unlock the
 * app and to set/confirm a new PIN in Settings — the caller decides what happens on submit.
 */
@Composable
fun PinKeypadScreen(
    title: String,
    error: Boolean,
    errorMessage: String,
    onSubmit: (String) -> Unit,
    onErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
    pinLength: Int = 4,
) {
    var pin by remember { mutableStateOf("") }
    val shake = remember { Animatable(0f) }

    // Clear the entered dots on a wrong PIN, but leave the message up until the user
    // starts the next attempt — clearing it the instant it appears would make it unreadable.
    LaunchedEffect(error) {
        if (error) {
            pin = ""
            shake.animateTo(1f, tween(60))
            shake.animateTo(-1f, tween(60))
            shake.animateTo(1f, tween(60))
            shake.animateTo(0f, tween(60))
        }
    }

    fun press(digit: Char) {
        if (error) onErrorShown()
        if (pin.length < pinLength) {
            pin += digit
            if (pin.length == pinLength) { onSubmit(pin); pin = "" }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier.formWidth(360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.graphicsLayer { translationX = shake.value * 14f },
            ) {
                repeat(pinLength) { i -> PinDot(filled = i < pin.length) }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                if (error) errorMessage else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(28.dp))
            val rows = listOf("123", "456", "789")
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { digit -> DigitKey(digit.toString()) { press(digit) } }
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(Modifier.size(64.dp))
                DigitKey("0") { press('0') }
                Box(
                    Modifier.size(64.dp).clip(CircleShape).clickable(enabled = pin.isNotEmpty()) { pin = pin.dropLast(1) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (pin.isNotEmpty()) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Backspace,
                            contentDescription = stringResource(R.string.lock_backspace),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinDot(filled: Boolean) {
    val scale by animateFloatAsState(if (filled) 1f else 0.7f, spring(dampingRatio = 0.5f), label = "pinDot")
    Box(
        Modifier
            .size(16.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(
                if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
    )
}

@Composable
private fun DigitKey(digit: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.size(64.dp)) {
        Text(Bn.digits(digit), style = MaterialTheme.typography.headlineSmall)
    }
}
