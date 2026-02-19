package com.example.codeonly.util

import android.content.Context
import android.content.SharedPreferences

class Preferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    var hasCompletedSetup: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETE, value).apply()

    var lastProviderId: String
        get() = prefs.getString(KEY_LAST_PROVIDER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_PROVIDER, value).apply()

    var lastModelId: String
        get() = prefs.getString(KEY_LAST_MODEL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_MODEL, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "opencode_chat_prefs"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
        private const val KEY_LAST_PROVIDER = "last_provider"
        private const val KEY_LAST_MODEL = "last_model"
    }
}
