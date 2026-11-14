package com.contextpulse.phase0.system

import com.contextpulse.phase0.model.ContextTimeframeMode
import com.contextpulse.phase0.model.RoomItem
import com.contextpulse.phase0.model.RoomProfile

/**
 * Pure state-machine model representing Preview Mode lifecycle states and transitions.
 * Allows thorough, 100% isolated verification of preview activation, schedule lock,
 * and boundary recovery without invoking Android hardware mutators.
 */
data class PreviewState(
    val isPreviewActive: Boolean = false,
    val previewModeId: String? = null,
    val previewContextName: String? = null,
    val savedSnapshot: PreviewSnapshot? = null
)

data class PreviewSnapshot(
    val activeRoomId: String?,
    val activeRoomName: String?,
    val activeModeId: String?,
    val brightnessPercent: Int,
    val dndMode: String,
    val restrictedPackages: List<String>
)

object PreviewStateEngine {

    /**
     * Starts a preview session, freezing the pre-preview configuration in the snapshot.
     */
    fun startPreview(
        currentState: PreviewState,
        activeContext: RoomItem?,
        previewContextName: String,
        previewMode: ContextTimeframeMode
    ): PreviewState {
        val currentMode = activeContext?.profile?.resolveActiveMode()
        val snapshot = currentState.savedSnapshot ?: PreviewSnapshot(
            activeRoomId = activeContext?.id,
            activeRoomName = activeContext?.name,
            activeModeId = currentMode?.id,
            brightnessPercent = currentMode?.brightnessPercent ?: 50,
            dndMode = currentMode?.dndMode ?: "Priority Only",
            restrictedPackages = currentMode?.restrictedApps ?: emptyList()
        )

        return PreviewState(
            isPreviewActive = true,
            previewModeId = previewMode.id,
            previewContextName = previewContextName,
            savedSnapshot = snapshot
        )
    }

    /**
     * Re-evaluates whether a background schedule boundary should override current state.
     * When isPreviewActive == true, the live schedule transition MUST be suppressed.
     */
    fun shouldApplyScheduleBoundary(state: PreviewState): Boolean {
        return !state.isPreviewActive
    }

    /**
     * Ends the preview session.
     * Returns the target state with isPreviewActive = false and resolves the
     * actual live mode that should be applied at the current time epoch.
     */
    fun endPreview(
        state: PreviewState,
        activeContext: RoomItem?,
        currentMinuteOfDay: Int,
        currentDayOfWeek: Int
    ): Pair<PreviewState, ContextTimeframeMode?> {
        val resolvedLiveMode = activeContext?.profile?.resolveActiveMode(currentMinuteOfDay, currentDayOfWeek)
        val clearedState = state.copy(
            isPreviewActive = false,
            previewModeId = null,
            previewContextName = null
        )
        return Pair(clearedState, resolvedLiveMode)
    }
}
