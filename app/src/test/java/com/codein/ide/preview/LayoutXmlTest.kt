package com.codein.ide.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LayoutXmlTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun parsesTreeAndAttributes() {
        val root = LayoutXml.parse(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical">
                <TextView android:id="@+id/title" android:text="@string/app_name" android:textSize="24sp"/>
                <com.google.android.material.button.MaterialButton android:text="Go"/>
            </LinearLayout>
            """.trimIndent(),
        ).getOrThrow()
        assertEquals("LinearLayout", root.tag)
        assertEquals("vertical", root["orientation"])
        assertEquals(2, root.children.size)
        assertEquals("MaterialButton", root.children[1].simpleTag)
        assertEquals(24f, LayoutXml.dimension(root.children[0]["textSize"], ResourceValues()))
    }

    @Test
    fun reportsMalformedXml() {
        val result = LayoutXml.parse("<LinearLayout><TextView></LinearLayout>")
        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull()?.message.isNullOrBlank())
    }

    @Test
    fun resolvesProjectResources() {
        File(tmp.root, "app/src/main/res/values").mkdirs()
        File(tmp.root, "app/src/main/res/values/strings.xml").writeText("<resources><string name=\"app_name\">Tip Calc</string><string name=\"alias\">@string/app_name</string></resources>")
        File(tmp.root, "app/src/main/res/values/colors.xml").writeText("<resources><color name=\"purple\">#6750A4</color></resources>")
        File(tmp.root, "app/src/main/res/values/dimens.xml").writeText("<resources><dimen name=\"gap\">12dp</dimen></resources>")
        val res = ResourceValues.load(tmp.root)
        assertEquals("Tip Calc", res.resolve("@string/alias"))
        assertEquals(0xFF6750A4, LayoutXml.color("@color/purple", res))
        assertEquals(0xFFFFFFFF, LayoutXml.color("@android:color/white", res))
        assertEquals(0x80FF0000, LayoutXml.color("#80FF0000", res))
        assertEquals(12f, LayoutXml.dimension("@dimen/gap", res))
        assertNull(LayoutXml.color("?attr/colorPrimary", res))
    }

    @Test
    fun detectsLayoutFiles() {
        assertTrue(isLayoutFile("app/src/main/res/layout/activity_main.xml"))
        assertTrue(isLayoutFile("app/src/main/res/layout-land/activity_main.xml"))
        assertFalse(isLayoutFile("app/src/main/res/values/strings.xml"))
        assertFalse(isLayoutFile("app/src/main/AndroidManifest.xml"))
    }
}
