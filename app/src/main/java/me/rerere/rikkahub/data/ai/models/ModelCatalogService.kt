package me.rerere.rikkahub.data.ai.models

import android.content.Context
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.common.http.await
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "ModelCatalogService"
private const val MODEL_CATALOG_DIR_NAME = "model_catalog"
private const val MODEL_CATALOG_FILE_NAME = "lastchat_catalog.json"
private const val MODEL_CATALOG_ASSET_NAME = "catalog/lastchat_catalog.json"

/** The only catalog schema version this build accepts at refresh time (incompatible → rejected). */
const val SUPPORTED_CATALOG_SCHEMA_VERSION = 2

/**
 * Update endpoint (R6). The file is published on the repo-owned fork; until it exists the
 * download is a silent no-op and the bundled asset stays authoritative.
 */
const val MODEL_CATALOG_URL =
    "https://raw.githubusercontent.com/udin-petot/Rikka-Agent/master/catalog/lastchat_catalog.json"

enum class ModelCatalogSource {
    BUNDLED,
    DOWNLOADED,
}

data class ModelCatalogStatus(
    val source: ModelCatalogSource = ModelCatalogSource.BUNDLED,
    val entryCount: Int = 0,
    val providerCount: Int = 0,
    val lastSuccessfulRefreshAt: Long? = null,
    val isRefreshing: Boolean = false,
)

private data class LoadedCatalog(
    val snapshot: ModelCatalogSnapshot,
    val source: ModelCatalogSource,
    val lastSuccessfulRefreshAt: Long?,
)

/**
 * Catalog loader/refresher bound to the existing OkHttp single + `context.filesDir`.
 *
 * The `internal open` seams exist so JVM tests can inject a temp-file store + a fake HTTP
 * layer while passing the ghost [NULL_CONTEXT] — the real Context-backed implementations
 * are only reached through the overridable hooks.
 */
open class ModelCatalogService(
    private val context: Context,
    private val httpClient: OkHttpClient,
) {
    @Volatile
    private var snapshot: ModelCatalogSnapshot? = null
    private val loadMutex = Mutex()
    private val _status = MutableStateFlow(ModelCatalogStatus())
    private val _providerPresets = MutableStateFlow<List<CatalogProvider>>(emptyList())
    private val _snapshotFlow = MutableStateFlow<ModelCatalogSnapshot?>(null)

    val status: StateFlow<ModelCatalogStatus> = _status.asStateFlow()
    val providerPresets: StateFlow<List<CatalogProvider>> = _providerPresets.asStateFlow()
    val snapshotFlow: StateFlow<ModelCatalogSnapshot?> = _snapshotFlow.asStateFlow()

    fun snapshotOrNull(): ModelCatalogSnapshot? = snapshot

    suspend fun warmUp() {
        loadCatalogIfNeeded(forceReload = false)
    }

    suspend fun refreshCatalog(): ModelCatalogStatus {
        _status.value = _status.value.copy(isRefreshing = true)
        return try {
            val rawJson = downloadRaw()
            val snapshot = ModelCatalogParser.parse(rawJson)
            // Reject anything that did not parse into a usable shape (FR-009 / SC-008) —
            // a corrupt download must never replace the active catalog.
            if (snapshot.providers.isEmpty() && snapshot.exactEntries.isEmpty()) {
                throw IOException("Downloaded catalog parsed to an empty snapshot")
            }
            if (snapshot.schemaVersion != SUPPORTED_CATALOG_SCHEMA_VERSION) {
                throw IOException(
                    "Unsupported catalog schema version ${snapshot.schemaVersion} " +
                        "(expected $SUPPORTED_CATALOG_SCHEMA_VERSION)",
                )
            }
            writeDownloadedCatalog(rawJson)
            loadCatalogIfNeeded(forceReload = true)
        } catch (t: Throwable) {
            runCatching { android.util.Log.w(TAG, "Catalog refresh failed; keeping last-good", t) }
            loadCatalogIfNeeded(forceReload = false)
        } finally {
            _status.value = _status.value.copy(isRefreshing = false)
        }
    }

    private suspend fun loadCatalogIfNeeded(forceReload: Boolean): ModelCatalogStatus {
        if (!forceReload) {
            snapshot?.let { existing ->
                if (_status.value.entryCount == existing.exactEntries.size) {
                    return _status.value
                }
            }
        }

        return loadMutex.withLock {
            if (!forceReload) {
                snapshot?.let { existing ->
                    if (_status.value.entryCount == existing.exactEntries.size) {
                        return@withLock _status.value
                    }
                }
            }

            val loadedCatalog = readActiveCatalog()
            snapshot = loadedCatalog.snapshot
            _snapshotFlow.value = loadedCatalog.snapshot
            _providerPresets.value = loadedCatalog.snapshot.providers.filter { it.preset }
            val nextStatus = ModelCatalogStatus(
                source = loadedCatalog.source,
                entryCount = loadedCatalog.snapshot.exactEntries.size,
                providerCount = loadedCatalog.snapshot.providers.size,
                lastSuccessfulRefreshAt = loadedCatalog.lastSuccessfulRefreshAt,
                isRefreshing = _status.value.isRefreshing,
            )
            _status.value = nextStatus
            nextStatus
        }
    }

    private suspend fun readActiveCatalog(): LoadedCatalog {
        readDownloadedCatalogOrNull()?.let { return it }
        return readBundledCatalog()
    }

    private suspend fun readDownloadedCatalogOrNull(): LoadedCatalog? {
        val file = downloadedCatalogFile()
        val rawJson = runCatching {
            file.takeIf { it.exists() }?.readText()
        }.getOrNull() ?: return null

        return runCatching {
            val snapshot = ModelCatalogParser.parse(rawJson)
            // A download that parses to an empty snapshot is treated as corrupt (e.g. a
            // truncated/garbled payload that `parse` tolerated into an empty shape) — it must
            // never silently blank the browser (FR-009 / SC-008). The bundled asset is kept.
            if (snapshot.providers.isEmpty() && snapshot.exactEntries.isEmpty()) {
                throw IOException("Downloaded catalog parsed to an empty snapshot")
            }
            LoadedCatalog(
                snapshot = snapshot,
                source = ModelCatalogSource.DOWNLOADED,
                lastSuccessfulRefreshAt = runCatching { file.lastModified() }.getOrNull(),
            )
        }.onFailure {
            runCatching { android.util.Log.w(TAG, "Downloaded catalog invalid; falling back to bundled", it) }
        }.getOrNull()
    }

    private suspend fun readBundledCatalog(): LoadedCatalog {
        val rawJson = readBundledRaw()
        return LoadedCatalog(
            snapshot = ModelCatalogParser.parse(rawJson),
            source = ModelCatalogSource.BUNDLED,
            lastSuccessfulRefreshAt = null,
        )
    }

    private suspend fun downloadCatalogJson(): String {
        val request = Request.Builder()
            .url(MODEL_CATALOG_URL)
            .get()
            .build()
        return withContext(Dispatchers.IO) {
            httpClient.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Failed to download catalog: ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    throw IOException("Downloaded catalog was empty")
                }
                body
            }
        }
    }

    /**
     * Atomically write the validated catalog: write to a sibling temp file first, then
     * rename over the target. A crash mid-write can never leave a truncated file that would
     * mask the last-good catalog. Falls back to a direct write if rename is unsupported.
     */
    private suspend fun writeDownloadedCatalog(rawJson: String) {
        withContext(Dispatchers.IO) {
            val file = downloadedCatalogFile()
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(rawJson)
            if (!temp.renameTo(file)) {
                file.writeText(rawJson)
                temp.delete()
            }
        }
    }

    // ---- seams (JVM tests override these; production uses the Context defaults) ----
    /** Absolute path of the downloaded catalog file under `filesDir`. */
    internal open fun downloadedCatalogFile(): File =
        File(context.filesDir, "$MODEL_CATALOG_DIR_NAME/$MODEL_CATALOG_FILE_NAME")

    /** Raw bundled catalog JSON, read from assets. */
    internal open suspend fun readBundledRaw(): String = withContext(Dispatchers.IO) {
        context.assets.open(MODEL_CATALOG_ASSET_NAME)
            .bufferedReader()
            .use { it.readText() }
    }

    /** Download the catalog JSON over HTTP (GET [MODEL_CATALOG_URL]). */
    internal open suspend fun downloadRaw(): String = downloadCatalogJson()
}
