package com.codein.ide.ai.ui

import com.codein.ide.core.ui.i18n.tr
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codein.ide.ai.model.AiProvider
import com.codein.ide.ai.model.ApiFormat
import com.codein.ide.ai.net.LlmClient
import com.codein.ide.ai.net.ModelListException
import com.codein.ide.ai.settings.AiSettings
import com.codein.ide.ai.settings.AiSettingsRepository
import com.codein.ide.ai.settings.ProviderSettings
import com.codein.ide.core.ui.components.PillChip
import com.codein.ide.core.ui.components.SegmentedTabs
import com.codein.ide.core.ui.components.SpinningIcon
import com.codein.ide.core.ui.components.StudioSectionCard
import com.codein.ide.core.ui.components.ToolbarIconButton
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.core.ui.theme.Motion
import com.codein.ide.core.ui.theme.Sizes
import com.codein.ide.core.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/** What the user has typed for one provider, saved or not yet saved. */
data class ProviderDraft(
    val provider: AiProvider,
    val apiKey: String,
    val model: String,
    val baseUrl: String,
    val name: String,
    val format: ApiFormat,
)

/** Progress of a "Refresh models" request. */
data class ModelFetchState(
    val provider: AiProvider? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val fetched: Int? = null,
)

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val repository: AiSettingsRepository,
    private val client: LlmClient,
) : ViewModel() {

    val settings: StateFlow<AiSettings> = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiSettings())

    private val _fetch = MutableStateFlow(ModelFetchState())
    val fetch: StateFlow<ModelFetchState> = _fetch.asStateFlow()
    private var fetchJob: Job? = null

    fun selectProvider(provider: AiProvider) = viewModelScope.launch {
        repository.setActiveProvider(provider)
        if (_fetch.value.provider != provider) _fetch.value = ModelFetchState()
    }

    fun save(draft: ProviderDraft) = viewModelScope.launch {
        val p = draft.provider
        if (draft.apiKey.isNotBlank()) repository.setApiKey(p, draft.apiKey)
        repository.setModel(p, draft.model.ifBlank { p.defaultModel })
        repository.setBaseUrl(p, draft.baseUrl.ifBlank { p.defaultBaseUrl })
        if (p == AiProvider.Custom) repository.setCustomProvider(draft.name, draft.format)
    }

    /** Lists the endpoint's models using the typed (possibly unsaved) URL, key and protocol. */
    fun refreshModels(draft: ProviderDraft) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _fetch.value = ModelFetchState(draft.provider, loading = true)
            val config = repository.configFor(
                provider = draft.provider,
                apiKeyOverride = draft.apiKey,
                baseUrlOverride = draft.baseUrl,
                formatOverride = if (draft.provider == AiProvider.Custom) draft.format else null,
            )
            _fetch.value = try {
                val models = client.listModels(config)
                repository.setAvailableModels(draft.provider, models)
                ModelFetchState(draft.provider, fetched = models.size)
            } catch (e: ModelListException) {
                ModelFetchState(draft.provider, error = e.message)
            }
        }
    }

    fun clearKey(provider: AiProvider) = viewModelScope.launch { repository.clearApiKey(provider) }

    fun setMaxTokens(tokens: Int) = viewModelScope.launch { repository.setMaxOutputTokens(tokens) }
}

private fun AiProvider.icon(): ImageVector = when (this) {
    AiProvider.Anthropic -> CodeinIcons.Sparkle
    AiProvider.OpenAiCompatible -> CodeinIcons.Bot
    AiProvider.Gemini -> CodeinIcons.Globe
    AiProvider.Custom -> CodeinIcons.Server
}

/** One-tap starting points for a custom endpoint. */
private data class EndpointPreset(val name: String, val baseUrl: String, val format: ApiFormat)

private val Presets = listOf(
    EndpointPreset("OpenRouter", "https://openrouter.ai/api/v1", ApiFormat.OpenAi),
    EndpointPreset("Groq", "https://api.groq.com/openai/v1", ApiFormat.OpenAi),
    EndpointPreset("DeepSeek", "https://api.deepseek.com/v1", ApiFormat.OpenAi),
    EndpointPreset("Mistral", "https://api.mistral.ai/v1", ApiFormat.OpenAi),
    EndpointPreset("Together", "https://api.together.xyz/v1", ApiFormat.OpenAi),
    EndpointPreset("Ollama", "http://localhost:11434/v1", ApiFormat.OpenAi),
    EndpointPreset("LM Studio", "http://localhost:1234/v1", ApiFormat.OpenAi),
)

/** Provider picker, connection (custom endpoint, base URL, key), model list with refresh, token budget. */
@Composable
fun AiProviderSettings(
    settings: AiSettings,
    fetch: ModelFetchState,
    onSelectProvider: (AiProvider) -> Unit,
    onSave: (ProviderDraft) -> Unit,
    onRefreshModels: (ProviderDraft) -> Unit,
    onClearKey: (AiProvider) -> Unit,
    onMaxTokens: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val provider = settings.activeProvider
    val current = settings.providers[provider]
    var apiKey by rememberSaveable(provider) { mutableStateOf("") }
    var model by rememberSaveable(provider) { mutableStateOf(current?.model ?: provider.defaultModel) }
    var baseUrl by rememberSaveable(provider) { mutableStateOf(current?.baseUrl ?: provider.defaultBaseUrl) }
    var name by rememberSaveable(provider) { mutableStateOf(if (provider == AiProvider.Custom) current?.label.orEmpty() else "") }
    var format by rememberSaveable(provider) { mutableStateOf(current?.format ?: provider.format) }
    var saved by remember(provider) { mutableStateOf(false) }
    LaunchedEffect(current?.model, current?.baseUrl, current?.label, current?.format) {
        current?.let {
            model = it.model
            baseUrl = it.baseUrl
            format = it.format
            if (provider == AiProvider.Custom) name = it.label
        }
    }
    val draft = ProviderDraft(provider, apiKey, model, baseUrl, name, format)
    val dirty = { saved = false }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        ProviderGrid(settings, provider, onSelectProvider)

        StudioSectionCard(
            title = tr("Connection"),
            icon = CodeinIcons.Server,
            subtitle = if (provider == AiProvider.Custom) "Any endpoint that speaks one of the supported protocols" else format.endpointHint,
        ) {
            Column(Modifier.animateContentSize(Motion.spatial()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                if (provider == AiProvider.Custom) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Presets.forEach { preset ->
                            PillChip(
                                preset.name,
                                selected = baseUrl.trimEnd('/') == preset.baseUrl,
                                onClick = {
                                    name = preset.name
                                    baseUrl = preset.baseUrl
                                    format = preset.format
                                    dirty()
                                },
                            )
                        }
                    }
                    Field(value = name, onValueChange = { name = it; dirty() }, label = "Name", placeholder = tr("My provider"))
                    Text(tr("Protocol"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SegmentedTabs(
                        items = ApiFormat.entries.toList(),
                        selected = format,
                        onSelect = { format = it; dirty() },
                        label = {
                            when (it) {
                                ApiFormat.OpenAi -> "OpenAI"
                                ApiFormat.Anthropic -> "Anthropic"
                                ApiFormat.Gemini -> "Gemini"
                            }
                        },
                    )
                }
                Field(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; dirty() },
                    label = "Base URL",
                    placeholder = if (provider == AiProvider.Custom) "https://api.example.com/v1" else provider.defaultBaseUrl,
                    supporting = format.endpointHint,
                    keyboardType = KeyboardType.Uri,
                )
                ApiKeyField(
                    value = apiKey,
                    onValueChange = { apiKey = it; dirty() },
                    current = current,
                    optional = !provider.requiresKey,
                    onClear = { onClearKey(provider) },
                )
            }
        }

        ModelSection(
            provider = provider,
            current = current,
            model = model,
            onModel = { model = it; dirty() },
            fetch = fetch.takeIf { it.provider == provider } ?: ModelFetchState(),
            onRefresh = { onRefreshModels(draft) },
        )

        Button(
            onClick = { onSave(draft); apiKey = ""; saved = true },
            enabled = baseUrl.isNotBlank() && (provider != AiProvider.Custom || model.isNotBlank()),
            modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.MinTouch),
            shape = RoundedCornerShape(12.dp),
        ) {
            AnimatedContent(saved, transitionSpec = { fadeIn(Motion.switch()) togetherWith fadeOut(Motion.switch()) }, label = "save") { done ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (done) CodeinIcons.Check else CodeinIcons.Save, null, modifier = Modifier.size(Sizes.Icon))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(if (done) "Saved" else "Save ${if (provider == AiProvider.Custom) name.ifBlank { "custom provider" } else provider.displayName}")
                }
            }
        }

        StudioSectionCard(title = tr("Max output tokens"), icon = CodeinIcons.Layers, subtitle = "${settings.maxOutputTokens} tokens per reply") {
            Slider(
                value = settings.maxOutputTokens.toFloat(),
                onValueChange = { onMaxTokens((it / 1024f).roundToInt() * 1024) },
                valueRange = AiSettingsRepository.MIN_TOKENS.toFloat()..AiSettingsRepository.MAX_TOKENS_LIMIT.toFloat(),
            )
        }

        Row(verticalAlignment = Alignment.Top) {
            Icon(CodeinIcons.Lock, null, tint = CodeinTheme.colors.textMuted, modifier = Modifier.padding(top = 2.dp).size(Sizes.IconSm))
            Spacer(Modifier.width(Spacing.sm))
            Text(tr("Keys are encrypted with an AES-256-GCM key held in the Android Keystore and never leave the device except in requests to the provider you choose."),
                style = MaterialTheme.typography.bodySmall,
                color = CodeinTheme.colors.textMuted,
            )
        }
    }
}

/** 2-column grid of provider cards with a ready / needs-setup status line. */
@Composable
private fun ProviderGrid(settings: AiSettings, selected: AiProvider, onSelect: (AiProvider) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AiProvider.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { p ->
                    ProviderCard(p, settings.providers[p], p == selected, Modifier.weight(1f)) { onSelect(p) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ProviderCard(provider: AiProvider, info: ProviderSettings?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val accent = MaterialTheme.colorScheme.primary
    val ready = info?.isReady == true
    Row(
        modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.12f) else CodeinTheme.colors.tier2)
            .border(1.dp, if (selected) accent.copy(alpha = 0.7f) else CodeinTheme.colors.hairline, shape)
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(if (selected) accent.copy(alpha = 0.18f) else CodeinTheme.colors.tier3),
            contentAlignment = Alignment.Center,
        ) {
            Icon(provider.icon(), null, tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.Icon))
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                info?.label ?: provider.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    ready -> info?.model.orEmpty()
                    provider == AiProvider.Custom && info?.baseUrl.isNullOrBlank() -> "Set up endpoint"
                    else -> "Needs API key"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (ready) CodeinTheme.colors.success else CodeinTheme.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Model field that doubles as a filter, the refresh action and the fetched model list. */
@Composable
private fun ModelSection(
    provider: AiProvider,
    current: ProviderSettings?,
    model: String,
    onModel: (String) -> Unit,
    fetch: ModelFetchState,
    onRefresh: () -> Unit,
) {
    val available = current?.availableModels.orEmpty()
    val filtered = remember(available, model) {
        val q = model.trim()
        if (q.isEmpty() || available.any { it == q }) available else available.filter { it.contains(q, ignoreCase = true) }
    }
    StudioSectionCard(
        title = tr("Model"),
        icon = CodeinIcons.Bot,
        subtitle = when {
            fetch.loading -> "Fetching models…"
            available.isNotEmpty() -> "${available.size} models" + (current?.modelsFetchedAt?.let { " · updated " + relative(it) } ?: "")
            else -> "Refresh to load the models this endpoint offers"
        },
        actions = {
            if (fetch.loading) {
                Box(Modifier.size(Sizes.MinTouch), contentAlignment = Alignment.Center) {
                    SpinningIcon(CodeinIcons.Refresh, MaterialTheme.colorScheme.primary, Modifier.size(Sizes.Icon))
                }
            } else {
                ToolbarIconButton(CodeinIcons.Refresh, tr("Refresh models"), tint = MaterialTheme.colorScheme.primary, onClick = onRefresh)
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Field(
                value = model,
                onValueChange = onModel,
                label = "Model id",
                placeholder = provider.defaultModel.ifBlank { "e.g. llama3.1:8b" },
                leading = CodeinIcons.Search.takeIf { available.isNotEmpty() },
            )
            AnimatedVisibility(fetch.error != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                        .padding(Spacing.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(CodeinIcons.AlertCircle, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(Sizes.IconSm))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(fetch.error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (available.isEmpty()) {
                if (provider.suggestedModels.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        provider.suggestedModels.forEach { m -> PillChip(m, selected = m == model, onClick = { onModel(m) }) }
                    }
                }
                PillChip(tr("Refresh models"), icon = CodeinIcons.Refresh, onClick = onRefresh.takeUnless { fetch.loading })
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, CodeinTheme.colors.hairline, RoundedCornerShape(8.dp))
                        .heightIn(max = 264.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (filtered.isEmpty()) {
                        Text(
                            "No fetched model matches \"${model.trim()}\". It will still be used as typed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CodeinTheme.colors.textMuted,
                            modifier = Modifier.padding(Spacing.md),
                        )
                    }
                    filtered.forEach { id -> ModelRow(id, selected = id == model.trim()) { onModel(id) } }
                }
            }
        }
    }
}

@Composable
private fun ModelRow(id: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else CodeinTheme.colors.tier1)
            .clickable(onClick = onClick)
            .height(Sizes.MinTouch)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            id,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) Icon(CodeinIcons.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Sizes.IconSm))
    }
}

@Composable
private fun ApiKeyField(value: String, onValueChange: (String) -> Unit, current: ProviderSettings?, optional: Boolean, onClear: () -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(if (current?.hasApiKey == true) "Replace API key" else if (optional) "API key (optional)" else "API key") },
            placeholder = { Text(if (optional) "Leave empty for local servers" else "sk-…", color = CodeinTheme.colors.textMuted) },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                ToolbarIconButton(if (visible) CodeinIcons.EyeOff else CodeinIcons.Eye, if (visible) "Hide key" else "Show key") { visible = !visible }
            },
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (current?.hasApiKey == true) CodeinIcons.Lock else CodeinIcons.Unlock,
                null,
                tint = if (current?.hasApiKey == true) CodeinTheme.colors.success else CodeinTheme.colors.textMuted,
                modifier = Modifier.size(Sizes.IconSm),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                if (current?.hasApiKey == true) "Stored key ${current.maskedKey}" else "No key stored",
                style = MaterialTheme.typography.labelMedium,
                color = if (current?.hasApiKey == true) CodeinTheme.colors.success else CodeinTheme.colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            if (current?.hasApiKey == true) PillChip(tr("Remove"), icon = CodeinIcons.Trash, accent = MaterialTheme.colorScheme.error, onClick = onClear)
        }
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    supporting: String? = null,
    leading: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = CodeinTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingText = if (supporting != null) {
            { Text(supporting) }
        } else {
            null
        },
        leadingIcon = if (leading != null) {
            { Icon(leading, null, modifier = Modifier.size(Sizes.Icon)) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedContainerColor = CodeinTheme.colors.tier1,
    unfocusedContainerColor = CodeinTheme.colors.tier1,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedSupportingTextColor = CodeinTheme.colors.textMuted,
    unfocusedSupportingTextColor = CodeinTheme.colors.textMuted,
    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

private fun relative(time: Long): String =
    DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString().lowercase()
