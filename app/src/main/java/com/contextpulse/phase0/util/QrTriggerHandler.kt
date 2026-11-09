package com.contextpulse.phase0.util

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.contextpulse.phase0.model.TriggerSource
import com.contextpulse.phase0.repository.BeaconRepository
import com.contextpulse.phase0.repository.RoomProfileRepository
import com.contextpulse.phase0.system.ProfileActivationHelper

object QrTriggerHandler {

    fun parsePayloadToTriggerKey(raw: String): String {
        val trimmed = raw.trim()
        val uri = try { Uri.parse(trimmed) } catch (_: Exception) { null }
        if (uri != null) {
            val qTrigger = uri.getQueryParameter("trigger")
            if (!qTrigger.isNullOrBlank()) return qTrigger.trim()
            val qContext = uri.getQueryParameter("context")
            if (!qContext.isNullOrBlank()) return qContext.trim()
            val last = uri.lastPathSegment
            if (!last.isNullOrBlank() && last != "ContextPulse" && last != "trigger") return last.trim()
        }
        return trimmed
    }

    fun processQrPayload(context: Context, rawPayload: String): Pair<Boolean, String> {
        val triggerKey = parsePayloadToTriggerKey(rawPayload)
        val beaconRepo = BeaconRepository.getInstance(context)
        val roomRepo = RoomProfileRepository.getInstance(context)

        // 1. Try finding matching configured Trigger/Beacon
        val trigger = beaconRepo.beacons.value.find {
            it.id.equals(triggerKey, ignoreCase = true) ||
            it.qrPayload.equals(rawPayload, ignoreCase = true) ||
            it.qrPayload.endsWith("/$triggerKey", ignoreCase = true) ||
            it.qrPayload.contains("trigger=$triggerKey", ignoreCase = true) ||
            it.name.equals(triggerKey, ignoreCase = true)
        }

        if (trigger != null) {
            val assignedRoomName = trigger.assignedRoomName
            if (!assignedRoomName.isNullOrBlank()) {
                val targetRoom = roomRepo.rooms.value.find { it.name.equals(assignedRoomName.trim(), ignoreCase = true) }
                if (targetRoom != null) {
                    ProfileActivationHelper.activateProfile(
                        context = context,
                        roomId = targetRoom.id,
                        triggerSource = TriggerSource.QR_CODE,
                        reason = "Room QR Code scanned: ${trigger.name}"
                    )
                    val msg = "Room QR Code Scanned: Activated '${targetRoom.name}'"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    return Pair(true, msg)
                } else {
                    val msg = "Assigned context '$assignedRoomName' not found."
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    return Pair(false, msg)
                }
            } else {
                val msg = "Scanned Trigger '${trigger.name}', but it is not assigned to a context yet."
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                return Pair(false, msg)
            }
        }

        // 2. Try matching room name or ID directly
        val directRoom = roomRepo.rooms.value.find {
            it.name.equals(triggerKey, ignoreCase = true) ||
            it.id.equals(triggerKey, ignoreCase = true) ||
            it.name.equals(rawPayload.trim(), ignoreCase = true)
        }
        if (directRoom != null) {
            ProfileActivationHelper.activateProfile(
                context = context,
                roomId = directRoom.id,
                triggerSource = TriggerSource.QR_CODE,
                reason = "Room QR Code scanned directly"
            )
            val msg = "Room QR Code Scanned: Activated '${directRoom.name}'"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            return Pair(true, msg)
        }

        val notRecognized = "QR Code trigger ($triggerKey) not recognized."
        Toast.makeText(context, notRecognized, Toast.LENGTH_SHORT).show()
        return Pair(false, notRecognized)
    }
}
