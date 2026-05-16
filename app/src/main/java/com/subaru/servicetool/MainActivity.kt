package com.subaru.servicetool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.subaru.servicetool.ui.navigation.AppNavigation
import com.subaru.servicetool.ui.theme.SubaruServiceToolTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SubaruServiceToolTheme {
                AppNavigation()
            }
        }
    }
}
