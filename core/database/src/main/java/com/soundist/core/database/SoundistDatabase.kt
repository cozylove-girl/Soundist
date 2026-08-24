package com.soundist.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SoundEntity::class, PresetEntity::class, RadioEntity::class, PlaybackEntity::class,
        FocusSessionEntity::class, TodoEntity::class, PlanEntity::class, HabitEntity::class,
        HabitCheckEntity::class, CountdownEntity::class, SleepRoutineEntity::class,
        SleepSessionEntity::class, NotebookEntity::class, NoteEntity::class, NoteBlockEntity::class,
        AttachmentEntity::class, PlaybackEventEntity::class, SyncQueueEntity::class,
        SyncConflictEntity::class, SyncCursorEntity::class, SyncRevisionEntity::class,
        CustomRadioFileEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
abstract class SoundistDatabase : RoomDatabase() {
    abstract fun sounds(): SoundDao
    abstract fun listening(): ListeningDao
    abstract fun productivity(): ProductivityDao
    abstract fun notes(): NotesDao
    abstract fun records(): RecordsDao
    abstract fun customRadioFiles(): CustomRadioFileDao
    abstract fun sync(): SyncDao
    abstract fun maintenance(): MaintenanceDao

    companion object {
        fun create(context: Context) = Room.databaseBuilder(
            context,
            SoundistDatabase::class.java,
            "soundist.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11).build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `sleep_routines` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `minutes` INTEGER NOT NULL, `target` TEXT NOT NULL, `fadeMinutes` INTEGER NOT NULL, `endMode` TEXT NOT NULL, `clockTime` TEXT, `sceneId` TEXT, `enabled` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `sleep_sessions` (`id` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `targetEndAt` INTEGER NOT NULL, `actualEndAt` INTEGER, `target` TEXT NOT NULL, `fadeMinutes` INTEGER NOT NULL, `status` TEXT NOT NULL, `routineId` TEXT, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleep_sessions_startedAt` ON `sleep_sessions` (`startedAt`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `note_blocks` (`id` TEXT NOT NULL, `noteId` TEXT NOT NULL, `kind` TEXT NOT NULL, `text` TEXT NOT NULL, `checked` INTEGER NOT NULL, `assetId` TEXT, `linkedNoteId` TEXT, `position` REAL NOT NULL, `revision` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_blocks_noteId` ON `note_blocks` (`noteId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `playback_events` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `activeSeconds` INTEGER NOT NULL, `completed` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_events_startedAt` ON `playback_events` (`startedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_events_kind_sourceId` ON `playback_events` (`kind`, `sourceId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sync_queue` ADD COLUMN `operationId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `sync_queue` ADD COLUMN `baseRevision` INTEGER")
                db.execSQL("ALTER TABLE `sync_queue` ADD COLUMN `clientUpdatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `sync_queue` SET `operationId` = 'legacy-' || `id`, `clientUpdatedAt` = `createdAt` WHERE `operationId` = ''")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_queue_operationId` ON `sync_queue` (`operationId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `sync_conflicts` (`operationId` TEXT NOT NULL, `userId` TEXT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `localPayload` TEXT, `remotePayload` TEXT, `localBaseRevision` INTEGER, `remoteRevision` INTEGER NOT NULL, `remoteDeleted` INTEGER NOT NULL, `recordedAt` INTEGER NOT NULL, PRIMARY KEY(`operationId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_conflicts_userId` ON `sync_conflicts` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_conflicts_entityType_entityId` ON `sync_conflicts` (`entityType`, `entityId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `sync_cursors` (`userId` TEXT NOT NULL, `cursor` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`userId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `sync_revisions` (`entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `revision` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, `serverUpdatedAt` INTEGER NOT NULL, PRIMARY KEY(`entityType`, `entityId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_revisions_revision` ON `sync_revisions` (`revision`)")
            }
        }

        /** Adds fields required to restore active focus/sleep sessions and habit reminder time without reconstruction. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `focus_sessions` ADD COLUMN `phase` TEXT NOT NULL DEFAULT 'FOCUS'")
                db.execSQL("ALTER TABLE `focus_sessions` ADD COLUMN `focusMinutes` INTEGER NOT NULL DEFAULT 25")
                db.execSQL("ALTER TABLE `focus_sessions` ADD COLUMN `breakMinutes` INTEGER NOT NULL DEFAULT 5")
                db.execSQL("ALTER TABLE `focus_sessions` ADD COLUMN `cycleRound` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `focus_sessions` ADD COLUMN `cycleRounds` INTEGER NOT NULL DEFAULT 4")
                db.execSQL("ALTER TABLE `focus_sessions` ADD COLUMN `longBreakMinutes` INTEGER NOT NULL DEFAULT 15")
                db.execSQL("ALTER TABLE `focus_sessions` ADD COLUMN `autoBreak` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `focus_sessions` ADD COLUMN `autoFocus` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `focus_sessions` ADD COLUMN `completionMinutes` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `reminderHour` INTEGER NOT NULL DEFAULT 9")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `reminderMinute` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sleep_sessions` ADD COLUMN `volumeSnapshotCaptured` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sleep_sessions` ADD COLUMN `masterVolumeSnapshot` REAL NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `sleep_sessions` ADD COLUMN `ambientVolumeSnapshot` REAL NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `sleep_sessions` ADD COLUMN `radioVolumeSnapshot` REAL NOT NULL DEFAULT 0.8")
            }
        }

        /** Preserves station-kind-specific generator, track and local-audio state. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `radio_stations` ADD COLUMN `payloadJson` TEXT")
            }
        }

        /** Persists the user-selected notebook accent from the mobile notes frontend. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notebooks` ADD COLUMN `accent` INTEGER NOT NULL DEFAULT 4286549127")
            }
        }

        /** Persists the user-selected notebook icon so it survives app restart. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notebooks` ADD COLUMN `iconKey` TEXT NOT NULL DEFAULT 'bookOpen'")
            }
        }

        /** Adds radio offline-pack/asset/custom-file persistence and enriches playback events with track attribution. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_7_8_STATEMENTS.forEach(db::execSQL)
            }
        }

        /** Persists the radio track index so the selected station resumes on the right track. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playback_snapshot` ADD COLUMN `radioTrackIndex` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Drops the retired offline audio-pack tables. All tracks ship inside the APK (asset:///radio/), so radio_packs/audio_assets are dead storage. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `radio_packs`")
                db.execSQL("DROP TABLE IF EXISTS `audio_assets`")
            }
        }

        /** Persists the exact ambient mode/source captured with each focus session. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `focus_sessions` ADD COLUMN `ambientMode` TEXT")
                db.execSQL("ALTER TABLE `focus_sessions` ADD COLUMN `audioSource` TEXT NOT NULL DEFAULT 'UNRECORDED'")
            }
        }
    }
}

/** SQL run by MIGRATION_7_8. Kept as data so the offline migration-contract test can assert additive, non-destructive DDL. */
internal val MIGRATION_7_8_STATEMENTS = listOf(
    "ALTER TABLE `playback_events` ADD COLUMN `trackId` TEXT",
    "ALTER TABLE `playback_events` ADD COLUMN `sourceKind` TEXT",
    "ALTER TABLE `playback_events` ADD COLUMN `listenedMs` INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE `playback_events` ADD COLUMN `completionReason` TEXT",
    "CREATE TABLE IF NOT EXISTS `radio_packs` (`packId` TEXT NOT NULL, `version` INTEGER NOT NULL, `title` TEXT NOT NULL, `state` TEXT NOT NULL, `downloadedBytes` INTEGER NOT NULL, `totalBytes` INTEGER NOT NULL, `installPath` TEXT, `manifestSha256` TEXT, `lastError` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`packId`))",
    "CREATE TABLE IF NOT EXISTS `audio_assets` (`trackId` TEXT NOT NULL, `fileName` TEXT NOT NULL, `extension` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `sha256` TEXT NOT NULL, `sourcePage` TEXT NOT NULL, `licenseName` TEXT NOT NULL, `licenseUrl` TEXT NOT NULL, `attribution` TEXT NOT NULL, `author` TEXT NOT NULL, `packId` TEXT, `localRelativePath` TEXT NOT NULL, `verified` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `codec` TEXT NOT NULL, `sampleRate` INTEGER NOT NULL, `channels` INTEGER NOT NULL, `downloadedAt` INTEGER NOT NULL, PRIMARY KEY(`trackId`))",
    "CREATE TABLE IF NOT EXISTS `custom_radio_files` (`id` TEXT NOT NULL, `stationId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `privatePath` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `sha256` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_custom_radio_files_stationId` ON `custom_radio_files` (`stationId`)",
)
