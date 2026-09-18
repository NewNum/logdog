package com.example.logdoy.lib

internal data class LogEntry(
    val timestampMs: Long,
    val tag: String?,
    val message: String,
)
