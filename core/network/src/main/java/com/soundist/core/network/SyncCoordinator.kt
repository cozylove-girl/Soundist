package com.soundist.core.network

import kotlin.math.min

class SyncCoordinator(
    private val deviceId: String,
    private val store: SyncLocalStore,
    private val transport: SyncTransport,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
) {
    suspend fun run(): SyncRunResult {
        require(deviceId.isNotBlank()) { "A stable, non-empty deviceId is required for sync" }
        var pushed = 0
        var pulled = 0
        var conflicts = 0
        val pending = store.pendingMutations(retryPolicy.batchSize)
        if (pending.isNotEmpty()) when (val result = transport.push(PushRequest(deviceId, pending))) {
            is TransportResult.Success -> {
                val byId = pending.associateBy { it.operationId }
                val responseIds = result.value.results.map { it.operationId }
                if (responseIds.size != responseIds.distinct().size || responseIds.toSet() != byId.keys) return protocolFailure("push_result_mismatch", "Push response must contain exactly one result for each operation id")
                val acknowledged = mutableSetOf<String>()
                val foundConflicts = mutableListOf<SyncConflict>()
                result.value.results.forEach { item -> when (item.status) {
                    PushStatus.APPLIED, PushStatus.ALREADY_APPLIED -> {
                        if ((item.serverRevision ?: 0) < 1) return protocolFailure("invalid_revision", "Applied mutation omitted a positive server revision")
                        acknowledged += item.operationId
                    }
                    PushStatus.CONFLICT -> {
                        val remote = item.serverChange ?: return protocolFailure("missing_conflict_record", "Conflict response omitted canonical server record")
                        val local = checkNotNull(byId[item.operationId])
                        if (remote.entityType != local.entityType || remote.entityId != local.entityId || remote.revision < 1) return protocolFailure("invalid_conflict_record", "Conflict record does not match the local entity")
                        foundConflicts += SyncConflict(local, remote)
                    }
                    PushStatus.REJECTED -> store.recordRejected(item.operationId, item.errorCode ?: "rejected")
                } }
                store.acknowledge(acknowledged)
                store.recordConflicts(foundConflicts)
                pushed += acknowledged.size
                conflicts += foundConflicts.size
            }
            else -> return terminal(result, 0)
        }

        var cursor = store.currentCursor()
        var pages = 0
        do when (val result = transport.pull(PullRequest(deviceId, cursor, retryPolicy.pullPageSize))) {
            is TransportResult.Success -> {
                if (result.value.nextCursor.isBlank() || result.value.nextCursor == cursor && result.value.hasMore) return protocolFailure("cursor_not_advanced", "Pull cursor did not advance")
                if (result.value.changes.any { it.revision < 1 }) return protocolFailure("invalid_revision", "Remote revision must be positive")
                store.applyRemotePage(result.value.changes, result.value.nextCursor)
                pulled += result.value.changes.size
                cursor = result.value.nextCursor
                pages++
                if (!result.value.hasMore) return SyncRunResult.Completed(pushed, pulled, conflicts)
            }
            else -> return terminal(result, pages)
        } while (pages < retryPolicy.maxPullPages)
        return SyncRunResult.Retry(SyncError(SyncError.Category.SERVER, "pull_page_limit", "More pull pages remain"), retryPolicy.delayFor(0))
    }

    private fun terminal(result: TransportResult<*>, attempt: Int): SyncRunResult = when (result) {
        TransportResult.Disabled -> SyncRunResult.Disabled
        TransportResult.Unauthenticated -> SyncRunResult.Unauthenticated
        is TransportResult.Failure -> if (result.error.retryable) SyncRunResult.Retry(result.error, result.error.retryAfterMillis ?: retryPolicy.delayFor(attempt)) else SyncRunResult.Failed(result.error)
        is TransportResult.Success -> error("Success is handled by the caller")
    }
    private fun protocolFailure(code: String, message: String) = SyncRunResult.Failed(SyncError(SyncError.Category.PROTOCOL, code, message))
}

data class RetryPolicy(
    val batchSize: Int = 50,
    val pullPageSize: Int = 200,
    val maxPullPages: Int = 20,
    val baseDelayMillis: Long = 30_000,
    val maxDelayMillis: Long = 6 * 60 * 60_000L,
) {
    init { require(batchSize in 1..500 && pullPageSize in 1..500 && maxPullPages > 0); require(baseDelayMillis > 0 && maxDelayMillis >= baseDelayMillis) }
    fun delayFor(attempt: Int): Long = min(maxDelayMillis, baseDelayMillis * (1L shl attempt.coerceIn(0, 16)))
}
