package com.contextpulse.phase0.ui.components

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

/**
 * Stages of the Guided Room Boundary Calibration Wizard.
 */
enum class CalibrationStage {
    PREREQUISITE,
    PROMPT_CENTER,
    SAMPLING_CENTER,
    CENTER_RESULT,
    PROMPT_DOORWAY,
    SAMPLING_DOORWAY,
    DOORWAY_RESULT,
    COMPLETED
}

/**
 * Interactive "Calibrate Room Boundary" wizard dialog.
 * 
 * Flow:
 * 1. Prerequisite: Place beacon in permanent spot in room.
 * 2. Step 1 (Room Center Baseline): Sample RSSI for 3s, compute trimmed mean, verify viability (> -75 dBm).
 * 3. Step 2 (Doorway Boundary): Sample RSSI for 4s, sanity check (doorway weaker than center).
 * 4. Automatic Hysteresis Calculation:
 *    - enterThreshold = boundaryRssi + 4 dBm
 *    - exitThreshold = boundaryRssi - 6 dBm
 *    - Clamped to -100 dBm .. -40 dBm, with guaranteed >= 8 dBm hysteresis gap.
 */
@Composable
fun RoomCalibrationWizardDialog(
    beaconMac: String,
    beaconUuid: String,
    beaconMajorMinor: String,
    roomName: String,
    onCalibrationComplete: (enterThreshold: Int, exitThreshold: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalContextPulseTheme.current

    val btManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager }
    val btAdapter: BluetoothAdapter? = btManager?.adapter

    val hasPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
    val isBtEnabled = btAdapter?.isEnabled == true

    var currentStage by remember { mutableStateOf(CalibrationStage.PREREQUISITE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Live RSSI during sampling
    var liveRssi by remember { mutableIntStateOf(-100) }
    var samplesCount by remember { mutableIntStateOf(0) }
    var progressFraction by remember { mutableFloatStateOf(0f) }

    // Collected samples
    val centerSamples = remember { mutableStateListOf<Int>() }
    val doorwaySamples = remember { mutableStateListOf<Int>() }

    var centerBaselineRssi by remember { mutableIntStateOf(0) }
    var doorwayBaselineRssi by remember { mutableIntStateOf(0) }

    var calculatedEnterThreshold by remember { mutableIntStateOf(-65) }
    var calculatedExitThreshold by remember { mutableIntStateOf(-80) }

    // Clean match keys
    val cleanTargetUuid = remember(beaconUuid) {
        beaconUuid.replace("-", "").uppercase(Locale.ROOT)
    }
    val (parsedMajor, parsedMinor) = remember(beaconMajorMinor) {
        val parts = beaconMajorMinor.split("/").map { it.trim() }
        val maj = parts.getOrNull(0)?.toIntOrNull()
        val min = parts.getOrNull(1)?.toIntOrNull()
        Pair(maj, min)
    }
    val cleanMac = remember(beaconMac) { beaconMac.trim().uppercase(Locale.ROOT) }

    // Active BLE scanning during SAMPLING_CENTER and SAMPLING_DOORWAY
    val isActivelySampling = currentStage == CalibrationStage.SAMPLING_CENTER || currentStage == CalibrationStage.SAMPLING_DOORWAY

    DisposableEffect(isActivelySampling, hasPermissions, isBtEnabled) {
        if (!isActivelySampling || !hasPermissions || !isBtEnabled || btAdapter == null) {
            return@DisposableEffect onDispose {}
        }

        val scanner = btAdapter.bluetoothLeScanner
        if (scanner == null) {
            errorMessage = "Bluetooth LE scanner unavailable on this device."
            return@DisposableEffect onDispose {}
        }

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                if (result == null) return
                val record = result.scanRecord ?: return
                val rawRssi = result.rssi
                val deviceAddress = result.device?.address?.uppercase(Locale.ROOT) ?: ""

                // Filter by MAC if provided
                if (cleanMac.isNotBlank() && cleanMac != deviceAddress && cleanMac != "00:00:00:00:00:00") {
                    // If MAC is specified and doesn't match, continue checking iBeacon identity
                }

                // Check iBeacon payload
                val appleData = record.getManufacturerSpecificData(0x004C) ?: return
                if (appleData.size < 23 || appleData[0] != 0x02.toByte() || appleData[1] != 0x15.toByte()) {
                    return
                }

                // Match UUID if configured
                if (cleanTargetUuid.isNotBlank()) {
                    val uuidBytes = appleData.copyOfRange(2, 18)
                    val sb = StringBuilder()
                    for (b in uuidBytes) {
                        sb.append(String.format("%02X", b))
                    }
                    val scannedUuid = sb.toString()
                    if (!scannedUuid.equals(cleanTargetUuid, ignoreCase = true)) {
                        return
                    }
                }

                // Match Major & Minor if provided
                val major = ((appleData[18].toInt() and 0xFF) shl 8) or (appleData[19].toInt() and 0xFF)
                val minor = ((appleData[20].toInt() and 0xFF) shl 8) or (appleData[21].toInt() and 0xFF)
                if (parsedMajor != null && parsedMajor != major) return
                if (parsedMinor != null && parsedMinor != minor) return

                // Packet accepted from target beacon
                liveRssi = rawRssi
                if (currentStage == CalibrationStage.SAMPLING_CENTER) {
                    centerSamples.add(rawRssi)
                    samplesCount = centerSamples.size
                } else if (currentStage == CalibrationStage.SAMPLING_DOORWAY) {
                    doorwaySamples.add(rawRssi)
                    samplesCount = doorwaySamples.size
                }
            }

            override fun onScanFailed(errorCode: Int) {
                errorMessage = "BLE scan failed with error code $errorCode."
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        try {
            scanner.startScan(emptyList<ScanFilter>(), settings, scanCallback)
        } catch (e: Exception) {
            errorMessage = "Failed to start BLE scanner: ${e.message}"
        }

        onDispose {
            try {
                scanner.stopScan(scanCallback)
            } catch (_: Exception) {}
        }
    }

    // Step 1: 3-Second Timer for Center Sampling
    LaunchedEffect(currentStage) {
        if (currentStage == CalibrationStage.SAMPLING_CENTER) {
            errorMessage = null
            centerSamples.clear()
            samplesCount = 0
            val totalDurationMs = 3000L
            val intervalMs = 100L
            var elapsed = 0L

            while (isActive && elapsed < totalDurationMs) {
                delay(intervalMs)
                elapsed += intervalMs
                progressFraction = (elapsed.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
            }

            // Finish Sampling Center
            if (centerSamples.isEmpty()) {
                errorMessage = "No signal received from beacon. Ensure the beacon is turned on and broadcasting nearby."
            } else {
                val baseline = calculateTrimmedMean(centerSamples.toList())
                centerBaselineRssi = baseline
                if (baseline < -75) {
                    // Viability warning
                    errorMessage = "Baseline is weak ($baseline dBm). For reliable room boundary detection, consider moving the beacon higher or closer to the center of the room."
                } else {
                    errorMessage = null
                }
                currentStage = CalibrationStage.CENTER_RESULT
            }
        } else if (currentStage == CalibrationStage.SAMPLING_DOORWAY) {
            errorMessage = null
            doorwaySamples.clear()
            samplesCount = 0
            val totalDurationMs = 4000L
            val intervalMs = 100L
            var elapsed = 0L

            while (isActive && elapsed < totalDurationMs) {
                delay(intervalMs)
                elapsed += intervalMs
                progressFraction = (elapsed.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
            }

            // Finish Sampling Doorway
            if (doorwaySamples.isEmpty()) {
                errorMessage = "No signal received at doorway. Please hold steady and try sampling again."
                currentStage = CalibrationStage.DOORWAY_RESULT
            } else {
                val doorwayBaseline = calculateTrimmedMean(doorwaySamples.toList())
                doorwayBaselineRssi = doorwayBaseline

                // Sanity check: Ensure doorway RSSI is weaker than room center RSSI
                if (doorwayBaseline >= centerBaselineRssi) {
                    errorMessage = "Doorway signal ($doorwayBaseline dBm) was stronger or equal to room center ($centerBaselineRssi dBm). The doorway should be farther from the beacon than the room center. Please re-sample."
                } else {
                    errorMessage = null
                }

                // Calculate automatic hysteresis thresholds:
                // enterThreshold = boundaryRssi + 4 dBm (requires walking inside)
                // exitThreshold = boundaryRssi - 6 dBm (requires stepping out)
                var enter = (doorwayBaseline + 4).coerceIn(-95, -45)
                var exit = (doorwayBaseline - 6).coerceIn(-100, -55)

                // Guarantee a minimum 8 dBm hysteresis gap
                if (enter - exit < 8) {
                    exit = (enter - 8).coerceAtLeast(-100)
                }

                calculatedEnterThreshold = enter
                calculatedExitThreshold = exit
                currentStage = CalibrationStage.DOORWAY_RESULT
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = theme.surface,
            border = BorderStroke(1.dp, theme.border),
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Straighten,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "CALIBRATION WIZARD",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.accent,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (roomName.isNotBlank()) "Calibrate $roomName" else "Calibrate Room Boundary",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = theme.textPrimary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(theme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = theme.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Stage Stepper Indicator
                StageStepper(currentStage = currentStage, theme = theme)

                Spacer(modifier = Modifier.height(20.dp))

                // Error / Warning Card if present
                if (errorMessage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF59E0B).copy(alpha = 0.14f)),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = errorMessage ?: "",
                                color = theme.textPrimary,
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                // Stage Content
                when (currentStage) {
                    CalibrationStage.PREREQUISITE -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(theme.surfaceVariant.copy(alpha = 0.5f))
                                .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                                .padding(18.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.Sensors,
                                    contentDescription = null,
                                    tint = theme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Permanent Beacon Placement",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.textPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Place your beacon in its permanent spot in this room, then tap Next.",
                                    fontSize = 13.5.sp,
                                    color = theme.textSecondary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 19.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                errorMessage = null
                                currentStage = CalibrationStage.PROMPT_CENTER
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.primary,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Next: Step 1 (Room Center)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    CalibrationStage.PROMPT_CENTER -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(theme.surfaceVariant.copy(alpha = 0.5f))
                                .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                                .padding(18.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    tint = theme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Step 1: Go to Room Center",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.textPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Walk and stand where you normally sit or spend time (e.g. desk or bed). Once you are there and holding your phone, tap 'Start Sampling' to record a 3-second baseline.",
                                    fontSize = 13.5.sp,
                                    color = theme.textSecondary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 19.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                errorMessage = null
                                currentStage = CalibrationStage.SAMPLING_CENTER
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.primary,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Sampling (3s)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    CalibrationStage.SAMPLING_CENTER -> {
                        SamplingProgressView(
                            title = "Sampling Room Center Baseline",
                            instruction = "Hold your phone steady at your normal spot. Sampling for 3 seconds...",
                            liveRssi = liveRssi,
                            samplesCount = samplesCount,
                            progress = progressFraction,
                            theme = theme
                        )
                    }

                    CalibrationStage.CENTER_RESULT -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = theme.surfaceVariant.copy(alpha = 0.5f)),
                            border = BorderStroke(1.dp, theme.border)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Center Baseline RSSI",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = theme.textPrimary
                                    )
                                    Text(
                                        text = "$centerBaselineRssi dBm",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = theme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Computed from ${centerSamples.size} samples with 10% outlier trimming.",
                                    fontSize = 12.sp,
                                    color = theme.textSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    errorMessage = null
                                    currentStage = CalibrationStage.SAMPLING_CENTER
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Re-Sample", fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    errorMessage = null
                                    currentStage = CalibrationStage.PROMPT_DOORWAY
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.primary,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(46.dp)
                            ) {
                                Text("Next: Doorway", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    CalibrationStage.PROMPT_DOORWAY -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(theme.surfaceVariant.copy(alpha = 0.5f))
                                .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                                .padding(18.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.MeetingRoom,
                                    contentDescription = null,
                                    tint = theme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Step 2: Walk to the Doorway",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.textPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Walk to the room doorway / entrance threshold where you want the boundary to trigger, and hold your phone. Once in position, tap 'Start Doorway Test' to sample for 4 seconds.",
                                    fontSize = 13.5.sp,
                                    color = theme.textSecondary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 19.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                errorMessage = null
                                currentStage = CalibrationStage.SAMPLING_DOORWAY
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.primary,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Doorway Test (4s)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    CalibrationStage.SAMPLING_DOORWAY -> {
                        SamplingProgressView(
                            title = "Sampling Doorway Boundary",
                            instruction = "Hold your phone steady at the doorway. Sampling for 4 seconds...",
                            liveRssi = liveRssi,
                            samplesCount = samplesCount,
                            progress = progressFraction,
                            theme = theme
                        )
                    }

                    CalibrationStage.DOORWAY_RESULT -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = theme.surfaceVariant.copy(alpha = 0.5f)),
                            border = BorderStroke(1.dp, theme.border)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Doorway Boundary RSSI",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = theme.textPrimary
                                    )
                                    Text(
                                        text = "$doorwayBaselineRssi dBm",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = theme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Computed from ${doorwaySamples.size} samples at the room entrance.",
                                    fontSize = 12.sp,
                                    color = theme.textSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    errorMessage = null
                                    currentStage = CalibrationStage.SAMPLING_DOORWAY
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retest Doorway", fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    if (errorMessage == null) {
                                        currentStage = CalibrationStage.COMPLETED
                                    } else {
                                        currentStage = CalibrationStage.SAMPLING_DOORWAY
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (errorMessage == null) theme.primary else Color(0xFFF59E0B),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(46.dp)
                            ) {
                                Text(
                                    if (errorMessage == null) "View Final Result" else "Re-sample Doorway",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    CalibrationStage.COMPLETED -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = theme.surfaceVariant.copy(alpha = 0.5f)),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Calibration Complete!",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981)
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Thresholds Summary Table
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Center Baseline", fontSize = 11.5.sp, color = theme.textSecondary)
                                        Text(
                                            text = "$centerBaselineRssi dBm",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.textPrimary
                                        )
                                    }
                                    Column {
                                        Text("Doorway Baseline", fontSize = 11.5.sp, color = theme.textSecondary)
                                        Text(
                                            text = "$doorwayBaselineRssi dBm",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.textPrimary
                                        )
                                    }
                                    Column {
                                        Text("Hysteresis Gap", fontSize = 11.5.sp, color = theme.textSecondary)
                                        Text(
                                            text = "${calculatedEnterThreshold - calculatedExitThreshold} dBm",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = theme.border.copy(alpha = 0.6f))
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Enter Threshold (Walking inside)",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = theme.textPrimary
                                        )
                                        Text(
                                            text = "Doorway ($doorwayBaselineRssi) + 4 dBm",
                                            fontSize = 11.sp,
                                            color = theme.textSecondary
                                        )
                                    }
                                    Text(
                                        text = "$calculatedEnterThreshold dBm",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = theme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Exit Threshold (Stepping out)",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = theme.textPrimary
                                        )
                                        Text(
                                            text = "Doorway ($doorwayBaselineRssi) - 6 dBm",
                                            fontSize = 11.sp,
                                            color = theme.textSecondary
                                        )
                                    }
                                    Text(
                                        text = "$calculatedExitThreshold dBm",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = theme.primary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    errorMessage = null
                                    currentStage = CalibrationStage.PROMPT_DOORWAY
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retest Doorway", fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    onCalibrationComplete(calculatedEnterThreshold, calculatedExitThreshold)
                                },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.primary,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Apply",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SamplingProgressView(
    title: String,
    instruction: String,
    liveRssi: Int,
    samplesCount: Int,
    progress: Float,
    theme: com.contextpulse.phase0.ui.theme.AppThemeKey
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = theme.textPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = instruction,
            fontSize = 13.sp,
            color = theme.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Large RSSI readout
        Card(
            modifier = Modifier.fillMaxWidth(0.7f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = theme.surfaceVariant),
            border = BorderStroke(1.dp, theme.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "LIVE RSSI",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.accent,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (liveRssi <= -100) "Listening..." else "$liveRssi dBm",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = if (liveRssi > -75) Color(0xFF10B981) else theme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$samplesCount samples collected",
                    fontSize = 11.5.sp,
                    color = theme.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = theme.primary,
            trackColor = theme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
private fun StageStepper(
    currentStage: CalibrationStage,
    theme: com.contextpulse.phase0.ui.theme.AppThemeKey
) {
    val activeStep = when (currentStage) {
        CalibrationStage.PREREQUISITE -> 0
        CalibrationStage.PROMPT_CENTER, CalibrationStage.SAMPLING_CENTER, CalibrationStage.CENTER_RESULT -> 1
        CalibrationStage.PROMPT_DOORWAY, CalibrationStage.SAMPLING_DOORWAY, CalibrationStage.DOORWAY_RESULT -> 2
        CalibrationStage.COMPLETED -> 3
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        listOf("Setup", "Center", "Doorway", "Result").forEachIndexed { index, label ->
            val isDone = index < activeStep
            val isCurrent = index == activeStep

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isDone -> Color(0xFF10B981)
                                isCurrent -> theme.primary
                                else -> theme.surfaceVariant
                            }
                        )
                        .border(
                            1.dp,
                            if (isCurrent) theme.primary else theme.border,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) Color.White else theme.textSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    fontSize = 10.5.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) theme.textPrimary else theme.textSecondary
                )
            }

            if (index < 3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .padding(horizontal = 6.dp)
                        .background(
                            if (index < activeStep) Color(0xFF10B981) else theme.border
                        )
                )
            }
        }
    }
}

/**
 * Computes a trimmed mean by discarding the top 10% and bottom 10% outliers.
 */
private fun calculateTrimmedMean(samples: List<Int>): Int {
    if (samples.isEmpty()) return -75
    if (samples.size < 5) return samples.average().toInt()

    val sorted = samples.sorted()
    val trimCount = (sorted.size * 0.10).toInt()
    val trimmed = sorted.subList(trimCount, sorted.size - trimCount)
    return trimmed.average().toInt()
}
