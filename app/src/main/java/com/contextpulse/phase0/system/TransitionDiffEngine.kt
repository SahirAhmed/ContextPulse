package com.contextpulse.phase0.system

import android.content.Context
import android.util.Log
import com.contextpulse.phase0.model.ContextItem
import com.contextpulse.phase0.model.ContextTimeframeMode
import com.contextpulse.phase0.ui.StickyNoteOverlayActivity

data class TransitionDiffResult(
    val summary: String,
    val deltaItems: List<String>
)

/**
 * Intelligent Context Transition Engine:
 * - Computes the exact delta between outgoing context/mode and incoming context/mode.
 * - Resolves conflicts by giving precedence to the incoming context while leaving unchanged
 *   settings undisturbed (preventing screen flicker, notification jumps, or audio pops).
 * - Restores authentic device baselines whenever the incoming context does not control a setting
 *   that the outgoing context did.
 * - Formats a comprehensive transition diff for the Activity History log.
 */
object TransitionDiffEngine {

    private const val TAG = "TransitionDiffEngine"

    fun computeDiffWithoutApplying(
        outgoingContext: ContextItem?,
        incomingContext: ContextItem,
        incomingMode: ContextTimeframeMode
    ): TransitionDiffResult {
        val outgoingMode = outgoingContext?.profile?.resolveActiveMode()
        val deltas = mutableListOf<String>()

        // 1. Screen Brightness
        if (incomingMode.brightnessEnabled) {
            val incomingLevel = incomingMode.brightnessLevel
            if (outgoingMode?.brightnessEnabled == true && outgoingMode.brightnessLevel == incomingLevel) {
                deltas.add("Brightness: Kept at $incomingLevel% (unchanged)")
            } else {
                val outLabel = if (outgoingMode?.brightnessEnabled == true) "${outgoingMode.brightnessLevel}%" else "Baseline"
                deltas.add("Brightness: $outLabel ➔ $incomingLevel%")
            }
        } else {
            if (outgoingMode?.brightnessEnabled == true) {
                deltas.add("Brightness: Restored to baseline")
            }
        }

        // 2. Do Not Disturb (DND)
        if (incomingMode.dndEnabled) {
            val incomingDnd = incomingMode.dndMode
            if (outgoingMode?.dndEnabled == true && outgoingMode.dndMode == incomingDnd) {
                deltas.add("DND: Kept as '$incomingDnd' (unchanged)")
            } else {
                val outLabel = if (outgoingMode?.dndEnabled == true) outgoingMode.dndMode else "Off"
                deltas.add("DND: $outLabel ➔ $incomingDnd")
            }
        } else {
            if (outgoingMode?.dndEnabled == true) {
                deltas.add("DND: Deactivated (restored to baseline)")
            }
        }

        // 3. Eye Comfort
        if (incomingMode.eyeComfortEnabled) {
            val incomingIntensity = incomingMode.eyeComfortLevel
            if (outgoingMode?.eyeComfortEnabled == true && outgoingMode.eyeComfortLevel == incomingIntensity) {
                deltas.add("Eye Comfort: Kept at $incomingIntensity% (unchanged)")
            } else {
                deltas.add("Eye Comfort: Activated ($incomingIntensity%)")
            }
        } else {
            if (outgoingMode?.eyeComfortEnabled == true) {
                deltas.add("Eye Comfort: Deactivated")
            }
        }

        // 4. App Guard
        val blockedList = if (incomingMode.appRestrictionsEnabled) incomingMode.restrictedApps.toSet() else emptySet()
        val budgetList = if (incomingMode.appRestrictionsEnabled) incomingMode.appBudgets else emptyList()
        if (blockedList.isNotEmpty() || budgetList.isNotEmpty()) {
            val guardDetails = mutableListOf<String>()
            if (blockedList.isNotEmpty()) guardDetails.add("${blockedList.size} blocked")
            if (budgetList.isNotEmpty()) guardDetails.add("${budgetList.size} budgeted")
            deltas.add("App Guard: ${guardDetails.joinToString(", ")}")
        } else {
            if (outgoingMode?.appRestrictionsEnabled == true || outgoingMode?.appBudgets?.isNotEmpty() == true) {
                deltas.add("App Guard: All restrictions cleared")
            }
        }

        val diffSummary = if (deltas.isNotEmpty()) deltas.joinToString(" • ") else "All settings synchronized"
        return TransitionDiffResult(summary = diffSummary, deltaItems = deltas)
    }

    fun executeIntelligentTransition(
        context: Context,
        outgoingContext: ContextItem?,
        incomingContext: ContextItem,
        incomingMode: ContextTimeframeMode,
        triggerReason: String
    ): TransitionDiffResult {
        val outgoingMode = outgoingContext?.profile?.resolveActiveMode()
        val deltas = mutableListOf<String>()

        Log.d(TAG, "Calculating transition diff: '${outgoingContext?.name ?: "Baseline"}' -> '${incomingContext.name}' (Mode: ${incomingMode.name})")

        // 1. Screen Brightness
        if (incomingMode.brightnessEnabled) {
            val incomingLevel = incomingMode.brightnessLevel
            if (outgoingMode?.brightnessEnabled == true && outgoingMode.brightnessLevel == incomingLevel) {
                deltas.add("Brightness: Kept at $incomingLevel% (unchanged)")
            } else {
                SystemControllers.applyBrightness(context, incomingLevel)
                val outLabel = if (outgoingMode?.brightnessEnabled == true) "${outgoingMode.brightnessLevel}%" else "Baseline"
                deltas.add("Brightness: $outLabel ➔ $incomingLevel%")
            }
        } else {
            if (outgoingMode?.brightnessEnabled == true) {
                SystemControllers.restoreBrightness(context)
                deltas.add("Brightness: Restored to baseline")
            }
        }

        // 2. Do Not Disturb (DND)
        if (incomingMode.dndEnabled) {
            val incomingDnd = incomingMode.dndMode
            if (outgoingMode?.dndEnabled == true && outgoingMode.dndMode == incomingDnd) {
                deltas.add("DND: Kept as '$incomingDnd' (unchanged)")
            } else {
                SystemControllers.applyDndMode(context, incomingDnd)
                val outLabel = if (outgoingMode?.dndEnabled == true) outgoingMode.dndMode else "Off"
                deltas.add("DND: $outLabel ➔ $incomingDnd")
            }
        } else {
            if (outgoingMode?.dndEnabled == true) {
                SystemControllers.restoreDnd(context)
                deltas.add("DND: Deactivated (restored to baseline)")
            }
        }

        // 3. Eye Comfort / Blue Light Filter
        if (incomingMode.eyeComfortEnabled) {
            val incomingIntensity = incomingMode.eyeComfortLevel
            if (outgoingMode?.eyeComfortEnabled == true && outgoingMode.eyeComfortLevel == incomingIntensity) {
                deltas.add("Eye Comfort: Kept at $incomingIntensity% (unchanged)")
            } else {
                SystemControllers.applyEyeComfort(context, true, incomingIntensity)
                deltas.add("Eye Comfort: Activated ($incomingIntensity%)")
            }
        } else {
            if (outgoingMode?.eyeComfortEnabled == true) {
                SystemControllers.applyEyeComfort(context, false)
                deltas.add("Eye Comfort: Deactivated")
            }
        }

        // 4. Focus App Restrictions & Per-App Usage Budgets
        val blockedList = if (incomingMode.appRestrictionsEnabled) incomingMode.restrictedApps.toSet() else emptySet()
        val budgetList = if (incomingMode.appRestrictionsEnabled) incomingMode.appBudgets else emptyList()

        if (blockedList.isNotEmpty() || budgetList.isNotEmpty()) {
            AppRestrictionMonitor.setRestrictedPackages(blockedList, incomingContext.name, incomingMode.id)
            AppUsageBudgetManager.setActiveContextBudgets(
                context = context,
                contextId = incomingContext.id,
                contextName = incomingContext.name,
                budgets = budgetList,
                modeId = incomingMode.id
            )
            AppRestrictionMonitor.startContinuousMonitoring(context, 200L)
            val guardDetails = mutableListOf<String>()
            if (blockedList.isNotEmpty()) guardDetails.add("${blockedList.size} blocked")
            if (budgetList.isNotEmpty()) guardDetails.add("${budgetList.size} budgeted")
            deltas.add("App Guard: ${guardDetails.joinToString(", ")}")
        } else {
            AppRestrictionMonitor.setRestrictedPackages(emptySet(), null, null)
            AppUsageBudgetManager.setActiveContextBudgets(context, null, null, emptyList())
            AppRestrictionMonitor.stopContinuousMonitoring()
            FloatingTimerOverlayManager.clearBudgetOverlay(context)
            if (outgoingMode?.appRestrictionsEnabled == true || outgoingMode?.appBudgets?.isNotEmpty() == true) {
                deltas.add("App Guard: All restrictions cleared")
            }
        }

        // 5. Auto-Open App
        if (incomingMode.autoLaunchAppEnabled && incomingMode.autoLaunchPackage.isNotBlank()) {
            val appLabel = incomingMode.autoLaunchAppName.ifBlank { incomingMode.autoLaunchPackage }
            ProfileActivationHelper.launchTargetApp(context, incomingMode.autoLaunchPackage)
            deltas.add("Auto-Opened: $appLabel")
        }

        // 6. Sticky Note System Overlay
        if (incomingMode.stickyNoteEnabled && (incomingMode.stickyNoteText.isNotBlank() || incomingMode.stickyNoteChecklist.isNotEmpty())) {
            StickyNoteOverlayActivity.show(
                context = context,
                contextName = incomingContext.name,
                modeName = incomingMode.name,
                noteText = incomingMode.stickyNoteText,
                checklist = incomingMode.stickyNoteChecklist
            )
            deltas.add("Sticky Note Overlay shown")
        }

        val diffSummary = if (deltas.isNotEmpty()) {
            deltas.joinToString(" • ")
        } else {
            "All settings synchronized"
        }

        return TransitionDiffResult(
            summary = diffSummary,
            deltaItems = deltas
        )
    }
}
