package com.soundist.feature.listening

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * task2 验收：环境声轨播放失败必须回流 UI——失败的声音不再显示「播放中」、
 * 从混音移除但保留音量，其余声音继续，并给出可理解错误信息。
 * 同时锁定「显式移除保留音量」与「ToggleSound 清音量=0」的语义区别。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AmbientPlaybackFailureTest {

    private fun TestScope.newViewModel(
        initial: ListeningState = ListeningState(),
        audio: ListeningAudioController = AmbientFailingAudioController(),
    ): ListeningViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return ListeningViewModel(StatefulListeningRepository(initial), audio)
            .also { testScheduler.advanceUntilIdle() } // 让 init 完成加载并订阅 ambientErrors 回流。
    }

    @Test
    fun `failed ambient sound leaves the mix but keeps volume while others keep playing`() = runTest {
        try {
            val waves = SoundCatalog.items.first { it.id == "waves" }.copy(active = true, volume = 0.6f)
            val cafe = SoundCatalog.items.first { it.id == "cafe" }.copy(active = true, volume = 0.5f)
            val audio = AmbientFailingAudioController()
            val vm = newViewModel(
                initial = ListeningState(
                    sounds = SoundCatalog.items.map { s -> when (s.id) { "waves" -> waves; "cafe" -> cafe; else -> s } },
                    ambientPlaying = true,
                    globalPlaying = true,
                ),
                audio = audio,
            )

            assertTrue(vm.state.value.sounds.first { it.id == "waves" }.active)
            assertTrue(vm.state.value.ambientPlaying)

            // 引擎播放器事件：环境声轨解码失败回流。
            audio.emitAmbientError(AmbientErrorEvent("waves", "无法解码该声音文件"))
            testScheduler.advanceUntilIdle()

            // 失败的声音不再显示「播放中」：active=false 从混音移除，音量保留（不是 ToggleSound 的清 0）。
            val failed = vm.state.value.sounds.first { it.id == "waves" }
            assertFalse(failed.active)
            assertEquals(0.6f, failed.volume)
            // 其余声音继续在混音与播放中。
            assertTrue(vm.state.value.sounds.first { it.id == "cafe" }.active)
            assertTrue(vm.state.value.ambientPlaying)
            // 给出可理解错误信息。
            assertEquals("无法解码该声音文件", vm.state.value.operationError)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun `last failing sound ends the ambient session`() = runTest {
        try {
            val waves = SoundCatalog.items.first { it.id == "waves" }.copy(active = true, volume = 0.6f)
            val audio = AmbientFailingAudioController()
            val vm = newViewModel(
                initial = ListeningState(
                    sounds = SoundCatalog.items.map { if (it.id == "waves") waves else it },
                    ambientPlaying = true,
                    globalPlaying = true,
                ),
                audio = audio,
            )
            assertTrue(vm.state.value.ambientPlaying)

            // 唯一的声音失败：环境声会话结束，不再显示任何「播放中」。
            audio.emitAmbientError(AmbientErrorEvent("waves", "无法解码该声音文件"))
            testScheduler.advanceUntilIdle()

            assertFalse(vm.state.value.sounds.first { it.id == "waves" }.active)
            assertFalse(vm.state.value.ambientPlaying)
            assertFalse(vm.state.value.globalPlaying)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun `explicit remove keeps volume while toggle zeroes it`() = runTest {
        try {
            val waves = SoundCatalog.items.first { it.id == "waves" }.copy(active = true, volume = 0.6f)
            val vm = newViewModel(
                initial = ListeningState(
                    sounds = SoundCatalog.items.map { if (it.id == "waves") waves else it },
                    ambientPlaying = true,
                ),
            )

            // 显式移除：active=false 但音量保留（0.6 不被清 0）。
            vm.dispatch(ListeningAction.RemoveSound("waves"))
            testScheduler.advanceUntilIdle()
            val afterRemove = vm.state.value.sounds.first { it.id == "waves" }
            assertFalse(afterRemove.active)
            assertEquals(0.6f, afterRemove.volume)

            // ToggleSound（Web 混音行原按钮语义）重新打开时把音量重置为 58%。
            vm.dispatch(ListeningAction.ToggleSound("waves"))
            testScheduler.advanceUntilIdle()
            val afterToggle = vm.state.value.sounds.first { it.id == "waves" }
            assertTrue(afterToggle.active)
            assertEquals(0.58f, afterToggle.volume)
        } finally { Dispatchers.resetMain() }
    }

    /** 假控制器：可主动推送环境声轨失败事件，驱动 ViewModel 状态机。 */
    private class AmbientFailingAudioController : ListeningAudioController {
        private val errorFlow = MutableSharedFlow<AmbientErrorEvent>(extraBufferCapacity = 1)
        private val radio = MutableStateFlow(RadioEngineState())
        override val radioEvents: Flow<RadioEngineState> = radio
        override val ambientErrors: Flow<AmbientErrorEvent> = errorFlow
        override suspend fun setAmbient(tracks: List<AmbientSound>, playing: Boolean, masterVolume: Float) {}
        override suspend fun playRadio(station: RadioStation, trackIndex: Int) {}
        override suspend fun pauseRadio() {}
        override suspend fun stopRadio() {}
        override suspend fun stopAll() {}
        fun emitAmbientError(event: AmbientErrorEvent) = errorFlow.tryEmit(event)
    }
}
