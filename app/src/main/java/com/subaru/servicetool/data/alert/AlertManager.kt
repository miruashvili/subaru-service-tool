package com.subaru.servicetool.data.alert

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import com.subaru.servicetool.data.bluetooth.BluetoothConnectionState
import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
import com.subaru.servicetool.data.obd.ObdPids
import com.subaru.servicetool.data.obd.ObdQueryEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class TempAlertLevel { NONE, CVT_HOT, OIL_HOT, COOLANT_CRITICAL }

@Singleton
class AlertManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val obdEngine: ObdQueryEngine,
    private val btManager: OBDBluetoothManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _alertLevel = MutableStateFlow(TempAlertLevel.NONE)
    val alertLevel: StateFlow<TempAlertLevel> = _alertLevel.asStateFlow()

    private var toneGen: ToneGenerator? = null
    private var audioJob: Job? = null
    private var oilBeepFired = false

    init {
        scope.launch { monitorSensors() }
        scope.launch {
            btManager.connectionState.collect { state ->
                if (state !is BluetoothConnectionState.Connected) {
                    stopAudio()
                    _alertLevel.value = TempAlertLevel.NONE
                    oilBeepFired = false
                }
            }
        }
    }

    private suspend fun monitorSensors() {
        obdEngine.sensorValues.collect { values ->
            val coolant   = values[ObdPids.COOLANT_TEMP.cmd]
            val oilTemp   = values[ObdPids.OIL_TEMP.cmd]
            val intakeTemp = values[ObdPids.INTAKE_TEMP.cmd]

            val newLevel = when {
                coolant   != null && coolant   >= 120f -> TempAlertLevel.COOLANT_CRITICAL
                oilTemp   != null && oilTemp   >= 127f -> TempAlertLevel.OIL_HOT
                intakeTemp != null && intakeTemp >= 121f -> TempAlertLevel.CVT_HOT
                else -> TempAlertLevel.NONE
            }

            val prev = _alertLevel.value
            _alertLevel.value = newLevel

            when (newLevel) {
                TempAlertLevel.COOLANT_CRITICAL -> {
                    oilBeepFired = false
                    if (prev != TempAlertLevel.COOLANT_CRITICAL) startContinuousBeep()
                }
                TempAlertLevel.OIL_HOT -> {
                    if (!oilBeepFired) {
                        oilBeepFired = true
                        stopAudio()
                        audioJob = scope.launch { playThreeBeeps() }
                    }
                }
                TempAlertLevel.CVT_HOT -> stopAudio()
                TempAlertLevel.NONE -> {
                    stopAudio()
                    oilBeepFired = false
                }
            }
        }
    }

    // Continuous 100 ms beep / 200 ms silence until level drops
    private fun startContinuousBeep() {
        audioJob?.cancel()
        audioJob = scope.launch {
            val tg = getToneGen() ?: return@launch
            while (_alertLevel.value == TempAlertLevel.COOLANT_CRITICAL) {
                tg.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                delay(300L)
            }
            runCatching { tg.stopTone() }
        }
    }

    // 3 beeps (100 ms on, 200 ms off) then stop
    private suspend fun playThreeBeeps() {
        val tg = getToneGen() ?: return
        repeat(3) {
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
            delay(300L)
        }
        runCatching { tg.stopTone() }
    }

    private fun stopAudio() {
        audioJob?.cancel()
        audioJob = null
        runCatching { toneGen?.stopTone() }
    }

    private fun getToneGen(): ToneGenerator? {
        if (toneGen == null) {
            toneGen = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 100) }.getOrNull()
        }
        return toneGen
    }

    fun release() {
        stopAudio()
        runCatching { toneGen?.release() }
        toneGen = null
    }
}
