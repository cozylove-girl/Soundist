package com.soundist.feature.listening

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * B2：自定义封面即时刷新。保存（新建/编辑）自定义频道后，artworkUri 必须立即写回 state，
 * 管理列表项（RadioScreen 的 RadioArtwork）以 station.artworkUri 为键重新解码，无需退出重进。
 * 本测试锁定「artworkUri 写回 state」这一环节；列表监听与解码见 RadioScreen。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomRadioSaveStateTest {

    private fun TestScope.newViewModel(initial: ListeningState = ListeningState()): ListeningViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return ListeningViewModel(StatefulListeningRepository(initial)).also { testScheduler.advanceUntilIdle() }
    }

    @Test
    fun `saving new stream channel keeps picked artwork in state immediately`() = runTest {
        try {
            val vm = newViewModel()
            vm.dispatch(ListeningAction.OpenCustomRadio(RadioSourceKind.STREAM))
            vm.dispatch(ListeningAction.UpdateRadioDraft {
                it.copy(name = "深夜书房", url = "https://example.com/live.m3u8", imageUrl = "content://media/images/42")
            })
            vm.dispatch(ListeningAction.SaveCustomRadio)
            testScheduler.advanceUntilIdle()
            val saved = vm.state.value.stations.firstOrNull { it.name == "深夜书房" }
            assertEquals("content://media/images/42", saved?.artworkUri)
            assertEquals(RadioSourceKind.STREAM, saved?.sourceKind)
            // 保存即选中新频道（现在播放卡片同样使用新封面渲染）。
            assertEquals(saved?.id, vm.state.value.selectedStationId)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun `editing existing channel refreshes artwork in state immediately`() = runTest {
        try {
            val existing = RadioStation(
                id = "custom-edit", name = "旧名", description = "d", group = RadioGroup.CUSTOM,
                source = "直接音频流", url = "https://old.example/live.mp3", sourceKind = RadioSourceKind.STREAM,
                artworkUri = "content://media/images/1", custom = true,
            )
            val vm = newViewModel(ListeningState(stations = listOf(existing)))
            vm.dispatch(ListeningAction.EditRadio(existing))
            vm.dispatch(ListeningAction.UpdateRadioDraft { it.copy(imageUrl = "content://media/images/2") })
            vm.dispatch(ListeningAction.SaveCustomRadio)
            testScheduler.advanceUntilIdle()
            val edited = vm.state.value.stations.firstOrNull { it.id == "custom-edit" }
            assertEquals("content://media/images/2", edited?.artworkUri)
            assertEquals("旧名", edited?.name)
            assertEquals(1, vm.state.value.stations.size)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun `clearing artwork back to null also reflects in state`() = runTest {
        try {
            val existing = RadioStation(
                id = "custom-edit", name = "旧名", description = "d", group = RadioGroup.CUSTOM,
                source = "直接音频流", url = "https://old.example/live.mp3", sourceKind = RadioSourceKind.STREAM,
                artworkUri = "content://media/images/1", custom = true,
            )
            val vm = newViewModel(ListeningState(stations = listOf(existing)))
            vm.dispatch(ListeningAction.EditRadio(existing))
            vm.dispatch(ListeningAction.UpdateRadioDraft { it.copy(imageUrl = null) })
            vm.dispatch(ListeningAction.SaveCustomRadio)
            testScheduler.advanceUntilIdle()
            assertEquals(null, vm.state.value.stations.firstOrNull { it.id == "custom-edit" }?.artworkUri)
        } finally { Dispatchers.resetMain() }
    }
}
