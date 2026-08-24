package com.soundist.app

import android.app.Application
import androidx.work.Configuration
import com.soundist.core.database.RoomNotesRepository
import com.soundist.core.database.RoomOfflineContentRepository
import com.soundist.core.database.RoomProductivityRepository
import com.soundist.core.database.RoomRecordsRepository
import com.soundist.core.database.RoomSoundRepository
import com.soundist.core.database.SoundistDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SoundistApplication : Application(), Configuration.Provider {
    val database by lazy { SoundistDatabase.create(this) }
    val services by lazy { SoundistServices(database) }
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()
    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.ensureChannel(this)
        applicationScope.launch { services.sounds.seedCatalogue() }
        applicationScope.launch { services.notes.seedNotes() }
    }
}

class SoundistServices(database: SoundistDatabase) {
    val sounds = RoomSoundRepository(database)
    val productivity = RoomProductivityRepository(database)
    val notes = RoomNotesRepository(database)
    val records = RoomRecordsRepository(database)
    val offlineContent = RoomOfflineContentRepository(database)
}
