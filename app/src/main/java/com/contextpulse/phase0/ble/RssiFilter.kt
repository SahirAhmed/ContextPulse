package com.contextpulse.phase0.ble

import android.os.SystemClock
import com.contextpulse.phase0.model.BleBeaconInfo

/**
 * Handles RSSI smoothing, dual-stage hysteresis, and beacon disappearance tracking.
 *
 * Uses android.os.SystemClock.elapsedRealtime() (monotonic clock) rather than
 * System.currentTimeMillis() to prevent clock jumps from NTP or time zone updates.
 *
 * Concurrency Safety:
 * All updates and purges are synchronized under a dedicated lock, guaranteeing that
 * a fresh advertisement arrival cannot be inadvertently deleted during a purge operation.
 */
class RssiFilter(
    private val alpha: Double = 0.25, // Weight of newest raw RSSI sample (0.2 - 0.3 is standard for BLE)
    private val disappearanceTimeoutMs: Long = 18_000L, // 18 seconds of silence to declare beacon lost
    private val hysteresisThresholdDb: Double = 5.0 // Required RSSI lead (dB) to switch room winner
) {
    // MacAddress -> Smoothed RSSI
    private val smoothedValues = HashMap<String, Double>()
    // MacAddress -> Last seen timestamp in monotonic elapsedRealtime
    private val lastSeenMonotonicMs = HashMap<String, Long>()

    private val lock = Any()

    // Current dominating/winning room beacon MAC
    private var activeRoomBeaconMac: String? = null

    // Track challenger lead start time to enforce temporal stability (prevents wall-bounce spikes)
    private var challengerMac: String? = null
    private var challengerLeadStartedMs: Long = 0L
    private val challengerDwellRequiredMs: Long = 2_500L // Must maintain lead for 2.5s

    /**
     * Updates and smooths the RSSI value for a beacon using Exponential Moving Average (EMA).
     * Synchronized to guarantee atomic updates against concurrent purges.
     */
    fun update(macAddress: String, rawRssi: Int): Double {
        synchronized(lock) {
            val nowMono = SystemClock.elapsedRealtime()
            lastSeenMonotonicMs[macAddress] = nowMono

            val prev = smoothedValues[macAddress]
            val smoothed = if (prev == null) {
                rawRssi.toDouble()
            } else {
                (alpha * rawRssi) + ((1.0 - alpha) * prev)
            }
            smoothedValues[macAddress] = smoothed
            return smoothed
        }
    }

    /**
     * Checks if a beacon has disappeared based on monotonic elapsed time.
     */
    fun isDisappeared(macAddress: String, nowMono: Long = SystemClock.elapsedRealtime()): Boolean {
        synchronized(lock) {
            val lastSeen = lastSeenMonotonicMs[macAddress] ?: return true
            return (nowMono - lastSeen) > disappearanceTimeoutMs
        }
    }

    /**
     * Cleans up expired beacons in a strictly synchronized atomic block.
     * Prevents race conditions where a fresh scan arrival could be deleted during purge.
     */
    fun purgeDisappearedBeacons(nowMono: Long = SystemClock.elapsedRealtime()): List<String> {
        val purged = mutableListOf<String>()
        synchronized(lock) {
            val iterator = lastSeenMonotonicMs.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if ((nowMono - entry.value) > disappearanceTimeoutMs) {
                    purged.add(entry.key)
                    smoothedValues.remove(entry.key)
                    iterator.remove()
                }
            }

            if (activeRoomBeaconMac in purged) {
                activeRoomBeaconMac = null
                challengerMac = null
            }
            if (challengerMac in purged) {
                challengerMac = null
            }
        }
        return purged
    }

    /**
     * Test 12: Arbitrates between multiple nearby beacons to determine the primary room.
     * Enforces dual-stage hysteresis:
     * 1. Signal buffer: Challenger must be >= current winner + hysteresisThresholdDb (+5 dB).
     * 2. Time dwell buffer: Challenger must maintain this lead for at least 2.5 seconds.
     */
    fun arbitrateWinningBeacon(
        activeBeacons: List<BleBeaconInfo>,
        nowMono: Long = SystemClock.elapsedRealtime()
    ): BleBeaconInfo? {
        synchronized(lock) {
            if (activeBeacons.isEmpty()) {
                activeRoomBeaconMac = null
                challengerMac = null
                return null
            }

            val sorted = activeBeacons.sortedByDescending { it.smoothedRssi }
            val strongest = sorted.first()

            val currentWinner = activeRoomBeaconMac?.let { mac ->
                activeBeacons.find { it.macAddress == mac }
            }

            if (currentWinner == null) {
                // No current room, adopt strongest registered beacon immediately
                activeRoomBeaconMac = strongest.macAddress
                challengerMac = null
                return strongest
            }

            // If the strongest is already the active room, reset any challenger
            if (strongest.macAddress == currentWinner.macAddress) {
                challengerMac = null
                return currentWinner
            }

            // Check if challenger exceeds current winner by the hysteresis threshold (e.g., +5 dB)
            val lead = strongest.smoothedRssi - currentWinner.smoothedRssi
            if (lead >= hysteresisThresholdDb) {
                if (challengerMac == strongest.macAddress) {
                    // Challenger has maintained lead
                    if ((nowMono - challengerLeadStartedMs) >= challengerDwellRequiredMs) {
                        // Winner transition confirmed!
                        activeRoomBeaconMac = strongest.macAddress
                        challengerMac = null
                        return strongest
                    }
                } else {
                    // Start dwell timer for new challenger
                    challengerMac = strongest.macAddress
                    challengerLeadStartedMs = nowMono
                }
            } else {
                // Lead below +5 dB, reset challenger
                challengerMac = null
            }

            return currentWinner
        }
    }

    fun getActiveRoomBeaconMac(): String? = synchronized(lock) { activeRoomBeaconMac }

    fun clear() {
        synchronized(lock) {
            smoothedValues.clear()
            lastSeenMonotonicMs.clear()
            activeRoomBeaconMac = null
            challengerMac = null
        }
    }
}
