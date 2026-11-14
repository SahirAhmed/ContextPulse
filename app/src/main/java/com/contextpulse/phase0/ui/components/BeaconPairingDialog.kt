package com.contextpulse.phase0.ui.components

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.contextpulse.phase0.ble.BeaconParser
import com.contextpulse.phase0.model.RegisteredBeacon
import com.contextpulse.phase0.model.TriggerType
import com.contextpulse.phase0.repository.BeaconRepository
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private const val TARGET_SENSECAP_UUID = "7B93E4A2-1D58-48B0-8F6A-93C68A4E12D5"
private const val TARGET_SENSECAP_UUID_CLEAN = "7B93E4A21D5848B08F6A93C68A4E12D5"

enum class PairingStatus {
    SCANNING,
    BEACON_DETECTED,
    VERY_CLOSE,
    BEACON_FOUND,
    SIGNAL_LOST,
    PERMISSION_REQUIRED,
    BLUETOOTH_DISABLED
}

data class DetectedSenseCapBeacon(
    val macAddress: String,
    val uuid: String,
    val major: Int,
    val minor: Int,
    val txPower: Int,
    val rawRssi: Int,
    val smoothedRssi: Double,
    val estimatedDistanceMeters: Double
)

@Composable
fun BeaconPairingDialog(
    beaconRepo: BeaconRepository,
    onDismiss: () -> Unit,
    onBeaconSaved: (RegisteredBeacon) -> Unit
) {
    val context = LocalContext.current
    val theme = LocalContextPulseTheme.current

    // Permission checks
    fun checkHasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val connectGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            scanGranted && connectGranted
        } else {
            val locGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            locGranted
        }
    }

    var hasPermissions by remember { mutableStateOf(checkHasPermissions()) }
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermissions = checkHasPermissions()
    }

    val btManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager }
    val btAdapter: BluetoothAdapter? = btManager?.adapter
    val isBtEnabled = btAdapter?.isEnabled == true

    var pairingStatus by remember {
        mutableStateOf(
            when {
                !hasPermissions -> PairingStatus.PERMISSION_REQUIRED
                !isBtEnabled -> PairingStatus.BLUETOOTH_DISABLED
                else -> PairingStatus.SCANNING
            }
        )
    }

    // Live beacon telemetry
    var detectedBeacon by remember { mutableStateOf<DetectedSenseCapBeacon?>(null) }
    var rawRssiState by remember { mutableIntStateOf(-100) }
    var smoothedRssiState by remember { mutableDoubleStateOf(-100.0) }
    var estimatedDistanceState by remember { mutableDoubleStateOf(2.0) }
    var consecutiveCloseSamples by remember { mutableIntStateOf(0) }
    var lastSeenMonotonicMs by remember { mutableLongStateOf(0L) }

    // Proximity parameters
    // Calibrated threshold: -60 dBm represents immediate close proximity (~10-20cm depending on phone antenna)
    var proximityThresholdDbm by remember { mutableFloatStateOf(-60f) }
    val hysteresisDb = 5.0 // Fallback guard: drop below -65 dBm before falling back from VERY_CLOSE

    // Smoothing EMA state
    var emaSmoothedRssi by remember { mutableStateOf<Double?>(null) }
    val emaAlpha = 0.30 // Standard responsive BLE smoothing factor

    // Name input & duplicate check
    var customName by remember { mutableStateOf("") }
    var duplicateBeacon by remember { mutableStateOf<RegisteredBeacon?>(null) }
    val tlmBatteryByMac = remember { mutableStateMapOf<String, Int>() }

    // Temporary BLE Scanner lifecycle
    DisposableEffect(hasPermissions, isBtEnabled) {
        if (!hasPermissions || !isBtEnabled || btAdapter == null) {
            return@DisposableEffect onDispose {}
        }

        val scanner = btAdapter.bluetoothLeScanner
        if (scanner == null) {
            pairingStatus = PairingStatus.BLUETOOTH_DISABLED
            return@DisposableEffect onDispose {}
        }

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                if (result == null || pairingStatus == PairingStatus.BEACON_FOUND) return

                val record = result.scanRecord ?: return
                val mac = result.device.address ?: "00:00:00:00:00:00"
                val rawRssi = result.rssi

                // Capture passive Eddystone-TLM battery % if beacon interleaves TLM frames
                BeaconParser.parseEddystoneTlmBattery(record)?.let { tlm ->
                    tlmBatteryByMac[mac.uppercase()] = tlm.second
                }

                // Parse iBeacon data (0x004C)
                val appleData = record.getManufacturerSpecificData(0x004C) ?: return
                if (appleData.size < 23 || appleData[0] != 0x02.toByte() || appleData[1] != 0x15.toByte()) {
                    return
                }

                // Verify target UUID (7B93E4A2-1D58-48B0-8F6A-93C68A4E12D5)
                val uuidBytes = appleData.copyOfRange(2, 18)
                val sb = StringBuilder()
                for (b in uuidBytes) {
                    sb.append(String.format("%02X", b))
                }
                val rawUuidClean = sb.toString()
                if (!rawUuidClean.equals(TARGET_SENSECAP_UUID_CLEAN, ignoreCase = true)) {
                    return
                }

                val major = ((appleData[18].toInt() and 0xFF) shl 8) or (appleData[19].toInt() and 0xFF)
                val minor = ((appleData[20].toInt() and 0xFF) shl 8) or (appleData[21].toInt() and 0xFF)
                val txPower = appleData[22].toInt()

                // Calculate smoothed RSSI with Exponential Moving Average
                val currentEma = emaSmoothedRssi
                val updatedSmoothed = if (currentEma == null) {
                    rawRssi.toDouble()
                } else {
                    (emaAlpha * rawRssi) + ((1.0 - emaAlpha) * currentEma)
                }
                emaSmoothedRssi = updatedSmoothed

                // Calculate estimated distance
                val distance = BeaconParser.calculateDistance(updatedSmoothed, txPower)

                val nowMono = SystemClock.elapsedRealtime()
                lastSeenMonotonicMs = nowMono
                rawRssiState = rawRssi
                smoothedRssiState = updatedSmoothed
                estimatedDistanceState = distance

                val beaconInfo = DetectedSenseCapBeacon(
                    macAddress = mac,
                    uuid = TARGET_SENSECAP_UUID,
                    major = major,
                    minor = minor,
                    txPower = txPower,
                    rawRssi = rawRssi,
                    smoothedRssi = updatedSmoothed,
                    estimatedDistanceMeters = distance
                )
                detectedBeacon = beaconInfo

                // Evaluate Proximity State Machine
                val targetThreshold = proximityThresholdDbm.toDouble()
                if (updatedSmoothed >= targetThreshold) {
                    // In immediate proximity zone
                    val newCount = consecutiveCloseSamples + 1
                    consecutiveCloseSamples = newCount

                    if (newCount >= 3) {
                        // Confirmed stable immediate proximity
                        pairingStatus = PairingStatus.BEACON_FOUND
                        // Default name prefill
                        if (customName.isBlank()) {
                            customName = "ContextPulse BLE Beacon $minor"
                        }
                        // Check for duplicate registered identity (UUID + Major + Minor)
                        duplicateBeacon = beaconRepo.findDuplicateIBeacon(TARGET_SENSECAP_UUID, major, minor)

                        // Trigger haptic confirmation
                        try {
                            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                            if (vibrator != null && vibrator.hasVibrator()) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator.vibrate(120)
                                }
                            }
                        } catch (_: Exception) {}

                        // Immediately stop low-latency scanning once found
                        try {
                            scanner.stopScan(this)
                        } catch (_: Exception) {}
                    } else {
                        pairingStatus = PairingStatus.VERY_CLOSE
                    }
                } else if (updatedSmoothed >= (targetThreshold - hysteresisDb)) {
                    // Hysteresis buffer
                    if (pairingStatus == PairingStatus.VERY_CLOSE) {
                        // Retain VERY_CLOSE in buffer zone
                    } else {
                        pairingStatus = PairingStatus.BEACON_DETECTED
                        consecutiveCloseSamples = 0
                    }
                } else {
                    pairingStatus = PairingStatus.BEACON_DETECTED
                    consecutiveCloseSamples = 0
                }
            }

            override fun onScanFailed(errorCode: Int) {
                pairingStatus = PairingStatus.SIGNAL_LOST
            }
        }

        // Low-latency temporary scan settings
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        // Hardware filters for Apple iBeacon prefix (0x004C) and Eddystone telemetry (0xFEAA)
        val iBeaconFilter = ScanFilter.Builder()
            .setManufacturerData(0x004C, byteArrayOf(0x02, 0x15), byteArrayOf(0xFF.toByte(), 0xFF.toByte()))
            .build()
        val eddystoneFilter = ScanFilter.Builder()
            .setServiceUuid(android.os.ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB"))
            .build()

        try {
            scanner.startScan(listOf(iBeaconFilter, eddystoneFilter), settings, scanCallback)
        } catch (_: SecurityException) {
            pairingStatus = PairingStatus.PERMISSION_REQUIRED
        } catch (_: Exception) {
            pairingStatus = PairingStatus.SIGNAL_LOST
        }

        onDispose {
            try {
                scanner.stopScan(scanCallback)
            } catch (_: Exception) {}
        }
    }

    // Ticker to check for signal loss if packets stop arriving (> 3.5s)
    LaunchedEffect(pairingStatus, lastSeenMonotonicMs) {
        if (pairingStatus == PairingStatus.BEACON_DETECTED || pairingStatus == PairingStatus.VERY_CLOSE) {
            while (isActive) {
                delay(1000L)
                val nowMono = SystemClock.elapsedRealtime()
                if (lastSeenMonotonicMs > 0L && (nowMono - lastSeenMonotonicMs) > 3500L) {
                    pairingStatus = PairingStatus.SIGNAL_LOST
                    consecutiveCloseSamples = 0
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 560.dp)
                .wrapContentHeight()
                .clip(RoundedCornerShape(26.dp)),
            color = theme.surface,
            border = BorderStroke(1.dp, theme.border),
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(theme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BluetoothSearching,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "BEACON PAIRING",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.accent,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "ContextPulse Beacon",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = theme.textPrimary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(theme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = theme.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Radar Pulse Animation Canvas
                RadarPulseView(
                    status = pairingStatus,
                    smoothedRssi = smoothedRssiState,
                    estimatedDistance = estimatedDistanceState,
                    consecutiveSamples = consecutiveCloseSamples,
                    theme = theme
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Status Banner
                StatusBanner(pairingStatus, consecutiveCloseSamples, theme)

                Spacer(modifier = Modifier.height(16.dp))

                // Telemetry & Live Proximity Meter
                if (pairingStatus == PairingStatus.BEACON_DETECTED ||
                    pairingStatus == PairingStatus.VERY_CLOSE ||
                    pairingStatus == PairingStatus.BEACON_FOUND
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surfaceVariant),
                        border = BorderStroke(1.dp, theme.border)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "PROXIMITY RADAR TELEMETRY",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.accent,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = "${smoothedRssiState.toInt()} dBm",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (pairingStatus == PairingStatus.VERY_CLOSE || pairingStatus == PairingStatus.BEACON_FOUND) Color(0xFF10B981) else theme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Distance progress bar (0.0m to 1.5m range)
                            val progress = ((1.5 - estimatedDistanceState.coerceIn(0.1, 1.5)) / 1.4).toFloat().coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (pairingStatus == PairingStatus.VERY_CLOSE || pairingStatus == PairingStatus.BEACON_FOUND) Color(0xFF10B981) else theme.primary,
                                trackColor = theme.border
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Raw: $rawRssiState dBm",
                                    fontSize = 11.sp,
                                    color = theme.textSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = String.format(Locale.US, "~%.2f m (Estimated)", estimatedDistanceState),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = theme.textPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actions / Found Content
                when (pairingStatus) {
                    PairingStatus.PERMISSION_REQUIRED -> {
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    permLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.BLUETOOTH_SCAN,
                                            Manifest.permission.BLUETOOTH_CONNECT
                                        )
                                    )
                                } else {
                                    permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                        ) {
                            Text("Grant Bluetooth Permissions", fontWeight = FontWeight.Bold)
                        }
                    }

                    PairingStatus.BLUETOOTH_DISABLED -> {
                        Button(
                            onClick = {
                                try {
                                    context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    })
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                        ) {
                            Text("Enable Bluetooth in Settings", fontWeight = FontWeight.Bold)
                        }
                    }

                    PairingStatus.BEACON_FOUND -> {
                        val beacon = detectedBeacon
                        if (beacon != null) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Metadata Card
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = theme.surfaceVariant),
                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("MAC Address", fontSize = 11.sp, color = theme.textSecondary)
                                            Text(beacon.macAddress, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = theme.textPrimary)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("iBeacon UUID", fontSize = 11.sp, color = theme.textSecondary)
                                            Text(beacon.uuid.take(18) + "...", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = theme.textPrimary)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Major / Minor", fontSize = 11.sp, color = theme.textSecondary)
                                            Text("${beacon.major} / ${beacon.minor}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.primary)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("TxPower (1m)", fontSize = 11.sp, color = theme.textSecondary)
                                            Text("${beacon.txPower} dBm", fontSize = 12.sp, color = theme.textSecondary)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Duplicate Notice
                                if (duplicateBeacon != null) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF59E0B).copy(alpha = 0.12f)),
                                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Warning,
                                                contentDescription = null,
                                                tint = Color(0xFFF59E0B),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = "This beacon identity (${beacon.major}/${beacon.minor}) is already registered as '${duplicateBeacon?.name}'. Saving will update its hardware MAC address.",
                                                fontSize = 11.5.sp,
                                                color = theme.textPrimary,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                // Trigger Name Input
                                OutlinedTextField(
                                    value = customName,
                                    onValueChange = { customName = it },
                                    label = { Text("Trigger Name") },
                                    placeholder = { Text("e.g. Desk Beacon, Entryway") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        val finalName = customName.trim().ifBlank { "ContextPulse BLE Beacon ${beacon.minor}" }
                                        val existing = duplicateBeacon
                                        val triggerToSave = RegisteredBeacon(
                                            id = existing?.id ?: "trigger_${System.currentTimeMillis() % 100000}",
                                            name = finalName,
                                            type = TriggerType.BLE_BEACON,
                                            macAddress = beacon.macAddress,
                                            namespaceOrUuid = beacon.uuid,
                                            instanceOrMajorMinor = "${beacon.major} / ${beacon.minor}",
                                            assignedRoomId = existing?.assignedRoomId ?: "",
                                            assignedRoomName = existing?.assignedRoomName ?: "",
                                            enterRssiThreshold = -65,
                                            exitRssiThreshold = -80,
                                            batteryPercent = tlmBatteryByMac[beacon.macAddress.uppercase()] ?: existing?.batteryPercent,
                                            isReachable = true
                                        )
                                        beaconRepo.saveTrigger(triggerToSave)
                                        onBeaconSaved(triggerToSave)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF10B981),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Save Unassigned Trigger", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    else -> {
                        // Scanning / detected in-progress
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, theme.border)
                        ) {
                            Text("Cancel", color = theme.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(
    status: PairingStatus,
    consecutiveSamples: Int,
    theme: com.contextpulse.phase0.ui.theme.AppThemeKey
) {
    val (bgColor, borderColor, textColor, message) = when (status) {
        PairingStatus.SCANNING -> Quad(
            theme.primary.copy(alpha = 0.12f),
            theme.primary.copy(alpha = 0.3f),
            theme.primary,
            "Bring your ContextPulse beacon close to the phone (10–20 cm)"
        )
        PairingStatus.BEACON_DETECTED -> Quad(
            Color(0xFFF59E0B).copy(alpha = 0.12f),
            Color(0xFFF59E0B).copy(alpha = 0.35f),
            Color(0xFFF59E0B),
            "Beacon detected! Bring it closer to confirm pairing..."
        )
        PairingStatus.VERY_CLOSE -> Quad(
            Color(0xFF10B981).copy(alpha = 0.15f),
            Color(0xFF10B981).copy(alpha = 0.4f),
            Color(0xFF10B981),
            "Very close! Hold steady ($consecutiveSamples/3)..."
        )
        PairingStatus.BEACON_FOUND -> Quad(
            Color(0xFF10B981).copy(alpha = 0.18f),
            Color(0xFF10B981).copy(alpha = 0.6f),
            Color(0xFF10B981),
            "BLE Beacon Found! Review and save trigger below."
        )
        PairingStatus.SIGNAL_LOST -> Quad(
            Color(0xFFEF4444).copy(alpha = 0.12f),
            Color(0xFFEF4444).copy(alpha = 0.35f),
            Color(0xFFEF4444),
            "Signal lost. Reposition beacon near phone..."
        )
        PairingStatus.PERMISSION_REQUIRED -> Quad(
            Color(0xFFEF4444).copy(alpha = 0.12f),
            Color(0xFFEF4444).copy(alpha = 0.35f),
            Color(0xFFEF4444),
            "Bluetooth and Location permissions are required to scan."
        )
        PairingStatus.BLUETOOTH_DISABLED -> Quad(
            Color(0xFFF59E0B).copy(alpha = 0.12f),
            Color(0xFFF59E0B).copy(alpha = 0.35f),
            Color(0xFFF59E0B),
            "Bluetooth is turned off on this device."
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RadarPulseView(
    status: PairingStatus,
    smoothedRssi: Double,
    estimatedDistance: Double,
    consecutiveSamples: Int,
    theme: com.contextpulse.phase0.ui.theme.AppThemeKey
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarTransition")

    // Expanding concentric ripple animations
    val ripple1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Ripple1"
    )
    val ripple2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 730, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Ripple2"
    )
    val ripple3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 1460, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Ripple3"
    )

    // Rotating sweep hand
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Sweep"
    )

    val activeColor = when (status) {
        PairingStatus.BEACON_FOUND -> Color(0xFF10B981)
        PairingStatus.VERY_CLOSE -> Color(0xFF10B981)
        PairingStatus.BEACON_DETECTED -> Color(0xFFF59E0B)
        PairingStatus.SIGNAL_LOST -> Color(0xFFEF4444)
        else -> theme.primary
    }

    val radarBg = theme.surfaceVariant.copy(alpha = 0.5f)
    val radarBorder = theme.border

    Box(
        modifier = Modifier
            .size(190.dp)
            .clip(CircleShape)
            .background(radarBg)
            .border(1.5.dp, radarBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.width / 2f

            // Static range grid circles
            val rings = listOf(0.25f, 0.50f, 0.75f, 1.0f)
            rings.forEach { ratio ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.07f),
                    radius = maxRadius * ratio,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Radar crosshairs
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(center.x, 0f),
                end = Offset(center.x, size.height),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(0f, center.y),
                end = Offset(size.width, center.y),
                strokeWidth = 1.dp.toPx()
            )

            // Animated concentric ripples when scanning / detected
            if (status != PairingStatus.BEACON_FOUND) {
                listOf(ripple1, ripple2, ripple3).forEach { r ->
                    val radius = maxRadius * r
                    val alpha = (1f - r).coerceIn(0f, 1f) * 0.45f
                    drawCircle(
                        color = activeColor.copy(alpha = alpha),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Rotating sweep line
                val rad = Math.toRadians(sweepAngle.toDouble())
                val endX = center.x + (maxRadius * cos(rad)).toFloat()
                val endY = center.y + (maxRadius * sin(rad)).toFloat()
                drawLine(
                    color = activeColor.copy(alpha = 0.35f),
                    start = center,
                    end = Offset(endX, endY),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            } else {
                // Static solid lock circle when found
                drawCircle(
                    color = Color(0xFF10B981).copy(alpha = 0.25f),
                    radius = maxRadius * 0.85f,
                    center = center
                )
                drawCircle(
                    color = Color(0xFF10B981),
                    radius = maxRadius * 0.85f,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        // Center Icon & Proximity feedback
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(activeColor.copy(alpha = 0.2f))
                    .border(2.dp, activeColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (status) {
                        PairingStatus.BEACON_FOUND -> Icons.Default.Check
                        PairingStatus.VERY_CLOSE -> Icons.Outlined.Sensors
                        PairingStatus.BEACON_DETECTED -> Icons.Outlined.BluetoothSearching
                        else -> Icons.Outlined.Bluetooth
                    },
                    contentDescription = null,
                    tint = activeColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            if (status == PairingStatus.VERY_CLOSE || status == PairingStatus.BEACON_DETECTED) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${smoothedRssi.toInt()} dBm",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = activeColor,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
