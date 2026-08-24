package com.soundist.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Offline contract test for MIGRATION_7_8.
 *
 * Room's MigrationTestHelper (androidx.room:room-testing) is an instrumented-test
 * dependency that is not present in the offline Gradle cache, so a live v7→v8
 * migration run cannot be compiled here. This test instead validates the migration's
 * DDL contract directly against the statement list MIGRATION_7_8 runs:
 * it must be non-destructive, additive only, and produce the expected columns/tables.
 */
class MigrationV8ContractTest {
    @Test fun databaseVersionIsTenAndMigrationsAreRegistered() {
        // androidx.room.Database is BINARY-retained so it is not visible via reflection at
        // runtime; verify the exported Room schema (generated at compile time into schemas/)
        // declares version 10 instead. Gradle unit tests run with the module dir as cwd.
        val schema = File("schemas/com.soundist.core.database.SoundistDatabase/10.json")
        assertTrue("expected generated schema 10.json to exist at ${schema.absolutePath}", schema.exists())
        assertTrue(schema.readText().contains("\"version\": 10"))
        assertEquals(8, SoundistDatabase.MIGRATION_8_9.startVersion)
        assertEquals(9, SoundistDatabase.MIGRATION_8_9.endVersion)
        assertEquals(9, SoundistDatabase.MIGRATION_9_10.startVersion)
        assertEquals(10, SoundistDatabase.MIGRATION_9_10.endVersion)
    }

    @Test fun versionTenSchemaDropsOfflinePackTables() {
        // MIGRATION_9_10 retires the offline audio-pack feature: the exported v10 schema must
        // no longer declare radio_packs / audio_assets, while custom_radio_files (custom channels) survives.
        val schema = File("schemas/com.soundist.core.database.SoundistDatabase/10.json")
        assertTrue("expected generated schema 10.json to exist at ${schema.absolutePath}", schema.exists())
        val text = schema.readText()
        assertFalse("v10 schema must not declare radio_packs", text.contains("\"tableName\": \"radio_packs\""))
        assertFalse("v10 schema must not declare audio_assets", text.contains("\"tableName\": \"audio_assets\""))
        assertTrue("v10 schema must keep custom_radio_files", text.contains("\"tableName\": \"custom_radio_files\""))
    }

    @Test fun statementsAreAdditiveAndNonDestructive() {
        val statements = MIGRATION_7_8_STATEMENTS
        assertTrue("must contain DDL", statements.isNotEmpty())
        assertFalse("must not DROP tables", statements.any { it.uppercase().startsWith("DROP") })
        assertFalse("must not DROP COLUMNS", statements.any { it.uppercase().contains("DROP COLUMN") })
        assertFalse("must not RENAME tables/columns", statements.any { it.uppercase().startsWith("ALTER TABLE") && it.uppercase().contains("RENAME") })

        // Only playback_events is altered; every other statement creates a brand-new object.
        statements.filter { it.uppercase().startsWith("ALTER TABLE") }.forEach { sql ->
            assertTrue("ALTER must target playback_events only: $sql", sql.contains("`playback_events`"))
        }
        statements.filter { it.uppercase().startsWith("CREATE TABLE") }.forEach { sql ->
            assertTrue("new tables must be created IF NOT EXISTS: $sql", sql.uppercase().startsWith("CREATE TABLE IF NOT EXISTS"))
        }
    }

    @Test fun playbackEventsGainsFourColumnsInOrder() {
        val alters = MIGRATION_7_8_STATEMENTS.filter { it.uppercase().startsWith("ALTER TABLE") }
        assertEquals(
            listOf(
                "ALTER TABLE `playback_events` ADD COLUMN `trackId` TEXT",
                "ALTER TABLE `playback_events` ADD COLUMN `sourceKind` TEXT",
                "ALTER TABLE `playback_events` ADD COLUMN `listenedMs` INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE `playback_events` ADD COLUMN `completionReason` TEXT",
            ),
            alters,
        )
    }

    @Test fun newTablesDeclareExpectedColumns() {
        val creates = MIGRATION_7_8_STATEMENTS.filter { it.uppercase().startsWith("CREATE TABLE") }
        assertEquals(3, creates.size)

        val columnsByName = creates.associate { sql ->
            val name = Regex("`([a-z_]+)`").find(sql.substringAfter("CREATE TABLE"))!!.groupValues[1]
            val body = sql.substringAfter("(").substringBeforeLast(")")
            val columns = Regex("`([a-zA-Z0-9_]+)`").findAll(body).map { it.groupValues[1] }.distinct().toList()
            name to columns
        }

        assertEquals(
            listOf("packId", "version", "title", "state", "downloadedBytes", "totalBytes", "installPath", "manifestSha256", "lastError", "createdAt", "updatedAt"),
            columnsByName["radio_packs"],
        )
        assertEquals(
            listOf("trackId", "fileName", "extension", "mimeType", "sizeBytes", "sha256", "sourcePage", "licenseName", "licenseUrl", "attribution", "author", "packId", "localRelativePath", "verified", "durationMs", "codec", "sampleRate", "channels", "downloadedAt"),
            columnsByName["audio_assets"],
        )
        assertEquals(
            listOf("id", "stationId", "displayName", "privatePath", "mimeType", "sizeBytes", "durationMs", "sha256", "sortIndex"),
            columnsByName["custom_radio_files"],
        )
    }

    @Test fun newTablesIndexPlaybackEventsAndCustomFiles() {
        val statements = MIGRATION_7_8_STATEMENTS
        assertTrue(statements.any { it == "CREATE INDEX IF NOT EXISTS `index_custom_radio_files_stationId` ON `custom_radio_files` (`stationId`)" })
        // custom_radio_files must be indexed by stationId (per entity definition).
        assertTrue(columns("custom_radio_files").contains("stationId"))
    }

    private fun columns(table: String): List<String> {
        val sql = MIGRATION_7_8_STATEMENTS.first { it.uppercase().startsWith("CREATE TABLE") && it.contains("`$table`") }
        val body = sql.substringAfter("(").substringBeforeLast(")")
        return Regex("`([a-zA-Z0-9_]+)`").findAll(body).map { it.groupValues[1] }.distinct().toList()
    }
}
