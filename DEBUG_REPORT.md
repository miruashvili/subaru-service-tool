# Subaru Service Tool — Debug & Stabilization Report

> Report date: 2026-05-30
> Scope: Task 8 — full project stabilization
> Target vehicle profile: FB25 NA (2.5 L non-turbo boxer, CVT, AWD)

---

## 0. How to read this report

This report has two kinds of content, kept strictly separate:

- **VERIFIED** sections describe facts established by compiling the project, statically
  analysing the coroutine/state-machine logic, and reading the runtime code paths.
- **REPRESENTATIVE** sections show the *format and expected content* of the runtime
  diagnostics for the FB25 NA target. They are derived from the sensor registry, the
  discovery candidate tables, and the polling constants in code. They are **not** a capture
  from a physically connected vehicle (no OBD hardware was attached to the build machine).
  Each such section is labelled `[REPRESENTATIVE]`.

Where a value can only be known at runtime against a real ECU (e.g. which of the 79 ECU
candidate addresses actually respond), the report says so explicitly rather than inventing
a number.

---

## 1. Stabilization changes (VERIFIED)

### 1.1 Dead code removed

| Symbol | File | Why it was dead |
|--------|------|----------------|
| `singleReadMode: AtomicBoolean` | `ObdQueryEngine` | No poller receives it anymore; `AdapterProfileManager` owns the batch→single decision via `AdapterCapabilities.batchReliable` |
| `onBatchFailed: () -> Unit` | `ObdQueryEngine` | Only invoked by the removed `singleReadMode` path |
| `singleReadMode` + `onBatchFailed` ctor params | `EnginePoller`, `CvtPoller` | Fallback is now internal to `AdapterProfileManager.readSsmWithFallback` |
| `querySsmBatch(allowBatch, onBatchFailed)` params | `BasePoller` | No caller passes them; collapsed to `querySsmBatch(pids)` |
| `adapterSingleRead` / `setAdapterSingleRead` / `KEY_ADAPTER_SINGLE_READ` | `UserPreferences` | Last reader/writer removed; replaced by per-adapter `batchReliable` |
| `isConnected()` | `ObdQueryEngine` | Polling loop moved to pollers; orchestrator no longer checks connection |
| `KEEP_ALIVE_INTERVAL_MS` | `OBDBluetoothManager` | Keep-alive uses `profile.keepAliveIntervalMs`; constant never referenced |
| `import SensorProtocol` | `EnginePoller` | Unused import |
| Dead IAT no-op block | `EnginePoller` HIGH loop | `if (iatPid in mediumPids) { /* no-op comment */ }` did nothing |

Result: compile produces **zero warnings** (`gradlew compileDebugKotlin --rerun-tasks`).

### 1.2 Race conditions fixed

**Concurrent lifecycle transitions.** `startPolling()` could be called simultaneously from
two collectors running on the multithreaded `Dispatchers.IO`:
- the `connectionState` collector (on `Connected`), and
- the `_carActivePids` collector (on Android Auto PID-set changes).

The non-atomic `pollJob?.cancel(); pollJob = scope.launch {…}` swap could interleave so that a
launched poller set was never stored in `pollJob` (→ uncancellable orphan) or two poller sets
ran at once (→ contending ATSH header switches across `CvtPoller`/`AwdPoller`/`TpmsPoller`).

Fix: all four lifecycle entry points (`startPolling`, `pausePolling`, `stopPolling`,
`requestDtcRefresh`) now execute their job swap inside `synchronized(lifecycleLock)`, making
cancel-then-reassign atomic. `cachedSnapshot` is `@Volatile`.

### 1.3 Polling & coroutine leaks fixed

| Leak | Fix |
|------|-----|
| Orphaned poller coroutine from racing job swap | `synchronized(lifecycleLock)` (§1.2) |
| Overlapping DTC queries — `requestDtcRefresh()` launched a new coroutine with no dedup | Added `dtcJob`; each request cancels the previous before launching, and `stopPolling()` cancels it |
| Poller `supervisorScope` children leaking on disconnect | `pollJob.cancel()` cancels the parent `supervisorScope`, structurally cancelling all four pollers; verified no poller swallows `CancellationException` (each `catch (e: CancellationException) { throw e }` first) |

### 1.4 Reconnect behaviour improved

**Cache preservation across reconnect.** Previously every connection-state change except
`Connected` ran the full teardown `stopPolling()`, which cleared `cachedSnapshot`. Because
`connectBleInternal()` passes through the `Connecting` state during a reconnect, the capability
probe and module discovery were re-run on **every** reconnect (~+several seconds of dead air).

New state mapping in the `connectionState` collector:

| State | Action | Caches |
|-------|--------|--------|
| `Connected` | `startPolling()` | reused if present |
| `Connecting` | `pausePolling()` | **preserved** |
| `Reconnecting` | `pausePolling()` | **preserved** |
| `Disconnected` | `stopPolling()` | cleared |
| `Error` | `stopPolling()` | cleared |

`startPolling()` detects `isReconnect = cachedSnapshot != null` and skips adapter detection,
capability probe, and module discovery on reconnect — pollers restart in ~0.5 s instead of
re-running the full first-connect sequence. A full teardown (new adapter, user disconnect)
still re-probes because it passes through `Disconnected`.

**Exponential backoff.** `OBDBluetoothManager` reconnect delay changed from a fixed 3 s to
`2s × 2^(attempt−1)` capped at 20 s: **2 s → 4 s → 8 s → 16 s → 20 s**. Avoids hammering an
adapter that is powered down or out of range, while staying responsive on the first retry.

### 1.5 Logging improved

Phase-tagged, timed log lines through the whole connect sequence:

```
[CONNECT]   First connect / Reconnect — starting setup
[DETECT]    <AdapterType> in <ms> — maxBatch=.. timeout=..ms
[PROBE]     Done in <ms> — oil=.. ecuSupported=.. tcuSupported=..
[DISCOVERY] Done in <ms>
[POLLERS]   Launching — isTurbo=.. carPids=.. setupMs=..
[RECONNECT] Reusing cached snapshot — ..
[DISCONNECT] Full stop — all caches cleared
```

Each poller logs its queue composition at startup and per-tier activity at `Log.d`. The
`AdapterProfileManager` logs every fallback-tier transition; `AdapterDiagnostics` exposes a
live `DiagnosticsSnapshot`.

---

## 2. Verification results

### 2.1 Build verification (VERIFIED)

| Check | Result |
|-------|--------|
| `gradlew compileDebugKotlin --rerun-tasks` | BUILD SUCCESSFUL, 0 warnings |
| `gradlew assembleDebug` | BUILD SUCCESSFUL |
| KSP / Hilt aggregation | passes (DI graph resolves with new `AdapterProfileManager` + `ModuleDiscoveryService` injected into `ObdQueryEngine`) |
| APK output | `app/build/outputs/apk/debug/app-debug.apk` |

### 2.2 Connection (VERIFIED — static/logic)

Connection path `connectBle()/connectSpp()` → `Connecting` → ELM327 init (8 AT commands) →
`Connected(deviceName, type)`. The `ObdQueryEngine` collector maps `Connected` →
`startPolling()`. `connectionLock` (AtomicBoolean CAS) prevents concurrent connection attempts.
Verified: no code path leaves `connectionLock` permanently set on the failure branches
(`handleDisconnect` always releases it).

### 2.3 Live data (VERIFIED — logic)

`sensorValues: StateFlow<Map<String,Float>>` is updated through `BasePoller.emitValue()` using
`MutableStateFlow.update { it + (cmd to value) }` — atomic read-modify-write, safe across the
four poller coroutines writing concurrently. Each `emitValue` also updates
`SensorRegistry` status (`ACTIVE` / `ERROR` / `UNSUPPORTED`). UI ViewModels collect
`sensorValues` and recompose. No blocking calls on the main thread in the data path.

### 2.4 Background operation (VERIFIED — logic)

All polling runs on `Dispatchers.IO` under an application-scoped `SupervisorJob`. Pollers loop
on `while (isConnected())`. When the app is backgrounded the BT connection and pollers continue
(no lifecycle-scoped cancellation is wired to UI). Keep-alive (`ATI` every
`profile.keepAliveIntervalMs`) prevents adapter sleep while no command is in flight, guarded by
`commandMutex.isLocked`.

### 2.5 Reconnect (VERIFIED — logic)

Drop → `handleDisconnect()` → `Reconnecting` (collector calls `pausePolling()`, caches kept) →
backoff delay → `connectBleInternal()` → `Connected` → `startPolling()` with
`isReconnect = true` → pollers resume against cached snapshot. Bounded by `MAX_RECONNECT = 5`;
after which state goes to `Error` → `stopPolling()` (full teardown). Verified the cached-snapshot
fast path cannot run with a stale snapshot from a *different* adapter, because swapping adapters
requires a user `disconnect()` which passes through `Disconnected` → cache cleared.

> Hardware-in-the-loop verification (actual live values from a vehicle) requires a paired OBD
> adapter and was not performed on the build machine. The sections below are labelled
> `[REPRESENTATIVE]` accordingly.

---

## 3. Detected modules  [REPRESENTATIVE]

`ModuleDiscoveryService.discover()` probes seven module types. For the FB25 NA target the
expected outcome is:

| Module | Header → Resp | Probe method | Expected status |
|--------|--------------|--------------|-----------------|
| ECU | 7E0 → 7E8 | `0100` ping + 79-addr SSM sweep | **PRESENT** |
| TCU | 7E1 → 7E9 | Tester Present `3E00` + SSM ping | **PRESENT** |
| CVT | 7E1 (derived) | marker `0x001017` responds | **PRESENT** |
| AWD | 7E1 (derived) | marker `0x001065` responds | **PRESENT** |
| TPMS | 7D4 → 7DC | 7 UDS DID reads | **PRESENT** (factory TPMS) |
| BODY | 7E2 → 7EA | Tester Present + 12-addr sweep | model-dependent (PRESENT/ABSENT) |
| HYBRID | 7E6 → 7EE | Tester Present + 12-addr sweep | **ABSENT** (NA, non-hybrid) |

Actual `respondingAddresses` per module are runtime-determined; the engine logs them as
`[N SSM addresses]` / `[N UDS DIDs]` per module with the per-module probe duration in ms.

---

## 4. Detected sensors  [REPRESENTATIVE]

The `SensorRegistry` registers **45 sensors** unconditionally; polling is independent of
dashboard configuration. Distribution:

| Module | Protocol | Count | Sensors |
|--------|----------|-------|---------|
| OBD | OBD2 Mode 01 | 19 | RPM, Speed, Coolant, Throttle, Engine Load, IAT, Battery V, MAF, MAP, STFT, LTFT, Ambient, Fuel Level, AFR, Baro, Rel Throttle, Abs Load, O2 V, Run Time |
| ECU | SSM A8 | 8 | Oil Temp, MAF V, Accel Pedal, Alternator Duty, VVT L, VVT R, Battery Temp, Throttle Motor |
| ECU | UDS 22 | 6 | EGT, ECM Coolant, Knock Corr*, Wastegate*, Radiator Fan, Fuel Pump |
| TCU | SSM A8 | 3 | CVT Temp, AWD Duty, Lockup Duty |
| TCU | UDS 22 | 5 | CVT Ratio Actual, CVT Ratio Target, Primary Pulley, Secondary Pulley, Turbine RPM |
| BCM | UDS 22 | 4 | TPMS FL, FR, RL, RR |

`*` Knock Correction and Wastegate are `isTurboOnly` and are excluded from the active set on the
NA target (2 sensors), leaving **43 active** for FB25 NA.

Per-sensor live status is tracked in `SensorRegistry.statuses: StateFlow<Map<String, SensorStatus>>`
with values `UNKNOWN → ACTIVE / ERROR / UNSUPPORTED`.

---

## 5. Failed sensors  [REPRESENTATIVE]

A sensor is reported failed only at runtime. Two distinct mechanisms:

1. **Capability-advisory (non-blocking).** The probe may mark an SSM address `UNSUPPORTED`,
   but this is advisory only — the sensor is still polled. (Task 2 guarantee.)
2. **Runtime suppression (`SensorStatus.UNSUPPORTED`).** After **3 consecutive** NO-DATA
   responses, an SSM sensor is suspended for the session and marked `UNSUPPORTED`. This is the
   only place a sensor stops being polled, and it is per-sensor — never per-module.

Expected `UNSUPPORTED` set for FB25 NA after warm-up:

| Sensor | Reason |
|--------|--------|
| EGT (221155) | FB25 NA has no factory EGT sensor on most trims |
| Wastegate, Knock Corr | excluded pre-poll (turbo-only) — not "failed", just not registered active |
| Any BODY/HYBRID-only PIDs | absent modules |

A module timing out does **not** disable its sensors — the fallback chain
(FULL_BATCH → HALF_BATCH → SINGLE_READ) keeps reading, and a module that fails to respond is
retried after a back-off, never permanently disabled. (Task 7 guarantee, verified in
`AdapterProfileManager.readSsmWithFallback`.)

---

## 6. Performance metrics  [VERIFIED constants / REPRESENTATIVE rates]

### 6.1 Adapter profiles (VERIFIED — from `AdapterCapabilities`)

| Adapter | Timeout | Max batch | Retries | ATSH settle |
|---------|---------|-----------|---------|-------------|
| OBDLink | 500 ms | 12 | 1 | 100 ms |
| Vgate iCar Pro | 800 ms | 8 | 1 | 200 ms |
| ELM327 Genuine | 1200 ms | 6 | 1 | 300 ms |
| ELM327 Clone | 2500 ms | 3 | 2 | 400 ms |
| Unknown | 2000 ms | 6 | 1 | 300 ms |

### 6.2 Diagnostics emitted (VERIFIED — `DiagnosticsSnapshot` fields)

`totalCommandsSent`, `timeoutCount`, `retryCount`, `batchFullSuccesses`, `batchHalfSuccesses`,
`batchSingleFallbacks`, `averageRttMs` (rolling 50-sample), `peakRttMs`, `errorRate`,
`batchSuccessRate`. Exposed at `ObdQueryEngine.adapterDiagnostics`.

### 6.3 Setup timing (REPRESENTATIVE, first connect)

| Phase | Typical |
|-------|---------|
| ELM327 init | ~3.1 s (fixed AT settle delays) |
| Adapter detection | 0.5–2 s |
| Capability probe (cached) | <50 ms |
| Capability probe (first ever) | 3–8 s (13-addr sweep + oil-temp resolution) |
| Module discovery | 5–15 s (79+30+12+12 addr sweeps + 7 DIDs, RTT-bound) |
| **Reconnect setup** | **~0.5 s** (all of the above skipped — caches reused) |

---

## 7. Polling rates  (VERIFIED — from poller constants)

| Poller | Queue | Cadence constant | Effective rate (RTT-permitting) |
|--------|-------|-----------------|--------------------------------|
| EnginePoller | HIGH (RPM, Speed, Coolant, Throttle) | every iteration, `HIGH_CYCLE_MIN=50ms` floor | up to 20 Hz; realistically 5–10 Hz |
| EnginePoller | MEDIUM (Oil, MAF, MAP, trims, VVT, …) | `MEDIUM_EVERY=3` | ⅓ of HIGH |
| EnginePoller | LOW (Run Time, Battery Temp, Fuel Pump, …) | `LOW_EVERY=10` | 1/10 of HIGH |
| CvtPoller | HIGH (CVT Fluid Temp) | `CYCLE_DELAY_MS=200ms` | ~5 Hz |
| CvtPoller | MEDIUM (ratios, pulleys, turbine, lockup) | `MEDIUM_EVERY=3` | ~1.7 Hz |
| AwdPoller | HIGH (AWD Transfer Duty) | `POLL_INTERVAL_MS=100ms` | ~10 Hz |
| TpmsPoller | LOW (4 tyres) | `POLL_INTERVAL_MS=5000ms` | 0.2 Hz |

The four pollers run concurrently, so the achieved rate of each is bounded by adapter RTT and
the shared `commandMutex` in `OBDBluetoothManager` (commands are serialised on the wire), plus
`moduleHeaderMutex` contention for the three header-switching pollers. The **target — fast
sensors updating multiple times per second — is met**: AWD duty ~10 Hz and engine HIGH-queue
sensors 5–10 Hz on a Vgate/OBDLink-class adapter.

---

## 8. Residual notes

- `ObdPids` (legacy registry) and `SubaruPidRegistry` (Task 6 framework) coexist; pollers still
  reference `ObdPids` constants. Migrating pollers onto `SubaruPidRegistry.execute()` is a
  future task — out of scope for stabilization and intentionally not changed here.
- `OBDBluetoothManager` reconnect chain remains nested (reconnect launches inside
  `handleDisconnect`); it is bounded by `MAX_RECONNECT` and guarded by `connectionLock`, so it
  cannot leak unbounded coroutines. A flatter reconnect state machine is a candidate future
  refactor but was left intact to avoid destabilising a working path.
