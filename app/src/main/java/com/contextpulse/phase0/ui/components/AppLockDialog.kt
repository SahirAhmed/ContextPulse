package com.contextpulse.phase0.ui.components

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.contextpulse.phase0.security.AppLockManager
import com.contextpulse.phase0.security.BiometricAuthHelper
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme

@Composable
fun AppLockDialog(
    lockManager: AppLockManager,
    title: String = "Security Lock",
    subtitle: String = "Enter your 6-digit PIN to continue",
    isAppEntrance: Boolean = false,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalContextPulseTheme.current
    val context = LocalContext.current
    val activity = context as? Activity

    var pinEntered by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isBiometricEnabled by lockManager.isBiometricEnabled.collectAsState()
    val canUseBiometrics = remember(isBiometricEnabled) {
        isBiometricEnabled && BiometricAuthHelper.canAuthenticate(context)
    }

    var showForgotPinDialog by remember { mutableStateOf(false) }
    var showWipeHelpDialog by remember { mutableStateOf(false) }

    fun handleDigit(digit: String) {
        if (pinEntered.length < 6) {
            val updated = pinEntered + digit
            pinEntered = updated
            errorMessage = null
            if (updated.length == 6) {
                if (lockManager.verifyPin(updated)) {
                    onSuccess()
                    onDismiss()
                } else {
                    errorMessage = "Incorrect 6-digit PIN. Try again."
                    pinEntered = ""
                }
            }
        }
    }

    fun triggerBiometrics() {
        if (activity != null && canUseBiometrics) {
            BiometricAuthHelper.promptBiometric(
                activity = activity,
                title = title,
                subtitle = "Touch the fingerprint sensor to unlock",
                onSuccess = {
                    lockManager.markAppAccessUnlocked()
                    lockManager.markSessionUnlocked()
                    onSuccess()
                    onDismiss()
                },
                onError = { err ->
                    if (!err.contains("canceled", ignoreCase = true)) {
                        errorMessage = err
                    }
                }
            )
        }
    }

    // Auto-prompt biometrics once if supported
    LaunchedEffect(Unit) {
        if (canUseBiometrics && activity != null) {
            triggerBiometrics()
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isAppEntrance) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isAppEntrance,
            dismissOnClickOutside = !isAppEntrance
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = theme.surface,
            border = BorderStroke(1.dp, theme.border),
            shadowElevation = 20.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 480.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(theme.primary.copy(alpha = 0.15f))
                        .border(1.dp, theme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock",
                        tint = theme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    fontSize = 12.5.sp,
                    color = theme.textSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                // PIN dots indicator - mandatory 6 digits
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(6) { idx ->
                        val filled = idx < pinEntered.length
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (filled) theme.primary else theme.border)
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Keypad grid
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf(if (canUseBiometrics) "FP" else "", "0", "DEL")
                )

                keys.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { key ->
                            when (key) {
                                "" -> {
                                    Spacer(modifier = Modifier.size(54.dp))
                                }
                                "FP" -> {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(theme.primary.copy(alpha = 0.15f))
                                            .clickable { triggerBiometrics() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Fingerprint,
                                            contentDescription = "Scan Fingerprint",
                                            tint = theme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                "DEL" -> {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(theme.background)
                                            .clickable {
                                                if (pinEntered.isNotEmpty()) {
                                                    pinEntered = pinEntered.dropLast(1)
                                                    errorMessage = null
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Backspace,
                                            contentDescription = "Delete",
                                            tint = theme.textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                else -> {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(theme.background)
                                            .clickable { handleDigit(key) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = key,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Forgot PIN Button
                TextButton(
                    onClick = { showForgotPinDialog = true }
                ) {
                    Text(
                        text = "Forgot PIN?",
                        color = theme.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (!isAppEntrance) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel", color = theme.textSecondary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    // Forgot PIN / Enter 16-Digit Recovery Code Dialog
    if (showForgotPinDialog) {
        var recoveryInput by remember { mutableStateOf("") }
        var recoveryError by remember { mutableStateOf<String?>(null) }

        Dialog(
            onDismissRequest = { showForgotPinDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = theme.surface,
                border = BorderStroke(1.dp, theme.border),
                shadowElevation = 24.dp,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 480.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Forgot PIN Verification",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary
                    )

                    Text(
                        text = "Enter the 16-digit recovery code provided when you enabled Security Lock. This will disable lock protection.",
                        fontSize = 12.5.sp,
                        color = theme.textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )

                    OutlinedTextField(
                        value = recoveryInput,
                        onValueChange = {
                            if (it.length <= 19) {
                                recoveryInput = it
                                recoveryError = null
                            }
                        },
                        label = { Text("16-Digit Recovery Code") },
                        placeholder = { Text("e.g. 4920-8192-3810-7412") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (recoveryError != null) {
                        Text(
                            text = recoveryError ?: "",
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (lockManager.verifyRecoveryCodeAndDisableLock(recoveryInput)) {
                                    showForgotPinDialog = false
                                    Toast.makeText(context, "Recovery code verified. Security Lock has been turned off.", Toast.LENGTH_LONG).show()
                                    onSuccess()
                                    onDismiss()
                                } else {
                                    recoveryError = "Invalid recovery code. Please check and try again."
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                        ) {
                            Text("Disable Lock", color = theme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { showWipeHelpDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(15.dp), tint = theme.accent)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Need Help?", color = theme.accent, fontSize = 12.5.sp)
                            }

                            TextButton(
                                onClick = { showForgotPinDialog = false },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Cancel", color = theme.textSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Help Dialog: What to do if Recovery Code is also lost
    if (showWipeHelpDialog) {
        AlertDialog(
            onDismissRequest = { showWipeHelpDialog = false },
            containerColor = theme.surface,
            title = {
                Text(
                    text = "Lost Recovery Code?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = theme.textPrimary
                )
            },
            text = {
                Text(
                    text = "If you do not have your 16-digit recovery code, there is no other way to bypass the security lock. The only solution is to wipe the app by clearing data in Android Settings:\n\n1. Open device Settings > Apps > ContextPulse\n2. Tap Storage & Cache\n3. Tap 'Clear Storage' or 'Clear Data'\n\nThis will reset ContextPulse to a fresh install.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = theme.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { showWipeHelpDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                ) {
                    Text("Understood", color = theme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
