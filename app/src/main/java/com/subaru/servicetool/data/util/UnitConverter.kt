package com.subaru.servicetool.data.util

object UnitConverter {
    fun celsiusToFahrenheit(c: Double): Double = c * 9.0 / 5.0 + 32.0
    fun kpaToPsi(kpa: Double): Double = kpa * 0.14504
    fun kpaToBar(kpa: Double): Double = kpa * 0.01
}
