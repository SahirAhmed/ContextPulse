package com.contextpulse.phase0.ble

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.contextpulse.phase0.Phase0Application
import com.contextpulse.phase0.model.BleBeaconInfo
import com.contextpulse.phase0.model.TriggerSource
import com.contextpulse.phase0.repository.BeaconRepository
import com.contextpulse.phase0.repository.RoomProfileRepository
import com.contextpulse.phase0.system.ModeScheduleManager
import com.contextpulse.phase0.system.ProfileActivationHelper
import com.contextpulse.phase0.system.SnoozeManager
import com.contextpulse.phase0.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

enum class BleScanProfile(val scanMode: Int, val label: String, val dutyCycle: String) {
    HIGH_PERFORMANCE(ScanSettings.SCAN_MODE_LOW_LATENCY, "High Performance (100% Duty Cycle)", "High battery consumption (Test mode)"),
    BALANCED(ScanSettings.SCAN_MODE_BALANCED, "Balanced (~25% Duty Cycle)", "Moderate battery consumption"),
    LOW_POWER(ScanSettings.SCAN_MODE_LOW_POWER, "Low Power (~10% Duty Cycle)", "Minimal battery consumption")
}

/**
 * Authoritative ContextPulse Background Engine Service.
 *
 * Runs as an Android Foreground Service with type 'connectedDevice' (Bluetooth LE).
 * Coordinates:
 * 1. Hardware-filtered BLE Beacon scanning and arbitration across registered contexts.
 * 2. Schedule boundary evaluation via AlarmManager wakeups (eliminating polling loops).
 * 3. Dynamic ongoing status notification reflecting actual sensor and permission status.
 * 4. Separation of concerns: Delegates all context and mode transitions solely to ProfileActivationHelper
 *    and TransitionDiffEngine, preventing duplicate executions.
 */
class Phase0BleService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START_BLE_SERVICE"
        const val ACTION_STOP = "ACTION_STOP_BLE_SERVICE"
        const val ACTION_SET_SCAN_PROFILE = "ACTION_SET_SCAN_PROFILE"
        const val ACTION_START_SNOOZE_15 = "ACTION_START_SNOOZE_15"
        const val ACTION_CANCEL_SNOOZE = "ACTION_CANCEL_SNOOZE"
        const val ACTION_REAPPLY_ACTIVE = "ACTION_REAPPLY_ACTIVE"

        const val EXTRA_SCAN_PROFILE = "EXTRA_SCAN_PROFILE"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "Phase0BleService"

        // All discovered BLE devices (raw radar view)
        private val _allDiscoveredDevices = MutableStateFlow<List<BleBeaconInfo>>(emptyList())
        val allDiscoveredDevices: StateFlow<List<BleBeaconInfo>> = _allDiscoveredDevices.asStateFlow()

        // Service active status
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        // Only registered room beacons
        private val _registeredRoomBeacons = MutableStateFlow<List<BleBeaconInfo>>(emptyList())
        val registeredRoomBeacons: StateFlow<List<BleBeaconInfo>> = _registeredRoomBeacons.asStateFlow()

        // Dominating active room winner
        private val _activeRoomBeacon = MutableStateFlow<BleBeaconInfo?>(null)
        val activeRoomBeacon: StateFlow<BleBeaconInfo?> = _activeRoomBeacon.asStateFlow()

        // Active scan profile
        private val _currentScanProfile = MutableStateFlow(BleScanProfile.BALANCED)
        val currentScanProfile: StateFlow<BleScanProfile> = _currentScanProfile.asStateFlow()

        // Diagnostic logs
        private val _serviceLogs = MutableStateFlow<List<String>>(emptyList())
        val serviceLogs: StateFlow<List<String>> = _serviceLogs.asStateFlow()

        fun log(message: String) {
            Log.d(TAG, message)
            val timestamped = "[${System.currentTimeMillis() % 100000}] $message"
            val current = _serviceLogs.value.takeLast(99).toMutableList()
            current.add(timestamped)
            _serviceLogs.value = current
        }

        fun hasBluetoothPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
        }

        fun start(context: Context) {
            // Android 14+ connectedDevice foreground service type strictly requires Bluetooth permissions at runtime.
            // When permissions are not granted, do not start the service to prevent crashes.
            // AlarmManager will still handle scheduled context and mode boundaries.
            if (!hasBluetoothPermission(context)) {
                Log.w(TAG, "Bluetooth permissions not granted; skipping Phase0BleService start. Scheduled alarms will manage mode boundaries.")
                com.contextpulse.phase0.system.ModeScheduleManager.scheduleNextBoundaryAlarm(context)
                return
            }

            try {
                val intent = Intent(context, Phase0BleService::class.java).apply {
                    action = ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Phase0BleService", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, Phase0BleService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop Phase0BleService", e)
            }
        }

        fun updatePersistentNotification(context: Context) {
            try {
                val intent = Intent(context, Phase0BleService::class.java).apply {
                    action = ACTION_REAPPLY_ACTIVE
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val rssiFilter = RssiFilter()

    // MAC -> Info maps
    private val allDevicesMap = ConcurrentHashMap<String, BleBeaconInfo>()
    private val registeredRoomsMap = ConcurrentHashMap<String, BleBeaconInfo>()

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var isScanning = false

    // Context & Mode tracking for deduplication
    private var previousActiveRoomId: String? = null
    private var currentActiveModeId: String? = null
    private var isInitialServiceLaunch = true

    // Bluetooth State Receiver
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_ON -> {
                        log("Bluetooth enabled by user. Resuming BLE scanner.")
                        startBleScanning()
                        updateNotification()
                    }
                    BluetoothAdapter.STATE_OFF -> {
                        log("Bluetooth disabled by user. Suspending BLE scanner.")
                        stopBleScanning()
                        updateNotification()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bleScanner = bluetoothAdapter?.bluetoothLeScanner

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ContextPulse::BleScanWakeLock")

        // Register dynamic Bluetooth receiver
        try {
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            registerReceiver(bluetoothReceiver, filter)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to register bluetooth receiver", e)
        }

        // Initialize background coordination without polling loops
        startAutomationCoordinator()
        startDisappearanceTicker()

        log("Phase0BleService initialized. BluetoothLeScanner ready: ${bleScanner != null}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_START -> {
                _isServiceRunning.value = true
                startForegroundWithNotification()
                startBleScanning()
                ModeScheduleManager.scheduleNextBoundaryAlarm(applicationContext)
            }
            ACTION_STOP -> {
                _isServiceRunning.value = false
                stopBleScanning()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_SET_SCAN_PROFILE -> {
                val profileName = intent?.getStringExtra(EXTRA_SCAN_PROFILE)
                val newProfile = BleScanProfile.values().find { it.name == profileName } ?: BleScanProfile.BALANCED
                _currentScanProfile.value = newProfile
                log("Switching BLE Scan Profile to: ${newProfile.label}")
                if (isScanning) {
                    stopBleScanning()
                    startBleScanning()
                }
            }
            ACTION_START_SNOOZE_15 -> {
                log("Snooze triggered from persistent notification (15 mins)")
                SnoozeManager.getInstance(applicationContext).startSnooze(15)
                ProfileActivationHelper.reapplyCurrentActive(applicationContext)
                updateNotification()
            }
            ACTION_CANCEL_SNOOZE -> {
                log("Snooze resumed from persistent notification")
                SnoozeManager.getInstance(applicationContext).cancelSnooze()
                ProfileActivationHelper.reapplyCurrentActive(applicationContext)
                updateNotification()
            }
            ACTION_REAPPLY_ACTIVE -> {
                // Triggered e.g. from ScheduleBoundaryReceiver alarm
                ProfileActivationHelper.reapplyCurrentActive(applicationContext)
                updateNotification()
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        log("ContextPulse swiped from Recent Apps. Maintaining persistent foreground monitoring.")
        updateNotification()
    }

    /**
     * Starts foreground service using ONLY 'connectedDevice' type where permitted.
     * Complies with Android 14/15/16 foreground service security invariants.
     */
    private fun startForegroundWithNotification() {
        val notification = buildPersistentNotification()
        val hasBlePermission = hasBluetoothPermission()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasBlePermission) {
                // Strictly connectedDevice: removes deprecated dataSync and avoids 6-hour timeout
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException on startForeground; continuing with notification fallback", e)
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Throwable) {
                Log.e(TAG, "Cannot start foreground service without permissions", e2)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error starting foreground service", e)
        }

        log("Foreground Service active. connectedDevice granted: $hasBlePermission")
    }

    private fun hasBluetoothPermission(): Boolean {
        return Phase0BleService.hasBluetoothPermission(this)
    }

    /**
     * Updates the persistent notification in place with accurate sensor and context state.
     */
    fun updateNotification() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val notification = buildPersistentNotification()
            notificationManager?.notify(NOTIFICATION_ID, notification)
        } catch (e: Throwable) {
            Log.w(TAG, "Error updating foreground notification", e)
        }
    }

    /**
     * Builds persistent notification accurately reflecting ground truth:
     * - Identifies whether BLE scanning is genuinely active, disabled, or permission-denied.
     * - Accurately reflects Active Context, Mode, or Snooze countdown.
     */
    private fun buildPersistentNotification(): Notification {
        val roomRepo = RoomProfileRepository.getInstance(applicationContext)
        val snoozeManager = SnoozeManager.getInstance(applicationContext)
        val activeRoom = roomRepo.activeRoom.value
        val isSnoozed = snoozeManager.isSnoozeActive()

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: bluetoothAdapter
        val isBtEnabled = adapter != null && adapter.isEnabled
        val hasBlePermission = hasBluetoothPermission()

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Notification Action 1: Snooze / Resume toggle
        val actionIntent = Intent(this, Phase0BleService::class.java).apply {
            action = if (isSnoozed) ACTION_CANCEL_SNOOZE else ACTION_START_SNOOZE_15
        }
        val actionPendingIntent = PendingIntent.getService(
            this,
            1,
            actionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val actionTitle = if (isSnoozed) "Resume Automations" else "Snooze (15m)"

        // Notification Action 2: Open App
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            2,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title: String
        val body: String

        if (isSnoozed) {
            val remainingClock = snoozeManager.getFormattedRemainingTime()
            title = "ContextPulse • Snoozed"
            body = "Automations paused ($remainingClock left) • Tap to manage"
        } else if (activeRoom != null) {
            val activeMode = activeRoom.profile.resolveActiveMode()
            title = "ContextPulse: ${activeRoom.name}"
            body = when {
                !hasBlePermission -> "Mode: ${activeMode.name} • Bluetooth permission required (Schedules active)"
                !isBtEnabled -> "Mode: ${activeMode.name} • Bluetooth disabled (Schedules active)"
                isScanning -> "Mode: ${activeMode.name} • BLE & schedules active"
                else -> "Mode: ${activeMode.name} • Schedules active"
            }
        } else {
            title = "ContextPulse: Baseline (Default)"
            body = when {
                !hasBlePermission -> "Ready • Bluetooth permission required (Schedules active)"
                !isBtEnabled -> "Ready • Bluetooth disabled (Schedules active)"
                isScanning -> "Ready • BLE & schedules active"
                else -> "Ready • Schedules active"
            }
        }

        return NotificationCompat.Builder(this, Phase0Application.CHANNEL_BLE_SERVICE)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(tapPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                if (isSnoozed) android.R.drawable.ic_media_play else android.R.drawable.ic_lock_idle_alarm,
                actionTitle,
                actionPendingIntent
            )
            .addAction(android.R.drawable.ic_menu_view, "Open App", openAppPendingIntent)
            .build()
    }

    /**
     * Deduplicated Context & Mode Coordinator.
     * Watches repository state changes to maintain accurate notifications and boundary alarms.
     * Leaves actual system automation execution strictly to TransitionDiffEngine & ProfileActivationHelper.
     */
    private fun startAutomationCoordinator() {
        val roomRepo = RoomProfileRepository.getInstance(applicationContext)
        val snoozeManager = SnoozeManager.getInstance(applicationContext)

        serviceScope.launch {
            // Collect Active Room changes
            launch {
                roomRepo.activeRoom.collect { activeRoom ->
                    val newRoomId = activeRoom?.id
                    val newModeId = activeRoom?.profile?.resolveActiveMode()?.id

                    val roomChanged = newRoomId != previousActiveRoomId
                    val modeChanged = newModeId != currentActiveModeId

                    if (roomChanged || modeChanged) {
                        previousActiveRoomId = newRoomId
                        currentActiveModeId = newModeId

                        // Recalculate schedule boundary alarm whenever context/mode changes
                        ModeScheduleManager.scheduleNextBoundaryAlarm(applicationContext)

                        // If cold service startup with an existing active room, ensure settings are applied
                        if (isInitialServiceLaunch && activeRoom != null) {
                            isInitialServiceLaunch = false
                            ProfileActivationHelper.reapplyCurrentActive(applicationContext)
                        } else {
                            isInitialServiceLaunch = false
                        }

                        updateNotification()
                    }
                }
            }

            // Collect Snooze state changes
            launch {
                snoozeManager.snoozeEndTime.collect { _ ->
                    updateNotification()
                }
            }
        }
    }

    /**
     * Builds hardware ScanFilters derived from registered beacon configurations (iBeacon, Eddystone, and MAC).
     * Prevents Android OS from dropping background advertisements when screen turns off.
     */
    private fun buildScanFilters(): List<ScanFilter> {
        val filters = mutableListOf<ScanFilter>()
        try {
            val registered = BeaconRegistry.getRegisteredBeacons(this)
            val repoBeacons = BeaconRepository.getInstance(this).beacons.value

            val macSet = mutableSetOf<String>()
            registered.forEach { r ->
                if (r.macAddress.isNotBlank() && BluetoothAdapter.checkBluetoothAddress(r.macAddress)) {
                    macSet.add(r.macAddress.uppercase())
                }
            }
            repoBeacons.forEach { b ->
                if (b.macAddress.isNotBlank() && BluetoothAdapter.checkBluetoothAddress(b.macAddress)) {
                    macSet.add(b.macAddress.uppercase())
                }
            }

            // 1. MAC Address Hardware Filters
            for (mac in macSet) {
                filters.add(ScanFilter.Builder().setDeviceAddress(mac).build())
            }

            // 2. Apple iBeacon Manufacturer Filter (0x004C, header 0x02, 0x15)
            // Allows hardware controller to pass iBeacon advertisements when screen is sleeping
            val iBeaconHeader = byteArrayOf(0x02, 0x15)
            val iBeaconMask = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
            filters.add(
                ScanFilter.Builder()
                    .setManufacturerData(0x004C, iBeaconHeader, iBeaconMask)
                    .build()
            )

            // 3. Eddystone Service UUID Filter (0xFEAA)
            try {
                val eddystoneUuid = ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")
                filters.add(ScanFilter.Builder().setServiceUuid(eddystoneUuid).build())
            } catch (_: Exception) {}

        } catch (e: Exception) {
            log("Error building scan filters: ${e.message}")
        }
        return filters
    }

    @SuppressLint("MissingPermission")
    private fun startBleScanning() {
        if (isScanning) return

        if (!hasBluetoothPermission()) {
            log("BLE scanning deferred: BLUETOOTH_SCAN permission not granted.")
            updateNotification()
            return
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            log("Bluetooth adapter disabled or unavailable. Scanning standby.")
            updateNotification()
            return
        }

        bleScanner = adapter.bluetoothLeScanner
        if (bleScanner == null) {
            log("ERROR: BluetoothLeScanner is null. Please enable Bluetooth.")
            updateNotification()
            return
        }

        val profile = _currentScanProfile.value
        val scanSettings = ScanSettings.Builder()
            .setScanMode(profile.scanMode)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        val scanFilters = buildScanFilters()

        try {
            bleScanner?.startScan(scanFilters, scanSettings, scanCallback)
            isScanning = true

            // WakeLock management: Only acquire for HIGH_PERFORMANCE test mode with safety timeout (3 mins max)
            if (profile == BleScanProfile.HIGH_PERFORMANCE) {
                wakeLock?.acquire(3 * 60 * 1000L) // 3 minutes timeout prevents battery drain
                log("Acquired temporary 3-minute WakeLock for HIGH_PERFORMANCE scan test.")
            } else {
                safeReleaseWakeLock()
            }

            log("BLE Scanning started: Mode=${profile.label}, Filters=${scanFilters.size}, WakeLock held=${wakeLock?.isHeld == true}")
            updateNotification()
        } catch (e: Throwable) {
            log("ERROR starting BLE scan: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScanning() {
        if (!isScanning) return
        try {
            bleScanner?.stopScan(scanCallback)
            isScanning = false
            safeReleaseWakeLock()
            log("BLE Scanning stopped and WakeLock safely released.")
            updateNotification()
        } catch (e: Exception) {
            log("Error stopping BLE scan: ${e.message}")
        }
    }

    private fun safeReleaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            try {
                wakeLock?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing wake lock", e)
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            log("BLE Scan Failed with error code: $errorCode")
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val mac = result.device.address ?: return
        val rawRssi = result.rssi
        val smoothed = rssiFilter.update(mac, rawRssi)

        val rawBeaconInfo = BeaconParser.parseScanResult(result, smoothed)

        // Check if this advertisement matches a registered room beacon
        val matchingRoom = BeaconRegistry.findMatchingRoom(
            context = this,
            macAddress = mac,
            uuid = rawBeaconInfo.uuid,
            major = rawBeaconInfo.major,
            minor = rawBeaconInfo.minor
        )

        val finalInfo = rawBeaconInfo.copy(
            isRegisteredRoomBeacon = matchingRoom != null,
            assignedRoomName = matchingRoom?.roomName
        )

        allDevicesMap[mac] = finalInfo
        _allDiscoveredDevices.value = allDevicesMap.values.toList().sortedByDescending { it.smoothedRssi }

        // Low battery notification check (< 15%)
        val battery = finalInfo.batteryPercentage
        if (battery != null && battery < 15) {
            val displayName = finalInfo.assignedRoomName ?: finalInfo.name ?: "BLE Beacon ($mac)"
            try {
                com.contextpulse.phase0.system.ContextPulseNotificationManager.getInstance(this)
                    .notifyLowBeaconBattery(displayName, battery)
            } catch (e: Throwable) {
                // Ignored
            }
        }

        // ONLY registered room beacons are eligible for active room winner selection
        if (matchingRoom != null) {
            registeredRoomsMap[mac] = finalInfo
            val activeRoomsList = registeredRoomsMap.values.toList().sortedByDescending { it.smoothedRssi }
            _registeredRoomBeacons.value = activeRoomsList

            // Arbitrate among REGISTERED room beacons only using smoothed RSSI & hysteresis
            val winner = rssiFilter.arbitrateWinningBeacon(activeRoomsList)
            if (_activeRoomBeacon.value?.macAddress != winner?.macAddress) {
                log("Room context changed -> Active: ${winner?.assignedRoomName ?: winner?.name ?: "None"} (${winner?.macAddress}) [${winner?.smoothedRssi?.toInt()} dBm]")
                _activeRoomBeacon.value = winner

                // BRIDGE TO CONTEXT LAYER: If winning beacon changed, trigger context activation
                if (winner != null) {
                    val targetName = winner.assignedRoomName ?: winner.name
                    val roomRepo = RoomProfileRepository.getInstance(applicationContext)
                    val targetRoom = roomRepo.rooms.value.find {
                        it.name.equals(targetName, ignoreCase = true) || it.id.equals(targetName, ignoreCase = true)
                    }
                    if (targetRoom != null && targetRoom.id != roomRepo.activeRoomId.value) {
                        log("Triggering context activation for '${targetRoom.name}' via BLE Beacon (${winner.macAddress})")
                        ProfileActivationHelper.activateProfile(
                            context = applicationContext,
                            roomId = targetRoom.id,
                            triggerSource = TriggerSource.BLE_BEACON,
                            reason = "Arrived at beacon: ${winner.assignedRoomName ?: winner.name}"
                        )
                    }
                }
            } else {
                _activeRoomBeacon.value = winner
            }
        }
    }

    /**
     * Periodic ticker for detecting when BLE signal disappears.
     */
    private fun startDisappearanceTicker() {
        serviceScope.launch {
            while (isActive) {
                delay(3000L) // Check every 3 seconds
                val nowMono = SystemClock.elapsedRealtime()
                val purgedMacs = rssiFilter.purgeDisappearedBeacons(nowMono)

                if (purgedMacs.isNotEmpty()) {
                    var roomLost = false
                    for (mac in purgedMacs) {
                        val removedRaw = allDevicesMap.remove(mac)
                        val removedRoom = registeredRoomsMap.remove(mac)
                        if (removedRoom != null) {
                            roomLost = true
                            log("Registered Room Beacon Disappeared: ${removedRoom.assignedRoomName} ($mac)")
                        } else {
                            log("Unregistered BLE Device Disappeared: ${removedRaw?.name ?: mac}")
                        }
                    }

                    _allDiscoveredDevices.value = allDevicesMap.values.toList().sortedByDescending { it.smoothedRssi }
                    val updatedRooms = registeredRoomsMap.values.toList().sortedByDescending { it.smoothedRssi }
                    _registeredRoomBeacons.value = updatedRooms

                    if (roomLost || _activeRoomBeacon.value?.macAddress in purgedMacs) {
                        val newWinner = rssiFilter.arbitrateWinningBeacon(updatedRooms, nowMono)
                        log("Room context updated after signal disappearance -> ${newWinner?.assignedRoomName ?: "No Active Room"}")
                        _activeRoomBeacon.value = newWinner
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
        stopBleScanning()
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (_: Exception) {}
        serviceJob.cancel()
        safeReleaseWakeLock()
        log("Phase0BleService destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
