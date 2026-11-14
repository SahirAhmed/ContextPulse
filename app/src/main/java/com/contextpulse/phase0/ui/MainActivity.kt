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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.contextpulse.phase0.system.AppUsageBudgetManager
import com.contextpulse.phase0.system.BaselineStateManager
import com.contextpulse.phase0.system.ContextEngine
import com.contextpulse.phase0.system.SnoozeManager
import com.contextpulse.phase0.system.SystemControllers
import com.contextpulse.phase0.model.ContextTimeframeMode
import com.contextpulse.phase0.ui.components.ContextPulseBottomBar
import com.contextpulse.phase0.ui.components.ContextPulseTab
import com.contextpulse.phase0.ui.components.ContextPulseTopBar
import com.contextpulse.phase0.ui.components.SnoozeDialog
import com.contextpulse.phase0.ui.StickyNoteOverlayActivity
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
            com.contextpulse.phase0.system.ActiveConfigSnapshotManager.checkAndRecoverInterruptedPreview(this)
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Error checking interrupted preview", e)
        }
        try {
            com.contextpulse.phase0.ble.Phase0BleService.start(this)
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Failed to start Phase0BleService from onCreate", e)
        }
        com.contextpulse.phase0.ui.components.prewarmInstalledApps(this)
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

    override fun onResume() {
        super.onResume()
        // If an interrupted guided hardware flash test was left pending, attempt restoration upon returning to foreground
        com.contextpulse.phase0.testing.GuidedTestRecoveryJournal.checkAndAttemptRecovery(this)
        enableNfcForegroundDispatch()
    }

    override fun onPause() {
        disableNfcForegroundDispatch()
        super.onPause()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTriggerDeepLink(intent)
    }

    private fun enableNfcForegroundDispatch() {
        try {
            val adapter = android.nfc.NfcAdapter.getDefaultAdapter(this) ?: return
            if (!adapter.isEnabled) return
            val intent = android.content.Intent(this, javaClass).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
            } else {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent, flags)

            val githubHttpsFilter = android.content.IntentFilter(android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                addCategory(android.content.Intent.CATEGORY_DEFAULT)
                addDataScheme("https")
                addDataScheme("http")
                addDataAuthority("sahirahmed.github.io", null)
                addDataAuthority("sahir01ahmed.github.io", null)
            }
            val customSchemeFilter = android.content.IntentFilter(android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                addCategory(android.content.Intent.CATEGORY_DEFAULT)
                addDataScheme("contextpulse")
                addDataAuthority("trigger", null)
            }
            adapter.enableForegroundDispatch(
                this,
                pendingIntent,
                arrayOf(githubHttpsFilter, customSchemeFilter),
                null
            )
        } catch (_: Exception) {
        }
    }

    private fun disableNfcForegroundDispatch() {
        try {
            android.nfc.NfcAdapter.getDefaultAdapter(this)?.disableForegroundDispatch(this)
        } catch (_: Exception) {
        }
    }

    private fun handleTriggerDeepLink(intent: android.content.Intent?) {
        if (intent == null) return
        val extraction = com.contextpulse.phase0.util.NfcTagHelper.extractFromIntent(intent)
        val rawUriString = extraction.uriOrPayload
        val hardwareUid = extraction.hardwareUid

        val data = intent.data ?: rawUriString?.let {
            try {
                android.net.Uri.parse(it)
            } catch (_: Exception) {
                null
            }
        }

        val rawKey = if (data != null) {
            val scheme = data.scheme ?: ""
            val host = data.host ?: ""
            val path = data.path ?: ""
            when {
                // Query param: e.g. https://sahirahmed.github.io/ContextPulse/?trigger=xyz
                !data.getQueryParameter("trigger").isNullOrBlank() -> data.getQueryParameter("trigger")
                !data.getQueryParameter("context").isNullOrBlank() -> data.getQueryParameter("context")
                scheme == "contextpulse" && host == "trigger" -> data.lastPathSegment ?: path.removePrefix("/")
                scheme == "contextpulse" -> data.getQueryParameter("trigger") ?: data.lastPathSegment ?: path.removePrefix("/")
                (scheme == "https" || scheme == "http") && (host.contains("sahirahmed.github.io") || host.contains("sahir01ahmed.github.io")) -> {
                    data.getQueryParameter("trigger") ?: data.getQueryParameter("context") ?: data.lastPathSegment
                }
                (scheme == "https" || scheme == "http") && path.contains("trigger") -> {
                    data.getQueryParameter("trigger") ?: data.lastPathSegment
                }
                else -> data.getQueryParameter("trigger") ?: data.lastPathSegment
            }
        } else {
            null
        } ?: rawUriString ?: hardwareUid ?: return

        com.contextpulse.phase0.util.QrTriggerHandler.processQrPayload(
            context = this,
            rawPayload = rawKey,
            hardwareUid = hardwareUid
        )

        // Prevent re-processing the same deep link / NFC tap across configuration changes
        intent.data = null
        intent.removeExtra(android.nfc.NfcAdapter.EXTRA_NDEF_MESSAGES)
        intent.removeExtra(android.nfc.NfcAdapter.EXTRA_ID)
        intent.removeExtra(android.nfc.NfcAdapter.EXTRA_TAG)
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
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Automatically refresh all permissions whenever returning from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                permissionsRepository.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isLockActive by lockManager.isLockEnabled.collectAsState()
    val isAppAccessUnlocked by lockManager.isAppAccessUnlockedFlow.collectAsState()
    var showSnoozeLockDialog by remember { mutableStateOf(false) }

    val appScreenSaver = remember {
        Saver<AppScreen, String>(
            save = { screen ->
                when (screen) {
                    is AppScreen.Tab -> "TAB:${screen.tab.name}"
                    is AppScreen.EditContext -> "EDIT_CONTEXT:${screen.contextId}"
                    is AppScreen.ConfigureTrigger -> "CONFIG_TRIGGER:${screen.triggerId}"
                    AppScreen.ActivityHistory -> "HISTORY"
                    AppScreen.Settings -> "SETTINGS"
                }
            },
            restore = { saved ->
                when {
                    saved.startsWith("TAB:") -> {
                        val tab = runCatching { ContextPulseTab.valueOf(saved.removePrefix("TAB:")) }
                            .getOrDefault(ContextPulseTab.HOME)
                        AppScreen.Tab(tab)
                    }
                    saved.startsWith("EDIT_CONTEXT:") -> AppScreen.EditContext(saved.removePrefix("EDIT_CONTEXT:"))
                    saved.startsWith("CONFIG_TRIGGER:") -> AppScreen.ConfigureTrigger(saved.removePrefix("CONFIG_TRIGGER:"))
                    saved == "HISTORY" -> AppScreen.ActivityHistory
                    saved == "SETTINGS" -> AppScreen.Settings
                    else -> AppScreen.Tab(ContextPulseTab.HOME)
                }
            }
        )
    }

    var currentScreen by rememberSaveable(stateSaver = appScreenSaver) {
        mutableStateOf<AppScreen>(AppScreen.Tab(ContextPulseTab.HOME))
    }

    // First launch tour guide state
    val tourPrefs = remember { context.getSharedPreferences("contextpulse_tour_prefs", Context.MODE_PRIVATE) }
    var showTourGuide by remember { mutableStateOf(!tourPrefs.getBoolean("tour_completed", false)) }

    // Snooze dialog state & live ticking seconds countdown
    var showSnoozeDialog by remember { mutableStateOf(false) }
    var showQrScannerDialog by remember { mutableStateOf(false) }
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
    val activeRoomStartedAt by roomRepository.activeRoomStartedAt.collectAsState()
    val beacons by beaconRepository.beacons.collectAsState()
    val activationLogs by activationLogRepository.activationLogs.collectAsState()
    val permissionsState by permissionsRepository.state.collectAsState()

    var isInitialAppLaunch by remember { mutableStateOf(true) }
    var previousActiveRoomId by remember { mutableStateOf<String?>(activeRoomId) }

    // Background engine (Phase0BleService) authoritatively coordinates and applies automations.
    // UI only ensures home screen widgets remain synchronized.
    LaunchedEffect(activeRoom, isSnoozed) {
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

    Box(modifier = Modifier.fillMaxSize()) {
        // Main column layout: TopBar -> Content (weight 1f) -> BottomBar
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(currentTheme.background)
        ) {
        // Consistent Brand Top Bar across all 4 tab screens with ZZZ sleep button
        if (currentScreen is AppScreen.Tab) {
            ContextPulseTopBar(
                onHomeClick = { currentScreen = AppScreen.Tab(ContextPulseTab.HOME) },
                onSettingsClick = { currentScreen = AppScreen.Settings },
                onSnoozeClick = {
                    if (lockManager.isLockedForAction()) {
                        showSnoozeLockDialog = true
                    } else {
                        showSnoozeDialog = true
                    }
                },
                onScanQrClick = { showQrScannerDialog = true },
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
                .then(
                    if (currentScreen is AppScreen.Tab) {
                        Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    } else {
                        Modifier
                    }
                )
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
                                val systemDnd = remember {
                                    (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                                }
                                val systemBrightness = remember {
                                    try {
                                        val cur = android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS)
                                        (cur * 100 / 255)
                                    } catch (_: Exception) { 50 }
                                }
                                HomeScreen(
                                    activeRoom = activeRoom,
                                    activeStartedAt = activeRoomStartedAt,
                                    allRooms = rooms,
                                    triggersCount = beacons.size,
                                    isSnoozed = isSnoozed,
                                    snoozeRemainingSeconds = currentRemainingSeconds,
                                    systemDndActive = systemDnd,
                                    systemBrightnessPercent = systemBrightness,
                                    recentLogs = activationLogs,
                                    onActivateRoom = { room ->
                                        com.contextpulse.phase0.system.ProfileActivationHelper.activateProfile(
                                            context = context,
                                            roomId = room.id,
                                            triggerSource = TriggerSource.MANUAL,
                                            reason = "Switched via Quick Switch"
                                        )
                                    },
                                    onDeactivateActiveRoom = {
                                        val activeId = activeRoom?.id
                                        if (activeId != null) {
                                            com.contextpulse.phase0.system.ProfileActivationHelper.toggleOrActivateFromTrigger(
                                                context = context,
                                                roomId = activeId,
                                                triggerSource = TriggerSource.MANUAL,
                                                triggerName = "Home Deactivate"
                                            )
                                        }
                                    },
                                    onEditActiveRoom = { room ->
                                        currentScreen = AppScreen.EditContext(room.id)
                                    },
                                    onStartSnooze = { minutes ->
                                        snoozeManager.startSnooze(minutes)
                                    },
                                    onCancelSnooze = {
                                        snoozeManager.cancelSnooze()
                                    },
                                    onViewAllLogs = { currentScreen = AppScreen.ActivityHistory },
                                    onStartTour = { showTourGuide = true },
                                    onNavigateToContexts = { currentScreen = AppScreen.Tab(ContextPulseTab.CONTEXTS) },
                                    onNavigateToTriggers = { currentScreen = AppScreen.Tab(ContextPulseTab.TRIGGERS) },
                                    onOpenStickyNote = {
                                        if (activeRoom != null) {
                                            val m = activeRoom!!.profile.resolveActiveMode()
                                            StickyNoteOverlayActivity.show(
                                                context = context,
                                                contextName = activeRoom!!.name,
                                                modeName = m.name,
                                                noteText = m.stickyNoteText,
                                                checklist = m.stickyNoteChecklist
                                            )
                                        }
                                    }
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
                                        if (activeRoomId == contextItem.id) {
                                            com.contextpulse.phase0.system.ProfileActivationHelper.toggleOrActivateFromTrigger(
                                                context = context,
                                                roomId = contextItem.id,
                                                triggerSource = TriggerSource.MANUAL,
                                                triggerName = "Manual Switch"
                                            )
                                        } else {
                                            com.contextpulse.phase0.system.ProfileActivationHelper.activateProfile(
                                                context = context,
                                                roomId = contextItem.id,
                                                triggerSource = TriggerSource.MANUAL,
                                                reason = "The context was manually changed by user in Contexts screen"
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
                                    onAttachTrigger = { triggerId, contextId, contextName ->
                                        val trig = beacons.find { it.id == triggerId }
                                        val attached = beaconRepository.attachBeaconToContext(triggerId, contextId, contextName)
                                        if (attached) {
                                            Toast.makeText(context, "Paired ${trig?.name ?: "trigger"} to $contextName", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val locked = beaconRepository.getLockedTriggerTypeForContext(contextId, contextName)
                                            val lockedLabel = locked?.getDisplayName() ?: "matching"
                                            Toast.makeText(
                                                context,
                                                "$contextName only accepts $lockedLabel triggers",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
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
                                    onAddTriggerWithName = { name, type ->
                                        val created = beaconRepository.addUnconfiguredTrigger(name, type)
                                        currentScreen = AppScreen.ConfigureTrigger(created.id)
                                        Toast.makeText(context, "Created $name (${type.getDisplayName()}). Please configure hardware.", Toast.LENGTH_SHORT).show()
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
                                onSaved = { currentScreen = AppScreen.Tab(ContextPulseTab.TRIGGERS) },
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
                                    if (activeRoomId == targetRoom.id) {
                                        com.contextpulse.phase0.system.ProfileActivationHelper.reapplyCurrentActive(context)
                                    }
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
                            logs = activationLogs,
                            onClearLogs = { activationLogRepository.clearLogs() },
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
                                permissionsRepository.revokeAllPermissions()
                                snoozeManager.cancelSnooze()
                                lockManager.disableLock()
                                tourPrefs.edit().putBoolean("tour_completed", false).apply()
                                showTourGuide = true
                                onSelectTheme(com.contextpulse.phase0.ui.theme.AppThemeKey.CANARY_YELLOW)
                                try {
                                    com.contextpulse.phase0.widget.ContextPulseWidgetProvider.updateAllWidgets(context)
                                } catch (e: Throwable) {
                                    // Ignored
                                }
                                Toast.makeText(context, "Configurations reset. Starting interactive tour...", Toast.LENGTH_LONG).show()
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

    // Virtual Tour Guide Overlay (Rendered above Column)
    if (showTourGuide) {
        TourGuideOverlay(
            onDismiss = {
                tourPrefs.edit().putBoolean("tour_completed", true).apply()
                showTourGuide = false
                currentScreen = AppScreen.Tab(ContextPulseTab.HOME)
            },
            onNavigateStep = { targetTab, isSettings ->
                currentScreen = if (isSettings) {
                    AppScreen.Settings
                } else if (targetTab != null) {
                    AppScreen.Tab(targetTab)
                } else {
                    AppScreen.Tab(ContextPulseTab.HOME)
                }
            },
            onFinishTour = {
                currentScreen = AppScreen.Tab(ContextPulseTab.HOME)
            }
        )
    }

    // App Entrance Security Lock: If lock is enabled and app access is not unlocked, show fullscreen lock
    if (isLockActive && !isAppAccessUnlocked) {
        com.contextpulse.phase0.ui.components.AppLockDialog(
            lockManager = lockManager,
            title = "ContextPulse Locked",
            subtitle = "Enter 6-digit PIN or scan fingerprint to access",
            isAppEntrance = true,
            onSuccess = {
                lockManager.markAppAccessUnlocked()
            },
            onDismiss = {
                // For app entrance, dismissing does not grant access
            }
        )
    }

    // Snooze Lock Verification Dialog
    if (showSnoozeLockDialog) {
        com.contextpulse.phase0.ui.components.AppLockDialog(
            lockManager = lockManager,
            title = "Security Verification",
            subtitle = "Enter PIN or scan fingerprint to pause automations",
            isAppEntrance = false,
            onSuccess = {
                showSnoozeLockDialog = false
                showSnoozeDialog = true
            },
            onDismiss = {
                showSnoozeLockDialog = false
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

    // In-App QR Code Scanner Dialog
    if (showQrScannerDialog) {
        com.contextpulse.phase0.ui.components.QrScannerDialog(
            onDismiss = { showQrScannerDialog = false },
            onQrScanned = { payload ->
                showQrScannerDialog = false
                com.contextpulse.phase0.util.QrTriggerHandler.processQrPayload(context, payload)
            }
        )
    }
}
}
