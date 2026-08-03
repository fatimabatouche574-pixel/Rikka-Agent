package me.rerere.rikkahub.data.ai.models

import kotlin.uuid.Uuid
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * US3 — invariants I1–I6 of `contracts/settings-merger-contract.md`.
 *
 * A [RecordingResolver] (fake [ModelMetadataResolver]) observes the `preserve*` flags the
 * merger passes and passes providers through unchanged, so the matching ladder and the
 * no-loss/no-add/no-reorder/no-duplication invariants are tested in isolation. A real
 * snapshot-backed resolver verifies I6 (resolver safety) end-to-end.
 */
private class RecordingResolver(
    private val onApply: (ProviderSetting, ModelResolutionOptions) -> ProviderSetting = { p, _ -> p },
) : ModelMetadataResolver(snapshotProvider = { null }) {
    val calls = mutableListOf<Pair<ProviderSetting, ModelResolutionOptions>>()

    override fun applyToProvider(
        provider: ProviderSetting,
        options: ModelResolutionOptions,
    ): ProviderSetting {
        calls += provider to options
        return onApply(provider, options)
    }
}

private fun uuid(): String = Uuid.random().toString()

private fun preset(
    id: String,
    name: String,
    baseUrl: String,
    type: CatalogProviderType = CatalogProviderType.OPENAI,
    builtIn: Boolean = false,
    preset: Boolean = true,
    setupModels: List<String> = emptyList(),
): CatalogProvider = CatalogProvider(
    id = id,
    name = name,
    baseUrl = baseUrl,
    type = type,
    builtIn = builtIn,
    preset = preset,
    setupModels = setupModels,
)

private fun openAi(
    id: String,
    name: String = "OpenAI",
    baseUrl: String = "https://api.openai.com/v1",
    enabled: Boolean = true,
    apiKey: String = "",
    models: List<Model> = emptyList(),
): ProviderSetting.OpenAI = ProviderSetting.OpenAI(
    id = Uuid.parse(id),
    enabled = enabled,
    name = name,
    models = models,
    apiKey = apiKey,
    baseUrl = baseUrl,
)

private fun snapshotOf(vararg providers: CatalogProvider): ModelCatalogSnapshot {
    val catalog = LastChatCatalog(
        schemaVersion = 2,
        providers = providers.toList(),
        modelFamilies = emptyList(),
        models = emptyList(),
        globalRules = emptyList(),
        modelOverrides = emptyList(),
    )
    return ModelCatalogParser.parse(JsonInstant.encodeToString(catalog))
}

private fun merge(
    settings: Settings,
    snapshot: ModelCatalogSnapshot,
    resolver: ModelMetadataResolver,
    includeMissing: Boolean = false,
): Settings = mergeCatalogIntoSettings(
    settings = settings,
    snapshot = snapshot,
    resolver = resolver,
    includeMissingCatalogProviders = includeMissing,
)

class CatalogSettingsMergerTest {

    @Test
    fun `I1 - match by stable UUID preserves provider fields and list position`() {
        val providerId = uuid()
        val preset = preset(
            id = providerId,
            name = "Renamed Upstream",
            baseUrl = "https://new-endpoint.example.com/v1",
        )
        val snapshot = snapshotOf(preset)
        val existing = openAi(
            id = providerId,
            name = "My Custom Name",
            baseUrl = "https://custom.example.com/v1",
            enabled = true,
            apiKey = "sk-secret",
            models = listOf(
                Model(
                    modelId = "gpt-5",
                    displayName = "My GPT",
                    type = ModelType.CHAT,
                    inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
                    outputModalities = listOf(Modality.TEXT),
                    abilities = listOf(ModelAbility.TOOL, ModelAbility.REASONING),
                ),
            ),
        )
        val resolver = RecordingResolver()

        val result = merge(Settings(providers = listOf(existing)), snapshot, resolver)

        assertEquals(1, result.providers.size)
        val merged = result.providers.single()
        // Identity, apiKey, enabled, models (ids, order, per-model corrections), name, position
        assertEquals(existing.id, merged.id)
        assertEquals("My Custom Name", merged.name)
        assertEquals(true, merged.enabled)
        assertEquals("sk-secret", (merged as ProviderSetting.OpenAI).apiKey)
        assertEquals(1, merged.models.size)
        assertEquals("gpt-5", merged.models[0].modelId)
        assertEquals("My GPT", merged.models[0].displayName)
        assertEquals(listOf(Modality.TEXT, Modality.IMAGE), merged.models[0].inputModalities)
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), merged.models[0].abilities)
    }

    @Test
    fun `I2 - no auto-add with default includeMissingCatalogProviders false`() {
        val snapshot = snapshotOf(
            preset(uuid(), "Alpha", "https://alpha.example.com/v1"),
            preset(uuid(), "Beta", "https://beta.example.com/v1"),
            preset(uuid(), "Gamma", "https://gamma.example.com/v1"),
        )
        val existing = openAi(uuid(), name = "User Provider", baseUrl = "https://mine.example.com/v1")
        val resolver = RecordingResolver()

        val result = merge(Settings(providers = listOf(existing)), snapshot, resolver)

        assertEquals(1, result.providers.size)
        assertEquals(listOf(existing.id), result.providers.map { it.id })
    }

    @Test
    fun `I3 - input ordering of providers is preserved`() {
        val snapshot = snapshotOf(
            preset(uuid(), "Alpha", "https://alpha.example.com/v1"),
            preset(uuid(), "Beta", "https://beta.example.com/v1"),
        )
        val first = openAi(uuid(), name = "First", baseUrl = "https://first.example.com/v1")
        val second = openAi(uuid(), name = "Second", baseUrl = "https://second.example.com/v1")
        val third = openAi(uuid(), name = "Third", baseUrl = "https://third.example.com/v1")
        val resolver = RecordingResolver()

        val result = merge(
            Settings(providers = listOf(second, first, third)),
            snapshot,
            resolver,
        )

        assertEquals(
            listOf(second.id, first.id, third.id),
            result.providers.map { it.id },
        )
    }

    @Test
    fun `I4 - one preset never claims two existing providers`() {
        val preset = preset(
            id = uuid(),
            name = "Shared",
            baseUrl = "https://shared.example.com/v1",
        )
        val snapshot = snapshotOf(preset)
        val x = openAi(uuid(), name = "X", baseUrl = "https://shared.example.com/v1")
        val y = openAi(uuid(), name = "Y", baseUrl = "https://shared.example.com/v1")

        // includeMissing=true: if the preset were NOT claimed it would be re-added as a
        // missing provider. Single-claim means the first provider (X) claims it, so the
        // result must stay at exactly the two existing providers.
        val result = merge(
            Settings(providers = listOf(x, y)),
            snapshot,
            RecordingResolver(),
            includeMissing = true,
        )

        assertEquals(2, result.providers.size)
        assertEquals(listOf(x.id, y.id), result.providers.map { it.id })
    }

    @Test
    fun `I4 - two presets matching one provider resolve to the earliest matching key`() {
        val firstId = uuid()
        val secondId = uuid()
        val snapshot = snapshotOf(
            preset(firstId, "Shared", "https://shared.example.com/v1"),
            preset(secondId, "Shared", "https://shared.example.com/v1"),
        )
        // The configured provider carries the FIRST preset's stable UUID, so the earliest
        // matching key (stable UUID) claims the first preset; the second preset is not yet
        // configured and is added via the explicit-add path.
        val existing = openAi(
            id = firstId,
            name = "Shared",
            baseUrl = "https://shared.example.com/v1",
        )

        val result = merge(
            Settings(providers = listOf(existing)),
            snapshot,
            RecordingResolver(),
            includeMissing = true,
        )

        assertEquals(2, result.providers.size)
        assertEquals(listOf(firstId, secondId), result.providers.map { it.id.toString() })
    }

    @Test
    fun `I5 - deleted built-in providers are never re-seeded`() {
        val deletedId = uuid()
        val snapshot = snapshotOf(
            preset(deletedId, "BuiltIn", "https://builtin.example.com/v1", builtIn = true),
            preset(uuid(), "Other", "https://other.example.com/v1"),
        )
        val existing = openAi(uuid(), name = "Mine", baseUrl = "https://mine.example.com/v1")

        val result = merge(
            Settings(
                providers = listOf(existing),
                deletedBuiltInProviderIds = setOf(Uuid.parse(deletedId)),
            ),
            snapshot,
            RecordingResolver(),
            includeMissing = true,
        )

        val ids = result.providers.map { it.id }
        assertTrue(ids.contains(existing.id))
        assertFalse("Deleted built-in preset must not be re-seeded", ids.contains(Uuid.parse(deletedId)))
    }

    @Test
    fun `I6 - resolver runs for every provider with all preserve flags set`() {
        val presetId = uuid()
        val snapshot = snapshotOf(preset(presetId, "Alpha", "https://alpha.example.com/v1"))
        val matched = openAi(presetId, name = "Alpha", baseUrl = "https://alpha.example.com/v1")
        val unmatched = openAi(uuid(), name = "Mine", baseUrl = "https://mine.example.com/v1")
        val resolver = RecordingResolver()

        merge(Settings(providers = listOf(matched, unmatched)), snapshot, resolver)

        assertEquals(2, resolver.calls.size)
        resolver.calls.forEach { (_, options) ->
            assertTrue("preserveDisplayName must be true", options.preserveDisplayName)
            assertTrue("preserveExistingCapabilities must be true", options.preserveExistingCapabilities)
            assertTrue("preserveExistingType must be true", options.preserveExistingType)
        }
    }

    @Test
    fun `I6 - null snapshot resolver is a no-op passthrough at merge time`() {
        val snapshot = snapshotOf(preset(uuid(), "Alpha", "https://alpha.example.com/v1"))
        val existing = openAi(uuid(), name = "Mine", baseUrl = "https://mine.example.com/v1")
        val passthrough = ModelMetadataResolver(snapshotProvider = { null })

        val result = merge(Settings(providers = listOf(existing)), snapshot, passthrough)

        assertEquals(existing, result.providers.single())
    }

    @Test
    fun `I6 - preserve flags keep user corrections at merge time`() {
        val providerId = uuid()
        val snapshot = ModelCatalogParser.parse(
            """
            {
              "schema_version": 2,
              "providers": [{
                "id": "$providerId",
                "name": "Gemini",
                "base_url": "https://generativelanguage.googleapis.com/v1beta",
                "type": "google",
                "setup_models": []
              }],
              "model_families": [{
                "id": "gemini",
                "match_patterns": ["gemini"],
                "input_modalities": ["text", "image"],
                "abilities": ["tool"]
              }],
              "models": []
            }
            """.trimIndent(),
        )
        // User corrected the model with REASONING + image input + custom display name.
        val existing = ProviderSetting.Google(
            id = Uuid.parse(providerId),
            name = "Gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            models = listOf(
                Model(
                    modelId = "gemini-3-pro",
                    displayName = "My Gemini",
                    type = ModelType.CHAT,
                    inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
                    outputModalities = listOf(Modality.TEXT),
                    abilities = listOf(ModelAbility.TOOL, ModelAbility.REASONING),
                ),
            ),
        )
        val resolver = ModelMetadataResolver(snapshotProvider = { snapshot })

        val result = merge(Settings(providers = listOf(existing)), snapshot, resolver)

        val merged = result.providers.single().models.single()
        assertEquals("My Gemini", merged.displayName)
        // Catalog only lists tool; the user's REASONING survives (preserveExistingCapabilities).
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), merged.abilities)
        assertEquals(listOf(Modality.TEXT, Modality.IMAGE), merged.inputModalities)
    }

    @Test
    fun `match ladder - type and base url match when uuid differs`() {
        val preset = preset(
            id = uuid(),
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
        )
        val snapshot = snapshotOf(preset)
        // Different UUID + different name, same type + baseUrl (with trailing slash).
        val existing = openAi(
            id = uuid(),
            name = "My Router",
            baseUrl = "https://openrouter.ai/api/v1/",
        )

        // includeMissing=true would re-add the preset if it did not match the existing
        // provider by (type, baseUrl) — so exactly one provider means the ladder hit.
        val result = merge(
            Settings(providers = listOf(existing)),
            snapshot,
            RecordingResolver(),
            includeMissing = true,
        )

        assertEquals(1, result.providers.size)
        assertEquals(existing.id, result.providers.single().id)
    }

    @Test
    fun `match ladder - type and name match when uuid and base url differ`() {
        val preset = preset(
            id = uuid(),
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
        )
        val snapshot = snapshotOf(preset)
        val existing = openAi(
            id = uuid(),
            name = "  openrouter ",
            baseUrl = "https://entirely-different.example.com/v1",
        )

        val result = merge(
            Settings(providers = listOf(existing)),
            snapshot,
            RecordingResolver(),
            includeMissing = true,
        )

        assertEquals(1, result.providers.size)
        assertEquals(existing.id, result.providers.single().id)
    }

    @Test
    fun `non-uuid preset ids are skipped and never matched or added`() {
        val validId = uuid()
        val snapshot = snapshotOf(
            preset("not-a-uuid", "Invalid", "https://invalid.example.com/v1"),
            preset(validId, "Valid", "https://valid.example.com/v1"),
        )
        val existing = openAi(uuid(), name = "Mine", baseUrl = "https://mine.example.com/v1")

        val result = merge(
            Settings(providers = listOf(existing)),
            snapshot,
            RecordingResolver(),
            includeMissing = true,
        )

        val addedIds = result.providers.drop(1).map { it.id }
        assertEquals(listOf(Uuid.parse(validId)), addedIds)
        result.providers.forEach { provider -> assertNotNull(provider.id) }
    }
}
