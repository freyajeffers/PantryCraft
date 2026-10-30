package com.jeffers.pantrycraft.feature.camera.vision

import android.content.Context

object LiveVisionPreferences {
    private const val PREFS_NAME = "pantrycraft_live_vision_prefs"
    private const val KEY_GEMINI_LIVE_ENABLED = "gemini_live_vision_enabled"

    fun isGeminiLiveEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_GEMINI_LIVE_ENABLED, false)

    fun setGeminiLiveEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GEMINI_LIVE_ENABLED, enabled)
            .apply()
    }
}
