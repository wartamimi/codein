package com.codein.ide.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorLiteralsTest {

    @Test
    fun findsHexAndComposeColorsAtTheCursor() {
        val text = "<resources>\n    <color name=\"primary\">#6750A4</color>\n    val c = Color(0xFF00FF00)\n</resources>"
        val xml = ColorLiterals.at(text, 1, 26)!!
        assertEquals("#6750A4", xml.text)
        assertEquals("#6750A4", text.substring(xml.start, xml.end))
        assertEquals(0xFF6750A4, xml.argb)

        val compose = ColorLiterals.at(text, 2, 20)!!
        assertEquals("#FF00FF00", compose.text)
        assertEquals("0xFF00FF00", text.substring(compose.start, compose.end))

        assertNull(ColorLiterals.at(text, 0, 3))
        assertEquals("#6750A4", ColorLiterals.format(0xFF6750A4))
        assertEquals("#806750A4", ColorLiterals.format(0x806750A4))
    }
}
