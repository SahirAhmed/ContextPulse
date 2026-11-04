package com.contextpulse.phase0.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 8 Intentional Color Themes for ContextPulse:
 * - 6 Dark variations (Black & Canary Yellow, Black & Electric Red, Black & Cyber Blue,
 *   Black & Neon Green, Black & Ultra Purple, Black & Sunset Orange)
 * - 2 Light/White variations (Pearl White & Cobalt Blue, Monochrome White & Jet Black)
 */
enum class AppThemeKey(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val primaryHex: Long,
    val backgroundHex: Long,
    val surfaceHex: Long,
    val surfaceVariantHex: Long,
    val onPrimaryHex: Long,
    val textPrimaryHex: Long,
    val textSecondaryHex: Long,
    val accentHex: Long,
    val borderHex: Long,
    val isLight: Boolean,
    val aliasName: String? // null for default MainActivity
) {
    CANARY_YELLOW(
        id = "yellow",
        displayName = "Canary Yellow",
        subtitle = "Signature High-Contrast Dark",
        primaryHex = 0xFFFFDF36,
        backgroundHex = 0xFF0C0D0E,
        surfaceHex = 0xFF16171B,
        surfaceVariantHex = 0xFF222329,
        onPrimaryHex = 0xFF101010,
        textPrimaryHex = 0xFFFFFFFF,
        textSecondaryHex = 0xFFC5B36A,
        accentHex = 0xFFFFDF36,
        borderHex = 0xFF26272F,
        isLight = false,
        aliasName = "com.contextpulse.phase0.ui.LauncherAliasYellow"
    ),
    ELECTRIC_RED(
        id = "red",
        displayName = "Electric Crimson",
        subtitle = "Black & Vibrant Red",
        primaryHex = 0xFFFF3B56,
        backgroundHex = 0xFF0E0A0B,
        surfaceHex = 0xFF1A1214,
        surfaceVariantHex = 0xFF281C20,
        onPrimaryHex = 0xFFFFFFFF,
        textPrimaryHex = 0xFFFFFFFF,
        textSecondaryHex = 0xFFE57385,
        accentHex = 0xFFFF3B56,
        borderHex = 0xFF352026,
        isLight = false,
        aliasName = "com.contextpulse.phase0.ui.LauncherAliasRed"
    ),
    CYBER_BLUE(
        id = "blue",
        displayName = "Cyber Blue",
        subtitle = "Black & Electric Cyan",
        primaryHex = 0xFF00D2FF,
        backgroundHex = 0xFF080D14,
        surfaceHex = 0xFF111924,
        surfaceVariantHex = 0xFF1B2636,
        onPrimaryHex = 0xFF06141F,
        textPrimaryHex = 0xFFFFFFFF,
        textSecondaryHex = 0xFF6CC5E8,
        accentHex = 0xFF00D2FF,
        borderHex = 0xFF1D2E42,
        isLight = false,
        aliasName = "com.contextpulse.phase0.ui.LauncherAliasBlue"
    ),
    NEON_GREEN(
        id = "green",
        displayName = "Neon Emerald",
        subtitle = "Black & Emerald Green",
        primaryHex = 0xFF00E676,
        backgroundHex = 0xFF070F0B,
        surfaceHex = 0xFF101F18,
        surfaceVariantHex = 0xFF1A2F24,
        onPrimaryHex = 0xFF061A0F,
        textPrimaryHex = 0xFFFFFFFF,
        textSecondaryHex = 0xFF69D99F,
        accentHex = 0xFF00E676,
        borderHex = 0xFF1C3829,
        isLight = false,
        aliasName = "com.contextpulse.phase0.ui.LauncherAliasGreen"
    ),
    ULTRA_PURPLE(
        id = "purple",
        displayName = "Ultra Violet",
        subtitle = "Black & Neon Purple",
        primaryHex = 0xFFB388FF,
        backgroundHex = 0xFF0D0814,
        surfaceHex = 0xFF191126,
        surfaceVariantHex = 0xFF261A3B,
        onPrimaryHex = 0xFF140824,
        textPrimaryHex = 0xFFFFFFFF,
        textSecondaryHex = 0xFFC9ADF8,
        accentHex = 0xFFB388FF,
        borderHex = 0xFF302047,
        isLight = false,
        aliasName = "com.contextpulse.phase0.ui.LauncherAliasPurple"
    ),
    SUNSET_ORANGE(
        id = "orange",
        displayName = "Sunset Amber",
        subtitle = "Black & Vivid Orange",
        primaryHex = 0xFFFF8A00,
        backgroundHex = 0xFF110B06,
        surfaceHex = 0xFF1F150D,
        surfaceVariantHex = 0xFF2E2015,
        onPrimaryHex = 0xFF180A00,
        textPrimaryHex = 0xFFFFFFFF,
        textSecondaryHex = 0xFFEAA96B,
        accentHex = 0xFFFF8A00,
        borderHex = 0xFF3C2714,
        isLight = false,
        aliasName = "com.contextpulse.phase0.ui.LauncherAliasOrange"
    ),
    WHITE_BLUE(
        id = "white_blue",
        displayName = "Pearl & Cobalt",
        subtitle = "Clean White & Cobalt Blue",
        primaryHex = 0xFF1D64F2,
        backgroundHex = 0xFFF5F7FB,
        surfaceHex = 0xFFFFFFFF,
        surfaceVariantHex = 0xFFE8EEF8,
        onPrimaryHex = 0xFFFFFFFF,
        textPrimaryHex = 0xFF0F172A,
        textSecondaryHex = 0xFF4B6A9B,
        accentHex = 0xFF1D64F2,
        borderHex = 0xFFD8E2F2,
        isLight = true,
        aliasName = "com.contextpulse.phase0.ui.LauncherAliasWhiteBlue"
    ),
    WHITE_BLACK(
        id = "white_black",
        displayName = "Pure Monochrome",
        subtitle = "Crisp White & Obsidian Black",
        primaryHex = 0xFF0F172A,
        backgroundHex = 0xFFF8FAFC,
        surfaceHex = 0xFFFFFFFF,
        surfaceVariantHex = 0xFFE2E8F0,
        onPrimaryHex = 0xFFFFFFFF,
        textPrimaryHex = 0xFF0F172A,
        textSecondaryHex = 0xFF64748B,
        accentHex = 0xFF0F172A,
        borderHex = 0xFFCBD5E1,
        isLight = true,
        aliasName = "com.contextpulse.phase0.ui.LauncherAliasWhiteBlack"
    );

    val primary: Color get() = Color(primaryHex)
    val background: Color get() = Color(backgroundHex)
    val surface: Color get() = Color(surfaceHex)
    val surfaceVariant: Color get() = Color(surfaceVariantHex)
    val onPrimary: Color get() = Color(onPrimaryHex)
    val textPrimary: Color get() = Color(textPrimaryHex)
    val textSecondary: Color get() = Color(textSecondaryHex)
    val accent: Color get() = Color(accentHex)
    val border: Color get() = Color(borderHex)

    fun getLauncherIconResId(): Int {
        return when (this) {
            CANARY_YELLOW -> com.example.R.mipmap.ic_launcher_yellow
            ELECTRIC_RED -> com.example.R.mipmap.ic_launcher_red
            CYBER_BLUE -> com.example.R.mipmap.ic_launcher_blue
            NEON_GREEN -> com.example.R.mipmap.ic_launcher_green
            ULTRA_PURPLE -> com.example.R.mipmap.ic_launcher_purple
            SUNSET_ORANGE -> com.example.R.mipmap.ic_launcher_orange
            WHITE_BLUE -> com.example.R.mipmap.ic_launcher_white_blue
            WHITE_BLACK -> com.example.R.mipmap.ic_launcher_white_black
        }
    }
}

val LocalContextPulseTheme = compositionLocalOf { AppThemeKey.CANARY_YELLOW }

class AppThemeRepository(private val context: Context) {
    companion object {
        private const val PREFS_NAME = "contextpulse_theme_prefs"
        private const val KEY_SELECTED_THEME = "key_selected_theme"
        private const val TAG = "AppThemeRepository"

        @Volatile
        private var INSTANCE: AppThemeRepository? = null

        fun getInstance(context: Context): AppThemeRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppThemeRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        val ALL_ALIASES = listOf(
            "com.contextpulse.phase0.ui.LauncherAliasYellow",
            "com.contextpulse.phase0.ui.LauncherAliasRed",
            "com.contextpulse.phase0.ui.LauncherAliasBlue",
            "com.contextpulse.phase0.ui.LauncherAliasGreen",
            "com.contextpulse.phase0.ui.LauncherAliasPurple",
            "com.contextpulse.phase0.ui.LauncherAliasOrange",
            "com.contextpulse.phase0.ui.LauncherAliasWhiteBlue",
            "com.contextpulse.phase0.ui.LauncherAliasWhiteBlack"
        )
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentTheme = MutableStateFlow(loadInitialTheme())
    val currentTheme: StateFlow<AppThemeKey> = _currentTheme.asStateFlow()

    init {
        updateLauncherIcon(_currentTheme.value)
    }

    private fun loadInitialTheme(): AppThemeKey {
        val savedId = prefs.getString(KEY_SELECTED_THEME, AppThemeKey.CANARY_YELLOW.id)
        return AppThemeKey.values().find { it.id == savedId } ?: AppThemeKey.CANARY_YELLOW
    }

    fun setTheme(theme: AppThemeKey) {
        _currentTheme.value = theme
        prefs.edit().putString(KEY_SELECTED_THEME, theme.id).apply()
        updateLauncherIcon(theme)
    }

    private fun updateLauncherIcon(theme: AppThemeKey) {
        try {
            val pm = context.packageManager
            // Ensure MainActivity is ALWAYS enabled so system runner and direct intent launches never fail
            val mainActivityComponent = ComponentName(context.packageName, "com.contextpulse.phase0.ui.MainActivity")
            try {
                if (pm.getComponentEnabledSetting(mainActivityComponent) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    pm.setComponentEnabledSetting(
                        mainActivityComponent,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error ensuring MainActivity enabled: ${e.message}")
            }

            val targetAlias = theme.aliasName ?: "com.contextpulse.phase0.ui.LauncherAliasYellow"

            for (alias in ALL_ALIASES) {
                val component = ComponentName(context.packageName, alias)
                val isTarget = (alias == targetAlias)
                val targetState = if (isTarget) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }

                val currentState = pm.getComponentEnabledSetting(component)
                if (currentState != targetState) {
                    pm.setComponentEnabledSetting(
                        component,
                        targetState,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
            Log.d(TAG, "Successfully updated launcher icon alias to: $targetAlias")
        } catch (e: Exception) {
            Log.w(TAG, "Unable to switch launcher icon alias: ${e.message}")
        }
    }
}
