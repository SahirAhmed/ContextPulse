package com.contextpulse.phase0.widget

import android.content.Context
import android.content.SharedPreferences
import com.contextpulse.phase0.repository.RoomProfileRepository
import org.json.JSONArray

object WidgetPreferences {

    private const val PREFS_NAME = "contextpulse_widget_preferences"
    private const val KEY_DEFAULT_PROFILES = "key_default_widget_profiles"
    private const val KEY_DEFAULT_PROFILES_6 = "key_default_widget_profiles_6"
    private const val KEY_WIDGET_PREFIX = "key_widget_profiles_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun defaultKeyFor(maxCount: Int): String {
        return if (maxCount > 3) KEY_DEFAULT_PROFILES_6 else KEY_DEFAULT_PROFILES
    }

    fun getChosenProfileIds(
        context: Context,
        appWidgetId: Int,
        maxCount: Int = 3
    ): List<String> {
        val prefs = getPrefs(context)
        val defaultKey = defaultKeyFor(maxCount)
        val hasWidgetKey = prefs.contains(KEY_WIDGET_PREFIX + appWidgetId)
        val hasDefaultKey = prefs.contains(defaultKey)

        val jsonStr = prefs.getString(KEY_WIDGET_PREFIX + appWidgetId, null)
            ?: prefs.getString(defaultKey, null)

        val list = mutableListOf<String>()
        if (!jsonStr.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
            } catch (_: Exception) {
                // Ignore parse errors
            }
        }

        val roomRepo = RoomProfileRepository.getInstance(context)
        val allRooms = roomRepo.rooms.value
        val validIds = list.filter { id -> allRooms.any { it.id == id } }.take(maxCount)

        // If user already saved a preference (even if empty or fewer than maxCount), respect it
        if (hasWidgetKey || hasDefaultKey) {
            return validIds
        }

        // Only on fresh first launch fallback to first maxCount rooms if available
        return allRooms.take(maxCount).map { it.id }
    }

    fun saveChosenProfileIds(
        context: Context,
        appWidgetId: Int,
        ids: List<String>,
        maxCount: Int = 3
    ) {
        val trimmed = ids.take(maxCount)
        val json = JSONArray(trimmed).toString()
        getPrefs(context).edit()
            .putString(KEY_WIDGET_PREFIX + appWidgetId, json)
            .putString(defaultKeyFor(maxCount), json)
            .apply()
    }

    fun getDefaultProfileIds(context: Context, maxCount: Int = 3): List<String> {
        val prefs = getPrefs(context)
        val defaultKey = defaultKeyFor(maxCount)
        val hasKey = prefs.contains(defaultKey)
        val jsonStr = prefs.getString(defaultKey, null)
        val list = mutableListOf<String>()
        if (!jsonStr.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
            } catch (_: Exception) {
                // Ignore
            }
        }

        val roomRepo = RoomProfileRepository.getInstance(context)
        val allRooms = roomRepo.rooms.value
        val valid = list.filter { id -> allRooms.any { it.id == id } }.take(maxCount)

        if (hasKey) {
            return valid
        }

        return allRooms.take(maxCount).map { it.id }
    }

    fun saveDefaultProfileIds(context: Context, ids: List<String>, maxCount: Int = 3) {
        val trimmed = ids.take(maxCount)
        val json = JSONArray(trimmed).toString()
        getPrefs(context).edit()
            .putString(defaultKeyFor(maxCount), json)
            .apply()
    }

    fun deleteWidgetPreferences(context: Context, appWidgetId: Int) {
        getPrefs(context).edit()
            .remove(KEY_WIDGET_PREFIX + appWidgetId)
            .apply()
    }
}
