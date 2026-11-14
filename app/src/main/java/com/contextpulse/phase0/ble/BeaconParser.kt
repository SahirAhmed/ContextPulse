package com.contextpulse.phase0.ble

import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.os.ParcelUuid
import com.contextpulse.phase0.model.BeaconType
import com.contextpulse.phase0.model.BleBeaconInfo
import java.util.Locale
import kotlin.math.pow

object BeaconParser {

    private val EDDYSTONE_SERVICE_UUID = ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")

    /**
     * Parses standard BLE advertisements into recognized beacon formats:
     * - Apple iBeacon (Manufacturer ID 0x004C)
     * - AltBeacon (Manufacturer ID 0x0118)
     * - Google Eddystone UID / URL (Service UUID 0xFEAA)
     * - Generic BLE devices
     */
    fun parseScanResult(scanResult: ScanResult, smoothedRssi: Double): BleBeaconInfo {
        val device = scanResult.device
        val record: ScanRecord? = scanResult.scanRecord
        val rawRssi = scanResult.rssi
        val macAddress = device.address ?: "00:00:00:00:00:00"
        val name = device.name ?: record?.deviceName

        if (record == null) {
            return BleBeaconInfo(
                macAddress = macAddress,
                name = name,
                rawRssi = rawRssi,
                smoothedRssi = smoothedRssi,
                beaconType = BeaconType.GENERIC_MANUFACTURER,
                estimatedDistanceMeters = calculateDistance(smoothedRssi, -59)
            )
        }

        // 1. Check for Apple iBeacon (Manufacturer ID: 0x004C)
        // Format: [0x02, 0x15, 16-byte UUID, 2-byte Major, 2-byte Minor, 1-byte TxPower]
        val appleData = record.getManufacturerSpecificData(0x004C)
        if (appleData != null && appleData.size >= 23 && appleData[0] == 0x02.toByte() && appleData[1] == 0x15.toByte()) {
            val uuidBytes = appleData.copyOfRange(2, 18)
            val uuid = bytesToUuidString(uuidBytes)
            val major = ((appleData[18].toInt() and 0xFF) shl 8) or (appleData[19].toInt() and 0xFF)
            val minor = ((appleData[20].toInt() and 0xFF) shl 8) or (appleData[21].toInt() and 0xFF)
            val txPower = appleData[22].toInt() // Signed byte (-128 to 127 dBm)

            return BleBeaconInfo(
                macAddress = macAddress,
                name = name ?: "iBeacon (${uuid.takeLast(4)})",
                rawRssi = rawRssi,
                smoothedRssi = smoothedRssi,
                beaconType = BeaconType.IBEACON,
                uuid = uuid,
                major = major,
                minor = minor,
                txPower = txPower,
                estimatedDistanceMeters = calculateDistance(smoothedRssi, txPower)
            )
        }

        // 2. Check for AltBeacon (Radius Networks ID: 0x0118)
        // Format: [0xBE, 0xAC, 16-byte UUID, 2-byte Major, 2-byte Minor, 1-byte TxPower]
        val altBeaconData = record.getManufacturerSpecificData(0x0118)
        if (altBeaconData != null && altBeaconData.size >= 24 && altBeaconData[0] == 0xBE.toByte() && altBeaconData[1] == 0xAC.toByte()) {
            val uuid = bytesToUuidString(altBeaconData.copyOfRange(2, 18))
            val major = ((altBeaconData[18].toInt() and 0xFF) shl 8) or (altBeaconData[19].toInt() and 0xFF)
            val minor = ((altBeaconData[20].toInt() and 0xFF) shl 8) or (altBeaconData[21].toInt() and 0xFF)
            val txPower = altBeaconData[22].toInt()

            return BleBeaconInfo(
                macAddress = macAddress,
                name = name ?: "AltBeacon (${uuid.takeLast(4)})",
                rawRssi = rawRssi,
                smoothedRssi = smoothedRssi,
                beaconType = BeaconType.ALTBEACON,
                uuid = uuid,
                major = major,
                minor = minor,
                txPower = txPower,
                estimatedDistanceMeters = calculateDistance(smoothedRssi, txPower)
            )
        }

        // 3. Check for Google Eddystone (Service UUID: 0xFEAA)
        val eddystoneData = record.getServiceData(EDDYSTONE_SERVICE_UUID)
        if (eddystoneData != null && eddystoneData.isNotEmpty()) {
            val frameType = eddystoneData[0].toInt() and 0xFF
            when (frameType) {
                0x00 -> { // Eddystone-UID
                    if (eddystoneData.size >= 18) {
                        val txPower = eddystoneData[1].toInt() // Rssi at 0m
                        val namespace = bytesToHexString(eddystoneData.copyOfRange(2, 12))
                        val instance = bytesToHexString(eddystoneData.copyOfRange(12, 18))
                        return BleBeaconInfo(
                            macAddress = macAddress,
                            name = name ?: "Eddystone-UID ($instance)",
                            rawRssi = rawRssi,
                            smoothedRssi = smoothedRssi,
                            beaconType = BeaconType.EDDYSTONE_UID,
                            uuid = "$namespace-$instance",
                            txPower = txPower,
                            estimatedDistanceMeters = calculateDistance(smoothedRssi, txPower - 41) // Calibrate 0m to 1m offset
                        )
                    }
                }
                0x10 -> { // Eddystone-URL
                    val txPower = if (eddystoneData.size > 1) eddystoneData[1].toInt() else -59
                    return BleBeaconInfo(
                        macAddress = macAddress,
                        name = name ?: "Eddystone-URL",
                        rawRssi = rawRssi,
                        smoothedRssi = smoothedRssi,
                        beaconType = BeaconType.EDDYSTONE_URL,
                        txPower = txPower,
                        estimatedDistanceMeters = calculateDistance(smoothedRssi, txPower - 41)
                    )
                }
                0x20 -> { // Eddystone-TLM (Telemetry: Battery voltage in mV, temperature, packet count)
                    if (eddystoneData.size >= 4) {
                        val batteryMv = ((eddystoneData[2].toInt() and 0xFF) shl 8) or (eddystoneData[3].toInt() and 0xFF)
                        val batteryPct = batteryMvToPercentage(batteryMv)
                        return BleBeaconInfo(
                            macAddress = macAddress,
                            name = name ?: "Eddystone-TLM ($batteryMv mV)",
                            rawRssi = rawRssi,
                            smoothedRssi = smoothedRssi,
                            beaconType = BeaconType.EDDYSTONE_TLM,
                            txPower = -59,
                            estimatedDistanceMeters = calculateDistance(smoothedRssi, -59),
                            batteryPercentage = batteryPct
                        )
                    }
                }
            }
        }

        // Fallback: Generic BLE device
        return BleBeaconInfo(
            macAddress = macAddress,
            name = name ?: "BLE (${macAddress.takeLast(5)})",
            rawRssi = rawRssi,
            smoothedRssi = smoothedRssi,
            beaconType = BeaconType.GENERIC_MANUFACTURER,
            estimatedDistanceMeters = calculateDistance(smoothedRssi, -59)
        )
    }

    /**
     * Calculates approximate distance in meters using the log-distance path loss formula:
     * Distance = 10 ^ ((TxPower - RSSI) / (10 * n))
     * where n is path-loss exponent (typically 2.0 to 2.5 in indoor environments).
     */
    fun calculateDistance(rssi: Double, txPower: Int, pathLossExponent: Double = 2.4): Double {
        if (rssi >= 0.0) return 0.1
        val ratio = (txPower - rssi) / (10.0 * pathLossExponent)
        val distance = 10.0.pow(ratio).coerceIn(0.05, 50.0)
        return String.format(Locale.US, "%.2f", distance).toDoubleOrNull() ?: distance
    }

    /**
     * Intercepts passive Eddystone-TLM (Service UUID 0xFEAA, Frame Type 0x20)
     * to read raw battery millivolts and calculate percentage passively without maintaining a GATT connection.
     * Returns Pair(millivolts, percentage).
     */
    fun parseEddystoneTlmBattery(scanRecord: ScanRecord?): Pair<Int, Int>? {
        if (scanRecord == null) return null
        val eddystoneData = scanRecord.getServiceData(EDDYSTONE_SERVICE_UUID) ?: return null
        if (eddystoneData.size >= 4 && (eddystoneData[0].toInt() and 0xFF) == 0x20) {
            val batteryMv = ((eddystoneData[2].toInt() and 0xFF) shl 8) or (eddystoneData[3].toInt() and 0xFF)
            val batteryPct = batteryMvToPercentage(batteryMv) ?: return null
            return Pair(batteryMv, batteryPct)
        }
        return null
    }

    /**
     * Converts coin cell battery voltage (mV) to percentage (0 - 100%), or null if 0 mV / unsupported.
     * Nominal 3.0V lithium coin cell (e.g. CR2032/CR2477) discharge curve:
     * >= 3000 mV -> 100%, 2800 mV -> ~70%, 2500 mV -> ~20%, <= 2200 mV -> 0%.
     */
    fun batteryMvToPercentage(batteryMv: Int): Int? {
        if (batteryMv <= 0) return null
        val pct = ((batteryMv - 2200).toFloat() / (3000 - 2200)) * 100f
        return pct.toInt().coerceIn(0, 100)
    }

    private fun bytesToUuidString(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (i in bytes.indices) {
            sb.append(String.format("%02X", bytes[i]))
            if (i == 3 || i == 5 || i == 7 || i == 9) {
                sb.append("-")
            }
        }
        return sb.toString()
    }

    private fun bytesToHexString(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02X", b))
        }
        return sb.toString()
    }
}
