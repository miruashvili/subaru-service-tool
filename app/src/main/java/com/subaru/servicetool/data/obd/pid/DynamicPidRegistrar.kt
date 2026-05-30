package com.subaru.servicetool.data.obd.pid

import android.util.Log
import com.subaru.servicetool.data.obd.SensorModule
import com.subaru.servicetool.data.obd.SensorPriority
import com.subaru.servicetool.data.obd.discovery.ModuleInfo
import com.subaru.servicetool.data.obd.discovery.SubaruModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DynamicPidRegistrar"

/**
 * Bridges [com.subaru.servicetool.data.obd.discovery.ModuleDiscoveryService] results into the
 * [SubaruPidRegistry], so the extensible PID framework reflects what the connected ECU actually
 * exposes — the ActiveOBD model of "discover every readable address, then read it".
 *
 * For each module's responding SSM addresses (and TPMS UDS DIDs) that are not already covered by a
 * curated [SubaruPidDefinitions] entry, a generic raw [PidDefinition] is registered. Curated PIDs
 * keep their proper scaling and decoder; newly-discovered addresses become readable as raw bytes
 * until a curated definition is added — without any change to polling/transport/parser core code.
 */
@Singleton
class DynamicPidRegistrar @Inject constructor(
    private val pidRegistry: SubaruPidRegistry,
) {
    private val _dynamicCount = MutableStateFlow(0)
    /** Number of PIDs registered from discovery during this session. */
    val dynamicCount: StateFlow<Int> = _dynamicCount.asStateFlow()

    /**
     * Registers every responding address from [modules] that is not already a curated PID.
     * Safe to call repeatedly; duplicates are de-duplicated by [SubaruPidRegistry] key.
     *
     * @return number of newly-registered (previously-unknown) PIDs.
     */
    fun registerDiscovered(modules: Map<SubaruModule, ModuleInfo>): Int {
        var added = 0
        for ((module, info) in modules) {
            if (!info.isPresent) continue
            val sensorModule = module.toSensorModule()
            val header = module.canHeader

            // SSM A8 responding addresses
            for (addr in info.respondingAddresses) {
                val address = PidAddress.Ssm2(addr, header)
                if (pidRegistry.findByAddress(address) != null) continue
                pidRegistry.register(rawSsmPid(module, sensorModule, address, addr))
                added++
            }

            // UDS Mode-22 responding DIDs (TPMS / BODY)
            for (did in info.respondingDids) {
                val address = PidAddress.Uds22(did, header)
                if (pidRegistry.findByAddress(address) != null) continue
                pidRegistry.register(rawUdsPid(module, sensorModule, address, did))
                added++
            }
        }
        _dynamicCount.value = added
        Log.i(TAG, "Registered $added dynamic PIDs from discovery " +
            "(registry now ${pidRegistry.size} total)")
        return added
    }

    /** Clears the dynamic count (curated PIDs remain; called on disconnect). */
    fun reset() {
        _dynamicCount.value = 0
    }

    // ── PID factories for discovered-but-uncurated addresses ──────────────────

    private fun rawSsmPid(
        module: SubaruModule,
        sensorModule: SensorModule,
        address: PidAddress.Ssm2,
        addr: Int,
    ) = PidDefinition(
        name        = "%s 0x%06X".format(module.name, addr),
        module      = sensorModule,
        protocol    = PidProtocol.SSM2,
        address     = address,
        length      = 1,
        scaling     = PidScaling.Raw,
        unit        = "raw",
        priority    = SensorPriority.LOW,
        decoder     = PidDecoder { b -> b.firstOrNull()?.toFloat() },
        minVal      = 0f, maxVal = 255f,
        description = "Discovered SSM address (raw byte). Add a curated definition for proper scaling.",
    )

    private fun rawUdsPid(
        module: SubaruModule,
        sensorModule: SensorModule,
        address: PidAddress.Uds22,
        did: Int,
    ) = PidDefinition(
        name        = "%s DID 0x%04X".format(module.name, did),
        module      = sensorModule,
        protocol    = PidProtocol.UDS22,
        address     = address,
        length      = 1,
        scaling     = PidScaling.Raw,
        unit        = "raw",
        priority    = SensorPriority.LOW,
        decoder     = PidDecoder { b -> b.firstOrNull()?.toFloat() },
        minVal      = 0f, maxVal = 255f,
        description = "Discovered UDS DID (raw byte). Add a curated definition for proper scaling.",
    )

    private fun SubaruModule.toSensorModule(): SensorModule = when (this) {
        SubaruModule.ECU    -> SensorModule.ECU
        SubaruModule.TCU    -> SensorModule.TCU
        SubaruModule.CVT    -> SensorModule.TCU
        SubaruModule.AWD    -> SensorModule.TCU
        SubaruModule.TPMS   -> SensorModule.BCM
        SubaruModule.BODY   -> SensorModule.BCM
        SubaruModule.HYBRID -> SensorModule.ECU
    }
}
