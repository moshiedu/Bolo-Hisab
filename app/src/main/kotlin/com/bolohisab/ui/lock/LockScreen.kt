package com.bolohisab.ui.lock

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bolohisab.R
import com.bolohisab.ui.components.PinKeypadScreen

/** Shown in place of the whole app while app lock is on and the PIN has not been entered yet. */
@Composable
fun LockScreen(viewModel: LockGateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize()) {
        PinKeypadScreen(
            title = stringResource(R.string.lock_enter_title),
            error = state.wrongPin,
            errorMessage = stringResource(R.string.lock_wrong),
            onSubmit = viewModel::tryUnlock,
            onErrorShown = viewModel::consumeWrongPin,
        )
    }
}
