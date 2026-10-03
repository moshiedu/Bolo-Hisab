package com.bolohisab

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.bolohisab.ui.format.LocaleBootstrap
import com.bolohisab.ui.lock.AppLockGate
import com.bolohisab.ui.navigation.AppNavHost
import com.bolohisab.ui.settings.DisplaySettingsSync
import com.bolohisab.ui.theme.BoloHisabTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleBootstrap.wrap(newBase, LocaleBootstrap.read(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DisplaySettingsSync()
            BoloHisabTheme { AppLockGate { AppNavHost() } }
        }
    }
}
