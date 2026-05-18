package com.subaru.servicetool

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.subaru.servicetool.data.bluetooth.BluetoothConnectionState
import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
import com.subaru.servicetool.data.preferences.UserPreferences
import com.subaru.servicetool.ui.navigation.AppNavigation
import com.subaru.servicetool.ui.theme.SubaruServiceToolTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var btManager: OBDBluetoothManager
    @Inject lateinit var userPreferences: UserPreferences

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("locale_pref", MODE_PRIVATE)
        val lang = prefs.getString("language", "") ?: ""
        if (lang.isNotEmpty()) {
            val locale = Locale.forLanguageTag(lang)
            // setDefault makes Locale.getDefault() return the chosen locale throughout the
            // process, which is required for our forLocale() helpers to work correctly.
            // createConfigurationContext alone only affects resource resolution, not the JVM default.
            Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            super.attachBaseContext(newBase.createConfigurationContext(config))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by userPreferences.themeMode.collectAsState(initial = "system")
            val landscapeEnabled by userPreferences.landscapeEnabled.collectAsState(initial = false)
            val btState by btManager.connectionState.collectAsState()

            LaunchedEffect(landscapeEnabled) {
                requestedOrientation = if (landscapeEnabled)
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR
                else
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }

            LaunchedEffect(btState) {
                if (btState is BluetoothConnectionState.Connected)
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            SubaruServiceToolTheme(themeMode = themeMode) {
                AppNavigation()
            }
        }
    }
}
