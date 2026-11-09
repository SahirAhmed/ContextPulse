package com.contextpulse.phase0.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contextpulse.phase0.model.RegisteredBeacon
import com.contextpulse.phase0.model.TriggerSource
import com.contextpulse.phase0.model.TriggerType
import com.contextpulse.phase0.repository.BeaconRepository
import com.contextpulse.phase0.repository.PermissionsRepository
import com.contextpulse.phase0.repository.RoomProfileRepository
import com.contextpulse.phase0.system.ProfileActivationHelper
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme
import com.contextpulse.phase0.util.QrCodeUtil

@Composable
fun ConfigureTriggerScreen(
    trigger: RegisteredBeacon,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val theme = LocalContextPulseTheme.current
    val beaconRepository = remember { BeaconRepository.getInstance(context) }
    val permissionsRepo = remember { com.contextpulse.phase0.repository.PermissionsRepository.getInstance(context) }
    val permState by permissionsRepo.state.collectAsState()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                permissionsRepo.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val roomRepository = remember { RoomProfileRepository.getInstance(context) }
    val rooms by roomRepository.rooms.collectAsState()

    var nameText by remember(trigger.id) { mutableStateOf(trigger.name) }
    var selectedType by remember(trigger.id) { mutableStateOf(trigger.type) }
    var macText by remember(trigger.id) { mutableStateOf(trigger.macAddress) }
    var uuidText by remember(trigger.id) { mutableStateOf(trigger.namespaceOrUuid) }
    var instanceText by remember(trigger.id) { mutableStateOf(trigger.instanceOrMajorMinor) }
    var nfcTagIdText by remember(trigger.id) {
        mutableStateOf(trigger.nfcTagId.ifBlank { "04:A1:2B:${trigger.id.takeLast(4).uppercase()}" })
    }

    // Default to GitHub Pages AppLinks URL for sahirahmed.github.io
    val defaultGitHubBridgeUrl = remember(trigger.id) {
        "https://sahirahmed.github.io/ContextPulse/?trigger=${trigger.id}"
    }

    var selectedRoomId by remember(trigger.id, rooms) {
        val initial = if (trigger.assignedRoomId.isNotBlank()) {
            trigger.assignedRoomId
        } else if (trigger.assignedRoomName.isNotBlank()) {
            rooms.find { it.name.equals(trigger.assignedRoomName, ignoreCase = true) }?.id ?: ""
        } else {
            ""
        }
        mutableStateOf(initial)
    }
    var enterRssi by remember(trigger.id) { mutableFloatStateOf(trigger.enterRssiThreshold.toFloat()) }
    var exitRssi by remember(trigger.id) { mutableFloatStateOf(trigger.exitRssiThreshold.toFloat()) }

    var showBlePermissionDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState)
    ) {
        // Top Bar - strictly aligned with ContextPulse top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        tint = theme.primary
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "TRIGGER SETUP",
                        color = theme.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = nameText.ifBlank { "Configure Trigger" },
                        color = theme.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Button(
                onClick = {
                    if (selectedType == TriggerType.BLE_BEACON && !permissionsRepo.isBlePermissionsGranted()) {
                        showBlePermissionDialog = true
                        return@Button
                    }
                    val isQrCode = selectedType == TriggerType.QR_CODE
                    val targetRoom = if (isQrCode) null else rooms.find { it.id == selectedRoomId }
                    val updated = trigger.copy(
                        name = nameText.trim().ifBlank { "Room Trigger" },
                        type = selectedType,
                        macAddress = macText.trim(),
                        namespaceOrUuid = uuidText.trim(),
                        instanceOrMajorMinor = instanceText.trim(),
                        nfcTagId = nfcTagIdText.trim(),
                        qrPayload = defaultGitHubBridgeUrl,
                        assignedRoomId = if (isQrCode) trigger.assignedRoomId else (targetRoom?.id ?: ""),
                        assignedRoomName = if (isQrCode) trigger.assignedRoomName else (targetRoom?.name ?: ""),
                        enterRssiThreshold = enterRssi.toInt(),
                        exitRssiThreshold = exitRssi.toInt()
                    )
                    beaconRepository.saveOrUpdateBeacon(updated)
                    Toast.makeText(
                        context,
                        "Saved '${updated.name}' trigger",
                        Toast.LENGTH_SHORT
                    ).show()
                    onSaved()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.primary,
                    contentColor = theme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 1. Trigger Type Selector
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = theme.surface),
            border = BorderStroke(1.dp, theme.border)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "TRIGGER TYPE",
                    color = theme.tertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TriggerType.entries.filter { it != TriggerType.UNCONFIGURED }.forEach { typeOption ->
                        val isSelected = selectedType == typeOption
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) theme.primary.copy(alpha = 0.16f) else theme.surfaceVariant
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) theme.primary else theme.border,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    if (typeOption == TriggerType.BLE_BEACON && !permissionsRepo.isBlePermissionsGranted()) {
                                        showBlePermissionDialog = true
                                    }
                                    selectedType = typeOption
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = when (typeOption) {
                                        TriggerType.BLE_BEACON, TriggerType.UNCONFIGURED -> Icons.Outlined.BluetoothSearching
                                        TriggerType.NFC_TAG -> Icons.Outlined.Nfc
                                        TriggerType.QR_CODE -> Icons.Outlined.QrCode2
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) theme.primary else theme.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = typeOption.label,
                                    color = if (isSelected) theme.primary else theme.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Trigger Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.primary,
                        unfocusedBorderColor = theme.border,
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Linked Context Assignment
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = theme.surface),
            border = BorderStroke(1.dp, theme.border)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (selectedType == TriggerType.QR_CODE) {
                    val assignedRoom = rooms.find { it.id == trigger.assignedRoomId }
                        ?: rooms.find { it.name.equals(trigger.assignedRoomName, ignoreCase = true) }
                    val linkedContextName = assignedRoom?.name ?: trigger.assignedRoomName.takeIf { it.isNotBlank() }

                    Text(
                        text = "LINKED CONTEXT",
                        color = theme.tertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = linkedContextName ?: "Unattached",
                        color = if (linkedContextName != null) theme.textPrimary else theme.textSecondary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = "LINKED CONTEXT",
                        color = theme.tertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose which Context activates when this trigger is detected.",
                        color = theme.textSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rooms.forEach { room ->
                            val isSelected = selectedRoomId == room.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) theme.primary.copy(alpha = 0.14f) else theme.surfaceVariant
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) theme.primary else theme.border,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedRoomId = room.id }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = room.name,
                                        color = theme.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp
                                    )
                                    val modesCount = room.profile.normalizedModes().size
                                    Text(
                                        text = "$modesCount configured mode${if (modesCount == 1) "" else "s"}",
                                        color = theme.textSecondary,
                                        fontSize = 11.5.sp
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedRoomId = room.id },
                                    colors = RadioButtonDefaults.colors(selectedColor = theme.primary)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Type-Specific Configuration
        when (selectedType) {
            TriggerType.BLE_BEACON, TriggerType.UNCONFIGURED -> {
                if (!permState.bluetoothEnabled) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF59E0B).copy(alpha = 0.14f)),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Bluetooth & Location Permissions Disabled",
                                color = Color(0xFFF59E0B),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Automatic BLE Beacon detection will remain disabled until you grant Nearby Devices & Fine Location permissions.",
                                color = theme.textPrimary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                    }
                                },
                                border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "Enable in Android Settings",
                                    color = Color(0xFFF59E0B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    border = BorderStroke(1.dp, theme.border)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "BLE BEACON PARAMETERS",
                            color = theme.tertiary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = macText,
                            onValueChange = { macText = it },
                            label = { Text("Hardware MAC Address (Optional)") },
                            placeholder = { Text("e.g. AA:BB:CC:11:22:33") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = uuidText,
                            onValueChange = { uuidText = it },
                            label = { Text("iBeacon UUID / Eddystone Namespace") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = instanceText,
                            onValueChange = { instanceText = it },
                            label = { Text("Major / Minor or Instance ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Enter Proximity Threshold: ${enterRssi.toInt()} dBm",
                            color = theme.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        )
                        Slider(
                            value = enterRssi,
                            onValueChange = { enterRssi = it },
                            valueRange = -95f..-45f,
                            colors = SliderDefaults.colors(
                                thumbColor = theme.primary,
                                activeTrackColor = theme.primary
                            )
                        )

                        Text(
                            text = "Exit Proximity Threshold: ${exitRssi.toInt()} dBm",
                            color = theme.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        )
                        Slider(
                            value = exitRssi,
                            onValueChange = { exitRssi = it },
                            valueRange = -100f..-55f,
                            colors = SliderDefaults.colors(
                                thumbColor = theme.primary,
                                activeTrackColor = theme.primary
                            )
                        )
                    }
                }
            }

            TriggerType.NFC_TAG -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    border = BorderStroke(1.dp, theme.border)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "NFC TAG CONFIGURATION",
                            color = theme.tertiary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = nfcTagIdText,
                            onValueChange = { nfcTagIdText = it },
                            label = { Text("NFC Tag UID or Deep-Link URI") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Write 'contextpulse://trigger/${trigger.id}' to your physical NFC sticker or match its hardware UID above. Tapping your phone against the NFC tag will immediately toggle the linked Context.",
                            color = theme.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        val nfcPayload = nfcTagIdText.trim().ifBlank { "contextpulse://trigger/${trigger.id}" }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(nfcPayload))
                                    Toast.makeText(
                                        context,
                                        "NFC Payload copied to clipboard",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, theme.border)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = null,
                                    tint = theme.textPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Copy URI",
                                    color = theme.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "ContextPulse NFC Trigger: ${nameText.ifBlank { trigger.name }}")
                                        putExtra(Intent.EXTRA_TEXT, nfcPayload)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Context NFC Tag"))
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, theme.border)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = null,
                                    tint = theme.textPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Share Tag",
                                    color = theme.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                val targetRoom = rooms.find { it.id == selectedRoomId }
                                if (targetRoom != null) {
                                    val updated = trigger.copy(
                                        name = nameText.trim().ifBlank { "Room Trigger" },
                                        type = selectedType,
                                        nfcTagId = nfcTagIdText.trim().ifBlank { "contextpulse://trigger/${trigger.id}" },
                                        assignedRoomId = targetRoom.id,
                                        assignedRoomName = targetRoom.name
                                    )
                                    beaconRepository.saveOrUpdateBeacon(updated)
                                    ProfileActivationHelper.toggleOrActivateFromTrigger(
                                        context = context,
                                        roomId = targetRoom.id,
                                        triggerSource = TriggerSource.NFC_TAG,
                                        triggerName = updated.name
                                    )
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Select a Linked Context above first",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, theme.tertiary)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Nfc,
                                contentDescription = null,
                                tint = theme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Test NFC Tap",
                                color = theme.tertiary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            TriggerType.QR_CODE -> {
                val qrBitmap = remember(defaultGitHubBridgeUrl) {
                    QrCodeUtil.generateQrBitmap(
                        content = defaultGitHubBridgeUrl,
                        size = 512
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    border = BorderStroke(1.dp, theme.border)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "PRINTABLE CONTEXT QR CODE",
                            color = theme.tertiary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Box(
                            modifier = Modifier
                                .size(230.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White)
                                .padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            qrBitmap?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Context QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                val bmp = qrBitmap ?: QrCodeUtil.generateQrBitmap(defaultGitHubBridgeUrl, 512)
                                if (bmp != null) {
                                    QrCodeUtil.shareQrCode(context, bmp, nameText.ifBlank { trigger.name })
                                } else {
                                    Toast.makeText(context, "Generating QR image...", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.primary,
                                contentColor = theme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Share QR Code",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showBlePermissionDialog) {
        AlertDialog(
            onDismissRequest = { showBlePermissionDialog = false },
            containerColor = theme.surface,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.BluetoothSearching,
                    contentDescription = null,
                    tint = theme.tertiary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Bluetooth & Location Permissions Required",
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "To use BLE Beacon proximity triggers, please grant Nearby Devices (Bluetooth Scan) and Fine Location permissions in Android Settings. Otherwise, BLE beacon detection will remain disabled.",
                    color = theme.textSecondary,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBlePermissionDialog = false
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.primary,
                        contentColor = theme.onPrimary
                    )
                ) {
                    Text("Open Android Settings", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBlePermissionDialog = false
                        val isQrCode = selectedType == TriggerType.QR_CODE
                        val targetRoom = if (isQrCode) null else rooms.find { it.id == selectedRoomId }
                        val updated = trigger.copy(
                            name = nameText.trim().ifBlank { "Room Trigger" },
                            type = selectedType,
                            macAddress = macText.trim(),
                            namespaceOrUuid = uuidText.trim(),
                            instanceOrMajorMinor = instanceText.trim(),
                            nfcTagId = nfcTagIdText.trim(),
                            qrPayload = defaultGitHubBridgeUrl,
                            assignedRoomId = if (isQrCode) trigger.assignedRoomId else (targetRoom?.id ?: ""),
                            assignedRoomName = if (isQrCode) trigger.assignedRoomName else (targetRoom?.name ?: ""),
                            enterRssiThreshold = enterRssi.toInt(),
                            exitRssiThreshold = exitRssi.toInt()
                        )
                        beaconRepository.saveOrUpdateBeacon(updated)
                        Toast.makeText(
                            context,
                            "Saved '${updated.name}' (BLE remains disabled until permission is granted)",
                            Toast.LENGTH_LONG
                        ).show()
                        onSaved()
                    }
                ) {
                    Text("Save Anyway", color = theme.textSecondary)
                }
            }
        )
    }
}
