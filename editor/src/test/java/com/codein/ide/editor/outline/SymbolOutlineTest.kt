package com.codein.ide.editor.outline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolOutlineTest {

    @Test
    fun kotlinOutlineSkipsLocalsAndStrings() {
        val source = """
            package demo

            /* class Hidden { */
            data class User(val name: String) {
                val greeting = "fun notReal() {"
                fun greet(): String {
                    val local = 1
                    return "Hi ${'$'}name"
                }
                companion object {
                    const val MAX = 3
                }
            }

            fun top() = User("a")
            enum class Mode { A, B }
        """.trimIndent()
        val symbols = SymbolOutline.outline("User.kt", source)
        val names = symbols.map { it.name }
        assertEquals(listOf("User", "greeting", "greet", "Companion", "MAX", "top", "Mode"), names)
        assertEquals(SymbolKind.Enum, symbols.last().kind)
        assertEquals(4, symbols.first().line)
        assertEquals(1, symbols.first { it.name == "greet" }.depth)
    }

    @Test
    fun javaAndXmlOutlines() {
        val java = """
            public class MainActivity extends Activity {
                @Override
                protected void onCreate(Bundle savedInstanceState) {
                    if (savedInstanceState != null) {
                        helper();
                    }
                }
                private int helper() {
                    return 1;
                }
            }
        """.trimIndent()
        assertEquals(listOf("MainActivity", "onCreate", "helper"), SymbolOutline.outline("MainActivity.java", java).map { it.name })

        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
                <TextView android:id="@+id/title" />
                <Button
                    android:id="@+id/go" />
            </LinearLayout>
        """.trimIndent()
        val tags = SymbolOutline.outline("main.xml", xml)
        assertTrue(tags.any { it.name == "title" && it.detail == "TextView" })
    }
}
