package me.rerere.rikkahub.di

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.models.ModelCatalogService
import okhttp3.OkHttpClient
import org.koin.dsl.module

val catalogModule = module {
    single<ModelCatalogService> {
        ModelCatalogService(
            context = get(),
            httpClient = get(),
        )
    }
}
