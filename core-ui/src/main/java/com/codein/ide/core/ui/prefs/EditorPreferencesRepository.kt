package com.codein.ide.core.ui.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.codein.ide.core.ui.theme.CodingFont
import com.codein.ide.core.ui.theme.EditorColorScheme
import com.codein.ide.core.ui.theme.EditorColorSchemes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

enum class AppThemeMode { Dark, Light, System }

/** Immutable snapshot of all editor and appearance preferences. */
data class EditorPreferences(
    val codingFont: CodingFont = CodingFont.JetBrainsMono,
    val fontSizeSp: Float = 14f,
    val lineHeightMultiplier: Float = 1.35f,
    val ligatures: Boolean = true,
    val tabSize: Int = 4,
    val softWrap: Boolean = false,
    val editorSchemeId: String = EditorColorSchemes.Default.id,
    val dynamicColor: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.Dark,
    val autoSave: Boolean = true,
    val showIndentGuides: Boolean = true,
    val hapticExtraKeys: Boolean = true,
) {
    val editorScheme: EditorColorScheme get() = EditorColorSchemes.byId(editorSchemeId)

    /**
     * The theme actually shown. The chosen theme wins when its brightness matches the app theme
     * mode; otherwise Codein falls back to Obsidian (dark) or Clean Daylight (light), so the editor
     * and the chrome always agree.
     */
    fun resolveScheme(systemDark: Boolean): EditorColorScheme {
        val wantDark = when (themeMode) {
            AppThemeMode.Dark -> true
            AppThemeMode.Light -> false
            AppThemeMode.System -> systemDark
        }
        val chosen = editorScheme
        return when {
            chosen.isDark == wantDark -> chosen
            wantDark -> EditorColorSchemes.Default
            else -> EditorColorSchemes.DefaultLight
        }
    }

    companion object {
        const val MIN_FONT_SIZE = 11f
        const val MAX_FONT_SIZE = 22f
        const val MIN_LINE_HEIGHT = 1.15f
        const val MAX_LINE_HEIGHT = 1.55f
        val TAB_SIZES = listOf(2, 4)
    }
}

/** DataStore-backed repository for [EditorPreferences]. All writes are clamped to valid ranges. */
@Singleton
class EditorPreferencesRepository @Inject constructor(
    @Named(EDITOR_PREFS) private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val font = stringPreferencesKey("coding_font")
        val fontSize = floatPreferencesKey("font_size_sp")
        val lineHeight = floatPreferencesKey("line_height")
        val ligatures = booleanPreferencesKey("ligatures")
        val tabSize = intPreferencesKey("tab_size")
        val softWrap = booleanPreferencesKey("soft_wrap")
        val scheme = stringPreferencesKey("editor_scheme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val themeMode = stringPreferencesKey("theme_mode")
        val autoSave = booleanPreferencesKey("auto_save")
        val indentGuides = booleanPreferencesKey("indent_guides")
        val haptics = booleanPreferencesKey("haptic_extra_keys")
    }

    val preferences: Flow<EditorPreferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            val defaults = EditorPreferences()
            EditorPreferences(
                codingFont = p[Keys.font]?.let(CodingFont::byId) ?: defaults.codingFont,
                fontSizeSp = (p[Keys.fontSize] ?: defaults.fontSizeSp)
                    .coerceIn(EditorPreferences.MIN_FONT_SIZE, EditorPreferences.MAX_FONT_SIZE),
                lineHeightMultiplier = (p[Keys.lineHeight] ?: defaults.lineHeightMultiplier)
                    .coerceIn(EditorPreferences.MIN_LINE_HEIGHT, EditorPreferences.MAX_LINE_HEIGHT),
                ligatures = p[Keys.ligatures] ?: defaults.ligatures,
                tabSize = (p[Keys.tabSize] ?: defaults.tabSize).let { if (it in EditorPreferences.TAB_SIZES) it else 4 },
                softWrap = p[Keys.softWrap] ?: defaults.softWrap,
                editorSchemeId = p[Keys.scheme] ?: defaults.editorSchemeId,
                dynamicColor = p[Keys.dynamicColor] ?: defaults.dynamicColor,
                themeMode = p[Keys.themeMode]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() } ?: defaults.themeMode,
                autoSave = p[Keys.autoSave] ?: defaults.autoSave,
                showIndentGuides = p[Keys.indentGuides] ?: defaults.showIndentGuides,
                hapticExtraKeys = p[Keys.haptics] ?: defaults.hapticExtraKeys,
            )
        }
        .distinctUntilChanged()

    suspend fun setCodingFont(font: CodingFont) = dataStore.edit { it[Keys.font] = font.id }

    suspend fun setFontSize(sizeSp: Float) = dataStore.edit {
        it[Keys.fontSize] = sizeSp.coerceIn(EditorPreferences.MIN_FONT_SIZE, EditorPreferences.MAX_FONT_SIZE)
    }

    suspend fun setLineHeight(multiplier: Float) = dataStore.edit {
        it[Keys.lineHeight] = multiplier.coerceIn(EditorPreferences.MIN_LINE_HEIGHT, EditorPreferences.MAX_LINE_HEIGHT)
    }

    suspend fun setLigatures(enabled: Boolean) = dataStore.edit { it[Keys.ligatures] = enabled }

    suspend fun setTabSize(size: Int) = dataStore.edit {
        it[Keys.tabSize] = if (size in EditorPreferences.TAB_SIZES) size else 4
    }

    suspend fun setSoftWrap(enabled: Boolean) = dataStore.edit { it[Keys.softWrap] = enabled }

    suspend fun setEditorScheme(id: String) = dataStore.edit { it[Keys.scheme] = EditorColorSchemes.byId(id).id }

    suspend fun setDynamicColor(enabled: Boolean) = dataStore.edit { it[Keys.dynamicColor] = enabled }

    suspend fun setThemeMode(mode: AppThemeMode) = dataStore.edit { it[Keys.themeMode] = mode.name }

    suspend fun setAutoSave(enabled: Boolean) = dataStore.edit { it[Keys.autoSave] = enabled }

    suspend fun setIndentGuides(enabled: Boolean) = dataStore.edit { it[Keys.indentGuides] = enabled }

    suspend fun setHapticExtraKeys(enabled: Boolean) = dataStore.edit { it[Keys.haptics] = enabled }

    companion object {
        const val EDITOR_PREFS = "editor_prefs"
    }
}
