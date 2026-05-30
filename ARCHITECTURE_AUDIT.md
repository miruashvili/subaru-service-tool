# Subaru Service Tool — Architecture Audit

> Audit date: 2026-05-30  
> Codebase: `main` branch @ `8e286b0`  
> Scope: Read-only analysis. No functionality changed.

---

## 1. Full Architecture Map

### Module & Package Layout

```
:app
└── com.subaru.servicetool
    ├── SubaruApp                          HiltAndroidApp entry point
    ├── MainActivity                       Compose host; keeps screen on, handles orientation
    │
    ├── data/
    │   ├── bluetooth/
    │   │   ├── OBDBluetoothManager        BLE + SPP transport singleton (Hilt)
    │   │   ├── BluetoothConnectionState   Sealed class: Connected | Connecting | Reconnecting
    │   │   │                              | Disconnected | Error
    │   │   ├── BleUuids                   Service/char UUIDs for Vgate iCar Pro, Generic,
    │   │   │                              OBDLink CX, and SPP UUID
    │   │   └── OBDConnectionType          Enum: BLE | SPP
    │   │
    │   ├── obd/
    │   │   ├── ObdQueryEngine             Polling coordinator singleton (Hilt)
    │   │   ├── ObdCapabilityProber        SSM A8 probe + batch read singleton (Hilt)
    │   │   ├── ObdParser                  Stateless parser for all response formats
    │   │   ├── ObdPids                    PID registry object (all ~45 PIDs + tier lists)
    │   │   ├── ObdPid                     Data class: cmd, name, unit, bounds, group,
    │   │   │                              header, isTurboOnly, ssmAddress, parse lambda
    │   │   ├── AdapterSpeedProfile        Enum: FAST | MEDIUM | SLOW | MINIMAL
    │   │   ├── OilTempSource              Enum: OBD_STANDARD | SSM_ECU | SSM_ECU_ALT | NONE
    │   │   └── PidGroup                   Enum: ENGINE | TEMPERATURE | FUEL | TRANSMISSION | MISC
    │   │
    │   ├── preferences/
    │   │   └── UserPreferences            DataStore wrapper (gauge slots, vehicle, units,
    │   │                                  probe cache, adapter flags)
    │   │
    │   ├── model/
    │   │   └── VehicleSpec                Vehicle metadata: year, model, isTurbo, isCVT, market
    │   │
    │   ├── alert/
    │   │   └── AlertManager               Coolant/oil/CVT temp threshold alerts + audio beeps
    │   │
    │   ├── dtc/
    │   │   └── DtcRepository              DTC code descriptions (P/C/B/U code lookup)
    │   │
    │   ├── service/
    │   │   └── ServiceRepository          Service history events (DataStore-backed)
    │   │
    │   └── util/
    │       └── UnitConverter              °C↔°F, kPa↔bar↔psi, km/h↔mph conversions
    │
    ├── ui/
    │   ├── dashboard/
    │   │   ├── DashboardViewModel         Combines sensor values + gauge slots → DashboardUiState
    │   │   ├── DashboardScreen            Main gauge grid (portrait)
    │   │   └── SportGaugeScreen           Landscape sport gauge layout
    │   │
    │   ├── sensors/
    │   │   ├── SensorsViewModel           Builds grouped sensor list for the Sensors screen
    │   │   └── (SensorsScreen in screens/) Full sensor list with live values
    │   │
    │   ├── bluetooth/
    │   │   ├── BluetoothSettingsViewModel BT scan, connect, raw OBD log stream
    │   │   └── BluetoothSettingsScreen    Device list, connection controls, raw log toggle
    │   │
    │   ├── screens/
    │   │   ├── SensorsScreen              Live sensor list by PidGroup
    │   │   ├── SettingsScreen             Unit prefs, language, theme, landscape toggle
    │   │   ├── SettingsViewModel          Persists user preferences
    │   │   └── ServiceScreen             Service log UI
    │   │
    │   ├── main/
    │   │   └── MainViewModel              Top-level: connection state, vehicle, DTC badge
    │   │
    │   ├── onboarding/
    │   │   ├── OnboardingScreen           First-run vehicle selection flow
    │   │   └── OnboardingViewModel
    │   │
    │   ├── navigation/
    │   │   └── Navigation                 NavHost routes: Dashboard, Sensors, BT, Settings, Service
    │   │
    │   └── theme/
    │       ├── Color, Theme, Type         Material 3 dark/light theming
    │
    ├── car/                               Android Auto / Automotive OS CarAppService
    │
    └── di/
        └── AppModule                      Hilt module; provides DataStore singleton
```

### Dependency Graph (simplified)

```
MainActivity
  └─ NavHost
       ├─ DashboardViewModel ──────────┐
       ├─ SensorsViewModel ────────────┤──► ObdQueryEngine ──► OBDBluetoothManager
       ├─ BluetoothSettingsViewModel ──┤                   └──► ObdCapabilityProber
       ├─ MainViewModel ───────────────┘
       └─ SettingsViewModel ──────────────► UserPreferences
```

All heavy singletons (`OBDBluetoothManager`, `ObdQueryEngine`, `ObdCapabilityProber`, `UserPreferences`) are injected by Hilt and live for the application lifetime.

---

## 2. Bluetooth Communication Flow

### Connection Types

| Type | Transport | Adapter examples |
|------|-----------|-----------------|
| BLE  | GATT (Low Energy) | Vgate iCar Pro, OBDLink CX, Generic FFF0 adapters |
| SPP  | Classic RFCOMM | Any ELM327-compatible dongle via Bluetooth serial |

### BLE Setup Sequence

```
User picks device
  │
  ▼
connectBle(device)
  ├── BluetoothGatt.connectGatt()
  ├── onMtuChanged(512)          ← request MTU 512 first
  ├── discoverServices()
  ├── detectCharacteristics()    ← match Vgate / OBDLink / Generic UUIDs; fallback scan
  ├── enableNotifications()      ← write CCCD descriptor 0x0001
  └── initElm327()               ← async ELM init sequence (see below)
```

### ELM327 Initialization Sequence

Sent in order, each with a settle delay:

| Command | Purpose | Delay |
|---------|---------|-------|
| `ATZ`    | Reset adapter | 1000 ms |
| `ATE0`   | Echo off | 300 ms |
| `ATL0`   | Line feed off | 300 ms |
| `ATH1`   | Headers on (required by parser) | 300 ms |
| `ATSP6`  | Protocol: ISO 15765-4 CAN 11-bit 500 kbaud | 300 ms |
| `ATAT1`  | Auto adaptive timing | 300 ms |
| `ATST FF` | Max timeout | 300 ms |
| `ATSH7E0` | Default CAN header = ECM (7E0) | 300 ms |

### Command Send / Receive

```
sendCommand(cmd, timeoutMs)
  ├── acquire commandMutex (serializes all commands)
  ├── set commandActive = true
  ├── writeToAdapter(cmd + "\r")
  ├── wait for response ending with '>'
  │     - routed to cmdResponseChannel
  │     - all responses also published to rawObdLog (debug stream)
  ├── set commandActive = false
  └── return raw response string (or null on timeout)
```

Unsolicited frames (keep-alive `ATI` responses, spontaneous ECU messages) are emitted via `incomingData: SharedFlow<String>`.

### Adaptive Speed Profile

On first successful connection, three `0100` RTT probes set the initial profile:

| RTT | Profile | Cmd timeout | PID delay | Cycle delay |
|-----|---------|-------------|-----------|-------------|
| < 50 ms  | FAST    | 800 ms  | 0 ms   | 50 ms  |
| < 120 ms | MEDIUM  | 1200 ms | 20 ms  | 100 ms |
| < 250 ms | SLOW    | 2000 ms | 50 ms  | 200 ms |
| ≥ 250 ms | MINIMAL | 2500 ms | 100 ms | 500 ms |

Runtime adaptation: cycle time > 2× baseline → `downgradeProfile()`; 5 consecutive fast cycles → `upgradeProfile()`.

### Keep-Alive

Sends `ATI\r` every `profile.keepAliveIntervalMs` (1–3 s) while idle to prevent adapter sleep; skipped if a command is in-flight (`commandMutex` occupied).

### Auto-Reconnect

Up to 5 attempts on disconnect, 3 s between attempts, reconnects to `lastDeviceMac` using `lastConnectionType`.

---

## 3. OBD Communication Flow

### High-Level Pipeline

```
BT connected
  │
  ▼
ObdQueryEngine.startPolling()
  │
  ├── delay(500 ms)           ← let adapter settle
  ├── queryDtcs()             ← initial DTC count (Mode 03)
  ├── loadOrRunProbe()        ← capability probe (cached in DataStore)
  ├── buildActivePidSet()     ← compute which PIDs to poll this session
  │
  └── polling loop (while isConnected):
        tick skip counters
        classify due PIDs (by tier and protocol)
        ├── individualDue   → queryRegularPid() each (standard OBD-II)
        ├── ecuSsmDue       → batchSsm(null, ...) (SSM A8 to ECU 7E0)
        ├── tcuSsmDue       → batchSsm("7E1", ...) (SSM A8 to TCU 7E1)
        └── moduleGroups    → batchQueryModule(header, ...) (UDS Mode 22)
        
        emit updated sensorValues StateFlow
        adaptive throttle check
        delay(profile.delayBetweenCyclesMs)
        cycle++
```

### Response Parsing Dispatch

| PID type | Parser called | Condition |
|----------|--------------|-----------|
| Standard OBD-II (mode 01) | `ObdParser.parseStandard()` | `mode <= 9` and `pid.ssmAddress == null` |
| SSM single A8 | `ObdParser.parseSsmResponse()` | `pid.ssmAddress != null` (individual read) |
| UDS Mode 22 | `ObdParser.parseUdsResponse()` | `mode > 9` (22xx commands) |
| SSM A8 batch | `ObdCapabilityProber.parseSsmBatchResponse()` | batch group read |
| DTC Mode 03 | `ObdParser.parseDtcCount()` / `parseDtcCodes()` | on-demand |
| UDS DTC 19 02 | `ObdParser.parseUdsDtcResponse()` | on-demand from DTC screen |

### Error Handling

| Event | Action |
|-------|--------|
| Response null (timeout) | `consecutiveTimeouts++` |
| 5 consecutive timeouts | `btManager.reinitializeElm327()` |
| PID returns null 3× | `skipCycles[pid.cmd] = skipFor` |
| SSM PID fails 3× | `skipFor = 9999` (permanent for session) |
| Module PID fails 3× | `skipFor = MODULE_SKIP_CYCLES (20)` |
| Module batch fails 3× | `moduleSkipCycles[header] = 20` |

### IAT Fallback

If `010F` (Intake Air Temp) returns NO DATA, engine tries `0168` as a fallback command automatically. The fallback uses a different byte offset: `bytes[1] - 40` instead of `bytes[0] - 40`.

---

## 4. Subaru SSM Implementation Status

### Protocol: SSM-over-CAN (A8 service, ISO-TP framing)

Subaru ECUs expose a proprietary memory-read service (SSM) accessible over CAN using service byte `A8` (request) / `E8` (response). This is implemented without any third-party SSM library.

### Single Address Read

```
Command: 05 A8 00 <addr_hi> <addr_mid> <addr_lo>
Example: 05A8000000AF   (read 0x0000AF — engine oil temp)

Response formats (all handled by parseSsmResponse()):
  Long form:  [7E8] [len] E8 [addr_hi] [addr_mid] [addr_lo] [data_byte]
  Medium form:[7E8] [len] E8 [addr_lo] [data_byte]
  Short form: [7E8] [len] E8 [data_byte]
```

`parseSsmResponse()` disambiguates by counting tokens after the `E8` marker:
- 4+ tokens → long form (data at index +4)
- 2 tokens → medium form (data at index +2)
- 1 token → short form (data at index +1)

### Multi-Address Batch Read

```
Command: <total_len> A8 00 <addr1×3> <addr2×3> ...
Example: 0BA800 0000AF 0010B4 0010B5  (3 addresses, len = 0x0B)

Response: [header] [len] E8 [data1] [data2] [data3] ...
  One data byte per address, in request order, no address echoes.
```

`parseSsmBatchResponse()` locates the `E8` marker, then reads `N` consecutive bytes (one per requested address). If parsed count ≠ N, `batchFailed = true` → engine switches to single-read mode permanently for this adapter.

### Verified SSM Addresses (FB25 NA confirmed)

**ECU (ATSH7E0):**

| Address | Signal | Scale |
|---------|--------|-------|
| `0x000008` | Coolant Temp (SSM backup) | A − 40 (°C) |
| `0x0000AF` | Engine Oil Temp | A − 40 (°C) |
| `0x0010A1` | MAF Sensor Voltage | A × 0.02 (V) |
| `0x0010A3` | Injector 1 Pulse Width | raw |
| `0x0010A5` | Learned Ignition Timing | raw |
| `0x0010A6` | Accelerator Pedal Angle | A × 100 / 255 (%) |
| `0x0010B2` | Alternator Duty | A × 100 / 255 (%) |
| `0x0010B4` | VVT Advance Right | (A − 128) × 0.5 (°) |
| `0x0010B5` | VVT Advance Left | (A − 128) × 0.5 (°) |
| `0x001136` | Battery Temperature | A − 40 (°C) |

**TCU (ATSH7E1):**

| Address | Signal | Scale |
|---------|--------|-------|
| `0x001017` | CVT Fluid Temp | A − 40 (°C) |
| `0x001065` | AWD Transfer Duty | A × 100 / 255 (%) |
| `0x001045` | CVT Lock-Up Duty | A × 100 / 255 (%) |

**Oil Temp alternate (not in probe sweep):**
- `0x009D5C` — probed only if `0x0000AF` fails the ECU sweep

### Addresses NOT in Probe Candidates (turbo-only, intentionally excluded)

- `0x003018` — Feedback Knock Correction (turbo)
- `0x0010C9` — Wastegate Duty (turbo)
- `0x00105F` — Throttle Motor Duty (electronic throttle; listed in PIDs but not in probe list)

> **Gap:** `THROTTLE_MOTOR` (0x00105F) has `ssmAddress = 0x00105F` in `ObdPids.kt` but `0x00105F` is **not** in `CANDIDATE_ECU_ADDRESSES`. The probe will never mark it as supported, so `isEcuAllowed()` will block it unless `ecuCaps.isEmpty()` (probe failed entirely). See §6 for full analysis.

---

## 5. PID Registry Analysis

### Registry Location

`ObdPids.kt` — a Kotlin `object` (singleton). All PIDs are top-level `val` properties.

### PID Count by Protocol

| Protocol | Header | Count | Examples |
|----------|--------|-------|---------|
| OBD-II Mode 01 | (none / 7E0) | 19 | RPM, Speed, Coolant, Throttle, MAF, MAP, Fuel trims, AFR, O2, Voltage, Run Time |
| Subaru ECU Mode 22 / SSM A8 | 7E0 | 12 | Oil Temp, VVT L/R, MAF Voltage, Accel Pedal, Alternator Duty, Battery Temp, ECM Coolant, EGT, Knock Correction, Wastegate, Throttle Motor, Radiator Fan, Fuel Pump |
| Subaru TCU Mode 22 / SSM A8 | 7E1 | 7 | CVT Temp, AWD Duty, Lockup Duty, CVT Ratio Actual/Target, Pulley speeds ×2, Turbine RPM |
| Body/TPMS Mode 22 | 7D4 | 4 | TPMS FL/FR/RL/RR |
| **Total** | | **42** | |

### Tier Assignment

| Tier | PIDs | Polling frequency |
|------|------|------------------|
| TIER1 | RPM, Speed, Coolant Temp, Throttle | Every cycle (real-time) |
| TIER2 | Oil Temp, CVT Temp, Engine Load, Voltage, Intake Temp, EGT | Every `tier2Every` cycles (2–5) |
| TIER3 | MAF, MAP, Fuel Trims, Ambient Temp, Fuel Level, AFR, Baro Press, AWD Duty, Rel. Throttle, Abs Load, ECM Coolant, Knock Correction, Wastegate, VVT L/R, Lockup Duty, CVT Ratios, Pulley Speeds, Turbine RPM, Alternator Duty, MAF Voltage, Accel Pedal, O2 Voltage | Every `tier3Every` cycles (2–8) |
| TIER4 | TPMS ×4, Run Time, Battery Temp, Radiator Fan, Fuel Pump, Throttle Motor | Every `tier4Every` cycles (8–10) |

### Turbo-Only PIDs

`isTurboOnly = true` for: `KNOCK_CORRECTION`, `WASTEGATE`.  
These are filtered out entirely for NA vehicles in `buildActivePidSet()`.

### PID Routing Logic

```
if (header == null || header == "7E0") && ssmAddress == null → individualDue (standard OBD-II)
if header == "7E0" && ssmAddress != null && pid != OIL_TEMP  → ecuSsmDue (ECU A8 batch)
if header == "7E1" && ssmAddress != null                     → tcuSsmDue (TCU A8 batch)
if header != null && header != "7E0" && ssmAddress == null   → moduleGroups (UDS Mode 22 per header)
OIL_TEMP                                                      → individualDue (source-dependent path)
```

### Notable Anomalies

1. **`RADIATOR_FAN` (cmd `2210E3`)** — has no `header` field set (defaults to `null`) and no `ssmAddress`. This means it routes to `individualDue` as if it were a standard OBD command, but its command prefix `22` will cause `parsePid()` to call `parseUdsResponse()` without switching the CAN header. If the default header is not 7E0 or the ECU doesn't respond on 7E0, this will silently fail.

2. **`THROTTLE_MOTOR` (cmd `22105F`, ssmAddress `0x00105F`)** — ssmAddress is set, so it routes to `ecuSsmDue` and goes through the A8 batch path. But `0x00105F` is not in `CANDIDATE_ECU_ADDRESSES`, so the probe will never confirm it. `isEcuAllowed()` only passes it through if `ecuCaps.isEmpty()` (probe failure). On a successful probe, it is silently dropped.

3. **`FUEL_PUMP` (cmd `2210B3`)** — no `header`, no `ssmAddress`. Routes to `individualDue`; `parsePid()` will call `parseUdsResponse()`. The response parser will look for `62 10 B3` in the response, but no `ATSH` switch happens before this command. May fail silently on non-7E0 ECUs.

---

## 6. Capability Probing Analysis

### Probe Trigger

The probe runs exactly once per install (or after clearing app data). Result is cached in DataStore keys `ecuCaps`, `tcuCaps`, `probeOilTempSource`. On subsequent connections, `loadOrRunProbe()` returns the cached `ProbeResult` immediately.

Cached probe is also held in `cachedProbe` (in-memory) and cleared on `stopPolling()`, so each new BT session re-loads from DataStore (not re-probes).

### Probe Algorithm

```
probeModule(header, candidates):
  sendCommand("ATSH{header}")
  delay(300 ms)
  for each address in candidates:
    resp = sendCommand("05A800{addr}", timeout=800ms)
    if parseSsmResponse(resp) != null:
      mark address as supported
    delay(30 ms)
  return supported set
```

### Oil Temp Source Resolution (3-step priority)

```
1. sendCommand("015C")
   if parseStandard() != null → OilTempSource.OBD_STANDARD

2. if 0x0000AF in ecuCaps (from ECU probe sweep)
   → OilTempSource.SSM_ECU

3. sendCommand("05A8000x009D5C", timeout=2000ms)
   if parseSsmResponse() != null → OilTempSource.SSM_ECU_ALT

4. → OilTempSource.NONE (oil temp not available on this vehicle)
```

### Probe Result Usage

| ProbeResult field | Usage in polling |
|-------------------|-----------------|
| `oilTempSource` | Decides oil temp read path in `queryRegularPid()` |
| `ecuCaps` | `isEcuAllowed(address, probe)` gates ecuSsmDue PIDs |
| `tcuCaps` | `isTcuAllowed(address, probe)` gates tcuSsmDue PIDs |
| `tcuCaps.isNotEmpty()` | Adds CVT_TEMP + AWD_DUTY to active set unconditionally |

### Gate Behavior: `isEcuAllowed()`

```kotlin
fun isEcuAllowed(address: Int, probe: ProbeResult): Boolean =
    probe.ecuCaps.isEmpty() || address in probe.ecuCaps
```

**Critical:** If the ECU probe returns an empty set (ATSH7E0 timed out, adapter not connected, or all addresses returned NO DATA), `ecuCaps.isEmpty() == true` and **all** SSM addresses are allowed through without filtering. This is a safe-fail open design — better to try than silently drop.

### Gap: `THROTTLE_MOTOR` Never Probed

`ssmAddress = 0x00105F` is not in `CANDIDATE_ECU_ADDRESSES`. The probe never tests it. After a successful probe (ecuCaps non-empty), `isEcuAllowed(0x00105F, probe)` returns `false`. This PID will never emit a value on vehicles with a working ECU probe.

**Fix needed:** Add `0x00105F` to `CANDIDATE_ECU_ADDRESSES` in `ObdCapabilityProber.kt`.

---

## 7. Polling Architecture Analysis

### Concurrency Model

```
Application scope (SupervisorJob + IO Dispatcher)
  ├── connectionState collector (permanent)
  ├── carActivePids collector (permanent)  
  └── pollJob (child, cancelled + relaunched on each connect)
```

The polling coroutine runs on `Dispatchers.IO`. All BT commands are `suspend` calls. The `pollJob` is a child of the supervised scope — an unhandled exception cancels only the poll job, not the manager or connection.

### Tier-Based Scheduling

```
cycle % 1       == 0  → TIER1 always fires
cycle % tier2Every == 0  → TIER2 fires (FAST=2, MEDIUM=3, SLOW=3, MINIMAL=5)
cycle % tier3Every == 0  → TIER3 fires (FAST=2, MEDIUM=3, SLOW=5, MINIMAL=8)
cycle % tier4Every == 0  → TIER4 fires (FAST=8, MEDIUM=8, SLOW=8, MINIMAL=10)
```

At cycle 0, all four tiers fire simultaneously (initial data fill). At cycle 1 with FAST profile, only TIER1 fires. At cycle 2, TIER1+TIER2+TIER3 fire.

### Per-Cycle Execution Order

1. Tick down per-PID skip counters
2. Tick down per-module skip counters
3. Query `individualDue` PIDs one by one (standard OBD-II; oil temp via source-specific path)
4. Query `ecuSsmDue` as single A8 batch (or singles) against 7E0
5. Query `tcuSsmDue` as single A8 batch (or singles) against 7E1, restore 7E0
6. Query each `moduleGroups` header batch (UDS Mode 22), restore 7E0
7. Emit `sensorValues` StateFlow (updated after each successful PID)
8. Adaptive profile check
9. `delay(profile.delayBetweenCyclesMs)`

### State Emission

`_sensorValues` is updated immediately inside `trackResult()` after each successful PID parse. This means the UI sees incremental updates during a cycle, not a batch update at cycle end. This is correct behavior for low-latency display.

### Known Polling Gaps

1. **Single-read mode fallback is sticky per session, not per-connection.** If `singleReadMode` is flipped to `true` during a session and the adapter is then replaced (user pairs a new adapter), the flag remains `true` until the app restarts. The DataStore value persists across sessions, so a new adapter session will also start in single-read mode unless the user clears the flag.

2. **Probe result is cleared on `stopPolling()` (`cachedProbe = null`)** but DataStore is not cleared. On the next connection, `loadOrRunProbe()` finds the DataStore hit and reuses the old probe — it does not re-probe. This means switching to a different vehicle (different ECU) requires clearing app data or a manual "re-probe" trigger, which does not currently exist in the UI.

3. **DTC refresh is fire-and-forget** (`requestDtcRefresh()` launches a new coroutine in the scope). If called rapidly, multiple DTC queries can be in-flight simultaneously. There is no debounce or job deduplication.

4. **TCU SSM batch failure handling gap**: The TCU batch failure calls `registerModuleFailure("7E1", ...)` which increments the module failure counter and sets `moduleSkipCycles["7E1"] = 20` after 3 failures. However, TCU SSM PIDs are checked against `moduleSkipCycles["7E1"]` only at the top of the cycle (the per-module skip block). The TCU SSM batch check also reads `moduleSkipCycles["7E1"]` inline at line 197. This is consistent but worth noting — TCU SSM and TCU UDS Mode 22 share the same `"7E1"` key in `moduleSkipCycles`, so a TCU SSM failure can suppress TCU UDS Mode 22 PIDs (CVT ratio, pulley speeds, turbine RPM) and vice versa.

---

## 8. UI Sensor Pipeline Analysis

### Data Flow

```
OBDBluetoothManager
  │  sendCommand() → raw string response
  ▼
ObdQueryEngine
  │  parsePid() → Float value
  │  trackResult() → sensorValues: StateFlow<Map<String, Float>>
  │                  (key = pid.cmd, e.g. "010C")
  ▼
ViewModel (DashboardViewModel / SensorsViewModel)
  │  combine(sensorValues, userPreferences, ...) → UiState
  ▼
Composable
  │  collect(uiState) → recompose
  ▼
User sees value
```

### DashboardViewModel

Combines seven flows into `DashboardUiState`:
- `selectedVehicle` (UserPreferences)
- `connectionState` (OBDBluetoothManager)
- `sensorValues` (ObdQueryEngine)
- `dtcCount` (ObdQueryEngine)
- `gaugeSlots`, `wideGaugeSlots` (UserPreferences — portrait)
- `lsTopSlots`, `lsMidSlots`, `lsBotSlots`, `lsBotWideSlots`, `landscapeBottomSlots` (landscape)

The `combine` resolves which PID cmd each configured slot refers to, reads the current value from `sensorValues[pid.cmd]`, applies unit conversion, and packages it into `SlotMetric(label, value, unit, minVal, maxVal)`.

### SensorsViewModel

Reads all PIDs from `ObdPids.ALL`, groups by `PidGroup`, looks up live value in `sensorValues`, applies unit conversion, emits `List<SensorGroup>`. No filtering by capability or vehicle type at this layer — all 42 PIDs are shown in the Sensors screen regardless of whether they polled successfully. Sensors without a live value show `"—"`.

### Unit Conversion Points

Conversions happen in the ViewModel layer, not in the parser or engine:

| Unit type | DataStore key | Conversions |
|-----------|--------------|------------|
| Temperature | `temperatureUnit` | `"fahrenheit"` → `(°C × 9/5) + 32` |
| Pressure | `pressureUnit` | `"bar"` → `kPa / 100`; `"psi"` → `kPa / 6.895` |
| Speed | `speedUnit` | `"mph"` → `km/h × 0.621371` |
| Fuel economy | (derived) | L/100km ↔ MPG |

### Alert Pipeline

`AlertManager` subscribes to `sensorValues` independently. When `COOLANT_TEMP`, `OIL_TEMP`, or `CVT_TEMP` cross user-configured thresholds, it fires an audio alert (beep via AudioTrack) and updates `AlertManager.activeAlerts: StateFlow`. The dashboard UI observes `activeAlerts` to show warning overlays.

---

## 9. Sensor Filtering, Disabling & Gating — Complete Map

This section enumerates every code path that suppresses a sensor from appearing in the poll cycle or in the UI.

### 9.1 Build-Time Exclusion (buildActivePidSet)

| Condition | Effect |
|-----------|--------|
| `probe.oilTempSource == OilTempSource.NONE` | `OIL_TEMP` not added to active set; never polled |
| `probe.tcuCaps.isEmpty()` | `CVT_TEMP` and `AWD_DUTY` not added to active set |
| `tcuAvailable == false` | All `header == "7E1"` candidates filtered from TIER2/3/4 |
| `pid.isTurboOnly && !vehicle.isTurbo` | `KNOCK_CORRECTION`, `WASTEGATE` excluded for NA |
| `pid.cmd not in allCmds` (user gauge slots) | TIER2/3/4 PIDs not in any gauge slot not polled |
| `"TPMS_ALL" not in allCmds` | TIER4 TPMS PIDs not added |

> TIER1 (RPM, Speed, Coolant, Throttle) + AMBIENT_TEMP + MAF + INTAKE_TEMP are **always** added regardless of gauge configuration or probe results.

### 9.2 Capability Probe Gating (isEcuAllowed / isTcuAllowed)

| Function | Returns true when | Returns false when |
|----------|------------------|--------------------|
| `isEcuAllowed(addr, probe)` | `ecuCaps.isEmpty()` OR `addr in ecuCaps` | `ecuCaps` non-empty AND `addr` not in it |
| `isTcuAllowed(addr, probe)` | `tcuCaps.isNotEmpty()` AND `addr in tcuCaps` | `tcuCaps.isEmpty()` OR `addr` not in it |

**Implication:** A successful ECU probe acts as an allowlist. Any SSM address not in `CANDIDATE_ECU_ADDRESSES` (and therefore not probed) will be blocked by `isEcuAllowed()`. Currently blocked: `0x00105F` (Throttle Motor).

### 9.3 Runtime Per-PID Skip (trackResult + skipCycles)

| Trigger | Skip duration |
|---------|--------------|
| SSM A8 PID fails 3× consecutively | `9999` cycles (effectively permanent for session) |
| Module PID (non-7E0 header) fails 3× | `20` cycles |
| Standard OBD-II PID fails 3× | `10` cycles |

Once skipped, the PID is absent from `due` list; its last emitted value remains in `sensorValues` until `stopPolling()` clears the map. The UI will continue showing the stale last value until disconnect.

### 9.4 Runtime Per-Module Skip (registerModuleFailure + moduleSkipCycles)

| Trigger | Effect | Duration |
|---------|--------|---------|
| Module batch fails 3× | Entire module header skipped | `20` cycles |
| TCU SSM batch fails 3× | Header `"7E1"` skipped (affects both TCU SSM and TCU UDS) | `20` cycles |
| ATSH command timeout | Single-cycle skip; returns `false` | 1 cycle |

### 9.5 Consecutive Timeout Handling

| Event | Action |
|-------|--------|
| 5 consecutive individual-PID timeouts | `reinitializeElm327()` — full AT init sequence |
| 5 consecutive module-batch timeouts | `reinitializeElm327()` |
| ELM reinit during poll | Polling continues; next cycle picks up normally |

### 9.6 User-Level Disable (gauge slot configuration)

Only PIDs explicitly placed in a gauge slot (portrait or landscape) are polled beyond TIER1 + always-on sensors. A user who has no TIER2/3/4 PIDs configured in any slot effectively polls only TIER1 + AMBIENT_TEMP + MAF + INTAKE_TEMP.

### 9.7 Adapter Single-Read Mode

When `singleReadMode == true` (flipped on first batch-parse failure, persisted in DataStore), all SSM batch reads fall back to sequential single-address reads. This does not disable any sensor; it increases latency.

### 9.8 Disconnect

`stopPolling()` cancels `pollJob` and sets `sensorValues = emptyMap()`, clearing all displayed values. The DTC count is also reset to 0.

---

## 10. Open Issues & Recommendations

| # | Severity | Location | Issue | Recommendation |
|---|----------|----------|-------|---------------|
| 1 | Medium | `ObdCapabilityProber.kt:33` | `THROTTLE_MOTOR` address `0x00105F` not in `CANDIDATE_ECU_ADDRESSES` — silently blocked by `isEcuAllowed()` after a successful probe | Add `0x00105F` to `CANDIDATE_ECU_ADDRESSES` |
| 2 | Medium | `ObdPids.kt:131` | `RADIATOR_FAN` has no `header` (null) and no `ssmAddress`, but uses Mode 22 cmd (`2210E3`) — routes to individual query without header switch; likely always returns NO DATA | Add `header = "7E0"` to RADIATOR_FAN |
| 3 | Medium | `ObdPids.kt:183` | `FUEL_PUMP` has no `header`, no `ssmAddress`, Mode 22 cmd (`2210B3`) — same routing issue as RADIATOR_FAN | Add `header = "7E0"` to FUEL_PUMP |
| 4 | Low | `ObdQueryEngine.kt:59` | `singleReadMode` is a session-level flag initialized once from DataStore; if user swaps adapter, the old adapter's flag persists until app restart | Add UI option to reset adapter flags; or re-load from DataStore on each `startPolling()` |
| 5 | Low | `ObdQueryEngine.kt:263` | `cachedProbe = null` on stop, but DataStore probe cache persists indefinitely — no way to force re-probe after vehicle swap without clearing app data | Add "Reset ECU probe" action in Settings |
| 6 | Low | `ObdQueryEngine.kt:556` | `requestDtcRefresh()` launches a new coroutine without deduplication — rapid calls can overlap | Store DTC job reference; cancel previous before launching |
| 7 | Low | `ObdQueryEngine.kt:197` | TCU SSM and TCU UDS Mode 22 share the `"7E1"` key in `moduleSkipCycles`; a TCU SSM batch failure suppresses UDS Mode 22 TCU PIDs (CVT ratio, pulley speeds) | Separate tracking keys or document intentional shared suppression |
| 8 | Info | `ObdParser.kt:64` | `parseSsmResponse()` medium form (2 tokens after E8) assumes `tokens[e8idx+2]` is data, but some adapters may echo all 3 address bytes in a different order — could read wrong byte | Verify against additional adapter models |
| 9 | Info | `ObdCapabilityProber.kt:47` | TCU candidates are minimal (3 addresses) and do not include CVT Ratio (`0x2140`) or Pulley Speed addresses — probe cannot discover those | Document that CVT Ratio/Pulley PIDs are always-on (no probe gate) if TCU present |

---

## 11. File Index

| File | Package | Role |
|------|---------|------|
| `data/bluetooth/OBDBluetoothManager.kt` | bluetooth | BLE/SPP transport, ELM init, keep-alive, adaptive speed |
| `data/bluetooth/BluetoothConnectionState.kt` | bluetooth | Connection state sealed class |
| `data/bluetooth/BleUuids.kt` | bluetooth | BLE service/characteristic UUIDs |
| `data/bluetooth/OBDConnectionType.kt` | bluetooth | BLE vs SPP enum |
| `data/obd/ObdQueryEngine.kt` | obd | Polling coordinator, tier scheduler, failure tracker |
| `data/obd/ObdCapabilityProber.kt` | obd | SSM A8 probe + batch read |
| `data/obd/ObdParser.kt` | obd | Response parser (OBD-II, UDS, SSM, DTC) |
| `data/obd/ObdPids.kt` | obd | PID registry + tier lists |
| `data/obd/ObdPid.kt` | obd | PID data class |
| `data/obd/AdapterSpeedProfile.kt` | obd | Speed profile enum (FAST/MEDIUM/SLOW/MINIMAL) |
| `data/obd/OilTempSource.kt` | obd | Oil temp source enum |
| `data/obd/PidGroup.kt` | obd | PID display group enum |
| `data/preferences/UserPreferences.kt` | preferences | DataStore wrapper (all persistent state) |
| `data/model/VehicleSpec.kt` | model | Vehicle metadata |
| `data/alert/AlertManager.kt` | alert | Threshold alerts |
| `data/dtc/DtcRepository.kt` | dtc | DTC code descriptions |
| `data/util/UnitConverter.kt` | util | Unit conversions |
| `ui/dashboard/DashboardViewModel.kt` | dashboard | Dashboard UiState combiner |
| `ui/dashboard/DashboardScreen.kt` | dashboard | Portrait gauge grid Composable |
| `ui/dashboard/SportGaugeScreen.kt` | dashboard | Landscape gauge Composable |
| `ui/sensors/SensorsViewModel.kt` | sensors | Full sensor list state |
| `ui/screens/SensorsScreen.kt` | screens | Sensors list Composable |
| `ui/bluetooth/BluetoothSettingsViewModel.kt` | bluetooth | BT scan + raw log |
| `ui/bluetooth/BluetoothSettingsScreen.kt` | bluetooth | BT UI Composable |
| `ui/screens/SettingsScreen.kt` | screens | Settings Composable |
| `ui/screens/SettingsViewModel.kt` | screens | Settings persistence |
| `ui/main/MainViewModel.kt` | main | Top-level connection + DTC badge |
| `ui/onboarding/OnboardingScreen.kt` | onboarding | First-run vehicle selection |
| `ui/navigation/Navigation.kt` | navigation | NavHost route definitions |
| `di/AppModule.kt` | di | Hilt DataStore provider |
| `SubaruApp.kt` | root | HiltAndroidApp |
| `MainActivity.kt` | root | Compose host |
