package com.bolohisab.ui.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Fingerprint unlock through the system prompt. Only strong (Class 3) biometrics count: weak face
 * unlock on budget phones can be fooled by a photo, and this guards a shop's money ledger.
 */
object Biometrics {

    fun available(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows the system prompt; [onSuccess] runs only on a recognised fingerprint. Cancelling or an
     * error just closes it — the PIN keypad stays there.
     */
    fun prompt(context: Context, title: String, usePin: String, onSuccess: () -> Unit) {
        val activity = context.findActivity() ?: return
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(usePin)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        runCatching { prompt.authenticate(info) }
    }

    private tailrec fun Context.findActivity(): FragmentActivity? = when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
