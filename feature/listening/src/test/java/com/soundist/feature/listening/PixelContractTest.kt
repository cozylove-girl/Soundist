package com.soundist.feature.listening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Numeric contracts measured from App.tsx at a 390x844 CSS viewport. */
class PixelContractTest {
    @Test fun galaxy_uses_web_safe_rectangle_and_non_ring_cutout() {
        val ids = SoundCatalog.items.take(20).map { it.id }
        val points = galaxyPlacements(ids)
        assertEquals(ids.size, points.size)
        points.values.forEach { point ->
            val x = point.x * 236f
            val y = point.y * 236f
            assertTrue(x in -64f..300f)
            assertTrue(y in -76f..232f)
            assertTrue(kotlin.math.hypot(x - 118f, y - 118f) > 112f)
            assertTrue(!(x > 8f && x < 228f && y > -82f && y < 42f))
        }
    }

    @Test fun web_initial_tab_contract_is_generated_radio() {
        assertEquals(RadioGroup.GENERATED, ListeningState().radioGroup)
    }
}
