# Soundist cloud sync contract

## Product requirements found in repository documents

`prototypes/mobile-interactive/BACKEND_AND_NATIVE_REQUIREMENTS.md` defines optional authenticated, local-first cloud sync; stable device identity; incremental push/pull; idempotent operation replay; revisions and conflict handling; tombstones; RLS; device revocation; private attachment storage; account export/deletion; and offline operation. The Android application remains fully usable with local storage when cloud sync is disabled.

This module now implements the transport and orchestration contract for bidirectional entity sync. Attachment upload, authentication UI, account deletion/export jobs, and database persistence remain separate integrations.

## Wire protocol

- `POST /rest/v1/rpc/sync_push` accepts `PushRequest`.
- `POST /rest/v1/rpc/sync_pull` accepts `PullRequest`.
- Every mutation has a globally unique, durable `operationId` and optional `baseRevision`.
- The server stores processed operation IDs per user so replay returns `ALREADY_APPLIED`.
- Updates use compare-and-swap against `baseRevision`. A mismatch returns `CONFLICT` plus the canonical server record; it must never silently overwrite.
- Deletions are versioned tombstones and therefore participate in pull.
- Pull cursors are opaque, per-user watermarks. Ordering must be deterministic by `(serverUpdatedAt, entityType, entityId, revision)`.
- `auth.uid()` owns every row. The client uses only the public anon key plus the current user JWT; a service-role key must never ship in the APK.

## Required `core:database` bridge

Implement `SyncLocalStore` with these atomic guarantees:

1. `pendingMutations` returns durable queue records containing operation ID, entity ID/type, operation, serialized payload, base revision and client timestamp. Current `SyncQueueEntity` lacks `operationId` and `baseRevision`; add both through a non-destructive Room migration.
2. `acknowledge` removes only server-acknowledged operation IDs.
3. `recordConflicts` persists both local and remote copies for explicit domain/UI resolution; it must not delete the local mutation.
4. `recordRejected` marks a mutation permanently rejected with its structured error; it must not enter an endless retry loop.
5. `applyRemotePage(changes, nextCursor)` applies revision-monotonic upserts/tombstones and advances the cursor in the same Room transaction. Never advance a cursor if any entity write fails.
6. Store one cursor per authenticated user, not globally, and clear/switch it atomically when accounts change.

## Required application wiring

- Provide a stable app-private device ID and a live auth-token provider.
- Add a WorkManager adapter in the application layer only after a production `SyncLocalStore`, stable device ID and live token provider exist. The worker must map `Completed` to success, `Retry` to retry, and every other result to failure.
- Treat `Disabled` and `Unauthenticated` as user-visible inactive states; they are deliberately worker failures, not successful sync.
- Schedule constrained WorkManager runs plus foreground/login/network-change triggers. Offline writes remain local and queued.
- Server SQL/RPC, RLS cross-user tests, token refresh, device revocation and account deletion/export must be deployed and integration-tested before cloud sync is advertised as available.
