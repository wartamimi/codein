package com.codein.ide

import android.app.Application
import com.codein.ide.core.ui.i18n.I18n
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CodeinApplication : Application() {
    override fun onCreate() {
        // The interface language must be known before Hilt creates anything that shows text.
        I18n.load(this)
        com.codein.ide.core.ui.prefs.UiPrefs.load(this)
        super.onCreate()
    }
}
