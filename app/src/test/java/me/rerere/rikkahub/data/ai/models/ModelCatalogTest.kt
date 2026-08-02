package me.rerere.rikkahub.data.ai.models

import java.io.File
import java.util.Locale
import kotlin.uuid.Uuid
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    private val bundledCatalogFile: File = File("src/main/assets/catalog/lastchat_catalog.json")

    @Test
    fun `parse bundled asset exposes 60+ providers`() {
        assertTrue("Catalog file should exist at ${bundledCatalogFile.absolutePath}", bundledCatalogFile.exists())
        val snapshot = ModelCatalogParser.parse(bundledCatalogFile.readText())
        assertNotNull(snapshot)
        assertTrue("Should parse 60+ providers, got ${snapshot.providers.size}", snapshot.providers.size >= 60)
        assertTrue("Should parse model families, got ${snapshot.modelFamilies.size}", snapshot.modelFamilies.isNotEmpty())
    }

    @Test
    fun `parse bundled asset resolves setup models through families`() {
        assertTrue(bundledCatalogFile.exists())
        val snapshot = ModelCatalogParser.parse(bundledCatalogFile.readText())
        val missingRefs = snapshot.providers.flatMap { provider ->
            val setupRefs = provider.setupModels + listOfNotNull(
                provider.setupDefaults?.chat,
                provider.setupDefaults?.title,
                provider.setupDefaults?.summarizer,
                provider.setupDefaults?.ocr,
            )
            setupRefs
                .distinct()
                .filter { modelId -> snapshot.resolveModelEntry(modelId) == null }
                .map { modelId -> "${provider.name}: $modelId" }
        }
        assertTrue("Setup model refs should resolve: $missingRefs", missingRefs.isEmpty())
    }

    @Test
    fun `parse - alias map construction and ambiguous canonical id removal`() {
        val raw = """
            {
              "schema_version": 2,
              "providers": [],
              "model_overrides": [
                { "id": "gpt-5", "canonical_model_id": "gpt-5", "api_aliases": ["gpt5"], "type": "chat" },
                { "id": "gpt-5-pro", "canonical_model_id": "gpt-5", "api_aliases": [], "type": "chat" }
              ]
            }
        """.trimIndent()
        val snapshot = ModelCatalogParser.parse(raw)
        // Ambiguous canonical id "gpt-5" must be removed from exactEntries
        assertTrue(snapshot.exactEntries["gpt-5"] == null)
        // But aliases and individual keys remain
        assertNotNull(snapshot.exactEntries["gpt5"])
        assertNotNull(snapshot.exactEntries["gpt-5-pro"])
        // canonicalEntries keeps both candidates
        assertEquals(2, snapshot.canonicalEntries["gpt-5"]?.size)
    }

    @Test
    fun `parse - models array folds into overrides`() {
        val raw = """
            {
              "schema_version": 2,
              "providers": [],
              "models": [
                { "id": "some-model", "type": "chat", "input_modalities": ["text", "image"], "abilities": ["tool"] }
              ]
            }
        """.trimIndent()
        val snapshot = ModelCatalogParser.parse(raw)
        val entry = snapshot.exactEntries["some-model"]
        assertNotNull(entry)
        assertTrue(entry!!.supportsVision)
        assertTrue(entry.supportsFunctionCalling)
        assertTrue(snapshot.modelOverrides.any { it.id == "some-model" })
    }

    @Test
    fun `parse - empty and malformed json return empty snapshot without throwing`() {
        val empty = ModelCatalogParser.parse("")
        assertNotNull(empty)
        assertEquals(0, empty.providers.size)

        val malformed = ModelCatalogParser.parse("{ not valid json !!")
        assertNotNull(malformed)
        assertEquals(0, malformed.providers.size)
        assertEquals(0, malformed.exactEntries.size)

        val wrongShape = ModelCatalogParser.parse("""
            {"schema_version": 2, "providers": [{"id": 1}]}
        """.trimIndent())
        assertNotNull(wrongShape)
        assertEquals(0, wrongShape.providers.size)
    }

    @Test
    fun `parse - corrupt input resilience never throws`() {
        val raw = (0..200).joinToString(separator = "") { "x$it," }
        val snapshot = runCatching { ModelCatalogParser.parse(raw) }.getOrNull()
        assertNotNull(snapshot)
    }

    @Test
    fun `parse - family fallback to legacy model_groups`() {
        val raw = """
            {
              "schema_version": 2,
              "providers": [],
              "model_groups": [
                { "id": "legacy-group", "match_patterns": ["legacy"], "type": "chat" }
              ]
            }
        """.trimIndent()
        val snapshot = ModelCatalogParser.parse(raw)
        assertEquals(1, snapshot.modelFamilies.size)
        assertEquals("legacy-group", snapshot.modelFamilies[0].id)
        assertNotNull(snapshot.resolveModelEntry("legacy-model"))
    }

    @Test
    fun `parse - unknown model falls through as null`() {
        val snapshot = ModelCatalogParser.parse(
            """{"schema_version": 2, "providers": [], "model_families": []}"""
        )
        assertEquals(null, snapshot.resolveModelEntry("totally-unknown-model-id-xyz"))
    }

    @Test
    fun `parse - stt type resolves as chat safe default`() {
        val raw = """
            {
              "schema_version": 2,
              "providers": [],
              "model_families": [
                { "id": "stt", "match_patterns": ["whisper"], "type": "stt", "input_modalities": ["audio"], "output_modalities": ["text"] }
              ]
            }
        """.trimIndent()
        val snapshot = ModelCatalogParser.parse(raw)
        val entry = snapshot.resolveModelEntry("whisper-1")
        assertNotNull(entry)
        // STT is skipped → CHAT default
        assertEquals("chat", entry!!.mode)
    }

    @Test
    fun `toProviderSetting - non-uuid id returns null`() {
        val provider = CatalogProvider(
            id = "not-a-uuid",
            name = "Nope",
            baseUrl = "https://example.com",
        )
        assertNull(provider.toProviderSetting(apiKey = "k", models = emptyList()))
    }

    @Test
    fun `toProviderSetting - builds disabled OpenAI preset with seeded fields`() {
        val provider = CatalogProvider(
            id = "d5734028-d39b-4d41-9841-fd648d65440e",
            name = "OpenRouter",
            description = "Access many hosted models",
            baseUrl = "https://openrouter.ai/api/v1",
            chatCompletionsPath = "/chat/completions",
            useResponseApi = true,
            balanceOption = me.rerere.ai.provider.BalanceOption(
                enabled = true,
                apiPath = "/credits",
                resultPath = "data.total_credits",
            ),
            setupModels = listOf("gemini-2.5-flash"),
        )
        val seededModel = me.rerere.ai.provider.Model(modelId = "gemini-2.5-flash")
        val setting = provider.toProviderSetting(apiKey = "sk-test", models = listOf(seededModel))
        assertNotNull(setting)
        assertTrue(setting is ProviderSetting.OpenAI)
        val openAi = setting as ProviderSetting.OpenAI
        assertEquals(false, openAi.enabled)
        assertEquals("OpenRouter", openAi.name)
        assertEquals("https://openrouter.ai/api/v1", openAi.baseUrl)
        assertEquals("/chat/completions", openAi.chatCompletionsPath)
        assertEquals(true, openAi.useResponseApi)
        assertEquals(true, openAi.balanceOption.enabled)
        assertEquals("sk-test", openAi.apiKey)
        assertEquals(1, openAi.models.size)
        assertEquals("gemini-2.5-flash", openAi.models[0].modelId)
        assertEquals(Uuid.parse("d5734028-d39b-4d41-9841-fd648d65440e"), openAi.id)
    }

    @Test
    fun `toProviderSetting - builds Google and Claude subclasses disabled`() {
        val googleProvider = CatalogProvider(
            id = "11111111-2222-3333-4444-555555555555",
            name = "Google",
            type = CatalogProviderType.GOOGLE,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
        )
        val googleSetting = googleProvider.toProviderSetting(apiKey = "g", models = emptyList())
        assertTrue(googleSetting is ProviderSetting.Google)
        assertEquals(false, googleSetting!!.enabled)

        val claudeProvider = CatalogProvider(
            id = "66666666-7777-8888-9999-000000000000",
            name = "Claude",
            type = CatalogProviderType.CLAUDE,
            baseUrl = "https://api.anthropic.com/v1",
        )
        val claudeSetting = claudeProvider.toProviderSetting(apiKey = "c", models = emptyList())
        assertTrue(claudeSetting is ProviderSetting.Claude)
        assertEquals(false, claudeSetting!!.enabled)
    }

    @Test
    fun `displayName and description fall back to English`() {
        val provider = CatalogProvider(
            id = "d5734028-d39b-4d41-9841-fd648d65440e",
            name = "OpenRouter",
            description = "Access many hosted models",
            baseUrl = "https://openrouter.ai/api/v1",
        )
        assertEquals("OpenRouter", provider.displayName(Locale("zh", "CN")))
        assertEquals("OpenRouter", provider.displayName(Locale.JAPAN))
        assertEquals("Access many hosted models", provider.description(Locale("ar")))
    }

    @Test
    fun `displayName and description resolve i18n maps by locale tag then language`() {
        val provider = CatalogProvider(
            id = "d5734028-d39b-4d41-9841-fd648d65440e",
            name = "OpenRouter",
            description = "Access many hosted models",
            baseUrl = "https://openrouter.ai/api/v1",
            nameI18n = mapOf("zh-CN" to "开放路由", "zh" to "中文路由"),
            descriptionI18n = mapOf("zh-CN" to "中文描述"),
        )
        assertEquals("开放路由", provider.displayName(Locale("zh", "CN")))
        assertEquals("中文路由", provider.displayName(Locale("zh")))
        assertEquals("中文描述", provider.description(Locale("zh", "CN")))
        // Unknown locale falls back to English
        assertEquals("OpenRouter", provider.displayName(Locale("de")))
    }
}
