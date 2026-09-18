package com.uxnhe.logdog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LogDoyBufferingTest {
    @Before
    fun reset() {
        LogDoy.initialized = false
        LogDoy.resetForTest()
    }

    @Test
    fun log_beforeInit_stillBuffers() {
        LogDoy.log("early")
        LogDoy.log("Net", "ok")
        assertFalse(LogDoy.initialized)
        val snap = LogDoy.buffer.snapshot()
        assertEquals(2, snap.size)
        assertEquals("early", snap[0].message)
        assertEquals("Net", snap[1].tag)
        assertEquals("ok", snap[1].message)
    }

    @Test
    fun log_afterFlagInit_stillAppends() {
        LogDoy.initialized = true
        LogDoy.log("later")
        assertEquals(1, LogDoy.buffer.size())
        assertTrue(LogDoy.buffer.snapshot().last().message == "later")
    }
}
