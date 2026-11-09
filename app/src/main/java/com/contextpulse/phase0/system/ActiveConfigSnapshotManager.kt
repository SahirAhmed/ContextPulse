package com.contextpulse.phase0.system

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import com.contextpulse.phase0.model.ContextTimeframeMode
import com.contextpulse.phase0.model.TriggerSource
import com.contextpulse.phase0.repository.RoomProfileRepository
import com.contextpulse.phase0.ui.StickyNoteOverlayActivity
import com.contextpulse.phase0.system.AppUsageBudgetManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages an isolated, persistent snapshot of active hardware and environment configurations.
 *
 * Core Workflow:
 * 1. During normal operation (when isPreviewActive == false), whenever a context or mode activates,
 *    [captureCurrentActive] records the live configuration values (brightness, DND, eye comfort,
 *    and app restrictions) into persistent SharedPreferences.
 * 2. When the user taps "Preview", [startPreview] locks the snapshot (isPreviewActive = true)
 *    and applies the previewed mode's settings without overwriting the saved baseline.
 * 3. When the preview ends (via "End Preview", screen back navigation, or saving), [endPreview]
 *    retrieves the frozen snapshot and faithfully re-applies each prior configuration.
 * 4. If the app is killed or terminated during an active preview, [checkAndRecoverInterruptedPreview]
 *    detects the dangling preview on next app launch and automatically restores the saved snapshot.
 */
object ActiveConfigSnapshotManager {

    private const val TAG = "ActiveConfigSnapshot"
    private const val PREFS_NAME = "contextpulse_active_config_snapshot"

    private const val KEY_IS_PREVIEW_ACTIVE = "is_preview_active"
    private const val KEY_PREVIEW_MODE_ID = "preview_mode_id"
    private const val KEY_PREVIEW_CONTEXT_NAME = "preview_context_name"

    // Snapshot fields
    private const val KEY_HAS_SNAPSHOT = "has_snapshot"
    private const val KEY_ACTIVE_ROOM_ID = "active_room_id"
    private const val KEY_ACTIVE_ROOM_NAME = "active_room_name"
    private const val KEY_ACTIVE_MODE_NAME = "active_mode_name"

    private const val KEY_BRIGHTNESS_ENABLED = "brightness_enabled"
    private const val KEY_BRIGHTNESS_LEVEL = "brightness_level"

    private const val KEY_DND_ENABLED = "dnd_enabled"
    private const val KEY_DND_MODE = "dnd_mode"

    private const val KEY_EYE_COMFORT_ENABLED = "eye_comfort_enabled"
    private const val KEY_EYE_COMFORT_LEVEL = "eye_comfort_level"

    private const val KEY_APP_RESTRICTIONS_ENABLED = "app_restrictions_enabled"
    private const val KEY_RESTRICTED_PACKAGES = "restricted_packages_json"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isPreviewActive(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_PREVIEW_ACTIVE, false)
    }

    fun hasSavedSnapshot(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_HAS_SNAPSHOT, false)
    }

    fun getActivePreviewModeId(context: Context): String? {
        return if (isPreviewActive(context)) {
            getPrefs(context).getString(KEY_PREVIEW_MODE_ID, null)
        } else {
            null
        }
    }

    /**
     * Records the current live settings to persistent storage.
     * Guaranteed to NEVER overwrite the snapshot if a preview is currently running.
     */
    fun captureCurrentActive(context: Context) {
        val prefs = getPrefs(context)
        if (prefs.getBoolean(KEY_IS_PREVIEW_ACTIVE, false)) {
            Log.d(TAG, "Skipping snapshot capture: Preview session is currently active")
            return
        }

        val roomRepo = RoomProfileRepository.getInstance(context)
        val activeRoom = roomRepo.activeRoom.value

        if (activeRoom != null) {
            val activeMode = activeRoom.profile.resolveActiveMode()
            val packagesJson = JSONArray(activeMode.restrictedApps).toString()

            prefs.edit()
                .putBoolean(KEY_HAS_SNAPSHOT, true)
                .putString(KEY_ACTIVE_ROOM_ID, activeRoom.id)
                .putString(KEY_ACTIVE_ROOM_NAME, activeRoom.name)
                .putString(KEY_ACTIVE_MODE_NAME, activeMode.name)
                .putBoolean(KEY_BRIGHTNESS_ENABLED, activeMode.brightnessEnabled)
                .putInt(KEY_BRIGHTNESS_LEVEL, activeMode.brightnessLevel)
                .putBoolean(KEY_DND_ENABLED, activeMode.dndEnabled)
                .putString(KEY_DND_MODE, activeMode.dndMode)
                .putBoolean(KEY_EYE_COMFORT_ENABLED, activeMode.eyeComfortEnabled)
                .putInt(KEY_EYE_COMFORT_LEVEL, activeMode.eyeComfortLevel)
                .putBoolean(KEY_APP_RESTRICTIONS_ENABLED, activeMode.appRestrictionsEnabled)
                .putString(KEY_RESTRICTED_PACKAGES, packagesJson)
                .apply()

            Log.d(TAG, "Captured active snapshot for context '${activeRoom.name}' (Mode: ${activeMode.name})")
        } else {
            // Baseline / No active context
            prefs.edit()
                .putBoolean(KEY_HAS_SNAPSHOT, true)
                .putString(KEY_ACTIVE_ROOM_ID, null)
                .putString(KEY_ACTIVE_ROOM_NAME, null)
                .putString(KEY_ACTIVE_MODE_NAME, "Baseline")
                .putBoolean(KEY_BRIGHTNESS_ENABLED, false)
                .putInt(KEY_BRIGHTNESS_LEVEL, 50)
                .putBoolean(KEY_DND_ENABLED, false)
                .putString(KEY_DND_MODE, "Priority Only")
                .putBoolean(KEY_EYE_COMFORT_ENABLED, false)
                .putInt(KEY_EYE_COMFORT_LEVEL, 50)
                .putBoolean(KEY_APP_RESTRICTIONS_ENABLED, false)
                .putString(KEY_RESTRICTED_PACKAGES, "[]")
                .apply()

            Log.d(TAG, "Captured baseline snapshot (No active context)")
        }
    }

    /**
     * Starts a live preview for the specified mode.
     * Freezes the currently saved snapshot and applies the preview mode's automations.
     */
    fun startPreview(context: Context, contextName: String, mode: ContextTimeframeMode) {
        val prefs = getPrefs(context)

        // Ensure we have a valid baseline snapshot before entering preview
        if (!prefs.getBoolean(KEY_HAS_SNAPSHOT, false) || !prefs.getBoolean(KEY_IS_PREVIEW_ACTIVE, false)) {
            captureCurrentActive(context)
        }

        // Lock snapshot against background updates
        prefs.edit()
            .putBoolean(KEY_IS_PREVIEW_ACTIVE, true)
            .putString(KEY_PREVIEW_MODE_ID, mode.id)
            .putString(KEY_PREVIEW_CONTEXT_NAME, contextName)
            .apply()

        Log.d(TAG, "Started preview session for mode '${mode.name}' in context '$contextName'")

        // Apply mode automations live
        applyModeAutomationsInternal(context, contextName, mode)
    }

    /**
     * Ends the preview session, unlocking the snapshot and restoring all prior active settings.
     */
    fun endPreview(context: Context, showToast: Boolean = true) {
        val prefs = getPrefs(context)
        if (!prefs.getBoolean(KEY_IS_PREVIEW_ACTIVE, false)) {
            Log.d(TAG, "No preview session is active to end")
            return
        }

        Log.d(TAG, "Ending preview session. Restoring saved snapshot...")

        val activeRoomId = prefs.getString(KEY_ACTIVE_ROOM_ID, null)
        val activeRoomName = prefs.getString(KEY_ACTIVE_ROOM_NAME, null)

        val brightnessEnabled = prefs.getBoolean(KEY_BRIGHTNESS_ENABLED, false)
        val brightnessLevel = prefs.getInt(KEY_BRIGHTNESS_LEVEL, 50)

        val dndEnabled = prefs.getBoolean(KEY_DND_ENABLED, false)
        val dndMode = prefs.getString(KEY_DND_MODE, "Priority Only") ?: "Priority Only"

        val eyeComfortEnabled = prefs.getBoolean(KEY_EYE_COMFORT_ENABLED, false)
        val eyeComfortLevel = prefs.getInt(KEY_EYE_COMFORT_LEVEL, 50)

        val appRestrictionsEnabled = prefs.getBoolean(KEY_APP_RESTRICTIONS_ENABLED, false)
        val packagesJson = prefs.getString(KEY_RESTRICTED_PACKAGES, "[]") ?: "[]"
        val restrictedPackages = parsePackagesJson(packagesJson)

        // 1. Restore Brightness
        if (brightnessEnabled) {
            SystemControllers.applyBrightness(context, brightnessLevel)
        } else {
            SystemControllers.restoreBrightness(context)
        }

        // 2. Restore DND
        if (dndEnabled) {
            SystemControllers.applyDndMode(context, dndMode)
        } else {
            SystemControllers.restoreDnd(context)
        }

        // 3. Restore Eye Comfort
        if (eyeComfortEnabled) {
            SystemControllers.applyEyeComfort(context, true, eyeComfortLevel)
        } else {
            SystemControllers.applyEyeComfort(context, false)
        }

        // 4. Restore App Restrictions
        if (appRestrictionsEnabled && restrictedPackages.isNotEmpty()) {
            AppRestrictionMonitor.setRestrictedPackages(
                packages = restrictedPackages,
                roomName = activeRoomName
            )
            AppRestrictionMonitor.startContinuousMonitoring(context, 200L)
        } else {
            AppRestrictionMonitor.setRestrictedPackages(emptySet(), null)
            AppRestrictionMonitor.stopContinuousMonitoring()
        }

        // 5. Restore Active Context in RoomProfileRepository
        val roomRepo = RoomProfileRepository.getInstance(context)
        if (activeRoomId != null && roomRepo.rooms.value.any { it.id == activeRoomId }) {
            roomRepo.setActiveRoom(activeRoomId)
        } else {
            roomRepo.setActiveRoom(null)
        }

        // 6. Reset preview state
        prefs.edit()
            .putBoolean(KEY_IS_PREVIEW_ACTIVE, false)
            .remove(KEY_PREVIEW_MODE_ID)
            .remove(KEY_PREVIEW_CONTEXT_NAME)
            .apply()

        // 7. Immediately reevaluate real active context and currently scheduled mode
        ProfileActivationHelper.reapplyCurrentActive(context)

        // 8. Recalculate next schedule boundary alarm so schedule transitions remain precise
        ModeScheduleManager.scheduleNextBoundaryAlarm(context)

        Log.d(TAG, "Successfully restored settings from snapshot and re-evaluated live schedule. Preview ended.")

        if (showToast) {
            val label = if (!activeRoomName.isNullOrBlank()) "active context ($activeRoomName)" else "baseline settings"
            Toast.makeText(context, "Restored $label", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Called during MainActivity.onCreate() to automatically recover device settings
     * if the app was unexpectedly killed or restarted while a preview was running.
     */
    fun checkAndRecoverInterruptedPreview(context: Context) {
        val prefs = getPrefs(context)
        if (prefs.getBoolean(KEY_IS_PREVIEW_ACTIVE, false)) {
            Log.w(TAG, "Interrupted preview session detected upon app start! Restoring saved configurations...")
            endPreview(context, showToast = false)
            Toast.makeText(context, "Restored saved settings after interrupted preview", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyModeAutomationsInternal(
        context: Context,
        contextName: String,
        mode: ContextTimeframeMode
    ) {
        // Brightness
        if (mode.brightnessEnabled) {
            SystemControllers.applyBrightness(context, mode.brightnessLevel)
        } else {
            SystemControllers.restoreBrightness(context)
        }

        // DND
        if (mode.dndEnabled) {
            SystemControllers.applyDndMode(context, mode.dndMode)
        } else {
            SystemControllers.restoreDnd(context)
        }

        // Eye Comfort
        if (mode.eyeComfortEnabled) {
            SystemControllers.applyEyeComfort(context, true, mode.eyeComfortLevel)
        } else {
            SystemControllers.applyEyeComfort(context, false)
        }

        // Apps Blocked & Usage Budgets
        val hasRestricted = mode.appRestrictionsEnabled &&
            (mode.restrictedApps.isNotEmpty() || mode.appBudgets.isNotEmpty())

        if (hasRestricted) {
            AppUsageBudgetManager.setActiveContextBudgets(
                context = context,
                contextId = "preview_${mode.id}",
                contextName = "$contextName (Preview)",
                budgets = mode.appBudgets
            )
            AppRestrictionMonitor.setRestrictedPackages(
                packages = mode.restrictedApps.toSet(),
                roomName = "$contextName (Preview)"
            )
            AppRestrictionMonitor.startContinuousMonitoring(context, 200L)
        } else {
            AppUsageBudgetManager.setActiveContextBudgets(context, "", "", emptyList())
            AppRestrictionMonitor.setRestrictedPackages(emptySet(), null)
            AppRestrictionMonitor.stopContinuousMonitoring()
        }

        // Auto-Open App
        if (mode.autoLaunchAppEnabled && mode.autoLaunchPackage.isNotBlank()) {
            ProfileActivationHelper.launchTargetApp(context, mode.autoLaunchPackage)
        }

        // Sticky Note System Overlay
        if (mode.stickyNoteEnabled && (mode.stickyNoteText.isNotBlank() || mode.stickyNoteChecklist.isNotEmpty())) {
            StickyNoteOverlayActivity.show(
                context = context,
                contextName = contextName,
                modeName = "${mode.name} (Preview)",
                noteText = mode.stickyNoteText,
                checklist = mode.stickyNoteChecklist
            )
        }
    }

    private fun parsePackagesJson(jsonStr: String): Set<String> {
        return try {
            val jsonArray = JSONArray(jsonStr)
            val set = mutableSetOf<String>()
            for (i in 0 until jsonArray.length()) {
                val pkg = jsonArray.optString(i, "")
                if (pkg.isNotBlank()) {
                    set.add(pkg)
                }
            }
            set
        } catch (_: Exception) {
            emptySet()
        }
    }
}
