package com.bolohisab

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.bolohisab.data.security.DatabaseKeyProvider
import com.bolohisab.ui.format.LocaleBootstrap
import com.bolohisab.ui.lock.AppLockGate
import com.bolohisab.ui.navigation.AppNavHost
import com.bolohisab.ui.recovery.KeyRecoveryScreen
import com.bolohisab.ui.settings.DisplaySettingsSync
import com.bolohisab.ui.theme.BoloHisabTheme
import dagger.hilt.android.AndroidEntryPoint

/** A FragmentActivity (still a ComponentActivity) because the fingerprint prompt needs one. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleBootstrap.wrap(newBase, LocaleBootstrap.read(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Checked before anything opens the database: with the key gone it could never open, and
        // the app would crash on every launch.
        val keys = DatabaseKeyProvider(applicationContext)
        var health by mutableStateOf(keys.health())
        setContent {
            DisplaySettingsSync()
            BoloHisabTheme {
                AppLockGate {
                    if (health == DatabaseKeyProvider.Health.UNREADABLE) {
                        KeyRecoveryScreen(
                            onRetry = { health = keys.health() },
                            onStartOver = { keys.startOver(); health = keys.health() },
                        )
                    } else {
                        AppNavHost()
                    }
                }
            }
        }
    }
}
