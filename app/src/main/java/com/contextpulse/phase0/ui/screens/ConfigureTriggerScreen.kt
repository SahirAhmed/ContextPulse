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
import androidx.compose.ui.platform.LocalContext
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
import com.contextpulse.phase0.ui.components.RoomCalibrationWizardDialog
import com.contextpulse.phase0.ui.components.WriteNfcTagDialog
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme
import com.contextpulse.phase0.util.NfcTagHelper
import com.contextpulse.phase0.util.QrCodeUtil

@Composable
fun ConfigureTriggerScreen(
    trigger: RegisteredBeacon,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalContextPulseTheme.current
    val beaconRepository = remember { BeaconRepository.getInstance(context) }
    val permissionsRepo = remember { com.contextpulse.phase0.repository.PermissionsRepository.getInstance(context) }
    val permState by permissionsRepo.state.collectAsState()
    var isNfcEnabled by remember { mutableStateOf(NfcTagHelper.isNfcEnabled(context)) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                permissionsRepo.refreshPermissions()
                isNfcEnabled = NfcTagHelper.isNfcEnabled(context)
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
    var nfcTagIdText by remember(trigger.id) { mutableStateOf(trigger.nfcTagId) }

    // Android App Links URI for sahirahmed.github.io (shared by QR Codes and NFC Tags)
    val defaultGitHubBridgeUrl = remember(trigger.id) {
        NfcTagHelper.buildAppLinkUri(trigger.id)
    }

    var enterRssi by remember(trigger.id) { mutableFloatStateOf(trigger.enterRssiThreshold.toFloat()) }
    var exitRssi by remember(trigger.id) { mutableFloatStateOf(trigger.exitRssiThreshold.toFloat()) }

    var showBlePermissionDialog by remember { mutableStateOf(false) }
    var showCalibrationWizard by remember { mutableStateOf(false) }
    var showWriteNfcDialog by remember { mutableStateOf(false) }

    val assignedRoom = rooms.find { it.id == trigger.assignedRoomId }
        ?: rooms.find { it.name.equals(trigger.assignedRoomName, ignoreCase = true) }
    val linkedContextName = assignedRoom?.name ?: trigger.assignedRoomName.takeIf { it.isNotBlank() }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .displayCutoutPadding()
    ) {
        // Pinned Top Bar - stays cleanly beneath the status bar at all scroll offsets
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 860.dp)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 12.dp)
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
                        tint = theme.primary
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
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
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            Button(
                onClick = {
                    if (selectedType == TriggerType.BLE_BEACON && !permissionsRepo.isBlePermissionsGranted()) {
                        showBlePermissionDialog = true
                        return@Button
                    }
                    val updated = trigger.copy(
                        name = nameText.trim().ifBlank { "Room Trigger" },
                        type = selectedType,
                        macAddress = macText.trim(),
                        namespaceOrUuid = uuidText.trim(),
                        instanceOrMajorMinor = instanceText.trim(),
                        nfcTagId = nfcTagIdText.trim(),
                        qrPayload = defaultGitHubBridgeUrl,
                        assignedRoomId = trigger.assignedRoomId,
                        assignedRoomName = trigger.assignedRoomName,
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
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 860.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

        // 1. Trigger Identity Card (Type is fixed once selected; only name is editable)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = theme.surface),
            border = BorderStroke(1.dp, theme.border)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "TRIGGER IDENTITY",
                        color = theme.tertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = when (selectedType) {
                                TriggerType.BLE_BEACON, TriggerType.UNCONFIGURED -> Icons.Outlined.BluetoothSearching
                                TriggerType.NFC_TAG -> Icons.Outlined.Nfc
                                TriggerType.QR_CODE -> Icons.Outlined.QrCode2
                            },
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = selectedType.label,
                            color = theme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

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

        // 2. Linked Context Assignment (Read-Only for all Trigger Types; attach/detach via Contexts page)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = theme.surface),
            border = BorderStroke(1.dp, theme.border)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (linkedContextName != null) {
                        "This trigger is linked to context '$linkedContextName'. You can reassign or unlink it directly from the Contexts page."
                    } else {
                        "This trigger is currently unattached. Link it to a context directly from the Contexts page."
                    },
                    color = theme.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
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
                            onValueChange = {},
                            enabled = false,
                            label = { Text("MAC Address") },
                            placeholder = { Text("e.g. AA:BB:CC:11:22:33") },
                            singleLine = true,
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = "Locked",
                                    tint = theme.textSecondary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = theme.textSecondary,
                                disabledBorderColor = theme.border.copy(alpha = 0.5f),
                                disabledLabelColor = theme.textSecondary.copy(alpha = 0.7f),
                                disabledContainerColor = theme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = uuidText,
                            onValueChange = {},
                            enabled = false,
                            label = { Text("UUID") },
                            singleLine = true,
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = "Locked",
                                    tint = theme.textSecondary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = theme.textSecondary,
                                disabledBorderColor = theme.border.copy(alpha = 0.5f),
                                disabledLabelColor = theme.textSecondary.copy(alpha = 0.7f),
                                disabledContainerColor = theme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = instanceText,
                            onValueChange = {},
                            enabled = false,
                            label = { Text("Major / Minor ID") },
                            singleLine = true,
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = "Locked",
                                    tint = theme.textSecondary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = theme.textSecondary,
                                disabledBorderColor = theme.border.copy(alpha = 0.5f),
                                disabledLabelColor = theme.textSecondary.copy(alpha = 0.7f),
                                disabledContainerColor = theme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Hardware parameters are locked to the paired ContextPulse beacon.",
                            fontSize = 11.5.sp,
                            color = theme.textSecondary.copy(alpha = 0.7f)
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

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = {
                                if (!permissionsRepo.isBlePermissionsGranted()) {
                                    showBlePermissionDialog = true
                                } else {
                                    showCalibrationWizard = true
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, theme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = theme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Straighten,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val roomDisplay = rooms.find { it.id == trigger.assignedRoomId }?.name
                                ?: trigger.assignedRoomName.takeIf { it.isNotBlank() }
                            Text(
                                text = if (roomDisplay != null) "Calibrate Room ($roomDisplay)" else "Calibrate Room Boundary",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }

            TriggerType.NFC_TAG -> {
                val nfcSupported = remember { NfcTagHelper.isNfcSupported(context) }
                val nfcEnabled = isNfcEnabled

                if (nfcSupported && !nfcEnabled) {
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
                                text = "NFC Is Turned Off",
                                color = Color(0xFFF59E0B),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Turn on NFC in Android settings to write this trigger to a physical NFC tag or detect future taps.",
                                color = theme.textPrimary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                                    } catch (_: Exception) {
                                    }
                                },
                                border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "Open NFC Settings",
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
                            text = "NFC TAG PROVISIONING",
                            color = theme.tertiary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Physical Tag UID status block
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.surfaceVariant.copy(alpha = 0.65f))
                                .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "PHYSICAL TAG UID",
                                        color = theme.tertiary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = nfcTagIdText.ifBlank { "Not written to physical tag yet" },
                                        color = if (nfcTagIdText.isNotBlank()) theme.textPrimary else theme.textSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (nfcTagIdText.isNotBlank()) Color(0xFF10B981).copy(alpha = 0.16f)
                                            else theme.surface
                                        )
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (nfcTagIdText.isNotBlank()) "Written" else "Pending",
                                        color = if (nfcTagIdText.isNotBlank()) Color(0xFF10B981) else theme.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Tap 'Assign & Write to NFC Tag' and hold your NFC sticker against the back of your phone. Future taps will automatically switch to the attached Context.",
                            color = theme.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { showWriteNfcDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.primary,
                                contentColor = theme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Nfc,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (nfcTagIdText.isNotBlank()) "Rewrite NFC Tag" else "Assign & Write to NFC Tag",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
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
        }
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
                        val updated = trigger.copy(
                            name = nameText.trim().ifBlank { "Room Trigger" },
                            type = selectedType,
                            macAddress = macText.trim(),
                            namespaceOrUuid = uuidText.trim(),
                            instanceOrMajorMinor = instanceText.trim(),
                            nfcTagId = nfcTagIdText.trim(),
                            qrPayload = defaultGitHubBridgeUrl,
                            assignedRoomId = trigger.assignedRoomId,
                            assignedRoomName = trigger.assignedRoomName,
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

    if (showCalibrationWizard) {
        val targetRoomName = linkedContextName ?: "Room"

        RoomCalibrationWizardDialog(
            beaconMac = macText,
            beaconUuid = uuidText,
            beaconMajorMinor = instanceText,
            roomName = targetRoomName,
            onCalibrationComplete = { newEnter, newExit ->
                enterRssi = newEnter.toFloat()
                exitRssi = newExit.toFloat()

                // Immediately update underlying repository model as well
                val updated = trigger.copy(
                    enterRssiThreshold = newEnter,
                    exitRssiThreshold = newExit
                )
                beaconRepository.saveOrUpdateBeacon(updated)

                Toast.makeText(
                    context,
                    "Calibrated thresholds: Enter $newEnter dBm / Exit $newExit dBm",
                    Toast.LENGTH_SHORT
                ).show()

                showCalibrationWizard = false
            },
            onDismiss = {
                showCalibrationWizard = false
            }
        )
    }

    if (showWriteNfcDialog) {
        WriteNfcTagDialog(
            triggerName = nameText.ifBlank { trigger.name },
            appLinkUrl = defaultGitHubBridgeUrl,
            linkedContextName = linkedContextName,
            onTagWritten = { tagUid, writtenUri ->
                nfcTagIdText = tagUid
                val updated = trigger.copy(
                    name = nameText.trim().ifBlank { trigger.name },
                    type = selectedType,
                    nfcTagId = tagUid,
                    qrPayload = writtenUri
                )
                beaconRepository.saveOrUpdateBeacon(updated)
            },
            onDismiss = {
                showWriteNfcDialog = false
            }
        )
    }
}
