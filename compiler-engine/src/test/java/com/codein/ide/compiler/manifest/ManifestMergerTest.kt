package com.codein.ide.compiler.manifest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ManifestMergerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun write(name: String, text: String): File = temp.newFile(name).apply { writeText(text.trimIndent()) }

    private val app = """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
            <application android:name=".App" android:label="Demo">
                <activity android:name=".MainActivity" android:exported="true" />
                <activity android:name="SettingsActivity" />
                <provider android:name="androidx.startup.InitializationProvider" android:authorities="${'$'}{applicationId}.androidx-startup">
                    <meta-data android:name="com.example.AppInit" android:value="androidx.startup" />
                </provider>
            </application>
        </manifest>
    """

    private val lifecycle = """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="androidx.lifecycle.process">
            <application>
                <provider android:name="androidx.startup.InitializationProvider" android:authorities="${'$'}{applicationId}.androidx-startup" android:exported="false">
                    <meta-data android:name="androidx.lifecycle.ProcessLifecycleInitializer" android:value="androidx.startup" />
                </provider>
                <receiver android:name=".LibReceiver" />
            </application>
        </manifest>
    """

    @Test
    fun qualifiesRelativeNamesAgainstTheirOwnPackage() {
        val out = File(temp.root, "merged.xml")
        ManifestMerger().merge(write("app.xml", app), listOf(write("lib.xml", lifecycle)), out, "com.example.demo.debug", namespace = "com.example.demo")
        val text = out.readText()
        assertTrue(text, text.contains("package=\"com.example.demo.debug\""))
        assertTrue(text, text.contains("android:name=\"com.example.demo.MainActivity\""))
        assertTrue(text, text.contains("android:name=\"com.example.demo.SettingsActivity\""))
        assertTrue(text, text.contains("android:name=\"com.example.demo.App\""))
        assertTrue(text, text.contains("android:name=\"androidx.lifecycle.process.LibReceiver\""))
        assertFalse(text, text.contains("com.example.demo.debug.MainActivity"))
    }

    @Test
    fun mergesMetaDataOfSharedProviders() {
        val out = File(temp.root, "merged.xml")
        ManifestMerger().merge(write("app.xml", app), listOf(write("lib.xml", lifecycle)), out, "com.example.demo")
        val text = out.readText()
        assertEquals(1, Regex("InitializationProvider").findAll(text).count())
        assertTrue(text, text.contains("androidx.lifecycle.ProcessLifecycleInitializer"))
        assertTrue(text, text.contains("com.example.AppInit"))
        assertTrue(text, text.contains("android:authorities=\"com.example.demo.androidx-startup\""))
        assertTrue(text, text.contains("android:exported=\"false\""))
    }
}
