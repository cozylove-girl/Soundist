package com.soundist.feature.listening

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeaCanvasTest {
    @Test fun engine_uses_exact_frontend_pool_sizes_and_is_deterministic() {
        val first = DeepSeaParticleEngine(123)
        val second = DeepSeaParticleEngine(123)
        assertEquals(460, first.drift.size)
        assertEquals(first.drift, second.drift)
        first.step(true, true, .8f, false); second.step(true, true, .8f, false)
        assertEquals(first.drift, second.drift)
        assertEquals(first.dust, second.dust)
        repeat(30) { first.step(true, true, .8f, false) }
        assertTrue(first.dust.size <= 1250)
    }

    @Test fun playing_and_paused_have_different_emission_density() {
        val active = DeepSeaParticleEngine(7); val paused = DeepSeaParticleEngine(7)
        active.step(true, true, .8f, false); paused.step(true, false, .8f, false)
        assertTrue(active.dust.size > paused.dust.size)
    }

    @Test fun galaxy_nodes_respect_safe_rectangle_center_and_title_cutouts() {
        repeat(20) { index ->
            val point = galaxyPlacement("sound-$index", index, 20)
            assertTrue(point.x in -.27f..1.27f)
            assertTrue(point.y in -.32f.. .98f)
            assertTrue(hypot(point.x - .5f, point.y - .5f) > .475f)
            assertTrue(!(point.x > .034f && point.x < .966f && point.y < .178f))
            assertEquals(point, galaxyPlacement("sound-$index", index, 20))
        }
        assertNotEquals(galaxyPlacement("a", 0, 6), galaxyPlacement("b", 1, 6))
    }
}
