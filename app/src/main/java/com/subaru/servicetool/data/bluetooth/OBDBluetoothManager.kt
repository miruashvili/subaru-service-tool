package com.subaru.servicetool.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import android.util.Log
import com.subaru.servicetool.data.preferences.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OBDBluetoothManager"
private const val MAX_RECONNECT = 5
private const val RECONNECT_DELAY_MS = 3_000L
private const val KEEP_ALIVE_INTERVAL_MS = 1_500L
private const val GATT_TIMEOUT_MS = 10_000L
private const val MTU_SIZE = 512

private val ELM327_INIT = listOf(
    "ATZ\r"     to 1000L,
    "ATE0\r"    to  300L,
    "ATL0\r"    to  300L,
    "ATH1\r"    to  300L,
    "ATSP6\r"   to  300L,
    "ATAT1\r"   to  300L,
    "ATST FF\r" to  300L,
)

@Singleton
class OBDBluetoothManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences,
) {
    // ── Public state ──────────────────────────────────────────────────────────
    private val _state = MutableStateFlow<BluetoothConnectionState>(BluetoothConnectionState.Disconnected)
    val connectionState: StateFlow<BluetoothConnectionState> = _state.asStateFlow()

    private val _incomingData = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingData: SharedFlow<String> = _incomingData.asSharedFlow()

    // ── Internal ──────────────────────────────────────────────────────────────
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectionLock = AtomicBoolean(false)
    private val commandMutex = Mutex()
    private val commandActive = AtomicBoolean(false)
    private val cmdResponseChannel = Channel<String>(Channel.CONFLATED)

    var lastDeviceMac: String? = null; private set
    var lastConnectionType: OBDConnectionType? = null; private set
    private var reconnectAttempts = 0
    private var shouldAutoReconnect = false

    // BLE state
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private val gattEvents = Channel<GattEvent>(Channel.UNLIMITED)
    private val bleBuffer = StringBuilder()

    // SPP state
    private var sppSocket: BluetoothSocket? = null

    // Jobs — tracked so disconnect() can cancel them immediately
    private var keepAliveJob: Job? = null
    @Volatile private var activeConnectionJob: Job? = null

    private val btAdapter
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE)
                as? android.bluetooth.BluetoothManager)?.adapter

    init {
        scope.launch { restoreLastDevice() }
    }

    // ── Restore from prefs ────────────────────────────────────────────────────

    private suspend fun restoreLastDevice() {
        val mac = userPreferences.lastDeviceMac.first() ?: return
        val type = userPreferences.lastDeviceType.first()
            ?.let { runCatching { OBDConnectionType.valueOf(it) }.getOrNull() }
            ?: return
        lastDeviceMac = mac
        lastConnectionType = type
        delay(2_000) // Let the app fully initialise first
        reconnectToLastDevice()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun connectBle(device: BluetoothDevice) {
        if (!connectionLock.compareAndSet(false, true)) return
        shouldAutoReconnect = true
        reconnectAttempts = 0
        lastDeviceMac = device.address
        lastConnectionType = OBDConnectionType.BLE
        activeConnectionJob = scope.launch {
            scope.launch { userPreferences.saveLastDevice(device.address, OBDConnectionType.BLE) }
            try {
                connectBleInternal(device)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected BLE error", e)
                handleDisconnect(e.message ?: "BLE error")
            } finally {
                connectionLock.set(false)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectSpp(device: BluetoothDevice) {
        if (!connectionLock.compareAndSet(false, true)) return
        shouldAutoReconnect = true
        reconnectAttempts = 0
        lastDeviceMac = device.address
        lastConnectionType = OBDConnectionType.SPP
        activeConnectionJob = scope.launch {
            scope.launch { userPreferences.saveLastDevice(device.address, OBDConnectionType.SPP) }
            try {
                connectSppInternal(device)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected SPP error", e)
                handleDisconnect(e.message ?: "SPP error")
            } finally {
                connectionLock.set(false)
            }
        }
    }

    fun disconnect() {
        shouldAutoReconnect = false
        reconnectAttempts = 0
        keepAliveJob?.cancel(); keepAliveJob = null
        activeConnectionJob?.cancel(); activeConnectionJob = null
        writeChar = null
        closeGatt()
        closeSpp()
        connectionLock.set(false)
        _state.value = BluetoothConnectionState.Disconnected
    }

    fun reconnectToLastDevice() {
        val mac = lastDeviceMac ?: return
        val type = lastConnectionType ?: return
        @SuppressLint("MissingPermission")
        val device = btAdapter?.getRemoteDevice(mac) ?: return
        when (type) {
            OBDConnectionType.BLE -> connectBle(device)
            OBDConnectionType.SPP -> connectSpp(device)
        }
    }

    /** Sends a command and waits up to 5 s for a '>' terminated response. Null on timeout. */
    suspend fun sendCommand(command: String): String? {
        if (_state.value !is BluetoothConnectionState.Connected) return null
        return commandMutex.withLock {
            commandActive.set(true)
            bleBuffer.clear()
            try {
                writeToAdapter("$command\r")
                withTimeoutOrNull(5_000L) { cmdResponseChannel.receive() }
            } finally {
                commandActive.set(false)
            }
        }
    }

    // ── BLE connection flow ───────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private suspend fun connectBleInternal(device: BluetoothDevice) {
        _state.value = BluetoothConnectionState.Connecting
        drainGattEvents()
        bleBuffer.clear()

        val callback = buildGattCallback()
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)

        // 1. Wait for GATT connected
        val connEvent = awaitGattEvent(GATT_TIMEOUT_MS) { it is GattEvent.ConnectionStateChange }
                as? GattEvent.ConnectionStateChange
        if (connEvent?.newState != BluetoothProfile.STATE_CONNECTED) {
            Log.w(TAG, "GATT connection timeout/failed (status=${connEvent?.status})")
            handleDisconnect("Failed to connect")
            return
        }

        // 2. Request MTU 512
        gatt?.requestMtu(MTU_SIZE)
        awaitGattEvent(5_000L) { it is GattEvent.MtuChanged }

        // 3. Discover services
        gatt?.discoverServices()
        val svcEvent = awaitGattEvent(GATT_TIMEOUT_MS) { it is GattEvent.ServicesDiscovered }
                as? GattEvent.ServicesDiscovered
        if (svcEvent?.status != BluetoothGatt.GATT_SUCCESS) {
            Log.w(TAG, "Service discovery failed")
            handleDisconnect("Service discovery failed")
            return
        }

        // 4. Detect adapter type and find characteristics
        val (wChar, nChar) = findCharacteristics() ?: run {
            Log.w(TAG, "No suitable characteristics found")
            handleDisconnect("Unsupported BLE OBD adapter")
            return
        }
        writeChar = wChar

        // 5. Enable notifications on the notify characteristic
        enableNotifications(nChar)

        // 6. ELM327 init sequence (fire-and-wait, no response needed)
        for ((cmd, waitMs) in ELM327_INIT) {
            writeBle(cmd)
            delay(waitMs)
        }

        val name = runCatching { device.name }.getOrNull() ?: device.address
        _state.value = BluetoothConnectionState.Connected(name, OBDConnectionType.BLE)
        reconnectAttempts = 0
        Log.i(TAG, "BLE ready: $name")

        startKeepAlive()

        // Suspend here, routing data and watching for disconnect
        try {
            monitorBleEvents()
        } catch (e: CancellationException) {
            throw e // Explicit disconnect() — do not reconnect
        }
        handleDisconnect(null)
    }

    @SuppressLint("MissingPermission")
    private fun findCharacteristics(): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic>? {
        val services = gatt?.services ?: return null

        // Vgate iCar Pro
        services.find { it.uuid == BleUuids.VGATE_SERVICE }?.let { svc ->
            val w = svc.getCharacteristic(BleUuids.VGATE_WRITE)
            val n = svc.getCharacteristic(BleUuids.VGATE_NOTIFY)
            if (w != null && n != null) return w to n
        }

        // Generic BLE OBD
        services.find { it.uuid == BleUuids.GENERIC_SERVICE }?.let { svc ->
            val w = svc.getCharacteristic(BleUuids.GENERIC_WRITE)
            val n = svc.getCharacteristic(BleUuids.GENERIC_NOTIFY)
            if (w != null && n != null) return w to n
        }

        // OBDLink CX — pick first writable + notifiable pair in its service
        services.find { it.uuid == BleUuids.OBDLINK_SERVICE }
            ?.let { pickWriteNotifyPair(it.characteristics) }
            ?.let { return it }

        // Fallback: scan all services
        for (svc in services) {
            pickWriteNotifyPair(svc.characteristics)?.let { return it }
        }
        return null
    }

    private fun pickWriteNotifyPair(
        chars: List<BluetoothGattCharacteristic>,
    ): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic>? {
        val w = chars.firstOrNull { c ->
            c.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
        }
        val n = chars.firstOrNull { c ->
            c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        }
        return if (w != null && n != null) w to n else null
    }

    @SuppressLint("MissingPermission")
    private suspend fun enableNotifications(char: BluetoothGattCharacteristic) {
        gatt?.setCharacteristicNotification(char, true)
        val cccd = char.getDescriptor(BleUuids.CCCD) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt?.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt?.writeDescriptor(cccd)
        }
        awaitGattEvent(5_000L) { it is GattEvent.DescriptorWrite }
    }

    /** Runs until a disconnect event arrives; routes incoming data to listeners/command responses. */
    private suspend fun monitorBleEvents() {
        for (event in gattEvents) {
            when (event) {
                is GattEvent.ConnectionStateChange -> {
                    if (event.newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.d(TAG, "BLE disconnected (status ${event.status})")
                        break
                    }
                }
                is GattEvent.CharacteristicChanged -> {
                    val text = String(event.value, Charsets.ISO_8859_1)
                    bleBuffer.append(text)
                    if (bleBuffer.contains('>')) {
                        val response = bleBuffer.toString().trim()
                        bleBuffer.clear()
                        if (commandActive.get()) cmdResponseChannel.trySend(response)
                        else _incomingData.tryEmit(response)
                    }
                }
                else -> Unit
            }
        }
    }

    // ── Classic SPP ───────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private suspend fun connectSppInternal(device: BluetoothDevice) {
        _state.value = BluetoothConnectionState.Connecting
        val socket = try {
            btAdapter?.cancelDiscovery()
            val s = device.createRfcommSocketToServiceRecord(BleUuids.SPP)
            s.connect()
            s
        } catch (e: IOException) {
            Log.w(TAG, "SPP connect failed: ${e.message}")
            handleDisconnect(e.message ?: "SPP connection failed")
            return
        }
        sppSocket = socket

        for ((cmd, waitMs) in ELM327_INIT) {
            writeSpp(cmd)
            delay(waitMs)
        }

        val name = runCatching { device.name }.getOrNull() ?: device.address
        _state.value = BluetoothConnectionState.Connected(name, OBDConnectionType.SPP)
        reconnectAttempts = 0
        Log.i(TAG, "SPP ready: $name")

        startKeepAlive()
        sppReadLoop(socket)

        // Check for cancellation before scheduling reconnect
        currentCoroutineContext().ensureActive()
        handleDisconnect(null)
    }

    private suspend fun sppReadLoop(socket: BluetoothSocket) {
        val buf = ByteArray(1024)
        val sb = StringBuilder()
        try {
            while (true) {
                val n = socket.inputStream.read(buf)
                if (n < 0) break
                sb.append(String(buf, 0, n, Charsets.ISO_8859_1))
                if (sb.contains('>')) {
                    val response = sb.toString().trim()
                    sb.clear()
                    if (commandActive.get()) cmdResponseChannel.trySend(response)
                    else _incomingData.tryEmit(response)
                }
            }
        } catch (_: IOException) {
            // Socket closed — natural end of loop
        }
    }

    // ── Keep-alive ────────────────────────────────────────────────────────────

    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (_state.value is BluetoothConnectionState.Connected) {
                delay(KEEP_ALIVE_INTERVAL_MS)
                if (_state.value !is BluetoothConnectionState.Connected) break
                // Only send if no user command is in flight
                if (!commandMutex.isLocked) {
                    commandMutex.withLock {
                        if (_state.value is BluetoothConnectionState.Connected) {
                            writeToAdapter("ATI\r")
                        }
                    }
                }
            }
        }
    }

    // ── Write helpers ─────────────────────────────────────────────────────────

    private fun writeToAdapter(cmd: String) = when (lastConnectionType) {
        OBDConnectionType.BLE -> writeBle(cmd)
        OBDConnectionType.SPP -> writeSpp(cmd)
        null -> Unit
    }

    @SuppressLint("MissingPermission")
    private fun writeBle(cmd: String) {
        val g = gatt ?: return
        val c = writeChar ?: return
        val bytes = cmd.toByteArray(Charsets.ISO_8859_1)
        val writeType =
            if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(c, bytes, writeType)
        } else {
            @Suppress("DEPRECATION")
            c.value = bytes
            @Suppress("DEPRECATION")
            c.writeType = writeType
            @Suppress("DEPRECATION")
            g.writeCharacteristic(c)
        }
    }

    private fun writeSpp(cmd: String) {
        try { sppSocket?.outputStream?.write(cmd.toByteArray(Charsets.ISO_8859_1)) }
        catch (_: IOException) { }
    }

    // ── Disconnect / Reconnect ────────────────────────────────────────────────

    private fun handleDisconnect(errorMsg: String?) {
        keepAliveJob?.cancel(); keepAliveJob = null
        writeChar = null
        closeGatt()
        closeSpp()
        connectionLock.set(false)

        if (!shouldAutoReconnect || reconnectAttempts >= MAX_RECONNECT) {
            _state.value = if (errorMsg != null)
                BluetoothConnectionState.Error(errorMsg)
            else
                BluetoothConnectionState.Disconnected
            return
        }

        _state.value = BluetoothConnectionState.Reconnecting
        reconnectAttempts++
        Log.i(TAG, "Reconnect $reconnectAttempts/$MAX_RECONNECT in ${RECONNECT_DELAY_MS}ms")

        activeConnectionJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            val mac = lastDeviceMac ?: return@launch
            @SuppressLint("MissingPermission")
            val device = btAdapter?.getRemoteDevice(mac) ?: return@launch
            if (!connectionLock.compareAndSet(false, true)) return@launch
            try {
                when (lastConnectionType) {
                    OBDConnectionType.BLE -> connectBleInternal(device)
                    OBDConnectionType.SPP -> connectSppInternal(device)
                    null -> connectionLock.set(false)
                }
            } catch (e: CancellationException) {
                connectionLock.set(false)
                throw e
            } catch (e: Exception) {
                connectionLock.set(false)
                _state.value = BluetoothConnectionState.Error(e.message ?: "Reconnect failed")
            }
        }
    }

    // ── Resource cleanup ──────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        gatt?.let { g ->
            refreshGattCache(g)  // Flush GATT cache before close
            try { g.disconnect() } catch (_: Exception) { }
            try { g.close()     } catch (_: Exception) { }
        }
        gatt = null
    }

    private fun closeSpp() {
        try { sppSocket?.close() } catch (_: IOException) { }
        sppSocket = null
    }

    // GATT cache flush via hidden API — suppresses bond state caching that
    // causes the Vgate iCar Pro to drop connection after 2-3 s on reconnect.
    private fun refreshGattCache(g: BluetoothGatt): Boolean = try {
        g.javaClass.getMethod("refresh").invoke(g) as? Boolean ?: false
    } catch (_: Exception) { false }

    // ── GATT event helpers ────────────────────────────────────────────────────

    private fun drainGattEvents() {
        while (gattEvents.tryReceive().isSuccess) Unit
    }

    /**
     * Waits up to [timeoutMs] for the next GATT event matching [predicate].
     * Returns null on timeout or if a DISCONNECTED event is received first.
     * Propagates CancellationException so the caller's coroutine can be cancelled.
     */
    private suspend fun awaitGattEvent(timeoutMs: Long, predicate: (GattEvent) -> Boolean): GattEvent? =
        withTimeoutOrNull(timeoutMs) {
            var result: GattEvent? = null
            while (result == null) {
                val event = gattEvents.receiveCatching().getOrNull() ?: break
                if (event is GattEvent.ConnectionStateChange &&
                    event.newState == BluetoothProfile.STATE_DISCONNECTED) break
                if (predicate(event)) result = event
            }
            result
        }

    // ── GATT Callback ─────────────────────────────────────────────────────────

    private fun buildGattCallback() = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            gattEvents.trySend(GattEvent.ConnectionStateChange(newState, status))
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            gattEvents.trySend(GattEvent.ServicesDiscovered(status))
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            gattEvents.trySend(GattEvent.MtuChanged(mtu))
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int,
        ) {
            gattEvents.trySend(GattEvent.DescriptorWrite(status))
        }

        // API < 33
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic,
        ) {
            gattEvents.trySend(GattEvent.CharacteristicChanged(characteristic.value ?: ByteArray(0)))
        }

        // API >= 33
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray,
        ) {
            gattEvents.trySend(GattEvent.CharacteristicChanged(value))
        }
    }
}

// ── Internal GATT event types ─────────────────────────────────────────────────

private sealed class GattEvent {
    data class ConnectionStateChange(val newState: Int, val status: Int) : GattEvent()
    data class ServicesDiscovered(val status: Int) : GattEvent()
    data class MtuChanged(val mtu: Int) : GattEvent()
    data class CharacteristicChanged(val value: ByteArray) : GattEvent()
    data class DescriptorWrite(val status: Int) : GattEvent()
}
