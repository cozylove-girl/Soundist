package com.soundist.app

import android.content.Context

enum class MotionPreference { SYSTEM, REDUCED, FULL }

data class AppPreferenceState(
    val backgroundPlayback: Boolean = true,
    val autoResume: Boolean = false,
    val fadeSeconds: Int = 2,
    val motion: MotionPreference = MotionPreference.SYSTEM,
    val haptics: Boolean = true,
    val confirmDestructive: Boolean = true,
)

class AppPreferences(context: Context) {
    private val storage = context.applicationContext.getSharedPreferences("soundist.preferences.v1", Context.MODE_PRIVATE)

    fun load() = AppPreferenceState(
        backgroundPlayback = storage.getBoolean("background_playback", true),
        autoResume = storage.getBoolean("auto_resume", false),
        fadeSeconds = storage.getInt("fade_seconds", 2).takeIf { it in setOf(0, 1, 2, 4) } ?: 2,
        motion = runCatching { MotionPreference.valueOf(storage.getString("motion", null) ?: "SYSTEM") }.getOrDefault(MotionPreference.SYSTEM),
        haptics = storage.getBoolean("haptics", true),
        confirmDestructive = storage.getBoolean("confirm_destructive", true),
    )

    fun save(value: AppPreferenceState) {
        storage.edit()
            .putBoolean("background_playback", value.backgroundPlayback)
            .putBoolean("auto_resume", value.autoResume)
            .putInt("fade_seconds", value.fadeSeconds)
            .putString("motion", value.motion.name)
            .putBoolean("haptics", value.haptics)
            .putBoolean("confirm_destructive", value.confirmDestructive)
            .apply()
    }

    fun clear() = storage.edit().clear().commit()
}
