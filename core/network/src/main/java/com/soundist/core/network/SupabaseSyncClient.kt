package com.soundist.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.io.IOException

/** Supabase RPC transport. Server functions enforce auth.uid(), RLS, idempotency and revisions. */
class SupabaseSyncClient(
    private val config: SupabaseConfig,
    private val accessToken: () -> String?,
    private val client: HttpClient = defaultClient(),
) : SyncTransport {
    override suspend fun push(request: PushRequest): TransportResult<PushResponse> = execute("sync_push", request)
    override suspend fun pull(request: PullRequest): TransportResult<PullResponse> = execute("sync_pull", request)

    private suspend inline fun <reified Request : Any, reified Response : Any> execute(rpc: String, body: Request): TransportResult<Response> {
        if (!config.enabled) return TransportResult.Disabled
        val token = accessToken()?.takeIf { it.isNotBlank() } ?: return TransportResult.Unauthenticated
        return try {
            val response = client.post("${config.url.trimEnd('/')}/rest/v1/rpc/$rpc") {
                header("apikey", config.anonKey)
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(body)
            }
            response.toResult()
        } catch (_: HttpRequestTimeoutException) {
            TransportResult.Failure(SyncError(SyncError.Category.TIMEOUT, "request_timeout", "Sync request timed out"))
        } catch (error: IOException) {
            TransportResult.Failure(SyncError(SyncError.Category.OFFLINE, "network_io", error.message ?: "Network unavailable"))
        } catch (error: Exception) {
            TransportResult.Failure(SyncError(SyncError.Category.UNKNOWN, "client_exception", error.message ?: error.javaClass.simpleName))
        }
    }

    private suspend inline fun <reified T : Any> HttpResponse.toResult(): TransportResult<T> {
        val statusCode = status.value
        if (statusCode in 200..299) return try { TransportResult.Success(body()) } catch (error: Exception) {
            TransportResult.Failure(SyncError(SyncError.Category.PROTOCOL, "invalid_response", error.message ?: "Invalid sync response"))
        }
        val category = when (statusCode) {
            401, 403 -> SyncError.Category.AUTHORIZATION
            408 -> SyncError.Category.TIMEOUT
            429 -> SyncError.Category.RATE_LIMITED
            in 400..499 -> SyncError.Category.VALIDATION
            in 500..599 -> SyncError.Category.SERVER
            else -> SyncError.Category.UNKNOWN
        }
        val retryAfter = headers[HttpHeaders.RetryAfter]?.toLongOrNull()?.times(1_000)
        return TransportResult.Failure(SyncError(category, "http_$statusCode", "Sync server returned HTTP $statusCode", retryAfter))
    }

    fun close() = client.close()
    companion object {
        private fun defaultClient() = HttpClient(OkHttp) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
        }
    }
}
