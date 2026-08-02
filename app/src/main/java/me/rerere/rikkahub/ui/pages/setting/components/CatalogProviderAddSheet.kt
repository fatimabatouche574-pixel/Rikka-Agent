package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.models.CatalogProvider
import me.rerere.rikkahub.data.ai.models.CatalogProviderType
import me.rerere.rikkahub.data.ai.models.ModelCatalogSnapshot
import me.rerere.rikkahub.data.ai.models.resolveModelEntry
import me.rerere.rikkahub.data.ai.models.toProviderSetting
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.openUrl
import com.dokar.sonner.ToastType

/**
 * Bottom sheet for adding a provider preset from the catalog. Shows the preset's description,
 * base URL, API format and default models, optional sign-up / API-key links, and a single
 * API-key field. Add builds a [ProviderSetting] via [CatalogProvider.toProviderSetting] which is
 * ALWAYS created disabled (FR-005). The API key is only stored inside the returned setting —
 * never written to the catalog file, cache or any remote surface (FR-015).
 */
@Composable
fun CatalogProviderAddSheet(
    provider: CatalogProvider,
    snapshot: ModelCatalogSnapshot?,
    onDismiss: () -> Unit,
    onAdd: (ProviderSetting) -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var apiKey by remember(provider.id) { mutableStateOf("") }
    var adding by remember(provider.id) { mutableStateOf(false) }
    val successMessage = stringResource(R.string.setting_catalog_page_add_success)
    val failureMessage = stringResource(R.string.setting_catalog_page_add_failed)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = provider.displayName(),
            style = MaterialTheme.typography.titleLarge,
        )
        if (provider.description().isNotBlank()) {
            Text(
                text = provider.description(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        HorizontalDivider()

        ProviderPropertyRow(
            label = stringResource(R.string.setting_catalog_page_api_format_label),
            value = provider.apiFormatLabel(),
        )
        ProviderPropertyRow(
            label = stringResource(R.string.setting_catalog_page_base_url_label),
            value = provider.baseUrl,
        )
        if (provider.setupModels.isNotEmpty()) {
            ProviderPropertyRow(
                label = stringResource(R.string.setting_catalog_page_default_models_label),
                value = stringResource(R.string.setting_catalog_page_models_count, provider.setupModels.size),
            )
        }

        // Optional sign-up / API-key links (FR-017, US1-4) — only shown when the preset ships them.
        provider.signupUrl?.let { url ->
            Text(
                text = stringResource(R.string.setting_catalog_page_signup_link),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { context.openUrl(url) },
            )
        }
        provider.apiKeyUrl?.let { url ->
            Text(
                text = stringResource(R.string.setting_catalog_page_api_key_link),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { context.openUrl(url) },
            )
        }

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.setting_catalog_page_api_key_label)) },
            placeholder = { Text(stringResource(R.string.setting_catalog_page_api_key_placeholder)) },
            singleLine = true,
        )

        Text(
            text = stringResource(R.string.setting_catalog_page_disabled_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    if (adding) return@Button
                    adding = true
                    scope.launch {
                        runCatching {
                            val seedModels = provider.toSeedModels(snapshot)
                            provider.toProviderSetting(apiKey = apiKey.trim(), models = seedModels)
                        }.getOrNull()?.let { setting ->
                            onAdd(setting)
                            toaster.show(successMessage, type = ToastType.Success)
                            onDismiss()
                        } ?: run {
                            toaster.show(failureMessage, type = ToastType.Error)
                        }
                        adding = false
                    }
                },
                enabled = apiKey.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                if (adding) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text(stringResource(R.string.setting_catalog_page_add_provider))
            }
        }
    }
}

@Composable
private fun ProviderPropertyRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CatalogProvider.apiFormatLabel(): String = stringResource(
    when (type) {
        CatalogProviderType.OPENAI -> R.string.setting_catalog_page_api_format_openai
        CatalogProviderType.GOOGLE -> R.string.setting_catalog_page_api_format_google
        CatalogProviderType.CLAUDE -> R.string.setting_catalog_page_api_format_claude
    }
)

/**
 * Seed [Model] instances from the preset's `setup_models`, resolving type/modalities/abilities
 * through the catalog snapshot when available and falling back to safe CHAT/TEXT defaults
 * otherwise (FR-010). US1 seeds these at add time; US2's `ModelMetadataResolver` refines them.
 */
private fun CatalogProvider.toSeedModels(snapshot: ModelCatalogSnapshot?): List<Model> {
    if (setupModels.isEmpty()) return emptyList()
    return setupModels.map { modelId ->
        val entry = snapshot?.resolveModelEntry(modelId)
        Model(
            modelId = modelId,
            displayName = modelId,
            type = when (entry?.mode) {
                "embedding" -> ModelType.EMBEDDING
                "image" -> ModelType.IMAGE
                else -> ModelType.CHAT
            },
            inputModalities = entry?.inputModalities?.ifEmpty { listOf(Modality.TEXT) }
                ?: listOf(Modality.TEXT),
            outputModalities = entry?.outputModalities?.ifEmpty { listOf(Modality.TEXT) }
                ?: listOf(Modality.TEXT),
            abilities = buildList {
                if (entry?.supportsFunctionCalling == true) add(ModelAbility.TOOL)
                if (entry?.supportsReasoning == true) add(ModelAbility.REASONING)
            },
        )
    }
}
