package com.contextpulse.phase0.ui

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.contextpulse.phase0.model.ContextItem
import com.contextpulse.phase0.model.TriggerItem
import com.contextpulse.phase0.model.TriggerSource
import com.contextpulse.phase0.model.TriggerType
import com.contextpulse.phase0.repository.ActivationLogRepository
import com.contextpulse.phase0.repository.BeaconRepository
import com.contextpulse.phase0.repository.PermissionsRepository
import com.contextpulse.phase0.repository.RoomProfileRepository
import com.contextpulse.phase0.security.AppLockManager
import com.contextpulse.phase0.system.AppRestrictionMonitor
import com.contextpulse.phase0.system.BaselineStateManager
import com.contextpulse.phase0.system.ContextEngine
import com.contextpulse.phase0.system.SnoozeManager
import com.contextpulse.phase0.system.SystemControllers
import com.contextpulse.phase0.ui.components.ContextPulseBottomBar
import com.contextpulse.phase0.ui.components.ContextPulseTab
import com.contextpulse.phase0.ui.components.ContextPulseTopBar
import com.contextpulse.phase0.ui.components.SnoozeDialog
import com.contextpulse.phase0.ui.components.TourGuideOverlay
import com.contextpulse.phase0.ui.screens.ActivityHistoryScreen
import com.contextpulse.phase0.ui.screens.ConfigureTriggerScreen
import com.contextpulse.phase0.ui.screens.ContextsScreen
import com.contextpulse.phase0.ui.screens.EditContextScreen
import com.contextpulse.phase0.ui.screens.HomeScreen
import com.contextpulse.phase0.ui.screens.PermissionsScreen
import com.contextpulse.phase0.ui.screens.SettingsScreen
import com.contextpulse.phase0.ui.screens.TriggersScreen
import com.contextpulse.phase0.ui.theme.AppThemeRepository
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        try {
            com.contextpulse.phase0.ble.Phase0BleService.start(this)
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Failed to start Phase0BleService from onCreate", e)
        }
        handleTriggerDeepLink(intent)

        setContent {
            val themeRepository = remember { AppThemeRepository.getInstance(applicationContext) }
            val currentTheme by themeRepository.currentTheme.collectAsState()

            val colorScheme = if (currentTheme.isLight) {
                lightColorScheme(
                    primary = currentTheme.primary,
                    onPrimary = currentTheme.onPrimary,
                    background = currentTheme.background,
                    surface = currentTheme.surface,
                    surfaceVariant = currentTheme.surfaceVariant,
                    onBackground = currentTheme.textPrimary,
                    onSurface = currentTheme.textPrimary
                )
            } else {
                darkColorScheme(
                    primary = currentTheme.primary,
                    onPrimary = currentTheme.onPrimary,
                    background = currentTheme.background,
                    surface = currentTheme.surface,
                    surfaceVariant = currentTheme.surfaceVariant,
                    onBackground = currentTheme.textPrimary,
                    onSurface = currentTheme.textPrimary
                )
            }

            CompositionLocalProvider(LocalContextPulseTheme provides currentTheme) {
                MaterialTheme(colorScheme = colorScheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = currentTheme.background
                    ) {
                        ContextPulseMainScreen(
                            onSelectTheme = { themeKey ->
                                themeRepository.setTheme(themeKey)
                                try {
                                    com.contextpulse.phase0.widget.ContextPulseWidgetProvider.updateAllWidgets(applicationContext)
                                } catch (e: Throwable) {
                                    // Ignored
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTriggerDeepLink(intent)
    }

    private fun handleTriggerDeepLink(intent: android.content.Intent?) {
        val data = intent?.data ?: return
        val scheme = data.scheme ?: ""
        val host = data.host ?: ""
        val path = data.path ?: ""

        val rawKey = when {
            scheme == "contextpulse" && host == "trigger" -> {
                data.lastPathSegment ?: path.removePrefix("/")
            }
            (scheme == "https" || scheme == "http") && (host == "contextpulse.app" || host.contains("contextpulse")) && path.startsWith("/trigger") -> {
                data.lastPathSegment
            }
            else -> data.lastPathSegment
        } ?: return

        val triggerId = rawKey.trim()
        val beaconRepo = com.contextpulse.phase0.repository.BeaconRepository.getInstance(applicationContext)
        val roomRepo = com.contextpulse.phase0.repository.RoomProfileRepository.getInstance(applicationContext)

        val trigger = beaconRepo.beacons.value.find {
            it.id.equals(triggerId, ignoreCase = true) ||
            it.qrPayload.equals(data.toString(), ignoreCase = true) ||
            it.qrPayload.endsWith("/$triggerId", ignoreCase = true) ||
            it.name.equals(triggerId, ignoreCase = true)
        }

        if (trigger != null) {
            val assignedRoomName = trigger.assignedRoomName
            if (!assignedRoomName.isNullOrBlank()) {
                val targetRoom = roomRepo.rooms.value.find { it.name.equals(assignedRoomName.trim(), ignoreCase = true) }
                if (targetRoom != null) {
                    com.contextpulse.phase0.system.ProfileActivationHelper.activateProfile(
                        context = this,
                        roomId = targetRoom.id,
                        triggerSource = com.contextpulse.phase0.model.TriggerSource.QR_CODE,
                        reason = "Room QR Code scanned: ${trigger.name}"
                    )
                    Toast.makeText(this, "Room QR Code Scanned: Activated '${targetRoom.name}'", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Assigned context '$assignedRoomName' not found.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Scanned Trigger '${trigger.name}', but it is not assigned to a context yet.", Toast.LENGTH_LONG).show()
            }
        } else {
            // Check if triggerId matches a context name or ID directly
            val directRoom = roomRepo.rooms.value.find { it.name.equals(triggerId, ignoreCase = true) || it.id == triggerId }
            if (directRoom != null) {
                com.contextpulse.phase0.system.ProfileActivationHelper.activateProfile(
                    context = this,
                    roomId = directRoom.id,
                    triggerSource = com.contextpulse.phase0.model.TriggerSource.QR_CODE,
                    reason = "Room QR Code scanned directly"
                )
                Toast.makeText(this, "Room QR Code Scanned: Activated '${directRoom.name}'", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "QR Code trigger ($triggerId) not recognized.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

sealed class AppScreen {
    data class Tab(val tab: ContextPulseTab) : AppScreen()
    data class EditContext(val contextId: String) : AppScreen()
    data class ConfigureTrigger(val triggerId: String) : AppScreen()
    object ActivityHistory : AppScreen()
    object Settings : AppScreen()

    companion object {
        fun EditProfile(roomId: String): AppScreen = EditContext(roomId)
    }

    fun getOrderIndex(): Int = when (this) {
        is Tab -> tab.ordinal
        is EditContext -> 1
        is ConfigureTrigger -> 2
        ActivityHistory -> 4
        Settings -> 5
    }
}

@Composable
fun ContextPulseMainScreen(
    onSelectTheme: (com.contextpulse.phase0.ui.theme.AppThemeKey) -> Unit
) {
    val context = LocalContext.current
    val currentTheme = LocalContextPulseTheme.current
    val roomRepository = remember { RoomProfileRepository.getInstance(context) }
    val beaconRepository = remember { BeaconRepository.getInstance(context) }
    val permissionsRepository = remember { PermissionsRepository.getInstance(context) }
    val activationLogRepository = remember { ActivationLogRepository.getInstance(context) }
    val baselineStateManager = remember { BaselineStateManager.getInstance(context) }
    val contextEngine = remember { ContextEngine.getInstance(context) }
    val lockManager = remember { AppLockManager.getInstance(context) }
    val snoozeManager = remember { SnoozeManager.getInstance(context) }

    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Tab(ContextPulseTab.HOME)) }

    // First launch tour guide state
    val tourPrefs = remember { context.getSharedPreferences("contextpulse_tour_prefs", Context.MODE_PRIVATE) }
    var showTourGuide by remember { mutableStateOf(!tourPrefs.getBoolean("tour_completed", false)) }

    // Snooze dialog state & live ticking seconds countdown
    var showSnoozeDialog by remember { mutableStateOf(false) }
    val snoozeEndTime by snoozeManager.snoozeEndTime.collectAsState()
    var currentRemainingSeconds by remember { mutableStateOf(snoozeManager.getRemainingSeconds()) }
    LaunchedEffect(snoozeEndTime) {
        while (snoozeManager.isSnoozeActive()) {
            currentRemainingSeconds = snoozeManager.getRemainingSeconds()
            kotlinx.coroutines.delay(1000L)
        }
        currentRemainingSeconds = 0L
    }
    val isSnoozed = currentRemainingSeconds > 0
    val remainingSnoozeClock = remember(currentRemainingSeconds) { snoozeManager.getFormattedRemainingTime() }
    val remainingSnoozeDetailed = remember(currentRemainingSeconds) { snoozeManager.getFormattedRemainingDetailed() }

    // Observed State Flows
    val rooms by roomRepository.rooms.collectAsState()
    val activeRoomId by roomRepository.activeRoomId.collectAsState()
    val activeRoom by roomRepository.activeRoom.collectAsState()
    val beacons by beaconRepository.beacons.collectAsState()
    val activationLogs by activationLogRepository.activationLogs.collectAsState()
    val permissionsState by permissionsRepository.state.collectAsState()

    // Real execution of Context Profile Automations with Timeframe Scheduling: Brightness, DND, Eye Comfort, and Apps Blocked
    LaunchedEffect(activeRoom, isSnoozed) {
        val currentActive = activeRoom

        if (isSnoozed) {
            // During active snooze: suspend all automations & restrictions
            SystemControllers.restoreBrightness(context)
            SystemControllers.restoreDnd(context)
            SystemControllers.applyEyeComfort(context, false)
            AppRestrictionMonitor.setRestrictedPackages(emptySet(), null)
            AppRestrictionMonitor.stopContinuousMonitoring()
        } else if (currentActive != null) {
            val activeMode = currentActive.profile.resolveActiveMode()

            // 1. Brightness Automation
            if (activeMode.brightnessEnabled) {
                SystemControllers.applyBrightness(context, activeMode.brightnessLevel)
            } else {
                SystemControllers.restoreBrightness(context)
            }

            // 2. Do Not Disturb (DND) Automation
            if (activeMode.dndEnabled) {
                SystemControllers.applyDndMode(context, activeMode.dndMode)
            } else {
                SystemControllers.restoreDnd(context)
            }

            // 3. Eye Comfort / Night Light Automation
            if (activeMode.eyeComfortEnabled) {
                SystemControllers.applyEyeComfort(context, true, activeMode.eyeComfortLevel)
            } else {
                SystemControllers.applyEyeComfort(context, false)
            }

            // 4. Apps Blocked Automation
            if (activeMode.appRestrictionsEnabled && activeMode.restrictedApps.isNotEmpty()) {
                AppRestrictionMonitor.setRestrictedPackages(
                    packages = activeMode.restrictedApps.toSet(),
                    roomName = currentActive.name
                )
                AppRestrictionMonitor.startContinuousMonitoring(context, 800L)
            } else {
                AppRestrictionMonitor.setRestrictedPackages(emptySet(), null)
                AppRestrictionMonitor.stopContinuousMonitoring()
            }
        } else {
            SystemControllers.restoreBrightness(context)
            SystemControllers.restoreDnd(context)
            SystemControllers.applyEyeComfort(context, false)
            AppRestrictionMonitor.setRestrictedPackages(emptySet(), null)
            AppRestrictionMonitor.stopContinuousMonitoring()
        }

        // Keep Home Screen Widgets synchronized with active room state
        try {
            com.contextpulse.phase0.widget.ContextPulseWidgetProvider.updateAllWidgets(context)
        } catch (e: Throwable) {
            // Ignored
        }
    }

    // Reconcile triggers with valid room names whenever contexts change
    LaunchedEffect(rooms) {
        beaconRepository.reconcileWithRoomNames(rooms.map { it.name }.toSet())
        try {
            com.contextpulse.phase0.widget.ContextPulseWidgetProvider.updateAllWidgets(context)
        } catch (e: Throwable) {
            // Ignored
        }
    }

    // Android Runtime Permissions Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        permissionsRepository.refreshPermissions()
        try {
            com.contextpulse.phase0.ble.Phase0BleService.start(context)
        } catch (e: Throwable) {
            // Ignored
        }
    }

    // Virtual Tour Guide Overlay
    if (showTourGuide) {
        TourGuideOverlay(
            onDismiss = {
                tourPrefs.edit().putBoolean("tour_completed", true).apply()
                showTourGuide = false
            }
        )
    }

    // Temporary Snooze / Pause Dialog with live countdown
    if (showSnoozeDialog) {
        SnoozeDialog(
            isActive = isSnoozed,
            remainingMinutes = (currentRemainingSeconds / 60 + 1).toInt(),
            remainingTimeText = remainingSnoozeDetailed,
            onStartSnooze = { mins -> snoozeManager.startSnooze(mins) },
            onCancelSnooze = { snoozeManager.cancelSnooze() },
            onDismiss = { showSnoozeDialog = false }
        )
    }

    // Main column layout: TopBar -> Content (weight 1f) -> BottomBar
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(currentTheme.background)
    ) {
        // Consistent Brand Top Bar across all 4 tab screens with ZZZ sleep button
        if (currentScreen is AppScreen.Tab) {
            ContextPulseTopBar(
                onSettingsClick = { currentScreen = AppScreen.Settings },
                onSnoozeClick = { showSnoozeDialog = true },
                isSnoozed = isSnoozed,
                snoozeRemainingMinutes = (currentRemainingSeconds / 60 + 1).toInt(),
                snoozeRemainingText = remainingSnoozeClock
            )
        }

        // Screen content area strictly above the navbar
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    val initialOrder = initialState.getOrderIndex()
                    val targetOrder = targetState.getOrderIndex()
                    if (targetOrder > initialOrder) {
                        (slideInHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { width -> width } + fadeIn(animationSpec = tween(300)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { width -> -width } + fadeOut(animationSpec = tween(300)))
                    } else {
                        (slideInHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { width -> -width } + fadeIn(animationSpec = tween(300)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { width -> width } + fadeOut(animationSpec = tween(300)))
                    }
                },
                label = "PageSlideTransition",
                modifier = Modifier.fillMaxSize()
            ) { screen ->
                when (screen) {
                    is AppScreen.Tab -> {
                        when (screen.tab) {
                            ContextPulseTab.HOME -> {
                                HomeScreen(
                                    activeRoom = activeRoom,
                                    profileCount = rooms.size,
                                    beacons = beacons,
                                    activationLogs = activationLogs,
                                    onActiveProfileClick = { currentScreen = AppScreen.Tab(ContextPulseTab.CONTEXTS) },
                                    onBeaconsCardClick = { currentScreen = AppScreen.Tab(ContextPulseTab.TRIGGERS) },
                                    onProfilesCardClick = { currentScreen = AppScreen.Tab(ContextPulseTab.CONTEXTS) },
                                    onViewMoreClick = { currentScreen = AppScreen.ActivityHistory }
                                )
                            }

                            ContextPulseTab.CONTEXTS -> {
                                ContextsScreen(
                                    contexts = rooms,
                                    activeContextId = activeRoomId,
                                    triggers = beacons,
                                    roomRepo = roomRepository,
                                    beaconRepo = beaconRepository,
                                    lockManager = lockManager,
                                    onSelectContext = { contextItem ->
                                        val previousName = activeRoom?.name
                                        if (activeRoomId == contextItem.id) {
                                            contextEngine.deactivateRoom()
                                            activationLogRepository.logActivation(
                                                toProfileName = "Baseline (Default)",
                                                fromProfileName = contextItem.name,
                                                reason = "The context was manually deactivated by user",
                                                triggerSource = TriggerSource.MANUAL
                                            )
                                            Toast.makeText(context, "Deactivated ${contextItem.name}", Toast.LENGTH_SHORT).show()
                                        } else {
                                            contextEngine.activateRoom(contextItem.id)
                                            activationLogRepository.logActivation(
                                                toProfileName = contextItem.name,
                                                fromProfileName = previousName ?: "Baseline",
                                                reason = "The context was manually changed by user in Contexts screen",
                                                triggerSource = TriggerSource.MANUAL
                                            )
                                            Toast.makeText(context, "Switched context to ${contextItem.name}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onAddContext = { name, subtitle, iconName ->
                                        roomRepository.createRoom(
                                            name = name,
                                            subtitle = subtitle,
                                            iconName = iconName
                                        )
                                        Toast.makeText(context, "Created $name", Toast.LENGTH_SHORT).show()
                                    },
                                    onDeleteContext = { contextId ->
                                        val target = rooms.find { it.id == contextId }
                                        target?.let {
                                            beaconRepository.detachBeaconByRoomName(it.name)
                                        }
                                        roomRepository.deleteRoom(contextId)
                                        Toast.makeText(context, "Context deleted", Toast.LENGTH_SHORT).show()
                                    },
                                    onEditContext = { contextItem ->
                                        currentScreen = AppScreen.EditContext(contextItem.id)
                                    },
                                    onAttachTrigger = { triggerId, contextName ->
                                        beaconRepository.attachBeaconToRoom(triggerId, contextName)
                                        val trig = beacons.find { it.id == triggerId }
                                        Toast.makeText(context, "Paired ${trig?.name ?: "trigger"} to $contextName", Toast.LENGTH_SHORT).show()
                                    },
                                    onDetachTrigger = { triggerId ->
                                        val trig = beacons.find { it.id == triggerId }
                                        beaconRepository.detachBeaconFromRoom(triggerId)
                                        Toast.makeText(context, "Unpaired ${trig?.name ?: "trigger"}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }

                            ContextPulseTab.TRIGGERS -> {
                                TriggersScreen(
                                    triggers = beacons,
                                    contexts = rooms,
                                    beaconRepo = beaconRepository,
                                    lockManager = lockManager,
                                    onAddTriggerWithName = { name ->
                                        val created = beaconRepository.addUnconfiguredTrigger(name)
                                        currentScreen = AppScreen.ConfigureTrigger(created.id)
                                        Toast.makeText(context, "Created $name. Please configure hardware.", Toast.LENGTH_SHORT).show()
                                    },
                                    onOpenConfigureTrigger = { triggerId ->
                                        currentScreen = AppScreen.ConfigureTrigger(triggerId)
                                    },
                                    onDeleteTrigger = { triggerId ->
                                        beaconRepository.deleteSensor(triggerId)
                                        Toast.makeText(context, "Trigger removed", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }

                            ContextPulseTab.PERMISSIONS -> {
                                PermissionsScreen(
                                    state = permissionsState,
                                    permissionsRepository = permissionsRepository,
                                    onRequestLocation = {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    },
                                    onRequestBluetooth = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            permissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.BLUETOOTH_SCAN,
                                                    Manifest.permission.BLUETOOTH_CONNECT
                                                )
                                            )
                                        } else {
                                            permissionLauncher.launch(
                                                arrayOf(Manifest.permission.BLUETOOTH)
                                            )
                                        }
                                    },
                                    onRequestNotifications = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            permissionLauncher.launch(
                                                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
                                            )
                                        } else {
                                            permissionsRepository.openNotificationSettings(context)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    is AppScreen.ConfigureTrigger -> {
                        val targetTrigger = beacons.find { it.id == screen.triggerId }
                        if (targetTrigger != null) {
                            ConfigureTriggerScreen(
                                trigger = targetTrigger,
                                beaconRepo = beaconRepository,
                                onSaveTrigger = { updated ->
                                    beaconRepository.saveTrigger(updated)
                                },
                                onBack = { currentScreen = AppScreen.Tab(ContextPulseTab.TRIGGERS) }
                            )
                        } else {
                            currentScreen = AppScreen.Tab(ContextPulseTab.TRIGGERS)
                        }
                    }

                    is AppScreen.EditContext -> {
                        val targetRoom = rooms.find { it.id == screen.contextId }
                        if (targetRoom != null) {
                            EditContextScreen(
                                contextItem = targetRoom,
                                roomRepo = roomRepository,
                                onBack = { currentScreen = AppScreen.Tab(ContextPulseTab.CONTEXTS) },
                                onSave = { newName, newSubtitle, newIconKey, updatedProfile ->
                                    if (newName != targetRoom.name) {
                                        beaconRepository.updateAssignedRoomName(targetRoom.name, newName)
                                    }
                                    roomRepository.updateRoomFull(
                                        roomId = targetRoom.id,
                                        newName = newName,
                                        newSubtitle = newSubtitle,
                                        newIconName = newIconKey,
                                        newProfile = updatedProfile
                                    )
                                    currentScreen = AppScreen.Tab(ContextPulseTab.CONTEXTS)
                                    Toast.makeText(context, "Saved $newName context", Toast.LENGTH_SHORT).show()
                                }
                            )
                        } else {
                            currentScreen = AppScreen.Tab(ContextPulseTab.CONTEXTS)
                        }
                    }

                    AppScreen.ActivityHistory -> {
                        ActivityHistoryScreen(
                            activationLogs = activationLogs,
                            onBack = { currentScreen = AppScreen.Tab(ContextPulseTab.HOME) }
                        )
                    }

                    AppScreen.Settings -> {
                        SettingsScreen(
                            currentTheme = currentTheme,
                            onThemeSelected = { onSelectTheme(it) },
                            onBack = { currentScreen = AppScreen.Tab(ContextPulseTab.HOME) },
                            onTakeTour = { showTourGuide = true },
                            onResetConfigurations = {
                                contextEngine.deactivateRoom()
                                baselineStateManager.resetBaseline()
                                SystemControllers.restoreBrightness(context)
                                SystemControllers.restoreDnd(context)
                                SystemControllers.applyEyeComfort(context, false)
                                AppRestrictionMonitor.setRestrictedPackages(emptySet(), null)
                                AppRestrictionMonitor.stopContinuousMonitoring()
                                roomRepository.wipeAllProfiles()
                                beaconRepository.wipeAllBeacons()
                                com.contextpulse.phase0.repository.AppGroupRepository.getInstance(context).clearAll()
                                activationLogRepository.clearLogs()
                                permissionsRepository.revokeAllPermissions(context)
                                snoozeManager.cancelSnooze()
                                lockManager.disableLock()
                                onSelectTheme(com.contextpulse.phase0.ui.theme.AppThemeKey.CANARY_YELLOW)
                                try {
                                    com.contextpulse.phase0.widget.ContextPulseWidgetProvider.updateAllWidgets(context)
                                } catch (e: Throwable) {
                                    // Ignored
                                }
                                Toast.makeText(context, "All contexts & triggers wiped, logs cleared, app reset", Toast.LENGTH_LONG).show()
                                currentScreen = AppScreen.Tab(ContextPulseTab.HOME)
                            }
                        )
                    }
                }
            }
        }

        // Docked Bottom Navigation Bar strictly beneath content (shown on Tab screens)
        if (currentScreen is AppScreen.Tab) {
            ContextPulseBottomBar(
                selectedTab = (currentScreen as AppScreen.Tab).tab,
                onTabSelected = { currentScreen = AppScreen.Tab(it) }
            )
        }
    }
}
