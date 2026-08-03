package me.rerere.rikkahub.data.ai.models

import java.util.Locale
import kotlin.uuid.Uuid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import me.rerere.ai.provider.BalanceOption
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.registry.ModelIdNormalizer
import me.rerere.rikkahub.utils.JsonInstant

private const val TAG = "ModelCatalog"
private const val MODEL_CATALOG_ASSET_NAME = "catalog/lastchat_catalog.json"

/**
 * Base URL the catalog-relative icon paths (e.g. `icons/...`) are expanded against. Points
 * at the repo-owned fork (R6) so the bundled icons resolve over raw.githubusercontent.com.
 */
const val CATALOG_RAW_BASE_URL =
    "https://raw.githubusercontent.com/udin-petot/Rikka-Agentic/master/catalog/"

@Serializable
data class LastChatCatalog(
    @SerialName("schema_version")
    val schemaVersion: Int = 1,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    val providers: List<CatalogProvider> = emptyList(),
    val models: List<CatalogModel> = emptyList(),
    @SerialName("global_rules")
    val globalRules: List<CatalogModelRule> = emptyList(),
    @SerialName("model_overrides")
    val modelOverrides: List<CatalogModelOverride> = emptyList(),
    @SerialName("model_families")
    val modelFamilies: List<CatalogModelFamily> = emptyList(),
    @SerialName("search_providers")
    val searchProviders: List<CatalogServiceProvider> = emptyList(),
    @SerialName("tts_providers")
    val ttsProviders: List<CatalogTTSProvider> = emptyList(),
    @SerialName("stt_providers")
    val sttProviders: List<CatalogServiceProvider> = emptyList(),
    @SerialName("model_groups")
    val legacyModelGroups: List<CatalogModelFamily> = emptyList(),
) {
    val effectiveModelFamilies: List<CatalogModelFamily>
        get() = modelFamilies.ifEmpty { legacyModelGroups }
}

@Serializable
data class CatalogServiceProvider(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val description: String = "",
    val icon: String? = null,
    val preset: Boolean = true,
    @SerialName("built_in")
    val builtIn: Boolean = false,
)

@Serializable
data class CatalogTTSProvider(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val description: String = "",
    val icon: String? = null,
    val preset: Boolean = true,
    @SerialName("built_in")
    val builtIn: Boolean = false,
    val type: CatalogTTSProviderType = CatalogTTSProviderType.OPENAI,
    @SerialName("base_url")
    val baseUrl: String = "",
    @SerialName("default_model")
    val defaultModel: String = "",
    @SerialName("default_voice")
    val defaultVoice: String = "",
    @SerialName("signup_url")
    val signupUrl: String? = null,
    @SerialName("api_key_url")
    val apiKeyUrl: String? = null,
)

@Serializable
enum class CatalogTTSProviderType {
    @SerialName("openai") OPENAI,
    @SerialName("gemini") GEMINI,
    @SerialName("system") SYSTEM,
    @SerialName("minimax") MINIMAX,
    @SerialName("elevenlabs") ELEVENLABS,
    @SerialName("qwen") QWEN,
    @SerialName("fishaudio") FISHAUDIO,
    @SerialName("cartesia") CARTESIA,
    @SerialName("playht") PLAYHT,
}

@Serializable
data class CatalogProvider(
    val id: String,
    val name: String,
    val description: String = "",
    @SerialName("name_i18n")
    val nameI18n: Map<String, String> = emptyMap(),
    @SerialName("description_i18n")
    val descriptionI18n: Map<String, String> = emptyMap(),
    val type: CatalogProviderType = CatalogProviderType.OPENAI,
    @SerialName("base_url")
    val baseUrl: String,
    @SerialName("chat_completions_path")
    val chatCompletionsPath: String = "/chat/completions",
    @SerialName("use_response_api")
    val useResponseApi: Boolean = false,
    @SerialName("balance_option")
    val balanceOption: BalanceOption = BalanceOption(),
    val icon: String? = null,
    val enabled: Boolean = true,
    @SerialName("built_in")
    val builtIn: Boolean = false,
    val preset: Boolean = true,
    @SerialName("signup_url")
    val signupUrl: String? = null,
    @SerialName("api_key_url")
    val apiKeyUrl: String? = null,
    @SerialName("setup_recommended")
    val setupRecommended: Boolean = false,
    @SerialName("setup_order")
    val setupOrder: Int = 100,
    @SerialName("setup_description")
    val setupDescription: String? = null,
    @SerialName("setup_models")
    val setupModels: List<String> = emptyList(),
    @SerialName("setup_defaults")
    val setupDefaults: CatalogSetupDefaults? = null,
    @SerialName("setup_search_service")
    val setupSearchService: String? = null,
) {
    /**
     * Resolve the display name for [locale]: exact BCP-47 tag, then language subtag,
     * then the English default field (FR-012, R8).
     */
    fun displayName(locale: Locale = Locale.getDefault()): String {
        return nameI18n[locale.toLanguageTag()]
            ?: nameI18n[locale.language]
            ?: name
    }

    /**
     * Resolve the description for [locale] with English fallback (FR-012, R8).
     */
    fun description(locale: Locale = Locale.getDefault()): String {
        return descriptionI18n[locale.toLanguageTag()]
            ?: descriptionI18n[locale.language]
            ?: description
    }
}

@Serializable
data class CatalogSetupDefaults(
    val chat: String? = null,
    val title: String? = null,
    val summarizer: String? = null,
    val ocr: String? = null,
)

@Serializable
enum class CatalogProviderType {
    @SerialName("openai")
    OPENAI,

    @SerialName("google")
    GOOGLE,

    @SerialName("claude")
    CLAUDE,
}

/**
 * Catalog-side model-type vocabulary (R4). The bundled catalog ships the enum name in
 * upper case (`CHAT`, `STT`, ...); the lowercase spellings are accepted as aliases for
 * robustness against network-updated payloads. `stt` is skipped and maps to
 * [ModelType.CHAT]. Local [ModelType] cannot be reused directly because it serializes
 * uppercase and lacks `STT`, and changing it would alter the stored `Settings.providers`
 * shape (FR-016).
 */
@Serializable
enum class CatalogModelType {
    @SerialName("CHAT")
    @JsonNames("chat")
    CHAT,

    @SerialName("EMBEDDING")
    @JsonNames("embedding")
    EMBEDDING,

    @SerialName("IMAGE")
    @JsonNames("image")
    IMAGE,

    @SerialName("IMAGE_GENERATION")
    @JsonNames("image_generation", "imageGeneration")
    IMAGE_GENERATION,

    @SerialName("STT")
    @JsonNames("stt")
    STT,
}

fun CatalogModelType.toModelType(): ModelType = when (this) {
    CatalogModelType.CHAT, CatalogModelType.STT -> ModelType.CHAT
    CatalogModelType.EMBEDDING -> ModelType.EMBEDDING
    CatalogModelType.IMAGE, CatalogModelType.IMAGE_GENERATION -> ModelType.IMAGE
}

/**
 * Catalog-side modality vocabulary (R4). `AUDIO` is dropped and maps to [Modality.TEXT].
 */
@Serializable
enum class CatalogModality {
    @SerialName("TEXT")
    @JsonNames("text")
    TEXT,

    @SerialName("IMAGE")
    @JsonNames("image")
    IMAGE,

    @SerialName("AUDIO")
    @JsonNames("audio")
    AUDIO,
}

fun CatalogModality.toModality(): Modality = when (this) {
    CatalogModality.TEXT, CatalogModality.AUDIO -> Modality.TEXT
    CatalogModality.IMAGE -> Modality.IMAGE
}

@Serializable
enum class CatalogModelAbility {
    @SerialName("TOOL")
    @JsonNames("tool")
    TOOL,

    @SerialName("REASONING")
    @JsonNames("reasoning")
    REASONING,
}

fun CatalogModelAbility.toModelAbility(): ModelAbility = when (this) {
    CatalogModelAbility.TOOL -> ModelAbility.TOOL
    CatalogModelAbility.REASONING -> ModelAbility.REASONING
}

@Serializable
data class CatalogModel(
    val id: String,
    @SerialName("canonical_model_id")
    val canonicalModelId: String? = null,
    @SerialName("api_aliases")
    val apiAliases: List<String> = emptyList(),
    @SerialName("provider_ids")
    val providerIds: List<String> = emptyList(),
    val type: CatalogModelType = CatalogModelType.CHAT,
    @SerialName("input_modalities")
    val inputModalities: List<CatalogModality> = listOf(CatalogModality.TEXT),
    @SerialName("output_modalities")
    val outputModalities: List<CatalogModality> = listOf(CatalogModality.TEXT),
    val abilities: List<CatalogModelAbility> = emptyList(),
    @SerialName("context_window")
    val contextWindow: Int? = null,
    @SerialName("input_cost_per_token")
    val inputCostPerToken: Double? = null,
    @SerialName("output_cost_per_token")
    val outputCostPerToken: Double? = null,
    @SerialName("family_id")
    val familyId: String? = null,
    @SerialName("group_id")
    val legacyGroupId: String? = null,
    @SerialName("provider_slug")
    val providerSlug: String? = null,
) {
    val effectiveFamilyId: String?
        get() = familyId ?: legacyGroupId
}

@Serializable
data class CatalogModelFamily(
    val id: String,
    val aliases: List<String> = emptyList(),
    @SerialName("match_patterns")
    val matchPatterns: List<String> = emptyList(),
    val icon: String? = null,
    val type: CatalogModelType = CatalogModelType.CHAT,
    @SerialName("input_modalities")
    val inputModalities: List<CatalogModality> = listOf(CatalogModality.TEXT),
    @SerialName("output_modalities")
    val outputModalities: List<CatalogModality> = listOf(CatalogModality.TEXT),
    val abilities: List<CatalogModelAbility> = emptyList(),
    @SerialName("provider_slug")
    val providerSlug: String? = null,
    val versions: List<CatalogModelVersion> = emptyList(),
)

@Serializable
data class CatalogModelVersion(
    val id: String = "",
    @SerialName("match_patterns")
    val matchPatterns: List<String> = emptyList(),
    @SerialName("exclude_patterns")
    val excludePatterns: List<String> = emptyList(),
    val type: CatalogModelType? = null,
    @SerialName("input_modalities")
    val inputModalities: List<CatalogModality>? = null,
    @SerialName("output_modalities")
    val outputModalities: List<CatalogModality>? = null,
    val abilities: List<CatalogModelAbility>? = null,
    @SerialName("provider_slug")
    val providerSlug: String? = null,
    @SerialName("canonical_model_id")
    val canonicalModelId: String? = null,
)

@Serializable
data class CatalogModelRule(
    val id: String = "",
    @SerialName("match_patterns")
    val matchPatterns: List<String> = emptyList(),
    @SerialName("exclude_patterns")
    val excludePatterns: List<String> = emptyList(),
    val type: CatalogModelType? = null,
    @SerialName("input_modalities")
    val inputModalities: List<CatalogModality>? = null,
    @SerialName("output_modalities")
    val outputModalities: List<CatalogModality>? = null,
    val abilities: List<CatalogModelAbility>? = null,
    @SerialName("provider_slug")
    val providerSlug: String? = null,
    @SerialName("canonical_model_id")
    val canonicalModelId: String? = null,
)

@Serializable
data class CatalogModelOverride(
    val id: String = "",
    @SerialName("canonical_model_id")
    val canonicalModelId: String? = null,
    @SerialName("api_aliases")
    val apiAliases: List<String> = emptyList(),
    @SerialName("provider_ids")
    val providerIds: List<String> = emptyList(),
    @SerialName("provider_slugs")
    val providerSlugs: List<String> = emptyList(),
    @SerialName("base_url_patterns")
    val baseUrlPatterns: List<String> = emptyList(),
    @SerialName("match_patterns")
    val matchPatterns: List<String> = emptyList(),
    @SerialName("exclude_patterns")
    val excludePatterns: List<String> = emptyList(),
    val type: CatalogModelType? = null,
    @SerialName("input_modalities")
    val inputModalities: List<CatalogModality>? = null,
    @SerialName("output_modalities")
    val outputModalities: List<CatalogModality>? = null,
    val abilities: List<CatalogModelAbility>? = null,
    @SerialName("provider_slug")
    val providerSlug: String? = null,
    @SerialName("input_cost_per_token")
    val inputCostPerToken: Double? = null,
    @SerialName("output_cost_per_token")
    val outputCostPerToken: Double? = null,
)

data class ModelCatalogEntry(
    val key: String,
    val canonicalModelId: String,
    val apiAliases: List<String> = emptyList(),
    val providerIds: List<String> = emptyList(),
    val modelFamilyId: String? = null,
    val mode: String? = null,
    val supportedModalities: List<Modality> = emptyList(),
    val inputModalities: List<Modality> = emptyList(),
    val outputModalities: List<Modality> = emptyList(),
    val supportsVision: Boolean = false,
    val supportsFunctionCalling: Boolean = false,
    val supportsReasoning: Boolean = false,
    val inputCostPerToken: Double? = null,
    val outputCostPerToken: Double? = null,
    val iconUrl: String? = null,
    val providerSlug: String? = null,
)

data class ModelCatalogSnapshot(
    val exactEntries: Map<String, ModelCatalogEntry>,
    val canonicalEntries: Map<String, List<ModelCatalogEntry>>,
    val providers: List<CatalogProvider> = emptyList(),
    val modelFamilies: List<CatalogModelFamily> = emptyList(),
    val globalRules: List<CatalogModelRule> = emptyList(),
    val modelOverrides: List<CatalogModelOverride> = emptyList(),
    val searchProviders: List<CatalogServiceProvider> = emptyList(),
    val ttsProviders: List<CatalogTTSProvider> = emptyList(),
    val sttProviders: List<CatalogServiceProvider> = emptyList(),
) {
    val catalog: LastChatCatalog
        get() = LastChatCatalog(
            providers = providers,
            modelFamilies = modelFamilies,
            globalRules = globalRules,
            modelOverrides = modelOverrides,
            searchProviders = searchProviders,
            ttsProviders = ttsProviders,
            sttProviders = sttProviders,
        )
}

object ModelCatalogParser {
    /**
     * Pure string → snapshot. Throws nothing; malformed JSON returns an empty snapshot.
     */
    fun parse(rawJson: String): ModelCatalogSnapshot {
        val catalog = runCatching {
            JsonInstant.decodeFromString<LastChatCatalog>(rawJson)
        }.getOrElse {
            runCatching { android.util.Log.w(TAG, "Failed to parse catalog JSON", it) }
            return ModelCatalogSnapshot(
                exactEntries = emptyMap(),
                canonicalEntries = emptyMap(),
            )
        }

        val exactEntries = linkedMapOf<String, ModelCatalogEntry>()
        val canonicalEntries = linkedMapOf<String, MutableList<ModelCatalogEntry>>()
        val modelFamilies = catalog.effectiveModelFamilies
        val familiesById = modelFamilies.associateBy { it.id }
        val effectiveOverrides = catalog.modelOverrides + catalog.models.map { it.toModelOverride() }

        catalog.models.forEach { model ->
            val familyId = model.effectiveFamilyId
            val family = familyId?.let(familiesById::get)
            val canonicalModelId = ModelIdNormalizer.canonicalize(
                modelId = model.id,
                canonicalHint = model.canonicalModelId,
            )
            val inputModalities = model.inputModalities.ifEmpty { listOf(CatalogModality.TEXT) }
                .map { it.toModality() }
            val outputModalities = model.outputModalities.ifEmpty {
                defaultOutputModalities(model.type)
            }.map { it.toModality() }
            val entry = ModelCatalogEntry(
                key = model.id,
                canonicalModelId = canonicalModelId,
                apiAliases = model.apiAliases,
                providerIds = model.providerIds,
                modelFamilyId = familyId,
                mode = model.type.toModelType().name.lowercase(),
                supportedModalities = (inputModalities + outputModalities).distinct(),
                inputModalities = inputModalities,
                outputModalities = outputModalities,
                supportsVision = inputModalities.contains(Modality.IMAGE),
                supportsFunctionCalling = model.abilities.contains(CatalogModelAbility.TOOL),
                supportsReasoning = model.abilities.contains(CatalogModelAbility.REASONING),
                inputCostPerToken = model.inputCostPerToken,
                outputCostPerToken = model.outputCostPerToken,
                iconUrl = family?.icon?.toCatalogIconUrl(),
                providerSlug = model.providerSlug,
            )

            buildList {
                add(model.id)
                model.canonicalModelId?.takeIf { it.isNotBlank() }?.let(::add)
                addAll(model.apiAliases)
            }.map { candidate -> candidate.lowercase() }
                .filter { it.isNotBlank() }
                .distinct()
                .forEach { key ->
                    exactEntries.putIfAbsent(key, entry)
                }
            canonicalEntries.getOrPut(entry.canonicalModelId) { mutableListOf() }.add(entry)
        }

        catalog.modelOverrides.forEach { override ->
            val entry = override.toCatalogEntry(modelFamilies) ?: return@forEach
            buildList {
                override.id.takeIf { it.isNotBlank() }?.let(::add)
                override.canonicalModelId?.takeIf { it.isNotBlank() }?.let(::add)
                addAll(override.apiAliases)
            }.map { candidate -> candidate.lowercase() }
                .filter { it.isNotBlank() }
                .distinct()
                .forEach { key ->
                    exactEntries.putIfAbsent(key, entry)
                }
            canonicalEntries.getOrPut(entry.canonicalModelId) { mutableListOf() }.add(entry)
        }

        canonicalEntries
            .filterValues { entries -> entries.size > 1 }
            .keys
            .forEach { ambiguousCanonicalId ->
                exactEntries.remove(ambiguousCanonicalId.lowercase())
            }

        return ModelCatalogSnapshot(
            exactEntries = exactEntries,
            canonicalEntries = canonicalEntries.mapValues { (_, entries) -> entries.toList() },
            providers = catalog.providers,
            modelFamilies = modelFamilies,
            globalRules = catalog.globalRules,
            modelOverrides = effectiveOverrides,
            searchProviders = catalog.searchProviders,
            ttsProviders = catalog.ttsProviders,
            sttProviders = catalog.sttProviders,
        )
    }
}

private fun CatalogModel.toModelOverride(): CatalogModelOverride {
    return CatalogModelOverride(
        id = id,
        canonicalModelId = canonicalModelId,
        apiAliases = apiAliases,
        providerIds = providerIds,
        type = type,
        inputModalities = inputModalities,
        outputModalities = outputModalities,
        abilities = abilities,
        providerSlug = providerSlug,
        inputCostPerToken = inputCostPerToken,
        outputCostPerToken = outputCostPerToken,
    )
}

private fun CatalogModelOverride.toCatalogEntry(modelFamilies: List<CatalogModelFamily>): ModelCatalogEntry? {
    val key = id.takeIf { it.isNotBlank() } ?: apiAliases.firstOrNull { it.isNotBlank() } ?: return null
    val resolvedType = type ?: CatalogModelType.CHAT
    val inputs = inputModalities ?: listOf(CatalogModality.TEXT)
    val outputs = outputModalities ?: defaultOutputModalities(resolvedType)
    val resolvedAbilities = abilities ?: emptyList()
    val fingerprint = ModelCatalogFingerprint(
        modelId = key,
        canonicalHint = canonicalModelId,
        providerHint = null,
        providerSlugHint = providerSlug
    )
    val family = modelFamilies.firstOrNull { it.matches(fingerprint) }
    return ModelCatalogEntry(
        key = key,
        canonicalModelId = ModelIdNormalizer.canonicalize(key, canonicalModelId),
        apiAliases = apiAliases,
        providerIds = providerIds,
        modelFamilyId = family?.id,
        mode = resolvedType.toModelType().name.lowercase(),
        supportedModalities = (inputs + outputs).map { it.toModality() }.distinct(),
        inputModalities = inputs.map { it.toModality() },
        outputModalities = outputs.map { it.toModality() },
        supportsVision = inputs.contains(CatalogModality.IMAGE),
        supportsFunctionCalling = resolvedAbilities.contains(CatalogModelAbility.TOOL),
        supportsReasoning = resolvedAbilities.contains(CatalogModelAbility.REASONING),
        inputCostPerToken = inputCostPerToken,
        outputCostPerToken = outputCostPerToken,
        iconUrl = family?.icon?.toCatalogIconUrl(),
        providerSlug = providerSlug,
    )
}

fun ModelCatalogSnapshot.inferFamilyEntry(
    modelId: String,
    canonicalHint: String? = null,
): ModelCatalogEntry? {
    return resolveModelEntry(
        modelId = modelId,
        canonicalHint = canonicalHint,
        includeOverrides = false,
    )
}

fun ModelCatalogSnapshot.resolveModelEntry(
    modelId: String,
    canonicalHint: String? = null,
    providerHint: ProviderSetting? = null,
    providerSlugHint: String? = null,
    includeOverrides: Boolean = true,
): ModelCatalogEntry? {
    if (modelId.isBlank()) return null
    val fingerprint = ModelCatalogFingerprint(
        modelId = modelId,
        canonicalHint = canonicalHint,
        providerHint = providerHint,
        providerSlugHint = providerSlugHint,
    )
    val builder = ModelCatalogEntryBuilder(modelId, fingerprint.canonicalModelId)
    globalRules
        .filter { it.matches(fingerprint) }
        .forEach { builder.applyRule(it, fingerprint) }

    val family = modelFamilies.firstOrNull { it.matches(fingerprint) }
    family?.let { matchedFamily ->
        builder.modelFamilyId = matchedFamily.id
        builder.iconUrl = matchedFamily.icon?.toCatalogIconUrl()
        builder.applyFamily(matchedFamily)
        matchedFamily.versions
            .filter { it.matches(fingerprint) }
            .forEach { builder.applyVersion(it, fingerprint) }
    }

    if (includeOverrides) {
        modelOverrides
            .filter { it.matches(fingerprint) }
            .forEach { builder.applyOverride(it, fingerprint) }
    }

    return if (builder.hasMatchedRule) builder.build() else null
}

data class ModelCatalogResolutionTrace(
    val globalRules: List<String>,
    val familyId: String?,
    val familyVersions: List<String>,
    val overrides: List<String>,
    val entry: ModelCatalogEntry?,
)

fun ModelCatalogSnapshot.explainModelResolution(
    modelId: String,
    canonicalHint: String? = null,
    providerHint: ProviderSetting? = null,
    providerSlugHint: String? = null,
): ModelCatalogResolutionTrace {
    if (modelId.isBlank()) {
        return ModelCatalogResolutionTrace(emptyList(), null, emptyList(), emptyList(), null)
    }
    val fingerprint = ModelCatalogFingerprint(
        modelId = modelId,
        canonicalHint = canonicalHint,
        providerHint = providerHint,
        providerSlugHint = providerSlugHint,
    )
    val matchedGlobalRules = globalRules.filter { it.matches(fingerprint) }
    val family = modelFamilies.firstOrNull { it.matches(fingerprint) }
    val matchedVersions = family?.versions?.filter { it.matches(fingerprint) }.orEmpty()
    val matchedOverrides = modelOverrides.filter { it.matches(fingerprint) }
    val entry = resolveModelEntry(
        modelId = modelId,
        canonicalHint = canonicalHint,
        providerHint = providerHint,
        providerSlugHint = providerSlugHint,
    )
    return ModelCatalogResolutionTrace(
        globalRules = matchedGlobalRules.map { it.id.ifBlank { it.matchPatterns.joinToString() } },
        familyId = family?.id,
        familyVersions = matchedVersions.map { it.id.ifBlank { it.matchPatterns.joinToString() } },
        overrides = matchedOverrides.map { it.id.ifBlank { it.matchPatterns.joinToString() } },
        entry = entry,
    )
}

private data class ModelCatalogFingerprint(
    val modelId: String,
    val canonicalHint: String?,
    val providerHint: ProviderSetting?,
    val providerSlugHint: String?,
) {
    val canonicalModelId: String = ModelIdNormalizer.canonicalize(modelId, canonicalHint)
    private val preprocessedModelId: String = ModelIdNormalizer.preprocess(modelId, canonicalHint)
    val candidates: List<String> = buildList {
        add(modelId)
        canonicalHint?.takeIf { it.isNotBlank() }?.let(::add)
        add(canonicalModelId)
        add(preprocessedModelId)
    }.filter { it.isNotBlank() }.distinct()
    val providerId: String? = providerHint?.id?.toString()
    val providerBaseUrl: String? = providerHint?.catalogBaseUrl()
    val providerSlugs: Set<String> = buildSet {
        providerSlugHint?.takeIf { it.isNotBlank() }?.let { add(it.normalizeCatalogToken()) }
        providerHint?.catalogProviderTokens()?.forEach(::add)
    }
}

private class ModelCatalogEntryBuilder(
    private val key: String,
    initialCanonicalModelId: String,
) {
    var canonicalModelId: String = initialCanonicalModelId
    var modelFamilyId: String? = null
    var type: ModelType = ModelType.CHAT
    var inputModalities: List<Modality> = listOf(Modality.TEXT)
    var outputModalities: List<Modality> = listOf(Modality.TEXT)
    var abilities: List<ModelAbility> = emptyList()
    var inputCostPerToken: Double? = null
    var outputCostPerToken: Double? = null
    var iconUrl: String? = null
    var providerSlug: String? = null
    var apiAliases: List<String> = emptyList()
    var providerIds: List<String> = emptyList()
    var hasMatchedRule: Boolean = false

    fun applyFamily(family: CatalogModelFamily) {
        hasMatchedRule = true
        type = family.type.toModelType()
        inputModalities = family.inputModalities.ifEmpty { listOf(CatalogModality.TEXT) }
            .map { it.toModality() }
        outputModalities = family.outputModalities
            .ifEmpty { listOf(CatalogModality.TEXT) }
            .map { it.toModality() }
            .ifEmpty { defaultOutputModalities(type) }
        abilities = family.abilities.map { it.toModelAbility() }
        providerSlug = family.providerSlug
    }

    fun applyRule(rule: CatalogModelRule, fingerprint: ModelCatalogFingerprint) {
        hasMatchedRule = true
        applySharedFields(
            type = rule.type,
            inputModalities = rule.inputModalities,
            outputModalities = rule.outputModalities,
            abilities = rule.abilities,
            providerSlug = rule.providerSlug,
            canonicalModelId = rule.canonicalModelId,
            fingerprint = fingerprint,
        )
    }

    fun applyVersion(version: CatalogModelVersion, fingerprint: ModelCatalogFingerprint) {
        hasMatchedRule = true
        applySharedFields(
            type = version.type,
            inputModalities = version.inputModalities,
            outputModalities = version.outputModalities,
            abilities = version.abilities,
            providerSlug = version.providerSlug,
            canonicalModelId = version.canonicalModelId,
            fingerprint = fingerprint,
        )
    }

    fun applyOverride(override: CatalogModelOverride, fingerprint: ModelCatalogFingerprint) {
        hasMatchedRule = true
        apiAliases = override.apiAliases.ifEmpty { apiAliases }
        providerIds = override.providerIds.ifEmpty { providerIds }
        inputCostPerToken = override.inputCostPerToken ?: inputCostPerToken
        outputCostPerToken = override.outputCostPerToken ?: outputCostPerToken
        applySharedFields(
            type = override.type,
            inputModalities = override.inputModalities,
            outputModalities = override.outputModalities,
            abilities = override.abilities,
            providerSlug = override.providerSlug,
            canonicalModelId = override.canonicalModelId,
            fingerprint = fingerprint,
        )
    }

    private fun applySharedFields(
        type: CatalogModelType?,
        inputModalities: List<CatalogModality>?,
        outputModalities: List<CatalogModality>?,
        abilities: List<CatalogModelAbility>?,
        providerSlug: String?,
        canonicalModelId: String?,
        fingerprint: ModelCatalogFingerprint,
    ) {
        type?.let { nextType ->
            this.type = nextType.toModelType()
            this.outputModalities = defaultOutputModalities(this.type)
            if (nextType.toModelType() == ModelType.EMBEDDING) {
                this.inputModalities = listOf(Modality.TEXT)
            }
        }
        inputModalities?.let { this.inputModalities = it.map { m -> m.toModality() }.ifEmpty { listOf(Modality.TEXT) } }
        outputModalities?.let { this.outputModalities = it.map { m -> m.toModality() }.ifEmpty { defaultOutputModalities(this.type) } }
        abilities?.let { this.abilities = it.map { a -> a.toModelAbility() } }
        providerSlug?.let { this.providerSlug = it }
        canonicalModelId
            ?.takeIf { it.isNotBlank() }
            ?.let { this.canonicalModelId = ModelIdNormalizer.canonicalize(fingerprint.modelId, it) }
    }

    fun build(): ModelCatalogEntry {
        val inputs = inputModalities.ifEmpty { listOf(Modality.TEXT) }
        val outputs = outputModalities.ifEmpty { defaultOutputModalities(type) }
        return ModelCatalogEntry(
            key = key,
            canonicalModelId = canonicalModelId,
            apiAliases = apiAliases,
            providerIds = providerIds,
            modelFamilyId = modelFamilyId,
            mode = type.name.lowercase(),
            supportedModalities = (inputs + outputs).distinct(),
            inputModalities = inputs,
            outputModalities = outputs,
            supportsVision = inputs.contains(Modality.IMAGE),
            supportsFunctionCalling = abilities.contains(ModelAbility.TOOL),
            supportsReasoning = abilities.contains(ModelAbility.REASONING),
            inputCostPerToken = inputCostPerToken,
            outputCostPerToken = outputCostPerToken,
            iconUrl = iconUrl,
            providerSlug = providerSlug,
        )
    }
}

internal fun String.toCatalogIconUrl(): String {
    return when {
        startsWith("http://") || startsWith("https://") -> this
        else -> CATALOG_RAW_BASE_URL + trimStart('/')
    }
}

fun ModelCatalogSnapshot.searchProviderIconUri(providerIdOrName: String): String? {
    return searchProviders
        .firstOrNull { provider -> provider.matchesCatalogServiceProvider(providerIdOrName) }
        ?.icon
        ?.toCatalogIconUrl()
}

fun ModelCatalogSnapshot.ttsProviderIconUri(providerIdOrName: String): String? {
    return ttsProviders
        .firstOrNull { provider -> provider.matchesCatalogTTSProvider(providerIdOrName) }
        ?.icon
        ?.toCatalogIconUrl()
}

fun ModelCatalogSnapshot.ttsProviderById(providerId: String): CatalogTTSProvider? {
    return ttsProviders.firstOrNull { it.id == providerId }
}

fun ModelCatalogSnapshot.sttProviderIconUri(providerIdOrName: String): String? {
    return sttProviders
        .firstOrNull { provider -> provider.matchesCatalogServiceProvider(providerIdOrName) }
        ?.icon
        ?.toCatalogIconUrl()
}

private fun CatalogServiceProvider.matchesCatalogServiceProvider(value: String): Boolean {
    val normalizedValue = value.normalizeCatalogToken()
    if (normalizedValue.isBlank()) return false
    return sequenceOf(id, name)
        .plus(aliases)
        .map { it.normalizeCatalogToken() }
        .any { it == normalizedValue }
}

private fun CatalogTTSProvider.matchesCatalogTTSProvider(value: String): Boolean {
    val normalizedValue = value.normalizeCatalogToken()
    if (normalizedValue.isBlank()) return false
    return sequenceOf(id, name)
        .plus(aliases)
        .map { it.normalizeCatalogToken() }
        .any { it == normalizedValue }
}

private fun defaultOutputModalities(type: ModelType): List<Modality> {
    return when (type) {
        ModelType.CHAT, ModelType.EMBEDDING -> listOf(Modality.TEXT)
        ModelType.IMAGE -> listOf(Modality.IMAGE)
    }
}

private fun defaultOutputModalities(type: CatalogModelType): List<CatalogModality> {
    return when (type.toModelType()) {
        ModelType.CHAT, ModelType.EMBEDDING -> listOf(CatalogModality.TEXT)
        ModelType.IMAGE -> listOf(CatalogModality.IMAGE)
    }
}

private fun CatalogModelFamily.matches(fingerprint: ModelCatalogFingerprint): Boolean {
    return matchPatterns.anyPatternMatches(fingerprint.candidates)
}

private fun CatalogModelRule.matches(fingerprint: ModelCatalogFingerprint): Boolean {
    if (excludePatterns.anyPatternMatches(fingerprint.candidates)) return false
    if (matchPatterns.isEmpty()) return false
    return matchPatterns.anyPatternMatches(fingerprint.candidates)
}

private fun CatalogModelVersion.matches(fingerprint: ModelCatalogFingerprint): Boolean {
    if (excludePatterns.anyPatternMatches(fingerprint.candidates)) return false
    if (matchPatterns.isEmpty()) return false
    return matchPatterns.anyPatternMatches(fingerprint.candidates)
}

private fun CatalogModelOverride.matches(fingerprint: ModelCatalogFingerprint): Boolean {
    if (!matchesProviderConstraints(fingerprint)) return false
    if (excludePatterns.anyPatternMatches(fingerprint.candidates)) return false

    val exactCandidates = buildList {
        id.takeIf { it.isNotBlank() }?.let(::add)
        addAll(apiAliases)
    }.map { it.lowercase() }
    val fingerprintCandidates = fingerprint.candidates.map { it.lowercase() }
    if (exactCandidates.any { it in fingerprintCandidates }) return true
    if (matchPatterns.isEmpty()) return false
    return matchPatterns.anyPatternMatches(fingerprint.candidates)
}

private fun CatalogModelOverride.matchesProviderConstraints(fingerprint: ModelCatalogFingerprint): Boolean {
    if (providerIds.isNotEmpty() && fingerprint.providerId !in providerIds) return false
    if (
        providerSlugs.isNotEmpty() &&
        providerSlugs.map { it.normalizeCatalogToken() }.none { it in fingerprint.providerSlugs }
    ) {
        return false
    }
    if (
        baseUrlPatterns.isNotEmpty() &&
        fingerprint.providerBaseUrl?.let { baseUrl ->
            baseUrlPatterns.any { pattern -> baseUrl.matchesCatalogPattern(pattern) }
        } != true
    ) {
        return false
    }
    return true
}

private fun List<String>.anyPatternMatches(candidates: List<String>): Boolean {
    return candidates.any { candidate ->
        any { pattern -> candidate.matchesCatalogPattern(pattern) }
    }
}

private fun String.matchesCatalogPattern(pattern: String): Boolean {
    if (pattern.isBlank()) return false
    return runCatching {
        Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(this)
    }.getOrDefault(false)
}

private fun ProviderSetting.catalogBaseUrl(): String {
    return when (this) {
        is ProviderSetting.Claude -> baseUrl
        is ProviderSetting.Google -> baseUrl
        is ProviderSetting.OpenAI -> baseUrl
        else -> ""
    }
}

private fun ProviderSetting.catalogProviderTokens(): Set<String> {
    return when (this) {
        is ProviderSetting.Claude -> setOf("anthropic", "claude")
        is ProviderSetting.Google -> {
            if (vertexAI) {
                setOf("google", "vertex-ai", "vertex-ai-language-models")
            } else {
                setOf("google", "gemini", "google-ai-studio")
            }
        }
        is ProviderSetting.OpenAI -> {
            val base = baseUrl.lowercase()
            buildSet {
                if ("api.openai.com" in base) add("openai")
                if ("openrouter" in base) add("openrouter")
                if ("github" in base) add("github")
                if ("ollama" in base) add("ollama")
            }
        }
        else -> emptySet()
    }.map { it.normalizeCatalogToken() }.toSet()
}

private fun String.normalizeCatalogToken(): String {
    return trim()
        .lowercase()
        .replace(Regex("\\s+"), "-")
        .replace('_', '-')
        .replace('.', '-')
}

/**
 * Build the matching [ProviderSetting] subclass from this preset. The provider is ALWAYS
 * created disabled ([ProviderSetting.enabled] = false) — 3-layer safety (FR-005): the user
 * must explicitly enable it from the provider detail page. Returns null when [id] is not a
 * valid [Uuid] (those presets are skipped by the merger too).
 *
 * @param apiKey the user-supplied API key; stored only inside the returned setting
 * @param models seed models resolved through the catalog (already resolver-applied)
 */
fun CatalogProvider.toProviderSetting(apiKey: String, models: List<Model>): ProviderSetting? {
    val parsedId = runCatching { Uuid.parse(id) }.getOrNull() ?: return null
    return when (type) {
        CatalogProviderType.OPENAI -> ProviderSetting.OpenAI(
            id = parsedId,
            enabled = false,
            name = name,
            models = models,
            balanceOption = balanceOption,
            builtIn = builtIn,
            apiKey = apiKey,
            baseUrl = baseUrl,
            chatCompletionsPath = chatCompletionsPath,
            useResponseApi = useResponseApi,
        )

        CatalogProviderType.GOOGLE -> ProviderSetting.Google(
            id = parsedId,
            enabled = false,
            name = name,
            models = models,
            balanceOption = balanceOption,
            builtIn = builtIn,
            apiKey = apiKey,
            baseUrl = baseUrl,
        )

        CatalogProviderType.CLAUDE -> ProviderSetting.Claude(
            id = parsedId,
            enabled = false,
            name = name,
            models = models,
            balanceOption = balanceOption,
            builtIn = builtIn,
            apiKey = apiKey,
            baseUrl = baseUrl,
        )
    }
}

/** Canonical-id fallback helper (FR-011): look up an exact entry by a normalized key. */
fun ModelCatalogSnapshot.exactEntryOrNull(key: String): ModelCatalogEntry? {
    return exactEntries[key.lowercase()]
}
