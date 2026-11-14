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

    return if (startMinutes < endMinutes) {
        currentMinutes in startMinutes until endMinutes
    } else if (startMinutes > endMinutes) {
        // Overnight window e.g. 22:00 to 07:00
        currentMinutes >= startMinutes || currentMinutes < endMinutes
    } else {
        true
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

fun formatMinutesToTime(minutes: Int): String {
    val h = (minutes / 60) % 24
    val m = minutes % 60
    return String.format("%02d:%02d", h, m)
}

data class StickyNoteCheckItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val isChecked: Boolean = false
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("isChecked", isChecked)
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): StickyNoteCheckItem = StickyNoteCheckItem(
            id = obj.optString("id", UUID.randomUUID().toString()),
            text = obj.optString("text", ""),
            isChecked = obj.optBoolean("isChecked", false)
        )
    }
}

data class RestrictedAppEntry(
    val packageName: String,
    val appName: String = "",
    val maxMinutes: Int = 0 // 0 = Outright block, >0 = Usage budget allowance
) {
    val isOutrightBlock: Boolean get() = maxMinutes <= 0
}

data class AppUsageBudget(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String,
    val appName: String = "",
    val maxMinutes: Int = 15
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("packageName", packageName)
        put("appName", appName)
        put("maxMinutes", maxMinutes)
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): AppUsageBudget = AppUsageBudget(
            id = obj.optString("id", UUID.randomUUID().toString()),
            packageName = obj.optString("packageName", ""),
            appName = obj.optString("appName", ""),
            maxMinutes = obj.optInt("maxMinutes", 15)
        )
    }
}

/**
 * An individual Context Mode operating within a space based on time and day.
 * Bundles DND, Brightness, Restricted Apps (outright blocked & budgeted), Eye Comfort, Auto-Launch App, and Sticky Note.
 */
data class ContextTimeframeMode(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Default",
    val isDefault: Boolean = false,
    val isEnabled: Boolean = true,
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val endHour: Int = 17,
    val endMinute: Int = 0,
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7), // 1=Mon .. 7=Sun
    val dndEnabled: Boolean = false,
    val dndMode: String = "Priority Only",
    val brightnessEnabled: Boolean = false,
    val brightnessPercent: Int = 50,
    val appRestrictionsEnabled: Boolean = false,
    val blockedApps: List<String> = emptyList(),
    val blockedAppGroupIds: List<String> = emptyList(),
    val eyeComfortEnabled: Boolean = false,
    val eyeComfortIntensity: Int = 50,
    val autoLaunchAppEnabled: Boolean = false,
    val autoLaunchPackage: String = "",
    val autoLaunchAppName: String = "",
    val stickyNoteEnabled: Boolean = false,
    val stickyNoteText: String = "",
    val stickyNoteChecklist: List<StickyNoteCheckItem> = emptyList(),
    val appBudgets: List<AppUsageBudget> = emptyList()
) {
    // Aliases & convenience properties for compatibility across all screens
    val isAllTime: Boolean get() = isDefault || name.equals("Default", ignoreCase = true)
    val startTime: String get() = String.format("%02d:%02d", startHour, startMinute)
    val endTime: String get() = String.format("%02d:%02d", endHour, endMinute)
    val activeDays: List<Int> get() = daysOfWeek.toList()
    val brightnessLevel: Int get() = brightnessPercent
    val eyeComfortLevel: Int get() = eyeComfortIntensity
    val restrictedApps: List<String> get() = blockedApps

    // Count of outright blocked individual apps (excluding budgeted apps)
    val outrightBlockedCount: Int get() = blockedApps.size
    // Count of budgeted apps
    val budgetedAppsCount: Int get() = appBudgets.size
    // Total count of uniquely restricted apps
    val totalRestrictedCount: Int get() = (blockedApps + appBudgets.map { it.packageName }).distinct().size

    /**
     * Resolves unified restricted app entries combining outright blocked apps and budgeted apps.
     */
    fun getRestrictedAppEntries(resolveAppName: (String) -> String = { it }): List<RestrictedAppEntry> {
        val result = mutableListOf<RestrictedAppEntry>()
        val budgetedMap = appBudgets.associateBy { it.packageName }
        
        // Outright blocked apps
        blockedApps.forEach { pkg ->
            if (!budgetedMap.containsKey(pkg)) {
                result.add(RestrictedAppEntry(packageName = pkg, appName = resolveAppName(pkg), maxMinutes = 0))
            }
        }
        
        // Budgeted apps
        appBudgets.forEach { budget ->
            result.add(
                RestrictedAppEntry(
                    packageName = budget.packageName,
                    appName = budget.appName.ifBlank { resolveAppName(budget.packageName) },
                    maxMinutes = budget.maxMinutes
                )
            )
        }
        
        return result.sortedBy { it.appName.lowercase() }
    }

    fun isEffectiveNow(): Boolean {
        if (!isEnabled) return false
        if (isDefault || name.equals("Default", ignoreCase = true)) return true
        return isCurrentTimeWithinSchedule(startTime, endTime, activeDays)
    }

    fun getDaysSummary(): String {
        if (daysOfWeek.size >= 7 || daysOfWeek.isEmpty()) return "Everyday"
        val sorted = daysOfWeek.sorted()
        if (sorted == listOf(1, 2, 3, 4, 5)) return "Mon - Fri"
        if (sorted == listOf(6, 7)) return "Sat - Sun"
        val dayNames = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
        return sorted.mapNotNull { dayNames[it] }.joinToString(", ")
    }

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("isDefault", isDefault)
            put("isEnabled", isEnabled)
            put("startHour", startHour)
            put("startMinute", startMinute)
            put("endHour", endHour)
            put("endMinute", endMinute)

            val daysArray = JSONArray()
            daysOfWeek.forEach { daysArray.put(it) }
            put("daysOfWeek", daysArray)

            put("dndEnabled", dndEnabled)
            put("dndMode", dndMode)
            put("brightnessEnabled", brightnessEnabled)
            put("brightnessPercent", brightnessPercent)
            put("appRestrictionsEnabled", appRestrictionsEnabled)

            val blockedArr = JSONArray()
            blockedApps.forEach { blockedArr.put(it) }
            put("blockedApps", blockedArr)

            val groupArr = JSONArray()
            blockedAppGroupIds.forEach { groupArr.put(it) }
            put("blockedAppGroupIds", groupArr)

            put("eyeComfortEnabled", eyeComfortEnabled)
            put("eyeComfortIntensity", eyeComfortIntensity)
            put("autoLaunchAppEnabled", autoLaunchAppEnabled)
            put("autoLaunchPackage", autoLaunchPackage)
            put("autoLaunchAppName", autoLaunchAppName)

            put("stickyNoteEnabled", stickyNoteEnabled)
            put("stickyNoteText", stickyNoteText)
            val checkArr = JSONArray()
            stickyNoteChecklist.forEach { checkArr.put(it.toJsonObject()) }
            put("stickyNoteChecklist", checkArr)

            val budgetArr = JSONArray()
            appBudgets.forEach { budgetArr.put(it.toJsonObject()) }
            put("appBudgets", budgetArr)
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): ContextTimeframeMode {
            val appsList = mutableListOf<String>()
            val rawBlocked = if (obj.has("blockedApps")) obj.optJSONArray("blockedApps") else obj.optJSONArray("restrictedApps")
            if (rawBlocked != null) {
                for (i in 0 until rawBlocked.length()) {
                    appsList.add(rawBlocked.getString(i))
                }
            }

            val groupList = mutableListOf<String>()
            if (obj.has("blockedAppGroupIds")) {
                val groupArr = obj.getJSONArray("blockedAppGroupIds")
                for (i in 0 until groupArr.length()) {
                    groupList.add(groupArr.getString(i))
                }
            }

            val daysSet = mutableSetOf<Int>()
            val rawDays = if (obj.has("daysOfWeek")) obj.optJSONArray("daysOfWeek") else obj.optJSONArray("activeDays")
            if (rawDays != null) {
                for (i in 0 until rawDays.length()) {
                    daysSet.add(rawDays.getInt(i))
                }
            }
            val finalDays = if (daysSet.isEmpty()) setOf(1, 2, 3, 4, 5, 6, 7) else daysSet

            val rawName = obj.optString("name", "Default")
            val isDef = obj.optBoolean("isDefault", rawName.equals("Default", ignoreCase = true) || obj.optBoolean("isAllTime", false))

            val startH = obj.optInt("startHour", 9)
            val startM = obj.optInt("startMinute", 0)
            val endH = obj.optInt("endHour", 17)
            val endM = obj.optInt("endMinute", 0)

            val bright = if (obj.has("brightnessPercent")) obj.optInt("brightnessPercent", 50) else obj.optInt("brightnessLevel", 50)
            val comfort = if (obj.has("eyeComfortIntensity")) obj.optInt("eyeComfortIntensity", 50) else obj.optInt("eyeComfortLevel", 50)

            val checkList = mutableListOf<StickyNoteCheckItem>()
            if (obj.has("stickyNoteChecklist")) {
                val cArr = obj.optJSONArray("stickyNoteChecklist")
                if (cArr != null) {
                    for (i in 0 until cArr.length()) {
                        val itemObj = cArr.optJSONObject(i)
                        if (itemObj != null) {
                            checkList.add(StickyNoteCheckItem.fromJsonObject(itemObj))
                        } else {
                            val str = cArr.optString(i, "")
                            if (str.isNotBlank()) {
                                checkList.add(StickyNoteCheckItem(text = str))
                            }
                        }
                    }
                }
            }

            val budgetList = mutableListOf<AppUsageBudget>()
            if (obj.has("appBudgets")) {
                val bArr = obj.optJSONArray("appBudgets")
                if (bArr != null) {
                    for (i in 0 until bArr.length()) {
                        val bObj = bArr.optJSONObject(i)
                        if (bObj != null) {
                            budgetList.add(AppUsageBudget.fromJsonObject(bObj))
                        }
                    }
                }
            }

            return ContextTimeframeMode(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = if (rawName == "All Time Standard") "Default" else rawName,
                isDefault = isDef,
                isEnabled = obj.optBoolean("isEnabled", true),
                startHour = startH,
                startMinute = startM,
                endHour = endH,
                endMinute = endM,
                daysOfWeek = finalDays,
                dndEnabled = obj.optBoolean("dndEnabled", false),
                dndMode = obj.optString("dndMode", "Priority Only"),
                brightnessEnabled = obj.optBoolean("brightnessEnabled", false),
                brightnessPercent = bright,
                appRestrictionsEnabled = obj.optBoolean("appRestrictionsEnabled", false),
                blockedApps = appsList,
                blockedAppGroupIds = groupList,
                eyeComfortEnabled = obj.optBoolean("eyeComfortEnabled", false),
                eyeComfortIntensity = comfort,
                autoLaunchAppEnabled = obj.optBoolean("autoLaunchAppEnabled", false),
                autoLaunchPackage = obj.optString("autoLaunchPackage", ""),
                autoLaunchAppName = obj.optString("autoLaunchAppName", ""),
                stickyNoteEnabled = obj.optBoolean("stickyNoteEnabled", false),
                stickyNoteText = obj.optString("stickyNoteText", ""),
                stickyNoteChecklist = checkList,
                appBudgets = budgetList
            )
        }
    }
}

/**
 * Sorts modes chronologically: Default mode first, followed by custom modes ordered by day of week and start time.
 */
fun sortModesChronologically(modesList: List<ContextTimeframeMode>): List<ContextTimeframeMode> {
    val defaultMode = modesList.find { it.isDefault || it.name.equals("Default", ignoreCase = true) }
    val customModes = modesList.filter { it != defaultMode }.sortedWith(
        compareBy(
            { it.daysOfWeek.minOrNull() ?: 1 },
            { it.startHour * 60 + it.startMinute }
        )
    )
    return if (defaultMode != null) listOf(defaultMode) + customModes else customModes
}

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

    val dailyUncovered = mutableMapOf<Int, String>()
    for (day in 1..7) {
        val modesOnDay = customModes.filter { it.isEnabled && it.daysOfWeek.contains(day) }
        if (modesOnDay.isEmpty()) {
            dailyUncovered[day] = "All day (24 hrs)"
        } else {
            val intervals = modesOnDay.map {
                (it.startHour * 60 + it.startMinute) to (it.endHour * 60 + it.endMinute)
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

/**
 * Encapsulates the multi-mode context configuration for a physical space.
 */
data class RoomProfile(
    val spaceName: String = "",
    val dndEnabled: Boolean = false,
    val dndMode: String = "Priority Only",
    val brightnessEnabled: Boolean = false,
    val brightnessPercent: Int = 50,
    val appRestrictionsEnabled: Boolean = false,
    val blockedApps: List<String> = emptyList(),
    val blockedAppGroupIds: List<String> = emptyList(),
    val eyeComfortEnabled: Boolean = false,
    val eyeComfortIntensity: Int = 50,
    val autoLaunchAppEnabled: Boolean = false,
    val autoLaunchPackage: String = "",
    val autoLaunchAppName: String = "",
    val modes: List<ContextTimeframeMode> = listOf(
        ContextTimeframeMode(name = "Default", isDefault = true)
    ),
    val autoRestoreOnExit: Boolean = true,
    val customNotes: String = ""
) {
    // Normalized list of modes ensuring at least one default mode exists
    fun normalizedModes(): List<ContextTimeframeMode> {
        if (modes.isEmpty()) {
            return listOf(ContextTimeframeMode(name = "Default", isDefault = true))
        }
        val hasDef = modes.any { it.isDefault || it.name.equals("Default", ignoreCase = true) }
        return if (!hasDef) {
            listOf(ContextTimeframeMode(name = "Default", isDefault = true)) + modes
        } else {
            modes
        }
    }

    /**
     * Resolves the active mode at a given minute of day and day of week.
     * Custom modes that are enabled take priority over the default mode.
     */
    fun resolveActiveMode(minuteOfDay: Int? = null, dayOfWeek: Int? = null): ContextTimeframeMode {
        val norm = normalizedModes()
        val customModes = norm.filter { !it.isDefault && !it.name.equals("Default", ignoreCase = true) && it.isEnabled }
            .sortedWith(
                compareBy(
                    { it.daysOfWeek.minOrNull() ?: 1 },
                    { it.startHour * 60 + it.startMinute }
                )
            )

        if (minuteOfDay != null && dayOfWeek != null) {
            val matchingCustom = customModes.find { mode ->
                if (!mode.daysOfWeek.contains(dayOfWeek)) return@find false
                val startM = mode.startHour * 60 + mode.startMinute
                val endM = mode.endHour * 60 + mode.endMinute
                if (startM < endM) {
                    minuteOfDay in startM until endM
                } else if (startM > endM) {
                    minuteOfDay >= startM || minuteOfDay < endM
                } else {
                    true
                }
            }
            if (matchingCustom != null) return matchingCustom
        } else {
            val activeCustom = customModes.find { it.isEffectiveNow() }
            if (activeCustom != null) return activeCustom
        }

        val defaultMode = norm.find { it.isDefault || it.name.equals("Default", ignoreCase = true) }
        return defaultMode ?: norm.first()
    }

    /**
     * Synchronizes top-level properties from the default or currently active mode
     */
    fun syncFromModes(): RoomProfile {
        val defaultMode = normalizedModes().find { it.isDefault || it.name.equals("Default", ignoreCase = true) }
            ?: normalizedModes().first()
        return copy(
            dndEnabled = defaultMode.dndEnabled,
            dndMode = defaultMode.dndMode,
            brightnessEnabled = defaultMode.brightnessEnabled,
            brightnessPercent = defaultMode.brightnessPercent,
            appRestrictionsEnabled = defaultMode.appRestrictionsEnabled,
            blockedApps = defaultMode.blockedApps,
            blockedAppGroupIds = defaultMode.blockedAppGroupIds,
            eyeComfortEnabled = defaultMode.eyeComfortEnabled,
            eyeComfortIntensity = defaultMode.eyeComfortIntensity,
            autoLaunchAppEnabled = defaultMode.autoLaunchAppEnabled,
            autoLaunchPackage = defaultMode.autoLaunchPackage,
            autoLaunchAppName = defaultMode.autoLaunchAppName
        )
    }

    // Property forwards & aliases
    val brightnessLevel: Int get() = brightnessPercent
    val eyeComfortLevel: Int get() = eyeComfortIntensity
    val restrictedApps: List<String> get() = blockedApps

    fun isDndEffectiveNow(): Boolean = resolveActiveMode().dndEnabled
    fun isBrightnessEffectiveNow(): Boolean = resolveActiveMode().brightnessEnabled
    fun isAppRestrictionsEffectiveNow(): Boolean = resolveActiveMode().appRestrictionsEnabled
    fun isEyeComfortEffectiveNow(): Boolean = resolveActiveMode().eyeComfortEnabled
    fun isAutoLaunchEffectiveNow(): Boolean = resolveActiveMode().autoLaunchAppEnabled

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("spaceName", spaceName)
            put("dndEnabled", dndEnabled)
            put("dndMode", dndMode)
            put("brightnessEnabled", brightnessEnabled)
            put("brightnessPercent", brightnessPercent)
            put("appRestrictionsEnabled", appRestrictionsEnabled)
            put("eyeComfortEnabled", eyeComfortEnabled)
            put("eyeComfortIntensity", eyeComfortIntensity)
            put("autoLaunchAppEnabled", autoLaunchAppEnabled)
            put("autoLaunchPackage", autoLaunchPackage)
            put("autoLaunchAppName", autoLaunchAppName)

            val blockedArray = JSONArray()
            blockedApps.forEach { blockedArray.put(it) }
            put("blockedApps", blockedArray)

            val groupArray = JSONArray()
            blockedAppGroupIds.forEach { groupArray.put(it) }
            put("blockedAppGroupIds", groupArray)

            val modesArray = JSONArray()
            normalizedModes().forEach { modesArray.put(it.toJsonObject()) }
            put("modes", modesArray)
            put("autoRestoreOnExit", autoRestoreOnExit)
            put("customNotes", customNotes)
        }
    }

    companion object {
        fun defaultProfile(spaceName: String = ""): RoomProfile {
            // Newly created contexts are completely brand new and clean with only a blank Default 24/7 mode
            return RoomProfile(
                spaceName = spaceName,
                modes = listOf(
                    ContextTimeframeMode(name = "Default", isDefault = true, isEnabled = true)
                )
            ).syncFromModes()
        }

        fun initialSeedProfile(spaceName: String): RoomProfile {
            return when (spaceName) {
                "Bedroom" -> RoomProfile(
                    spaceName = "Bedroom",
                    dndEnabled = true,
                    brightnessPercent = 25,
                    modes = listOf(
                        ContextTimeframeMode(
                            name = "Default",
                            isDefault = true,
                            isEnabled = true,
                            dndEnabled = true,
                            brightnessEnabled = true,
                            brightnessPercent = 25
                        ),
                        ContextTimeframeMode(
                            name = "Deep Sleep",
                            isDefault = false,
                            isEnabled = true,
                            startHour = 22,
                            startMinute = 0,
                            endHour = 7,
                            endMinute = 0,
                            dndEnabled = true,
                            dndMode = "Total Silence",
                            brightnessEnabled = true,
                            brightnessPercent = 10,
                            eyeComfortEnabled = true,
                            eyeComfortIntensity = 80
                        )
                    )
                ).syncFromModes()

                "Office" -> RoomProfile(
                    spaceName = "Office",
                    dndEnabled = false,
                    brightnessPercent = 75,
                    modes = listOf(
                        ContextTimeframeMode(
                            name = "Default",
                            isDefault = true,
                            isEnabled = true,
                            brightnessEnabled = true,
                            brightnessPercent = 75
                        ),
                        ContextTimeframeMode(
                            name = "Deep Work",
                            isDefault = false,
                            isEnabled = true,
                            startHour = 9,
                            startMinute = 0,
                            endHour = 17,
                            endMinute = 0,
                            daysOfWeek = setOf(1, 2, 3, 4, 5),
                            dndEnabled = true,
                            dndMode = "Priority Only",
                            brightnessEnabled = true,
                            brightnessPercent = 80,
                            appRestrictionsEnabled = true,
                            blockedApps = listOf("com.instagram.android", "com.twitter.android", "com.zhiliaoapp.musically")
                        )
                    )
                ).syncFromModes()

                "Living Room" -> RoomProfile(
                    spaceName = "Living Room",
                    dndEnabled = false,
                    brightnessPercent = 60,
                    modes = listOf(
                        ContextTimeframeMode(
                            name = "Default",
                            isDefault = true,
                            isEnabled = true,
                            brightnessEnabled = true,
                            brightnessPercent = 60
                        )
                    )
                ).syncFromModes()

                "Mosque" -> RoomProfile(
                    spaceName = "Mosque",
                    dndEnabled = true,
                    dndMode = "Total Silence",
                    brightnessPercent = 15,
                    modes = listOf(
                        ContextTimeframeMode(
                            name = "Default",
                            isDefault = true,
                            isEnabled = true,
                            dndEnabled = true,
                            dndMode = "Total Silence",
                            brightnessEnabled = true,
                            brightnessPercent = 15
                        )
                    )
                ).syncFromModes()

                else -> defaultProfile(spaceName)
            }
        }

        fun fromJsonObject(obj: JSONObject): RoomProfile {
            val space = obj.optString("spaceName", "")
            val modesList = mutableListOf<ContextTimeframeMode>()
            if (obj.has("modes")) {
                val array = obj.getJSONArray("modes")
                for (i in 0 until array.length()) {
                    modesList.add(ContextTimeframeMode.fromJsonObject(array.getJSONObject(i)))
                }
            } else {
                val appsList = mutableListOf<String>()
                val rawBlocked = if (obj.has("blockedApps")) obj.optJSONArray("blockedApps") else obj.optJSONArray("restrictedApps")
                if (rawBlocked != null) {
                    for (i in 0 until rawBlocked.length()) {
                        appsList.add(rawBlocked.getString(i))
                    }
                }
                modesList.add(
                    ContextTimeframeMode(
                        name = "Default",
                        isDefault = true,
                        dndEnabled = obj.optBoolean("dndEnabled", false),
                        dndMode = obj.optString("dndMode", "Priority Only"),
                        brightnessEnabled = obj.optBoolean("brightnessEnabled", false),
                        brightnessPercent = obj.optInt("brightnessPercent", obj.optInt("brightnessLevel", 50)),
                        appRestrictionsEnabled = obj.optBoolean("appRestrictionsEnabled", false),
                        blockedApps = appsList,
                        eyeComfortEnabled = obj.optBoolean("eyeComfortEnabled", false)
                    )
                )
            }

            return RoomProfile(
                spaceName = space,
                dndEnabled = obj.optBoolean("dndEnabled", false),
                dndMode = obj.optString("dndMode", "Priority Only"),
                brightnessEnabled = obj.optBoolean("brightnessEnabled", false),
                brightnessPercent = obj.optInt("brightnessPercent", 50),
                appRestrictionsEnabled = obj.optBoolean("appRestrictionsEnabled", false),
                eyeComfortEnabled = obj.optBoolean("eyeComfortEnabled", false),
                eyeComfortIntensity = obj.optInt("eyeComfortIntensity", 50),
                modes = sortModesChronologically(modesList),
                autoRestoreOnExit = obj.optBoolean("autoRestoreOnExit", true),
                customNotes = obj.optString("customNotes", "")
            ).syncFromModes()
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
    val isActive: Boolean = false,
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
            put("isActive", isActive)
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
                isActive = obj.optBoolean("isActive", false),
                profile = RoomProfile.fromJsonObject(profileObj),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
            )
        }
    }
}

/**
 * Backward compatibility typealias across the codebase.
 */
typealias RoomItem = ContextItem
