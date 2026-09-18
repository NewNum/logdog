package com.uxnhe.logdog

import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList

internal class LogBuffer(private val capacity: Int = 500) {
    private val lock = Any()
    private val entries = ArrayDeque<LogEntry>(capacity.coerceAtLeast(1))
    private val observers = CopyOnWriteArrayList<(LogEntry) -> Unit>()

    fun add(entry: LogEntry) {
        val snapshotObservers: List<(LogEntry) -> Unit>
        synchronized(lock) {
            while (entries.size >= capacity) {
                entries.removeFirst()
            }
            entries.addLast(entry)
            snapshotObservers = observers.toList()
        }
        snapshotObservers.forEach { it(entry) }
    }

    fun snapshot(): List<LogEntry> = synchronized(lock) {
        entries.toList()
    }

    fun size(): Int = synchronized(lock) {
        entries.size
    }

    fun addObserver(observer: (LogEntry) -> Unit) {
        observers.add(observer)
    }

    /** Registers [observer] and returns the current buffer contents atomically (no gap vs [add]). */
    fun subscribe(observer: (LogEntry) -> Unit): List<LogEntry> {
        synchronized(lock) {
            observers.add(observer)
            return entries.toList()
        }
    }

    fun removeObserver(observer: (LogEntry) -> Unit) {
        observers.remove(observer)
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
        }
    }
}
