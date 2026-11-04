package com.contextpulse.phase0.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

/**
 * Checks if current local device time and day falls within a start/end schedule window.
 */
fun isCurrentTimeWithinSchedule(start: String, end: String, activeDays: List<Int>): Boolean {
    val calendar = Calendar.getInstance()
    // Calendar.DAY_OF_WEEK: Sunday=1, Monday=2, Tuesday=3, Wednesday=4, Thursday=5, Friday=6, Saturday=7
    val dayOfWeekIso = when (calendar.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> 1
    }
    if (activeDays.isNotEmpty() && !activeDays.contains(dayOfWeekIso)) {
        return false
    }

    val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    val startMinutes = parseScheduleTimeToMinutes(start)
    val endMinutes = parseScheduleTimeToMinutes(end)

    return if (startMinutes <= endMinutes) {
        currentMinutes in startMinutes..endMinutes
    } else {
        // Overnight window e.g. 22:00 to 07:00
        currentMinutes >= startMinutes || currentMinutes <= endMinutes
    }
}

fun parseScheduleTimeToMinutes(timeStr: String): Int {
    return try {
        val parts = timeStr.split(":")
        val hours = parts[0].trim().toInt()
        val minutes = if (parts.size > 1) parts[1].trim().toInt() else 0
        hours * 60 + minutes
    } catch (e: Exception) {
        0
    }
}

/**
 * An individual Context Mode operating within a space based on time and day.
 * Bundles DND, Brightness, Blocked Apps, and Eye Comfort / Night Light.
 */
data class ContextTimeframeMode(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Default", // "Default" covers all times/days outside custom modes
    val isAllTime: Boolean = true, // true = Default fallback covering all other times and days
    val startTime: String = "09:00",
    val endTime: String = "17:00",
    val activeDays: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7), // 1=Mon .. 7=Sun
    val dndEnabled: Boolean = false,
    val dndMode: String = "Priority Only", // Priority Only, Total Silence, Alarms Only
    val brightnessEnabled: Boolean = false,
    val brightnessLevel: Int = 50, // 0..100 %
    val appRestrictionsEnabled: Boolean = false,
    val restrictedApps: List<String> = emptyList(),
    val eyeComfortEnabled: Boolean = false,
    val eyeComfortLevel: Int = 50 // 0..100 warm tint intensity
) {
    fun isEffectiveNow(): Boolean {
        if (isAllTime || name.equals("Default", ignoreCase = true)) return true
        return isCurrentTimeWithinSchedule(startTime, endTime, activeDays)
    }

    fun getDaysSummary(): String {
        if (activeDays.size >= 7 || activeDays.isEmpty()) return "Everyday"
        val sorted = activeDays.sorted()
        if (sorted == listOf(1, 2, 3, 4, 5)) return "Mon - Fri"
        if (sorted == listOf(6, 7)) return "Sat - Sun"
        val dayNames = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
        return sorted.mapNotNull { dayNames[it] }.joinToString(", ")
    }

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("isAllTime", isAllTime)
            put("startTime", startTime)
            put("endTime", endTime)
            val daysArray = JSONArray()
            activeDays.forEach { daysArray.put(it) }
            put("activeDays", daysArray)

            put("dndEnabled", dndEnabled)
            put("dndMode", dndMode)
            put("brightnessEnabled", brightnessEnabled)
            put("brightnessLevel", brightnessLevel)
            put("appRestrictionsEnabled", appRestrictionsEnabled)
            val appsArray = JSONArray()
            restrictedApps.forEach { appsArray.put(it) }
            put("restrictedApps", appsArray)

            put("eyeComfortEnabled", eyeComfortEnabled)
            put("eyeComfortLevel", eyeComfortLevel)
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): ContextTimeframeMode {
            val appsList = mutableListOf<String>()
            if (obj.has("restrictedApps")) {
                val array = obj.getJSONArray("restrictedApps")
                for (i in 0 until array.length()) {
                    appsList.add(array.getString(i))
                }
            }

            val daysList = mutableListOf<Int>()
            if (obj.has("activeDays")) {
                val array = obj.getJSONArray("activeDays")
                for (i in 0 until array.length()) {
                    daysList.add(array.getInt(i))
                }
            }
            val activeDays = if (daysList.isEmpty()) listOf(1, 2, 3, 4, 5, 6, 7) else daysList

            val rawName = obj.optString("name", "Default")
            val isAllTimeVal = obj.optBoolean("isAllTime", rawName == "All Time Standard" || rawName.equals("Default", ignoreCase = true))
            val normalizedName = if (rawName == "All Time Standard") "Default" else rawName

            return ContextTimeframeMode(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = normalizedName,
                isAllTime = isAllTimeVal,
                startTime = obj.optString("startTime", "09:00"),
                endTime = obj.optString("endTime", "17:00"),
                activeDays = activeDays,
                dndEnabled = obj.optBoolean("dndEnabled", false),
                dndMode = obj.optString("dndMode", "Priority Only"),
                brightnessEnabled = obj.optBoolean("brightnessEnabled", false),
                brightnessLevel = obj.optInt("brightnessLevel", 50),
                appRestrictionsEnabled = obj.optBoolean("appRestrictionsEnabled", false),
                restrictedApps = appsList,
                eyeComfortEnabled = obj.optBoolean("eyeComfortEnabled", false),
                eyeComfortLevel = obj.optInt("eyeComfortLevel", 50)
            )
        }
    }
}

/**
 * Helper to compute human-readable auto-adjusted coverage of the Default mode.
 */
fun computeDefaultUncoveredSummary(customModes: List<ContextTimeframeMode>): String {
    if (customModes.isEmpty()) {
        return "Continuous 24/7 coverage across all 7 days"
    }
    val breakdown = computeDefaultDetailedScheduleBreakdown(customModes)
    return breakdown.joinToString(" • ")
}

fun computeDefaultDetailedScheduleBreakdown(customModes: List<ContextTimeframeMode>): List<String> {
    if (customModes.isEmpty()) {
        return listOf("Continuous 24-hour coverage across all 7 days")
    }

    val dayNames = mapOf(
        1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun"
    )

    // For each day 1..7, compute uncovered intervals
    val dailyUncovered = mutableMapOf<Int, String>()
    for (day in 1..7) {
        val modesOnDay = customModes.filter { it.activeDays.contains(day) }
        if (modesOnDay.isEmpty()) {
            dailyUncovered[day] = "All day (24 hrs)"
        } else {
            val intervals = modesOnDay.map {
                parseScheduleTimeToMinutes(it.startTime) to parseScheduleTimeToMinutes(it.endTime)
            }.sortedBy { it.first }

            val freeSegments = mutableListOf<String>()
            var currentMin = 0
            for (interval in intervals) {
                if (interval.first > currentMin) {
                    freeSegments.add("${formatMinutesToTime(currentMin)}–${formatMinutesToTime(interval.first)}")
                }
                if (interval.second > currentMin) {
                    currentMin = interval.second
                }
            }
            if (currentMin < 1440) {
                freeSegments.add("${formatMinutesToTime(currentMin)}–23:59")
            }
            dailyUncovered[day] = if (freeSegments.isEmpty()) "Covered by scheduled modes" else freeSegments.joinToString(" & ")
        }
    }

    val result = mutableListOf<String>()
    var groupStart = 1
    var prevCoverage = dailyUncovered[1] ?: ""

    for (day in 2..8) {
        val currentCoverage = if (day <= 7) dailyUncovered[day] ?: "" else null
        if (currentCoverage != prevCoverage) {
            val groupEnd = day - 1
            val daysLabel = if (groupStart == groupEnd) {
                dayNames[groupStart] ?: ""
            } else if (groupStart == 1 && groupEnd == 5) {
                "Mon – Fri"
            } else if (groupStart == 6 && groupEnd == 7) {
                "Sat – Sun (Weekend)"
            } else {
                "${dayNames[groupStart]} – ${dayNames[groupEnd]}"
            }

            if (prevCoverage != "Covered by scheduled modes") {
                result.add("$daysLabel: $prevCoverage")
            }
            groupStart = day
            prevCoverage = currentCoverage ?: ""
        }
    }

    return if (result.isEmpty()) listOf("Active outside custom scheduled windows") else result
}

fun formatMinutesToTime(minutes: Int): String {
    val h = (minutes / 60) % 24
    val m = minutes % 60
    return String.format("%02d:%02d", h, m)
}

/**
 * Sorts modes chronologically: Default mode first, followed by custom modes ordered by day of week and start time.
 */
fun sortModesChronologically(modesList: List<ContextTimeframeMode>): List<ContextTimeframeMode> {
    val defaultMode = modesList.find { it.isAllTime || it.name.equals("Default", ignoreCase = true) }
    val customModes = modesList.filter { it != defaultMode }.sortedWith(
        compareBy(
            { it.activeDays.minOrNull() ?: 1 },
            { parseScheduleTimeToMinutes(it.startTime) }
        )
    )
    return if (defaultMode != null) listOf(defaultMode) + customModes else customModes
}

/**
 * Encapsulates the multi-mode context configuration for a physical space.
 * A physical space can contain multiple modes governed by day and timeframes.
 */
data class RoomProfile(
    val modes: List<ContextTimeframeMode> = listOf(ContextTimeframeMode(name = "Default", isAllTime = true)),
    val autoRestoreOnExit: Boolean = true,
    val customNotes: String = ""
) {
    /**
     * Resolves the current active mode.
     * Evaluates custom modes in chronological order first; if current time/day does not match any custom mode,
     * falls back to the Default mode (guaranteeing 24/7 coverage across all 7 days).
     */
    fun resolveActiveMode(): ContextTimeframeMode {
        if (modes.isEmpty()) {
            return ContextTimeframeMode(name = "Default", isAllTime = true)
        }
        val customModes = modes.filter { !it.isAllTime && !it.name.equals("Default", ignoreCase = true) }
            .sortedWith(
                compareBy(
                    { it.activeDays.minOrNull() ?: 1 },
                    { parseScheduleTimeToMinutes(it.startTime) }
                )
            )
        val activeCustom = customModes.find { it.isEffectiveNow() }
        if (activeCustom != null) {
            return activeCustom
        }
        val defaultMode = modes.find { it.isAllTime || it.name.equals("Default", ignoreCase = true) }
        return defaultMode ?: modes.first()
    }

    // Property forwards to current active mode
    val dndEnabled: Boolean get() = resolveActiveMode().dndEnabled
    val dndMode: String get() = resolveActiveMode().dndMode
    val brightnessEnabled: Boolean get() = resolveActiveMode().brightnessEnabled
    val brightnessLevel: Int get() = resolveActiveMode().brightnessLevel
    val appRestrictionsEnabled: Boolean get() = resolveActiveMode().appRestrictionsEnabled
    val restrictedApps: List<String> get() = resolveActiveMode().restrictedApps
    val eyeComfortEnabled: Boolean get() = resolveActiveMode().eyeComfortEnabled
    val eyeComfortLevel: Int get() = resolveActiveMode().eyeComfortLevel

    fun isDndEffectiveNow(): Boolean = dndEnabled
    fun isBrightnessEffectiveNow(): Boolean = brightnessEnabled
    fun isAppRestrictionsEffectiveNow(): Boolean = appRestrictionsEnabled
    fun isEyeComfortEffectiveNow(): Boolean = eyeComfortEnabled

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            val modesArray = JSONArray()
            modes.forEach { modesArray.put(it.toJsonObject()) }
            put("modes", modesArray)
            put("autoRestoreOnExit", autoRestoreOnExit)
            put("customNotes", customNotes)
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): RoomProfile {
            val modesList = mutableListOf<ContextTimeframeMode>()
            if (obj.has("modes")) {
                val array = obj.getJSONArray("modes")
                for (i in 0 until array.length()) {
                    modesList.add(ContextTimeframeMode.fromJsonObject(array.getJSONObject(i)))
                }
            } else {
                // Migration from legacy schema: wrap existing single profile in a default mode
                val appsList = mutableListOf<String>()
                if (obj.has("restrictedApps")) {
                    val array = obj.getJSONArray("restrictedApps")
                    for (i in 0 until array.length()) {
                        appsList.add(array.getString(i))
                    }
                }
                modesList.add(
                    ContextTimeframeMode(
                        name = "Default",
                        isAllTime = true,
                        dndEnabled = obj.optBoolean("dndEnabled", false),
                        dndMode = obj.optString("dndMode", "Priority Only"),
                        brightnessEnabled = obj.optBoolean("brightnessEnabled", false),
                        brightnessLevel = obj.optInt("brightnessLevel", 50),
                        appRestrictionsEnabled = obj.optBoolean("appRestrictionsEnabled", false),
                        restrictedApps = appsList,
                        eyeComfortEnabled = obj.optBoolean("eyeComfortEnabled", false)
                    )
                )
            }

            val finalModes = if (modesList.isEmpty()) {
                listOf(ContextTimeframeMode(name = "Default", isAllTime = true))
            } else {
                // Ensure at least one Default mode exists
                if (modesList.none { it.isAllTime || it.name.equals("Default", ignoreCase = true) }) {
                    listOf(ContextTimeframeMode(name = "Default", isAllTime = true)) + modesList
                } else {
                    modesList
                }
            }

            return RoomProfile(
                modes = sortModesChronologically(finalModes),
                autoRestoreOnExit = obj.optBoolean("autoRestoreOnExit", true),
                customNotes = obj.optString("customNotes", "")
            )
        }

        fun defaultProfile(spaceName: String = ""): RoomProfile {
            return when {
                spaceName.contains("Bedroom", ignoreCase = true) -> RoomProfile(
                    modes = listOf(
                        ContextTimeframeMode(
                            name = "Default",
                            isAllTime = true,
                            dndEnabled = false,
                            brightnessEnabled = true,
                            brightnessLevel = 45,
                            eyeComfortEnabled = false
                        ),
                        ContextTimeframeMode(
                            name = "Deep Sleep",
                            isAllTime = false,
                            startTime = "22:00",
                            endTime = "07:00",
                            activeDays = listOf(1, 2, 3, 4, 5, 6, 7),
                            dndEnabled = true,
                            dndMode = "Total Silence",
                            brightnessEnabled = true,
                            brightnessLevel = 15,
                            eyeComfortEnabled = true,
                            eyeComfortLevel = 80
                        )
                    )
                )
                spaceName.contains("Office", ignoreCase = true) -> RoomProfile(
                    modes = listOf(
                        ContextTimeframeMode(
                            name = "Default",
                            isAllTime = true,
                            brightnessEnabled = true,
                            brightnessLevel = 60,
                            eyeComfortEnabled = true,
                            eyeComfortLevel = 60
                        ),
                        ContextTimeframeMode(
                            name = "Deep Work Focus",
                            isAllTime = false,
                            startTime = "09:00",
                            endTime = "17:00",
                            activeDays = listOf(1, 2, 3, 4, 5),
                            dndEnabled = true,
                            dndMode = "Priority Only",
                            brightnessEnabled = true,
                            brightnessLevel = 85,
                            appRestrictionsEnabled = false,
                            eyeComfortEnabled = false
                        )
                    )
                )
                else -> RoomProfile(
                    modes = listOf(
                        ContextTimeframeMode(name = "Default", isAllTime = true)
                    )
                )
            }
        }
    }
}

/**
 * Representation of an individual context/space.
 */
data class ContextItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val subtitle: String = "",
    val iconName: String = "meeting_room",
    val profile: RoomProfile = RoomProfile.defaultProfile(name),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("subtitle", subtitle)
            put("iconName", iconName)
            put("profile", profile.toJsonObject())
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): ContextItem {
            val profileObj = if (obj.has("profile")) obj.getJSONObject("profile") else JSONObject()

            return ContextItem(
                id = obj.getString("id"),
                name = obj.getString("name"),
                subtitle = obj.optString("subtitle", ""),
                iconName = obj.optString("iconName", "meeting_room"),
                profile = RoomProfile.fromJsonObject(profileObj),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
            )
        }
    }
}

/**
 * Typealias preserving full backward compatibility across the codebase.
 */
typealias RoomItem = ContextItem
