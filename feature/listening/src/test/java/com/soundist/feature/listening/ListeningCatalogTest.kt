package com.soundist.feature.listening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningCatalogTest {
    @Test fun `catalog matches desktop 84 sounds and filter ordering`() {
        assertEquals(84, SoundCatalog.items.size)
        assertEquals(SoundCatalog.items.size, SoundCatalog.items.map { it.id }.distinct().size)
        assertEquals(listOf(SoundFilter.ALL, SoundFilter.CURRENT, SoundFilter.FAVORITES), SoundFilter.entries.take(3))
        assertTrue(SoundFilter.entries.drop(3).all { filter -> SoundCatalog.items.any { it.category == filter } })
    }

    @Test fun `built in presets match mobile source of truth`() {
        assertEquals(listOf("夜雨森林", "移动城市", "深海呼吸", "晨间咖啡馆", "火车入眠", "寺庙晨钟"), SoundCatalog.builtInPresets.map { it.name })
        assertTrue(SoundCatalog.builtInPresets.all { it.builtIn && it.tracks.isNotEmpty() })
    }
}
