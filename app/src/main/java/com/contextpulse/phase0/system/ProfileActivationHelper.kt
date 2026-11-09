package com.contextpulse.phase0.system

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.contextpulse.phase0.model.ContextTimeframeMode
import com.contextpulse.phase0.model.TriggerSource
import com.contextpulse.phase0.repository.ActivationLogRepository
import com.contextpulse.phase0.repository.RoomProfileRepository
import com.contextpulse.phase0.ui.StickyNoteOverlayActivity
import com.contextpulse.phase0.widget.ContextPulseWidgetProvider
import com.contextpulse.phase0.system.AppUsageBudgetManager

object ProfileActivationHelper {

    fun activateProfile(
        context: Context,
        roomId: String,
        triggerSource: TriggerSource = TriggerSource.MANUAL,
        reason: String = "Manual switch"
    ): Boolean {
        val roomRepo = RoomProfileRepository.getInstance(context)
        val activationLogRepo = ActivationLogRepository.getInstance(context)
        val contextEngine = ContextEngine.getInstance(context)

        val outgoingRoom = roomRepo.activeRoom.value
        val targetRoom = roomRepo.rooms.value.find { it.id == roomId } ?: return false
        val previousRoomName = outgoingRoom?.name

        // 1. Update Context Engine & Room Repo
        contextEngine.activateRoom(roomId)
        roomRepo.setActiveRoom(roomId)

        // 2. Execute Intelligent Delta-Based Context Transition
        val activeMode = targetRoom.profile.resolveActiveMode()
        val diffResult = TransitionDiffEngine.executeIntelligentTransition(
            context = context,
            outgoingContext = outgoingRoom,
            incomingContext = targetRoom,
            incomingMode = activeMode,
            triggerReason = reason
        )

        // 3. Log activation with rich transition diff summary
        val richReason = if (diffResult.summary.isNotBlank()) "$reason (${diffResult.summary})" else reason
        activationLogRepo.logActivation(
            toProfileName = targetRoom.name,
            fromProfileName = previousRoomName,
            reason = richReason,
            triggerSource = triggerSource
        )

        // 4. Send device notification
        try {
            ContextPulseNotificationManager.getInstance(context).notifyContextChanged(
                fromContextName = previousRoomName,
                toContextName = targetRoom.name,
                reason = reason
            )
        } catch (e: Throwable) {
            // Ignored
        }

        // 5. Update all widgets
        try {
            ContextPulseWidgetProvider.updateAllWidgets(context)
        } catch (e: Throwable) {
            // Ignored
        }

        // 6. Persist active configuration snapshot (never overwrites if preview is active)
        ActiveConfigSnapshotManager.captureCurrentActive(context)

        // 7. Schedule exact or low-drift AlarmManager wakeup for next mode boundary
        ModeScheduleManager.scheduleNextBoundaryAlarm(context)

        return true
    }

    fun toggleOrActivateFromTrigger(
        context: Context,
        roomId: String,
        triggerSource: TriggerSource,
        triggerName: String
    ): Boolean {
        val roomRepo = RoomProfileRepository.getInstance(context)
        val activeId = roomRepo.activeRoomId.value
        val targetRoom = roomRepo.rooms.value.find { it.id == roomId } ?: return false

        // QR Code triggers should only activate and never deactivate if scanned again
        if (triggerSource == TriggerSource.QR_CODE) {
            activateProfile(
                context = context,
                roomId = roomId,
                triggerSource = triggerSource,
                reason = "Activated via $triggerName QR scan"
            )
            Toast.makeText(context, "Activated '${targetRoom.name}' via QR", Toast.LENGTH_SHORT).show()
            return true
        }

        return if (activeId == roomId) {
            // Already active: deactivate back to baseline
            val contextEngine = ContextEngine.getInstance(context)
            val actLog = ActivationLogRepository.getInstance(context)
            contextEngine.deactivateRoom()
            roomRepo.setActiveRoom(null)
            SystemControllers.restoreBrightness(context)
            SystemControllers.restoreDnd(context)
            SystemControllers.applyEyeComfort(context, false)
            AppRestrictionMonitor.setRestrictedPackages(emptySet(), null)
            AppRestrictionMonitor.stopContinuousMonitoring()
            AppUsageBudgetManager.setActiveContextBudgets(context, "", "", emptyList())
            ActiveConfigSnapshotManager.captureCurrentActive(context)
            ModeScheduleManager.cancelBoundaryAlarm(context)
            actLog.logActivation(
                toProfileName = "Baseline (Default)",
                fromProfileName = targetRoom.name,
                reason = "Deactivated via $triggerName scan",
                triggerSource = triggerSource
            )
            try {
                ContextPulseNotificationManager.getInstance(context).notifyContextChanged(
                    fromContextName = targetRoom.name,
                    toContextName = "Baseline (Default)",
                    reason = "Trigger deactivated: $triggerName"
                )
            } catch (e: Throwable) {
                // Ignored
            }
            try {
                ContextPulseWidgetProvider.updateAllWidgets(context)
            } catch (e: Throwable) {
                // Ignored
            }
            Toast.makeText(context, "Deactivated '${targetRoom.name}'", Toast.LENGTH_SHORT).show()
            true
        } else {
            activateProfile(
                context = context,
                roomId = roomId,
                triggerSource = triggerSource,
                reason = "Activated via $triggerName scan"
            )
            Toast.makeText(context, "Activated '${targetRoom.name}'", Toast.LENGTH_SHORT).show()
            true
        }
    }

    /**
     * Reapplies automations for the currently active context (e.g. after snooze ends).
     */
    fun reapplyCurrentActive(context: Context) {
        val roomRepo = RoomProfileRepository.getInstance(context)
        val activeRoom = roomRepo.activeRoom.value
        val snoozeManager = SnoozeManager.getInstance(context)

        if (snoozeManager.isSnoozeActive()) {
            // Suspended
            SystemControllers.restoreBrightness(context)
            SystemControllers.restoreDnd(context)
            SystemControllers.applyEyeComfort(context, false)
            AppRestrictionMonitor.setRestrictedPackages(emptySet(), null)
            AppRestrictionMonitor.stopContinuousMonitoring()
            return
        }

        if (activeRoom != null) {
            val activeMode = activeRoom.profile.resolveActiveMode()

            if (activeMode.brightnessEnabled) {
                SystemControllers.applyBrightness(context, activeMode.brightnessLevel)
            } else {
                SystemControllers.restoreBrightness(context)
            }

            if (activeMode.dndEnabled) {
                SystemControllers.applyDndMode(context, activeMode.dndMode)
            } else {
                SystemControllers.restoreDnd(context)
            }

            if (activeMode.eyeComfortEnabled) {
                SystemControllers.applyEyeComfort(context, true, activeMode.eyeComfortLevel)
            } else {
                SystemControllers.applyEyeComfort(context, false)
            }

            val hasRestricted = activeMode.appRestrictionsEnabled &&
                (activeMode.restrictedApps.isNotEmpty() || activeMode.appBudgets.isNotEmpty())

            if (hasRestricted) {
                AppUsageBudgetManager.setActiveContextBudgets(
                    context = context,
                    contextId = activeRoom.id,
                    contextName = activeRoom.name,
                    budgets = activeMode.appBudgets
                )
                AppRestrictionMonitor.setRestrictedPackages(
                    packages = activeMode.restrictedApps.toSet(),
                    roomName = activeRoom.name
                )
                AppRestrictionMonitor.startContinuousMonitoring(context, 200L)
            } else {
                AppUsageBudgetManager.setActiveContextBudgets(context, "", "", emptyList())
                AppRestrictionMonitor.setRestrictedPackages(emptySet(), null)
                AppRestrictionMonitor.stopContinuousMonitoring()
            }

            ModeScheduleManager.scheduleNextBoundaryAlarm(context)
        } else {
            SystemControllers.restoreBrightness(context)
            SystemControllers.restoreDnd(context)
            SystemControllers.applyEyeComfort(context, false)
            AppUsageBudgetManager.setActiveContextBudgets(context, "", "", emptyList())
            AppRestrictionMonitor.setRestrictedPackages(emptySet(), null)
            AppRestrictionMonitor.stopContinuousMonitoring()
            ModeScheduleManager.cancelBoundaryAlarm(context)
        }
    }

    fun launchTargetApp(context: Context, packageName: String) {
        if (packageName.isBlank()) return
        try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(launchIntent)
            } else {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                context.startActivity(intent)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Applies automations of a specific mode directly for real-time live preview.
     * Uses ActiveConfigSnapshotManager to freeze current active configurations in SharedPreferences
     * so they can be reliably restored without loss.
     */
    fun previewModeAutomations(
        context: Context,
        contextName: String,
        mode: ContextTimeframeMode
    ) {
        ActiveConfigSnapshotManager.startPreview(context, contextName, mode)
    }

    /**
     * Restores device state after a mode preview session ends.
     * Retrieves the persistent snapshot from SharedPreferences and faithfully restores all settings.
     */
    fun restoreAfterPreview(context: Context, restoreRoomId: String? = null) {
        ActiveConfigSnapshotManager.endPreview(context)
    }
}

