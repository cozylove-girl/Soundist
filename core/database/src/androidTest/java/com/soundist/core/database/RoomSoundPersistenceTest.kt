package com.soundist.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * task2 验收：真实 Room 库的「写库重启恢复」与「首次安装空状态」。
 *
 * 需要真机/仪器化运行（`:core:database:connectedDebugAndroidTest`）：
 * 该测试要真实的 Android SQLite（Room）。Robolectric 原生 SQLite 在 Windows + Temurin 17 上
 * 会以 DEP 违规崩在 `SQLiteConnectionNatives.nativeOpen`（hs_err），无法在 JVM 单测覆盖，
 * 因此放在 instrumentation 源集。测试不依赖任何宿主装配，直接开一个 Room 库验证持久化。
 */
@RunWith(AndroidJUnit4::class)
class RoomSoundPersistenceTest {

    private lateinit var db: SoundistDatabase

    @Before
    fun setUp() {
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(DB_NAME) // 每个测试独立文件。
        db = openDb()
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
    }

    private fun openDb(): SoundistDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.databaseBuilder(context, SoundistDatabase::class.java, DB_NAME)
            .allowMainThreadQueries()
            .build()
    }

    @Test
    fun favorite_persists_across_db_restart() = runTest {
        val repo = RoomSoundRepository(db)
        repo.seedCatalogue()
        assertFalse("收藏在初始播种时必须是 false", repo.observeSounds().first().first { it.id == "waves" }.isFavorite)

        repo.setFavorite("waves", true)
        assertTrue("写入收藏后必须立即可读", repo.observeSounds().first().first { it.id == "waves" }.isFavorite)

        // 重启恢复：关闭连接后重新打开同一数据库文件，收藏必须仍在。
        db.close()
        db = openDb()
        val reopened = RoomSoundRepository(db)
        assertTrue("收藏必须在数据库重启后恢复", reopened.observeSounds().first().first { it.id == "waves" }.isFavorite)
        // 未收藏的声音不受影响。
        assertFalse(reopened.observeSounds().first().first { it.id == "cafe" }.isFavorite)
    }

    @Test
    fun seed_catalogue_on_first_install_has_no_active_sounds() = runTest {
        val repo = RoomSoundRepository(db)
        repo.seedCatalogue()
        val sounds = repo.observeSounds().first()
        // 84 个环境声全部播种。
        assertEquals("首次安装必须播种全部 84 个声音", 84, sounds.size)
        // 不播种任何收藏（全新用户无预设收藏）。
        assertTrue(sounds.all { !it.isFavorite })
        // 不播种播放快照 → UI 走默认空混音（0 活跃声源），与 Web ALL_SOUNDS volume=0 active=false 一致。
        assertNull("首次安装不得存在播放快照", repo.observePlayback().first())
    }

    companion object {
        private const val DB_NAME = "soundist-test.db"
    }
}
