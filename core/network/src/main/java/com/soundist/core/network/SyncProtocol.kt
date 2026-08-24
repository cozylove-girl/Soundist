package com.soundist.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable enum class MutationOperation { UPSERT, DELETE }

@Serializable data class SyncMutation(
    val operationId: String,
    val entityType: String,
    val entityId: String,
    val operation: MutationOperation,
    val baseRevision: Long?,
    val payload: JsonElement? = null,
    val clientUpdatedAtEpochMillis: Long,
)

@Serializable data class RemoteChange(
    val entityType: String,
    val entityId: String,
    val revision: Long,
    val deleted: Boolean,
    val payload: JsonElement? = null,
    val serverUpdatedAtEpochMillis: Long,
)

@Serializable data class PushRequest(val deviceId: String, val mutations: List<SyncMutation>)
@Serializable data class PushItemResult(
    val operationId: String,
    val status: PushStatus,
    val serverRevision: Long? = null,
    val serverChange: RemoteChange? = null,
    val errorCode: String? = null,
)
@Serializable enum class PushStatus { APPLIED, ALREADY_APPLIED, CONFLICT, REJECTED }
@Serializable data class PushResponse(val results: List<PushItemResult>)
@Serializable data class PullRequest(val deviceId: String, val cursor: String? = null, val limit: Int = 200)
@Serializable data class PullResponse(val changes: List<RemoteChange>, val nextCursor: String, val hasMore: Boolean)

sealed interface TransportResult<out T> {
    data class Success<T>(val value: T) : TransportResult<T>
    data object Disabled : TransportResult<Nothing>
    data object Unauthenticated : TransportResult<Nothing>
    data class Failure(val error: SyncError) : TransportResult<Nothing>
}

data class SyncError(
    val category: Category,
    val code: String,
    val message: String,
    val retryAfterMillis: Long? = null,
) {
    enum class Category { OFFLINE, TIMEOUT, RATE_LIMITED, SERVER, AUTHORIZATION, VALIDATION, PROTOCOL, UNKNOWN }
    val retryable: Boolean get() = category in setOf(Category.OFFLINE, Category.TIMEOUT, Category.RATE_LIMITED, Category.SERVER)
}

interface SyncTransport {
    suspend fun push(request: PushRequest): TransportResult<PushResponse>
    suspend fun pull(request: PullRequest): TransportResult<PullResponse>
}

/** Implemented by core:database. Cursor advancement and remote application must be one transaction. */
interface SyncLocalStore {
    suspend fun pendingMutations(limit: Int): List<SyncMutation>
    suspend fun currentCursor(): String?
    suspend fun acknowledge(operationIds: Set<String>)
    suspend fun recordConflicts(conflicts: List<SyncConflict>)
    suspend fun recordRejected(operationId: String, code: String)
    suspend fun applyRemotePage(changes: List<RemoteChange>, nextCursor: String)
}

data class SyncConflict(val local: SyncMutation, val remote: RemoteChange)

sealed interface SyncRunResult {
    data class Completed(val pushed: Int, val pulled: Int, val conflicts: Int) : SyncRunResult
    data object Disabled : SyncRunResult
    data object Unauthenticated : SyncRunResult
    data class Retry(val error: SyncError, val delayMillis: Long) : SyncRunResult
    data class Failed(val error: SyncError) : SyncRunResult
}
