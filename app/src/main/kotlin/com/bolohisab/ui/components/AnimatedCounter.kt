package com.bolohisab.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.bolohisab.nlu.Poisha
import com.bolohisab.ui.format.Bn

/**
 * Formatted ৳ text that counts up (or down) from whatever it last showed to [value], starting
 * from zero the first time it appears. Mirrors how a "live" number feels more trustworthy than
 * one that just snaps — the shopkeeper can see the total actually add up.
 */
@Composable
fun animatedTaka(value: Poisha): String {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(value) {
        animated.animateTo(value.value.toFloat(), tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }
    return Bn.taka(Poisha(animated.value.toLong()))
}
