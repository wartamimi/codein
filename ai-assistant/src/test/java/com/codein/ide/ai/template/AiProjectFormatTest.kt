package com.codein.ide.ai.template

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProjectFormatTest {

    @Test
    fun parsesFilesDependenciesAndSummary() {
        val response = """
            Sure, here it is.
            <<<SUMMARY>>>
            A tip calculator.
            <<<END>>>
            <<<FILE app/src/main/kotlin/com/example/tip/MainActivity.kt>>>
            ```kotlin
            package com.example.tip
            class MainActivity
            ```
            <<<END>>>
            <<<FILE app/src/main/res/values/strings.xml>>>
            <resources><string name="app_name">Tip</string></resources>
            <<<END>>>
            <<<DEPENDENCIES>>>
            androidx.recyclerview:recyclerview:1.3.2
            implementation("com.google.code.gson:gson:2.11.0")
            not a dependency
            <<<END>>>
        """.trimIndent()
        val result = AiProjectFormat.parse(response)
        assertEquals("A tip calculator.", result.summary)
        assertEquals(2, result.files.size)
        assertEquals("package com.example.tip\nclass MainActivity\n", result.files["app/src/main/kotlin/com/example/tip/MainActivity.kt"])
        assertEquals(listOf("androidx.recyclerview:recyclerview:1.3.2", "com.google.code.gson:gson:2.11.0"), result.dependencies)
        assertEquals(1, result.rejected.size)
    }

    @Test
    fun rejectsUnsafePaths() {
        assertTrue(AiProjectFormat.pathProblem("../etc/passwd") != null)
        assertTrue(AiProjectFormat.pathProblem("/data/x") != null)
        assertTrue(AiProjectFormat.pathProblem("app/build.gradle.kts") != null)
        assertTrue(AiProjectFormat.pathProblem("app/src/main/res/../../../x") != null)
        assertNull(AiProjectFormat.pathProblem("app/src/main/AndroidManifest.xml"))
        assertNull(AiProjectFormat.pathProblem("app/src/main/res/layout/a.xml"))
        val result = AiProjectFormat.parse("<<<FILE app/build.gradle.kts>>>\nx\n<<<END>>>")
        assertTrue(result.files.isEmpty())
        assertEquals(1, result.rejected.size)
    }

    @Test
    fun toleratesMissingEndMarker() {
        val result = AiProjectFormat.parse("<<<FILE app/src/main/res/values/a.xml>>>\n<a/>\n<<<FILE app/src/main/res/values/b.xml>>>\n<b/>\n<<<END>>>")
        assertEquals(setOf("app/src/main/res/values/a.xml", "app/src/main/res/values/b.xml"), result.files.keys)
    }

    @Test
    fun keepsOnlyFinishedBlocksOfACutOffAnswer() {
        val cut = "<<<SUMMARY>>>\nA counter\n<<<END>>>\n<<<FILE app/src/main/AndroidManifest.xml>>>\n<manifest/>\n<<<END>>>\n<<<FILE app/src/main/kotlin/a/B.kt>>>\nclass B {"
        val kept = AiProjectFormat.completePrefix(cut)
        assertTrue(kept.endsWith("<manifest/>\n<<<END>>>"))
        assertEquals("", AiProjectFormat.completePrefix("<<<FILE x>>>\nunfinished"))
        val joined = AiProjectFormat.parse(kept + "\n<<<FILE app/src/main/kotlin/a/B.kt>>>\nclass B\n<<<END>>>\n<<<DEPENDENCIES>>>\n<<<END>>>")
        assertEquals(listOf("app/src/main/AndroidManifest.xml", "app/src/main/kotlin/a/B.kt"), joined.files.keys.toList())
        assertEquals("A counter", joined.summary)
    }
}
