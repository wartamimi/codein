package com.codein.ide.ai

import com.codein.ide.ai.diff.PatchResult
import com.codein.ide.ai.diff.UnifiedDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedDiffTest {

    private val original = """
        package demo

        fun main() {
            val name = "World"
            println("Hello")
        }
    """.trimIndent() + "\n"

    @Test
    fun parsesGitStyleDiff() {
        val diff = """
            diff --git a/src/Main.kt b/src/Main.kt
            --- a/src/Main.kt
            +++ b/src/Main.kt
            @@ -4,2 +4,2 @@
                 val name = "World"
            -    println("Hello")
            +    println("Hello, ${'$'}name")
        """.trimIndent()
        val patches = UnifiedDiff.parse(diff)
        assertEquals(1, patches.size)
        assertEquals("src/Main.kt", patches[0].targetPath)
        assertEquals(1, patches[0].additions)
        assertEquals(1, patches[0].deletions)
    }

    @Test
    fun appliesWithWrongLineNumbers() {
        val diff = """
            @@ -40,2 +40,2 @@
                 val name = "World"
            -    println("Hello")
            +    println("Hello, ${'$'}name")
        """.trimIndent()
        val result = UnifiedDiff.apply(original, UnifiedDiff.parse(diff).single())
        assertTrue(result is PatchResult.Applied)
        assertTrue((result as PatchResult.Applied).text.contains("println(\"Hello, \$name\")"))
        assertTrue(result.text.endsWith("}\n"))
    }

    @Test
    fun fuzzyWhitespaceKeepsIndentation() {
        val diff = """
            @@
             val name = "World"
            -println("Hello")
            +    println("Hi")
        """.trimIndent()
        val result = UnifiedDiff.apply(original, UnifiedDiff.parse(diff).single()) as PatchResult.Applied
        assertEquals(1, result.fuzzyHunks)
        assertTrue(result.text.contains("    val name = \"World\"\n    println(\"Hi\")"))
    }

    @Test
    fun reportsMismatch() {
        val diff = "@@ -1,1 +1,1 @@\n-does not exist\n+x\n"
        val result = UnifiedDiff.apply(original, UnifiedDiff.parse(diff).single())
        assertTrue(result is PatchResult.Failed)
    }

    @Test
    fun diffRoundTrips() {
        val changed = original.replace("World", "Codein").replace("}\n", "    return\n}\n")
        val patch = UnifiedDiff.diff(original, changed, "Main.kt")
        val applied = UnifiedDiff.apply(original, UnifiedDiff.parse(patch).single()) as PatchResult.Applied
        assertEquals(changed, applied.text)
    }
}
