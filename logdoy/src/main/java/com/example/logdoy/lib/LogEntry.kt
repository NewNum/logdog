package com.example.logdoy.lib

data class LogEntry(
    val timestampMs: Long,
    val tag: String?,
    val message: String,
)
