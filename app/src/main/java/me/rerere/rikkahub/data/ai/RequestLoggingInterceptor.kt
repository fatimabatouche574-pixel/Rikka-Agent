package me.rerere.rikkahub.data.ai

import me.rerere.common.android.LogEntry
import me.rerere.common.android.Logging
import okhttp3.Interceptor
import okhttp3.Response

class RequestLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!Logging.isRequestLoggingEnabled()) {
            return chain.proceed(chain.request())
        }

        val request = chain.request()
        val startTime = System.currentTimeMillis()

        // These logs are user-exportable diagnostics. Never persist headers, cookies,
        // authorization, query parameters, or request bodies because any of them can
        // contain provider credentials or private conversation content.
        val safeUrl = request.url.newBuilder()
            .query(null)
            .fragment(null)
            .build()
            .toString()

        val response: Response
        var error: String? = null

        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            error = e.javaClass.simpleName
            Logging.logRequest(
                LogEntry.RequestLog(
                    tag = "HTTP",
                    url = safeUrl,
                    method = request.method,
                    error = error
                )
            )
            throw e
        }

        val durationMs = System.currentTimeMillis() - startTime
        Logging.logRequest(
            LogEntry.RequestLog(
                tag = "HTTP",
                url = safeUrl,
                method = request.method,
                responseCode = response.code,
                durationMs = durationMs,
                error = error
            )
        )

        return response
    }
}
