package com.codein.ide.editor.sora

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CompletionWordsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun indexesDeclarationsResourcesAndIds() {
        val root = tmp.root
        File(root, "app/src/main/java/com/example/MainViewModel.kt").apply { parentFile!!.mkdirs() }.writeText(
            "package com.example\nclass MainViewModel { fun loadItems() {}\n val itemCount = 3 }\ndata class Item(val title: String)\n",
        )
        File(root, "app/src/main/java/com/example/Helper.java").apply { parentFile!!.mkdirs() }.writeText(
            "public class Helper { private static final int MAX_SIZE = 4; public String formatPrice(int cents) { return \"\"; } }",
        )
        File(root, "app/src/main/res/values/strings.xml").apply { parentFile!!.mkdirs() }.writeText(
            "<resources><string name=\"app_name\">X</string><style name=\"Theme.App\"/></resources>",
        )
        File(root, "app/src/main/res/layout/activity_main.xml").apply { parentFile!!.mkdirs() }.writeText(
            "<LinearLayout><TextView android:id=\"@+id/titleText\"/></LinearLayout>",
        )
        val symbols = CompletionWords.projectSymbols(root)
        listOf("MainViewModel", "loadItems", "itemCount", "Item", "Helper", "MAX_SIZE", "formatPrice", "app_name", "Theme_App", "titleText", "activity_main")
            .forEach { assertTrue("missing $it in $symbols", it in symbols) }
        assertFalse("package" in symbols)
    }

    @Test
    fun builtInWordsMatchTheLanguage() {
        assertTrue("suspend" in CompletionWords.builtIn(EditorLanguage.Kotlin))
        assertTrue("LazyColumn" in CompletionWords.builtIn(EditorLanguage.Kotlin))
        assertTrue("implements" in CompletionWords.builtIn(EditorLanguage.Java))
        assertFalse("suspend" in CompletionWords.builtIn(EditorLanguage.Java))
        assertTrue("android:layout_width" in CompletionWords.builtIn(EditorLanguage.Xml))
    }
}
