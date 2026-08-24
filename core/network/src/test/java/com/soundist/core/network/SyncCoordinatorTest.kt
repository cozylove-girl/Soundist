package com.soundist.core.network

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCoordinatorTest {
    @Test fun pushAcknowledgesThenPullsAndPersistsCursor() = runBlocking {
        val mutation = mutation("op-1")
        val store = FakeStore(mutableListOf(mutation))
        val transport = FakeTransport(
            pushResult = TransportResult.Success(PushResponse(listOf(PushItemResult("op-1", PushStatus.APPLIED, 2)))),
            pulls = ArrayDeque(listOf(TransportResult.Success(PullResponse(listOf(change(2)), "cursor-2", false)))),
        )
        val result = SyncCoordinator("device-1", store, transport).run()
        assertEquals(SyncRunResult.Completed(1, 1, 0), result)
        assertEquals(setOf("op-1"), store.acknowledged)
        assertEquals("cursor-2", store.cursor)
    }

    @Test fun conflictIsRecordedAndNotAcknowledged() = runBlocking {
        val mutation = mutation("op-conflict")
        val store = FakeStore(mutableListOf(mutation))
        val transport = FakeTransport(
            TransportResult.Success(PushResponse(listOf(PushItemResult("op-conflict", PushStatus.CONFLICT, 3, change(3))))),
            ArrayDeque(listOf(TransportResult.Success(PullResponse(emptyList(), "cursor-3", false)))),
        )
        val result = SyncCoordinator("device", store, transport).run()
        assertEquals(SyncRunResult.Completed(0, 0, 1), result)
        assertTrue(store.acknowledged.isEmpty())
        assertEquals(1, store.conflicts.size)
    }

    @Test fun unauthenticatedDoesNotDeleteQueueOrPretendSuccess() = runBlocking {
        val store = FakeStore(mutableListOf(mutation("op")))
        val result = SyncCoordinator("device", store, FakeTransport(TransportResult.Unauthenticated)).run()
        assertEquals(SyncRunResult.Unauthenticated, result)
        assertTrue(store.acknowledged.isEmpty())
        assertEquals(null, store.cursor)
    }

    @Test fun retryableFailureUsesServerRetryAfter() = runBlocking {
        val store = FakeStore(mutableListOf(mutation("op")))
        val error = SyncError(SyncError.Category.RATE_LIMITED, "http_429", "slow down", 75_000)
        val result = SyncCoordinator("device", store, FakeTransport(TransportResult.Failure(error))).run()
        assertEquals(SyncRunResult.Retry(error, 75_000), result)
    }

    @Test fun nonAdvancingCursorIsProtocolFailureAndPageIsNotApplied() = runBlocking {
        val store = FakeStore(cursor = "same")
        val transport = FakeTransport(pulls = ArrayDeque(listOf(TransportResult.Success(PullResponse(listOf(change(2)), "same", true)))))
        val result = SyncCoordinator("device", store, transport).run()
        assertTrue(result is SyncRunResult.Failed && result.error.code == "cursor_not_advanced")
        assertTrue(store.applied.isEmpty())
    }

    @Test fun retryPolicyIsExponentialAndCapped() {
        val policy = RetryPolicy(baseDelayMillis = 1_000, maxDelayMillis = 8_000)
        assertEquals(1_000, policy.delayFor(0))
        assertEquals(4_000, policy.delayFor(2))
        assertEquals(8_000, policy.delayFor(20))
    }

    @Test fun incompletePushResponseCannotBeReportedAsCompleted() = runBlocking {
        val store = FakeStore(mutableListOf(mutation("one"), mutation("two")))
        val transport = FakeTransport(TransportResult.Success(PushResponse(listOf(PushItemResult("one", PushStatus.APPLIED, 2)))))
        val result = SyncCoordinator("device", store, transport).run()
        assertTrue(result is SyncRunResult.Failed && result.error.code == "push_result_mismatch")
        assertTrue(store.acknowledged.isEmpty())
    }

    private fun mutation(id: String) = SyncMutation(id, "todo", "todo-1", MutationOperation.UPSERT, 1, buildJsonObject { put("title", "work") }, 100)
    private fun change(revision: Long) = RemoteChange("todo", "todo-1", revision, false, buildJsonObject { put("title", "server") }, 200)

    private class FakeTransport(
        private val pushResult: TransportResult<PushResponse> = TransportResult.Success(PushResponse(emptyList())),
        private val pulls: ArrayDeque<TransportResult<PullResponse>> = ArrayDeque(listOf(TransportResult.Success(PullResponse(emptyList(), "cursor", false)))),
    ) : SyncTransport {
        override suspend fun push(request: PushRequest) = pushResult
        override suspend fun pull(request: PullRequest) = pulls.removeFirst()
    }

    private class FakeStore(
        private val pending: MutableList<SyncMutation> = mutableListOf(),
        var cursor: String? = null,
    ) : SyncLocalStore {
        val acknowledged = mutableSetOf<String>()
        val conflicts = mutableListOf<SyncConflict>()
        val applied = mutableListOf<RemoteChange>()
        override suspend fun pendingMutations(limit: Int) = pending.take(limit)
        override suspend fun currentCursor() = cursor
        override suspend fun acknowledge(operationIds: Set<String>) { acknowledged += operationIds }
        override suspend fun recordConflicts(conflicts: List<SyncConflict>) { this.conflicts += conflicts }
        override suspend fun recordRejected(operationId: String, code: String) = Unit
        override suspend fun applyRemotePage(changes: List<RemoteChange>, nextCursor: String) { applied += changes; cursor = nextCursor }
    }
}
