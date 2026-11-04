package com.contextpulse.phase0.widget

import android.content.Context
import android.content.SharedPreferences
import com.contextpulse.phase0.repository.RoomProfileRepository
import org.json.JSONArray

object WidgetPreferences {

    private const val PREFS_NAME = "contextpulse_widget_preferences"
    private const val KEY_DEFAULT_PROFILES = "key_default_widget_profiles"
    private const val KEY_WIDGET_PREFIX = "key_widget_profiles_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getChosenProfileIds(context: Context, appWidgetId: Int): List<String> {
        val prefs = getPrefs(context)
        val hasWidgetKey = prefs.contains(KEY_WIDGET_PREFIX + appWidgetId)
        val hasDefaultKey = prefs.contains(KEY_DEFAULT_PROFILES)

        val jsonStr = prefs.getString(KEY_WIDGET_PREFIX + appWidgetId, null)
            ?: prefs.getString(KEY_DEFAULT_PROFILES, null)

        val list = mutableListOf<String>()
        if (!jsonStr.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }

        val roomRepo = RoomProfileRepository.getInstance(context)
        val allRooms = roomRepo.rooms.value
        val validIds = list.filter { id -> allRooms.any { it.id == id } }.take(3)

        // If user already saved a preference (even if 0, 1, or 2 items), respect it exactly!
        if (hasWidgetKey || hasDefaultKey) {
            return validIds
        }

        // Only on fresh first launch fallback to first 3 rooms if available
        return allRooms.take(3).map { it.id }
    }

    fun saveChosenProfileIds(context: Context, appWidgetId: Int, ids: List<String>) {
        val trimmed = ids.take(3)
        val json = JSONArray(trimmed).toString()
        getPrefs(context).edit()
            .putString(KEY_WIDGET_PREFIX + appWidgetId, json)
            .putString(KEY_DEFAULT_PROFILES, json)
            .apply()
    }

    fun getDefaultProfileIds(context: Context): List<String> {
        val prefs = getPrefs(context)
        val hasKey = prefs.contains(KEY_DEFAULT_PROFILES)
        val jsonStr = prefs.getString(KEY_DEFAULT_PROFILES, null)
        val list = mutableListOf<String>()
        if (!jsonStr.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        val roomRepo = RoomProfileRepository.getInstance(context)
        val allRooms = roomRepo.rooms.value
        val valid = list.filter { id -> allRooms.any { it.id == id } }.take(3)

        if (hasKey) {
            return valid
        }

        return allRooms.take(3).map { it.id }
    }

    fun saveDefaultProfileIds(context: Context, ids: List<String>) {
        val trimmed = ids.take(3)
        val json = JSONArray(trimmed).toString()
        getPrefs(context).edit()
            .putString(KEY_DEFAULT_PROFILES, json)
            .apply()
    }

    fun deleteWidgetPreferences(context: Context, appWidgetId: Int) {
        getPrefs(context).edit()
            .remove(KEY_WIDGET_PREFIX + appWidgetId)
            .apply()
    }
}
