package com.soundist.core.audio

import androidx.media3.common.Player
import com.soundist.core.model.MixTrack
import kotlinx.coroutines.flow.StateFlow

/** 电台播放状态。全部来自 ExoPlayer 播放器事件（Player.Listener），不允许乐观置位。 */
enum class RadioPlaybackState { IDLE, LOADING, BUFFERING, READY, PLAYING, PAUSED, ENDED, ERROR }

/** 电台错误类型清单（同步抛出的宿主错误 + 播放器异步错误映射）。 */
enum class RadioErrorType {
    /** 本地文件缺失或不可访问（file:// / content:// 不存在、asset 缺失）。 */
    FILE_MISSING,
    /** 离线音频包尚未安装（曲目没有本地资产）。 */
    PACK_NOT_INSTALLED,
    /** 离线包校验不匹配。 */
    CHECKSUM_MISMATCH,
    /** 容器/内容类型不支持。 */
    UNSUPPORTED_FORMAT,
    /** 解码器初始化或解码失败。 */
    DECODER_ERROR,
    /** 文件权限已撤销（URI 权限失效）。 */
    PERMISSION_REVOKED,
    /** 音频焦点被系统拒绝。 */
    AUDIO_FOCUS_DENIED,
    /** 其他/未归类错误。 */
    UNKNOWN,
}

/** 带错误类型的电台播放异常：宿主（控制器）在启动电台时做同步校验并直接抛出。 */
class RadioPlaybackException(val type: RadioErrorType, message: String) : Exception(message)

/** 电台播放列表中的一个可播放源。id 由宿主提供（官方曲目 id 或本地文件 uri），用于失败跳过。 */
data class RadioTrackSource(val id: String, val stationIndex: Int, val uri: String)

data class AudioState(
 val tracks:List<MixTrack> = emptyList(),
 val radioUri:String?=null,
 val ambientPlaying:Boolean=false,
 /** 电台是否正在出声（由播放器事件派生：LOADING/BUFFERING/PLAYING 时为 true）。 */
 val radioPlaying:Boolean=false,
 val externalSelected:Boolean=false,
 val externalPlaying:Boolean=false,
 val masterPlaying:Boolean=false,
 val muted:Boolean=false,
 val masterVolume:Float=.8f,
 val ambientVolume:Float=1f,
 val radioVolume:Float=.8f,
 val error:String?=null,
 /** 环境声轨解码失败（soundId → 错误信息，来自每个环境声播放器的 Player.Listener）。
  *  失败的声音不再计入播放中：UI 应把它从混音移除并展示错误，其余声音继续。 */
 val ambientErrors:Map<String,String> = emptyMap(),
 // ── 电台真实播放状态（播放器事件回流）──
 val radioPlayback:RadioPlaybackState=RadioPlaybackState.IDLE,
 /** 当前曲目在可播放列表中的索引（引擎内部坐标）。 */
 val radioTrackIndex:Int=0,
 /** 当前曲目在频道（station.tracks / station.localAudio）中的索引（持久化坐标）。 */
 val radioStationIndex:Int=0,
 val radioTrackCount:Int=0,
 val radioItemId:String?=null,
 /** 电台错误文本（终态错误常驻；跳曲等过渡错误仅短暂存在）。 */
 val radioError:String?=null,
 val radioErrorType:RadioErrorType?=null,
 /** 通知/锁屏展示用的人类可读上下文。 */
 val ambientLabel:String="",
 val radioLabel:String="",
)

/** The active ambient backend may be Media3 or miniaudio; notification controls use this bridge. */
interface AmbientGraphController {
 fun resumeAmbientGraph()
 fun pauseAmbientGraph()
}

/** Bridges a non-Media3 renderer (the offline generated station) into the single app audio graph. */
interface ExternalPlaybackController {
 fun resumeExternal()
 fun pauseExternal()
 fun stopExternal()
 /** 同步主音量等效值（masterVolume × radioVolume，含静音/duck 系数）给外部渲染器，使其跟随主音量淡出与 duck。 */
 fun setVolume(value: Float)
}

/** 环境声后端的中断协调接口（miniaudio 后端需要；Media3 引擎内部已处理焦点）。
 *  AudioFocus 属于整个 Soundist 音频会话，而不是某一个播放器实现；Media3AudioEngine 持有
 *  AudioInterruptionManager，在焦点/拔耳机事件时回调此监听器，让当前 ambient backend 同步暂停/恢复。 */
interface AmbientInterruptionListener {
    /** 永久丢失焦点：暂停，取消自动恢复。 */
    fun onFocusLoss()
    /** 短暂丢失（来电等）：暂停，记录恢复意图（重新获得焦点时按意图决定是否恢复）。 */
    fun onFocusLossTransient()
    /** 重新获得焦点：仅在 transient loss 且用户播放意图仍为 true 时恢复。 */
    fun onFocusGain()
    /** ACTION_AUDIO_BECOMING_NOISY（拔耳机等）：立即暂停，不得继续从扬声器播放。 */
    fun onBecomingNoisy()
}

interface AudioEngine {
 val state:StateFlow<AudioState>
 suspend fun setTrack(soundId:String,assetUri:String,volume:Float)
 suspend fun removeTrack(soundId:String)
 suspend fun setVolume(soundId:String,volume:Float)
 suspend fun playRadio(uri:String)
 fun setMasterVolume(volume:Float)
 fun setAmbientVolume(volume:Float)
 fun setRadioVolume(volume:Float)
 fun playAmbient(); fun pauseAmbient()
 fun playRadio(); fun pauseRadio()
 /** 停止电台并清空会话回到 IDLE（区别于暂停）。 */
 fun stopRadio()
 fun play(); fun pause(); fun stop(); fun setMuted(muted:Boolean)
}
