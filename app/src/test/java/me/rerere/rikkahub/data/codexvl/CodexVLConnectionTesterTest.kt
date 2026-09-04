package me.rerere.rikkahub.data.codexvl

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class CodexVLConnectionTesterTest {
    private val tester = CodexVLConnectionTester(OkHttpClient(), Json)

    @Test
    fun `responses endpoint preserves provider path without adding v1`() {
        assertEquals(
            "https://provider.example/codex/responses",
            tester.responsesEndpoint("https://provider.example/codex").toString(),
        )
        assertEquals(
            "https://provider.example/v1/responses",
            tester.responsesEndpoint("https://provider.example/v1/").toString(),
        )
    }

    @Test
    fun `maps provider status codes and response validation`() {
        assertFailure(401, "{}", CodexVLConnectionTester.Error.AUTHENTICATION_FAILED)
        assertFailure(403, "{}", CodexVLConnectionTester.Error.ACCESS_DENIED)
        assertFailure(404, "{}", CodexVLConnectionTester.Error.ENDPOINT_OR_MODEL_NOT_FOUND)
        assertFailure(408, "{}", CodexVLConnectionTester.Error.TIMEOUT)
        assertFailure(429, "{}", CodexVLConnectionTester.Error.RATE_LIMITED)
        assertFailure(503, "{}", CodexVLConnectionTester.Error.PROVIDER_ERROR)
        assertFailure(200, "not-json", CodexVLConnectionTester.Error.INVALID_JSON)
        assertFailure(400, "{\"error\":\"model unavailable\"}", CodexVLConnectionTester.Error.MODEL_UNAVAILABLE)
        assertEquals(CodexVLConnectionTester.Result.Success(200), tester.responseResult(200, "{}"))
    }

    private fun assertFailure(status: Int, body: String, error: CodexVLConnectionTester.Error) {
        assertEquals(CodexVLConnectionTester.Result.Failure(error, status), tester.responseResult(status, body))
    }
}
