package com.subaru.servicetool.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.subaru.servicetool.data.obd.ObdPids
import com.subaru.servicetool.data.obd.ObdQueryEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CarAppEntryPoint {
    fun obdQueryEngine(): ObdQueryEngine
}

class SubaruCarScreen(carContext: CarContext) : Screen(carContext) {

    private val obdEngine: ObdQueryEngine = EntryPointAccessors
        .fromApplication(carContext.applicationContext, CarAppEntryPoint::class.java)
        .obdQueryEngine()

    init {
        obdEngine.sensorValues
            .onEach { invalidate() }
            .launchIn(lifecycleScope)
    }

    override fun onGetTemplate(): Template {
        val v = obdEngine.sensorValues.value

        if (v.isEmpty()) {
            return MessageTemplate.Builder(
                "Connect your ELM327 OBD adapter via Bluetooth in the Subaru Service Tool app to view live sensor data."
            )
                .setTitle("Subaru Service Tool")
                .setHeaderAction(Action.APP_ICON)
                .build()
        }

        fun fmt(cmd: String, suffix: String, decimals: Int = 0): String =
            v[cmd]?.let { "%.${decimals}f $suffix".format(it).trim() } ?: "--"

        val list = ItemList.Builder()
            .addItem(Row.Builder()
                .setTitle("Engine RPM")
                .addText(fmt(ObdPids.RPM.cmd, "rpm"))
                .build())
            .addItem(Row.Builder()
                .setTitle("Vehicle Speed")
                .addText(fmt(ObdPids.SPEED.cmd, "km/h"))
                .build())
            .addItem(Row.Builder()
                .setTitle("Coolant Temp")
                .addText(fmt(ObdPids.COOLANT_TEMP.cmd, "°C"))
                .build())
            .addItem(Row.Builder()
                .setTitle("Oil Temp")
                .addText(fmt(ObdPids.OIL_TEMP.cmd, "°C"))
                .build())
            .addItem(Row.Builder()
                .setTitle("CVT Temp")
                .addText(fmt(ObdPids.CVT_TEMP.cmd, "°C"))
                .build())
            .addItem(Row.Builder()
                .setTitle("Battery")
                .addText(fmt(ObdPids.VOLTAGE.cmd, "V", 1))
                .build())
            .build()

        return ListTemplate.Builder()
            .setTitle("Subaru Service Tool")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(list)
            .build()
    }
}
