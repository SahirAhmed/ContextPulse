# Implementation Plan: ContextPulse Persistent Background Execution & Context Monitoring

## 1. Overview & Architectural Goals
ContextPulse must operate as an autonomous, persistent Android background service that continuously monitors configured triggers (Bluetooth Low Energy beacons, schedules, QR/NFC, manual triggers), evaluates active contexts and modes, and applies automations without depending on `MainActivity` or any Compose UI staying in memory or open in recents.

The existing architectural pipeline:
```
TRIGGER  ──▶  CONTEXT ENGINE  ──▶  MODE ENGINE  ──▶  AUTOMATION ENGINE
(BLE, Time,    (Active Context,     (resolveActiveMode,    (TransitionDiffEngine,
 QR, NFC,       Priority,            Effective Schedule,    Hardware, Restrictions,
 Manual)        Sleep/Snooze)        Preview Override)      Budgets, Auto-launch)
```
will be strictly preserved. The UI (`MainActivity`, Compose screens) becomes a pure client observing state from the underlying background engine and repositories.

---

## 2. Core Architectural Changes

### A. Decouple Automation & Trigger Execution from UI (`MainActivity`)
- **Current State**: `MainActivity.kt` lines 292–365 runs a Compose `LaunchedEffect(activeRoom, isSnoozed)` that applies brightness, DND, eye comfort, auto-launch, and starts `AppRestrictionMonitor`. When the user closes the app or swipes it away from recents, this `LaunchedEffect` terminates, breaking automations.
- **Solution**:
  - Extract this entire orchestration logic out of `MainActivity.kt` into `ContextPulseEngineService` (or enhanced `Phase0BleService`).
  - The service becomes the single authoritative runtime coordinator: it collects active room and snooze state from `ContextEngine` and `RoomProfileRepository`, evaluates mode schedule transitions, and calls `ProfileActivationHelper` and `TransitionDiffEngine` directly in the service process.
  - `MainActivity` becomes a lightweight client that binds/observes StateFlows and submits user actions.

### B. Persistent Foreground Service (`ContextPulseEngineService`)
- **Service Configuration**:
  - Foreground Service with appropriate types declared in `AndroidManifest.xml`: `connectedDevice` (for continuous BLE beacon scanning) and `dataSync` (for state synchronization, schedule evaluation, and automation dispatch).
  - Uses `START_STICKY` in `onStartCommand` to ensure automatic Android runtime recreation if killed under extreme memory pressure.
  - Listens to system broadcasts: `ACTION_BOOT_COMPLETED`, `ACTION_MY_PACKAGE_REPLACED`, and `BluetoothAdapter.ACTION_STATE_CHANGED` to automatically resume monitoring if enabled.

### C. Background Mode Schedule Evaluator (Time-based Triggers)
- **Problem**: Contexts can have modes with scheduled active hours (e.g., Work Mode 09:00–17:00 vs. Default Mode). When the app is in the background, a mode change must trigger at the exact scheduled boundary.
- **Solution**:
  - Implement a coroutine ticker (every 30 seconds) coupled with `AlarmManager` wakeups at exact mode boundaries within the active context.
  - When the clock passes a schedule threshold:
    1. Checks if `ActiveConfigSnapshotManager.isPreviewActive()` is active; if true, preview mode is preserved and background schedule evaluation does not overwrite it until preview completes.
    2. Calls `activeRoom.profile.resolveActiveMode()` to determine if the active mode has changed.
    3. If changed, executes `ProfileActivationHelper.reapplyCurrentActive(context)` and updates the persistent notification.

### D. Ongoing Persistent Notification
- **Notification Requirements**:
  - Channel: `contextpulse_service_channel` (Importance Low/Default to avoid sound buzzing on every update, ongoing, non-dismissible).
  - Dynamic Real-Time Status:
    - **Title**: `ContextPulse: [Active Context Name]` (e.g., `ContextPulse: Office Workspace`)
    - **Body**: `Mode: [Active Mode Name] • Monitoring active` (e.g., `Mode: Focus (Scheduled 9am-5pm) • BLE active`)
    - **Tap Action**: Launches `MainActivity` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP`.
    - **Action Buttons**:
      - **"Snooze / Pause"**: Instantly pauses monitoring/applies Snooze mode for 15/30 mins.
      - **"Open"**: Quick jump to active context details.
  - Updates seamlessly in background whenever context transitions, mode schedules shift, or snooze is toggled.

### E. Xiaomi / HyperOS Device Optimization Guidance
- Xiaomi / HyperOS (MIUI) kills background processes aggressively unless explicitly exempted by the user.
- Add a dedicated **"Background Reliability Guide"** in `SettingsScreen.kt` and `PermissionsScreen.kt`:
  - 1-Tap shortcut to open MIUI Autostart settings (`com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity` with safe fallback to application details).
  - Step-by-step visual cards:
    1. **Enable Autostart** (permits reboot and background trigger evaluation).
    2. **Battery Saver -> Set to "No restrictions"** (prevents HyperOS from freezing BLE scans).
    3. **Lock App in Recent Apps** (prevents swipe-away kill).

---

## 3. Implementation Steps

1. **Service Unification & Extraction**:
   - Consolidate BLE trigger scanning and active automation execution into `ContextPulseEngineService` (enhancing `Phase0BleService`).
   - Wire `ContextEngine.activeRoomState`, `SleepTimerManager`, and schedule ticker into the service's CoroutineScope.
   - Clean up `MainActivity.kt` so it only hosts UI presentation and delegates background execution to the service.

2. **Schedule & Trigger Monitoring in Background**:
   - Implement continuous schedule boundary checks inside the service.
   - Ensure `isEffectiveNow()` is evaluated accurately across day/night boundaries.
   - Respect Preview Mode, Sleep Mode, and manual overrides without bypass.

3. **Notification Builder & Action Receivers**:
   - Build dynamic notification updating with active context, mode, and snooze status.
   - Add `NotificationActionReceiver` for handling Pause/Snooze actions directly from the notification tray.

4. **Reboot & Process Recovery**:
   - Implement `BootCompletedReceiver` that checks `SettingsRepository.isMonitoringEnabled()` and restarts `ContextPulseEngineService` via `ContextCompat.startForegroundService()`.

5. **Xiaomi / HyperOS Guidance Integration**:
   - Add device-specific background persistence banner and navigation action in Settings.

---

## 4. Verification Plan

1. **Compilation**: Run `compile_applet` to ensure zero compilation or linking issues.
2. **Decoupled Background Execution**:
   - Launch app, activate a context with restricted apps/brightness.
   - Minimize app and swipe away from Android Recents.
   - Verify notification persists with active context/mode name.
   - Verify opening a restricted app still triggers the block overlay / floating timer overlay.
3. **Scheduled Mode Transition in Background**:
   - Configure a mode to activate at a near-future minute.
   - Close app UI. Verify that when the minute arrives, the mode transitions, the notification updates, and new mode automations apply.
4. **Notification Actions**:
   - Tap "Snooze" on notification; verify monitoring pauses and resumes correctly.
