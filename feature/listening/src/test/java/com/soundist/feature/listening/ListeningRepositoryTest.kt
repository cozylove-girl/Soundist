package com.soundist.feature.listening

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningRepositoryTest {
    @Test fun `stateful repository persists every mutation contract`() = runTest {
        val repository = StatefulListeningRepository()
        val preset = SoundPreset("mine", "我的声场", mapOf("waves" to .5f))
        repository.savePreset(preset)
        repository.saveSound(SoundCatalog.items.first().copy(active = true, volume = 0f))
        val station = RadioStation("mine", "频道", "描述", RadioGroup.CUSTOM, "网络", "https://example.test/audio", custom = true)
        repository.saveStation(station)
        assertTrue(repository.load().presets.any { it.id == "mine" })
        assertTrue(repository.load().sounds.first().active)
        assertEquals(0f, repository.load().sounds.first().volume)
        assertTrue(repository.load().stations.any { it.id == "mine" })
        repository.deletePreset("mine"); repository.deleteStation("mine")
        assertFalse(repository.load().presets.any { it.id == "mine" })
        assertFalse(repository.load().stations.any { it.id == "mine" })
    }
}
