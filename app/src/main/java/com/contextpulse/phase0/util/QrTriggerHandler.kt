package com.contextpulse.phase0.util

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.contextpulse.phase0.model.RegisteredBeacon
import com.contextpulse.phase0.model.TriggerSource
import com.contextpulse.phase0.model.TriggerType
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

    fun processQrPayload(
        context: Context,
        rawPayload: String,
        hardwareUid: String? = null
    ): Pair<Boolean, String> {
        val triggerKey = parsePayloadToTriggerKey(rawPayload)
        val beaconRepo = BeaconRepository.getInstance(context)
        val roomRepo = RoomProfileRepository.getInstance(context)

        // 1. Try finding matching configured Trigger/Beacon by App Link / ID / QR / NFC UID
        val trigger = beaconRepo.beacons.value.find { b ->
            b.id.equals(triggerKey, ignoreCase = true) ||
                (b.qrPayload.isNotBlank() && (
                    b.qrPayload.equals(rawPayload, ignoreCase = true) ||
                        b.qrPayload.endsWith("/$triggerKey", ignoreCase = true) ||
                        b.qrPayload.contains("trigger=$triggerKey", ignoreCase = true)
                    )) ||
                (b.nfcTagId.isNotBlank() && (
                    b.nfcTagId.equals(triggerKey, ignoreCase = true) ||
                        b.nfcTagId.equals(rawPayload.trim(), ignoreCase = true) ||
                        (!hardwareUid.isNullOrBlank() && b.nfcTagId.equals(hardwareUid, ignoreCase = true))
                    )) ||
                b.name.equals(triggerKey, ignoreCase = true)
        } ?: hardwareUid?.takeIf { it.isNotBlank() }?.let { uid ->
            beaconRepo.findByNfcTagId(uid)
        }

        if (trigger != null) {
            return activateMatchedTrigger(context, trigger, roomRepo)
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

        val notRecognized = "Trigger ($triggerKey) not recognized."
        Toast.makeText(context, notRecognized, Toast.LENGTH_SHORT).show()
        return Pair(false, notRecognized)
    }

    private fun activateMatchedTrigger(
        context: Context,
        trigger: RegisteredBeacon,
        roomRepo: RoomProfileRepository
    ): Pair<Boolean, String> {
        val targetRoom = when {
            trigger.assignedRoomId.isNotBlank() ->
                roomRepo.rooms.value.find { it.id == trigger.assignedRoomId }
                    ?: roomRepo.rooms.value.find { it.name.equals(trigger.assignedRoomName.trim(), ignoreCase = true) }
            trigger.assignedRoomName.isNotBlank() ->
                roomRepo.rooms.value.find { it.name.equals(trigger.assignedRoomName.trim(), ignoreCase = true) }
            else -> null
        }

        val hasAssignedReference = trigger.assignedRoomId.isNotBlank() || trigger.assignedRoomName.isNotBlank()
        if (!hasAssignedReference) {
            val label = if (trigger.type == TriggerType.NFC_TAG) "NFC tag" else "Trigger"
            val msg = "$label '${trigger.name}' is not attached to a Context yet."
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            return Pair(false, msg)
        }

        if (targetRoom == null) {
            val label = trigger.assignedRoomName.ifBlank { trigger.assignedRoomId }
            val msg = "Assigned context '$label' not found."
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            return Pair(false, msg)
        }

        val isNfc = trigger.type == TriggerType.NFC_TAG
        val source = if (isNfc) TriggerSource.NFC_TAG else TriggerSource.QR_CODE
        val reason = if (isNfc) {
            "NFC Tag tapped: ${trigger.name}"
        } else {
            "Room QR Code scanned: ${trigger.name}"
        }

        ProfileActivationHelper.activateProfile(
            context = context,
            roomId = targetRoom.id,
            triggerSource = source,
            reason = reason
        )

        val msg = if (isNfc) {
            "NFC Tag Tapped: Switched to '${targetRoom.name}'"
        } else {
            "Room QR Code Scanned: Activated '${targetRoom.name}'"
        }
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        return Pair(true, msg)
    }
}

