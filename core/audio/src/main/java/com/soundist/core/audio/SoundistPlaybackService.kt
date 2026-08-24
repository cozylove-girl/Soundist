package com.soundist.core.audio

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "soundist-playback"
private const val NOTIFICATION_ID = 0x50
private const val ACTION_TOGGLE_MASTER = "soundist.playback.toggle_master"
private const val ACTION_TOGGLE_AMBIENT = "soundist.playback.toggle_ambient"
private const val ACTION_TOGGLE_RADIO = "soundist.playback.toggle_radio"

private val toggleMasterCommand = SessionCommand(ACTION_TOGGLE_MASTER, Bundle.EMPTY)
private val toggleAmbientCommand = SessionCommand(ACTION_TOGGLE_AMBIENT, Bundle.EMPTY)
private val toggleRadioCommand = SessionCommand(ACTION_TOGGLE_RADIO, Bundle.EMPTY)

class SoundistPlaybackService : MediaSessionService() {
    private lateinit var engine: Media3AudioEngine
    private var session: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sessionPlayer: SessionBridgePlayer? = null

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(toggleMasterCommand)
                .add(toggleAmbientCommand)
                .add(toggleRadioCommand)
                .build()
            return MediaSession.ConnectionResult.accept(
                commands,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val state = engine.state.value
            when (customCommand.customAction) {
                ACTION_TOGGLE_MASTER -> if (state.masterPlaying) engine.pause() else engine.play()
                ACTION_TOGGLE_AMBIENT -> if (state.ambientPlaying) {
                    engine.pauseAmbientGraph()
                } else {
                    engine.playAmbientGraph()
                }
                ACTION_TOGGLE_RADIO -> if (state.radioPlaying || state.externalPlaying) {
                    engine.pauseRadioGraph()
                } else {
                    engine.playRadioGraph()
                }
                else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("播放")
                .build(),
        )
        engine = Media3AudioRuntime.get(this)
        sessionPlayer = SessionBridgePlayer(engine)
        setMediaNotificationProvider(object : MediaNotification.Provider {
            override fun createNotification(
                mediaSession: MediaSession,
                mediaButtonPreferences: ImmutableList<CommandButton>,
                actionFactory: MediaNotification.ActionFactory,
                onNotificationChangedCallback: MediaNotification.Provider.Callback,
            ) = MediaNotification(NOTIFICATION_ID, buildControlNotification(engine.state.value))

            override fun handleCustomCommand(
                mediaSession: MediaSession,
                action: String,
                extras: Bundle,
            ) = false
        })
        val initialButtons = playbackButtons(engine.state.value)
        session = MediaSession.Builder(this, checkNotNull(sessionPlayer))
            .setCallback(sessionCallback)
            .setCustomLayout(initialButtons)
            .setMediaButtonPreferences(initialButtons)
            .build()

        startForeground(NOTIFICATION_ID, buildControlNotification(engine.state.value))
        serviceScope.launch {
            engine.state.collectLatest { state ->
                sessionPlayer?.publish(state)
                val buttons = playbackButtons(state)
                session?.setCustomLayout(buttons)
                session?.setMediaButtonPreferences(buttons)
                runCatching { NotificationManagerCompat.from(this@SoundistPlaybackService).notify(NOTIFICATION_ID, buildControlNotification(state)) }
                // Keep paused selections visible so they can be resumed from the system UI.
                if (!engine.hasPlayableSelection()) pauseAllPlayersAndStopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = engine.state.value
        when (intent?.action) {
            ACTION_TOGGLE_MASTER -> if (state.masterPlaying) engine.pause() else engine.play()
            ACTION_TOGGLE_AMBIENT -> if (state.ambientPlaying) engine.pauseAmbientGraph() else engine.playAmbientGraph()
            ACTION_TOGGLE_RADIO -> if (state.radioPlaying || state.externalPlaying) engine.pauseRadioGraph() else engine.playRadioGraph()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        serviceScope.cancel()
        session?.release()
        sessionPlayer?.release()
        super.onDestroy()
    }

    private fun buildControlNotification(state: AudioState): android.app.Notification {
        val radioPlaying = state.radioPlaying || state.externalPlaying
        val ambientTitle = state.ambientLabel.ifBlank { "未选择环境声" }
        val radioTitle = state.radioLabel.ifBlank { "未选择电台" }
        val detail = "环境声：$ambientTitle\n电台：$radioTitle"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            // Status-bar icons are monochrome masks. A launcher/adaptive icon is not a valid
            // small icon and is rendered inconsistently by OEM skins such as MagicOS.
            .setSmallIcon(R.drawable.ic_notification_soundist)
            .setContentTitle("Soundist 声境")
            .setContentText(if (state.radioLabel.isBlank()) "环境声：$ambientTitle" else state.radioLabel)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                if (state.ambientPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (state.ambientPlaying) "环境声 · 暂停" else "环境声 · 继续",
                controlPendingIntent(ACTION_TOGGLE_AMBIENT, 1),
            )
            .addAction(
                if (state.masterPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (state.masterPlaying) "全部 · 暂停" else "全部 · 继续",
                controlPendingIntent(ACTION_TOGGLE_MASTER, 2),
            )
            .addAction(
                if (radioPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (radioPlaying) "电台 · 暂停" else "电台 · 继续",
                controlPendingIntent(ACTION_TOGGLE_RADIO, 3),
            )
            .build()
    }

    private fun controlPendingIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, SoundistPlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private fun playbackButtons(state: AudioState): List<CommandButton> {
    val radioPlaying = state.radioPlaying || state.externalPlaying
    return listOf(
        CommandButton.Builder(if (state.ambientPlaying) CommandButton.ICON_PAUSE else CommandButton.ICON_SIGNAL)
            .setSessionCommand(toggleAmbientCommand)
            .setDisplayName(if (state.ambientPlaying) "暂停环境声" else "继续环境声")
            .setSlots(CommandButton.SLOT_BACK)
            .build(),
        CommandButton.Builder(if (state.masterPlaying) CommandButton.ICON_PAUSE else CommandButton.ICON_PLAY)
            .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
            .setDisplayName(if (state.masterPlaying) "暂停全部" else "继续全部")
            .setSlots(CommandButton.SLOT_CENTRAL)
            .build(),
        CommandButton.Builder(if (radioPlaying) CommandButton.ICON_PAUSE else CommandButton.ICON_RADIO)
            .setSessionCommand(toggleRadioCommand)
            .setDisplayName(if (radioPlaying) "暂停电台" else "继续电台")
            .setSlots(CommandButton.SLOT_FORWARD)
            .build(),
    )
}

/** Media keys control the complete Soundist graph, not only the radio ExoPlayer. */
private class SessionBridgePlayer(
    private val engine: Media3AudioEngine,
) : androidx.media3.common.SimpleBasePlayer(android.os.Looper.getMainLooper()) {
    private var audioState = engine.state.value

    override fun getState(): State {
        val radio = audioState.radioLabel.ifBlank { "未选择电台" }
        val ambient = audioState.ambientLabel.ifBlank { "未选择环境声" }
        val metadata = MediaMetadata.Builder()
            .setTitle(if (audioState.radioLabel.isNotBlank()) audioState.radioLabel else "Soundist 声境")
            .setArtist("环境声：$ambient · 电台：$radio")
            .setIsPlayable(true)
            .build()
        val item = MediaItem.Builder()
            .setMediaId("soundist-composition")
            .setMediaMetadata(metadata)
            .build()
        val itemData = MediaItemData.Builder("soundist-composition")
            .setMediaItem(item)
            .setMediaMetadata(metadata)
            .setDurationUs(C.TIME_UNSET)
            .setIsSeekable(false)
            .build()
        return State.Builder()
            .setAvailableCommands(
                Player.Commands.Builder().addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_STOP,
                    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_GET_METADATA,
                ).build(),
            )
            .setPlaylist(listOf(itemData))
            .setCurrentMediaItemIndex(0)
            .setPlayWhenReady(audioState.masterPlaying, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (engine.hasPlayableSelection()) Player.STATE_READY else Player.STATE_IDLE)
            .build()
    }

    fun publish(state: AudioState) {
        audioState = state
        invalidateState()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) engine.play() else engine.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        engine.stop()
        return Futures.immediateVoidFuture()
    }
}
