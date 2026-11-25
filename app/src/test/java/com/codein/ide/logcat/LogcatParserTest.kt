package com.codein.ide.logcat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogcatParserTest {
    @Test
    fun parsesThreadtime() {
        val e = LogcatParser.parse(1, "11-24 08:31:40.126  1234  1250 E AndroidRuntime: FATAL EXCEPTION: main")!!
        assertEquals(1234, e.pid)
        assertEquals(1250, e.tid)
        assertEquals(LogPriority.Error, e.priority)
        assertEquals("AndroidRuntime", e.tag)
        assertEquals("FATAL EXCEPTION: main", e.message)
    }

    @Test
    fun parsesTagWithSpacesAndFatal() {
        val e = LogcatParser.parse(2, "01-02 03:04:05.006   99   99 F libc    : Fatal signal 11")!!
        assertEquals(LogPriority.Assert, e.priority)
        assertEquals("libc", e.tag)
        assertNull(LogcatParser.parse(3, "--------- beginning of main"))
    }
}
