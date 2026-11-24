package com.codein.ide

import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.codein.ide.compiler.BuildRequest
import com.codein.ide.compiler.model.BuildEvent
import com.codein.ide.compiler.model.BuildStep
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
    fun buildsJavaViewsProjectIntoSignedApk(): Unit = runBlocking {
        buildInstallAndLaunch(
            ProjectSpec(
                name = "Smoke",
                packageName = "com.example.smoke",
                language = ProjectLanguage.Java,
                template = ProjectTemplate.EmptyViewsActivity,
                minSdk = 26,
                targetSdk = 35,
            ),
        )
    }

    @Test
    fun buildsKotlinComposeProjectIntoSignedApk(): Unit = runBlocking {
        val installer = entryPoint.kotlinToolchainInstaller()
        val started = System.currentTimeMillis()
        installer.install(includeCompose = true) { }.getOrElse { fail("Kotlin toolchain download failed: $it") }
        Log.i(TAG, "Kotlin toolchain installed in ${System.currentTimeMillis() - started} ms")
        buildInstallAndLaunch(
            ProjectSpec(
                name = "Compose Smoke",
                packageName = "com.example.composesmoke",
                language = ProjectLanguage.Kotlin,
                template = ProjectTemplate.ComposeMaterial3,
                minSdk = 26,
                targetSdk = 35,
            ),
        )
    }

    /** Generates [spec], builds it twice (cold, then warm), then installs and launches the APK. */
    private suspend fun buildInstallAndLaunch(spec: ProjectSpec) {
        val dir = File(context.cacheDir, "smoke-" + spec.packageName.substringAfterLast('.')).apply { deleteRecursively(); mkdirs() }
        ProjectTemplates.generate(spec).forEach { (path, content) ->
            File(dir, path).apply { parentFile?.mkdirs(); writeText(content) }
        }
        val label = spec.packageName.substringAfterLast('.')
        val events = entryPoint.buildOrchestrator().build(BuildRequest(dir)).toList()
        events.forEach { e ->
            when (e) {
                is BuildEvent.Log -> Log.i(TAG, "[$label/${e.step?.name}] ${e.message}")
                is BuildEvent.Diagnostic -> Log.w(TAG, "[$label/${e.step.name}] ${e.diagnostic}")
                is BuildEvent.StepFinished -> Log.i(TAG, "$label step ${e.step.name}: ${e.status} in ${e.durationMillis} ms")
                else -> Unit
            }
        }
        val result = events.filterIsInstance<BuildEvent.Finished>().lastOrNull()?.result
        when (result) {
            is BuildResult.Success -> Unit
            is BuildResult.Failure -> fail("Build failed at ${result.failedStep}: " + result.diagnostics.joinToString("\n"))
            is BuildResult.Cancelled -> fail("Build was cancelled")
            null -> fail("Build produced no result")
        }
        result as BuildResult.Success
        assertTrue(result.output.isFile)
        ZipFile(result.output).use { zip ->
            assertNotNull(zip.getEntry("classes.dex"))
            assertNotNull(zip.getEntry("AndroidManifest.xml"))
            assertNotNull(zip.getEntry("resources.arsc"))
        }
        Log.i(TAG, "$label built ${result.output} (${result.output.length() / 1024} KB) in ${result.durationMillis} ms")

        // Second build with warm caches, after touching a source file, to measure an edit-and-run cycle.
        dir.walkTopDown().firstOrNull { it.name.startsWith("MainActivity.") }?.let { it.appendText("\n") }
        val warm = entryPoint.buildOrchestrator().build(BuildRequest(dir)).toList()
        warm.filterIsInstance<BuildEvent.Log>().filter { it.step == BuildStep.Dexing }.forEach { Log.i(TAG, "$label warm [Dexing] ${it.message}") }
        warm.filterIsInstance<BuildEvent.StepFinished>().forEach { Log.i(TAG, "$label warm step ${it.step.name}: ${it.status} in ${it.durationMillis} ms") }
        when (val warmResult = warm.filterIsInstance<BuildEvent.Finished>().lastOrNull()?.result) {
            is BuildResult.Success -> {
                Log.i(TAG, "$label warm build took ${warmResult.durationMillis} ms")
                installAndLaunch(warmResult.output, spec.packageName, "${spec.packageName}.MainActivity")
            }
            else -> fail("Warm build did not succeed: $warmResult")
        }
    }

    /** Installs [apk] via the shell user, launches [activity] and fails with the crash log if the process dies. */
    private fun installAndLaunch(apk: File, packageName: String, activity: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val pipes = automation.executeShellCommandRw("pm install -r -t -S ${apk.length()}")
        val output = pipes[0]
        val input = pipes[1]
        ParcelFileDescriptor.AutoCloseOutputStream(input).use { out -> apk.inputStream().use { it.copyTo(out) } }
        val installLog = ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().readText()
        Log.i(TAG, "pm install: $installLog")
        assertTrue("Install failed: $installLog", installLog.contains("Success"))
        shell("logcat -c")
        Log.i(TAG, "am start: " + shell("am start -W -n $packageName/$activity"))
        Thread.sleep(6000)
        val pid = shell("pidof $packageName").trim()
        val crash = shell("logcat -d -b crash")
        val appLog = shell("logcat -d -v brief AndroidRuntime:E ActivityManager:W ActivityTaskManager:W *:S")
        crash.lines().forEach { Log.i(TAG, "crash| $it") }
        appLog.lines().takeLast(80).forEach { Log.i(TAG, "log| $it") }
        shell("am force-stop $packageName")
        assertTrue("Built app crashed:\n$crash\n$appLog", pid.isNotEmpty() && !crash.contains(packageName))
    }

    private fun shell(command: String): String {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().readText()
    }

    private companion object {
        const val TAG = "CodeinDeviceTest"
    }
}
