package com.soundist.app

import android.content.Context
import com.soundist.feature.listening.AmbientSound
import com.soundist.feature.listening.PlaybackState
import com.soundist.feature.listening.RadioEngineState
import com.soundist.feature.listening.RadioStation
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the mature Kotlin generator as an audible fallback for devices where the native
 * generator cannot initialize, load a requested sample, or produce a non-silent stream.
 * Both backends consume the same RadioStation/GeneratedArrangement, so fallback preserves
 * scenes and advanced arrangement parameters instead of replacing the product with a demo.
 */
class FailoverGeneratedPlaybackController(context: Context) : GeneratedPlaybackController, Closeable {
    private val primary = NativeEngineGenerativeRenderer(context)
    private val fallback = NativeGeneratedAudioRenderer(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val switchMutex = Mutex()
    private val _events = MutableStateFlow(RadioEngineState())
    override val radioEvents: Flow<RadioEngineState> = _events.asStateFlow()

    @Volatile private var active: GeneratedPlaybackController = primary
    @Volatile private var nativeHealthy = true
    @Volatile private var desiredPlaying = false
    @Volatile private var outputVolume = 0.64f
    private var latestStation: RadioStation? = null
    private var latestAmbient: List<AmbientSound> = emptyList()

    init {
        primary.prewarmDefaultSamples()
        scope.launch {
            primary.radioEvents.collect { event ->
                if (active !== primary) return@collect
                if (event.state == PlaybackState.ERROR && desiredPlaying) {
                    switchToFallback(event.errorMessage ?: "原生持续声场没有产生可听输出")
                } else {
                    _events.value = event
                }
            }
        }
        scope.launch {
            fallback.radioEvents.collect { event ->
                if (active === fallback) _events.value = event
            }
        }
    }

    override suspend fun play(station: RadioStation, activeAmbient: List<AmbientSound>) {
        latestStation = station
        latestAmbient = activeAmbient.toList()
        desiredPlaying = true
        val target = if (nativeHealthy) primary else fallback
        active = target
        target.setVolume(outputVolume)
        try {
            target.play(station, latestAmbient)
        } catch (error: Throwable) {
            if (target === primary) switchToFallback(error.message ?: "原生持续声场启动失败")
            else throw error
        }
    }

    override suspend fun pause() {
        desiredPlaying = false
        active.pause()
    }

    override suspend fun stop() {
        desiredPlaying = false
        latestStation = null
        latestAmbient = emptyList()
        active.stop()
    }

    override suspend fun previewTimbre(timbre: String) {
        if (!nativeHealthy) {
            fallback.previewTimbre(timbre)
            return
        }
        runCatching { primary.previewTimbre(timbre) }
            .onFailure {
                nativeHealthy = false
                fallback.setVolume(outputVolume)
                fallback.previewTimbre(timbre)
            }
    }

    override fun setVolume(value: Float) {
        outputVolume = value.coerceIn(0f, 1f)
        primary.setVolume(outputVolume)
        fallback.setVolume(outputVolume)
    }

    override fun resumeExternal() {
        desiredPlaying = true
        active.resumeExternal()
    }

    override fun pauseExternal() {
        desiredPlaying = false
        active.pauseExternal()
    }

    override fun stopExternal() {
        desiredPlaying = false
        latestStation = null
        latestAmbient = emptyList()
        active.stopExternal()
    }

    private suspend fun switchToFallback(reason: String) = switchMutex.withLock {
        if (active !== primary || !desiredPlaying) return@withLock
        val station = latestStation ?: return@withLock
        nativeHealthy = false
        runCatching { primary.stop() }
        active = fallback
        fallback.setVolume(outputVolume)
        _events.value = RadioEngineState(PlaybackState.LOADING, station.id, errorMessage = "正在切换兼容播放")
        try {
            fallback.play(station, latestAmbient)
        } catch (fallbackError: Throwable) {
            _events.value = RadioEngineState(
                PlaybackState.ERROR,
                station.id,
                errorMessage = fallbackError.message ?: reason,
            )
        }
    }

    override fun close() {
        desiredPlaying = false
        scope.cancel()
        primary.close()
        fallback.close()
    }
}
