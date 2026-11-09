package com.contextpulse.phase0.repository

import android.content.Context
import android.content.SharedPreferences
import com.contextpulse.phase0.model.AppUsageBudget
import com.contextpulse.phase0.model.ContextTimeframeMode
import com.contextpulse.phase0.model.RoomProfile
import com.contextpulse.phase0.model.StickyNoteCheckItem
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ContextTemplate(
    val id: String,
    val name: String,
    val description: String,
    val iconKey: String,
    val isSystemTemplate: Boolean = false,
    val profile: RoomProfile
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", description)
        put("iconKey", iconKey)
        put("isSystemTemplate", isSystemTemplate)
        put("profile", profile.toJsonObject())
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): ContextTemplate = ContextTemplate(
            id = obj.optString("id", UUID.randomUUID().toString()),
            name = obj.optString("name", "Custom Template"),
            description = obj.optString("description", ""),
            iconKey = obj.optString("iconKey", "meeting_room"),
            isSystemTemplate = obj.optBoolean("isSystemTemplate", false),
            profile = RoomProfile.fromJsonObject(obj.optJSONObject("profile") ?: JSONObject())
        )
    }
}

object ContextTemplateRepository {

    private const val PREFS_NAME = "contextpulse_templates"
    private const val KEY_CUSTOM_TEMPLATES = "custom_templates_json"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getAllTemplates(context: Context): List<ContextTemplate> {
        return getSystemTemplates() + getCustomTemplates(context)
    }

    fun getSystemTemplates(): List<ContextTemplate> {
        return listOf(
            // 1. Work Template
            ContextTemplate(
                id = "template_work",
                name = "Work",
                description = "High focus with priority notifications and daytime brightness.",
                iconKey = "business",
                isSystemTemplate = true,
                profile = RoomProfile(
                    modes = listOf(
                        ContextTimeframeMode(
                            name = "Default",
                            isDefault = true,
                            dndEnabled = true,
                            dndMode = "Priority Only",
                            brightnessEnabled = true,
                            brightnessPercent = 75,
                            appRestrictionsEnabled = false
                        ),
                        ContextTimeframeMode(
                            name = "Morning Deep Focus",
                            isDefault = false,
                            startHour = 9,
                            startMinute = 0,
                            endHour = 12,
                            endMinute = 0,
                            daysOfWeek = setOf(1, 2, 3, 4, 5),
                            dndEnabled = true,
                            dndMode = "Priority Only",
                            brightnessEnabled = true,
                            brightnessPercent = 80,
                            stickyNoteEnabled = true,
                            stickyNoteText = "Daily sprint review & deep work blocks.",
                            stickyNoteChecklist = listOf(
                                StickyNoteCheckItem(text = "Review priorities"),
                                StickyNoteCheckItem(text = "Clear critical messages")
                            )
                        )
                    )
                )
            ),

            // 2. Gym / Fitness Template
            ContextTemplate(
                id = "template_gym",
                name = "Gym",
                description = "High energy environment with maximum screen clarity and workout checklist.",
                iconKey = "fitness_center",
                isSystemTemplate = true,
                profile = RoomProfile(
                    modes = listOf(
                        ContextTimeframeMode(
                            name = "Default",
                            isDefault = true,
                            dndEnabled = true,
                            dndMode = "Priority Only",
                            brightnessEnabled = true,
                            brightnessPercent = 90,
                            stickyNoteEnabled = true,
                            stickyNoteText = "Stay hydrated and maintain proper form.",
                            stickyNoteChecklist = listOf(
                                StickyNoteCheckItem(text = "5-minute warmup"),
                                StickyNoteCheckItem(text = "Complete workout sets"),
                                StickyNoteCheckItem(text = "Cool-down stretch")
                            ),
                            appBudgets = listOf(
                                AppUsageBudget(packageName = "com.spotify.music", appName = "Spotify", maxMinutes = 90)
                            )
                        )
                    )
                )
            ),

            // 3. Sleep / Bedroom Template
            ContextTemplate(
                id = "template_sleep",
                name = "Sleep",
                description = "Complete night-time wind-down with warm amber filter and silence.",
                iconKey = "bed",
                isSystemTemplate = true,
                profile = RoomProfile(
                    modes = listOf(
                        ContextTimeframeMode(
                            name = "Default",
                            isDefault = true,
                            dndEnabled = true,
                            dndMode = "Total Silence",
                            brightnessEnabled = true,
                            brightnessPercent = 10,
                            eyeComfortEnabled = true,
                            eyeComfortIntensity = 80
                        ),
                        ContextTimeframeMode(
                            name = "Evening Wind-Down",
                            isDefault = false,
                            startHour = 21,
                            startMinute = 0,
                            endHour = 23,
                            endMinute = 0,
                            daysOfWeek = setOf(1, 2, 3, 4, 5, 6, 7),
                            dndEnabled = true,
                            dndMode = "Priority Only",
                            brightnessEnabled = true,
                            brightnessPercent = 25,
                            eyeComfortEnabled = true,
                            eyeComfortIntensity = 60,
                            stickyNoteEnabled = true,
                            stickyNoteText = "Prepare for tomorrow and disconnect from screens."
                        )
                    )
                )
            ),

            // 4. Study / Learning Template
            ContextTemplate(
                id = "template_study",
                name = "Study",
                description = "Distraction-free learning environment with app usage allowances.",
                iconKey = "school",
                isSystemTemplate = true,
                profile = RoomProfile(
                    modes = listOf(
                        ContextTimeframeMode(
                            name = "Default",
                            isDefault = true,
                            dndEnabled = true,
                            dndMode = "Priority Only",
                            brightnessEnabled = true,
                            brightnessPercent = 70,
                            eyeComfortEnabled = true,
                            eyeComfortIntensity = 40,
                            stickyNoteEnabled = true,
                            stickyNoteText = "Focus on one topic at a time without multitasking.",
                            stickyNoteChecklist = listOf(
                                StickyNoteCheckItem(text = "Read chapter / notes"),
                                StickyNoteCheckItem(text = "Practice problems")
                            ),
                            appBudgets = listOf(
                                AppUsageBudget(packageName = "com.google.android.youtube", appName = "YouTube", maxMinutes = 20)
                            )
                        )
                    )
                )
            )
        )
    }

    fun getCustomTemplates(context: Context): List<ContextTemplate> {
        val jsonStr = getPrefs(context).getString(KEY_CUSTOM_TEMPLATES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<ContextTemplate>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj != null) {
                    list.add(ContextTemplate.fromJsonObject(obj))
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCustomTemplate(
        context: Context,
        name: String,
        description: String,
        iconKey: String,
        profile: RoomProfile
    ): ContextTemplate {
        val existing = getCustomTemplates(context).toMutableList()
        val newTemplate = ContextTemplate(
            id = "template_custom_${System.currentTimeMillis() % 100000}",
            name = name.trim().ifBlank { "Saved Template" },
            description = description.trim().ifBlank { "Custom user environment template" },
            iconKey = iconKey.ifBlank { "meeting_room" },
            isSystemTemplate = false,
            profile = profile
        )
        existing.add(0, newTemplate)

        val arr = JSONArray()
        existing.forEach { arr.put(it.toJsonObject()) }
        getPrefs(context).edit().putString(KEY_CUSTOM_TEMPLATES, arr.toString()).apply()

        return newTemplate
    }

    fun deleteCustomTemplate(context: Context, templateId: String) {
        val existing = getCustomTemplates(context).filter { it.id != templateId }
        val arr = JSONArray()
        existing.forEach { arr.put(it.toJsonObject()) }
        getPrefs(context).edit().putString(KEY_CUSTOM_TEMPLATES, arr.toString()).apply()
    }
}
