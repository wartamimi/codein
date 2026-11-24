package com.codein.ide

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.codein.ide.compiler.BuildRequest
import com.codein.ide.compiler.model.BuildEvent
import com.codein.ide.compiler.model.BuildResult
import com.codein.ide.di.ToolchainEntryPoint
import com.codein.ide.project.ProjectLanguage
import com.codein.ide.project.ProjectSpec
import com.codein.ide.project.ProjectTemplate
import com.codein.ide.project.ProjectTemplates
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/**
 * Runs on an emulator in CI. Verifies that the bundled toolchain actually works on Android:
 * aapt2 executes from nativeLibraryDir, android.jar is available, and a full Java project builds
 * into a signed APK end to end.
 */
@RunWith(AndroidJUnit4::class)
class OnDeviceToolchainTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val entryPoint = EntryPointAccessors.fromApplication(context, ToolchainEntryPoint::class.java)

    @Test
    fun appLaunchesToHome() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity -> assertTrue(!activity.isFinishing) }
        }
    }

    @Test
    fun bundledAapt2Executes() {
        val aapt2 = entryPoint.buildToolchain().aapt2Binary()
        assertNotNull("libaapt2.so missing from nativeLibraryDir", aapt2)
        val process = ProcessBuilder(aapt2!!.absolutePath, "version").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue("aapt2 timed out", process.waitFor(30, TimeUnit.SECONDS))
        Log.i(TAG, "aapt2 version: $output")
        assertEquals("aapt2 exit code, output: $output", 0, process.exitValue())
        assertTrue(output, output.contains("Android Asset Packaging Tool", ignoreCase = true))
    }

    @Test
    fun bundledAndroidJarIsAvailable() {
        val jar = entryPoint.buildToolchain().androidJar()
        assertNotNull("android.jar not available", jar)
        ZipFile(jar!!).use { zip -> assertNotNull(zip.getEntry("android/app/Activity.class")) }
    }

    @Test
    fun buildsJavaViewsProjectIntoSignedApk() = runBlocking {
        val spec = ProjectSpec(
            name = "Smoke",
            packageName = "com.example.smoke",
            language = ProjectLanguage.Java,
            template = ProjectTemplate.EmptyViewsActivity,
            minSdk = 26,
            targetSdk = 35,
        )
        val dir = File(context.cacheDir, "smoke-project").apply { deleteRecursively(); mkdirs() }
        ProjectTemplates.generate(spec).forEach { (path, content) ->
            File(dir, path).apply { parentFile?.mkdirs(); writeText(content) }
        }
        val events = entryPoint.buildOrchestrator().build(BuildRequest(dir)).toList()
        events.forEach { e ->
            when (e) {
                is BuildEvent.Log -> Log.i(TAG, "[${e.step?.name}] ${e.message}")
                is BuildEvent.Diagnostic -> Log.w(TAG, "[${e.step.name}] ${e.diagnostic}")
                is BuildEvent.StepFinished -> Log.i(TAG, "step ${e.step.name}: ${e.status} in ${e.durationMillis} ms")
                else -> Unit
            }
        }
        when (val result = events.filterIsInstance<BuildEvent.Finished>().lastOrNull()?.result) {
            is BuildResult.Success -> {
                assertTrue(result.output.isFile)
                ZipFile(result.output).use { zip ->
                    assertNotNull(zip.getEntry("classes.dex"))
                    assertNotNull(zip.getEntry("AndroidManifest.xml"))
                    assertNotNull(zip.getEntry("resources.arsc"))
                }
                Log.i(TAG, "Built ${result.output} (${result.output.length() / 1024} KB) in ${result.durationMillis} ms")
            }
            is BuildResult.Failure -> fail("Build failed at ${result.failedStep}: " + result.diagnostics.joinToString("\n"))
            is BuildResult.Cancelled -> fail("Build was cancelled")
            null -> fail("Build produced no result")
        }
    }

    private companion object {
        const val TAG = "CodeinDeviceTest"
    }
}
