package com.soundist.core.database

import androidx.room.withTransaction
import com.soundist.core.network.MutationOperation
import com.soundist.core.network.RemoteChange
import com.soundist.core.network.SyncConflict
import com.soundist.core.network.SyncLocalStore
import com.soundist.core.network.SyncMutation
import kotlinx.serialization.json.Json

/** Production bridge between the network sync protocol and Room. */
class RoomSyncStore(
    private val db: SoundistDatabase,
    private val userId: String,
    private val remoteApplier: RemoteEntityApplier,
    private val clock: () -> Long = System::currentTimeMillis,
) : SyncLocalStore {
    init { require(userId.isNotBlank()) }

    override suspend fun pendingMutations(limit: Int): List<SyncMutation> =
        db.sync().pending(clock(), limit).map { row ->
            SyncMutation(
                operationId = row.operationId,
                entityType = row.entityType,
                entityId = row.entityId,
                operation = MutationOperation.valueOf(row.operation),
                baseRevision = row.baseRevision,
                payload = row.payload?.let(Json::parseToJsonElement),
                clientUpdatedAtEpochMillis = row.clientUpdatedAt,
            )
        }

    override suspend fun currentCursor(): String? = db.sync().cursor(userId)?.cursor
    override suspend fun acknowledge(operationIds: Set<String>) { if (operationIds.isNotEmpty()) db.sync().acknowledge(operationIds) }
    override suspend fun recordRejected(operationId: String, code: String) = db.sync().reject(operationId, code)
    override suspend fun recordConflicts(conflicts: List<SyncConflict>) {
        if (conflicts.isEmpty()) return
        db.sync().saveConflicts(conflicts.map {
            SyncConflictEntity(it.local.operationId, userId, it.local.entityType, it.local.entityId, it.local.payload?.toString(), it.remote.payload?.toString(), it.local.baseRevision, it.remote.revision, it.remote.deleted, clock())
        })
    }

    override suspend fun applyRemotePage(changes: List<RemoteChange>, nextCursor: String) = db.withTransaction {
        changes.forEach { change ->
            val current = db.sync().revision(change.entityType, change.entityId)
            if (current != null && change.revision <= current.revision) return@forEach
            remoteApplier.apply(change)
            db.sync().saveRevision(SyncRevisionEntity(change.entityType, change.entityId, change.revision, change.deleted, change.serverUpdatedAtEpochMillis))
        }
        db.sync().saveCursor(SyncCursorEntity(userId, nextCursor, clock()))
    }

}

/** Domain decoder/upserter. Called inside the same Room transaction as revision and cursor writes. */
fun interface RemoteEntityApplier { suspend fun apply(change: RemoteChange) }
