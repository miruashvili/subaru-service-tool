package com.subaru.servicetool.ui.dashboard

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.subaru.servicetool.data.obd.ObdPids
import com.subaru.servicetool.data.obd.ObdQueryEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SportGaugeViewModel @Inject constructor(
    obdEngine: ObdQueryEngine,
) : ViewModel() {
    val sensorValues: StateFlow<Map<String, Float>> = obdEngine.sensorValues
}

@Composable
fun SportGaugeScreen(
    onBack: () -> Unit,
    viewModel: SportGaugeViewModel = hiltViewModel(),
) {
    val sensorValues by viewModel.sensorValues.collectAsState()

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07070B)),
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    setBackgroundColor(android.graphics.Color.parseColor("#07070B"))
                    loadUrl("file:///android_asset/sport_gauge.html")
                }
            },
            update = { wv ->
                if (sensorValues.isNotEmpty()) {
                    val json = buildSportGaugeJson(sensorValues)
                    wv.evaluateJavascript(
                        "if(typeof updateFromNative==='function')updateFromNative($json)", null,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        IconButton(
            onClick  = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint               = Color.White.copy(alpha = 0.65f),
            )
        }
    }
}

private fun buildSportGaugeJson(values: Map<String, Float>): String {
    val data = linkedMapOf(
        "rpm"      to (values[ObdPids.RPM.cmd]           ?: 0f),
        "speed"    to (values[ObdPids.SPEED.cmd]         ?: 0f),
        "cool"     to (values[ObdPids.COOLANT_TEMP.cmd]  ?: 70f),
        "oil"      to (values[ObdPids.OIL_TEMP.cmd]      ?: 0f),
        "cvt"      to (values[ObdPids.CVT_TEMP.cmd]      ?: 0f),
        "boost"    to (values[ObdPids.MAP.cmd]            ?: 0f),
        "iat"      to (values[ObdPids.INTAKE_TEMP.cmd]   ?: 20f),
        "bat"      to (values[ObdPids.VOLTAGE.cmd]        ?: 12.0f),
        "afr"      to (values[ObdPids.FUEL_TRIM_ST.cmd]  ?: 0f),
        "load"     to (values[ObdPids.ENGINE_LOAD.cmd]   ?: 0f),
        "ltrim"    to (values[ObdPids.FUEL_TRIM_LT.cmd]  ?: 0f),
        "throttle" to (values[ObdPids.THROTTLE.cmd]      ?: 0f),
        "awd"      to (values[ObdPids.AWD_DUTY.cmd]      ?: 0f),
    )
    return buildString {
        append('{')
        data.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) append(',')
            append('"'); append(k); append('"'); append(':'); append(v)
        }
        append('}')
    }
}
