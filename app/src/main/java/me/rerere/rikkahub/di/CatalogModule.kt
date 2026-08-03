package me.rerere.rikkahub.di

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.models.ModelCatalogService
import me.rerere.rikkahub.data.ai.models.ModelMetadataResolver
import okhttp3.OkHttpClient
import org.koin.dsl.module

val catalogModule = module {
    single<ModelCatalogService> {
        ModelCatalogService(
            context = get(),
            httpClient = get(),
        )
    }

    single<ModelMetadataResolver> {
        val service = get<ModelCatalogService>()
        ModelMetadataResolver(snapshotProvider = service::snapshotOrNull)
    }
}

/**
 * [CatalogRefreshWorker] (US3) is a `KoinComponent` CoroutineWorker — it is discovered by
 * WorkManager through the `koin-androidx-workmanager` worker factory (`workManagerFactory()`
 * in `RikkaHubApp.onCreate`), same as `CronJobWorker` / `TelegramBotHealthWorker`, and needs
 * no explicit Koin registration. Its 24h periodic schedule is enqueued from app start
 * (`RikkaHubApp.onCreate` → `CatalogRefreshWorker.schedule`).
 */
