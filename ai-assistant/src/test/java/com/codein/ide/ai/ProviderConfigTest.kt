package com.codein.ide.ai

import com.codein.ide.ai.model.AiProvider
import com.codein.ide.ai.model.ApiFormat
import com.codein.ide.ai.model.ProviderConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderConfigTest {

    @Test
    fun builtInProvidersNeedAKey() {
        val config = ProviderConfig(AiProvider.Anthropic, apiKey = "", model = "claude-opus-5-5", baseUrl = "https://api.anthropic.com")
        assertFalse(config.isUsable)
        assertTrue(config.copy(apiKey = "sk-ant").isUsable)
    }

    @Test
    fun customProviderWorksWithoutKeyForLocalServers() {
        val config = ProviderConfig(AiProvider.Custom, apiKey = "", model = "llama3.1:8b", baseUrl = "http://localhost:11434/v1")
        assertTrue(config.isUsable)
        assertEquals(ApiFormat.OpenAi, config.format)
    }

    @Test
    fun customProviderStillNeedsBaseUrlAndModel() {
        assertFalse(ProviderConfig(AiProvider.Custom, apiKey = "", model = "", baseUrl = "http://localhost:1234/v1").isUsable)
        assertFalse(ProviderConfig(AiProvider.Custom, apiKey = "k", model = "m", baseUrl = "").isUsable)
    }

    @Test
    fun formatNamesRoundTrip() {
        ApiFormat.entries.forEach { assertEquals(it, ApiFormat.byName(it.name)) }
        assertEquals(ApiFormat.OpenAi, ApiFormat.byName("unknown"))
    }
}
