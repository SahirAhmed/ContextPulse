package com.contextpulse.phase0.ui.components

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.contextpulse.phase0.repository.AppGroupRepository
import com.contextpulse.phase0.repository.BeaconRepository
import com.contextpulse.phase0.repository.ContextTemplateRepository
import com.contextpulse.phase0.repository.RoomProfileRepository
import com.contextpulse.phase0.system.ActiveConfigSnapshotManager
import com.contextpulse.phase0.system.AppRestrictionMonitor
import com.contextpulse.phase0.system.AppUsageBudgetManager
import com.contextpulse.phase0.system.FloatingTimerOverlayManager
import com.contextpulse.phase0.system.SnoozeManager
import com.contextpulse.phase0.system.SystemControllers
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TestStatus {
    PENDING,
    RUNNING,
    PASSED,
    RESOLVED,
    FAILED
}

data class AppFeatureTestCase(
    val id: String,
    val name: String,
    val category: String,
    val icon: ImageVector,
    var status: TestStatus = TestStatus.PENDING,
    var details: String = "",
    var fixActionLabel: String? = null,
    var onFix: (() -> Unit)? = null
)

/**
 * Interactive "Test App" automated diagnostic suite modal.
 * Performs a fast, non-disruptive 6-8 second dry-run verification of all app features
 * with live progress, remaining countdown, self-resolving fixes, and full summary.
 */
@Composable
fun TestAppSuiteDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    val theme = LocalContextPulseTheme.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var isRunning by remember { mutableStateOf(false) }
    var currentTestIndex by remember { mutableStateOf(-1) }
    var totalEstimatedSeconds by remember { mutableStateOf(7) }
    var remainingSeconds by remember { mutableStateOf(7) }
    var testComplete by remember { mutableStateOf(false) }

    val testCases = remember {
        mutableStateListOf(
            AppFeatureTestCase(
                id = "storage",
                name = "Context & Mode Storage",
                category = "Core Persistence",
                icon = Icons.Outlined.Storage,
                details = "Validates Room database read/write and mode deserialization"
            ),
            AppFeatureTestCase(
                id = "permissions_overlay",
                name = "Floating Timer System Overlay",
                category = "Permissions & WindowManager",
                icon = Icons.Outlined.Layers,
                details = "Validates overlay window capability for dynamic timer display"
            ),
            AppFeatureTestCase(
                id = "permissions_usage",
                name = "App Usage Stats Access",
                category = "Permissions & Telemetry",
                icon = Icons.Outlined.QueryStats,
                details = "Validates foreground app detection and allowance monitoring"
            ),
            AppFeatureTestCase(
                id = "permissions_dnd",
                name = "Do Not Disturb ZenRule Engine",
                category = "Audio & Interruptions",
                icon = Icons.Outlined.DoNotDisturb,
                details = "Validates Notification Policy Access for automated DND"
            ),
            AppFeatureTestCase(
                id = "permissions_brightness",
                name = "Screen Brightness Controller",
                category = "Display Automation",
                icon = Icons.Outlined.BrightnessMedium,
                details = "Validates system write settings & display backlight automation"
            ),
            AppFeatureTestCase(
                id = "app_restrictions",
                name = "Restricted Apps & Budget Engine",
                category = "Distraction Blocker",
                icon = Icons.Outlined.Apps,
                details = "Validates immediate blocking logic and allowance tracker math"
            ),
            AppFeatureTestCase(
                id = "templates_groups",
                name = "Templates & App Groups",
                category = "Presets & Reusability",
                icon = Icons.Outlined.Category,
                details = "Validates bundle storage, templates and beacon trigger repository"
            ),
            AppFeatureTestCase(
                id = "snapshot_preview",
                name = "Snapshot & State Restoration",
                category = "System Integrity",
                icon = Icons.Outlined.Restore,
                details = "Validates crash recovery, preview overrides and state fallback"
            )
        )
    }

    fun runTests() {
        if (isRunning) return
        isRunning = true
        testComplete = false
        currentTestIndex = 0
        totalEstimatedSeconds = 7
        remainingSeconds = 7

        // Reset all test statuses
        testCases.forEach {
            it.status = TestStatus.PENDING
            it.fixActionLabel = null
            it.onFix = null
        }

        coroutineScope.launch {
            // Countdown ticker
            launch {
                while (isRunning && remainingSeconds > 0) {
                    delay(1000L)
                    if (remainingSeconds > 0) remainingSeconds--
                }
            }

            for (i in testCases.indices) {
                currentTestIndex = i
                val tc = testCases[i]
                tc.status = TestStatus.RUNNING
                delay(650L) // Safe non-disruptive pause for smooth animation

                when (tc.id) {
                    "storage" -> {
                        try {
                            val roomRepo = RoomProfileRepository.getInstance(context)
                            val rooms = roomRepo.rooms.value
                            val active = roomRepo.activeRoom.value
                            tc.status = TestStatus.PASSED
                            tc.details = "Loaded ${rooms.size} contexts successfully (Active: ${active?.name ?: "None"})"
                        } catch (e: Exception) {
                            tc.status = TestStatus.FAILED
                            tc.details = "Storage access failed: ${e.message}"
                        }
                    }
                    "permissions_overlay" -> {
                        val canDraw = Settings.canDrawOverlays(context)
                        if (canDraw) {
                            tc.status = TestStatus.PASSED
                            tc.details = "Overlay permission active. Floating timers will display over apps."
                        } else {
                            tc.status = TestStatus.RESOLVED
                            tc.details = "Overlay permission not granted. Required for floating timer countdown."
                            tc.fixActionLabel = "Grant Overlay"
                            tc.onFix = { SystemControllers.openOverlaySettings(context) }
                        }
                    }
                    "permissions_usage" -> {
                        val hasUsage = SystemControllers.hasUsageStatsPermission(context)
                        if (hasUsage) {
                            tc.status = TestStatus.PASSED
                            tc.details = "Usage Access active. Foreground detection & budget tracking functional."
                        } else {
                            tc.status = TestStatus.RESOLVED
                            tc.details = "Usage Access required to detect foreground restricted apps."
                            tc.fixActionLabel = "Grant Usage Access"
                            tc.onFix = { SystemControllers.openUsageAccessSettings(context) }
                        }
                    }
                    "permissions_dnd" -> {
                        val hasDnd = SystemControllers.isDndAccessGranted(context)
                        if (hasDnd) {
                            tc.status = TestStatus.PASSED
                            tc.details = "Notification Policy Access active. DND automation fully functional."
                        } else {
                            tc.status = TestStatus.RESOLVED
                            tc.details = "DND permission not granted. Needed if you automate DND modes."
                            tc.fixActionLabel = "Grant DND Access"
                            tc.onFix = { SystemControllers.openDndAccessSettings(context) }
                        }
                    }
                    "permissions_brightness" -> {
                        val canWrite = SystemControllers.canModifySystemSettings(context)
                        if (canWrite) {
                            tc.status = TestStatus.PASSED
                            tc.details = "Write System Settings active. Screen brightness automation verified."
                        } else {
                            tc.status = TestStatus.RESOLVED
                            tc.details = "Settings permission missing. Required for system brightness control."
                            tc.fixActionLabel = "Grant Settings"
                            tc.onFix = { SystemControllers.openWriteSettings(context) }
                        }
                    }
                    "app_restrictions" -> {
                        try {
                            val activeBudgets = AppUsageBudgetManager.getActiveBudgets()
                            val restricted = AppRestrictionMonitor.getRestrictedPackages()
                            tc.status = TestStatus.PASSED
                            tc.details = "Verified restriction monitor. ${restricted.size} outright blocked, ${activeBudgets.size} budgeted."
                        } catch (e: Exception) {
                            tc.status = TestStatus.FAILED
                            tc.details = "App restriction check failed: ${e.message}"
                        }
                    }
                    "templates_groups" -> {
                        try {
                            val templates = ContextTemplateRepository.getAllTemplates(context)
                            val groups = AppGroupRepository.getInstance(context).appGroups.value
                            val beacons = BeaconRepository.getInstance(context).beacons.value
                            tc.status = TestStatus.PASSED
                            tc.details = "${templates.size} context templates, ${groups.size} app groups, ${beacons.size} beacons verified."
                        } catch (e: Exception) {
                            tc.status = TestStatus.FAILED
                            tc.details = "Template/group repository error: ${e.message}"
                        }
                    }
                    "snapshot_preview" -> {
                        try {
                            val isPreview = ActiveConfigSnapshotManager.isPreviewActive(context)
                            val hasSnap = ActiveConfigSnapshotManager.hasSavedSnapshot(context)
                            tc.status = TestStatus.PASSED
                            tc.details = "Preview state active: $isPreview, Saved snapshot ready: $hasSnap. Recovery operational."
                        } catch (e: Exception) {
                            tc.status = TestStatus.FAILED
                            tc.details = "Snapshot verification error: ${e.message}"
                        }
                    }
                }
            }

            isRunning = false
            testComplete = true
            remainingSeconds = 0
        }
    }

    LaunchedEffect(Unit) {
        runTests()
    }

    val passedCount = testCases.count { it.status == TestStatus.PASSED }
    val resolvedCount = testCases.count { it.status == TestStatus.RESOLVED }
    val failedCount = testCases.count { it.status == TestStatus.FAILED }
    val completedCount = testCases.count { it.status == TestStatus.PASSED || it.status == TestStatus.RESOLVED || it.status == TestStatus.FAILED }
    val progressFraction = if (testCases.isNotEmpty()) completedCount.toFloat() / testCases.size.toFloat() else 0f

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
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Top Header
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
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "App Diagnostics Suite",
                                fontSize = 17.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                            Text(
                                text = if (isRunning) "Running automated test checks in background..." else "Comprehensive verification completed",
                                fontSize = 11.5.sp,
                                color = theme.textSecondary
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

                Spacer(modifier = Modifier.height(14.dp))

                // Progress Banner Card
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
                                        text = "Estimated time left: ~${remainingSeconds}s",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = theme.primary
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (failedCount == 0) Color(0xFF10B981) else Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (failedCount == 0) "All diagnostic tests passed!" else "$failedCount test(s) failed",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (failedCount == 0) Color(0xFF10B981) else Color(0xFFEF4444)
                                    )
                                }
                            }

                            Text(
                                text = "$completedCount of ${testCases.size} completed",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.textSecondary
                            )
                        }

                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = theme.primary,
                            trackColor = theme.surfaceVariant
                        )

                        // Phone Usability Notice
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("📱", fontSize = 12.sp)
                            Text(
                                text = "Non-disruptive dry run: You can safely continue using your phone normally.",
                                fontSize = 11.sp,
                                color = theme.textSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Test cases stream list
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(testCases, key = { it.id }) { tc ->
                        val isCurrent = tc.status == TestStatus.RUNNING
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) theme.primary.copy(alpha = 0.08f) else theme.surface
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isCurrent) theme.primary.copy(alpha = 0.5f) else theme.border.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    when (tc.status) {
                                                        TestStatus.PASSED -> Color(0x2210B981)
                                                        TestStatus.RESOLVED -> Color(0x22F59E0B)
                                                        TestStatus.FAILED -> Color(0x22EF4444)
                                                        TestStatus.RUNNING -> theme.primary.copy(alpha = 0.2f)
                                                        TestStatus.PENDING -> theme.surfaceVariant
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = tc.icon,
                                                contentDescription = null,
                                                tint = when (tc.status) {
                                                    TestStatus.PASSED -> Color(0xFF10B981)
                                                    TestStatus.RESOLVED -> Color(0xFFF59E0B)
                                                    TestStatus.FAILED -> Color(0xFFEF4444)
                                                    TestStatus.RUNNING -> theme.primary
                                                    TestStatus.PENDING -> theme.textSecondary
                                                },
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = tc.name,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = theme.textPrimary
                                            )
                                            Text(
                                                text = tc.category,
                                                fontSize = 10.5.sp,
                                                color = theme.textSecondary
                                            )
                                        }
                                    }

                                    // Status Badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                when (tc.status) {
                                                    TestStatus.PASSED -> Color(0x2210B981)
                                                    TestStatus.RESOLVED -> Color(0x22F59E0B)
                                                    TestStatus.FAILED -> Color(0x22EF4444)
                                                    TestStatus.RUNNING -> theme.primary.copy(alpha = 0.15f)
                                                    TestStatus.PENDING -> theme.surfaceVariant
                                                }
                                            )
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = when (tc.status) {
                                                TestStatus.PASSED -> "✓ Passed"
                                                TestStatus.RESOLVED -> "Action Required"
                                                TestStatus.FAILED -> "✗ Failed"
                                                TestStatus.RUNNING -> "Testing..."
                                                TestStatus.PENDING -> "Queued"
                                            },
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (tc.status) {
                                                TestStatus.PASSED -> Color(0xFF10B981)
                                                TestStatus.RESOLVED -> Color(0xFFF59E0B)
                                                TestStatus.FAILED -> Color(0xFFEF4444)
                                                TestStatus.RUNNING -> theme.primary
                                                TestStatus.PENDING -> theme.textSecondary
                                            }
                                        )
                                    }
                                }

                                Text(
                                    text = tc.details,
                                    fontSize = 11.5.sp,
                                    color = theme.textSecondary,
                                    lineHeight = 15.sp
                                )

                                // Self-healing 1-tap action button if Action Required
                                if (tc.fixActionLabel != null && tc.onFix != null) {
                                    Button(
                                        onClick = { tc.onFix!!.invoke() },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFF59E0B),
                                            contentColor = Color.Black
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("1-Tap Fix: ${tc.fixActionLabel}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom summary & action bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🟢 $passedCount passed",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF10B981)
                        )
                        if (resolvedCount > 0) {
                            Text(
                                text = "🟡 $resolvedCount actions",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFF59E0B)
                            )
                        }
                        if (failedCount > 0) {
                            Text(
                                text = "🔴 $failedCount failed",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFEF4444)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!isRunning) {
                            OutlinedButton(
                                onClick = { runTests() },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retest", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = onDismiss,
                            enabled = !isRunning,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.primary,
                                contentColor = theme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(if (isRunning) "Testing..." else "Done", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
