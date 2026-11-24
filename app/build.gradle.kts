import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.codein.ide"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.codein.ide"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // Build tools (libaapt2.so) must be extracted to nativeLibraryDir so they
            // can be executed under Android 10+ W^X rules.
            useLegacyPackaging = true
            // libaapt2.so is a static executable, not a JNI library: never strip it.
            keepDebugSymbols += "**/libaapt2.so"
        }
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/INDEX.LIST",
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA",
                "META-INF/*.kotlin_module",
                "META-INF/versions/*/OSGI-INF/MANIFEST.MF",
                "META-INF/eclipse.inf",
                "plugin.properties",
                "plugin.xml",
                "about.html",
                "about_files/**",
                "jdtCompilerAdapter.jar",
                "META-INF/services/javax.annotation.processing.Processor",
            )
            pickFirsts += setOf(
                "META-INF/versions/9/module-info.class",
                "module-info.class",
                "google/protobuf/*.proto",
                "com/android/tools/r8/**",
            )
        }
    }
}

kotlin {
    jvmToolchain(17)
}

// ---------------------------------------------------------------------------------------------
// Bundled build toolchain
//
// * android.jar is copied from the Android SDK used to build Codein (platforms/android-35) into
//   assets/sdk/android.jar, so on-device builds work without a manual import.
// * aapt2 is a fully static Android executable from github.com/lzhiyong/android-sdk-tools
//   (Apache-2.0, built from AOSP). It ships as lib/<abi>/libaapt2.so so Android extracts it into
//   nativeLibraryDir, the only place an app may execute binaries from on Android 10+.
// ---------------------------------------------------------------------------------------------

val codeinGeneratedAssets = layout.buildDirectory.dir("generated/codein/assets")
val codeinGeneratedJniLibs = layout.buildDirectory.dir("generated/codein/jniLibs")

android.sourceSets["main"].assets.srcDir(codeinGeneratedAssets)
android.sourceSets["main"].jniLibs.srcDir(codeinGeneratedJniLibs)

val bundleAndroidJar = tasks.register("bundleAndroidJar") {
    val source = androidComponents.sdkComponents.sdkDirectory.map { it.file("platforms/android-35/android.jar").asFile }
    val target = codeinGeneratedAssets.map { it.file("sdk/android.jar").asFile }
    inputs.file(source)
    outputs.file(target)
    doLast {
        val from = source.get()
        val to = target.get()
        if (!from.isFile) throw GradleException("android.jar not found at $from. Install platforms;android-35 with sdkmanager.")
        to.parentFile.mkdirs()
        from.copyTo(to, overwrite = true)
    }
}

val aapt2Release = providers.gradleProperty("codein.aapt2.release").orElse("latest")
val fetchAapt2 = tasks.register("fetchAapt2") {
    val outDir = codeinGeneratedJniLibs.map { it.asFile }
    val release = aapt2Release
    val required = providers.environmentVariable("CI").map { it.isNotBlank() }.orElse(false)
    inputs.property("release", release)
    outputs.dir(outDir)
    doLast {
        val abis = mapOf("arm64-v8a" to "aarch64", "x86_64" to "x86_64")
        val base = if (release.get() == "latest") {
            "https://github.com/lzhiyong/android-sdk-tools/releases/latest/download"
        } else {
            "https://github.com/lzhiyong/android-sdk-tools/releases/download/${release.get()}"
        }
        for ((abi, arch) in abis) {
            val target = File(outDir.get(), "$abi/libaapt2.so")
            if (target.isFile && target.length() > 0) continue
            val manual = file("src/main/jniLibs/$abi/libaapt2.so")
            if (manual.isFile) continue
            val zipFile = File(temporaryDir, "android-sdk-tools-static-$arch.zip")
            try {
                var url = URL("$base/android-sdk-tools-static-$arch.zip")
                var redirects = 0
                while (true) {
                    val conn = url.openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = false
                    conn.connectTimeout = 30_000
                    conn.readTimeout = 180_000
                    val code = conn.responseCode
                    if (code in 300..399) {
                        url = URL(url, conn.getHeaderField("Location"))
                        conn.disconnect()
                        if (++redirects > 10) throw GradleException("Too many redirects for aapt2 download")
                        continue
                    }
                    if (code != 200) throw GradleException("HTTP $code downloading $url")
                    conn.inputStream.use { input -> zipFile.outputStream().use { input.copyTo(it) } }
                    break
                }
                ZipFile(zipFile).use { zip ->
                    val entry = zip.entries().asSequence().firstOrNull { !it.isDirectory && it.name.substringAfterLast('/') == "aapt2" }
                        ?: throw GradleException("aapt2 not found inside ${zipFile.name}")
                    target.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                }
                logger.lifecycle("Bundled aapt2 for $abi (${target.length() / 1024} KB)")
            } catch (e: Exception) {
                target.delete()
                if (required.get()) throw GradleException("Failed to fetch aapt2 for $abi: ${e.message}", e)
                logger.warn("Codein: could not fetch aapt2 for $abi (${e.message}). Resource builds will be unavailable in this APK.")
            } finally {
                zipFile.delete()
            }
        }
    }
}

tasks.named("preBuild") { dependsOn(bundleAndroidJar, fetchAapt2) }

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(project(":core-ui"))
    implementation(project(":editor"))
    implementation(project(":compiler-engine"))
    implementation(project(":maven-resolver"))
    implementation(project(":aab-signer"))
    implementation(project(":ai-assistant"))
    implementation(project(":git-vcs"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.documentfile)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
