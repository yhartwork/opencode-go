package com.example.codeonly.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LogLevel {
    Debug,
    Info,
    Warn,
    Error
}

data class LogEntry(
    val time: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
)

object AppLog {
    private const val MAX_ENTRIES = 200
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun debug(tag: String, message: String) = log(LogLevel.Debug, tag, message, null)
    fun info(tag: String, message: String) = log(LogLevel.Info, tag, message, null)
    fun warn(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.Warn, tag, message, throwable)
    fun error(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.Error, tag, message, throwable)

    fun clear() {
        _entries.value = emptyList()
    }

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val combined = if (throwable?.message.isNullOrBlank()) {
            message
        } else {
            "$message (${throwable?.message})"
        }
        val entry = LogEntry(System.currentTimeMillis(), level, tag, combined)
        _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)

        when (level) {
            LogLevel.Debug -> Log.d(tag, combined, throwable)
            LogLevel.Info -> Log.i(tag, combined, throwable)
            LogLevel.Warn -> Log.w(tag, combined, throwable)
            LogLevel.Error -> Log.e(tag, combined, throwable)
        }
    }
}
