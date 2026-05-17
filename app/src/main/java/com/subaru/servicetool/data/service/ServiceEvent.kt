package com.subaru.servicetool.data.service

import java.util.UUID

enum class ServiceEventType(
    val displayName: String,
    val intervalKm: Int? = null,
    val intervalDays: Int? = null,
) {
    OIL_CHANGE("Oil Change",      intervalKm = 6_000,  intervalDays = 180),
    CVT_FLUID("CVT Fluid",        intervalKm = 40_000),
    BRAKE_FLUID("Brake Fluid",    intervalDays = 730),
    COOLANT("Coolant",            intervalKm = 60_000, intervalDays = 1460),
    SPARK_PLUGS("Spark Plugs",    intervalKm = 30_000),
    AIR_FILTER("Air Filter",      intervalKm = 20_000),
    TIRE_ROTATION("Tire Rotation",intervalKm = 10_000),
    OTHER("Other"),
}

data class ServiceEvent(
    val id: String = UUID.randomUUID().toString(),
    val type: ServiceEventType,
    val dateMs: Long,
    val mileageKm: Int? = null,
    val notes: String = "",
) {
    val daysSince: Long get() = (System.currentTimeMillis() - dateMs) / 86_400_000L

    fun isDueSoon(): Boolean = type.intervalDays?.let { daysSince > it * 0.8 } == true
    fun isOverdue(): Boolean = type.intervalDays?.let { daysSince > it } == true

    fun toStorageString(): String =
        listOf(id, type.ordinal, dateMs, mileageKm ?: "", notes.replace("|", "∣")).joinToString("|")

    companion object {
        fun fromStorageString(s: String): ServiceEvent? = runCatching {
            val p = s.split("|", limit = 5)
            ServiceEvent(
                id        = p[0],
                type      = ServiceEventType.entries[p[1].toInt()],
                dateMs    = p[2].toLong(),
                mileageKm = p[3].takeIf { it.isNotBlank() }?.toInt(),
                notes     = if (p.size > 4) p[4].replace("∣", "|") else "",
            )
        }.getOrNull()
    }
}
