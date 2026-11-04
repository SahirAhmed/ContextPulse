package com.contextpulse.phase0.repository

import android.content.Context
import android.content.SharedPreferences
import com.contextpulse.phase0.model.AppGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Persistent repository managing user-defined App Groups for bulk profile blocking rules.
 */
class AppGroupRepository(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "contextpulse_app_groups"
        private const val KEY_APP_GROUPS = "key_app_groups_json"

        @Volatile
        private var INSTANCE: AppGroupRepository? = null

        fun getInstance(context: Context): AppGroupRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppGroupRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _appGroups = MutableStateFlow<List<AppGroup>>(emptyList())
    val appGroups: StateFlow<List<AppGroup>> = _appGroups.asStateFlow()

    init {
        loadData()
    }

    @Synchronized
    fun loadData() {
        val json = prefs.getString(KEY_APP_GROUPS, null)
        if (json.isNullOrBlank()) {
            _appGroups.value = emptyList()
        } else {
            try {
                val arr = JSONArray(json)
                val list = mutableListOf<AppGroup>()
                for (i in 0 until arr.length()) {
                    list.add(AppGroup.fromJson(arr.getJSONObject(i)))
                }
                _appGroups.value = list
            } catch (e: Exception) {
                _appGroups.value = emptyList()
            }
        }
    }

    private fun persist(list: List<AppGroup>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_APP_GROUPS, arr.toString()).apply()
        _appGroups.value = list
    }

    fun addGroup(name: String, packageNames: List<String>): AppGroup {
        val newGroup = AppGroup(name = name.trim(), packageNames = packageNames.distinct())
        val updated = _appGroups.value + newGroup
        persist(updated)
        return newGroup
    }

    fun updateGroup(id: String, name: String, packageNames: List<String>) {
        val updated = _appGroups.value.map {
            if (it.id == id) it.copy(name = name.trim(), packageNames = packageNames.distinct()) else it
        }
        persist(updated)
    }

    fun deleteGroup(id: String) {
        val updated = _appGroups.value.filterNot { it.id == id }
        persist(updated)
    }

    fun clearAll() {
        prefs.edit().remove(KEY_APP_GROUPS).apply()
        _appGroups.value = emptyList()
    }
}
