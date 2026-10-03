package com.bolohisab.ui.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Wraps [content] and swaps in [LockScreen] whenever app lock is on and not yet unlocked. */
@Composable
fun AppLockGate(content: @Composable () -> Unit) {
    val viewModel: LockGateViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    when {
        state.loading -> Unit
        state.locked -> LockScreen(viewModel)
        else -> content()
    }
}
