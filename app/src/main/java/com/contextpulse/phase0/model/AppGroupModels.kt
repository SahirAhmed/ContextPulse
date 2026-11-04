package com.contextpulse.phase0.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Model representing a user-defined group of applications (e.g., "Social Media", "Distracting Apps").
 * Allows users to bulk-toggle blocked apps across any profile.
 */
data class AppGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val packageNames: List<String> = emptyList()
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        val arr = JSONArray()
        packageNames.forEach { arr.put(it) }
        obj.put("packageNames", arr)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): AppGroup {
            val id = obj.optString("id", UUID.randomUUID().toString())
            val name = obj.optString("name", "Untitled Group")
            val arr = obj.optJSONArray("packageNames")
            val list = mutableListOf<String>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    list.add(arr.getString(i))
                }
            }
            return AppGroup(id = id, name = name, packageNames = list)
        }
    }
}
