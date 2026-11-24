package com.codein.ide.ai.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.codein.ide.ai.model.AiProvider
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
)

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
            )
        }
        AiSettings(
            activeProvider = AiProvider.byName(prefs[ACTIVE]),
            providers = providers,
            maxOutputTokens = prefs[MAX_TOKENS] ?: ProviderConfig.DEFAULT_MAX_OUTPUT_TOKENS,
        )
    }.flowOn(Dispatchers.IO)

    /** Resolves the active provider's configuration including the decrypted key. */
    suspend fun activeConfig(): ProviderConfig {
        val prefs = store.data.first()
        val provider = AiProvider.byName(prefs[ACTIVE])
        val encrypted = prefs[keyKey(provider)].orEmpty()
        return ProviderConfig(
            provider = provider,
            apiKey = if (encrypted.isEmpty()) "" else cipher.decrypt(encrypted),
            model = prefs[modelKey(provider)]?.takeIf { it.isNotBlank() } ?: provider.defaultModel,
            baseUrl = prefs[baseUrlKey(provider)]?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl,
            maxOutputTokens = prefs[MAX_TOKENS] ?: ProviderConfig.DEFAULT_MAX_OUTPUT_TOKENS,
        )
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
        private fun keyKey(p: AiProvider) = stringPreferencesKey("ai_${p.name}_key")
        private fun modelKey(p: AiProvider) = stringPreferencesKey("ai_${p.name}_model")
        private fun baseUrlKey(p: AiProvider) = stringPreferencesKey("ai_${p.name}_base_url")
    }
}
