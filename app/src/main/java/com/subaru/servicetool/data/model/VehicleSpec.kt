package com.subaru.servicetool.data.model

enum class Market { GLOBAL, JDM, EU }

val Market.displayName: String get() = when (this) {
    Market.GLOBAL -> "Global"
    Market.JDM    -> "JDM"
    Market.EU     -> "EU"
}

data class VehicleSpec(
    val year: Int,
    val modelName: String,
    val engineCode: String,
    val engineDisplayName: String,
    val isTurbo: Boolean,
    val market: Market = Market.GLOBAL,
    val generation: String = "",
    val cvtType: String? = null,
    val knownIssueIds: List<String> = emptyList(),
    val obdProtocol: String = "ISO 15765-4 CAN 11bit 500kbps",
    val ssmSupported: Boolean = true,
) {
    val generationBadge: String
        get() = Regex("\\(([^)]+)\\)").find(generation)?.groupValues?.get(1) ?: generation
}
