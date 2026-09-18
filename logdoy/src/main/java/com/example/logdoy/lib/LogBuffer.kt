package com.example.logdoy.lib

import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList

class LogBuffer(private val capacity: Int = 500) {
    private val lock = Any()
    private val entries = ArrayDeque<LogEntry>(capacity.coerceAtLeast(1))
    private val observers = CopyOnWriteArrayList<(LogEntry) -> Unit>()

    fun add(entry: LogEntry) {
        synchronized(lock) {
            while (entries.size >= capacity) {
                entries.removeFirst()
            }
            entries.addLast(entry)
        }
        observers.forEach { it(entry) }
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

    fun removeObserver(observer: (LogEntry) -> Unit) {
        observers.remove(observer)
    }
}
