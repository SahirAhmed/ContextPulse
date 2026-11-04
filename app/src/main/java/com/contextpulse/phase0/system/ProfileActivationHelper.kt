package com.contextpulse.phase0.system

import android.content.Context
import android.widget.Toast
import com.contextpulse.phase0.model.TriggerSource
import com.contextpulse.phase0.repository.ActivationLogRepository
import com.contextpulse.phase0.repository.RoomProfileRepository
import com.contextpulse.phase0.widget.ContextPulseWidgetProvider

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

        val targetRoom = roomRepo.rooms.value.find { it.id == roomId } ?: return false
        val previousRoomName = roomRepo.activeRoom.value?.name

        // 1. Update Context Engine & Room Repo
        contextEngine.activateRoom(roomId)
        roomRepo.setActiveRoom(roomId)

        // 2. Direct Hardware & System Automations
        val profile = targetRoom.profile

        // Brightness
        if (profile.brightnessEnabled) {
            SystemControllers.applyBrightness(context, profile.brightnessLevel)
        } else {
            SystemControllers.restoreBrightness(context)
        }

        // DND
        if (profile.dndEnabled) {
            SystemControllers.applyDndMode(context, profile.dndMode)
        } else {
            SystemControllers.restoreDnd(context)
        }

        // Apps Blocked
        if (profile.appRestrictionsEnabled && profile.restrictedApps.isNotEmpty()) {
            AppRestrictionMonitor.setRestrictedPackages(
                packages = profile.restrictedApps.toSet(),
                roomName = targetRoom.name
            )
            AppRestrictionMonitor.startContinuousMonitoring(context, 800L)
        } else {
            AppRestrictionMonitor.setRestrictedPackages(emptySet(), null)
            AppRestrictionMonitor.stopContinuousMonitoring()
        }

        // 3. Log activation
        activationLogRepo.logActivation(
            toProfileName = targetRoom.name,
            fromProfileName = previousRoomName,
            reason = reason,
            triggerSource = triggerSource
        )

        // 4. Update all widgets
        try {
            ContextPulseWidgetProvider.updateAllWidgets(context)
        } catch (e: Throwable) {
            // Ignored
        }

        return true
    }
}
