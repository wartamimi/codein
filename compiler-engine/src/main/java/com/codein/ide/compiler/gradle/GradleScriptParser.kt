package com.codein.ide.compiler.gradle

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A single `dependencies { }` entry. */
sealed interface GradleDependency {
    val configuration: String

    data class Maven(override val configuration: String, val group: String, val artifact: String, val version: String, val classifier: String? = null, val extension: String? = null) : GradleDependency {
        val coordinate: String get() = buildString {
            append(group).append(':').append(artifact).append(':').append(version)
            if (classifier != null) append(':').append(classifier)
            if (extension != null) append('@').append(extension)
        }
    }

    data class Project(override val configuration: String, val path: String) : GradleDependency
    data class Files(override val configuration: String, val paths: List<String>) : GradleDependency
    data class FileTree(override val configuration: String, val dir: String, val includes: List<String>) : GradleDependency
    data class Platform(override val configuration: String, val coordinate: String) : GradleDependency
}

data class BuildTypeConfig(
    val name: String,
    val minifyEnabled: Boolean = false,
    val shrinkResources: Boolean = false,
    val debuggable: Boolean = name == "debug",
    val applicationIdSuffix: String? = null,
    val versionNameSuffix: String? = null,
)

data class SigningConfigRef(val name: String, val storeFile: String?, val storePassword: String?, val keyAlias: String?, val keyPassword: String?)

/** Everything Codein needs from a module's `build.gradle(.kts)`. */
data class GradleModuleConfig(
    val modulePath: String,
    val plugins: List<String>,
    val namespace: String?,
    val applicationId: String?,
    val compileSdk: Int,
    val minSdk: Int,
    val targetSdk: Int,
    val versionCode: Int,
    val versionName: String,
    val buildTypes: List<BuildTypeConfig>,
    val signingConfigs: List<SigningConfigRef>,
    val dependencies: List<GradleDependency>,
    val javaVersion: String?,
    val usesKotlin: Boolean,
    val usesCompose: Boolean,
    val isApplication: Boolean,
) {
    val packageName: String get() = applicationId ?: namespace ?: "com.example.app"
}

data class GradleSettings(val rootProjectName: String, val includedModules: List<String>, val repositories: List<String>)

/**
 * Lightweight, dependency-free parser for both Groovy (`build.gradle`) and Kotlin DSL
 * (`build.gradle.kts`) scripts. It understands the subset of the Android Gradle DSL that the
 * on-device build needs: SDK levels, identity, build types, signing configs and dependencies,
 * including version-catalog (`libs.xxx`) references resolved from `gradle/libs.versions.toml`.
 */
@Singleton
class GradleScriptParser @Inject constructor() {

    fun parseSettings(projectDir: File): GradleSettings {
        val file = listOf("settings.gradle.kts", "settings.gradle").map { File(projectDir, it) }.firstOrNull { it.isFile }
        val text = file?.readText()?.let(::stripComments) ?: ""
        val rootName = Regex("""rootProject\.name\s*=\s*["']([^"']+)["']""").find(text)?.groupValues?.get(1) ?: projectDir.name
        val modules = mutableListOf<String>()
        Regex("""include\s*\(?([^\n)]*)\)?""").findAll(text).forEach { m ->
            Regex("""["'](:?[\w\-.:]+)["']""").findAll(m.groupValues[1]).forEach { modules += it.groupValues[1].let { p -> if (p.startsWith(":")) p else ":$p" } }
        }
        val repos = mutableListOf<String>()
        if (Regex("""\bgoogle\(\)""").containsMatchIn(text)) repos += "google"
        if (Regex("""\bmavenCentral\(\)""").containsMatchIn(text)) repos += "mavenCentral"
        Regex("""maven\s*\{[^}]*url\s*=?\s*(?:uri\()?["']([^"']+)["']""").findAll(text).forEach { repos += it.groupValues[1] }
        if (modules.isEmpty() && File(projectDir, "app").isDirectory) modules += ":app"
        return GradleSettings(rootName, modules.distinct(), repos.distinct())
    }

    fun findModuleScript(moduleDir: File): File? =
        listOf("build.gradle.kts", "build.gradle").map { File(moduleDir, it) }.firstOrNull { it.isFile }

    fun parseModule(projectDir: File, modulePath: String): GradleModuleConfig {
        val moduleDir = File(projectDir, modulePath.trimStart(':').replace(':', '/'))
        val script = findModuleScript(moduleDir)
            ?: throw IllegalStateException("No build.gradle(.kts) found in ${moduleDir.path}")
        val catalog = VersionCatalog.load(File(projectDir, "gradle/libs.versions.toml"))
        return parseModuleText(modulePath, script.readText(), catalog)
    }

    fun parseModuleText(modulePath: String, rawText: String, catalog: VersionCatalog = VersionCatalog.EMPTY): GradleModuleConfig {
        val text = stripComments(rawText)
        val plugins = parsePlugins(text, catalog)
        val android = extractBlock(text, "android") ?: ""
        val defaultConfig = extractBlock(android, "defaultConfig") ?: ""

        fun intValue(source: String, vararg names: String): Int? = names.firstNotNullOfOrNull { name ->
            Regex("""\b$name\s*(?:=|\s)\s*(?:android-)?(\d+)""").find(source)?.groupValues?.get(1)?.toIntOrNull()
        }
        fun stringValue(source: String, name: String): String? =
            Regex("""\b$name\s*(?:=|\s)\s*["']([^"']*)["']""").find(source)?.groupValues?.get(1)

        val compileSdk = intValue(android, "compileSdk", "compileSdkVersion") ?: 35
        val minSdk = intValue(defaultConfig, "minSdk", "minSdkVersion") ?: 26
        val targetSdk = intValue(defaultConfig, "targetSdk", "targetSdkVersion") ?: compileSdk
        val versionCode = intValue(defaultConfig, "versionCode") ?: 1
        val versionName = stringValue(defaultConfig, "versionName") ?: "1.0"
        val applicationId = stringValue(defaultConfig, "applicationId")
        val namespace = stringValue(android, "namespace")
        val javaVersion = Regex("""JavaVersion\.VERSION_(\d+(?:_\d+)?)""").find(android)?.groupValues?.get(1)?.replace('_', '.')

        val isApplication = plugins.any { it == "com.android.application" }
        val usesKotlin = plugins.any { it.startsWith("org.jetbrains.kotlin") || it == "kotlin-android" }
        val usesCompose = plugins.any { it == "org.jetbrains.kotlin.plugin.compose" } ||
            Regex("""compose\s*=?\s*true""").containsMatchIn(extractBlock(android, "buildFeatures") ?: "")

        return GradleModuleConfig(
            modulePath = modulePath,
            plugins = plugins,
            namespace = namespace,
            applicationId = applicationId,
            compileSdk = compileSdk,
            minSdk = minSdk,
            targetSdk = targetSdk,
            versionCode = versionCode,
            versionName = versionName,
            buildTypes = parseBuildTypes(extractBlock(android, "buildTypes") ?: ""),
            signingConfigs = parseSigningConfigs(extractBlock(android, "signingConfigs") ?: ""),
            dependencies = parseDependencies(extractBlock(text, "dependencies", topLevelOnly = true) ?: "", catalog),
            javaVersion = javaVersion,
            usesKotlin = usesKotlin,
            usesCompose = usesCompose,
            isApplication = isApplication,
        )
    }

    private fun parsePlugins(text: String, catalog: VersionCatalog): List<String> {
        val result = mutableListOf<String>()
        val block = extractBlock(text, "plugins") ?: ""
        Regex("""id\s*\(?\s*["']([\w.\-]+)["']""").findAll(block).forEach { result += it.groupValues[1] }
        Regex("""alias\s*\(\s*libs\.plugins\.([\w.]+)\s*\)""").findAll(block).forEach { m ->
            catalog.plugin(m.groupValues[1])?.let { result += it }
        }
        Regex("""kotlin\s*\(\s*["']([\w.\-]+)["']\s*\)""").findAll(block).forEach { result += "org.jetbrains.kotlin.${it.groupValues[1]}" }
        Regex("""apply\s+plugin:\s*["']([\w.\-]+)["']""").findAll(text).forEach { result += it.groupValues[1] }
        return result.map { if (it == "kotlin-android") "org.jetbrains.kotlin.android" else it }.distinct()
    }

    private fun parseBuildTypes(block: String): List<BuildTypeConfig> {
        val types = mutableListOf<BuildTypeConfig>()
        val namePattern = Regex("""(?:getByName\s*\(\s*["'](\w+)["']\s*\)|create\s*\(\s*["'](\w+)["']\s*\)|named\s*\(\s*["'](\w+)["']\s*\)|\b(\w+))\s*\{""")
        var index = 0
        while (index < block.length) {
            val match = namePattern.find(block, index) ?: break
            val name = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: break
            val bodyStart = match.range.last
            val bodyEnd = matchingBrace(block, bodyStart)
            if (bodyEnd < 0) break
            val body = block.substring(bodyStart + 1, bodyEnd)
            types += BuildTypeConfig(
                name = name,
                minifyEnabled = Regex("""(?:isMinifyEnabled|minifyEnabled)\s*=?\s*true""").containsMatchIn(body),
                shrinkResources = Regex("""(?:isShrinkResources|shrinkResources)\s*=?\s*true""").containsMatchIn(body),
                debuggable = Regex("""(?:isDebuggable|debuggable)\s*=?\s*true""").containsMatchIn(body) || name == "debug",
                applicationIdSuffix = Regex("""applicationIdSuffix\s*=?\s*["']([^"']+)["']""").find(body)?.groupValues?.get(1),
                versionNameSuffix = Regex("""versionNameSuffix\s*=?\s*["']([^"']+)["']""").find(body)?.groupValues?.get(1),
            )
            index = bodyEnd + 1
        }
        if (types.none { it.name == "debug" }) types += BuildTypeConfig("debug", debuggable = true)
        if (types.none { it.name == "release" }) types += BuildTypeConfig("release")
        return types
    }

    private fun parseSigningConfigs(block: String): List<SigningConfigRef> {
        val configs = mutableListOf<SigningConfigRef>()
        val namePattern = Regex("""(?:create\s*\(\s*["'](\w+)["']\s*\)|getByName\s*\(\s*["'](\w+)["']\s*\)|\b(\w+))\s*\{""")
        var index = 0
        while (index < block.length) {
            val match = namePattern.find(block, index) ?: break
            val name = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: break
            val bodyEnd = matchingBrace(block, match.range.last)
            if (bodyEnd < 0) break
            val body = block.substring(match.range.last + 1, bodyEnd)
            fun str(key: String) = Regex("""$key\s*=?\s*(?:file\s*\()?\s*["']([^"']+)["']""").find(body)?.groupValues?.get(1)
            configs += SigningConfigRef(name, str("storeFile"), str("storePassword"), str("keyAlias"), str("keyPassword"))
            index = bodyEnd + 1
        }
        return configs
    }

    private fun parseDependencies(block: String, catalog: VersionCatalog): List<GradleDependency> {
        val deps = mutableListOf<GradleDependency>()
        val configs = "implementation|api|compileOnly|runtimeOnly|debugImplementation|releaseImplementation|testImplementation|androidTestImplementation|ksp|kapt|annotationProcessor|coreLibraryDesugaring"
        val linePattern = Regex("""\b($configs)\s*[( ]\s*(.+)""")
        block.lines().forEach { rawLine ->
            val line = rawLine.trim()
            val match = linePattern.find(line) ?: return@forEach
            val configuration = match.groupValues[1]
            val rest = match.groupValues[2].trim().removeSuffix(")").trim()
            when {
                rest.startsWith("platform(") -> {
                    val inner = rest.removePrefix("platform(").trim()
                    val coordinate = Regex("""["']([^"']+)["']""").find(inner)?.groupValues?.get(1)
                        ?: Regex("""libs\.([\w.]+)""").find(inner)?.groupValues?.get(1)?.let { catalog.library(it) }
                    if (coordinate != null) deps += GradleDependency.Platform(configuration, coordinate)
                }
                rest.startsWith("project(") -> {
                    Regex("""["'](:[\w\-:]+)["']""").find(rest)?.let { deps += GradleDependency.Project(configuration, it.groupValues[1]) }
                }
                rest.startsWith("files(") -> {
                    val paths = Regex("""["']([^"']+)["']""").findAll(rest).map { it.groupValues[1] }.toList()
                    deps += GradleDependency.Files(configuration, paths)
                }
                rest.startsWith("fileTree(") -> {
                    val dir = Regex("""dir\s*[:=]\s*["']([^"']+)["']""").find(rest)?.groupValues?.get(1)
                        ?: Regex("""fileTree\(\s*["']([^"']+)["']""").find(rest)?.groupValues?.get(1) ?: "libs"
                    val includes = Regex("""include\s*[:=]\s*(?:listOf\(|\[)([^)\]]*)""").find(rest)?.groupValues?.get(1)
                        ?.let { inc -> Regex("""["']([^"']+)["']""").findAll(inc).map { it.groupValues[1] }.toList() }
                        ?: listOf("*.jar")
                    deps += GradleDependency.FileTree(configuration, dir, includes)
                }
                rest.startsWith("libs.") || rest.startsWith("(libs.") -> {
                    val alias = Regex("""libs\.([\w.]+)""").find(rest)?.groupValues?.get(1) ?: return@forEach
                    catalog.library(alias)?.let { coordinate -> parseCoordinate(configuration, coordinate)?.let(deps::add) }
                }
                else -> {
                    val quoted = Regex("""["']([^"']+)["']""").find(rest)?.groupValues?.get(1)
                    if (quoted != null) {
                        parseCoordinate(configuration, quoted)?.let(deps::add)
                    } else {
                        val group = Regex("""group\s*[:=]\s*["']([^"']+)["']""").find(rest)?.groupValues?.get(1)
                        val name = Regex("""name\s*[:=]\s*["']([^"']+)["']""").find(rest)?.groupValues?.get(1)
                        val version = Regex("""version\s*[:=]\s*["']([^"']+)["']""").find(rest)?.groupValues?.get(1)
                        if (group != null && name != null) deps += GradleDependency.Maven(configuration, group, name, version ?: "+")
                    }
                }
            }
        }
        return deps
    }

    private fun parseCoordinate(configuration: String, coordinate: String): GradleDependency.Maven? {
        val (base, extension) = coordinate.split('@').let { it[0] to it.getOrNull(1) }
        val parts = base.split(':')
        if (parts.size < 2) return null
        return GradleDependency.Maven(
            configuration = configuration,
            group = parts[0],
            artifact = parts[1],
            version = parts.getOrNull(2) ?: "+",
            classifier = parts.getOrNull(3),
            extension = extension,
        )
    }

    companion object {
        /** Removes `//` line comments and block comments while preserving string literals. */
        fun stripComments(text: String): String {
            val out = StringBuilder(text.length)
            var i = 0
            var inString: Char? = null
            while (i < text.length) {
                val c = text[i]
                val next = text.getOrNull(i + 1)
                if (inString != null) {
                    out.append(c)
                    if (c == '\\' && next != null) {
                        out.append(next); i += 2; continue
                    }
                    if (c == inString) inString = null
                    i++
                    continue
                }
                when {
                    c == '"' || c == '\'' -> { inString = c; out.append(c); i++ }
                    c == '/' && next == '/' -> { while (i < text.length && text[i] != '\n') i++ }
                    c == '/' && next == '*' -> {
                        val end = text.indexOf("*/", i + 2)
                        i = if (end < 0) text.length else end + 2
                    }
                    else -> { out.append(c); i++ }
                }
            }
            return out.toString()
        }

        /** Returns the index of the brace matching the `{` at [openIndex], or -1. */
        fun matchingBrace(text: String, openIndex: Int): Int {
            var depth = 0
            var i = openIndex
            var inString: Char? = null
            while (i < text.length) {
                val c = text[i]
                if (inString != null) {
                    if (c == '\\') { i += 2; continue }
                    if (c == inString) inString = null
                } else {
                    when (c) {
                        '"', '\'' -> inString = c
                        '{' -> depth++
                        '}' -> { depth--; if (depth == 0) return i }
                    }
                }
                i++
            }
            return -1
        }

        /** Extracts the body of the first `name { ... }` block. */
        fun extractBlock(text: String, name: String, topLevelOnly: Boolean = false): String? {
            val pattern = Regex("""(?<![\w.])$name\s*\{""")
            for (match in pattern.findAll(text)) {
                if (topLevelOnly && depthAt(text, match.range.first) != 0) continue
                val open = match.range.last
                val close = matchingBrace(text, open)
                if (close > open) return text.substring(open + 1, close)
            }
            return null
        }

        private fun depthAt(text: String, index: Int): Int {
            var depth = 0
            var inString: Char? = null
            var i = 0
            while (i < index) {
                val c = text[i]
                if (inString != null) {
                    if (c == '\\') { i += 2; continue }
                    if (c == inString) inString = null
                } else when (c) {
                    '"', '\'' -> inString = c
                    '{' -> depth++
                    '}' -> depth--
                }
                i++
            }
            return depth
        }
    }
}

/** Minimal `libs.versions.toml` reader supporting `[versions]`, `[libraries]` and `[plugins]`. */
class VersionCatalog private constructor(
    private val versions: Map<String, String>,
    private val libraries: Map<String, String>,
    private val plugins: Map<String, String>,
) {
    /** Resolves a dotted accessor (`androidx.core.ktx`) to `group:name:version`. */
    fun library(accessor: String): String? = libraries[normalize(accessor)]

    fun plugin(accessor: String): String? = plugins[normalize(accessor)]

    fun version(key: String): String? = versions[normalize(key)]

    companion object {
        val EMPTY = VersionCatalog(emptyMap(), emptyMap(), emptyMap())

        private fun normalize(key: String) = key.replace('-', '.').replace('_', '.').lowercase()

        fun load(file: File): VersionCatalog = if (file.isFile) parse(file.readText()) else EMPTY

        fun parse(text: String): VersionCatalog {
            val versions = mutableMapOf<String, String>()
            val libraries = mutableMapOf<String, String>()
            val plugins = mutableMapOf<String, String>()
            var section = ""
            text.lines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.forEach { line ->
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.removeSurrounding("[", "]").trim()
                    return@forEach
                }
                val key = line.substringBefore('=').trim()
                val value = line.substringAfter('=').trim()
                when (section) {
                    "versions" -> versions[normalize(key)] = value.trim('"')
                    "libraries" -> libraries[normalize(key)] = value
                    "plugins" -> plugins[normalize(key)] = value
                }
            }
            fun versionOf(value: String): String? {
                Regex("""version\.ref\s*=\s*"([^"]+)"""").find(value)?.let { return versions[normalize(it.groupValues[1])] }
                return Regex("""version\s*=\s*"([^"]+)"""").find(value)?.groupValues?.get(1)
            }
            val resolvedLibs = libraries.mapValues { (_, value) ->
                if (value.startsWith("\"")) return@mapValues value.trim('"')
                val module = Regex("""module\s*=\s*"([^"]+)"""").find(value)?.groupValues?.get(1)
                val group = Regex("""group\s*=\s*"([^"]+)"""").find(value)?.groupValues?.get(1)
                val name = Regex("""name\s*=\s*"([^"]+)"""").find(value)?.groupValues?.get(1)
                val base = module ?: if (group != null && name != null) "$group:$name" else return@mapValues ""
                val version = versionOf(value)
                if (version != null) "$base:$version" else base
            }.filterValues { it.isNotEmpty() }
            val resolvedPlugins = plugins.mapValues { (_, value) ->
                if (value.startsWith("\"")) value.trim('"').substringBefore(':')
                else Regex("""id\s*=\s*"([^"]+)"""").find(value)?.groupValues?.get(1) ?: ""
            }.filterValues { it.isNotEmpty() }
            return VersionCatalog(versions, resolvedLibs, resolvedPlugins)
        }
    }
}
