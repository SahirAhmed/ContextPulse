package com.contextpulse.phase0.ui.components

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme
import com.contextpulse.phase0.util.NfcTagHelper
import com.contextpulse.phase0.util.NfcTagHelper.findActivity
import com.contextpulse.phase0.util.NfcWriteResult

private sealed class NfcWriteUiState {
    data object WaitingForTag : NfcWriteUiState()
    data class Success(val tagUid: String, val bytesWritten: Int) : NfcWriteUiState()
    data class Error(val message: String) : NfcWriteUiState()
}

@Composable
fun WriteNfcTagDialog(
    triggerName: String,
    appLinkUrl: String,
    linkedContextName: String?,
    onTagWritten: (tagUid: String, writtenUri: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val theme = LocalContextPulseTheme.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val isNfcSupported = remember { NfcTagHelper.isNfcSupported(context) }
    var isNfcEnabled by remember { mutableStateOf(NfcTagHelper.isNfcEnabled(context)) }
    var uiState by remember { mutableStateOf<NfcWriteUiState>(NfcWriteUiState.WaitingForTag) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isNfcEnabled = NfcTagHelper.isNfcEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val shouldListenForWrite = isNfcSupported && isNfcEnabled && uiState !is NfcWriteUiState.Success

    DisposableEffect(shouldListenForWrite, appLinkUrl) {
        val activity = context.findActivity()
        val adapter = NfcAdapter.getDefaultAdapter(context)
        if (shouldListenForWrite && activity != null && adapter != null) {
            val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NFC_BARCODE

            val callback = NfcAdapter.ReaderCallback { tag ->
                val result = NfcTagHelper.writeAppLinkToTag(tag, appLinkUrl)
                mainHandler.post {
                    when (result) {
                        is NfcWriteResult.Success -> {
                            onTagWritten(result.tagUid, result.writtenUri)
                            uiState = NfcWriteUiState.Success(
                                tagUid = result.tagUid,
                                bytesWritten = result.bytesWritten
                            )
                        }
                        NfcWriteResult.TagReadOnly -> {
                            uiState = NfcWriteUiState.Error("This NFC tag is locked/read-only and cannot be written.")
                        }
                        is NfcWriteResult.InsufficientCapacity -> {
                            uiState = NfcWriteUiState.Error(
                                "Tag capacity is too small (${result.maxBytes} bytes available, ${result.neededBytes} bytes required)."
                            )
                        }
                        NfcWriteResult.NotNdefCompatible -> {
                            uiState = NfcWriteUiState.Error("Unsupported tag format. Please use an NDEF-compatible NFC sticker/tag (e.g. NTAG213/215/216).")
                        }
                        is NfcWriteResult.Error -> {
                            uiState = NfcWriteUiState.Error(result.message)
                        }
                    }
                }
            }

            try {
                adapter.enableReaderMode(activity, callback, flags, null)
            } catch (_: Exception) {
            }

            onDispose {
                try {
                    adapter.disableReaderMode(activity)
                } catch (_: Exception) {
                }
            }
        } else {
            onDispose { }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "nfc_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nfc_scale"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = theme.surface,
            border = BorderStroke(1.5.dp, theme.primary.copy(alpha = 0.6f)),
            shadowElevation = 20.dp,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "NFC TAG PROVISIONING",
                            color = theme.tertiary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = triggerName.ifBlank { "Assign NFC Tag" },
                            color = theme.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = theme.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when {
                    !isNfcSupported -> {
                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444).copy(alpha = 0.14f))
                                .border(1.5.dp, Color(0xFFEF4444), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "NFC Hardware Unavailable",
                            color = theme.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "This device does not report an active NFC reader/writer chip.",
                            color = theme.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    !isNfcEnabled -> {
                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B).copy(alpha = 0.15f))
                                .border(1.5.dp, Color(0xFFF59E0B), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(38.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Turn On NFC in Settings",
                            color = theme.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Enable NFC in your phone settings, then return here and hold your tag against the back of your device.",
                            color = theme.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                                } catch (_: Exception) {
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.primary,
                                contentColor = theme.onPrimary
                            )
                        ) {
                            Text("Open NFC Settings", fontWeight = FontWeight.Bold)
                        }
                    }

                    uiState is NfcWriteUiState.Success -> {
                        val success = uiState as NfcWriteUiState.Success
                        Box(
                            modifier = Modifier
                                .size(86.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.16f))
                                .border(2.dp, Color(0xFF10B981), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "NFC Write Success",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "NFC Tag Assigned & Written!",
                            color = Color(0xFF10B981),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (linkedContextName != null) {
                                "Tapping this tag will now open ContextPulse and switch to '$linkedContextName'."
                            } else {
                                "Tag written (${success.bytesWritten} bytes). Link this trigger to a Context from the Contexts page so taps switch automatically."
                            },
                            color = theme.textSecondary,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        if (success.tagUid.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(theme.surfaceVariant)
                                    .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "HARDWARE TAG UID",
                                        color = theme.tertiary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    )
                                    Text(
                                        text = success.tagUid,
                                        color = theme.textPrimary,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    uiState is NfcWriteUiState.Error -> {
                        val err = uiState as NfcWriteUiState.Error
                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444).copy(alpha = 0.14f))
                                .border(1.5.dp, Color(0xFFEF4444), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Write Failed",
                            color = Color(0xFFEF4444),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = err.message,
                            color = theme.textSecondary,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }

                    else -> {
                        Box(
                            modifier = Modifier.size(104.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(theme.primary.copy(alpha = 0.12f))
                                    .border(1.5.dp, theme.primary.copy(alpha = 0.45f), CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(theme.primary.copy(alpha = 0.2f))
                                    .border(2.dp, theme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Nfc,
                                    contentDescription = "Hold NFC tag near phone",
                                    tint = theme.primary,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Ready to Write NFC Tag",
                            color = theme.textPrimary,
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Hold your NFC sticker or card against the NFC antenna on the back of your phone until writing completes.",
                            color = theme.textSecondary,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (uiState is NfcWriteUiState.Error || uiState is NfcWriteUiState.Success) {
                        OutlinedButton(
                            onClick = { uiState = NfcWriteUiState.WaitingForTag },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, theme.border)
                        ) {
                            Text(
                                text = if (uiState is NfcWriteUiState.Success) "Write Again" else "Retry",
                                color = theme.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.primary,
                            contentColor = theme.onPrimary
                        )
                    ) {
                        Text(
                            text = if (uiState is NfcWriteUiState.Success) "Done" else "Cancel",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
