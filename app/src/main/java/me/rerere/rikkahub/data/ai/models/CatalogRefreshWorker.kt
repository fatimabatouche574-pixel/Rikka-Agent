package me.rerere.rikkahub.data.ai.models

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val TAG = "CatalogRefreshWorker"
private const val CATALOG_REFRESH_WORK_NAME = "catalog_refresh"
private const val CATALOG_REFRESH_INTERVAL_HOURS = 24L

/**
 * Periodic catalog refresh (US3 / FR-008). Fetches a newer catalog from
 * [MODEL_CATALOG_URL], validates it and, on success, atomically replaces the downloaded
 * copy under `filesDir/model_catalog/` — all inside [ModelCatalogService.refreshCatalog].
 *
 * Failures are non-fatal: `refreshCatalog()` keeps the last-good catalog and this worker
 * returns success so the next 24h period retries naturally. The worker is Koin-injected
 * (same pattern as [me.rerere.rikkahub.service.CronJobWorker]) and resolved by the
 * `koin-androidx-workmanager` worker factory.
 */
class CatalogRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val catalogService: ModelCatalogService by inject()

    override suspend fun doWork(): Result {
        return runCatching {
            catalogService.refreshCatalog()
        }.onFailure {
            Log.w(TAG, "doWork: refresh failed; keeping last-good catalog", it)
        }.let {
            // Non-fatal: a failed refresh must not burn the worker's retry budget or surface
            // an error to the user — the next period retries.
            Result.success()
        }
    }

    companion object {
        /**
         * Schedule (or no-op-keep) the daily periodic refresh. Idempotent — repeated calls
         * from app start and boot receivers converge on a single registered worker thanks to
         * [ExistingPeriodicWorkPolicy.KEEP].
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CatalogRefreshWorker>(
                CATALOG_REFRESH_INTERVAL_HOURS,
                TimeUnit.HOURS,
            ).build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    CATALOG_REFRESH_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }.onFailure {
                Log.w(TAG, "schedule: periodic work enqueue failed", it)
            }
        }
    }
}
