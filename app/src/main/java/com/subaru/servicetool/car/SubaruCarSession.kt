package com.subaru.servicetool.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class SubaruCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen =
        SubaruCarScreen(carContext)
}
