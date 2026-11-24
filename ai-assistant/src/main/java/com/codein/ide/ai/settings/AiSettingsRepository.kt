package com.codein.ide.ai.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.codein.ide.ai.model.AiProvider
import com.codein.ide.ai.model.ApiFormat
import com.codein.ide.ai.model.ProviderConfig
import com.codein.ide.core.ui.di.SECRETS_PREFS
import com.codein.ide.core.ui.prefs.SecureValueCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Per-provider settings as shown in the settings screen. The key is never exposed in plain text here. */
data class ProviderSettings(
    val provider: AiProvider,
    val model: String,
    val baseUrl: String,
    val hasApiKey: Boolean,
    val maskedKey: String,
    /** Protocol in use: fixed for built-in providers, user-chosen for [AiProvider.Custom]. */
    val format: ApiFormat = provider.format,
    /** Display name: the user's own name for a custom endpoint. */
    val label: String = provider.displayName,
    /** Model ids last fetched from the endpoint with "Refresh models". */
    val availableModels: List<String> = emptyList(),
    val modelsFetchedAt: Long? = null,
) {
    /** True when a request can be sent: credentials (if required), a base URL and a model. */
    val isReady: Boolean
        get() = (hasApiKey || !provider.requiresKey) && model.isNotBlank() && baseUrl.isNotBlank()
}

data class AiSettings(
    val activeProvider: AiProvider = AiProvider.Anthropic,
    val providers: Map<AiProvider, ProviderSettings> = emptyMap(),
    val maxOutputTokens: Int = ProviderConfig.DEFAULT_MAX_OUTPUT_TOKENS,
) {
    val active: ProviderSettings? get() = providers[activeProvider]
}

/**
 * Stores provider selection, model, base URL and the API key per provider. API keys are encrypted
 * with [SecureValueCipher] (Android Keystore AES-GCM) before they touch DataStore.
 */
@Singleton
class AiSettingsRepository @Inject constructor(
    @Named(SECRETS_PREFS) private val store: DataStore<Preferences>,
    private val cipher: SecureValueCipher,
) {

    val settings: Flow<AiSettings> = store.data.map { prefs ->
        val providers = AiProvider.entries.associateWith { p ->
            val encrypted = prefs[keyKey(p)].orEmpty()
            val plain = if (encrypted.isEmpty()) "" else cipher.decrypt(encrypted)
            ProviderSettings(
                provider = p,
                model = prefs[modelKey(p)]?.takeIf { it.isNotBlank() } ?: p.defaultModel,
                baseUrl = prefs[baseUrlKey(p)]?.takeIf { it.isNotBlank() } ?: p.defaultBaseUrl,
                hasApiKey = plain.isNotEmpty(),
                maskedKey = mask(plain),
                format = formatOf(prefs, p),
                label = labelOf(prefs, p),
                availableModels = prefs[modelsKey(p)]?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
                modelsFetchedAt = prefs[modelsAtKey(p)],
            )
        }
        AiSettings(
            activeProvider = AiProvider.byName(prefs[ACTIVE]),
            providers = providers,
            maxOutputTokens = prefs[MAX_TOKENS] ?: ProviderConfig.DEFAULT_MAX_OUTPUT_TOKENS,
        )
    }.flowOn(Dispatchers.IO)

    /** Resolves the active provider's configuration including the decrypted key. */
    suspend fun activeConfig(): ProviderConfig = configFor(AiProvider.byName(store.data.first()[ACTIVE]))

    /**
     * Resolves [provider]'s stored configuration with the decrypted key. The overrides let the
     * settings screen fetch models with values the user has typed but not saved yet.
     */
    suspend fun configFor(
        provider: AiProvider,
        apiKeyOverride: String? = null,
        baseUrlOverride: String? = null,
        formatOverride: ApiFormat? = null,
    ): ProviderConfig {
        val prefs = store.data.first()
        val encrypted = prefs[keyKey(provider)].orEmpty()
        val storedKey = if (encrypted.isEmpty()) "" else cipher.decrypt(encrypted)
        return ProviderConfig(
            provider = provider,
            apiKey = apiKeyOverride?.trim()?.takeIf { it.isNotEmpty() } ?: storedKey,
            model = prefs[modelKey(provider)]?.takeIf { it.isNotBlank() } ?: provider.defaultModel,
            baseUrl = baseUrlOverride?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
                ?: prefs[baseUrlKey(provider)]?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl,
            maxOutputTokens = prefs[MAX_TOKENS] ?: ProviderConfig.DEFAULT_MAX_OUTPUT_TOKENS,
            format = formatOverride ?: formatOf(prefs, provider),
            label = labelOf(prefs, provider),
        )
    }

    /** Name and protocol of the user's custom endpoint. */
    suspend fun setCustomProvider(name: String, format: ApiFormat) {
        store.edit {
            it[CUSTOM_NAME] = name.trim()
            it[CUSTOM_FORMAT] = format.name
        }
    }

    /** Caches the model ids fetched from [provider]'s endpoint. */
    suspend fun setAvailableModels(provider: AiProvider, models: List<String>) {
        store.edit {
            it[modelsKey(provider)] = models.joinToString("\n")
            it[modelsAtKey(provider)] = System.currentTimeMillis()
        }
    }

    suspend fun setActiveProvider(provider: AiProvider) {
        store.edit { it[ACTIVE] = provider.name }
    }

    suspend fun setModel(provider: AiProvider, model: String) {
        store.edit { it[modelKey(provider)] = model.trim() }
    }

    suspend fun setBaseUrl(provider: AiProvider, baseUrl: String) {
        store.edit { it[baseUrlKey(provider)] = baseUrl.trim().trimEnd('/') }
    }

    suspend fun setApiKey(provider: AiProvider, apiKey: String) {
        val encrypted = cipher.encrypt(apiKey.trim())
        store.edit { it[keyKey(provider)] = encrypted }
    }

    suspend fun clearApiKey(provider: AiProvider) {
        store.edit { it.remove(keyKey(provider)) }
    }

    suspend fun setMaxOutputTokens(tokens: Int) {
        store.edit { it[MAX_TOKENS] = tokens.coerceIn(MIN_TOKENS, MAX_TOKENS_LIMIT) }
    }

    private fun formatOf(prefs: Preferences, p: AiProvider): ApiFormat =
        if (p == AiProvider.Custom) prefs[CUSTOM_FORMAT]?.let { ApiFormat.byName(it) } ?: p.format else p.format

    private fun labelOf(prefs: Preferences, p: AiProvider): String =
        if (p == AiProvider.Custom) prefs[CUSTOM_NAME]?.takeIf { it.isNotBlank() } ?: "Custom provider" else p.displayName

    private fun mask(key: String): String = when {
        key.isEmpty() -> ""
        key.length <= 8 -> "•".repeat(key.length)
        else -> key.take(4) + "•".repeat(8) + key.takeLast(4)
    }

    companion object {
        const val MIN_TOKENS = 1024
        const val MAX_TOKENS_LIMIT = 64000
        private val ACTIVE = stringPreferencesKey("ai_active_provider")
        private val MAX_TOKENS = intPreferencesKey("ai_max_output_tokens")
        private val CUSTOM_NAME = stringPreferencesKey("ai_custom_name")
        private val CUSTOM_FORMAT = stringPreferencesKey("ai_custom_format")
        private fun modelsKey(p: AiProvider) = stringPreferencesKey("ai_${p.name}_models")
        private fun modelsAtKey(p: AiProvider) = longPreferencesKey("ai_${p.name}_models_at")
        private fun keyKey(p: AiProvider) = stringPreferencesKey("ai_${p.name}_key")
        private fun modelKey(p: AiProvider) = stringPreferencesKey("ai_${p.name}_model")
        private fun baseUrlKey(p: AiProvider) = stringPreferencesKey("ai_${p.name}_base_url")
    }
}
