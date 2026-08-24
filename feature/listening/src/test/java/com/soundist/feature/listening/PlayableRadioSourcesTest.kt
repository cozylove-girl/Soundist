package com.soundist.feature.listening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B1：直接音频流（STREAM）与 HLS 支持。
 * playableSources() 把用户主动输入的流地址组装成唯一播放 URI，交给 Media3
 * DefaultMediaSourceFactory 自动识别（.m3u8 → HLS、.mp3/流 → Progressive/Icecast）。
 * 同时保证官方曲目的 mediaUrl 永不进入生产播放列表（禁止热链）。
 */
class PlayableRadioSourcesTest {

    private val streamStation = RadioStation(
        id = "stream-1", name = "我的直接流", description = "d", group = RadioGroup.CUSTOM,
        source = "直接音频流", url = "https://example.com/live.mp3", sourceKind = RadioSourceKind.STREAM,
        custom = true,
    )

    @Test
    fun `stream station generates its url as the single playable uri`() {
        val sources = streamStation.playableSources()
        assertEquals(1, sources.size)
        // (itemId, stationIndex, uri) —— stationIndex 固定 0，曲目坐标与「唯一音源」一致。
        assertEquals(Triple("stream-1", 0, "https://example.com/live.mp3"), sources[0])
    }

    @Test
    fun `stream hls m3u8 url is kept as-is so Media3 default factory builds HlsMediaSource`() {
        val hls = streamStation.copy(url = "https://example.com/live/index.m3u8?token=abc")
        assertEquals("https://example.com/live/index.m3u8?token=abc", hls.playableSources().single().third)
    }

    @Test
    fun `icecast url without extension is still accepted as user stream`() {
        val icecast = streamStation.copy(url = "http://example.com:8000/stream")
        assertEquals("http://example.com:8000/stream", icecast.playableSources().single().third)
    }

    @Test
    fun `blank or invalid stream url yields no playable source`() {
        assertTrue(streamStation.copy(url = "   ").playableSources().isEmpty())
        // 非 http(s)/asset/content/file 的地址不被当作播放源。
        assertTrue(streamStation.copy(url = "generated://stream-1").playableSources().isEmpty())
    }

    @Test
    fun `official tracks use localAssetUri and never hotlink mediaUrl`() {
        val official = RadioStation(
            id = "official-1", name = "官方", description = "d", group = RadioGroup.OFFICIAL,
            source = "s", url = "https://official.example/station", sourceKind = RadioSourceKind.OFFICIAL,
            tracks = listOf(
                RadioTrack(id = "t1", title = "曲1", artist = "a", mediaUrl = "https://cdn.example/t1.mp3", localAssetUri = "asset:///radio/t1.mp3"),
                RadioTrack(id = "t2", title = "曲2", artist = "a", mediaUrl = "https://cdn.example/t2.mp3", localAssetUri = null),
            ),
        )
        val sources = official.playableSources()
        // 已内置曲目用 asset:// 播放；未内置曲目（无 localAssetUri）不得回退到 mediaUrl 热链。
        assertEquals(1, sources.size)
        assertEquals(Triple("t1", 0, "asset:///radio/t1.mp3"), sources[0])
        assertTrue(sources.none { it.third.startsWith("http") })
    }

    @Test
    fun `local files keep their content and file uris in playlist order`() {
        val local = RadioStation(
            id = "local-1", name = "本地", description = "d", group = RadioGroup.CUSTOM,
            source = "本地音频", url = "", sourceKind = RadioSourceKind.LOCAL, custom = true,
            localAudio = listOf(
                LocalAudioSelection("file:///data/user/0/app/files/1.mp3", "1.mp3", 100, "audio/mpeg"),
                LocalAudioSelection("content://media/2.mp3", "2.mp3", 200, "audio/mpeg"),
            ),
        )
        val sources = local.playableSources()
        assertEquals(2, sources.size)
        assertEquals(Triple("file:///data/user/0/app/files/1.mp3", 0, "file:///data/user/0/app/files/1.mp3"), sources[0])
        assertEquals(Triple("content://media/2.mp3", 1, "content://media/2.mp3"), sources[1])
    }

    @Test
    fun `generated station has no media source (renderer handles it)`() {
        val generated = RadioStation(
            id = "gen-1", name = "生成", description = "d", group = RadioGroup.GENERATED,
            source = "本机生成", url = "generated://gen-1", sourceKind = RadioSourceKind.GENERATED,
            generatorPresetId = "custom-lab",
        )
        assertTrue(generated.playableSources().isEmpty())
    }
}
