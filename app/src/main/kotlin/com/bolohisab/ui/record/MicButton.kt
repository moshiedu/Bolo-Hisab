package com.bolohisab.ui.record

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bolohisab.R

/**
 * Hold-to-talk microphone. Press starts recording, release ends it.
 * Screen-reader users get a plain click action that opens typed entry instead.
 */
@Composable
fun MicButton(
    listening: Boolean,
    level: Float,
    onDown: () -> Unit,
    onUp: () -> Unit,
    onAccessibilityClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val down by rememberUpdatedState(onDown)
    val up by rememberUpdatedState(onUp)
    val scale by animateFloatAsState(if (listening) 1.1f else 1f, spring(dampingRatio = 0.55f), label = "micScale")
    val ring by animateFloatAsState(if (listening) 1f + level * 0.45f else 1f, tween(90), label = "micRing")
    val ringAlpha by animateFloatAsState(if (listening) 0.22f else 0f, tween(200), label = "micRingAlpha")
    val label = stringResource(R.string.mic_content_description)
    val primary = MaterialTheme.colorScheme.primary

    Box(modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(80.dp)
                .graphicsLayer { scaleX = ring * 1.3f; scaleY = ring * 1.3f; alpha = ringAlpha }
                .background(primary, CircleShape),
        )
        Surface(
            shape = CircleShape,
            color = primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 8.dp,
            modifier = Modifier
                .size(76.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .semantics {
                    role = Role.Button
                    contentDescription = label
                    onClick(label) { onAccessibilityClick(); true }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            down()
                            tryAwaitRelease()
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            up()
                        },
                    )
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(34.dp))
            }
        }
    }
}
