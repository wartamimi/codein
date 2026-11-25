package com.codein.ide.ai

import com.codein.ide.ai.diff.LineDiff
import com.codein.ide.ai.diff.PatchResult
import com.codein.ide.ai.diff.UnifiedDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineDiffTest {

    @Test
    fun separateChangesBecomeSeparateHunksThatApplyBack() {
        val old = (1..30).joinToString("\n") { "line $it" }
        val new = old.replace("line 3\n", "line 3 changed\n").replace("line 25\n", "").replace("line 28", "line 28\ninserted")
        val hunks = LineDiff.hunks(old, new)
        assertEquals(2, hunks.size)
        assertEquals(1, hunks[0].additions)
        assertEquals(1, hunks[0].deletions)

        val text = LineDiff.unified("A.kt", old, new)
        val patch = UnifiedDiff.parse(text).single()
        val applied = UnifiedDiff.apply(old, patch) as PatchResult.Applied
        assertEquals(new, applied.text)
    }

    @Test
    fun identicalTextsHaveNoHunks() {
        assertTrue(LineDiff.hunks("a\nb", "a\nb").isEmpty())
        assertEquals("", LineDiff.unified("x", "a", "a"))
    }
}
