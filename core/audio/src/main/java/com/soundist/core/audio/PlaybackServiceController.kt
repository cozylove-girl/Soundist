package com.soundist.core.audio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

sealed interface PlaybackServiceStartResult {
    data object Started : PlaybackServiceStartResult
    data object NothingPlayable : PlaybackServiceStartResult
    data object NotificationPermissionRequired : PlaybackServiceStartResult
    data class Failed(val reason: String) : PlaybackServiceStartResult
}

/** Precise app-layer API for starting/stopping the MediaSessionService. */
class PlaybackServiceController(context: Context) {
    private val app = context.applicationContext
    fun startForActivePlayback(): PlaybackServiceStartResult {
        val engine = Media3AudioRuntime.get(app)
        if (!engine.hasPlayableSelection() || !engine.state.value.masterPlaying) return PlaybackServiceStartResult.NothingPlayable
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return PlaybackServiceStartResult.NotificationPermissionRequired
        return runCatching {
            ContextCompat.startForegroundService(app, Intent(app, SoundistPlaybackService::class.java))
            PlaybackServiceStartResult.Started
        }.getOrElse { PlaybackServiceStartResult.Failed(it.message ?: it.javaClass.simpleName) }
    }
    fun stop() { app.stopService(Intent(app, SoundistPlaybackService::class.java)) }
}
