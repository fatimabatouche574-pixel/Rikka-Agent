package me.rerere.ai.registry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelIdNormalizerTest {
    @Test
    fun `canonicalize - strips provider namespace and normalizes casing`() {
        assertEquals("gpt-4o", ModelIdNormalizer.canonicalize("openai/gpt-4o"))
        assertEquals("claude-3.5-sonnet", ModelIdNormalizer.canonicalize("Claude-3.5-Sonnet"))
    }

    @Test
    fun `canonicalize - strips removable suffixes`() {
        assertEquals("gemini-2.5-pro", ModelIdNormalizer.canonicalize("gemini-2.5-pro-preview"))
        assertEquals("deepseek-r1", ModelIdNormalizer.canonicalize("deepseek-r1-free"))
        assertEquals("some-model", ModelIdNormalizer.canonicalize("some-model-latest"))
    }

    @Test
    fun `canonicalize - removes trailing dates and version tokens`() {
        assertEquals("gemini-2.5-flash", ModelIdNormalizer.canonicalize("gemini-2.5-flash-preview-04-17"))
        assertEquals("llama-3.1-8b", ModelIdNormalizer.canonicalize("llama-3.1-8b-v2"))
    }

    @Test
    fun `canonicalize - normalizes version tokens and separators`() {
        assertEquals("claude-3.5", ModelIdNormalizer.canonicalize("claude-v3p5"))
        assertEquals("gpt-4.1", ModelIdNormalizer.canonicalize("gpt-4_1"))
    }

    @Test
    fun `canonicalize - canonical hint wins over model id`() {
        assertEquals("gemini-2.5-flash-image", ModelIdNormalizer.canonicalize("models/nano-banana", "gemini-2.5-flash-image"))
    }

    @Test
    fun `canonicalize - blank input returns empty`() {
        assertEquals("", ModelIdNormalizer.canonicalize("   "))
        assertEquals("", ModelIdNormalizer.canonicalize(""))
    }

    @Test
    fun `canonicalize - round trip is deterministic`() {
        val ids = listOf(
            "openai/gpt-4o",
            "gemini-2.5-pro-preview",
            "claude-3.5-sonnet",
            "meta-llama/Llama-4-70B-Instruct",
            "Qwen/Qwen2.5-VL-7B-Instruct",
        )
        ids.forEach { id ->
            val once = ModelIdNormalizer.canonicalize(id)
            val twice = ModelIdNormalizer.canonicalize(id)
            assertEquals(once, twice)
        }
    }

    @Test
    fun `preprocess - keeps removable suffix tokens`() {
        assertTrue(ModelIdNormalizer.preprocess("gemini-2.5-pro-preview").contains("preview"))
        assertTrue(ModelIdNormalizer.preprocess("deepseek-r1-free").contains("free"))
    }

    @Test
    fun `extractStrippedTokens - detects stripped suffixes`() {
        val tokens = ModelIdNormalizer.extractStrippedTokens("gemini-2.5-pro-preview")
        assertTrue("preview" in tokens)
        assertEquals(emptyList<String>(), ModelIdNormalizer.extractStrippedTokens("gemini-2.5-pro"))
    }

    @Test
    fun `extractStrippedTokens - date suffix produces tokens`() {
        val tokens = ModelIdNormalizer.extractStrippedTokens("gemini-2.5-flash-preview-04-17")
        assertTrue(tokens.isNotEmpty())
    }
}
