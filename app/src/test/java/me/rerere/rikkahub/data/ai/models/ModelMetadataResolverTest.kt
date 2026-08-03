package me.rerere.rikkahub.data.ai.models

import kotlin.uuid.Uuid
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelMetadataResolverTest {
    private fun resolverFor(rawJson: String): ModelMetadataResolver {
        val snapshot = ModelCatalogParser.parse(rawJson)
        return ModelMetadataResolver(snapshotProvider = { snapshot })
    }

    @Test
    fun `resolves exact model capabilities from the models array`() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 2,
              "models": [{
                "id": "gpt-5-mini",
                "canonical_model_id": "gpt-5-mini",
                "type": "chat",
                "input_modalities": ["text", "image"],
                "output_modalities": ["text"],
                "abilities": ["tool", "reasoning"]
              }]
            }
            """.trimIndent()
        )

        val resolved = resolver.applyToModel(Model(modelId = "gpt-5-mini"))

        assertEquals(ModelType.CHAT, resolved.type)
        assertEquals(listOf(Modality.TEXT, Modality.IMAGE), resolved.inputModalities)
        assertEquals(listOf(Modality.TEXT), resolved.outputModalities)
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), resolved.abilities)
    }

    @Test
    fun `api aliases resolve to a single canonical entry`() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 2,
              "models": [{
                "id": "gpt-5",
                "canonical_model_id": "gpt-5",
                "api_aliases": ["gpt5", "openai/gpt-5"],
                "type": "chat",
                "abilities": ["tool", "reasoning"]
              }]
            }
            """.trimIndent()
        )

        val viaAlias = resolver.applyToModel(Model(modelId = "gpt5"))
        val viaFull = resolver.applyToModel(Model(modelId = "openai/gpt-5"))

        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), viaAlias.abilities)
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), viaFull.abilities)
    }

    @Test
    fun `layered order - global rules infer capabilities without a family`() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 2,
              "global_rules": [
                { "id": "vision", "match_patterns": ["vision", "(^|[/._-])vl($|[/._-])"], "input_modalities": ["text", "image"] },
                { "id": "thinking", "match_patterns": ["thinking"], "abilities": ["tool", "reasoning"] }
              ],
              "models": []
            }
            """.trimIndent()
        )

        val resolved = resolver.applyToModel(Model(modelId = "acme-thinking-vl"))

        assertEquals(listOf(Modality.TEXT, Modality.IMAGE), resolved.inputModalities)
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), resolved.abilities)
    }

    @Test
    fun `layered order - family versions refine base capabilities`() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 2,
              "model_families": [{
                "id": "qwen",
                "match_patterns": ["qwen"],
                "input_modalities": ["text"],
                "output_modalities": ["text"],
                "abilities": ["tool"],
                "versions": [
                  { "id": "qwen3", "match_patterns": ["qwen3"], "abilities": ["tool", "reasoning"] },
                  { "id": "qwen-vl", "match_patterns": ["vl"], "input_modalities": ["text", "image"] }
                ]
              }],
              "models": []
            }
            """.trimIndent()
        )

        val qwen3 = resolver.applyToModel(Model(modelId = "qwen3-max"))
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), qwen3.abilities)

        val vl = resolver.applyToModel(Model(modelId = "Qwen/Qwen-VL-Max"))
        assertEquals(listOf(Modality.TEXT, Modality.IMAGE), vl.inputModalities)
        assertEquals(listOf(ModelAbility.TOOL), vl.abilities)
    }

    @Test
    fun `layered order - override wins after family and version inference`() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 2,
              "model_families": [{
                "id": "qwen",
                "match_patterns": ["qwen"],
                "abilities": ["tool", "reasoning"]
              }],
              "model_overrides": [{
                "id": "qwen3-embed",
                "type": "embedding",
                "input_modalities": ["text"],
                "output_modalities": ["text"],
                "abilities": []
              }],
              "models": []
            }
            """.trimIndent()
        )

        val resolved = resolver.applyToModel(Model(modelId = "qwen3-embed"))

        assertEquals(ModelType.EMBEDDING, resolved.type)
        assertEquals(emptyList<ModelAbility>(), resolved.abilities)
        assertEquals(listOf(Modality.TEXT), resolved.inputModalities)
    }

    @Test
    fun `exclusion patterns veto a rule match`() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 2,
              "global_rules": [
                { "id": "embedding", "match_patterns": ["embedding"], "exclude_patterns": ["gemini"], "type": "embedding" }
              ],
              "model_families": [{
                "id": "gemini",
                "match_patterns": ["gemini"],
                "abilities": ["tool"]
              }],
              "models": []
            }
            """.trimIndent()
        )

        // "gemini-embedding-001" is excluded from the embedding rule → stays CHAT, family tool applies
        val resolved = resolver.applyToModel(Model(modelId = "gemini-embedding-001"))

        assertEquals(ModelType.CHAT, resolved.type)
        assertEquals(listOf(ModelAbility.TOOL), resolved.abilities)
    }

    @Test
    fun `override gates on provider ids`() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 2,
              "model_families": [{
                "id": "foo",
                "match_patterns": ["foo-model"],
                "abilities": ["tool"]
              }],
              "model_overrides": [{
                "id": "foo-model",
                "provider_ids": ["d5734028-d39b-4d41-9841-fd648d65440e"],
                "abilities": ["tool", "reasoning"]
              }],
              "models": []
            }
            """.trimIndent()
        )

        val generic = resolver.applyToModel(Model(modelId = "foo-model"))
        assertEquals(listOf(ModelAbility.TOOL), generic.abilities)

        val openRouter = resolver.applyToModel(
            model = Model(modelId = "foo-model"),
            providerHint = ProviderSetting.OpenAI(
                id = Uuid.parse("d5734028-d39b-4d41-9841-fd648d65440e"),
                baseUrl = "https://openrouter.ai/api/v1",
            ),
        )
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), openRouter.abilities)
    }

    @Test
    fun `override gates on provider slugs`() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 2,
              "model_families": [{
                "id": "foo",
                "match_patterns": ["foo-model"],
                "abilities": ["tool"]
              }],
              "model_overrides": [{
                "id": "foo-model",
                "provider_slugs": ["openrouter"],
                "abilities": ["tool", "reasoning"]
              }],
              "models": []
            }
            """.trimIndent()
        )

        val openRouter = resolver.applyToModel(
            model = Model(modelId = "foo-model"),
            providerHint = ProviderSetting.OpenAI(baseUrl = "https://openrouter.ai/api/v1"),
        )
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), openRouter.abilities)
    }

    @Test
    fun `override gates on base url patterns`() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 2,
              "model_families": [{
                "id": "foo",
                "match_patterns": ["foo-model"],
                "abilities": ["tool"]
              }],
              "model_overrides": [{
                "id": "foo-model",
                "base_url_patterns": ["openrouter"],
                "abilities": ["tool", "reasoning"]
              }],
              "models": []
            }
            """.trimIndent()
        )

        val openRouter = resolver.applyToModel(
            model = Model(modelId = "foo-model"),
            providerHint = ProviderSetting.OpenAI(baseUrl = "https://openrouter.ai/api/v1"),
        )
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), openRouter.abilities)

        val elsewhere = resolver.applyToModel(
            model = Model(modelId = "foo-model"),
            providerHint = ProviderSetting.OpenAI(baseUrl = "https://other.example.com/v1"),
        )
        assertEquals(listOf(ModelAbility.TOOL), elsewhere.abilities)
    }

    @Test
    fun `preserve flags keep user corrected display name and added abilities`() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 2,
              "model_families": [{
                "id": "gemini",
                "match_patterns": ["gemini"],
                "input_modalities": ["text", "image"],
                "abilities": ["tool"]
              }],
              "models": []
            }
            """.trimIndent()
        )
        val userEdited = Model(
            modelId = "gemini-3-pro",
            displayName = "My Gemini",
            type = ModelType.CHAT,
            inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
            outputModalities = listOf(Modality.TEXT),
            abilities = listOf(ModelAbility.TOOL, ModelAbility.REASONING),
        )

        val resolved = resolver.applyToModel(
            model = userEdited,
            options = ModelResolutionOptions(
                preserveDisplayName = true,
                preserveExistingCapabilities = true,
                preserveExistingType = true,
            ),
        )

        assertEquals("My Gemini", resolved.displayName)
        // User's REASONING survives even though the catalog family only lists tool
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), resolved.abilities)
    }

    @Test
    fun `without preserve flags catalog capabilities replace user defaults`() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 2,
              "model_families": [{
                "id": "gemini",
                "match_patterns": ["gemini"],
                "input_modalities": ["text", "image"],
                "abilities": ["tool"]
              }],
              "models": []
            }
            """.trimIndent()
        )

        val resolved = resolver.applyToModel(Model(modelId = "gemini-3-pro"))

        assertEquals(listOf(ModelAbility.TOOL), resolved.abilities)
        assertEquals(listOf(Modality.TEXT, Modality.IMAGE), resolved.inputModalities)
    }

    @Test
    fun `stt and audio map to safe chat text defaults`() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 2,
              "model_families": [{
                "id": "stt",
                "match_patterns": ["whisper"],
                "type": "stt",
                "input_modalities": ["audio"],
                "output_modalities": ["text"]
              }],
              "models": []
            }
            """.trimIndent()
        )

        val resolved = resolver.applyToModel(Model(modelId = "whisper-1"))

        assertEquals(ModelType.CHAT, resolved.type)
        assertEquals(listOf(Modality.TEXT), resolved.inputModalities)
        assertEquals(listOf(Modality.TEXT), resolved.outputModalities)
    }

    @Test
    fun `null snapshot is a no-op passthrough`() {
        val resolver = ModelMetadataResolver(snapshotProvider = { null })
        val model = Model(
            modelId = "my-model",
            type = ModelType.IMAGE,
            inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
            outputModalities = listOf(Modality.IMAGE),
            abilities = listOf(ModelAbility.TOOL),
        )

        assertEquals(model, resolver.applyToModel(model))
        assertEquals(
            model,
            resolver.applyToModel(
                model,
                options = ModelResolutionOptions(preserveExistingCapabilities = true),
            ),
        )
    }

    @Test
    fun `unknown model resolves to safe chat defaults`() {
        val resolver = resolverFor(
            """{"schema_version": 2, "providers": [], "model_families": [], "models": []}"""
        )

        val resolved = resolver.applyToModel(Model(modelId = "totally-unknown-model-xyz"))

        assertEquals(ModelType.CHAT, resolved.type)
        assertEquals(listOf(Modality.TEXT), resolved.inputModalities)
        assertEquals(listOf(Modality.TEXT), resolved.outputModalities)
        assertEquals(emptyList<ModelAbility>(), resolved.abilities)
    }

    @Test
    fun `estimate cost usd returns cost when catalog has pricing and null otherwise`() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 2,
              "models": [{
                "id": "gpt-5-mini",
                "type": "chat",
                "input_cost_per_token": 0.0000005,
                "output_cost_per_token": 0.000002
              }]
            }
            """.trimIndent()
        )

        val cost = resolver.estimateCostUsd(
            model = Model(modelId = "gpt-5-mini"),
            promptTokens = 1000,
            completionTokens = 500,
        )
        assertEquals(1000 * 0.0000005 + 500 * 0.000002, cost!!, 1e-12)

        assertNull(resolver.estimateCostUsd(Model(modelId = "unknown-model-xyz"), 1000, 500))
    }

    @Test
    fun `applyToProvider resolves all models and preserves display names by default`() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 2,
              "model_families": [{
                "id": "gemini",
                "match_patterns": ["gemini"],
                "input_modalities": ["text", "image"],
                "abilities": ["tool", "reasoning"]
              }],
              "models": []
            }
            """.trimIndent()
        )
        val provider = ProviderSetting.OpenAI(
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            models = listOf(
                Model(modelId = "models/gemini-3-pro-preview", displayName = "My Gemini"),
                Model(modelId = "gemini-2.5-flash"),
            ),
        )

        val resolved = resolver.applyToProvider(provider)

        assertEquals("My Gemini", resolved.models[0].displayName)
        assertEquals(listOf(Modality.TEXT, Modality.IMAGE), resolved.models[0].inputModalities)
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), resolved.models[0].abilities)
        // Batch disambiguation keeps the two gemini display names distinct
        assertEquals(2, resolved.models.map { it.displayName }.distinct().size)
    }

    @Test
    fun `hasCatalogEntry is true only when a catalog entry matches`() {
        val resolver = resolverFor(
            """
            {
              "schema_version": 2,
              "models": [{ "id": "known-model", "type": "chat", "abilities": ["tool"] }]
            }
            """.trimIndent()
        )

        assertTrue(resolver.hasCatalogEntry(Model(modelId = "known-model")))
        assertTrue(!resolver.hasCatalogEntry(Model(modelId = "unknown-model-xyz")))
    }
}
