package com.soundist.feature.listening

import kotlin.random.Random
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomSoundSceneTest {
    @Test fun `desktop-compatible generator returns bounded diversified mix`() {
        val generator = RandomSoundSceneGenerator(Random(7))
        val first = generator.create(SoundCatalog.items)
        val second = generator.create(SoundCatalog.items, first.signature)
        assertTrue(first.sounds.size in 4..12)
        assertTrue(first.sounds.values.all { it in .06f.. .64f })
        assertTrue(first.sounds.keys.groupingBy { id -> SoundCatalog.items.first { it.id == id }.category }.eachCount().values.all { it <= 3 })
        assertNotEquals(first.signature, second.signature)
    }
}
