package com.soundist.feature.listening

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 电台播放控制状态机测试：按钮规格矩阵、播放/暂停/恢复/取消、生成电台真实事件回流、
 * 失败回流、快速连点串行化。全部用假控制器/假渲染器驱动，验证 ViewModel 状态机。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RadioPlaybackControlTest {

    private val officialStation = RadioStation(
        id = "test-official", name = "官方测试频道", description = "d", group = RadioGroup.OFFICIAL,
        source = "s", url = "https://example.test", sourceKind = RadioSourceKind.OFFICIAL,
        tracks = listOf(RadioTrack("t1", "曲目1", "作者", localAssetUri = "asset:///radio/t1.mp3")),
    )
    private val localStation = RadioStation(
        id = "test-local", name = "本地测试频道", description = "d", group = RadioGroup.CUSTOM,
        source = "本地音频", url = "", sourceKind = RadioSourceKind.LOCAL,
        localAudio = listOf(LocalAudioSelection("file:///data/user/0/app/files/1.mp3", "1.mp3", 1024, "audio/mpeg")),
        custom = true,
    )
    private val generatedStation = RadioStation(
        id = "test-generated", name = "生成测试频道", description = "d", group = RadioGroup.GENERATED,
        source = "本机生成", url = "generated://test-generated", sourceKind = RadioSourceKind.GENERATED,
        generatorPresetId = "custom-lab", custom = false,
    )
    private val allStations = listOf(officialStation, localStation, generatedStation)

    private fun TestScope.newViewModel(
        initial: ListeningState = ListeningState(stations = allStations),
        audio: ListeningAudioController = RecordingAudioController(),
        renderer: GeneratedAudioRenderer? = null,
    ): ListeningViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return ListeningViewModel(StatefulListeningRepository(initial), audio, generatedRenderer = renderer)
            .also { testScheduler.advanceUntilIdle() } // 让 init 从仓库加载初始频道后再分派命令
    }

    // ── 按钮规格矩阵（图标 / 无障碍文案）───────────────────────────────────

    @Test
    fun `button spec maps every playback state to icon kind and a11y label`() {
        assertEquals(RadioButtonSpec(RadioButtonKind.PROGRESS, "正在载入电台"), radioPlaybackButtonSpec(PlaybackState.LOADING))
        assertEquals(RadioButtonSpec(RadioButtonKind.PAUSE, "暂停电台"), radioPlaybackButtonSpec(PlaybackState.PLAYING))
        // AUDIBLE（已出声）与 PLAYING 一样是「实际播放中」，按钮规格 = 暂停。
        assertEquals(RadioButtonSpec(RadioButtonKind.PAUSE, "暂停电台"), radioPlaybackButtonSpec(PlaybackState.AUDIBLE))
        assertEquals(RadioButtonSpec(RadioButtonKind.PLAY, "继续播放电台"), radioPlaybackButtonSpec(PlaybackState.PAUSED))
        assertEquals(RadioButtonSpec(RadioButtonKind.RETRY, "重试播放电台"), radioPlaybackButtonSpec(PlaybackState.ERROR))
        // IDLE 隐藏播放卡片，不产生暂停/播放假按钮。
        assertNull(radioPlaybackButtonSpec(PlaybackState.IDLE))
    }

    @Test
    fun `status label per state`() {
        assertEquals("未播放", radioStatusLabel(PlaybackState.IDLE))
        assertEquals("载入中", radioStatusLabel(PlaybackState.LOADING))
        assertEquals("正在播放", radioStatusLabel(PlaybackState.PLAYING))
        assertEquals("正在播放", radioStatusLabel(PlaybackState.AUDIBLE))
        assertEquals("已暂停", radioStatusLabel(PlaybackState.PAUSED))
        assertEquals("播放失败", radioStatusLabel(PlaybackState.ERROR))
    }

    // ── 官方本地频道：播放 → 暂停 → 恢复 ────────────────────────────────────

    @Test
    fun `official play shows loading then playing only after player event`() = runTest {
        try {
            val audio = RecordingAudioController()
            val vm = newViewModel(audio = audio)
            vm.dispatch(ListeningAction.PlayStation(officialStation.id))
            // 播放命令提交后立即进入 LOADING（准备中），不乐观置位 PLAYING。
            assertEquals(PlaybackState.LOADING, vm.state.value.radioPlayback)
            testScheduler.advanceUntilIdle()
            assertEquals(PlaybackState.PLAYING, vm.state.value.radioPlayback)
            assertEquals(officialStation.id to 0, audio.playCalls.last())
            assertEquals(1, audio.playCalls.size)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun `official pause and resume from paused keeps state and reuses toggle`() = runTest {
        try {
            val audio = RecordingAudioController()
            val vm = newViewModel(audio = audio)
            vm.dispatch(ListeningAction.PlayStation(officialStation.id))
            testScheduler.advanceUntilIdle()
            assertEquals(PlaybackState.PLAYING, vm.state.value.radioPlayback)

            vm.dispatch(ListeningAction.ToggleRadio) // PLAYING → pause
            assertEquals(PlaybackState.PAUSED, vm.state.value.radioPlayback)
            testScheduler.advanceUntilIdle()
            assertEquals(PlaybackState.PAUSED, vm.state.value.radioPlayback)
            assertEquals(1, audio.pauseCalls.size)

            vm.dispatch(ListeningAction.ToggleRadio) // PAUSED → resume
            assertEquals(PlaybackState.LOADING, vm.state.value.radioPlayback)
            testScheduler.advanceUntilIdle()
            assertEquals(PlaybackState.PLAYING, vm.state.value.radioPlayback)
            // 恢复不重建频道：仍是同一个频道在播。
            assertEquals(officialStation.id to 0, audio.playCalls.last())
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun `local channel pause and resume`() = runTest {
        try {
            val audio = RecordingAudioController()
            val vm = newViewModel(audio = audio)
            vm.dispatch(ListeningAction.PlayStation(localStation.id))
            testScheduler.advanceUntilIdle()
            assertEquals(PlaybackState.PLAYING, vm.state.value.radioPlayback)
            // 本地频道曲目坐标是 localAudio（播放从第 0 个文件开始）。
            assertEquals(localStation.id to 0, audio.playCalls.last())

            vm.dispatch(ListeningAction.ToggleRadio)
            assertEquals(PlaybackState.PAUSED, vm.state.value.radioPlayback)
            testScheduler.advanceUntilIdle()

            vm.dispatch(ListeningAction.ToggleRadio)
            testScheduler.advanceUntilIdle()
            assertEquals(PlaybackState.PLAYING, vm.state.value.radioPlayback)
        } finally { Dispatchers.resetMain() }
    }

    // ── 载入中点按钮 = 明确取消载入（不是暂停）─────────────────────────────

    @Test
    fun `tapping while loading cancels the load back to idle`() = runTest {
        try {
            val audio = RecordingAudioController()
            val vm = newViewModel(audio = audio)
            vm.dispatch(ListeningAction.PlayStation(officialStation.id))
            // 尚未推进：播放命令仍在队列中，状态为 LOADING。
            assertEquals(PlaybackState.LOADING, vm.state.value.radioPlayback)
            vm.dispatch(ListeningAction.ToggleRadio)
            assertEquals(PlaybackState.IDLE, vm.state.value.radioPlayback)
            testScheduler.advanceUntilIdle()
            assertEquals(PlaybackState.IDLE, vm.state.value.radioPlayback)
            // 被取消的播放命令不启动播放器；只执行取消（stopRadio）。
            assertEquals(0, audio.playCalls.size)
            assertEquals(1, audio.stopRadioCalls.size)
            assertEquals(0, audio.pauseCalls.size)
        } finally { Dispatchers.resetMain() }
    }

    // ── 生成电台：真实事件回流，不乐观置位 PLAYING ─────────────────────────

    @Test
    fun `generated station stays loading until renderer actually starts PCM`() = runTest {
        try {
            val renderer = FakeGeneratedAudioRenderer()
            val audio = RecordingAudioController()
            val vm = newViewModel(audio = audio, renderer = renderer)
            vm.dispatch(ListeningAction.PlayStation(generatedStation.id))
            assertEquals(PlaybackState.LOADING, vm.state.value.radioPlayback)
            testScheduler.advanceUntilIdle()
            assertEquals(1, renderer.playCalls.size)
            // 渲染器 play() 返回成功但尚未真正开始写 PCM：不得假 PLAYING。
            assertEquals(PlaybackState.LOADING, vm.state.value.radioPlayback)
            renderer.events.value = RadioEngineState(PlaybackState.PLAYING, generatedStation.id, 0)
            testScheduler.advanceUntilIdle()
            assertEquals(PlaybackState.PLAYING, vm.state.value.radioPlayback)

            vm.dispatch(ListeningAction.ToggleRadio) // PLAYING → pause
            assertEquals(PlaybackState.PAUSED, vm.state.value.radioPlayback)
            testScheduler.advanceUntilIdle()
            assertEquals(1, renderer.pauseCalls.size)
            assertEquals(PlaybackState.PAUSED, vm.state.value.radioPlayback)

            vm.dispatch(ListeningAction.ToggleRadio) // PAUSED → resume
            testScheduler.advanceUntilIdle()
            assertEquals(2, renderer.playCalls.size)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun `generated renderer error flows back to error state`() = runTest {
        try {
            val renderer = FakeGeneratedAudioRenderer()
            val vm = newViewModel(renderer = renderer)
            renderer.events.value = RadioEngineState(PlaybackState.ERROR, generatedStation.id, 0, "生成电台渲染失败")
            testScheduler.advanceUntilIdle()
            assertEquals(PlaybackState.ERROR, vm.state.value.radioPlayback)
            assertEquals("生成电台渲染失败", vm.state.value.operationError)
            // 错误态按钮规格 = 重试（不是暂停图标）。
            assertEquals(RadioButtonKind.RETRY, radioPlaybackButtonSpec(vm.state.value.radioPlayback)?.kind)
        } finally { Dispatchers.resetMain() }
    }

    // ── 声场编排器：试听必须渲染「当前草稿」而非源频道已保存编排 ─────────────────

    @Test
    fun `preview generator draft renders the draft arrangement and settings`() = runTest {
        try {
            val source = generatedStation.copy(
                id = "gen-source",
                generatorPresetId = "custom-lab",
                generatorArrangement = createDefaultGeneratedArrangement("custom-lab"),
            )
            val renderer = CapturingGeneratedAudioRenderer()
            val vm = newViewModel(initial = ListeningState(stations = listOf(source)), renderer = renderer)
            vm.dispatch(ListeningAction.OpenGeneratorEditor(source))
            testScheduler.advanceUntilIdle()
            assertEquals("gen-source", vm.state.value.generatorSourceStationId)

            // 在草稿上改参数：速度 96 + 随机种子「draft-seed」，均未写入源频道。
            vm.dispatch(ListeningAction.UpdateGeneratorSettings { it.copy(tempo = 96) })
            vm.dispatch(ListeningAction.CommitGeneratorArrangement { it.copy(seed = "draft-seed") })

            vm.dispatch(ListeningAction.PreviewGeneratorDraft)
            testScheduler.advanceUntilIdle()

            val played = requireNotNull(renderer.lastStation)
            assertEquals("custom-lab", played.generatorPresetId)
            // 试听对象必须是草稿参数，而不是源频道保存的 54 BPM / 原 seed。
            assertEquals(96, played.generatorSettings?.tempo)
            assertEquals("draft-seed", played.generatorArrangement?.seed)
            assertNotEquals(createDefaultGeneratedArrangement("custom-lab").seed, played.generatorArrangement?.seed)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun `preview pause then resume keeps rendering the draft not the saved station`() = runTest {
        try {
            val source = generatedStation.copy(
                id = "gen-source", generatorPresetId = "custom-lab",
                generatorArrangement = createDefaultGeneratedArrangement("custom-lab"),
            )
            val renderer = CapturingGeneratedAudioRenderer()
            val vm = newViewModel(initial = ListeningState(stations = listOf(source)), renderer = renderer)
            vm.dispatch(ListeningAction.OpenGeneratorEditor(source))
            testScheduler.advanceUntilIdle()
            vm.dispatch(ListeningAction.UpdateGeneratorSettings { it.copy(tempo = 96) })
            vm.dispatch(ListeningAction.CommitGeneratorArrangement { it.copy(seed = "draft-seed") })

            vm.dispatch(ListeningAction.PreviewGeneratorDraft)
            testScheduler.advanceUntilIdle()
            assertEquals("draft-seed", renderer.lastStation?.generatorArrangement?.seed)

            // 进入 PLAYING 后暂停。
            renderer.events.value = RadioEngineState(PlaybackState.PLAYING, "gen-source", 0)
            testScheduler.advanceUntilIdle()
            vm.dispatch(ListeningAction.ToggleRadio)
            assertEquals(PlaybackState.PAUSED, vm.state.value.radioPlayback)
            testScheduler.advanceUntilIdle()

            // 暂停后恢复试听：仍必须渲染草稿（draft-seed/96BPM），而不是源频道已保存编排。
            vm.dispatch(ListeningAction.ToggleRadio)
            testScheduler.advanceUntilIdle()
            assertEquals("draft-seed", renderer.lastStation?.generatorArrangement?.seed)
            assertEquals(96, renderer.lastStation?.generatorSettings?.tempo)
        } finally { Dispatchers.resetMain() }
    }

    // ── 失败处理：缺失资产不得假播放 ──────────────────────────────────────

    @Test
    fun `missing asset surfaces error instead of fake playing`() = runTest {
        try {
            val failingAudio = object : ListeningAudioController {
                private val events = MutableStateFlow(RadioEngineState())
                override val radioEvents: Flow<RadioEngineState> = events
                override suspend fun setAmbient(tracks: List<AmbientSound>, playing: Boolean, masterVolume: Float) {}
                override suspend fun playRadio(station: RadioStation, trackIndex: Int) {
                    throw IllegalStateException("「测试官方频道」的音频包尚未安装，下载音频包后即可播放")
                }
                override suspend fun pauseRadio() {}
                override suspend fun stopRadio() {}
                override suspend fun stopAll() {}
            }
            val vm = newViewModel(audio = failingAudio)
            vm.dispatch(ListeningAction.PlayStation(officialStation.id))
            assertEquals(PlaybackState.LOADING, vm.state.value.radioPlayback)
            testScheduler.advanceUntilIdle()
            assertEquals(PlaybackState.ERROR, vm.state.value.radioPlayback)
            assertTrue(vm.state.value.operationError.orEmpty().contains("音频包尚未安装"))
        } finally { Dispatchers.resetMain() }
    }

    // ── 错误重试与清除 ─────────────────────────────────────────────────────

    @Test
    fun `error state retries on toggle and clears to idle`() = runTest {
        try {
            val audio = RecordingAudioController()
            val vm = newViewModel(audio = audio)
            audio.events.value = RadioEngineState(PlaybackState.ERROR, officialStation.id, 0, "解码失败")
            testScheduler.advanceUntilIdle()
            assertEquals(PlaybackState.ERROR, vm.state.value.radioPlayback)

            // ERROR → 点按钮 = 重试播放。
            vm.dispatch(ListeningAction.ToggleRadio)
            assertEquals(PlaybackState.LOADING, vm.state.value.radioPlayback)
            testScheduler.advanceUntilIdle()
            assertEquals(PlaybackState.PLAYING, vm.state.value.radioPlayback)

            // ClearError 只在 ERROR 时回到 IDLE。
            audio.events.value = RadioEngineState(PlaybackState.ERROR, officialStation.id, 0, "解码失败")
            testScheduler.advanceUntilIdle()
            vm.dispatch(ListeningAction.ClearError)
            assertEquals(PlaybackState.IDLE, vm.state.value.radioPlayback)
            assertNull(vm.state.value.operationError)
        } finally { Dispatchers.resetMain() }
    }

    // ── 快速连点：命令串行、最后意图胜出、不并发启动多个播放器 ────────────

    @Test
    fun `rapid toggles serialize and last intent wins without stacking players`() = runTest {
        try {
            val audio = RecordingAudioController()
            val vm = newViewModel(audio = audio)
            vm.dispatch(ListeningAction.PlayStation(officialStation.id))
            testScheduler.advanceUntilIdle()
            assertEquals(PlaybackState.PLAYING, vm.state.value.radioPlayback)

            // 快速连点 10 次（不推进调度器，命令各自按当前同步状态判定意图）。
            repeat(10) { vm.dispatch(ListeningAction.ToggleRadio) }
            testScheduler.advanceUntilIdle()

            // 10 次从 PLAYING 起：t1 暂停、t2 播放、t3 取消、… 偶数次为「播放」，最后一次命令 = 播放。
            // 只有最后一次命令真正执行，中间命令全部被取消 → 不并发启动多个播放器、不堆叠暂停。
            assertEquals(PlaybackState.PLAYING, vm.state.value.radioPlayback)
            assertEquals(2, audio.playCalls.size) // 初始 1 次 + 最后一次命令 1 次
            assertEquals(0, audio.pauseCalls.size)
            assertEquals(0, audio.stopRadioCalls.size)
        } finally { Dispatchers.resetMain() }
    }

    // ── 删除正在播放的频道：必须停止其音频 ────────────────────────────────

    @Test
    fun `deleting the playing channel stops its audio`() = runTest {
        try {
            val audio = RecordingAudioController()
            val vm = newViewModel(audio = audio)
            vm.dispatch(ListeningAction.PlayStation(officialStation.id))
            testScheduler.advanceUntilIdle()
            assertEquals(PlaybackState.PLAYING, vm.state.value.radioPlayback)

            vm.dispatch(ListeningAction.DeleteRadio(officialStation.id))
            vm.dispatch(ListeningAction.ConfirmDeleteRadio(true))
            testScheduler.advanceUntilIdle()

            // 播放器必须被停止（不再持有已删除频道的播放列表），否则 UI 切走但声音继续。
            assertEquals(1, audio.stopRadioCalls.size)
            assertEquals(PlaybackState.IDLE, vm.state.value.radioPlayback)
            assertEquals(null, vm.state.value.stations.firstOrNull { it.id == officialStation.id })
            assertEquals(vm.state.value.stations.firstOrNull()?.id, vm.state.value.selectedStationId)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun `deleting a paused current channel also stops its session`() = runTest {
        try {
            val audio = RecordingAudioController()
            val vm = newViewModel(audio = audio)
            vm.dispatch(ListeningAction.PlayStation(officialStation.id))
            testScheduler.advanceUntilIdle()
            vm.dispatch(ListeningAction.ToggleRadio) // PLAYING → PAUSED
            testScheduler.advanceUntilIdle()
            assertEquals(PlaybackState.PAUSED, vm.state.value.radioPlayback)

            vm.dispatch(ListeningAction.DeleteRadio(officialStation.id))
            vm.dispatch(ListeningAction.ConfirmDeleteRadio(false))
            testScheduler.advanceUntilIdle()

            // 暂停中的当前频道被删除同样清空会话（避免残留可恢复位置指向已删除频道）。
            assertEquals(1, audio.stopRadioCalls.size)
            assertEquals(PlaybackState.IDLE, vm.state.value.radioPlayback)
        } finally { Dispatchers.resetMain() }
    }

    // ── 单音色试听：快速连点多个音色，最后一次点击优先，立即打断旧试听 ─────────

    @Test
    fun `rapid timbre auditions cancel previous and only the last completes`() = runTest {
        try {
            val renderer = AuditionCapturingRenderer()
            val vm = newViewModel(renderer = renderer)
            // 快速连点 A → B → C（每次推进让前一次试听真正启动，模拟真实 Main.immediate 立即执行）。
            vm.dispatch(ListeningAction.AuditionGeneratorTimbre("A"))
            testScheduler.runCurrent()
            vm.dispatch(ListeningAction.AuditionGeneratorTimbre("B"))
            testScheduler.runCurrent()
            vm.dispatch(ListeningAction.AuditionGeneratorTimbre("C"))
            // 最后一次点击立即生效（旧实现会在 A 处 `return`，B/C 被忽略，试听卡在 A 约 5.2 秒）。
            assertEquals("C", vm.state.value.previewingTimbre)
            testScheduler.runCurrent()
            // 三个音色都被请求过试听；旧实现只有 A 会请求。
            assertEquals(listOf("A", "B", "C"), renderer.previewCalls)
            // 旧的试听被立即取消（渲染器取消路径停止旧 AudioTrack）；最后一个 C 不被取消。
            assertEquals(listOf("CANCELLED:A", "CANCELLED:B"), renderer.cancelled)
            testScheduler.advanceUntilIdle()
            // 只有最后一次点击真正播完（快速连点只响最后一个）。
            assertEquals(listOf("C"), renderer.previewCompleted)
            // 试听完成后指示态清除。
            assertNull(vm.state.value.previewingTimbre)
        } finally { Dispatchers.resetMain() }
    }

    // ── 假实现 ──────────────────────────────────────────────────────────────

    /** 记录每次试听请求与完成/取消，previewTimbre 模拟约 5.2 秒可取消试听。 */
    private class AuditionCapturingRenderer : GeneratedAudioRenderer {
        val events = MutableStateFlow(RadioEngineState())
        override val radioEvents: Flow<RadioEngineState> = events
        val previewCalls = mutableListOf<String>()
        val previewCompleted = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        override suspend fun play(station: RadioStation, activeAmbient: List<AmbientSound>) {}
        override suspend fun pause() {}
        override suspend fun stop() {}
        override suspend fun previewTimbre(timbre: String) {
            previewCalls += timbre
            try {
                kotlinx.coroutines.delay(5200) // 模拟渲染 + 播放约 5.2 秒，可被取消。
            } catch (error: kotlinx.coroutines.CancellationException) {
                cancelled += "CANCELLED:$timbre"
                throw error
            }
            previewCompleted += timbre
        }
    }

    private class RecordingAudioController : ListeningAudioController {
        val events = MutableStateFlow(RadioEngineState())
        override val radioEvents: Flow<RadioEngineState> = events
        val playCalls = mutableListOf<Pair<String, Int>>()
        val pauseCalls = mutableListOf<Unit>()
        val stopRadioCalls = mutableListOf<Unit>()
        val stopAllCalls = mutableListOf<Unit>()
        override suspend fun setAmbient(tracks: List<AmbientSound>, playing: Boolean, masterVolume: Float) {}
        override suspend fun playRadio(station: RadioStation, trackIndex: Int) {
            playCalls += station.id to trackIndex
            // 模拟真实播放器事件序列：先 LOADING 再 PLAYING，且事件在不同调度时刻到达
            // （真实 Media3 的 prepare → 实际出声之间存在时间间隔）。
            events.value = RadioEngineState(PlaybackState.LOADING, station.id, trackIndex)
            kotlinx.coroutines.yield()
            events.value = RadioEngineState(PlaybackState.PLAYING, station.id, trackIndex)
        }
        override suspend fun pauseRadio() {
            pauseCalls += Unit
            events.value = RadioEngineState(PlaybackState.PAUSED)
        }
        override suspend fun stopRadio() {
            stopRadioCalls += Unit
            events.value = RadioEngineState(PlaybackState.IDLE)
        }
        override suspend fun stopAll() {
            stopAllCalls += Unit
            events.value = RadioEngineState(PlaybackState.IDLE)
        }
    }

    private class FakeGeneratedAudioRenderer : GeneratedAudioRenderer {
        val events = MutableStateFlow(RadioEngineState())
        override val radioEvents: Flow<RadioEngineState> = events
        val playCalls = mutableListOf<String>()
        val pauseCalls = mutableListOf<Unit>()
        val stopCalls = mutableListOf<Unit>()
        override suspend fun play(station: RadioStation, activeAmbient: List<AmbientSound>) { playCalls += station.id }
        override suspend fun pause() { pauseCalls += Unit }
        override suspend fun stop() { stopCalls += Unit }
    }

    private class CapturingGeneratedAudioRenderer : GeneratedAudioRenderer {
        val events = MutableStateFlow(RadioEngineState())
        override val radioEvents: Flow<RadioEngineState> = events
        var lastStation: RadioStation? = null
        override suspend fun play(station: RadioStation, activeAmbient: List<AmbientSound>) { lastStation = station }
        override suspend fun pause() {}
        override suspend fun stop() {}
    }
}
