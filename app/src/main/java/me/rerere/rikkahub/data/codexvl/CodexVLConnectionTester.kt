package me.rerere.rikkahub.data.codexvl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.rerere.common.http.await
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class CodexVLConnectionTester(
    private val client: OkHttpClient,
    private val json: Json,
) {
    suspend fun test(config: CodexVLProviderConfig, apiKey: String): Result = withContext(Dispatchers.IO) {
        when (config.validate()) {
            CodexVLProviderConfig.Validation.INVALID_BASE_URL ->
                return@withContext Result.Failure(Error.INVALID_URL)
            CodexVLProviderConfig.Validation.INVALID_MODEL ->
                return@withContext Result.Failure(Error.MODEL_UNAVAILABLE)
            else -> Unit
        }
        if (apiKey.isBlank()) return@withContext Result.Failure(Error.MISSING_API_KEY)
        val endpoint = responsesEndpoint(config.baseUrl)
        val body = buildJsonObject {
            put("model", config.model)
            put("input", "Reply with OK.")
            put("max_output_tokens", 8)
            put("stream", false)
        }
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(json.encodeToString(body).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        try {
            client.newCall(request).await().use { response ->
                val responseBody = response.body.string()
                responseResult(response.code, responseBody)
            }
        } catch (_: SocketTimeoutException) {
            Result.Failure(Error.TIMEOUT)
        } catch (_: UnknownHostException) {
            Result.Failure(Error.DNS_FAILURE)
        } catch (_: SSLException) {
            Result.Failure(Error.TLS_FAILURE)
        } catch (_: Exception) {
            Result.Failure(Error.NETWORK_FAILURE)
        }
    }

    internal fun responsesEndpoint(baseUrl: String) = baseUrl.trimEnd('/').toHttpUrl().newBuilder()
        .addPathSegment("responses")
        .build()

    internal fun responseResult(status: Int, body: String): Result = when (status) {
        in 200..299 -> {
            val valid = runCatching { json.parseToJsonElement(body).jsonObject }.isSuccess
            if (valid) Result.Success(status) else Result.Failure(Error.INVALID_JSON, status)
        }
        400 -> Result.Failure(classifyBadRequest(body), status)
        401 -> Result.Failure(Error.AUTHENTICATION_FAILED, status)
        403 -> Result.Failure(Error.ACCESS_DENIED, status)
        404 -> Result.Failure(Error.ENDPOINT_OR_MODEL_NOT_FOUND, status)
        408 -> Result.Failure(Error.TIMEOUT, status)
        429 -> Result.Failure(Error.RATE_LIMITED, status)
        in 500..599 -> Result.Failure(Error.PROVIDER_ERROR, status)
        else -> Result.Failure(Error.HTTP_ERROR, status)
    }

    private fun classifyBadRequest(body: String): Error {
        val safe = body.lowercase().take(4_096)
        return if ("model" in safe && ("not found" in safe || "unavailable" in safe || "invalid" in safe)) {
            Error.MODEL_UNAVAILABLE
        } else {
            Error.BAD_REQUEST
        }
    }

    sealed class Result {
        data class Success(val httpStatus: Int) : Result()
        data class Failure(val error: Error, val httpStatus: Int? = null) : Result()
    }

    enum class Error {
        INVALID_URL,
        MISSING_API_KEY,
        AUTHENTICATION_FAILED,
        ACCESS_DENIED,
        ENDPOINT_OR_MODEL_NOT_FOUND,
        MODEL_UNAVAILABLE,
        RATE_LIMITED,
        PROVIDER_ERROR,
        TIMEOUT,
        DNS_FAILURE,
        TLS_FAILURE,
        INVALID_JSON,
        MALFORMED_STREAM,
        BAD_REQUEST,
        HTTP_ERROR,
        NETWORK_FAILURE,
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
