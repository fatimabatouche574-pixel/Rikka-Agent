package me.rerere.rikkahub.data.ai.models

import android.content.Context
import android.content.ContextWrapper
import java.io.File
import java.nio.file.Files
import java.io.IOException
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.ai.tools.local.NULL_CONTEXT
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val UNSAFE: sun.misc.Unsafe = run {
    val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
    field.isAccessible = true
    field.get(null) as sun.misc.Unsafe
}

private val GHOST_CONTEXT: Context =
    UNSAFE.allocateInstance(ContextWrapper::class.java) as Context

/**
 * Hermetic service backed by a temp filesDir + a fake HTTP seam. Never touches the network
 * and never touches a real Context (the bundled JSON is supplied via [readBundledRaw]).
 */
private class FakeCatalogService(
    context: Context,
    private val tempDir: File,
    private val bundledRaw: String,
    private val downloadResult: () -> String,
) : ModelCatalogService(context, OkHttpClient()) {

    override fun downloadedCatalogFile(): File = File(tempDir, "model_catalog/lastchat_catalog.json")

    override suspend fun readBundledRaw(): String = bundledRaw

    override suspend fun downloadRaw(): String = downloadResult()
}

class ModelCatalogServiceTest {
    private val bundledRaw: String by lazy {
        File("src/main/assets/catalog/lastchat_catalog.json").readText()
    }

    private fun newService(
        tempDir: File,
        download: () -> String = { throw IOException("no network in tests") },
    ) = FakeCatalogService(GHOST_CONTEXT, tempDir, bundledRaw, download)

    @Test
    fun `warmUp loads bundled asset and publishes BUNDLED status with 60+ presets`() = runBlocking {
        val tempDir = Files.createTempDirectory("catalog-test").toFile()
        try {
            val service = newService(tempDir)
            service.warmUp()

            val status = service.status.value
            assertEquals(ModelCatalogSource.BUNDLED, status.source)
            assertTrue("Expected 60+ provider presets, got ${status.providerCount}", status.providerCount >= 60)
            assertTrue(status.entryCount > 0)
            assertNull(status.lastSuccessfulRefreshAt)

            val presets = service.providerPresets.value
            assertTrue("Expected 60+ provider presets, got ${presets.size}", presets.size >= 60)

            val snapshot = service.snapshotFlow.value
            assertNotNull(snapshot)
            assertEquals(snapshot!!.providers.size, presets.size)
            assertEquals(snapshot, service.snapshotOrNull())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `warmUp prefers a valid downloaded catalog over bundled`() = runBlocking {
        val tempDir = Files.createTempDirectory("catalog-test").toFile()
        try {
            val service = newService(tempDir)
            // Simulate a prior successful download (same bundled JSON, still valid).
            val file = File(tempDir, "model_catalog/lastchat_catalog.json")
            file.parentFile.mkdirs()
            file.writeText(bundledRaw)

            service.warmUp()

            assertEquals(ModelCatalogSource.DOWNLOADED, service.status.value.source)
            assertNotNull(service.status.value.lastSuccessfulRefreshAt)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `warmUp falls back to bundled when downloaded catalog is corrupt`() = runBlocking {
        val tempDir = Files.createTempDirectory("catalog-test").toFile()
        try {
            val file = File(tempDir, "model_catalog/lastchat_catalog.json")
            file.parentFile.mkdirs()
            file.writeText("{ this is not valid json !!")

            val service = newService(tempDir)
            service.warmUp()

            assertEquals(ModelCatalogSource.BUNDLED, service.status.value.source)
            assertTrue(service.status.value.providerCount >= 60)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `refreshCatalog success writes downloaded catalog and publishes DOWNLOADED`() = runBlocking {
        val tempDir = Files.createTempDirectory("catalog-test").toFile()
        try {
            val service = newService(tempDir, download = { bundledRaw })
            service.warmUp()
            assertEquals(ModelCatalogSource.BUNDLED, service.status.value.source)

            val refreshed = service.refreshCatalog()

            assertEquals(ModelCatalogSource.DOWNLOADED, refreshed.source)
            assertEquals(ModelCatalogSource.DOWNLOADED, service.status.value.source)
            assertNotNull("lastSuccessfulRefreshAt should be stamped", refreshed.lastSuccessfulRefreshAt)

            val file = File(tempDir, "model_catalog/lastchat_catalog.json")
            assertTrue("Downloaded catalog should be written to filesDir", file.exists())
            assertTrue("Written file should be non-empty", file.length() > 0)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `refreshCatalog non-2xx or blank download silently falls back to bundled`() = runBlocking {
        val tempDir = Files.createTempDirectory("catalog-test").toFile()
        try {
            val service = newService(tempDir, download = { throw IOException("connection refused") })
            service.warmUp()

            // Must NOT throw — a download failure is silent and keeps the last-good catalog.
            val refreshed = service.refreshCatalog()

            assertEquals(ModelCatalogSource.BUNDLED, refreshed.source)
            assertTrue(refreshed.providerCount >= 60)
            val file = File(tempDir, "model_catalog/lastchat_catalog.json")
            assertTrue("No downloaded file should be written on failure", !file.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `refreshCatalog corrupt payload is rejected and bundled stays active`() = runBlocking {
        val tempDir = Files.createTempDirectory("catalog-test").toFile()
        try {
            val service = newService(tempDir, download = { "{ this is not valid json !!" })
            service.warmUp()

            val refreshed = service.refreshCatalog()

            assertEquals(ModelCatalogSource.BUNDLED, refreshed.source)
            assertEquals(ModelCatalogSource.BUNDLED, service.status.value.source)
            assertTrue(service.status.value.providerCount >= 60)
            val file = File(tempDir, "model_catalog/lastchat_catalog.json")
            assertTrue("Corrupt payload must not be written", !file.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `refreshCatalog wrong schema version is rejected`() = runBlocking {
        val tempDir = Files.createTempDirectory("catalog-test").toFile()
        try {
            val wrongSchema = """
                {
                  "schema_version": 99,
                  "updated_at": "2026-07-01",
                  "providers": [{
                    "id": "d5734028-d39b-4d41-9841-fd648d65440e",
                    "name": "FutureProvider",
                    "base_url": "https://future.example.com/v1",
                    "type": "openai"
                  }],
                  "model_families": [],
                  "models": []
                }
            """.trimIndent()
            val service = newService(tempDir, download = { wrongSchema })
            service.warmUp()

            val refreshed = service.refreshCatalog()

            assertEquals(ModelCatalogSource.BUNDLED, refreshed.source)
            assertEquals(ModelCatalogSource.BUNDLED, service.status.value.source)
            val file = File(tempDir, "model_catalog/lastchat_catalog.json")
            assertTrue("Incompatible schema must not be written", !file.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `refreshCatalog failure preserves a prior successful download`() = runBlocking {
        val tempDir = Files.createTempDirectory("catalog-test").toFile()
        try {
            // Prior good download on disk (same bundled JSON, valid).
            val file = File(tempDir, "model_catalog/lastchat_catalog.json")
            file.parentFile.mkdirs()
            file.writeText(bundledRaw)

            val service = newService(tempDir, download = { throw IOException("server down") })
            service.warmUp()
            assertEquals(ModelCatalogSource.DOWNLOADED, service.status.value.source)
            val priorLastRefresh = service.status.value.lastSuccessfulRefreshAt

            val refreshed = service.refreshCatalog()

            assertEquals(ModelCatalogSource.DOWNLOADED, refreshed.source)
            assertEquals(ModelCatalogSource.DOWNLOADED, service.status.value.source)
            assertEquals(priorLastRefresh, service.status.value.lastSuccessfulRefreshAt)
            // The prior download is byte-for-byte intact.
            assertEquals(bundledRaw, file.readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
