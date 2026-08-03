package me.rerere.rikkahub.data.ai.models

import kotlin.uuid.Uuid
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The single write-path the catalog uses into `Settings.providers` (FR-009 / SC-006).
 *
 * Catalog updates must never alter, remove, duplicate, or reorder a provider the user has
 * already configured (contract `settings-merger-contract.md`, invariants I1–I6):
 *
 * - **Matching ladder** (first match wins, single-claim): stable UUID → normalized
 *   `(type, baseUrl)` → normalized `(type, name)`. `matchedCatalogProviderIds` records
 *   claims so one catalog preset can never be applied to two configured rows and a preset
 *   already claimed is never re-added by the explicit-add path.
 * - **I1** — `enabled`, `apiKey`, `models` (ids, order, per-model corrections), custom
 *   `name`, and list position are preserved byte-for-byte; the catalog contributes only
 *   derived metadata via [ModelMetadataResolver.applyToProvider] with all `preserve*`
 *   flags (I6) and defaults that never override user values.
 * - **I2** — with the default `includeMissingCatalogProviders = false` no provider is added.
 * - **I3** — input ordering is preserved.
 * - **I4** — a preset never produces two configured providers.
 * - **I5** — providers in `settings.deletedBuiltInProviderIds` are never re-seeded.
 *
 * `includeMissingCatalogProviders = true` is used only by the user-driven add flow (US1),
 * which constructs the `ProviderSetting` directly via
 * `CatalogProvider.toProviderSetting(apiKey, models)`; catalog updates never call it.
 */
fun mergeCatalogIntoSettings(
    settings: Settings,
    snapshot: ModelCatalogSnapshot,
    resolver: ModelMetadataResolver,
    includeMissingCatalogProviders: Boolean = false,
): Settings {
    val catalogProvidersById = snapshot.providers
        .mapNotNull { provider -> provider.uuidOrNull()?.let { it to provider } }
        .toMap()
    val catalogProvidersByBaseUrl = snapshot.providers
        .groupBy { provider -> provider.type to provider.baseUrl.normalizedCatalogUrlKey() }
    val catalogProvidersByName = snapshot.providers
        .groupBy { provider -> provider.type to provider.name.normalizedCatalogNameKey() }
    val matchedCatalogProviderIds = mutableSetOf<String>()

    val normalizedExisting = settings.providers.map { provider ->
        val catalogProvider = findCatalogMatch(
            provider = provider,
            catalogProvidersById = catalogProvidersById,
            catalogProvidersByBaseUrl = catalogProvidersByBaseUrl,
            catalogProvidersByName = catalogProvidersByName,
            matchedCatalogProviderIds = matchedCatalogProviderIds,
        )
        if (catalogProvider != null) {
            matchedCatalogProviderIds += catalogProvider.id
        }
        // Non-destructive: this codebase persists no catalog-owned fields on ProviderSetting
        // (icons/cost stay on the snapshot — R5), so layering catalog defaults onto a
        // matched provider is exactly re-resolving derived metadata. Everything else
        // (enabled, apiKey, models, order, identity) is untouched by design.
        resolver.applyToProvider(provider, MERGE_PRESERVE_OPTIONS)
    }

    val existingProviderIds = normalizedExisting.map { it.id }.toSet()
    val missingCatalogProviders = if (includeMissingCatalogProviders) {
        snapshot.providers
            .filter { it.builtIn || it.preset }
            .mapNotNull { catalogProvider ->
                val id = catalogProvider.uuidOrNull() ?: return@mapNotNull null
                if (id in existingProviderIds) return@mapNotNull null
                if (catalogProvider.id in matchedCatalogProviderIds) return@mapNotNull null
                if (id in settings.deletedBuiltInProviderIds) return@mapNotNull null
                catalogProvider.toProviderSetting(apiKey = "", models = emptyList())
            }
    } else {
        emptyList()
    }

    return settings.copy(
        providers = normalizedExisting + missingCatalogProviders.map { provider ->
            resolver.applyToProvider(provider, MERGE_PRESERVE_OPTIONS)
        },
    )
}

/**
 * The merger always re-resolves derived metadata with every `preserve*` flag set (I6) so
 * persisted user values — display name, capabilities, type — win over catalog updates
 * (FR-007 / US4). Never rely on `applyToProvider`'s default here: this is the guarantee.
 */
private val MERGE_PRESERVE_OPTIONS = ModelResolutionOptions(
    preserveDisplayName = true,
    preserveExistingCapabilities = true,
    preserveExistingType = true,
)

/**
 * First-match-wins ladder with single-claim enforcement. Once a catalog provider has been
 * claimed by one configured row it can never be applied to a second (I4), and a candidate
 * whose claim is already taken falls through to the next configured row as unmatched.
 */
private fun findCatalogMatch(
    provider: ProviderSetting,
    catalogProvidersById: Map<Uuid, CatalogProvider>,
    catalogProvidersByBaseUrl: Map<Pair<CatalogProviderType, String>, List<CatalogProvider>>,
    catalogProvidersByName: Map<Pair<CatalogProviderType, String>, List<CatalogProvider>>,
    matchedCatalogProviderIds: MutableSet<String>,
): CatalogProvider? {
    val matchType = provider.matchType ?: return null

    catalogProvidersById[provider.id]?.let { byId ->
        if (byId.id !in matchedCatalogProviderIds) return byId
    }

    catalogProvidersByBaseUrl[matchType to provider.baseUrlForCatalogMatch().normalizedCatalogUrlKey()]
        ?.singleOrNull()
        ?.let { byBaseUrl ->
            if (byBaseUrl.id !in matchedCatalogProviderIds) return byBaseUrl
        }

    catalogProvidersByName[matchType to provider.name.normalizedCatalogNameKey()]
        ?.singleOrNull()
        ?.let { byName ->
            if (byName.id !in matchedCatalogProviderIds) return byName
        }

    return null
}

/** The catalog-preset type this provider would match under, or null for non-catalog rows. */
private val ProviderSetting.matchType: CatalogProviderType?
    get() = when (this) {
        is ProviderSetting.OpenAI -> CatalogProviderType.OPENAI
        is ProviderSetting.Google -> CatalogProviderType.GOOGLE
        is ProviderSetting.Claude -> CatalogProviderType.CLAUDE
        else -> null
    }

private fun ProviderSetting.baseUrlForCatalogMatch(): String {
    return when (this) {
        is ProviderSetting.OpenAI -> baseUrl
        is ProviderSetting.Google -> baseUrl
        is ProviderSetting.Claude -> baseUrl
        else -> ""
    }
}

/**
 * Normalize a catalog-match URL key: scheme + host + trimmed path, lowercased, trailing
 * slashes removed, so `https://EXAMPLE.com/v1/` matches `https://example.com/v1`.
 */
private fun String.normalizedCatalogUrlKey(): String {
    val url = trim().toHttpUrlOrNull()
    if (url != null) {
        val path = url.encodedPath.trimEnd('/').takeUnless { it == "/" }.orEmpty()
        return "${url.scheme}://${url.host}$path".lowercase()
    }
    return trim().lowercase().trimEnd('/')
}

/** Normalize a catalog-match name key: lowercase + whitespace-collapsed. */
private fun String.normalizedCatalogNameKey(): String {
    return trim().lowercase().replace(Regex("\\s+"), " ")
}

private fun CatalogProvider.uuidOrNull(): Uuid? {
    return runCatching { Uuid.parse(id) }.getOrNull()
}
