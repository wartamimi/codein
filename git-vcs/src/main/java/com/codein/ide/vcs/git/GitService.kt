package com.codein.ide.vcs.git

import android.content.Context
import com.codein.ide.core.ui.icons.GitFileStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.filter.PathFilter
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class GitStatusSnapshot(
    val isRepository: Boolean,
    val branch: String? = null,
    val ahead: Int = 0,
    val behind: Int = 0,
    val files: Map<String, GitFileStatus> = emptyMap(),
    val staged: Set<String> = emptySet(),
    val remoteUrl: String? = null,
    val hasCommits: Boolean = false,
) {
    val isClean: Boolean get() = files.isEmpty()
}

data class GitCommitInfo(val id: String, val shortId: String, val message: String, val author: String, val time: Long) {
    val relativeTime: String get() = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(time))
}

data class GitBranchInfo(val name: String, val isCurrent: Boolean, val isRemote: Boolean)

/** Thrown with a user-presentable message when a Git operation fails. */
class GitOperationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Local Git operations on a project directory through JGit (pure Java, no `git` binary needed,
 * so it works inside the app sandbox on every Android version).
 */
@Singleton
class GitService @Inject constructor(@ApplicationContext context: Context) {

    /** SSH identity and host-key store for `git@host:repo` remotes. */
    val ssh = GitSsh(File(context.filesDir, "ssh"))

    init {
        // JGit resolves ~/.gitconfig from user.home, which is empty on Android.
        if (System.getProperty("user.home").isNullOrEmpty() || System.getProperty("user.home") == "/") {
            System.setProperty("user.home", context.filesDir.absolutePath)
        }
    }

    fun isRepository(dir: File): Boolean = File(dir, ".git").exists()

    suspend fun init(dir: File, initialBranch: String = "main"): Unit = io("init") {
        Git.init().setDirectory(dir).setInitialBranch(initialBranch).call().use { git ->
            val ignore = File(dir, ".gitignore")
            if (!ignore.exists()) ignore.writeText(DEFAULT_GITIGNORE)
            git.repository.config.apply {
                setBoolean("core", null, "filemode", false)
                setString("core", null, "autocrlf", "input")
                save()
            }
        }
    }

    suspend fun clone(url: String, into: File, credentials: GitCredentials?): Unit = io("clone") {
        ssh.configure(
            Git.cloneRepository()
                .setURI(url)
                .setDirectory(into)
                .setCredentialsProvider(credentials?.provider()),
        ).call().close()
    }

    suspend fun status(dir: File): GitStatusSnapshot = withContext(Dispatchers.IO) {
        if (!isRepository(dir)) return@withContext GitStatusSnapshot(isRepository = false)
        try {
            open(dir).use { git ->
                val repo = git.repository
                val status = git.status().call()
                val files = LinkedHashMap<String, GitFileStatus>()
                status.untracked.forEach { files[it] = GitFileStatus.Untracked }
                status.added.forEach { files[it] = GitFileStatus.Added }
                status.changed.forEach { files[it] = GitFileStatus.Modified }
                status.modified.forEach { files[it] = GitFileStatus.Modified }
                status.missing.forEach { files[it] = GitFileStatus.Deleted }
                status.removed.forEach { files[it] = GitFileStatus.Deleted }
                status.conflicting.forEach { files[it] = GitFileStatus.Conflicting }
                val staged = status.added + status.changed + status.removed
                val branch = repo.branch
                val tracking = branch?.let { runCatching { BranchTrackingStatus.of(repo, it) }.getOrNull() }
                GitStatusSnapshot(
                    isRepository = true,
                    branch = branch,
                    ahead = tracking?.aheadCount ?: 0,
                    behind = tracking?.behindCount ?: 0,
                    files = files.toSortedMap(),
                    staged = staged,
                    remoteUrl = repo.config.getString("remote", "origin", "url"),
                    hasCommits = repo.resolve(Constants.HEAD) != null,
                )
            }
        } catch (e: Exception) {
            throw GitOperationException("Could not read Git status: ${e.message}", e)
        }
    }

    /** Unified diff of a working-tree file against HEAD (or a synthetic "new file" diff for untracked files). */
    suspend fun diff(dir: File, path: String): String = io("diff") {
        open(dir).use { git ->
            val repo = git.repository
            val out = ByteArrayOutputStream()
            val head = repo.resolve("HEAD^{tree}")
            val command = git.diff().setOutputStream(out).setPathFilter(PathFilter.create(path))
            if (head != null) {
                val parser = CanonicalTreeParser()
                repo.newObjectReader().use { reader -> parser.reset(reader, head) }
                command.setOldTree(parser)
            }
            command.call()
            val text = out.toString(Charsets.UTF_8.name())
            if (text.isNotBlank()) return@use text
            val file = File(dir, path)
            if (file.isFile) newFileDiff(path, file) else ""
        }
    }

    suspend fun stage(dir: File, paths: Collection<String>): Unit = io("stage") {
        open(dir).use { git ->
            val existing = paths.filter { File(dir, it).exists() }
            val deleted = paths - existing.toSet()
            if (existing.isNotEmpty()) git.add().apply { existing.forEach { addFilepattern(it) } }.call()
            if (deleted.isNotEmpty()) git.rm().setCached(true).apply { deleted.forEach { addFilepattern(it) } }.call()
        }
    }

    suspend fun stageAll(dir: File): Unit = io("stage all") {
        open(dir).use { git ->
            git.add().addFilepattern(".").call()
            git.add().addFilepattern(".").setUpdate(true).call()
        }
    }

    suspend fun unstage(dir: File, paths: Collection<String>): Unit = io("unstage") {
        open(dir).use { git ->
            if (git.repository.resolve(Constants.HEAD) == null) {
                git.rm().setCached(true).apply { paths.forEach { addFilepattern(it) } }.call()
            } else {
                git.reset().apply { paths.forEach { addPath(it) } }.call()
            }
        }
    }

    /** Discards working-tree changes to [path] (restores it from HEAD, or deletes it when untracked). */
    suspend fun discard(dir: File, path: String): Unit = io("discard") {
        open(dir).use { git ->
            val tracked = git.repository.resolve(Constants.HEAD) != null &&
                runCatching { git.repository.readDirCache().findEntry(path) >= 0 }.getOrDefault(false)
            if (tracked) git.checkout().addPath(path).call() else File(dir, path).delete()
        }
    }

    suspend fun commit(dir: File, message: String, author: PersonIdent, stageAllFirst: Boolean): GitCommitInfo = io("commit") {
        require(message.isNotBlank()) { "Commit message is empty" }
        open(dir).use { git ->
            if (stageAllFirst) {
                git.add().addFilepattern(".").call()
                git.add().addFilepattern(".").setUpdate(true).call()
            }
            val commit = git.commit().setMessage(message.trim()).setAuthor(author).setCommitter(author).call()
            GitCommitInfo(commit.name, commit.name.take(7), commit.shortMessage, commit.authorIdent.name, commit.commitTime * 1000L)
        }
    }

    suspend fun log(dir: File, limit: Int = 50): List<GitCommitInfo> = withContext(Dispatchers.IO) {
        if (!isRepository(dir)) return@withContext emptyList()
        runCatching {
            open(dir).use { git ->
                if (git.repository.resolve(Constants.HEAD) == null) return@use emptyList()
                git.log().setMaxCount(limit).call().map {
                    GitCommitInfo(it.name, it.name.take(7), it.shortMessage, it.authorIdent.name, it.commitTime * 1000L)
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun branches(dir: File): List<GitBranchInfo> = io("list branches") {
        open(dir).use { git ->
            val current = git.repository.fullBranch
            val local = git.branchList().call().map { GitBranchInfo(Repository.shortenRefName(it.name), it.name == current, false) }
            val remote = runCatching {
                git.branchList().setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.REMOTE).call()
                    .map { GitBranchInfo(Repository.shortenRefName(it.name), false, true) }
            }.getOrDefault(emptyList())
            if (local.isEmpty() && git.repository.branch != null) {
                listOf(GitBranchInfo(git.repository.branch, true, false)) + remote
            } else local + remote
        }
    }

    suspend fun checkout(dir: File, branch: String, create: Boolean): Unit = io("checkout") {
        open(dir).use { git ->
            val isRemote = branch.startsWith("origin/")
            val name = if (isRemote) branch.removePrefix("origin/") else branch
            val cmd = git.checkout().setName(name)
            if (create || isRemote) {
                cmd.setCreateBranch(true)
                if (isRemote) {
                    cmd.setStartPoint(branch)
                    cmd.setUpstreamMode(org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode.TRACK)
                }
            }
            cmd.call()
        }
    }

    suspend fun deleteBranch(dir: File, branch: String): Unit = io("delete branch") {
        open(dir).use { git -> git.branchDelete().setBranchNames(branch).setForce(true).call() }
    }

    suspend fun setRemote(dir: File, url: String): Unit = io("set remote") {
        open(dir).use { git ->
            val remotes = git.remoteList().call()
            if (remotes.any { it.name == "origin" }) {
                git.remoteSetUrl().setRemoteName("origin").setRemoteUri(URIish(url)).call()
            } else {
                git.remoteAdd().setName("origin").setUri(URIish(url)).call()
            }
        }
    }

    suspend fun push(dir: File, credentials: GitCredentials?): String = io("push") {
        open(dir).use { git ->
            val branch = git.repository.branch ?: throw GitOperationException("Not on a branch")
            val results = ssh.configure(
                git.push()
                    .setRemote("origin")
                    .add(branch)
                    .setCredentialsProvider(credentials?.provider()),
            ).call()
            val problems = results.flatMap { it.remoteUpdates }.filter {
                it.status != RemoteRefUpdate.Status.OK && it.status != RemoteRefUpdate.Status.UP_TO_DATE
            }
            if (problems.isNotEmpty()) {
                throw GitOperationException(problems.joinToString { "${it.remoteName}: ${it.status}${it.message?.let { m -> " ($m)" }.orEmpty()}" })
            }
            git.repository.config.apply {
                if (getString("branch", branch, "remote") == null) {
                    setString("branch", branch, "remote", "origin")
                    setString("branch", branch, "merge", "refs/heads/$branch")
                    save()
                }
            }
            "Pushed $branch to origin"
        }
    }

    suspend fun pull(dir: File, credentials: GitCredentials?): String = io("pull") {
        open(dir).use { git ->
            val result = ssh.configure(git.pull().setRemote("origin").setCredentialsProvider(credentials?.provider())).call()
            if (!result.isSuccessful) {
                val merge = result.mergeResult
                val conflicts = merge?.conflicts?.keys.orEmpty()
                throw GitOperationException(
                    if (conflicts.isNotEmpty()) "Merge conflicts in: ${conflicts.joinToString()}" else "Pull failed: ${merge?.mergeStatus ?: result.rebaseResult?.status}",
                )
            }
            "Pulled: ${result.mergeResult?.mergeStatus ?: "up to date"}"
        }
    }

    private fun open(dir: File): Git = Git.open(dir)

    private suspend fun <T> io(operation: String, block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: GitOperationException) {
            throw e
        } catch (e: GitAPIException) {
            throw GitOperationException("Git $operation failed: ${e.message}", e)
        } catch (e: Exception) {
            throw GitOperationException("Git $operation failed: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    private fun newFileDiff(path: String, file: File): String {
        if (file.length() > 1_000_000) return "Binary or large file: ${file.length()} bytes"
        val text = runCatching { file.readText() }.getOrDefault("")
        if (text.indexOf('\u0000') >= 0) return "Binary file $path"
        val lines = if (text.isEmpty()) emptyList() else text.removeSuffix("\n").split('\n')
        return buildString {
            append("diff --git a/").append(path).append(" b/").append(path).append('\n')
            append("new file\n--- /dev/null\n+++ b/").append(path).append('\n')
            append("@@ -0,0 +1,").append(lines.size).append(" @@\n")
            lines.forEach { append('+').append(it).append('\n') }
        }
    }

    companion object {
        val DEFAULT_GITIGNORE = """
            .gradle/
            build/
            .idea/
            .kotlin/
            local.properties
            *.keystore
            *.jks
            *.apk
            *.aab
        """.trimIndent() + "\n"
    }
}

/** HTTPS credentials (username + personal access token) and the commit identity. */
data class GitCredentials(val username: String, val token: String, val authorName: String, val authorEmail: String) {
    fun provider(): CredentialsProvider? =
        if (token.isBlank()) null else UsernamePasswordCredentialsProvider(username.ifBlank { "git" }, token)

    fun ident(): PersonIdent = PersonIdent(authorName.ifBlank { "Codein User" }, authorEmail.ifBlank { "codein@localhost" })
}
