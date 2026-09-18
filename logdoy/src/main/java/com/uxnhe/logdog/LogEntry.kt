package com.uxnhe.logdog

internal data class LogEntry(
    val timestampMs: Long,
    val tag: String?,
    val message: String,
)
