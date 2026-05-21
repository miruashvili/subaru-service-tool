package com.subaru.servicetool.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.subaru.servicetool.R
import com.subaru.servicetool.data.model.VehicleSpec
import com.subaru.servicetool.data.obd.ObdPid
import com.subaru.servicetool.data.obd.ObdPids
import com.subaru.servicetool.data.obd.ObdQueryEngine
import com.subaru.servicetool.data.preferences.UserPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

// Sentinel cmd values that represent composite widgets rather than real PIDs
private val WIDGET_SENTINELS = setOf("TPMS_ALL", "FUEL_CONS")
private const val MAX_GRID_ITEMS = 6  // 2 wide slots + 4 gauge slots

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CarAppEntryPoint {
    fun obdQueryEngine(): ObdQueryEngine
    fun userPreferences(): UserPreferences
}

/**
 * Android Auto screen that mirrors the phone's Wide Mode Dashboard Grid, with dynamic
 * layout selection based on vehicle configuration:
 *   - Non-Turbo (4 items): OIL_TEMP, CVT_TEMP, ECM_COOLANT_TEMP, INTAKE_TEMP
 *   - Turbo+CVT (6 items): OIL_TEMP, CVT_TEMP, ECM_COOLANT_TEMP / Boost, INTAKE_TEMP, EGT
 *   - Turbo+Manual (6 items): OIL_TEMP, AFR, ECM_COOLANT_TEMP / Boost, INTAKE_TEMP, EGT
 * When no vehicle is configured, falls back to user's configured gauge slots.
 */
class SubaruCarScreen(carContext: CarContext) : Screen(carContext) {

    private val ep = EntryPointAccessors
        .fromApplication(carContext.applicationContext, CarAppEntryPoint::class.java)

    private val obdEngine: ObdQueryEngine = ep.obdQueryEngine()
    private val userPrefs: UserPreferences = ep.userPreferences()

    // Flat lookup: PID cmd string → ObdPid definition
    private val pidMap: Map<String, ObdPid> = ObdPids.ALL.associateBy { it.cmd }

    // Single CarIcon instance shared by all grid cells (host applies its own tinting)
    private val gaugeIcon: CarIcon = CarIcon.Builder(
        IconCompat.createWithResource(carContext, R.drawable.ic_car_gauge)
    ).build()

    // ── Cached state — written by Flow collectors, triggers invalidate() ────────

    private var sensorValues: Map<String, Float> = emptyMap()
    private var selectedVehicle: VehicleSpec? = null

    // Fallback gauge slots (used when no vehicle is configured)
    private var wideSlots: List<String> = listOf(
        ObdPids.AWD_DUTY.cmd,
        ObdPids.TPMS_FL.cmd,
    )
    private var gaugeSlots: List<String> = listOf(
        ObdPids.COOLANT_TEMP.cmd,
        ObdPids.CVT_TEMP.cmd,
        ObdPids.RPM.cmd,
        ObdPids.SPEED.cmd,
    )

    init {
        obdEngine.sensorValues
            .onEach { sensorValues = it; invalidate() }
            .launchIn(lifecycleScope)

        userPrefs.selectedVehicle
            .onEach { spec ->
                selectedVehicle = spec
                obdEngine.setCarActivePids(buildCarPidSet(spec))
                invalidate()
            }
            .launchIn(lifecycleScope)

        userPrefs.wideGaugeSlots
            .onEach { wideSlots = it; invalidate() }
            .launchIn(lifecycleScope)

        userPrefs.gaugeSlots
            .onEach { gaugeSlots = it; invalidate() }
            .launchIn(lifecycleScope)

        // Release car-specific PID hints when this screen is destroyed
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                obdEngine.setCarActivePids(emptySet())
            }
        })
    }

    // ── Car-specific active PID set ───────────────────────────────────────────

    private fun buildCarPidSet(spec: VehicleSpec?): Set<ObdPid> {
        spec ?: return emptySet()
        return if (spec.isTurbo) {
            buildSet {
                add(ObdPids.OIL_TEMP)
                add(ObdPids.ECM_COOLANT_TEMP)
                add(ObdPids.INTAKE_TEMP)
                add(ObdPids.MAP)
                add(ObdPids.BAROMETRIC_PRESS)
                add(ObdPids.EGT)
                if (spec.cvtType != null) add(ObdPids.CVT_TEMP) else add(ObdPids.AFR)
            }
        } else {
            setOf(ObdPids.OIL_TEMP, ObdPids.CVT_TEMP, ObdPids.ECM_COOLANT_TEMP, ObdPids.INTAKE_TEMP)
        }
    }

    // ── Template rendering ────────────────────────────────────────────────────

    override fun onGetTemplate(): Template {
        val v = sensorValues

        if (v.isEmpty()) {
            return MessageTemplate.Builder(
                "Connect your ELM327 OBD adapter via Bluetooth in the Subaru Service Tool app on your phone to see live sensor data here."
            )
                .setTitle("Subaru Service Tool")
                .setHeaderAction(Action.APP_ICON)
                .build()
        }

        val listBuilder = ItemList.Builder()
        val spec = selectedVehicle

        if (spec != null) {
            for (item in buildDynamicGrid(spec, v)) listBuilder.addItem(item)
        } else {
            // Vehicle not configured — fall back to user's configured gauge slots
            val resolvedSlots = (wideSlots + gaugeSlots)
                .filter { it.isNotBlank() && it !in WIDGET_SENTINELS }
                .mapNotNull { cmd -> pidMap[cmd]?.let { pid -> cmd to pid } }
                .take(MAX_GRID_ITEMS)

            val displaySlots = resolvedSlots.ifEmpty {
                listOf(
                    ObdPids.RPM, ObdPids.SPEED,
                    ObdPids.COOLANT_TEMP, ObdPids.THROTTLE,
                    ObdPids.OIL_TEMP, ObdPids.CVT_TEMP,
                ).map { it.cmd to it }
            }

            for ((cmd, pid) in displaySlots) {
                val raw = v[cmd]
                listBuilder.addItem(makeGaugeItem(pid.name, if (raw != null) formatValue(raw, pid) else "--"))
            }
        }

        return GridTemplate.Builder()
            .setTitle("Subaru Service Tool")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .build()
    }

    // ── Dynamic layout builders ───────────────────────────────────────────────

    private fun buildDynamicGrid(spec: VehicleSpec, v: Map<String, Float>): List<GridItem> =
        if (spec.isTurbo) buildTurboGrid(spec, v) else buildNaGrid(v)

    private fun buildNaGrid(v: Map<String, Float>): List<GridItem> = listOf(
        ObdPids.OIL_TEMP          to v[ObdPids.OIL_TEMP.cmd],
        ObdPids.CVT_TEMP          to v[ObdPids.CVT_TEMP.cmd],
        ObdPids.ECM_COOLANT_TEMP  to v[ObdPids.ECM_COOLANT_TEMP.cmd],
        ObdPids.INTAKE_TEMP       to v[ObdPids.INTAKE_TEMP.cmd],
    ).map { (pid, raw) -> makeGaugeItem(pid.name, if (raw != null) formatValue(raw, pid) else "--") }

    private fun buildTurboGrid(spec: VehicleSpec, v: Map<String, Float>): List<GridItem> {
        // Row 1 slot 2: CVT fluid temp for CVT cars, AFR for manual
        val row1slot2: GridItem = if (spec.cvtType != null) {
            val raw = v[ObdPids.CVT_TEMP.cmd]
            makeGaugeItem(ObdPids.CVT_TEMP.name, if (raw != null) formatValue(raw, ObdPids.CVT_TEMP) else "--")
        } else {
            val raw = v[ObdPids.AFR.cmd]
            makeGaugeItem(ObdPids.AFR.name, if (raw != null) formatValue(raw, ObdPids.AFR) else "--")
        }

        return listOf(
            pidItem(ObdPids.OIL_TEMP, v),
            row1slot2,
            pidItem(ObdPids.ECM_COOLANT_TEMP, v),
            boostGridItem(v),
            pidItem(ObdPids.INTAKE_TEMP, v),
            pidItem(ObdPids.EGT, v),
        )
    }

    private fun pidItem(pid: ObdPid, v: Map<String, Float>): GridItem {
        val raw = v[pid.cmd]
        return makeGaugeItem(pid.name, if (raw != null) formatValue(raw, pid) else "--")
    }

    private fun boostGridItem(v: Map<String, Float>): GridItem {
        val map  = v[ObdPids.MAP.cmd]
        val baro = v[ObdPids.BAROMETRIC_PRESS.cmd]
        val title = if (map != null && baro != null) "%.2f bar".format((map - baro) / 100f) else "--"
        return makeGaugeItem("Boost", title)
    }

    private fun makeGaugeItem(label: String, value: String): GridItem =
        GridItem.Builder()
            .setImage(gaugeIcon)
            .setTitle(value)
            .setText(label)
            .build()

    // ── Value formatting ──────────────────────────────────────────────────────

    private fun formatValue(value: Float, pid: ObdPid): String = when (pid.unit) {
        "ratio"  -> "%.2fx".format(value)
        "%"      -> "%.0f%%".format(value)
        "rpm"    -> "%.0f rpm".format(value)
        "V"      -> "%.1f V".format(value)
        "°C"     -> "%.0f°C".format(value)
        "km/h"   -> "%.0f km/h".format(value)
        "kPa"    -> "%.0f kPa".format(value)
        "g/s"    -> "%.1f g/s".format(value)
        "°"      -> "%.1f°".format(value)
        "s"      -> "%.0f s".format(value)
        "AFR"    -> "%.2f:1".format(value)
        else     -> "%.1f ${pid.unit}".format(value)
    }
}
