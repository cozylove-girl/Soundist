package com.soundist.feature.listening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningSelectorsTest {
    @Test fun sound_scope_category_and_query_are_composed() {
        val rain = SoundCatalog.items.first { it.category == SoundFilter.RAIN }.copy(active = true, favorite = true)
        val state = ListeningState(sounds = SoundCatalog.items.map { if (it.id == rain.id) rain else it }, filter = SoundFilter.CURRENT, query = rain.name)
        assertEquals(listOf(rain.id), state.visibleSounds().map { it.id })
        assertTrue(state.copy(filter = SoundFilter.FAVORITES).visibleSounds().any { it.id == rain.id })
    }

    @Test fun radio_search_includes_track_metadata() {
        val state = ListeningState(stations = RadioCatalog.initial, radioGroup = RadioGroup.OFFICIAL, radioQuery = "omfgdude")
        assertEquals(listOf("channel-lofi"), state.visibleStations().map { it.id })
    }

    @Test fun official_catalog_separates_provenance_from_local_playback_and_generated_does_not_hotlink() {
        val official = RadioCatalog.initial.filter { it.group == RadioGroup.OFFICIAL }
        // 全量内置：所有曲目都走 asset:///radio/*，来源页/授权仍是网络元数据（非播放地址）。
        assertTrue(official.all { station -> station.tracks.isNotEmpty() && station.tracks.all { it.mediaUrl.startsWith("https://") && it.remoteCacheKey.startsWith("radio/") && it.localAssetUri != null && it.localAssetUri!!.startsWith("asset:///radio/") } })
        // 基础 APK 内置曲目的真实扩展名：mp3 不得被硬编码成 .ogg。
        val mp3Tracks = setOf("ambient-pad-i", "chopin-canon-f-minor", "jazz-avant")
        official.flatMap { it.tracks }.filter { it.id in mp3Tracks }.forEach { assertEquals("asset:///radio/${it.id}.mp3", it.localAssetUri) }
        assertTrue(RadioCatalog.initial.filter { it.sourceKind == RadioSourceKind.GENERATED }.all { it.url.startsWith("generated://") })
        assertTrue(official.flatMap { it.tracks }.all { it.license != null && it.license!!.sourcePage.startsWith("https://") && it.license!!.licenseUrl.startsWith("https://") })
    }

    @Test fun custom_channel_picker_rejects_non_audio_empty_and_oversized_files() {
        val max = 200L * 1024L * 1024L
        val selected = listOf(
            LocalAudioSelection("content://ok", "ok.ogg", max, "audio/ogg"),
            LocalAudioSelection("content://large", "large.wav", max + 1, "audio/wav"),
            LocalAudioSelection("content://image", "cover.png", 100, "image/png"),
            LocalAudioSelection("content://empty", "empty.mp3", 0, "audio/mpeg"),
        )
        assertEquals(listOf("content://ok"), selected.validLocalAudioSelections().map { it.uri })
    }

    @Test fun official_catalog_bundles_all_tracks() {
        val official = RadioCatalog.initial.filter { it.group == RadioGroup.OFFICIAL }
        val visible = official.flatMap { it.tracks }
        assertTrue(visible.isNotEmpty())
        // 全量内置：所有曲目 released 且有真实 asset 地址，不再隐藏任何曲目。
        assertTrue(visible.all { it.released })
        assertTrue(visible.all { it.localAssetUri?.startsWith("asset:///radio/") == true })
        // 原「未分包」曲目现在也应全部可见。
        val previouslyHidden = setOf("dvorak-larghetto", "mozart-flute-k313", "smetana-moldau", "chopin-nocturne-62", "rhapsody-blue", "cello-bach-cc0-prelude")
        assertTrue(previouslyHidden.all { id -> visible.any { it.id == id } })
    }
}
