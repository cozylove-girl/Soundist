package com.soundist.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.soundist.feature.productivity.SessionPhase
import java.util.concurrent.TimeUnit

private const val REMINDER_CHANNEL_ID = "reminders"
private const val FOCUS_CHANNEL_ID = "focus-transitions-v2"
private const val FOCUS_ALARM_ID = "focus-stage-transition"
private const val EXTRA_PHASE = "phase"
private const val EXTRA_TRIGGER_AT = "triggerAt"
private const val FOCUS_ALERT_PREFS = "soundist-focus-alerts"
private const val LAST_DELIVERED_TRIGGER = "lastDeliveredTrigger"

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        if (!context.canPostNotifications()) return
        NotificationManagerCompat.from(context).notify(
            id.hashCode(),
            NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Soundist")
                .setContentText(intent.getStringExtra("title") ?: "计时已结束")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(context.launchPendingIntent())
                .setAutoCancel(true)
                .build(),
        )
    }
}

class FocusTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val phase = runCatching {
            SessionPhase.valueOf(intent.getStringExtra(EXTRA_PHASE).orEmpty())
        }.getOrDefault(SessionPhase.FOCUS)
        ReminderScheduler.deliverFocusTransition(context, phase, intent.getLongExtra(EXTRA_TRIGGER_AT, 0L))
    }
}

object ReminderScheduler {
    fun ensureChannel(context: Context) {
        FocusTransitionCue.preload(context)
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(REMINDER_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("计时与提醒")
                .build(),
        )
        val focusChannel = NotificationChannel(
            FOCUS_CHANNEL_ID,
            "专注阶段提示",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "专注或休息倒计时结束时发出一声短提示"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(focusChannel)
    }

    fun schedule(context: Context, id: String, title: String, at: Long, exact: Boolean) {
        requireFuture(at)
        if (!context.canPostNotifications()) throw SecurityException("需要通知权限才能创建提醒")
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("id", id).putExtra("title", title)
        val pending = PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        scheduleAlarmOrWork(
            context, at, exact, pending, "reminder-$id",
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(at - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("id" to id, "title" to title))
                .build(),
        )
    }

    fun cancel(context: Context, id: String) {
        cancelAlarm(context, id.hashCode(), Intent(context, ReminderReceiver::class.java))
        WorkManager.getInstance(context).cancelUniqueWork("reminder-$id")
    }

    fun scheduleFocusTransition(context: Context, phase: SessionPhase, at: Long) {
        // The transition cue is useful even when notifications are disabled.
        // Notification permission is checked only when the notification is posted.
        requireFuture(at)
        val intent = Intent(context, FocusTransitionReceiver::class.java)
            .putExtra(EXTRA_PHASE, phase.name)
            .putExtra(EXTRA_TRIGGER_AT, at)
        val pending = PendingIntent.getBroadcast(context, FOCUS_ALARM_ID.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        scheduleAlarmOrWork(
            context, at, true, pending, FOCUS_ALARM_ID,
            OneTimeWorkRequestBuilder<FocusTransitionWorker>()
                .setInitialDelay(at - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(EXTRA_PHASE to phase.name, EXTRA_TRIGGER_AT to at))
                .build(),
        )
    }

    fun deliverFocusTransition(context: Context, phase: SessionPhase, triggerAtEpochMillis: Long) {
        val token = triggerAtEpochMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        synchronized(this) {
            val prefs = context.getSharedPreferences(FOCUS_ALERT_PREFS, Context.MODE_PRIVATE)
            if (prefs.getLong(LAST_DELIVERED_TRIGGER, Long.MIN_VALUE) == token) return
            prefs.edit().putLong(LAST_DELIVERED_TRIGGER, token).commit()
        }
        // The in-app cue is independent of notification permission.  It uses the
        // media stream without requesting audio focus, so ambient/radio playback
        // keeps running and the cue is mixed briefly on top.
        FocusTransitionCue.play(context, phase)
        if (!context.canPostNotifications()) return
        val title = if (phase == SessionPhase.FOCUS) "专注结束" else "休息结束"
        val detail = if (phase == SessionPhase.FOCUS) "停一停，让注意力自然回落。" else "准备好时，继续下一段声境。"
        NotificationManagerCompat.from(context).notify(
            FOCUS_ALARM_ID.hashCode(),
            NotificationCompat.Builder(context, FOCUS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(detail)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(context.launchPendingIntent())
                .setAutoCancel(true)
                .setTimeoutAfter(20_000L)
                .build(),
        )
    }

    fun cancelFocusTransition(context: Context) {
        cancelAlarm(context, FOCUS_ALARM_ID.hashCode(), Intent(context, FocusTransitionReceiver::class.java))
        WorkManager.getInstance(context).cancelUniqueWork(FOCUS_ALARM_ID)
    }

    private fun requireFuture(at: Long) {
        require(at > System.currentTimeMillis()) { "提醒时间必须晚于当前时间" }
    }

    private fun scheduleAlarmOrWork(context: Context, at: Long, exact: Boolean, pending: PendingIntent, workName: String, work: OneTimeWorkRequest) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val canScheduleExactly = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
        if (exact && canScheduleExactly) {
            WorkManager.getInstance(context).cancelUniqueWork(workName)
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            alarms.cancel(pending)
            WorkManager.getInstance(context).enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, work)
        }
    }

    private fun cancelAlarm(context: Context, requestCode: Int, intent: Intent) {
        val pending = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) {
            context.getSystemService(AlarmManager::class.java).cancel(pending)
            pending.cancel()
        }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        ReminderReceiver().onReceive(applicationContext, Intent().putExtra("id", inputData.getString("id")).putExtra("title", inputData.getString("title")))
        return Result.success()
    }
}

class FocusTransitionWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        FocusTransitionReceiver().onReceive(
            applicationContext,
            Intent()
                .putExtra(EXTRA_PHASE, inputData.getString(EXTRA_PHASE))
                .putExtra(EXTRA_TRIGGER_AT, inputData.getLong(EXTRA_TRIGGER_AT, 0L)),
        )
        return Result.success()
    }
}

/**
 * Process-wide, preloaded cue player. SoundPool does not request audio focus, so
 * the short cue overlays the current Soundist mix without pausing or ducking it.
 * Focus and break endings use audibly different pitch signatures from the same
 * calibrated source asset; a short haptic pulse is used only if playback fails.
 */
private object FocusTransitionCue {
    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private var pool: SoundPool? = null
    private var sampleId = 0
    private var loaded = false
    private var pending: Pair<Context, SessionPhase>? = null

    fun preload(context: Context) {
        val app = context.applicationContext
        synchronized(lock) {
            if (pool != null) return
            val created = SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()
            created.setOnLoadCompleteListener { soundPool, id, status ->
                val queued = synchronized(lock) {
                    if (soundPool !== pool || id != sampleId) return@setOnLoadCompleteListener
                    loaded = status == 0
                    pending.also { pending = null }
                }
                if (status == 0 && queued != null) playLoaded(queued.first, queued.second)
                else if (status != 0 && queued != null) vibrateFallback(queued.first)
            }
            pool = created
            sampleId = created.load(app, R.raw.focus_transition_chime, 1)
        }
    }

    fun play(context: Context, phase: SessionPhase) {
        val app = context.applicationContext
        preload(app)
        val playNow = synchronized(lock) {
            if (loaded) true else {
                pending = app to phase
                false
            }
        }
        if (playNow) {
            playLoaded(app, phase)
            return
        }
        // A cold background process may need a moment to load the sample. If it
        // cannot, give one restrained tactile confirmation rather than failing silently.
        handler.postDelayed({
            val fallback = synchronized(lock) {
                if (!loaded && pending?.second == phase) pending.also { pending = null } else null
            }
            fallback?.let { vibrateFallback(it.first) }
        }, 700L)
    }

    private fun playLoaded(context: Context, phase: SessionPhase) {
        val (soundPool, id) = synchronized(lock) { pool to sampleId }
        if (soundPool == null || id == 0) {
            vibrateFallback(context)
            return
        }
        // Focus closes with one settled low cue. A break closes with a lighter
        // two-note rise, so the phases remain recognizable without speech.
        val firstRate = if (phase == SessionPhase.FOCUS) 0.92f else 1.10f
        val firstVolume = if (phase == SessionPhase.FOCUS) 0.82f else 0.72f
        if (soundPool.play(id, firstVolume, firstVolume, 1, 0, firstRate) == 0) {
            Log.w("SoundistFocusChime", "SoundPool rejected the transition cue")
            vibrateFallback(context)
            return
        }
        if (phase == SessionPhase.BREAK) {
            handler.postDelayed({
                soundPool.play(id, 0.62f, 0.62f, 1, 0, 1.30f)
            }, 130L)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrateFallback(context: Context) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(36L, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }.onFailure { Log.w("SoundistFocusChime", "Unable to deliver haptic fallback", it) }
    }
}

private fun Context.canPostNotifications() =
    Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun Context.launchPendingIntent(): PendingIntent? =
    packageManager.getLaunchIntentForPackage(packageName)?.let {
        PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
