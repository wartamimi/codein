package com.codein.ide.project

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.codein.ide.compiler.gradle.GradleScriptParser
import com.codein.ide.data.ProjectDao
import com.codein.ide.data.ProjectEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates, imports and lists projects. All projects live under `filesDir/projects` so Codein never
 * needs broad storage permissions (Scoped Storage on Android 10+); imports from a user-picked
 * folder are copied in through the Storage Access Framework.
 */
@Singleton
class ProjectRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ProjectDao,
    private val gradleParser: GradleScriptParser,
) {
    val projectsRoot: File get() = File(context.filesDir, "projects").apply { mkdirs() }

    val recentProjects: Flow<List<ProjectEntity>> = dao.observeAll()

    suspend fun create(spec: ProjectSpec): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            ProjectSpec.validateName(spec.name)?.let { error(it) }
            ProjectSpec.validatePackage(spec.packageName)?.let { error(it) }
            val dir = uniqueDir(spec.name)
            ProjectTemplates.generate(spec).forEach { (relative, content) ->
                val file = File(dir, relative)
                file.parentFile?.mkdirs()
                file.writeText(content)
            }
            val now = System.currentTimeMillis()
            dao.upsert(
                ProjectEntity(
                    path = dir.absolutePath,
                    name = spec.name,
                    packageName = spec.packageName,
                    language = spec.effectiveLanguage.name,
                    template = spec.template.name,
                    minSdk = spec.minSdk,
                    targetSdk = spec.targetSdk,
                    createdAt = now,
                    lastOpenedAt = now,
                ),
            )
            dir
        }
    }

    /** Copies a project folder chosen with ACTION_OPEN_DOCUMENT_TREE into private storage. */
    suspend fun importFromTree(treeUri: Uri): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: error("Cannot open the selected folder")
            val name = root.name ?: "Imported"
            val dir = uniqueDir(name)
            copyTree(root, dir)
            if (!File(dir, "settings.gradle").exists() && !File(dir, "settings.gradle.kts").exists() && !File(dir, "app").isDirectory) {
                dir.deleteRecursively()
                error("The folder does not look like an Android Gradle project (no settings.gradle or app/ module)")
            }
            register(dir)
            dir
        }
    }

    /** Registers an existing directory (e.g. a fresh Git clone) as a project. */
    suspend fun register(dir: File) {
        val settings = runCatching { gradleParser.parseSettings(dir) }.getOrNull()
        val module = runCatching { gradleParser.parseModule(dir, ":app") }.getOrNull()
        val now = System.currentTimeMillis()
        val hasKotlin = dir.walkTopDown().onEnter { it.name != "build" && it.name != ".git" }.any { it.extension == "kt" }
        dao.upsert(
            ProjectEntity(
                path = dir.absolutePath,
                name = settings?.rootProjectName ?: dir.name,
                packageName = module?.packageName ?: "",
                language = if (hasKotlin) ProjectLanguage.Kotlin.name else ProjectLanguage.Java.name,
                template = "Imported",
                minSdk = module?.minSdk ?: 21,
                targetSdk = module?.targetSdk ?: 35,
                createdAt = now,
                lastOpenedAt = now,
            ),
        )
    }

    fun newCloneTarget(name: String): File = uniqueDir(name)

    suspend fun touch(path: String) = dao.touch(path, System.currentTimeMillis())

    suspend fun find(path: String): ProjectEntity? = dao.find(path)

    suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        val dir = File(path)
        if (dir.canonicalPath.startsWith(projectsRoot.canonicalPath + File.separator)) dir.deleteRecursively()
        dao.delete(path)
    }

    private fun uniqueDir(name: String): File {
        val base = name.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifEmpty { "Project" }
        var candidate = File(projectsRoot, base)
        var n = 2
        while (candidate.exists()) candidate = File(projectsRoot, "${base}_${n++}")
        candidate.mkdirs()
        return candidate
    }

    private fun copyTree(source: DocumentFile, target: File) {
        target.mkdirs()
        source.listFiles().forEach { child ->
            val childName = child.name ?: return@forEach
            if (child.isDirectory) {
                if (childName == "build" || childName == ".gradle" || childName == ".idea") return@forEach
                copyTree(child, File(target, childName))
            } else {
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    File(target, childName).outputStream().use { input.copyTo(it) }
                }
            }
        }
    }
}
