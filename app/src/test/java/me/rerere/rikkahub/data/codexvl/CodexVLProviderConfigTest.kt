package me.rerere.rikkahub.data.codexvl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexVLProviderConfigTest {
    @Test
    fun `preset provider leaves only the api key empty`() {
        val config = CodexVLProviderConfig()
        val state = CodexVLConfigStore.State()

        assertEquals("https://sharedchat.top/codex", config.baseUrl)
        assertEquals("gpt-5.6-sol", config.model)
        assertFalse(config.enabled)
        assertEquals("", state.apiKey)
    }

    @Test
    fun `generates a custom responses provider without embedding api key`() {
        val secret = "sk-never-write-this"
        val toml = CodexVLProviderConfig(
            baseUrl = "https://provider.example/codex/",
            model = "future-model-arbitrary-string",
        ).toCodexToml()

        assertTrue(toml.contains("base_url = \"https://provider.example/codex\""))
        assertTrue(toml.contains("wire_api = \"responses\""))
        assertTrue(toml.contains("env_key = \"RIKKA_CODEX_API_KEY\""))
        assertTrue(toml.contains("requires_openai_auth = false"))
        assertTrue(toml.contains("supports_websockets = false"))
        assertTrue(toml.contains("x-openai-actor-authorization"))
        assertTrue(toml.contains("codex-compatible-image-generation"))
        assertTrue(toml.contains("model = \"future-model-arbitrary-string\""))
        assertFalse(toml.contains(secret))
    }

    @Test
    fun `model remains an unrestricted string`() {
        val config = CodexVLProviderConfig(baseUrl = "https://provider.example/v1", model = "vendor/new-model-2030")
        assertEquals(CodexVLProviderConfig.Validation.VALID, config.validate())
    }

    @Test
    fun `does not invent v1 or responses in provider base url`() {
        val toml = CodexVLProviderConfig(baseUrl = "https://provider.example/codex", model = "m").toCodexToml()
        assertTrue(toml.contains("base_url = \"https://provider.example/codex\""))
        assertFalse(toml.contains("codex/v1"))
        assertFalse(toml.contains("codex/responses"))
    }
}
