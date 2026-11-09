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
 * 10 Intentional Color Themes for ContextPulse:
 * - 5 Black Primary Base Themes (Canary Yellow, Electric Crimson, Cyber Blue, Neon Emerald, Ultra Violet)
 * - 5 White Primary Base Themes (Pearl & Cobalt, Pure Monochrome, Alpine Emerald, Solar Amber, Blush Crimson)
 * Each theme features a primary color, a distinct secondary accent color, a third complementary tertiary color
 * to break up visual weight, and clean, high-readability neutral secondary text.
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
    val tertiaryHex: Long,
    val borderHex: Long,
    val isLight: Boolean,
    val aliasName: String? // null for default MainActivity
) {
    // 5 Dark Themes (Deep Black Primary Base)
    CANARY_YELLOW(
        id = "yellow",
        displayName = "Canary Yellow",
        subtitle = "Black Base, Canary Yellow & Sky Cyan",
        primaryHex = 0xFFFFDF36,
        backgroundHex = 0xFF0B0C0E,
        surfaceHex = 0xFF141519,
        surfaceVariantHex = 0xFF1F2128,
        onPrimaryHex = 0xFF101010,
        textPrimaryHex = 0xFFFFFFFF,
        textSecondaryHex = 0xFF94A3B8,
        accentHex = 0xFF38BDF8,
        tertiaryHex = 0xFF38BDF8,
        borderHex = 0xFF272932,
        isLight = false,
        aliasName = "com.contextpulse.phase0.ui.LauncherAliasYellow"
    ),
    ELECTRIC_RED(
        id = "red",
        displayName = "Electric Crimson",
        subtitle = "Black Base, Electric Crimson & Warm Amber",
        primaryHex = 0xFFFF385C,
        backgroundHex = 0xFF0D080A,
        surfaceHex = 0xFF191114,
        surfaceVariantHex = 0xFF26191F,
        onPrimaryHex = 0xFFFFFFFF,
        textPrimaryHex = 0xFFFFFFFF,
        textSecondaryHex = 0xFF94A3B8,
        accentHex = 0xFFFBBF24,
        tertiaryHex = 0xFFFBBF24,
        borderHex = 0xFF352028,
        isLight = false,
        aliasName = "com.contextpulse.phase0.ui.LauncherAliasRed"
    ),
    CYBER_BLUE(
        id = "blue",
        displayName = "Cyber Blue",
        subtitle = "Black Base, Cyber Blue & Neon Coral",
        primaryHex = 0xFF00D2FF,
        backgroundHex = 0xFF080D14,
        surfaceHex = 0xFF111924,
        surfaceVariantHex = 0xFF1B2636,
        onPrimaryHex = 0xFF06141F,
        textPrimaryHex = 0xFFFFFFFF,
        textSecondaryHex = 0xFF94A3B8,
        accentHex = 0xFFFF5376,
        tertiaryHex = 0xFFFF5376,
        borderHex = 0xFF1D2E42,
        isLight = false,
        aliasName = "com.contextpulse.phase0.ui.LauncherAliasBlue"
    ),
    NEON_GREEN(
        id = "green",
        displayName = "Neon Emerald",
        subtitle = "Black Base, Neon Emerald & Sky Cyan",
        primaryHex = 0xFF00E676,
        backgroundHex = 0xFF060E0A,
        surfaceHex = 0xFF0E1A13,
        surfaceVariantHex = 0xFF15271D,
        onPrimaryHex = 0xFF061A0F,
        textPrimaryHex = 0xFFFFFFFF,
        textSecondaryHex = 0xFF94A3B8,
        accentHex = 0xFF38BDF8,
        tertiaryHex = 0xFF38BDF8,
        borderHex = 0xFF1C3829,
        isLight = false,
        aliasName = "com.contextpulse.phase0.ui.LauncherAliasGreen"
    ),
    ULTRA_PURPLE(
        id = "purple",
        displayName = "Ultra Violet",
        subtitle = "Black Base, Ultra Violet & Hot Rose",
        primaryHex = 0xFFB388FF,
        backgroundHex = 0xFF0D0814,
        surfaceHex = 0xFF191126,
        surfaceVariantHex = 0xFF261A3B,
        onPrimaryHex = 0xFF140824,
        textPrimaryHex = 0xFFFFFFFF,
        textSecondaryHex = 0xFF94A3B8,
        accentHex = 0xFFF43F5E,
        tertiaryHex = 0xFFF43F5E,
        borderHex = 0xFF302047,
        isLight = false,
        aliasName = "com.contextpulse.phase0.ui.LauncherAliasPurple"
    ),

    // 5 Light Themes (Crisp White Primary Base)
    WHITE_BLUE(
        id = "white_blue",
        displayName = "Pearl & Cobalt",
        subtitle = "White Base, Cobalt Blue & Deep Teal",
        primaryHex = 0xFF1D64F2,
        backgroundHex = 0xFFF6F8FC,
        surfaceHex = 0xFFFFFFFF,
        surfaceVariantHex = 0xFFEDF2FA,
        onPrimaryHex = 0xFFFFFFFF,
        textPrimaryHex = 0xFF0F172A,
        textSecondaryHex = 0xFF64748B,
        accentHex = 0xFF0D9488,
        tertiaryHex = 0xFF0D9488,
        borderHex = 0xFFD8E2F2,
        isLight = true,
        aliasName = "com.contextpulse.phase0.ui.LauncherAliasWhiteBlue"
    ),
    WHITE_BLACK(
        id = "white_black",
        displayName = "Pure Monochrome",
        subtitle = "White Base, Obsidian Black & Royal Blue",
        primaryHex = 0xFF0F172A,
        backgroundHex = 0xFFF8FAFC,
        surfaceHex = 0xFFFFFFFF,
        surfaceVariantHex = 0xFFF1F5F9,
        onPrimaryHex = 0xFFFFFFFF,
        textPrimaryHex = 0xFF0F172A,
        textSecondaryHex = 0xFF64748B,
        accentHex = 0xFF2563EB,
        tertiaryHex = 0xFF2563EB,
        borderHex = 0xFFCBD5E1,
        isLight = true,
        aliasName = "com.contextpulse.phase0.ui.LauncherAliasWhiteBlack"
    ),
    WHITE_EMERALD(
        id = "white_emerald",
        displayName = "Alpine Emerald",
        subtitle = "White Base, Mint Emerald & Ocean Cyan",
        primaryHex = 0xFF059669,
        backgroundHex = 0xFFF4FAF6,
        surfaceHex = 0xFFFFFFFF,
        surfaceVariantHex = 0xFFE6F4EC,
        onPrimaryHex = 0xFFFFFFFF,
        textPrimaryHex = 0xFF0F172A,
        textSecondaryHex = 0xFF64748B,
        accentHex = 0xFF0284C7,
        tertiaryHex = 0xFF0284C7,
        borderHex = 0xFFCCE8D9,
        isLight = true,
        aliasName = "com.contextpulse.phase0.ui.LauncherAliasWhiteEmerald"
    ),
    WHITE_AMBER(
        id = "white_amber",
        displayName = "Solar Amber",
        subtitle = "White Base, Solar Amber & Coral Red",
        primaryHex = 0xFFD97706,
        backgroundHex = 0xFFFCF9F4,
        surfaceHex = 0xFFFFFFFF,
        surfaceVariantHex = 0xFFF6EFE5,
        onPrimaryHex = 0xFFFFFFFF,
        textPrimaryHex = 0xFF1E1B18,
        textSecondaryHex = 0xFF64748B,
        accentHex = 0xFFE11D48,
        tertiaryHex = 0xFFE11D48,
        borderHex = 0xFFE8DFD0,
        isLight = true,
        aliasName = "com.contextpulse.phase0.ui.LauncherAliasWhitePurple"
    ),
    WHITE_ROSE(
        id = "white_rose",
        displayName = "Blush Crimson",
        subtitle = "White Base, Blush Crimson & Royal Indigo",
        primaryHex = 0xFFE11D48,
        backgroundHex = 0xFFFDF7F8,
        surfaceHex = 0xFFFFFFFF,
        surfaceVariantHex = 0xFFFAEBEE,
        onPrimaryHex = 0xFFFFFFFF,
        textPrimaryHex = 0xFF1E1015,
        textSecondaryHex = 0xFF64748B,
        accentHex = 0xFF4F46E5,
        tertiaryHex = 0xFF4F46E5,
        borderHex = 0xFFEAD4D9,
        isLight = true,
        aliasName = "com.contextpulse.phase0.ui.LauncherAliasWhiteCrimson"
    );

    val primary: Color get() = Color(primaryHex)
    val background: Color get() = Color(backgroundHex)
    val surface: Color get() = Color(surfaceHex)
    val surfaceVariant: Color get() = Color(surfaceVariantHex)
    val onPrimary: Color get() = Color(onPrimaryHex)
    val textPrimary: Color get() = Color(textPrimaryHex)
    val textSecondary: Color get() = Color(textSecondaryHex)
    val accent: Color get() = Color(accentHex)
    val tertiary: Color get() = Color(tertiaryHex)
    val border: Color get() = Color(borderHex)

    fun getLauncherIconResId(): Int {
        return when (this) {
            CANARY_YELLOW -> com.example.R.mipmap.ic_launcher_yellow
            ELECTRIC_RED -> com.example.R.mipmap.ic_launcher_red
            CYBER_BLUE -> com.example.R.mipmap.ic_launcher_blue
            NEON_GREEN -> com.example.R.mipmap.ic_launcher_green
            ULTRA_PURPLE -> com.example.R.mipmap.ic_launcher_purple
            WHITE_BLUE -> com.example.R.mipmap.ic_launcher_white_blue
            WHITE_BLACK -> com.example.R.mipmap.ic_launcher_white_black
            WHITE_EMERALD -> com.example.R.mipmap.ic_launcher_white_emerald
            WHITE_AMBER -> com.example.R.mipmap.ic_launcher_white_purple
            WHITE_ROSE -> com.example.R.mipmap.ic_launcher_white_crimson
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
            "com.contextpulse.phase0.ui.LauncherAliasWhiteBlack",
            "com.contextpulse.phase0.ui.LauncherAliasWhiteEmerald",
            "com.contextpulse.phase0.ui.LauncherAliasWhitePurple",
            "com.contextpulse.phase0.ui.LauncherAliasWhiteCrimson"
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
