package com.codein.ide.core.ui.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Small app-wide UI switches that every screen reads synchronously: one-handed mode and whether
 * the first-run tour was seen. Kept in SharedPreferences next to the interface language.
 */
object UiPrefs {
    private const val PREFS = "codein_ui"
    private const val ONE_HANDED = "one_handed"
    private const val TOUR_SEEN = "tour_seen_v1"

    private val _oneHanded = MutableStateFlow(false)
    val oneHanded: StateFlow<Boolean> = _oneHanded.asStateFlow()

    private val _tourSeen = MutableStateFlow(true)
    val tourSeen: StateFlow<Boolean> = _tourSeen.asStateFlow()

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _oneHanded.value = p.getBoolean(ONE_HANDED, false)
        _tourSeen.value = p.getBoolean(TOUR_SEEN, false)
    }

    fun setOneHanded(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ONE_HANDED, value).apply()
        _oneHanded.value = value
    }

    fun setTourSeen(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(TOUR_SEEN, value).apply()
        _tourSeen.value = value
    }
}
