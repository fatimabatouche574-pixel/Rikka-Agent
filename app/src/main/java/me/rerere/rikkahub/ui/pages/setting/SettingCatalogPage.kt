package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.models.CatalogProvider
import me.rerere.rikkahub.data.ai.models.CatalogProviderType
import me.rerere.rikkahub.data.ai.models.ModelCatalogService
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.pages.setting.components.CatalogProviderAddSheet
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Searchable catalog browser. Renders 60+ provider presets from the [ModelCatalogService] flows,
 * offline-first (bundled asset, Coil-rendered icons from the local asset). Tapping a row opens
 * the add sheet; a status badge + manual refresh slot show BUNDLED vs DOWNLOADED.
 */
@Composable
fun SettingCatalogPage(vm: SettingVM = koinViewModel()) {
    val service = koinInject<ModelCatalogService>()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val presets by service.providerPresets.collectAsStateWithLifecycle()
    val status by service.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var searchQuery by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf<CatalogProvider?>(null) }
    val lazyListState = rememberLazyListState()

    val filteredPresets = remember(presets, searchQuery) {
        if (searchQuery.isBlank()) {
            presets
        } else {
            presets.filter { provider ->
                provider.name.contains(searchQuery, ignoreCase = true) ||
                    provider.description().contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_catalog_page_title)) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                runCatching { service.refreshCatalog() }
                            }
                        }
                    ) {
                        if (status.isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(
                                imageVector = HugeIcons.Refresh01,
                                contentDescription = null,
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.setting_catalog_page_search_placeholder)) },
                leadingIcon = {
                    Icon(HugeIcons.Search01, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(HugeIcons.Cancel01, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = lazyListState,
            ) {
                items(filteredPresets, key = { it.id }) { provider ->
                    CatalogProviderItem(
                        provider = provider,
                        onClick = { selectedProvider = provider },
                    )
                }
            }

            CatalogStatusBadge(
                isDownloaded = status.source == me.rerere.rikkahub.data.ai.models.ModelCatalogSource.DOWNLOADED,
                providerCount = status.providerCount,
            )
        }
    }

    selectedProvider?.let { provider ->
        ModalBottomSheet(
            onDismissRequest = { selectedProvider = null },
        ) {
            CatalogProviderAddSheet(
                provider = provider,
                snapshot = service.snapshotOrNull(),
                onDismiss = { selectedProvider = null },
                onAdd = { setting ->
                    vm.updateSettings(
                        settings.copy(providers = listOf(setting) + settings.providers)
                    )
                    selectedProvider = null
                },
            )
        }
    }
}

@Composable
private fun CatalogProviderItem(
    provider: CatalogProvider,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = CustomColors.listItemColors.containerColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CatalogProviderIcon(
                provider = provider,
                modifier = Modifier.size(40.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = provider.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.7f)) {
                        provider.description()
                    }
                }
            }
            Tag(type = TagType.INFO) {
                Text(provider.apiFormatLabel())
            }
        }
    }
}

@Composable
private fun CatalogProviderIcon(
    provider: CatalogProvider,
    modifier: Modifier = Modifier,
) {
    val iconPath = provider.icon
    if (iconPath.isNullOrBlank()) {
        AutoAIIcon(
            name = provider.name,
            modifier = modifier,
        )
        return
    }
    // Offline-first: bundled assets live under assets/catalog/. Load the local asset when the
    // icon is a catalog-relative path.
    val assetUri = "file:///android_asset/catalog/${iconPath.trimStart('/')}"
    AsyncImage(
        model = assetUri,
        contentDescription = provider.name,
        modifier = modifier.clip(CircleShape),
    )
}

@Composable
private fun CatalogStatusBadge(isDownloaded: Boolean, providerCount: Int) {
    val label = if (isDownloaded) {
        stringResource(R.string.setting_catalog_page_using_downloaded)
    } else {
        stringResource(R.string.setting_catalog_page_using_bundled)
    }
    ProvideTextStyle(MaterialTheme.typography.labelSmall) {
        CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.6f)) {
            Text(
                text = stringResource(R.string.setting_catalog_page_provider_count, providerCount, label),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
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
