package com.contextpulse.phase0.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contextpulse.phase0.model.TriggerItem
import com.contextpulse.phase0.model.TriggerType
import com.contextpulse.phase0.repository.BeaconRepository
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme
import com.contextpulse.phase0.util.QrCodeUtil
import java.util.UUID

@Composable
fun ConfigureTriggerScreen(
    trigger: TriggerItem,
    beaconRepo: BeaconRepository,
    onSaveTrigger: (TriggerItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalContextPulseTheme.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var name by remember { mutableStateOf(trigger.name) }
    var selectedType by remember {
        mutableStateOf(
            if (trigger.type == TriggerType.UNCONFIGURED) TriggerType.BLE_BEACON else trigger.type
        )
    }
    var code by remember {
        mutableStateOf(
            if (trigger.code.isBlank()) "CP-${UUID.randomUUID().toString().take(4).uppercase()}" else trigger.code
        )
    }
    var macAddress by remember {
        mutableStateOf(
            if (trigger.macAddress.isBlank()) "AA:BB:CC:11:22:${UUID.randomUUID().toString().take(2).uppercase()}" else trigger.macAddress
        )
    }
    var nfcTagUid by remember {
        mutableStateOf(
            if (trigger.nfcTagUid.isBlank()) "04:${UUID.randomUUID().toString().take(6).uppercase()}" else trigger.nfcTagUid
        )
    }
    var qrPayload by remember {
        mutableStateOf(
            if (trigger.qrPayload.isBlank() || trigger.qrPayload.startsWith("contextpulse://")) {
                "https://contextpulse.app/trigger/${trigger.id}"
            } else {
                trigger.qrPayload
            }
        )
    }
    var batteryPercent by remember { mutableStateOf(trigger.batteryPercent) }
    var isOffline by remember { mutableStateOf(trigger.isOffline) }

    // Duplicate name check
    val isDuplicate = remember(name) {
        name.isNotBlank() && beaconRepo.isDuplicateName(name, excludeId = trigger.id)
    }

    // QR Code Bitmap state
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(qrPayload, selectedType) {
        if (selectedType == TriggerType.QR_CODE) {
            qrBitmap = QrCodeUtil.generateQrBitmap(qrPayload, size = 512)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(theme.background)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.surface)
                        .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = theme.textPrimary
                    )
                }

                Column {
                    Text(
                        text = "Configure Trigger",
                        color = theme.textPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Set Up Detection Hardware",
                        color = theme.textSecondary,
                        fontSize = 12.5.sp
                    )
                }
            }

            Button(
                onClick = {
                    if (name.isBlank()) {
                        Toast.makeText(context, "Please enter a trigger name", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (isDuplicate) {
                        Toast.makeText(context, "A trigger with this name already exists", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val updated = trigger.copy(
                        name = name.trim(),
                        type = selectedType,
                        isConfigured = true,
                        code = code.trim(),
                        macAddress = macAddress.trim(),
                        nfcTagUid = nfcTagUid.trim(),
                        qrPayload = qrPayload.trim(),
                        batteryPercent = batteryPercent,
                        isOffline = isOffline
                    )
                    onSaveTrigger(updated)
                    Toast.makeText(context, "Trigger configured successfully", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                enabled = name.isNotBlank() && !isDuplicate,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.primary,
                    contentColor = theme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save", fontWeight = FontWeight.Bold)
            }
        }

        Divider(color = theme.border, thickness = 1.dp)

        // Scrollable Form Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section 1: Trigger Name
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Trigger Name",
                    color = theme.textPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("e.g. Office Door Beacon, Bedside NFC") },
                    singleLine = true,
                    isError = isDuplicate,
                    supportingText = {
                        if (isDuplicate) {
                            Text("A trigger with this name already exists. Choose a unique name.", color = Color(0xFFE53935))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.primary,
                        unfocusedBorderColor = theme.border,
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary
                    )
                )
            }

            // Section 2: Connection Method (BLE Beacon, NFC Chip, Room QR Code)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Connection Method",
                    color = theme.textPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )

                val methods = listOf(
                    Triple(TriggerType.BLE_BEACON, "BLE Beacon Proximity", Icons.Outlined.Bluetooth),
                    Triple(TriggerType.NFC_TAG, "NFC Chip Tag", Icons.Default.Nfc),
                    Triple(TriggerType.QR_CODE, "Room QR Code", Icons.Default.QrCode2)
                )

                methods.forEach { (type, label, icon) ->
                    val isSelected = selectedType == type
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) theme.primary.copy(alpha = 0.12f) else theme.surface)
                            .border(
                                1.5.dp,
                                if (isSelected) theme.primary else theme.border,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { selectedType = type }
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) theme.primary else theme.border.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) theme.onPrimary else theme.textSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    color = theme.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when (type) {
                                        TriggerType.BLE_BEACON -> "Continuous proximity sensing via Bluetooth iBeacon / Eddystone"
                                        TriggerType.NFC_TAG -> "Instant physical tap to trigger profile via NTAG / Mifare chip"
                                        TriggerType.QR_CODE -> "Printable room wall QR code for quick camera optical trigger"
                                        else -> ""
                                    },
                                    color = theme.textSecondary,
                                    fontSize = 12.sp
                                )
                            }

                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedType = type },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = theme.primary,
                                    unselectedColor = theme.textSecondary
                                )
                            )
                        }
                    }
                }
            }

            // Section 3: Hardware Parameters based on selected connection type
            when (selectedType) {
                TriggerType.BLE_BEACON -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(theme.surface)
                            .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "BLE Beacon Parameters",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )

                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            label = { Text("Hardware Code / Major ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = macAddress,
                            onValueChange = { macAddress = it },
                            label = { Text("Hardware MAC Address / UUID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Battery slider
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Simulated Beacon Battery",
                                    fontSize = 13.5.sp,
                                    color = theme.textSecondary
                                )
                                Text(
                                    text = "$batteryPercent%",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (batteryPercent <= 20) Color(0xFFF59E0B) else theme.primary
                                )
                            }
                            Slider(
                                value = batteryPercent.toFloat(),
                                onValueChange = { batteryPercent = it.toInt() },
                                valueRange = 0f..100f,
                                colors = SliderDefaults.colors(
                                    thumbColor = theme.primary,
                                    activeTrackColor = theme.primary
                                )
                            )
                            if (batteryPercent <= 20) {
                                Text(
                                    text = "Low battery warning will alert the user automatically.",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFFF59E0B)
                                )
                            }
                        }

                        // Broadcast status toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Broadcasting Signal",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = theme.textPrimary
                                )
                                Text(
                                    text = if (!isOffline) "Transmitting BLE packets" else "Beacon radio stopped",
                                    fontSize = 12.sp,
                                    color = theme.textSecondary
                                )
                            }
                            Switch(
                                checked = !isOffline,
                                onCheckedChange = { isOffline = !it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = theme.onPrimary,
                                    checkedTrackColor = theme.primary
                                )
                            )
                        }
                    }
                }

                TriggerType.NFC_TAG -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(theme.surface)
                            .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "NFC Tag Parameters",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )

                        OutlinedTextField(
                            value = nfcTagUid,
                            onValueChange = { nfcTagUid = it },
                            label = { Text("NFC Chip UID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.background)
                                .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Payload Identifier",
                                    fontSize = 12.sp,
                                    color = theme.textSecondary
                                )
                                Text(
                                    text = "contextpulse://trigger/${trigger.id}",
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = theme.primary
                                )
                            }
                        }

                        Button(
                            onClick = {
                                Toast.makeText(context, "Ready to write NFC chip. Tap tag to device.", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.primary.copy(alpha = 0.15f),
                                contentColor = theme.primary
                            )
                        ) {
                            Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ready To Scan / Write Tag", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                TriggerType.QR_CODE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(theme.surface)
                            .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Room QR Code",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )

                        if (qrBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .size(200.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White)
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = qrBitmap!!.asImageBitmap(),
                                    contentDescription = "Room QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            CircularProgressIndicator(color = theme.primary)
                        }

                        Text(
                            text = "Print and mount this QR code near the doorway, desk, or bedside. Scanning it with any camera or QR app will immediately activate the assigned context profile.",
                            fontSize = 12.5.sp,
                            color = theme.textSecondary,
                            textAlign = TextAlign.Center
                        )

                        // Deep Link display
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.background)
                                .border(1.dp, theme.border, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = qrPayload,
                                fontSize = 11.5.sp,
                                color = theme.primary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val assigned = trigger.assignedRoomName
                                    if (!assigned.isNullOrBlank()) {
                                        val roomRepo = com.contextpulse.phase0.repository.RoomProfileRepository.getInstance(context)
                                        val target = roomRepo.rooms.value.find { it.name.equals(assigned.trim(), ignoreCase = true) }
                                        if (target != null) {
                                            com.contextpulse.phase0.system.ProfileActivationHelper.activateProfile(
                                                context = context,
                                                roomId = target.id,
                                                triggerSource = com.contextpulse.phase0.model.TriggerSource.QR_CODE,
                                                reason = "QR Code Test: ${trigger.name}"
                                            )
                                            Toast.makeText(context, "QR Trigger Verified: Activated '$assigned'", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Context '$assigned' not found", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Attach this trigger to a context first to test activation.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, theme.primary)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test Scan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    if (qrBitmap != null) {
                                        QrCodeUtil.shareQrCode(context, qrBitmap!!, name)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.primary,
                                    contentColor = theme.onPrimary
                                )
                            ) {
                                Icon(Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Print / Share", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                else -> {}
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
