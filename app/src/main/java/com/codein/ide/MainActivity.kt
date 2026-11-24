package com.codein.ide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codein.ide.core.ui.prefs.EditorPreferences
import com.codein.ide.core.ui.prefs.EditorPreferencesRepository
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.navigation.CodeinNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesRepository: EditorPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val preferences by preferencesRepository.preferences.collectAsStateWithLifecycle(initialValue = EditorPreferences())
            CodeinTheme(preferences) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    CodeinNavHost()
                }
            }
        }
    }
}
