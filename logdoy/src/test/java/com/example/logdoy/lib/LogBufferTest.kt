package com.example.logdoy.lib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogBufferTest {
    @Test
    fun add_keepsOrderAndSize() {
        val buffer = LogBuffer(capacity = 3)
        buffer.add(LogEntry(1, null, "a"))
        buffer.add(LogEntry(2, "t", "b"))
        assertEquals(2, buffer.size())
        assertEquals(listOf("a", "b"), buffer.snapshot().map { it.message })
    }

    @Test
    fun add_dropsOldestWhenOverCapacity() {
        val buffer = LogBuffer(capacity = 2)
        buffer.add(LogEntry(1, null, "a"))
        buffer.add(LogEntry(2, null, "b"))
        buffer.add(LogEntry(3, null, "c"))
        assertEquals(2, buffer.size())
        assertEquals(listOf("b", "c"), buffer.snapshot().map { it.message })
    }

    @Test
    fun observer_receivesNewEntries() {
        val buffer = LogBuffer(capacity = 10)
        val received = mutableListOf<String>()
        val observer: (LogEntry) -> Unit = { received += it.message }
        buffer.addObserver(observer)
        buffer.add(LogEntry(1, null, "x"))
        assertEquals(listOf("x"), received)
    }

    @Test
    fun removeObserver_stopsNotifications() {
        val buffer = LogBuffer(capacity = 10)
        val received = mutableListOf<String>()
        val observer: (LogEntry) -> Unit = { received += it.message }
        buffer.addObserver(observer)
        buffer.removeObserver(observer)
        buffer.add(LogEntry(1, null, "x"))
        assertTrue(received.isEmpty())
    }
}
