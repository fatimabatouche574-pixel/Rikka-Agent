package me.rerere.ai.registry

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelDisplayNameGeneratorTest {
    @Test
    fun `single model - basic brand formatting`() {
        assertEquals("Gemini 2.5 Pro", ModelDisplayNameGenerator.generate("gemini-2.5-pro"))
        assertEquals("Claude 3.5 Sonnet", ModelDisplayNameGenerator.generate("claude-3.5-sonnet"))
        assertEquals("GPT-4o", ModelDisplayNameGenerator.generate("gpt-4o"))
        assertEquals("DeepSeek R1", ModelDisplayNameGenerator.generate("deepseek-r1"))
        assertEquals("Llama 3.1 70B", ModelDisplayNameGenerator.generate("llama-3.1-70b"))
    }

    @Test
    fun `single model - preview stripped`() {
        assertEquals("Gemini 2.5 Pro", ModelDisplayNameGenerator.generate("gemini-2.5-pro-preview"))
    }

    @Test
    fun `batch - no collisions stays unchanged`() {
        val names = ModelDisplayNameGenerator.generateBatch(
            listOf(
                "gemini-2.5-pro" to null,
                "claude-3.5-sonnet" to null,
                "gpt-4o" to null,
            )
        )
        assertEquals("Gemini 2.5 Pro", names[0])
        assertEquals("Claude 3.5 Sonnet", names[1])
        assertEquals("GPT-4o", names[2])
    }

    @Test
    fun `batch - preview vs base model`() {
        val names = ModelDisplayNameGenerator.generateBatch(
            listOf(
                "gemini-2.5-pro" to null,
                "gemini-2.5-pro-preview" to null,
            )
        )
        assertEquals("Gemini 2.5 Pro", names[0])
        assertEquals("Gemini 2.5 Pro Preview", names[1])
    }

    @Test
    fun `batch - free vs base model`() {
        val names = ModelDisplayNameGenerator.generateBatch(
            listOf(
                "deepseek-r1" to null,
                "deepseek-r1-free" to null,
            )
        )
        assertEquals("DeepSeek R1", names[0])
        assertEquals("DeepSeek R1 Free", names[1])
    }

    @Test
    fun `batch - beta and preview both present`() {
        val names = ModelDisplayNameGenerator.generateBatch(
            listOf(
                "some-model" to null,
                "some-model-preview" to null,
                "some-model-beta" to null,
            )
        )
        assertEquals("Some Model", names[0])
        assertEquals("Some Model Preview", names[1])
        assertEquals("Some Model Beta", names[2])
    }

    @Test
    fun `batch - parameter sizes already differ`() {
        val names = ModelDisplayNameGenerator.generateBatch(
            listOf(
                "llama-3.1-8b" to null,
                "llama-3.1-70b" to null,
            )
        )
        assertEquals("Llama 3.1 8B", names[0])
        assertEquals("Llama 3.1 70B", names[1])
    }

    @Test
    fun `batch - mixed collection with collisions`() {
        val names = ModelDisplayNameGenerator.generateBatch(
            listOf(
                "gpt-4o" to null,
                "gpt-4o-mini" to null,
                "gemini-2.5-pro" to null,
                "gemini-2.5-pro-preview" to null,
                "claude-3.5-sonnet" to null,
            )
        )
        assertEquals("GPT-4o", names[0])
        assertEquals("GPT-4o Mini", names[1])
        assertEquals("Gemini 2.5 Pro", names[2])
        assertEquals("Gemini 2.5 Pro Preview", names[3])
        assertEquals("Claude 3.5 Sonnet", names[4])
    }

    @Test
    fun `batch - latest suffix disambiguation`() {
        val names = ModelDisplayNameGenerator.generateBatch(
            listOf(
                "some-model" to null,
                "some-model-latest" to null,
            )
        )
        assertEquals("Some Model", names[0])
        assertEquals("Some Model Latest", names[1])
    }

    @Test
    fun `batch - empty input`() {
        assertEquals(emptyList<String>(), ModelDisplayNameGenerator.generateBatch(emptyList()))
    }

    @Test
    fun `batch - single model passes through`() {
        val names = ModelDisplayNameGenerator.generateBatch(listOf("gemini-2.5-pro-preview" to null))
        assertEquals("Gemini 2.5 Pro", names[0])
    }

    @Test
    fun `batch - provider namespace stripped before comparison`() {
        val names = ModelDisplayNameGenerator.generateBatch(
            listOf(
                "openai/gpt-4o" to null,
                "gpt-4o-mini" to null,
            )
        )
        assertEquals("GPT-4o", names[0])
        assertEquals("GPT-4o Mini", names[1])
    }

    @Test
    fun `batch - duplicate display names resolve deterministically`() {
        val names = ModelDisplayNameGenerator.generateBatch(
            listOf(
                "gemini-2.5-pro" to null,
                "gemini-2.5-pro" to null,
            )
        )
        assertEquals(names[0], names[1])
    }

    @Test
    fun `generate - unknown brand falls back to title case`() {
        assertEquals("Qwen 3 8B", ModelDisplayNameGenerator.generate("qwen-3-8b"))
        assertEquals("Some Model", ModelDisplayNameGenerator.generate("some-model"))
    }
}
