package com.soundist.core.database

import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationContractTest {
    @Test fun migrationsCoverEveryPublishedSchemaWithoutSkipping() {
        assertEquals(1, SoundistDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, SoundistDatabase.MIGRATION_1_2.endVersion)
        assertEquals(2, SoundistDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, SoundistDatabase.MIGRATION_2_3.endVersion)
        assertEquals(3, SoundistDatabase.MIGRATION_3_4.startVersion)
        assertEquals(4, SoundistDatabase.MIGRATION_3_4.endVersion)
        assertEquals(4, SoundistDatabase.MIGRATION_4_5.startVersion)
        assertEquals(5, SoundistDatabase.MIGRATION_4_5.endVersion)
        assertEquals(7, SoundistDatabase.MIGRATION_7_8.startVersion)
        assertEquals(8, SoundistDatabase.MIGRATION_7_8.endVersion)
        assertEquals(8, SoundistDatabase.MIGRATION_8_9.startVersion)
        assertEquals(9, SoundistDatabase.MIGRATION_8_9.endVersion)
        assertEquals(9, SoundistDatabase.MIGRATION_9_10.startVersion)
        assertEquals(10, SoundistDatabase.MIGRATION_9_10.endVersion)
        assertEquals(10, SoundistDatabase.MIGRATION_10_11.startVersion)
        assertEquals(11, SoundistDatabase.MIGRATION_10_11.endVersion)
    }
}
