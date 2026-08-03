package me.rerere.rikkahub.data.ai.models

import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.registry.ModelDisplayNameGenerator
import me.rerere.ai.registry.ModelIdNormalizer

/**
 * Resolution options controlling which user-edited `Model` fields survive re-resolution
 * (FR-007 / US4). Defaults are off: catalog metadata wins on the add path. The settings
 * merger and provider refresh always pass the `preserve*` flags so persisted user
 * corrections are never clobbered (FR-009, US3).
 */
data class ModelResolutionOptions(
    val preserveDisplayName: Boolean = false,
    val preserveExistingCapabilities: Boolean = false,
    val preserveExistingType: Boolean = false,
)

/**
 * Auto-detects a model's type, input/output modalities, tool-use and reasoning flags from
 * the layered catalog metadata (US2). Pure function over the snapshot: `snapshotProvider()`
 * is typically the catalog service's `snapshotOrNull()`.
 *
 * Only maps catalog entries onto the **existing** [Model] fields (`displayName`, `type`,
 * `inputModalities`, `outputModalities`, `abilities`); icon/cost stay on [ModelCatalogEntry]
 * and are never written to [Model] (R4/R5, FR-016).
 *
 * Lookup order (fixed): `resolveModelEntry` → `exactEntries` → stored canonical →
 * `canonicalEntries` (provider-hint disambiguation) → `inferFamilyEntry`. When the snapshot
 * is `null` the resolver is a no-op passthrough and callers fall through to the built-in
 * `ModelRegistry`.
 */
open class ModelMetadataResolver(
    private val snapshotProvider: () -> ModelCatalogSnapshot?,
) {
    fun applyToModel(
        model: Model,
        providerHint: ProviderSetting? = null,
        options: ModelResolutionOptions = ModelResolutionOptions(),
    ): Model {
        if (model.modelId.isBlank()) return model
        val snapshot = snapshotProvider() ?: return model

        val catalogEntry = resolveCatalogEntry(model = model, providerHint = providerHint, snapshot = snapshot)
        val canonicalModelId = catalogEntry?.canonicalModelId
            ?: ModelIdNormalizer.canonicalize(model.modelId)

        val displayName = if (
            options.preserveDisplayName &&
            model.displayName.isNotBlank() &&
            model.displayName != model.modelId
        ) {
            model.displayName
        } else {
            ModelDisplayNameGenerator.generate(model.modelId, canonicalModelId)
        }

        val resolvedType = resolveType(model, catalogEntry, options)
        val inputModalities = resolveInputModalities(model, catalogEntry, resolvedType, options)
        val outputModalities = resolveOutputModalities(model, catalogEntry, resolvedType, options)
        val abilities = resolveAbilities(model, catalogEntry, options)

        return model.copy(
            displayName = displayName,
            type = resolvedType,
            inputModalities = inputModalities,
            outputModalities = outputModalities,
            abilities = abilities,
        )
    }

    open fun applyToProvider(
        provider: ProviderSetting,
        options: ModelResolutionOptions = ModelResolutionOptions(
            preserveDisplayName = true,
            preserveExistingCapabilities = true,
            preserveExistingType = true,
        ),
    ): ProviderSetting {
        val resolvedModels = provider.models.map {
            applyToModel(it, providerHint = provider, options = options)
        }

        // Batch-aware display names for context-aware disambiguation (models whose name is
        // preserved keep it; the rest are generated together so duplicates disambiguate).
        val needsNameGen = resolvedModels.mapIndexed { index, model ->
            val original = provider.models[index]
            val isPreserved = options.preserveDisplayName &&
                original.displayName.isNotBlank() &&
                original.displayName != original.modelId
            !isPreserved
        }

        val batchEntries = resolvedModels.mapIndexedNotNull { index, model ->
            if (needsNameGen[index]) index to (model.modelId to null) else null
        }

        if (batchEntries.isNotEmpty()) {
            val batchNames = ModelDisplayNameGenerator.generateBatch(batchEntries.map { it.second })
            val finalModels = resolvedModels.toMutableList()
            batchEntries.forEachIndexed { batchIdx, (originalIdx, _) ->
                finalModels[originalIdx] = finalModels[originalIdx].copy(
                    displayName = batchNames[batchIdx]
                )
            }
            return provider.copyProvider(models = finalModels)
        }

        return provider.copyProvider(models = resolvedModels)
    }

    /**
     * Estimated USD cost for a round trip, from the catalog's per-token pricing. Returns
     * `null` when the model has no catalog entry or either price is missing.
     */
    fun estimateCostUsd(
        model: Model,
        promptTokens: Int,
        completionTokens: Int,
    ): Double? {
        val snapshot = snapshotProvider() ?: return null
        val catalogEntry = resolveCatalogEntry(model = model, providerHint = null, snapshot = snapshot)
            ?: return null
        val inputCost = catalogEntry.inputCostPerToken ?: return null
        val outputCost = catalogEntry.outputCostPerToken ?: return null
        return (promptTokens * inputCost) + (completionTokens * outputCost)
    }

    /**
     * True when [model] resolves to a catalog entry (exact, canonical, alias, family or
     * override). Lets callers decide whether to fall through to the built-in `ModelRegistry`
     * when the catalog produced nothing.
     */
    fun hasCatalogEntry(
        model: Model,
        providerHint: ProviderSetting? = null,
    ): Boolean {
        val snapshot = snapshotProvider() ?: return false
        return resolveCatalogEntry(model = model, providerHint = providerHint, snapshot = snapshot) != null
    }

    private fun resolveCatalogEntry(
        model: Model,
        providerHint: ProviderSetting?,
        snapshot: ModelCatalogSnapshot,
    ): ModelCatalogEntry? {
        snapshot.resolveModelEntry(
            modelId = model.modelId,
            canonicalHint = null,
            providerHint = providerHint,
            providerSlugHint = null,
        )?.let { return it }

        snapshot.exactEntries[model.modelId.lowercase()]?.let { return it }

        // "Stored canonical" hint: the local Model carries no canonical id, so this falls
        // back to the id-derived canonical key (FR-011).
        val canonicalModelId = ModelIdNormalizer.canonicalize(model.modelId)
        snapshot.exactEntries[canonicalModelId]?.let { return it }
        val candidates = snapshot.canonicalEntries[canonicalModelId]
        return candidates
            ?.let { selectCatalogCandidate(it, providerHint) }
            ?: snapshot.inferFamilyEntry(
                modelId = model.modelId,
                canonicalHint = null,
            )
    }

    /**
     * Pick one entry from an ambiguous canonical bucket. Provider hints (OpenAI/Google/
     * Claude base-url or vertex-ai flavor) disambiguate; without a hint, no candidate wins
     * and resolution falls through to the family inference.
     */
    private fun selectCatalogCandidate(
        candidates: List<ModelCatalogEntry>,
        providerHint: ProviderSetting?,
    ): ModelCatalogEntry? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.single()

        val providerMatches = candidates.filter { candidate ->
            candidate.matchesProviderHint(providerHint)
        }
        return providerMatches.singleOrNull()
    }

    private fun resolveType(
        model: Model,
        catalogEntry: ModelCatalogEntry?,
        options: ModelResolutionOptions,
    ): ModelType {
        if (options.preserveExistingType && model.type != ModelType.CHAT) {
            return model.type
        }

        if (model.type != ModelType.CHAT) {
            return model.type
        }

        return catalogEntry?.mode.toModelTypeOrNull() ?: model.type
    }

    private fun resolveInputModalities(
        model: Model,
        catalogEntry: ModelCatalogEntry?,
        resolvedType: ModelType,
        options: ModelResolutionOptions,
    ): List<Modality> {
        val inputs = linkedSetOf(Modality.TEXT)
        if (options.preserveExistingCapabilities && model.inputModalities.contains(Modality.IMAGE)) {
            inputs += Modality.IMAGE
        }
        if (
            catalogEntry?.supportsVision == true ||
            catalogEntry?.supportedModalities?.contains(Modality.IMAGE) == true
        ) {
            inputs += Modality.IMAGE
        }

        return when (resolvedType) {
            ModelType.CHAT, ModelType.IMAGE ->
                catalogEntry?.inputModalities?.takeIf { it.isNotEmpty() } ?: inputs.toList()
            ModelType.EMBEDDING -> listOf(Modality.TEXT)
        }
    }

    private fun resolveOutputModalities(
        model: Model,
        catalogEntry: ModelCatalogEntry?,
        resolvedType: ModelType,
        options: ModelResolutionOptions,
    ): List<Modality> {
        return when (resolvedType) {
            ModelType.CHAT -> catalogEntry?.outputModalities?.takeIf { it.isNotEmpty() } ?: buildList {
                add(Modality.TEXT)
                if (options.preserveExistingCapabilities && model.outputModalities.contains(Modality.IMAGE)) {
                    add(Modality.IMAGE)
                }
            }.distinct()

            ModelType.IMAGE -> catalogEntry?.outputModalities?.takeIf { it.isNotEmpty() } ?: buildList {
                if (options.preserveExistingCapabilities && model.outputModalities.contains(Modality.TEXT)) {
                    add(Modality.TEXT)
                } else if (catalogEntry?.supportedModalities?.contains(Modality.TEXT) == true) {
                    add(Modality.TEXT)
                }
                add(Modality.IMAGE)
            }.distinct()

            ModelType.EMBEDDING -> listOf(Modality.TEXT)
        }
    }

    private fun resolveAbilities(
        model: Model,
        catalogEntry: ModelCatalogEntry?,
        options: ModelResolutionOptions,
    ): List<ModelAbility> {
        val abilities = linkedSetOf<ModelAbility>()
        if (options.preserveExistingCapabilities && model.abilities.contains(ModelAbility.TOOL)) {
            abilities += ModelAbility.TOOL
        }
        if (catalogEntry?.supportsFunctionCalling == true) {
            abilities += ModelAbility.TOOL
        }
        if (options.preserveExistingCapabilities && model.abilities.contains(ModelAbility.REASONING)) {
            abilities += ModelAbility.REASONING
        }
        if (catalogEntry?.supportsReasoning == true) {
            abilities += ModelAbility.REASONING
        }
        return ModelAbility.entries.filter { it in abilities }
    }
}

private fun String?.toModelTypeOrNull(): ModelType? {
    return when (this?.lowercase()) {
        "embedding" -> ModelType.EMBEDDING
        "image_generation", "image" -> ModelType.IMAGE
        "chat" -> ModelType.CHAT
        else -> null
    }
}

private fun ModelCatalogEntry.matchesProviderHint(providerHint: ProviderSetting?): Boolean {
    val allowedProviders = when (providerHint) {
        is ProviderSetting.Claude -> setOf("anthropic")
        is ProviderSetting.Google -> {
            if (providerHint.vertexAI) {
                setOf("vertex-ai", "vertex-ai-language-models")
            } else {
                setOf("gemini", "google-ai-studio")
            }
        }

        is ProviderSetting.OpenAI -> {
            if (providerHint.baseUrl.contains("api.openai.com", ignoreCase = true)) {
                setOf("openai")
            } else {
                emptySet()
            }
        }

        else -> emptySet()
    }
    if (allowedProviders.isEmpty()) return false

    val keyProvider = key.substringBefore("/").takeIf { key.contains("/") }?.normalizeProviderToken()
    val providerToken = providerSlug?.normalizeProviderToken()
    return allowedProviders.any { candidate ->
        candidate == keyProvider || candidate == providerToken
    }
}

private fun String.normalizeProviderToken(): String {
    return lowercase()
        .replace('_', '-')
        .replace('.', '-')
}
