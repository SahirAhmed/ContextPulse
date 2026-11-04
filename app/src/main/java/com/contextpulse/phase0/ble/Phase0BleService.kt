package com.contextpulse.phase0.ble

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.contextpulse.phase0.Phase0Application
import com.contextpulse.phase0.model.BleBeaconInfo
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

class Phase0BleService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START_BLE_SERVICE"
        const val ACTION_STOP = "ACTION_STOP_BLE_SERVICE"
        const val ACTION_SET_SCAN_PROFILE = "ACTION_SET_SCAN_PROFILE"
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

        fun start(context: Context) {
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

    override fun onCreate() {
        super.onCreate()
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bleScanner = bluetoothAdapter?.bluetoothLeScanner

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ContextPulse::BleScanWakeLock")

        log("Phase0BleService initialized. BluetoothLeScanner ready: ${bleScanner != null}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_START -> {
                _isServiceRunning.value = true
                startForegroundWithNotification()
                startBleScanning()
                startDisappearanceTicker()
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
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, Phase0Application.CHANNEL_BLE_SERVICE)
            .setContentTitle("ContextPulse Service is currently running")
            .setContentText("Room beacon evaluation & context automation active")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val hasBlePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val fgsType = if (hasBlePermission) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                }
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val fgsType = if (hasBlePermission) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed startForeground with specific type, attempting fallback", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e2: Throwable) {
                Log.e(TAG, "Critical failure running startForeground", e2)
            }
        }

        log("Started Foreground Service with persistent status bar notification.")
    }

    @SuppressLint("MissingPermission")
    private fun startBleScanning() {
        if (isScanning) return

        val hasBlePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasBlePermission) {
            log("BLE scanning deferred: BLUETOOTH_SCAN permission not yet granted by user.")
            return
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            log("Bluetooth adapter disabled or unavailable. Scanning standby.")
            return
        }

        bleScanner = adapter.bluetoothLeScanner
        if (bleScanner == null) {
            log("ERROR: BluetoothLeScanner is null. Please enable Bluetooth.")
            return
        }

        val profile = _currentScanProfile.value
        val scanSettings = ScanSettings.Builder()
            .setScanMode(profile.scanMode)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        val scanFilters = listOf<ScanFilter>()

        try {
            bleScanner?.startScan(scanFilters, scanSettings, scanCallback)
            isScanning = true

            // WakeLock management: Only acquire for HIGH_PERFORMANCE test mode with safety timeout (3 mins max)
            if (profile == BleScanProfile.HIGH_PERFORMANCE) {
                wakeLock?.acquire(3 * 60 * 1000L) // 3 minutes timeout prevents battery drain
                log("Acquired temporary 3-minute WakeLock for HIGH_PERFORMANCE scan test.")
            } else {
                // In BALANCED / LOW_POWER, release wake lock to allow CPU sleep between duty cycles
                safeReleaseWakeLock()
            }

            log("BLE Scanning started: Mode=${profile.label}, WakeLock held=${wakeLock?.isHeld == true}")
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

        // TASK 4: ONLY registered room beacons are eligible for active room winner selection!
        if (matchingRoom != null) {
            registeredRoomsMap[mac] = finalInfo
            val activeRoomsList = registeredRoomsMap.values.toList().sortedByDescending { it.smoothedRssi }
            _registeredRoomBeacons.value = activeRoomsList

            // Arbitrate among REGISTERED room beacons only
            val winner = rssiFilter.arbitrateWinningBeacon(activeRoomsList)
            if (_activeRoomBeacon.value?.macAddress != winner?.macAddress) {
                log("Room context changed -> Active: ${winner?.assignedRoomName ?: winner?.name ?: "None"} (${winner?.macAddress}) [${winner?.smoothedRssi?.toInt()} dBm]")
            }
            _activeRoomBeacon.value = winner
        }
    }

    /**
     * Test 11: Periodic ticker for detecting when BLE signal disappears.
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
                            log("Test 11 - Registered Room Beacon Disappeared: ${removedRoom.assignedRoomName} ($mac)")
                        } else {
                            log("Test 11 - Unregistered BLE Device Disappeared: ${removedRaw?.name ?: mac}")
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
        serviceJob.cancel()
        safeReleaseWakeLock()
        log("Phase0BleService destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
