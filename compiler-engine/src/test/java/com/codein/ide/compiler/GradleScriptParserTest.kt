package com.codein.ide.compiler

import com.codein.ide.compiler.aapt2.Aapt2Runner
import com.codein.ide.compiler.gradle.GradleDependency
import com.codein.ide.compiler.gradle.GradleScriptParser
import com.codein.ide.compiler.gradle.VersionCatalog
import com.codein.ide.compiler.kotlin.KotlinCompilerHook
import com.codein.ide.compiler.model.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradleScriptParserTest {

    private val kts = """
        plugins {
            alias(libs.plugins.android.application)
            id("org.jetbrains.kotlin.android")
        }

        android {
            namespace = "com.example.demo"
            compileSdk = 35
            defaultConfig {
                applicationId = "com.example.demo"
                minSdk = 24 // comment
                targetSdk = 35
                versionCode = 7
                versionName = "1.2.3"
            }
            buildTypes {
                release {
                    isMinifyEnabled = true
                }
            }
        }

        dependencies {
            implementation(libs.androidx.core.ktx)
            implementation("com.squareup.okhttp3:okhttp:4.12.0")
            implementation(platform("androidx.compose:compose-bom:2025.05.00"))
            implementation(project(":core"))
            testImplementation("junit:junit:4.13.2")
        }
    """.trimIndent()

    private val catalog = VersionCatalog.parse(
        """
        [versions]
        core = "1.16.0"
        agp = "8.9.1"
        [libraries]
        androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "core" }
        [plugins]
        android-application = { id = "com.android.application", version.ref = "agp" }
        """.trimIndent(),
    )

    @Test
    fun parsesKotlinDsl() {
        val config = GradleScriptParser().parseModuleText(":app", kts, catalog)
        assertEquals("com.example.demo", config.applicationId)
        assertEquals(35, config.compileSdk)
        assertEquals(24, config.minSdk)
        assertEquals(7, config.versionCode)
        assertEquals("1.2.3", config.versionName)
        assertTrue(config.isApplication)
        assertTrue(config.usesKotlin)
        assertTrue(config.buildTypes.first { it.name == "release" }.minifyEnabled)
        val maven = config.dependencies.filterIsInstance<GradleDependency.Maven>().map { it.coordinate }
        assertTrue("androidx.core:core-ktx:1.16.0" in maven)
        assertTrue("com.squareup.okhttp3:okhttp:4.12.0" in maven)
        assertTrue(config.dependencies.any { it is GradleDependency.Project && it.path == ":core" })
        assertTrue(config.dependencies.any { it is GradleDependency.Platform })
    }

    @Test
    fun parsesGroovyDsl() {
        val groovy = """
            apply plugin: 'com.android.application'
            android {
                compileSdkVersion 34
                defaultConfig {
                    applicationId "org.sample"
                    minSdkVersion 21
                    targetSdkVersion 34
                    versionCode 3
                    versionName "0.3"
                }
            }
            dependencies {
                implementation 'androidx.appcompat:appcompat:1.7.0'
                implementation fileTree(dir: 'libs', include: ['*.jar'])
            }
        """.trimIndent()
        val config = GradleScriptParser().parseModuleText(":app", groovy)
        assertEquals("org.sample", config.applicationId)
        assertEquals(21, config.minSdk)
        assertEquals(34, config.compileSdk)
        assertTrue(config.dependencies.any { it is GradleDependency.FileTree })
    }

    @Test
    fun parsesToolDiagnostics() {
        val aapt = Aapt2Runner.parseLine("/p/app/src/main/res/layout/main.xml:12: error: attribute android:foo not found.")!!
        assertEquals(12, aapt.line)
        assertEquals(Severity.Error, aapt.severity)
        val kt = KotlinCompilerHook.parseLine("e: file:///p/Main.kt:4:9 Unresolved reference 'foo'.")!!
        assertEquals("/p/Main.kt", kt.file)
        assertEquals(4, kt.line)
        assertEquals(9, kt.column)
    }
}
