package com.codein.ide.compiler

import com.codein.ide.compiler.aapt2.Aapt2LinkOptions
import com.codein.ide.compiler.aapt2.Aapt2Runner
import com.codein.ide.compiler.d8.D8Dexer
import com.codein.ide.compiler.ecj.EcjCompiler
import com.codein.ide.compiler.ecj.JavaCompileRequest
import com.codein.ide.compiler.gradle.GradleDependency
import com.codein.ide.compiler.gradle.GradleModuleConfig
import com.codein.ide.compiler.gradle.GradleScriptParser
import com.codein.ide.compiler.kotlin.KotlinCompileRequest
import com.codein.ide.compiler.kotlin.KotlinCompilerHook
import com.codein.ide.compiler.manifest.ManifestMerger
import com.codein.ide.compiler.model.BuildEvent
import com.codein.ide.compiler.model.BuildResult
import com.codein.ide.compiler.model.BuildStep
import com.codein.ide.compiler.model.BuildStepException
import com.codein.ide.compiler.model.BuildVariant
import com.codein.ide.compiler.model.CompilerDiagnostic
import com.codein.ide.compiler.model.DiagnosticSource
import com.codein.ide.compiler.model.LogLevel
import com.codein.ide.compiler.model.Severity
import com.codein.ide.compiler.model.StepStatus
import com.codein.ide.compiler.cache.CachedArtifact
import com.codein.ide.compiler.cache.ResolutionCache
import com.codein.ide.compiler.packaging.ApkPackager
import com.codein.ide.compiler.packaging.PackagingInputs
import com.codein.ide.compiler.sdk.BuildToolchain
import com.codein.ide.maven.aar.AarUnpacker
import com.codein.ide.maven.aar.ExtractedAar
import com.codein.ide.maven.model.ArtifactType
import com.codein.ide.maven.model.MavenCoordinate
import com.codein.ide.maven.model.MavenRepository
import com.codein.ide.maven.model.ResolveEvent
import com.codein.ide.maven.resolve.MavenResolver
import com.codein.ide.signer.apk.ApkSignerService
import com.codein.ide.signer.bundle.AabBuilder
import com.codein.ide.signer.bundle.BundleModuleInputs
import com.codein.ide.signer.keystore.DebugKeystoreManager
import com.codein.ide.signer.keystore.KeystoreVault
import com.codein.ide.signer.keystore.SigningKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What to build. */
data class BuildRequest(
    val projectDir: File,
    val modulePath: String = ":app",
    val variant: BuildVariant = BuildVariant.DebugApk,
)

/** Intermediate state shared between pipeline steps. */
internal class BuildContext(
    val request: BuildRequest,
    val moduleDir: File,
    val buildDir: File,
    val config: GradleModuleConfig,
    val androidJar: File,
) {
    val compileJars = mutableListOf<File>()
    val runtimeJars = mutableListOf<File>()
    val aars = mutableListOf<ExtractedAar>()
    val libraryModules = mutableListOf<Pair<File, GradleModuleConfig>>()
    var resourcesApk: File? = null
    var protoResourcesApk: File? = null
    val generatedSourceDirs = mutableListOf<File>()
    val classesDirs = mutableListOf<File>()
    val dexFiles = mutableListOf<File>()
    var outputFile: File? = null

    val variantName: String get() = if (request.variant.isRelease) "release" else "debug"
    val intermediates: File get() = File(buildDir, "intermediates/$variantName")

    fun sourceRoots(kind: String): List<File> = buildList {
        add(File(moduleDir, "src/main/$kind"))
        add(File(moduleDir, "src/$variantName/$kind"))
        libraryModules.forEach { (dir, _) -> add(File(dir, "src/main/$kind")) }
    }.filter { it.isDirectory }
}

/**
 * Orchestrates the 5-step on-device build pipeline and streams progress as [BuildEvent]s:
 *
 * 1. **Maven Resolve** — parse Gradle scripts, resolve transitive Maven dependencies, unpack AARs.
 * 2. **AAPT2 Resources** — compile app + library resources, merge manifests, link, generate `R.java`.
 * 3. **ECJ/Kotlin Compile** — compile Kotlin (optional hook) then Java (+ R + BuildConfig) with ECJ.
 * 4. **D8 Dexing** — desugar and dex app classes and libraries into multidex output.
 * 5. **Sign & Package** — assemble, zipalign and sign the APK (v1/v2/v3) or build + sign an AAB.
 *
 * Cancellation of the collecting coroutine cancels the running step (including native processes).
 */
@Singleton
class BuildOrchestrator @Inject constructor(
    private val toolchain: BuildToolchain,
    private val gradleParser: GradleScriptParser,
    private val mavenResolver: MavenResolver,
    private val aarUnpacker: AarUnpacker,
    private val aapt2: Aapt2Runner,
    private val manifestMerger: ManifestMerger,
    private val ecj: EcjCompiler,
    private val kotlinCompiler: KotlinCompilerHook,
    private val dexer: D8Dexer,
    private val packager: ApkPackager,
    private val signer: ApkSignerService,
    private val aabBuilder: AabBuilder,
    private val debugKeystore: DebugKeystoreManager,
    private val vault: KeystoreVault,
) {

    fun build(request: BuildRequest): Flow<BuildEvent> = channelFlow {
        val started = System.currentTimeMillis()
        val projectName = runCatching { gradleParser.parseSettings(request.projectDir).rootProjectName }.getOrDefault(request.projectDir.name)
        send(BuildEvent.Started(request.variant, projectName))
        var currentStep = BuildStep.MavenResolve
        try {
            val ctx = prepare(request)
            val total = BuildStep.entries.size
            suspend fun runStep(step: BuildStep, action: suspend () -> Unit) {
                currentStep = step
                coroutineContext.ensureActive()
                send(BuildEvent.StepStarted(step))
                send(BuildEvent.Progress((step.index - 1) / total.toFloat()))
                val stepStart = System.currentTimeMillis()
                action()
                send(BuildEvent.StepFinished(step, StepStatus.Succeeded, System.currentTimeMillis() - stepStart))
            }
            runStep(BuildStep.MavenResolve) { resolveDependencies(ctx) }
            runStep(BuildStep.Aapt2Resources) { processResources(ctx) }
            runStep(BuildStep.Compile) { compileSources(ctx) }
            runStep(BuildStep.Dexing) { dex(ctx) }
            runStep(BuildStep.SignAndPackage) { packageAndSign(ctx) }
            val output = ctx.outputFile
            send(BuildEvent.Progress(1f))
            val duration = System.currentTimeMillis() - started
            send(BuildEvent.Log(null, LogLevel.Info, "BUILD SUCCESSFUL in ${duration} ms → ${output?.absolutePath}"))
            send(BuildEvent.Finished(BuildResult.Success(requireNotNull(output), request.variant, duration)))
        } catch (e: CancellationException) {
            val duration = System.currentTimeMillis() - started
            runCatching {
                trySend(BuildEvent.StepFinished(currentStep, StepStatus.Cancelled, 0))
                trySend(BuildEvent.Finished(BuildResult.Cancelled(duration)))
            }
            throw e
        } catch (e: BuildStepException) {
            e.diagnostics.forEach { send(BuildEvent.Diagnostic(e.step, it)) }
            send(BuildEvent.StepFinished(e.step, StepStatus.Failed, 0))
            send(BuildEvent.Log(e.step, LogLevel.Error, "BUILD FAILED: ${e.message}"))
            send(BuildEvent.Finished(BuildResult.Failure(e.step, e.diagnostics, System.currentTimeMillis() - started)))
        } catch (t: Throwable) {
            val diagnostic = CompilerDiagnostic(DiagnosticSource.Gradle, Severity.Error, "${t.javaClass.simpleName}: ${t.message}")
            send(BuildEvent.Diagnostic(currentStep, diagnostic))
            send(BuildEvent.StepFinished(currentStep, StepStatus.Failed, 0))
            send(BuildEvent.Log(currentStep, LogLevel.Error, "BUILD FAILED: ${t.stackTraceToString().lines().take(8).joinToString("\n")}"))
            send(BuildEvent.Finished(BuildResult.Failure(currentStep, listOf(diagnostic), System.currentTimeMillis() - started)))
        }
    }.buffer(256).flowOn(Dispatchers.Default)

    private suspend fun ProducerScope<BuildEvent>.log(step: BuildStep, message: String, level: LogLevel = LogLevel.Info) =
        send(BuildEvent.Log(step, level, message))

    private fun fail(step: BuildStep, source: DiagnosticSource, message: String): Nothing =
        throw BuildStepException(step, listOf(CompilerDiagnostic(source, Severity.Error, message)))

    private fun prepare(request: BuildRequest): BuildContext {
        val config = try {
            gradleParser.parseModule(request.projectDir, request.modulePath)
        } catch (e: Exception) {
            fail(BuildStep.MavenResolve, DiagnosticSource.Gradle, e.message ?: "Unable to parse build script")
        }
        val moduleDir = File(request.projectDir, request.modulePath.trimStart(':').replace(':', '/'))
        val androidJar = toolchain.androidJar(config.compileSdk)
            ?: fail(
                BuildStep.MavenResolve,
                DiagnosticSource.Gradle,
                "The android.jar bundled with Codein could not be prepared" +
                    (toolchain.lastExtractionError?.let { " ($it)" } ?: "") +
                    ". Check that the device has free storage, or import platforms/android-${config.compileSdk}/android.jar " +
                    "from an Android SDK in Settings > Toolchain.",
            )
        return BuildContext(request, moduleDir, File(moduleDir, "build"), config, androidJar)
    }

    // ---------------------------------------------------------------- Step 1: Maven Resolve

    private suspend fun ProducerScope<BuildEvent>.resolveDependencies(ctx: BuildContext) {
        val step = BuildStep.MavenResolve
        val variantConfigs = setOf("implementation", "api", "runtimeOnly", "compileOnly", "${ctx.variantName}Implementation", "${ctx.variantName}Api")
        val settings = gradleParser.parseSettings(ctx.request.projectDir)
        log(step, "Project ${settings.rootProjectName}: module ${ctx.request.modulePath}, compileSdk ${ctx.config.compileSdk}, minSdk ${ctx.config.minSdk}")

        // Collect project (library module) dependencies recursively.
        val allDeps = ctx.config.dependencies.toMutableList()
        val visitedModules = mutableSetOf(ctx.request.modulePath)
        val queue = ArrayDeque(ctx.config.dependencies.filterIsInstance<GradleDependency.Project>().filter { it.configuration in variantConfigs })
        while (queue.isNotEmpty()) {
            val project = queue.removeFirst()
            if (!visitedModules.add(project.path)) continue
            val libConfig = runCatching { gradleParser.parseModule(ctx.request.projectDir, project.path) }.getOrElse {
                fail(step, DiagnosticSource.Gradle, "Cannot read module ${project.path}: ${it.message}")
            }
            val libDir = File(ctx.request.projectDir, project.path.trimStart(':').replace(':', '/'))
            ctx.libraryModules += libDir to libConfig
            allDeps += libConfig.dependencies
            libConfig.dependencies.filterIsInstance<GradleDependency.Project>().filter { it.configuration in setOf("implementation", "api") }.forEach(queue::addLast)
            log(step, "Including library module ${project.path} (${libConfig.namespace ?: "no namespace"})")
        }

        // Local jars.
        for (dep in allDeps) {
            if (dep.configuration !in variantConfigs) continue
            val jars = when (dep) {
                is GradleDependency.Files -> dep.paths.map { File(ctx.moduleDir, it) }
                is GradleDependency.FileTree -> File(ctx.moduleDir, dep.dir).walkTopDown()
                    .filter { f -> f.isFile && dep.includes.any { pattern -> globMatches(pattern, f.name) } }.toList()
                else -> emptyList()
            }
            jars.filter { it.isFile }.forEach { jar ->
                ctx.compileJars += jar
                if (dep.configuration != "compileOnly") ctx.runtimeJars += jar
            }
        }

        val mavenDeps = allDeps.filterIsInstance<GradleDependency.Maven>().filter { it.configuration in variantConfigs }
        val compileOnly = mavenDeps.filter { it.configuration == "compileOnly" }.map { "${it.group}:${it.artifact}" }.toSet()
        val platforms = allDeps.filterIsInstance<GradleDependency.Platform>()
        if (mavenDeps.isEmpty()) {
            log(step, "No Maven dependencies declared")
            return
        }
        val repositories = repositoriesFor(settings.repositories)

        // Unchanged dependencies since the last build: reuse its resolution and skip the POM walk.
        val cache = ResolutionCache(File(toolchain.workRoot, "resolve-cache"))
        val cacheKey = cache.key(
            declared = mavenDeps.map { d -> MavenCoordinate(d.group, d.artifact, d.version.ifBlank { "managed-by-bom" }, d.classifier, d.extension).toString() },
            platforms = platforms.map { it.coordinate },
            repositories = repositories.map { it.baseUrl },
        )
        val cached = cacheKey?.let(cache::load)
        val artifacts: List<CachedArtifact> = if (cached != null) {
            log(step, "Dependencies unchanged since the last build: reusing ${cached.size} resolved artifacts")
            cached
        } else {
            resolveWithMaven(mavenDeps, platforms, repositories).also { resolved ->
                if (cacheKey != null) runCatching { cache.save(cacheKey, resolved) }
            }
        }
        val explodedRoot = File(toolchain.workRoot, "exploded-aar")
        for (artifact in artifacts) {
            coroutineContext.ensureActive()
            val file = artifact.file ?: continue
            val runtime = artifact.coordinate.groupArtifact !in compileOnly
            when (artifact.type) {
                ArtifactType.Jar -> {
                    ctx.compileJars += file
                    if (runtime) ctx.runtimeJars += file
                }
                ArtifactType.Aar -> {
                    val extracted = aarUnpacker.unpack(file, artifact.coordinate, explodedRoot)
                    ctx.aars += extracted
                    ctx.compileJars += extracted.allJars
                    if (runtime) ctx.runtimeJars += extracted.allJars
                }
                ArtifactType.PomOnly -> Unit
            }
        }
        log(step, "Resolved ${artifacts.size} artifacts (${ctx.aars.size} AARs, ${ctx.compileJars.size} jars)")
    }

    /** Full Maven resolution: BOM import, transitive POM walk, downloads. */
    private suspend fun ProducerScope<BuildEvent>.resolveWithMaven(
        mavenDeps: List<GradleDependency.Maven>,
        platforms: List<GradleDependency.Platform>,
        repositories: List<MavenRepository>,
    ): List<CachedArtifact> {
        val step = BuildStep.MavenResolve
        // BOM-managed versions.
        val managed = mutableMapOf<String, String>()
        for (platform in platforms) {
            val coordinate = runCatching { MavenCoordinate.parse(platform.coordinate) }.getOrNull() ?: continue
            log(step, "Importing BOM $coordinate")
            mavenResolver.effectivePom(coordinate, MavenRepository.Defaults)?.first?.managedVersions?.let(managed::putAll)
        }

        val roots = mavenDeps.map { d ->
            val version = if (d.version == "+" || d.version.isBlank()) managed["${d.group}:${d.artifact}"] ?: d.version else d.version
            MavenCoordinate(d.group, d.artifact, version, d.classifier, d.extension)
        }.distinctBy { it.groupArtifact }
        log(step, "Resolving ${roots.size} declared dependencies")
        val events = mutableListOf<ResolveEvent>()
        val result = mavenResolver.resolve(roots, repositories) { events += it }
        for (event in events) {
            when (event) {
                is ResolveEvent.Downloaded -> log(step, "Downloaded ${event.coordinate} from ${event.repository} (${event.bytes / 1024} KB)")
                is ResolveEvent.VersionConflict -> log(step, "Conflict ${event.groupArtifact}: ${event.versions.joinToString()} → ${event.selected}", LogLevel.Warning)
                is ResolveEvent.Failed -> log(step, "Failed ${event.coordinate}: ${event.reason}", LogLevel.Error)
                else -> Unit
            }
        }
        if (result.failures.isNotEmpty()) {
            throw BuildStepException(step, result.failures.map {
                CompilerDiagnostic(DiagnosticSource.Maven, Severity.Error, "Could not resolve ${it.coordinate}: ${it.reason}")
            })
        }
        return result.artifacts.map { CachedArtifact(it.coordinate, it.type, it.file) }
    }

    private fun repositoriesFor(declared: List<String>): List<MavenRepository> {
        val list = mutableListOf<MavenRepository>()
        if (declared.isEmpty() || "google" in declared) list += MavenRepository.Google
        if (declared.isEmpty() || "mavenCentral" in declared) list += MavenRepository.Central
        declared.filter { it.startsWith("http") }.forEach { url ->
            list += if (url.contains("jitpack.io")) MavenRepository.JitPack else MavenRepository(url.hashCode().toString(), url, url)
        }
        if (list.none { it.id == MavenRepository.JitPack.id }) list += MavenRepository.JitPack
        return list.distinctBy { it.baseUrl }
    }

    private fun globMatches(pattern: String, name: String): Boolean {
        val regex = pattern.replace(".", "\\.").replace("**/", "").replace("*", ".*").replace("?", ".")
        return Regex(regex).matches(name)
    }

    // ---------------------------------------------------------------- Step 2: AAPT2

    private suspend fun ProducerScope<BuildEvent>.processResources(ctx: BuildContext) {
        val step = BuildStep.Aapt2Resources
        val aapt2Binary = toolchain.aapt2Binary()
            ?: fail(
                step,
                DiagnosticSource.Aapt2,
                "aapt2 is not bundled in this build of Codein. Place an Android (bionic) aapt2 build at " +
                    "app/src/main/jniLibs/<abi>/libaapt2.so and rebuild Codein; it is executed from nativeLibraryDir.",
            )
        aapt2.version(aapt2Binary)?.let { log(step, "Using $it") }
        val compiledDir = File(ctx.intermediates, "compiled-res").apply { mkdirs() }
        val compiled = mutableListOf<File>()

        // Library resources first (lowest priority), compiled once and cached next to the exploded AAR.
        for (aar in ctx.aars) {
            val res = aar.resDir ?: continue
            if (!aar.hasResources) continue
            val out = File(aar.root, "codein-res.flat.zip")
            if (!out.isFile) {
                log(step, "Compiling resources of ${aar.coordinate}")
                val r = aapt2.compileDirectory(aapt2Binary, res, out)
                if (!r.success) throw BuildStepException(step, r.diagnostics)
            }
            compiled += out
        }
        for ((dir, cfg) in ctx.libraryModules) {
            val res = File(dir, "src/main/res").takeIf { it.isDirectory } ?: continue
            val out = File(compiledDir, "module-${cfg.modulePath.trim(':').replace(':', '-')}.zip")
            log(step, "Compiling resources of ${cfg.modulePath}")
            val r = aapt2.compileDirectory(aapt2Binary, res, out) { trySend(BuildEvent.Log(step, LogLevel.Verbose, it)) }
            r.diagnostics.forEach { send(BuildEvent.Diagnostic(step, it)) }
            if (!r.success) throw BuildStepException(step, r.diagnostics)
            compiled += out
        }
        for (res in ctx.sourceRoots("res")) {
            val out = File(compiledDir, "app-${res.parentFile?.name}.zip")
            log(step, "Compiling ${res.path.removePrefix(ctx.request.projectDir.path)}")
            val r = aapt2.compileDirectory(aapt2Binary, res, out)
            r.diagnostics.filter { it.severity != Severity.Error }.forEach { send(BuildEvent.Diagnostic(step, it)) }
            if (!r.success) throw BuildStepException(step, r.diagnostics)
            compiled += out
        }

        // Manifest merge.
        val appManifest = File(ctx.moduleDir, "src/main/AndroidManifest.xml")
        if (!appManifest.isFile) fail(step, DiagnosticSource.Aapt2, "Missing ${appManifest.path}")
        // R and BuildConfig live in the namespace; the installed package is the application id.
        val packageName = ctx.config.namespace ?: ctx.config.packageName
        val applicationId = ctx.config.packageName + (ctx.config.buildTypes.firstOrNull { it.name == ctx.variantName }?.applicationIdSuffix ?: "")
        val libraryManifests = ctx.aars.map { it.manifest } + ctx.libraryModules.map { File(it.first, "src/main/AndroidManifest.xml") }
        val merged = manifestMerger.merge(appManifest, libraryManifests, File(ctx.intermediates, "merged_manifest/AndroidManifest.xml"), applicationId, namespace = packageName)
        merged.warnings.forEach { log(step, it, LogLevel.Warning) }
        log(step, "Merged ${libraryManifests.count { it.isFile }} library manifests")

        val extraPackages = (ctx.aars.filter { it.hasResources || it.rTxt != null }.map { it.packageName } +
            ctx.libraryModules.mapNotNull { it.second.namespace }).filter { it != packageName }.distinct()
        val genDir = File(ctx.buildDir, "generated/source/r/${ctx.variantName}").apply { deleteRecursively(); mkdirs() }
        val assets = ctx.sourceRoots("assets") + ctx.aars.mapNotNull { it.assetsDir }
        val debuggable = !ctx.request.variant.isRelease

        val linkOptions = Aapt2LinkOptions(
            manifest = merged.mergedManifest,
            androidJar = ctx.androidJar,
            compiledResources = compiled,
            outputApk = File(ctx.intermediates, "resources.ap_"),
            javaOutputDir = genDir,
            textSymbolsFile = File(ctx.intermediates, "R.txt"),
            proguardRulesFile = File(ctx.intermediates, "aapt_rules.txt"),
            minSdk = ctx.config.minSdk,
            targetSdk = ctx.config.targetSdk,
            versionCode = ctx.config.versionCode,
            versionName = ctx.config.versionName + (ctx.config.buildTypes.firstOrNull { it.name == ctx.variantName }?.versionNameSuffix ?: ""),
            packageName = packageName,
            manifestPackage = applicationId,
            extraPackages = extraPackages,
            assetsDirs = assets,
            protoFormat = false,
            debuggable = debuggable,
        )
        log(step, "Linking ${compiled.size} resource sets against android.jar (API ${ctx.config.compileSdk})")
        val link = aapt2.link(aapt2Binary, linkOptions)
        link.diagnostics.forEach { send(BuildEvent.Diagnostic(step, it)) }
        if (!link.success) throw BuildStepException(step, link.diagnostics.filter { it.severity == Severity.Error }.ifEmpty { link.diagnostics })
        ctx.resourcesApk = linkOptions.outputApk

        if (ctx.request.variant.producesBundle) {
            val protoOptions = linkOptions.copy(
                outputApk = File(ctx.intermediates, "resources-proto.ap_"),
                javaOutputDir = File(ctx.intermediates, "r-proto-unused"),
                textSymbolsFile = File(ctx.intermediates, "R-proto.txt"),
                proguardRulesFile = null,
                protoFormat = true,
            )
            log(step, "Linking proto-format resources for the App Bundle")
            val proto = aapt2.link(aapt2Binary, protoOptions)
            if (!proto.success) throw BuildStepException(step, proto.diagnostics)
            ctx.protoResourcesApk = protoOptions.outputApk
        }

        // BuildConfig.
        val buildConfigDir = File(ctx.buildDir, "generated/source/buildConfig/${ctx.variantName}").apply { deleteRecursively(); mkdirs() }
        BuildConfigGenerator.generate(buildConfigDir, ctx.config.namespace ?: packageName, applicationId, ctx.variantName, debuggable, ctx.config.versionCode, ctx.config.versionName)
        ctx.libraryModules.forEach { (_, cfg) ->
            cfg.namespace?.let { ns -> BuildConfigGenerator.generate(buildConfigDir, ns, applicationId, ctx.variantName, debuggable, cfg.versionCode, cfg.versionName) }
        }
        ctx.generatedSourceDirs += genDir
        ctx.generatedSourceDirs += buildConfigDir
    }

    // ---------------------------------------------------------------- Step 3: Compile

    private suspend fun ProducerScope<BuildEvent>.compileSources(ctx: BuildContext) {
        val step = BuildStep.Compile
        val javaRoots = ctx.sourceRoots("java") + ctx.sourceRoots("kotlin")
        val javaFiles = (javaRoots + ctx.generatedSourceDirs).flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "java" }.toList()
        }
        val kotlinFiles = javaRoots.flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        val classesOut = File(ctx.intermediates, "javac-classes").apply { deleteRecursively(); mkdirs() }
        val kotlinOut = File(ctx.intermediates, "kotlin-classes").apply { deleteRecursively(); mkdirs() }

        if (kotlinFiles.isNotEmpty()) {
            if (!kotlinCompiler.isAvailable()) fail(step, DiagnosticSource.Kotlin, kotlinCompiler.unavailableReason())
            val composePlugin = toolchain.kotlinCompilerDir.listFiles { f -> f.name.startsWith("kotlin-compose-compiler-plugin") && f.extension == "jar" }?.firstOrNull()
            if (ctx.config.usesCompose && composePlugin == null) {
                fail(step, DiagnosticSource.Kotlin, "This module uses Jetpack Compose. Open Settings > Toolchain and download the Kotlin compiler with the Compose plugin.")
            }
            log(step, "Compiling ${kotlinFiles.size} Kotlin file(s)")
            val result = kotlinCompiler.compile(
                KotlinCompileRequest(
                    sourceFiles = kotlinFiles,
                    javaSourceRoots = javaRoots + ctx.generatedSourceDirs,
                    classpath = ctx.compileJars,
                    androidJar = ctx.androidJar,
                    outputDir = kotlinOut,
                    moduleName = ctx.request.modulePath.trim(':').ifEmpty { "app" },
                    composePluginJar = composePlugin,
                ),
            ) { line -> trySend(BuildEvent.Log(step, LogLevel.Verbose, line)) }
            result.diagnostics.filter { it.severity != Severity.Error }.forEach { send(BuildEvent.Diagnostic(step, it)) }
            if (!result.success) throw BuildStepException(step, result.diagnostics.filter { it.severity == Severity.Error })
            ctx.classesDirs += kotlinOut
        }

        if (javaFiles.isNotEmpty()) {
            log(step, "Compiling ${javaFiles.size} Java file(s) with ECJ")
            val result = ecj.compile(
                JavaCompileRequest(
                    sourceFiles = javaFiles,
                    classpath = ctx.compileJars + listOfNotNull(kotlinOut.takeIf { kotlinFiles.isNotEmpty() }, toolchain.lambdaStubsJar()),
                    bootClasspath = ctx.androidJar,
                    outputDir = classesOut,
                    debugInfo = !ctx.request.variant.isRelease,
                ),
            ) { line -> trySend(BuildEvent.Log(step, LogLevel.Verbose, line)) }
            result.diagnostics.filter { it.severity == Severity.Warning }.take(50).forEach { send(BuildEvent.Diagnostic(step, it)) }
            if (!result.success) throw BuildStepException(step, result.diagnostics.filter { it.severity == Severity.Error })
            ctx.classesDirs += classesOut
        }
        if (ctx.classesDirs.isEmpty()) fail(step, DiagnosticSource.Ecj, "No Java or Kotlin sources found under src/main/java or src/main/kotlin")
    }

    // ---------------------------------------------------------------- Step 4: D8

    private suspend fun ProducerScope<BuildEvent>.dex(ctx: BuildContext) {
        val step = BuildStep.Dexing
        val dexOut = File(ctx.intermediates, "dex")
        val cache = File(toolchain.workRoot, "predex")
        val kotlinStdlib = if (ctx.classesDirs.any { it.name == "kotlin-classes" }) {
            toolchain.kotlinCompilerDir.listFiles { f -> f.name.startsWith("kotlin-stdlib") && f.extension == "jar" }.orEmpty().toList()
                .filter { std -> ctx.runtimeJars.none { it.name.startsWith("kotlin-stdlib") } }
        } else emptyList()
        val runtime = (ctx.runtimeJars + kotlinStdlib).distinctBy { it.absolutePath }
        log(step, "D8: ${ctx.classesDirs.size} class dir(s), ${runtime.size} library jar(s), minSdk ${ctx.config.minSdk}")
        val result = dexer.dexApplication(
            classesDirs = ctx.classesDirs,
            libraryJars = runtime,
            androidJar = ctx.androidJar,
            minSdk = ctx.config.minSdk,
            release = ctx.request.variant.isRelease,
            cacheDir = cache,
            outputDir = dexOut,
        ) { trySend(BuildEvent.Log(step, LogLevel.Info, it)) }
        result.diagnostics.filter { it.severity == Severity.Warning }.take(30).forEach { send(BuildEvent.Diagnostic(step, it)) }
        if (!result.success) throw BuildStepException(step, result.diagnostics.filter { it.severity == Severity.Error }.ifEmpty { result.diagnostics })
        ctx.dexFiles += result.dexFiles
        ctx.runtimeJars.clear()
        ctx.runtimeJars += runtime
        log(step, "Produced ${result.dexFiles.size} dex file(s): ${result.dexFiles.joinToString { "${it.name} (${it.length() / 1024} KB)" }}")
    }

    // ---------------------------------------------------------------- Step 5: Sign & Package

    private suspend fun ProducerScope<BuildEvent>.packageAndSign(ctx: BuildContext) {
        val step = BuildStep.SignAndPackage
        val key = signingKey(ctx)
        log(step, "Signing with ${key.alias} (${key.certificate.subjectX500Principal.name})")
        val nativeDirs = ctx.sourceRoots("jniLibs") + ctx.aars.mapNotNull { it.jniDir }
        val moduleName = ctx.request.modulePath.trim(':').substringAfterLast(':').ifEmpty { "app" }

        if (ctx.request.variant.producesBundle) {
            val protoApk = ctx.protoResourcesApk ?: fail(step, DiagnosticSource.Bundletool, "Proto resources were not generated")
            val javaResources = packager.extractJavaResources(ctx.runtimeJars, File(ctx.intermediates, "java-res"))
            val unsigned = File(ctx.intermediates, "bundle/$moduleName-release-unsigned.aab")
            log(step, "Building App Bundle with bundletool")
            try {
                aabBuilder.build(
                    BundleModuleInputs(protoApk, ctx.dexFiles, nativeDirs, emptyList(), javaResources),
                    File(ctx.intermediates, "bundle"),
                    unsigned,
                )
            } catch (e: Exception) {
                fail(step, DiagnosticSource.Bundletool, "bundletool: ${e.message}")
            }
            val output = File(ctx.buildDir, "outputs/bundle/release/$moduleName-release.aab")
            output.parentFile?.mkdirs()
            signer.signBundle(unsigned, output, key, ctx.config.minSdk)
            log(step, "Signed bundle: ${output.name} (${output.length() / 1024} KB)")
            ctx.outputFile = output
        } else {
            val resources = ctx.resourcesApk ?: fail(step, DiagnosticSource.Packager, "Resources were not linked")
            val unsigned = File(ctx.intermediates, "apk/$moduleName-${ctx.variantName}-unsigned.apk")
            log(step, "Packaging ${ctx.dexFiles.size} dex file(s), resources and native libraries")
            packager.packageApk(PackagingInputs(resources, ctx.dexFiles, nativeDirs, ctx.runtimeJars), unsigned)
            val output = File(ctx.buildDir, "outputs/apk/${ctx.variantName}/$moduleName-${ctx.variantName}.apk")
            output.parentFile?.mkdirs()
            log(step, "zipalign + apksig (v1, v2, v3)")
            val report = signer.alignAndSign(unsigned, output, key, ctx.config.minSdk)
            report.warnings.take(10).forEach { log(step, it, LogLevel.Warning) }
            if (!report.verified) {
                throw BuildStepException(step, report.errors.map { CompilerDiagnostic(DiagnosticSource.Signer, Severity.Error, it) })
            }
            log(step, "Signed APK verified (v1=${report.v1}, v2=${report.v2}, v3=${report.v3}): ${output.name} (${output.length() / 1024} KB)")
            ctx.outputFile = output
        }
    }

    private suspend fun signingKey(ctx: BuildContext): SigningKey {
        if (!ctx.request.variant.isRelease) return debugKeystore.getOrCreate()
        vault.refresh()
        val releaseId = vault.releaseKeystoreId.value
            ?: fail(BuildStep.SignAndPackage, DiagnosticSource.Signer, "No release key selected. Open Keystore Vault and choose \"Use for release\" on a keystore.")
        return runCatching { vault.signingKey(releaseId) }.getOrElse {
            fail(BuildStep.SignAndPackage, DiagnosticSource.Signer, "Unable to load release key: ${it.message}")
        }
    }
}
