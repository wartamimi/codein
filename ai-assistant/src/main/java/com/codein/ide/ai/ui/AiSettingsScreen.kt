package com.codein.ide.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codein.ide.ai.model.AiProvider
import com.codein.ide.ai.settings.AiSettings
import com.codein.ide.ai.settings.AiSettingsRepository
import com.codein.ide.core.ui.components.CodeinCard
import com.codein.ide.core.ui.components.PillChip
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.theme.CodeinTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class AiSettingsViewModel @Inject constructor(private val repository: AiSettingsRepository) : ViewModel() {

    val settings: StateFlow<AiSettings> = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiSettings())

    fun selectProvider(provider: AiProvider) = viewModelScope.launch { repository.setActiveProvider(provider) }

    fun save(provider: AiProvider, apiKey: String, model: String, baseUrl: String) = viewModelScope.launch {
        if (apiKey.isNotBlank()) repository.setApiKey(provider, apiKey)
        repository.setModel(provider, model.ifBlank { provider.defaultModel })
        repository.setBaseUrl(provider, baseUrl.ifBlank { provider.defaultBaseUrl })
    }

    fun clearKey(provider: AiProvider) = viewModelScope.launch { repository.clearApiKey(provider) }

    fun setMaxTokens(tokens: Int) = viewModelScope.launch { repository.setMaxOutputTokens(tokens) }
}

/** Provider, model, base URL and API key editor. Embedded in the app's Settings screen. */
@Composable
fun AiProviderSettings(
    settings: AiSettings,
    onSelectProvider: (AiProvider) -> Unit,
    onSave: (AiProvider, String, String, String) -> Unit,
    onClearKey: (AiProvider) -> Unit,
    onMaxTokens: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val provider = settings.activeProvider
    val current = settings.providers[provider]
    var apiKey by rememberSaveable(provider) { mutableStateOf("") }
    var model by rememberSaveable(provider) { mutableStateOf(current?.model ?: provider.defaultModel) }
    var baseUrl by rememberSaveable(provider) { mutableStateOf(current?.baseUrl ?: provider.defaultBaseUrl) }
    var saved by remember(provider) { mutableStateOf(false) }
    LaunchedEffect(current?.model, current?.baseUrl) {
        current?.let { model = it.model; baseUrl = it.baseUrl }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiProvider.entries.forEach { p ->
                PillChip(
                    p.displayName,
                    selected = p == provider,
                    trailing = if (settings.providers[p]?.hasApiKey == true) CodeinIcons.Check else null,
                    onClick = { onSelectProvider(p) },
                )
            }
        }
        CodeinCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (current?.hasApiKey == true) "Stored key: ${current.maskedKey}" else "No API key stored",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (current?.hasApiKey == true) CodeinTheme.colors.success else CodeinTheme.colors.textMuted,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; saved = false },
                    label = { Text(if (current?.hasApiKey == true) "Replace API key" else "API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it; saved = false },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    provider.suggestedModels.forEach { m -> PillChip(m, selected = m == model, onClick = { model = m; saved = false }) }
                }
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; saved = false },
                    label = { Text("Base URL") },
                    supportingText = {
                        Text(
                            when (provider) {
                                AiProvider.Anthropic -> "Requests go to <base>/v1/messages"
                                AiProvider.OpenAiCompatible -> "Requests go to <base>/chat/completions (OpenAI, OpenRouter, Groq, Ollama, LM Studio…)"
                                AiProvider.Gemini -> "Requests go to <base>/v1beta/models/<model>:streamGenerateContent"
                            },
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { onSave(provider, apiKey, model, baseUrl); apiKey = ""; saved = true }) { Text(if (saved) "Saved" else "Save") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { onClearKey(provider) }, enabled = current?.hasApiKey == true) { Text("Remove key") }
                }
            }
        }
        Text("Max output tokens · ${settings.maxOutputTokens}", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = settings.maxOutputTokens.toFloat(),
            onValueChange = { onMaxTokens((it / 1024f).roundToInt() * 1024) },
            valueRange = AiSettingsRepository.MIN_TOKENS.toFloat()..AiSettingsRepository.MAX_TOKENS_LIMIT.toFloat(),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            "Keys are encrypted with an AES-256-GCM key held in the Android Keystore and never leave the device except in requests to the provider you choose.",
            style = MaterialTheme.typography.bodySmall,
            color = CodeinTheme.colors.textMuted,
        )
    }
}
