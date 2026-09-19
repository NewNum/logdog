package com.uxnhe.logdog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LogDogBufferingTest {
    @Before
    fun reset() {
        LogDog.initialized = false
        LogDog.resetForTest()
    }

    @Test
    fun log_beforeInit_stillBuffers() {
        LogDog.log("early")
        LogDog.log("Net", "ok")
        assertFalse(LogDog.initialized)
        val snap = LogDog.buffer.snapshot()
        assertEquals(2, snap.size)
        assertEquals("early", snap[0].message)
        assertEquals("Net", snap[1].tag)
        assertEquals("ok", snap[1].message)
    }

    @Test
    fun log_afterFlagInit_stillAppends() {
        LogDog.initialized = true
        LogDog.log("later")
        assertEquals(1, LogDog.buffer.size())
        assertTrue(LogDog.buffer.snapshot().last().message == "later")
    }
}
