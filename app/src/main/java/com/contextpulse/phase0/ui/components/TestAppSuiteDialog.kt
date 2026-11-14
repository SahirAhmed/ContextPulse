package com.contextpulse.phase0.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.contextpulse.phase0.testing.*
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme

/**
 * ContextPulse Diagnostics & Testing Centre
 *
 * Provides 3 distinct testing sections:
 * 1. System Diagnostics (Read-only configuration & readiness)
 * 2. Automated Functional Tests (Isolated, authentic logic verification with real assertions)
 * 3. Guided Device Tests (User-confirmed physical device observation and external triggers)
 */
@Composable
fun TestAppSuiteDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    val theme = LocalContextPulseTheme.current
    val coordinator = remember { DiagnosticsCoordinator.getInstance(context) }

    val isRunning by coordinator.isRunning.collectAsState()
    val runningTestName by coordinator.currentRunningTestName.collectAsState()

    val systemDiagnostics by coordinator.systemDiagnostics.collectAsState()
    val functionalTests by coordinator.functionalTests.collectAsState()
    val guidedDeviceTests by coordinator.guidedDeviceTests.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var expandedTestId by remember { mutableStateOf<String?>(null) }
    var pendingConfirmTest by remember { mutableStateOf<DiagnosticTestResult?>(null) }
    var showQrTestScanner by remember { mutableStateOf(false) }
    var showNfcTestDialog by remember { mutableStateOf(false) }

    // Summary calculations across all categories
    val totalTests = systemDiagnostics.size + functionalTests.size + guidedDeviceTests.size
    val passedCount = systemDiagnostics.count { it.status == DiagnosticStatus.PASSED } +
            functionalTests.count { it.status == DiagnosticStatus.PASSED }
    val failedCount = systemDiagnostics.count { it.status == DiagnosticStatus.FAILED } +
            functionalTests.count { it.status == DiagnosticStatus.FAILED }
    val attentionCount = systemDiagnostics.count { it.status == DiagnosticStatus.NEEDS_ATTENTION }
    val notTestedCount = functionalTests.count { it.status == DiagnosticStatus.NOT_TESTED }
    val deviceTestCount = guidedDeviceTests.size

    val progressFraction = if (totalTests > 0) {
        val completed = (totalTests - notTestedCount - deviceTestCount).coerceAtLeast(0)
        completed.toFloat() / (totalTests - deviceTestCount).toFloat()
    } else 0f

    Dialog(
        onDismissRequest = {
            if (!isRunning) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = theme.surface,
            border = BorderStroke(1.dp, theme.border),
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Diagnostics & Testing Centre",
                                fontSize = 17.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                            Text(
                                text = if (isRunning) "Running: ${runningTestName ?: "tests..."}" else "Isolated functional assertions & system checks",
                                fontSize = 11.5.sp,
                                color = theme.textSecondary,
                                maxLines = 1
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isRunning,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = if (isRunning) theme.textSecondary.copy(alpha = 0.4f) else theme.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Overall Health Summary Banner
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.background),
                    border = BorderStroke(1.dp, theme.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (isRunning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = theme.primary
                                    )
                                    Text(
                                        text = "Executing isolated assertions...",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = theme.primary
                                    )
                                } else {
                                    Text(
                                        text = if (failedCount == 0 && attentionCount == 0) "All tests passed" else "$passedCount passed • $failedCount failed • $attentionCount actions",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            failedCount > 0 -> Color(0xFFEF4444)
                                            attentionCount > 0 -> Color(0xFFF59E0B)
                                            else -> Color(0xFF10B981)
                                        }
                                    )
                                }
                            }

                            Text(
                                text = "${passedCount + failedCount} of ${systemDiagnostics.size + functionalTests.size} verified",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.textSecondary
                            )
                        }

                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = theme.primary,
                            trackColor = theme.surfaceVariant
                        )

                        // Multi-metric chips row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatusMetricChip(label = "Passed", count = passedCount, color = Color(0xFF10B981))
                            StatusMetricChip(label = "Failed", count = failedCount, color = Color(0xFFEF4444))
                            StatusMetricChip(label = "Attention", count = attentionCount, color = Color(0xFFF59E0B))
                            StatusMetricChip(label = "Not Run", count = notTestedCount, color = theme.textSecondary)
                            StatusMetricChip(label = "Device", count = deviceTestCount, color = Color(0xFF60A5FA))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Three distinct category tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = theme.surface,
                    contentColor = theme.primary,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "1. System (${systemDiagnostics.size})",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "2. Functional (${functionalTests.size})",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Text(
                                "3. Device (${guidedDeviceTests.size})",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Content list
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> DiagnosticCategoryList(
                            items = systemDiagnostics,
                            expandedId = expandedTestId,
                            onToggleExpand = { id -> expandedTestId = if (expandedTestId == id) null else id },
                            onRunIndividual = null,
                            onActionRequest = { testItem ->
                                testItem.onFix?.invoke()
                            }
                        )
                        1 -> DiagnosticCategoryList(
                            items = functionalTests,
                            expandedId = expandedTestId,
                            onToggleExpand = { id -> expandedTestId = if (expandedTestId == id) null else id },
                            onRunIndividual = { idx -> coordinator.runSingleFunctionalTest(idx) },
                            onActionRequest = { testItem ->
                                testItem.onFix?.invoke()
                            }
                        )
                        2 -> DiagnosticCategoryList(
                            items = guidedDeviceTests,
                            expandedId = expandedTestId,
                            onToggleExpand = { id -> expandedTestId = if (expandedTestId == id) null else id },
                            onRunIndividual = null,
                            onActionRequest = { testItem ->
                                when (testItem.id) {
                                    "device_brightness_flash", "device_dnd_engage" -> {
                                        pendingConfirmTest = testItem
                                    }
                                    "device_qr_scan" -> {
                                        showQrTestScanner = true
                                    }
                                    "device_nfc_tap" -> {
                                        showNfcTestDialog = true
                                    }
                                    else -> {
                                        testItem.onFix?.invoke()
                                    }
                                }
                            }
                        )
                    }
                }

                // Standard Compose confirmation dialog before executing temporary hardware flash tests
                pendingConfirmTest?.let { testToRun ->
                    val isBrightness = testToRun.id == "device_brightness_flash"
                    AlertDialog(
                        onDismissRequest = { pendingConfirmTest = null },
                        title = {
                            Text(
                                text = if (isBrightness) "Confirm Screen Brightness Flash Test" else "Confirm DND Interruption Flash Test",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (isBrightness) {
                                        "ContextPulse will temporarily set screen brightness to 10% for exactly 3 seconds, then restore your exact previous brightness level and mode (manual or automatic).\n\nA synchronous recovery record will be committed before changing settings. If interrupted, ContextPulse will restore your setting on next app start."
                                    } else {
                                        "ContextPulse will temporarily activate Do Not Disturb (Priority Only) for exactly 3 seconds, then restore your exact previous interruption filter.\n\nA synchronous recovery record will be committed before changing settings. If interrupted, ContextPulse will restore your setting on next app start."
                                    },
                                    fontSize = 12.5.sp,
                                    color = theme.textSecondary,
                                    lineHeight = 17.sp
                                )
                                Text(
                                    text = "Conflict Safety: If you manually adjust this setting during the test, your new setting will NOT be overwritten.",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF10B981)
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val action = testToRun.onFix
                                    pendingConfirmTest = null
                                    action?.invoke()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Proceed with Test", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        },
                        dismissButton = {
                            OutlinedButton(
                                onClick = { pendingConfirmTest = null },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Cancel", fontSize = 12.sp)
                            }
                        },
                        containerColor = theme.surface,
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Controls Bottom Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRunning) {
                        OutlinedButton(
                            onClick = { coordinator.cancelRunningTests() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { coordinator.retestFailed() },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retest Failed", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                when (selectedTab) {
                                    0 -> coordinator.runAllTests(DiagnosticCategory.SYSTEM_DIAGNOSTICS)
                                    1 -> coordinator.runAllTests(DiagnosticCategory.AUTOMATED_FUNCTIONAL)
                                    else -> coordinator.refreshGuidedDeviceTests()
                                }
                            },
                            enabled = !isRunning,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                if (selectedTab == 2) Icons.Default.Refresh else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                when (selectedTab) {
                                    0 -> "Check System"
                                    1 -> "Run Functional"
                                    else -> "Refresh Device"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = onDismiss,
                            enabled = !isRunning,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceVariant, contentColor = theme.textPrimary),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Done", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showQrTestScanner) {
        QrScannerDialog(
            onDismiss = { showQrTestScanner = false },
            onQrScanned = { rawPayload ->
                showQrTestScanner = false
                val matched = com.contextpulse.phase0.repository.BeaconRepository.getInstance(context)
                    .findByQrPayload(rawPayload)
                val summary = if (matched != null) {
                    val linked = matched.assignedRoomName.takeIf { it.isNotBlank() } ?: "Unattached"
                    "Scanned & matched trigger '${matched.name}' (Context: $linked)."
                } else {
                    "Camera decoded QR payload: ${rawPayload.take(60)}"
                }
                coordinator.updateGuidedDeviceTest(
                    id = "device_qr_scan",
                    status = DiagnosticStatus.PASSED,
                    actualResult = summary
                )
            }
        )
    }

    if (showNfcTestDialog) {
        NfcTapDiagnosticDialog(
            onResult = { uid, payload ->
                showNfcTestDialog = false
                val repo = com.contextpulse.phase0.repository.BeaconRepository.getInstance(context)
                val byUid = uid?.let { repo.findByNfcTagId(it) }
                val byPayload = payload?.let {
                    val key = com.contextpulse.phase0.util.QrTriggerHandler.parsePayloadToTriggerKey(it)
                    repo.getBeaconById(key) ?: repo.findByNfcTagId(key)
                }
                val matched = byUid ?: byPayload
                val msg = buildString {
                    append("Tag detected")
                    if (!uid.isNullOrBlank()) append(" • UID: $uid")
                    if (matched != null) {
                        val ctxName = matched.assignedRoomName.ifBlank { "Unattached" }
                        append(" • Matched '${matched.name}' ($ctxName)")
                    } else if (!payload.isNullOrBlank()) {
                        append(" • Payload read (${payload.take(40)})")
                    }
                }
                coordinator.updateGuidedDeviceTest(
                    id = "device_nfc_tap",
                    status = DiagnosticStatus.PASSED,
                    actualResult = msg
                )
            },
            onDismiss = { showNfcTestDialog = false }
        )
    }
}

@Composable
private fun StatusMetricChip(label: String, count: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = "$label: $count",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun DiagnosticCategoryList(
    items: List<DiagnosticTestResult>,
    expandedId: String?,
    onToggleExpand: (String) -> Unit,
    onRunIndividual: ((Int) -> Unit)?,
    onActionRequest: ((DiagnosticTestResult) -> Unit)? = null
) {
    val theme = LocalContextPulseTheme.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            val isExpanded = expandedId == item.id
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = theme.background),
                border = BorderStroke(
                    1.dp,
                    if (isExpanded) theme.primary.copy(alpha = 0.6f) else theme.border.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(tween(150))
            ) {
                Column(
                    modifier = Modifier
                        .clickable { onToggleExpand(item.id) }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = item.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.textPrimary
                                )
                            }
                            Text(
                                text = item.subcategory,
                                fontSize = 10.5.sp,
                                color = theme.textSecondary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Status Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(item.status.color().copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = item.status.label(),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = item.status.color()
                                )
                            }

                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = theme.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Brief Explanation
                    Text(
                        text = item.explanation,
                        fontSize = 11.5.sp,
                        color = theme.textSecondary,
                        lineHeight = 15.sp
                    )

                    // Expanded Section
                    if (isExpanded) {
                        Divider(color = theme.border.copy(alpha = 0.5f), thickness = 0.8.dp)

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (item.expectedResult.isNotBlank()) {
                                Text(
                                    text = "Expected: ${item.expectedResult}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = theme.primary
                                )
                            }
                            if (item.actualResult.isNotBlank()) {
                                Text(
                                    text = "Actual: ${item.actualResult}",
                                    fontSize = 11.sp,
                                    color = theme.textPrimary
                                )
                            }
                            if (!item.correctiveAction.isNullOrBlank()) {
                                Text(
                                    text = "Fix: ${item.correctiveAction}",
                                    fontSize = 11.sp,
                                    color = Color(0xFFF59E0B)
                                )
                            }
                        }
                    }

                    if ((item.fixActionLabel != null && item.onFix != null) || onRunIndividual != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.fixActionLabel != null && item.onFix != null) {
                                Button(
                                    onClick = {
                                        if (onActionRequest != null) {
                                            onActionRequest(item)
                                        } else {
                                            item.onFix.invoke()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (item.category == DiagnosticCategory.GUIDED_DEVICE) theme.primary else Color(0xFFF59E0B),
                                        contentColor = if (item.category == DiagnosticCategory.GUIDED_DEVICE) theme.onPrimary else Color.Black
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text(item.fixActionLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                if (onRunIndividual != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                            }

                            if (onRunIndividual != null) {
                                OutlinedButton(
                                    onClick = { onRunIndividual(index) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Run This Test", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun DiagnosticStatus.label(): String = when (this) {
    DiagnosticStatus.PASSED -> "✓ Passed"
    DiagnosticStatus.FAILED -> "✗ Failed"
    DiagnosticStatus.NEEDS_ATTENTION -> "Action Required"
    DiagnosticStatus.MANUAL_VERIFICATION_REQUIRED -> "Manual Check"
    DiagnosticStatus.NOT_APPLICABLE -> "Not Applicable"
    DiagnosticStatus.NOT_TESTED -> "Not Tested"
    DiagnosticStatus.DEVICE_TEST_REQUIRED -> "Device Test Required"
    DiagnosticStatus.SKIPPED -> "Skipped"
    DiagnosticStatus.RUNNING -> "Running..."
}

private fun DiagnosticStatus.color(): Color = when (this) {
    DiagnosticStatus.PASSED -> Color(0xFF10B981)
    DiagnosticStatus.FAILED -> Color(0xFFEF4444)
    DiagnosticStatus.NEEDS_ATTENTION -> Color(0xFFF59E0B)
    DiagnosticStatus.MANUAL_VERIFICATION_REQUIRED -> Color(0xFF38BDF8) // Bright Cyan/Sky Blue
    DiagnosticStatus.NOT_APPLICABLE -> Color(0xFF64748B) // Slate gray
    DiagnosticStatus.NOT_TESTED -> Color(0xFF94A3B8)
    DiagnosticStatus.DEVICE_TEST_REQUIRED -> Color(0xFF60A5FA)
    DiagnosticStatus.SKIPPED -> Color(0xFF94A3B8)
    DiagnosticStatus.RUNNING -> Color(0xFF38BDF8)
}

@Composable
private fun NfcTapDiagnosticDialog(
    onResult: (uid: String?, payload: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val theme = LocalContextPulseTheme.current
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val isSupported = remember { com.contextpulse.phase0.util.NfcTagHelper.isNfcSupported(context) }
    var isEnabled by remember { mutableStateOf(com.contextpulse.phase0.util.NfcTagHelper.isNfcEnabled(context)) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isEnabled = com.contextpulse.phase0.util.NfcTagHelper.isNfcEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    DisposableEffect(isSupported, isEnabled) {
        val activity = with(com.contextpulse.phase0.util.NfcTagHelper) { context.findActivity() }
        val adapter = android.nfc.NfcAdapter.getDefaultAdapter(context)
        if (isSupported && isEnabled && activity != null && adapter != null) {
            val flags = android.nfc.NfcAdapter.FLAG_READER_NFC_A or
                android.nfc.NfcAdapter.FLAG_READER_NFC_B or
                android.nfc.NfcAdapter.FLAG_READER_NFC_F or
                android.nfc.NfcAdapter.FLAG_READER_NFC_V
            val cb = android.nfc.NfcAdapter.ReaderCallback { tag ->
                val extraction = com.contextpulse.phase0.util.NfcTagHelper.readTagPayload(tag)
                mainHandler.post {
                    onResult(extraction.hardwareUid, extraction.uriOrPayload)
                }
            }
            try {
                adapter.enableReaderMode(activity, cb, flags, null)
            } catch (_: Exception) {}
            onDispose {
                try {
                    adapter.disableReaderMode(activity)
                } catch (_: Exception) {}
            }
        } else {
            onDispose {}
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.surface,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Nfc,
                contentDescription = null,
                tint = theme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = when {
                    !isSupported -> "NFC Not Supported"
                    !isEnabled -> "Enable NFC to Test"
                    else -> "Hold NFC Tag Near Phone"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = theme.textPrimary
            )
        },
        text = {
            Text(
                text = when {
                    !isSupported -> "This device does not report an NFC reader hardware chip."
                    !isEnabled -> "Turn on NFC in Android settings, then return here to test tapping an NFC sticker."
                    else -> "Listening in foreground reader mode. Hold any NTAG/NFC sticker against the back of your device."
                },
                fontSize = 13.sp,
                color = theme.textSecondary
            )
        },
        confirmButton = {
            if (isSupported && !isEnabled) {
                Button(
                    onClick = {
                        try {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NFC_SETTINGS))
                        } catch (_: Exception) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary)
                ) {
                    Text("Open NFC Settings", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = theme.textSecondary)
            }
        },
        shape = RoundedCornerShape(18.dp)
    )
}
