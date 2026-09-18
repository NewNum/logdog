package com.example.logdoy.lib

import android.app.Application

object LogDoy {
    @Volatile
    private var bufferRef = LogBuffer()
    internal val buffer: LogBuffer get() = bufferRef

    @Volatile
    internal var initialized: Boolean = false

    @Volatile
    private var started: Boolean = false

    /** Set by library internals before/during first real init wiring (Task 6). */
    @Volatile
    internal var starter: ((Application) -> Unit)? = null

    fun init(app: Application) {
        if (started) return
        started = true
        initialized = true
        starter?.invoke(app)
    }

    fun log(message: String) {
        append(tag = null, message = message)
    }

    fun log(tag: String, message: String) {
        append(tag = tag, message = message)
    }

    private fun append(tag: String?, message: String) {
        bufferRef.add(LogEntry(System.currentTimeMillis(), tag, message))
    }

    internal fun resetForTest() {
        started = false
        initialized = false
        starter = null
        bufferRef = LogBuffer()
    }
}
