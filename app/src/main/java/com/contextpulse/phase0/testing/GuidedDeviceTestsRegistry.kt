package com.contextpulse.phase0.testing

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.provider.Settings
import com.contextpulse.phase0.ble.BeaconParser
import com.contextpulse.phase0.ble.Phase0BleService
import com.contextpulse.phase0.repository.BeaconRepository
import com.contextpulse.phase0.system.SystemControllers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Defines Interactive Guided Physical Device Tests.
 * Every entry provides an actionable on-device test trigger (purely passive instructions are omitted).
 */
object GuidedDeviceTestsRegistry {

    fun getGuidedDeviceTests(context: Context): List<DiagnosticTestResult> {
        val tests = mutableListOf<DiagnosticTestResult>()

        // 1. Live Screen Brightness Flash & Restore
        tests.add(
            DiagnosticTestResult(
                id = "device_brightness_flash",
                name = "Live Screen Brightness Flash & Restore",
                category = DiagnosticCategory.GUIDED_DEVICE,
                subcategory = "Display Hardware",
                status = DiagnosticStatus.DEVICE_TEST_REQUIRED,
                explanation = "Temporarily shifts screen brightness for 2.5 seconds, then safely restores your prior baseline.",
                expectedResult = "Screen brightness visibly shifts and restores prior baseline cleanly.",
                actualResult = "Ready to run on device.",
                correctiveAction = if (!SystemControllers.canModifySystemSettings(context)) "Grant Write System Settings first." else null,
                fixActionLabel = "Run 3s Flash Test",
                onFix = {
                    executeBrightnessFlashTest(context)
                }
            )
        )

        // 2. Do Not Disturb Mode Engage & Restore
        tests.add(
            DiagnosticTestResult(
                id = "device_dnd_engage",
                name = "Live Do Not Disturb (ZenMode) Test",
                category = DiagnosticCategory.GUIDED_DEVICE,
                subcategory = "Audio & Interruptions",
                status = DiagnosticStatus.DEVICE_TEST_REQUIRED,
                explanation = "Temporarily activates Priority DND for 3 seconds, then restores your previous interruption policy.",
                expectedResult = "DND status engages for 3s, then safely restores.",
                actualResult = "Ready to run on device.",
                correctiveAction = if (!SystemControllers.isDndAccessGranted(context)) "Grant Notification Policy Access first." else null,
                fixActionLabel = "Run 3s DND Test",
                onFix = {
                    executeDndFlashTest(context)
                }
            )
        )

        // 3. Live BLE Beacon Proximity Scan
        tests.add(
            DiagnosticTestResult(
                id = "device_ble_beacon_detection",
                name = "Live BLE Beacon Proximity Scan",
                category = DiagnosticCategory.GUIDED_DEVICE,
                subcategory = "Sensors & Hardware",
                status = DiagnosticStatus.DEVICE_TEST_REQUIRED,
                explanation = "Runs a 4-second high-priority BLE scan for nearby iBeacon and Eddystone beacons and measures live RSSI.",
                expectedResult = "Detects nearby BLE beacons and reports live signal strength (dBm).",
                actualResult = "Tap 'Scan Beacons (4s)' to test Bluetooth LE reception.",
                correctiveAction = if (!Phase0BleService.hasBluetoothPermission(context)) "Grant Bluetooth & Location permissions first." else null,
                fixActionLabel = "Scan Beacons (4s)",
                onFix = {
                    executeBleBeaconScanTest(context)
                }
            )
        )

        // 4. Camera QR Code Scanner Test (handled via QrScannerDialog in TestAppSuiteDialog)
        tests.add(
            DiagnosticTestResult(
                id = "device_qr_scan",
                name = "Camera QR Code Scanner Test",
                category = DiagnosticCategory.GUIDED_DEVICE,
                subcategory = "Triggers & Actions",
                status = DiagnosticStatus.DEVICE_TEST_REQUIRED,
                explanation = "Opens the live camera QR scanner to verify optical trigger decoding and payload matching.",
                expectedResult = "Decodes QR payload and reports whether it matches a registered ContextPulse trigger.",
                actualResult = "Tap 'Open QR Scanner' to scan a QR code.",
                fixActionLabel = "Open QR Scanner",
                onFix = { }
            )
        )

        // 5. Physical NFC Tag Tap Test (handled via NfcTapTestDialog in TestAppSuiteDialog)
        tests.add(
            DiagnosticTestResult(
                id = "device_nfc_tap",
                name = "Physical NFC Tag Tap Test",
                category = DiagnosticCategory.GUIDED_DEVICE,
                subcategory = "Triggers & Actions",
                status = DiagnosticStatus.DEVICE_TEST_REQUIRED,
                explanation = "Listens in foreground reader mode for a physical NFC sticker or card tap and inspects its UID and record.",
                expectedResult = "Reads physical tag hardware UID and NDEF record upon tap.",
                actualResult = "Tap 'Test NFC Tap' and hold a tag to your phone.",
                fixActionLabel = "Test NFC Tap",
                onFix = { }
            )
        )

        return tests
    }

    @SuppressLint("MissingPermission")
    fun executeBleBeaconScanTest(context: Context) {
        val coordinator = DiagnosticsCoordinator.getInstance(context)
        if (!Phase0BleService.hasBluetoothPermission(context)) {
            coordinator.updateGuidedDeviceTest(
                id = "device_ble_beacon_detection",
                status = DiagnosticStatus.NEEDS_ATTENTION,
                actualResult = "Missing Bluetooth Scan / Location permission."
            )
            android.widget.Toast.makeText(context, "Grant Bluetooth & Location permissions first.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = btManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            coordinator.updateGuidedDeviceTest(
                id = "device_ble_beacon_detection",
                status = DiagnosticStatus.NEEDS_ATTENTION,
                actualResult = "Bluetooth adapter is turned off."
            )
            android.widget.Toast.makeText(context, "Enable Bluetooth to run BLE scan test.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            coordinator.updateGuidedDeviceTest(
                id = "device_ble_beacon_detection",
                status = DiagnosticStatus.FAILED,
                actualResult = "BluetoothLeScanner unavailable on this device."
            )
            return
        }

        coordinator.updateGuidedDeviceTest(
            id = "device_ble_beacon_detection",
            status = DiagnosticStatus.RUNNING,
            actualResult = "Scanning for nearby iBeacon & Eddystone packets (4s)..."
        )

        val discovered = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, String>>()
        val registeredMacs = BeaconRepository.getInstance(context).beacons.value
            .filter { it.macAddress.isNotBlank() }
            .map { it.macAddress.uppercase() }
            .toSet()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result ?: return
                val mac = result.device?.address ?: return
                val info = BeaconParser.parseScanResult(result, result.rssi.toDouble())
                val label = info.name ?: info.beaconType.name
                discovered[mac.uppercase()] = Pair(result.rssi, label)
            }
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setManufacturerData(0x004C, byteArrayOf(0x02, 0x15), byteArrayOf(0xFF.toByte(), 0xFF.toByte()))
                .build(),
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB"))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(filters, settings, callback)
        } catch (e: Throwable) {
            coordinator.updateGuidedDeviceTest(
                id = "device_ble_beacon_detection",
                status = DiagnosticStatus.FAILED,
                actualResult = "Failed to start BLE scan: ${e.message ?: "unknown error"}"
            )
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            delay(4000L)
            try {
                scanner.stopScan(callback)
            } catch (_: Exception) {}

            if (discovered.isEmpty()) {
                coordinator.updateGuidedDeviceTest(
                    id = "device_ble_beacon_detection",
                    status = DiagnosticStatus.FAILED,
                    actualResult = "0 beacons detected in 4s. Ensure a beacon is powered on nearby."
                )
            } else {
                val strongest = discovered.entries.maxByOrNull { it.value.first }
                val matchedRegistered = discovered.keys.count { registeredMacs.contains(it) }
                coordinator.updateGuidedDeviceTest(
                    id = "device_ble_beacon_detection",
                    status = DiagnosticStatus.PASSED,
                    actualResult = "Detected ${discovered.size} beacon(s) ($matchedRegistered registered). Strongest: ${strongest?.value?.second ?: strongest?.key} at ${strongest?.value?.first} dBm."
                )
            }
        }
    }

    fun executeBrightnessFlashTest(context: Context) {
        val coordinator = DiagnosticsCoordinator.getInstance(context)
        if (!SystemControllers.canModifySystemSettings(context)) {
            coordinator.updateGuidedDeviceTest(
                id = "device_brightness_flash",
                status = DiagnosticStatus.NEEDS_ATTENTION,
                actualResult = "Modify System Settings permission not granted."
            )
            SystemControllers.openWriteSettings(context)
            return
        }

        val pending = GuidedTestRecoveryJournal.getPendingRecord(context)
        if (pending != null) {
            val (_, msg) = GuidedTestRecoveryJournal.attemptRestoration(context, pending)
            android.widget.Toast.makeText(context, "Recovery pending: $msg", android.widget.Toast.LENGTH_LONG).show()
            return
        }

        val originalBrightness = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (_: Exception) { 128 }

        val originalMode = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
        } catch (_: Exception) { Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL }

        val targetBrightness = if (originalBrightness > 150) originalBrightness - 30 else originalBrightness + 30
        val testId = java.util.UUID.randomUUID().toString()

        val committed = GuidedTestRecoveryJournal.recordTestIntent(
            context = context,
            testId = testId,
            testType = FlashTestType.BRIGHTNESS_FLASH,
            origBrightnessVal = originalBrightness,
            origBrightnessMode = originalMode,
            origDndFilter = 0,
            tempValApplied = targetBrightness
        )

        if (!committed) {
            android.widget.Toast.makeText(context, "Cannot run test: Failed to commit recovery journal.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        coordinator.updateGuidedDeviceTest(
            id = "device_brightness_flash",
            status = DiagnosticStatus.RUNNING,
            actualResult = "Shifting brightness temporarily for 2.5s..."
        )

        CoroutineScope(Dispatchers.Main).launch {
            try {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, targetBrightness.coerceIn(10, 255))
                android.widget.Toast.makeText(context, "Brightness flash active (Restoring in 2.5s)...", android.widget.Toast.LENGTH_SHORT).show()
                delay(2500L)
            } finally {
                val record = GuidedTestRecoveryJournal.getPendingRecord(context)
                if (record != null) {
                    val (success, msg) = GuidedTestRecoveryJournal.attemptRestoration(context, record)
                    coordinator.updateGuidedDeviceTest(
                        id = "device_brightness_flash",
                        status = if (success) DiagnosticStatus.PASSED else DiagnosticStatus.FAILED,
                        actualResult = msg
                    )
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun executeDndFlashTest(context: Context) {
        val coordinator = DiagnosticsCoordinator.getInstance(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (nm == null || !nm.isNotificationPolicyAccessGranted) {
            coordinator.updateGuidedDeviceTest(
                id = "device_dnd_engage",
                status = DiagnosticStatus.NEEDS_ATTENTION,
                actualResult = "Notification Policy (DND) access not granted."
            )
            SystemControllers.openDndAccessSettings(context)
            return
        }

        val pending = GuidedTestRecoveryJournal.getPendingRecord(context)
        if (pending != null) {
            val (_, msg) = GuidedTestRecoveryJournal.attemptRestoration(context, pending)
            android.widget.Toast.makeText(context, "Recovery pending: $msg", android.widget.Toast.LENGTH_LONG).show()
            return
        }

        val originalFilter = nm.currentInterruptionFilter
        val testId = java.util.UUID.randomUUID().toString()

        val committed = GuidedTestRecoveryJournal.recordTestIntent(
            context = context,
            testId = testId,
            testType = FlashTestType.DND_FLASH,
            origBrightnessVal = 0,
            origBrightnessMode = 0,
            origDndFilter = originalFilter,
            tempValApplied = NotificationManager.INTERRUPTION_FILTER_PRIORITY
        )

        if (!committed) {
            android.widget.Toast.makeText(context, "Cannot run test: Failed to commit recovery journal.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        coordinator.updateGuidedDeviceTest(
            id = "device_dnd_engage",
            status = DiagnosticStatus.RUNNING,
            actualResult = "Priority DND engaged for 3s..."
        )

        CoroutineScope(Dispatchers.Main).launch {
            try {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                android.widget.Toast.makeText(context, "DND Priority active for 3s (Restoring)...", android.widget.Toast.LENGTH_SHORT).show()
                delay(3000L)
            } finally {
                val record = GuidedTestRecoveryJournal.getPendingRecord(context)
                if (record != null) {
                    val (success, msg) = GuidedTestRecoveryJournal.attemptRestoration(context, record)
                    coordinator.updateGuidedDeviceTest(
                        id = "device_dnd_engage",
                        status = if (success) DiagnosticStatus.PASSED else DiagnosticStatus.FAILED,
                        actualResult = msg
                    )
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
