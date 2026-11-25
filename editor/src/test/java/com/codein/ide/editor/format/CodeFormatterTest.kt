package com.codein.ide.editor.format

import org.junit.Assert.assertEquals
import org.junit.Test

class CodeFormatterTest {

    @Test
    fun reindentsKotlinByBracesIgnoringStringsAndComments() {
        val messy = """
            |class A {
            |fun b(x: Int) {
            |      if (x > 0) {
            |println("{ not a brace")
            |   }
            |// } comment
            |val list = listOf(
            |1,
            |2,
            |)
            |
            |
            |
            |  return
            |}   
            |}
            |""".trimMargin()
        val expected = """
            |class A {
            |    fun b(x: Int) {
            |        if (x > 0) {
            |            println("{ not a brace")
            |        }
            |        // } comment
            |        val list = listOf(
            |            1,
            |            2,
            |        )
            |
            |        return
            |    }
            |}
            |""".trimMargin()
        assertEquals(expected, CodeFormatter.format(messy, CodeFormatter.Language.Kotlin))
    }

    @Test
    fun indentsChainsAndKeepsRawStrings() {
        val messy = "fun f() {\nval s = \"\"\"\n  raw {\n\"\"\"\nitems\n.filter { it }\n.map { it }\n}\n"
        val expected = "fun f() {\n    val s = \"\"\"\n  raw {\n\"\"\"\n    items\n        .filter { it }\n        .map { it }\n}\n"
        assertEquals(expected, CodeFormatter.format(messy, CodeFormatter.Language.Kotlin))
    }

    @Test
    fun formatsXmlAttributesOnePerLine() {
        val messy = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" android:orientation=\"vertical\">\n<TextView android:text=\"a > b\"/>\n<!-- note -->\n<Button android:id=\"@+id/go\"\nandroid:text=\"Go\" />\n<string name=\"x\">Hello</string>\n</LinearLayout>\n"
        val expected = """
            |<?xml version="1.0" encoding="utf-8"?>
            |<LinearLayout
            |    xmlns:android="http://schemas.android.com/apk/res/android"
            |    android:orientation="vertical">
            |    <TextView android:text="a > b" />
            |    <!-- note -->
            |    <Button
            |        android:id="@+id/go"
            |        android:text="Go" />
            |    <string name="x">Hello</string>
            |</LinearLayout>
            |""".trimMargin()
        assertEquals(expected, CodeFormatter.format(messy, CodeFormatter.Language.Xml))
    }
}
