package com.codein.ide.vcs.git

import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import org.eclipse.jgit.api.TransportCommand
import org.eclipse.jgit.transport.JschConfigSessionFactory
import org.eclipse.jgit.transport.OpenSshConfig
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.util.FS
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * SSH support for Git remotes (`git@github.com:user/repo.git`, `ssh://...`).
 *
 * Codein keeps one RSA-3072 identity in app-private storage. Host keys are trusted on first use
 * and stored in a private known_hosts file; a changed host key is rejected. The JSch fork used
 * (com.github.mwiede:jsch) speaks `rsa-sha2-256/512`, which GitHub, GitLab and Bitbucket require.
 */
class GitSsh(private val sshDir: File) {

    val privateKeyFile: File get() = File(sshDir, "id_rsa")
    val publicKeyFile: File get() = File(sshDir, "id_rsa.pub")
    val knownHostsFile: File get() = File(sshDir, "known_hosts")

    fun hasKey(): Boolean = privateKeyFile.isFile && publicKeyFile.isFile

    /** The OpenSSH public key line to paste into GitHub/GitLab, or null when no key exists. */
    fun publicKey(): String? = publicKeyFile.takeIf { it.isFile }?.readText()?.trim()

    /** Generates (or replaces) the identity. Returns the new public key line. */
    fun generateKey(comment: String): String {
        sshDir.mkdirs()
        val pair = KeyPair.genKeyPair(JSch(), KeyPair.RSA, KEY_BITS)
        try {
            val privateOut = ByteArrayOutputStream()
            pair.writePrivateKey(privateOut)
            val publicOut = ByteArrayOutputStream()
            pair.writePublicKey(publicOut, comment.ifBlank { "codein" })
            writePrivate(privateKeyFile, privateOut.toByteArray())
            publicKeyFile.writeBytes(publicOut.toByteArray())
        } finally {
            pair.dispose()
        }
        return publicKey().orEmpty()
    }

    fun deleteKey() {
        privateKeyFile.delete()
        publicKeyFile.delete()
    }

    /** Forgets every trusted host key (needed after a server legitimately rotates its key). */
    fun clearKnownHosts() {
        knownHostsFile.delete()
    }

    /** Applies SSH settings to clone/fetch/push/pull commands whose remote is an SSH URL. */
    fun <C : TransportCommand<C, *>> configure(command: C): C =
        command.setTransportConfigCallback { transport ->
            if (transport is SshTransport) transport.sshSessionFactory = sessionFactory()
        }

    private fun sessionFactory(): JschConfigSessionFactory = object : JschConfigSessionFactory() {
        override fun configure(host: OpenSshConfig.Host, session: Session) {
            session.setConfig("StrictHostKeyChecking", "ask")
            session.setConfig("PreferredAuthentications", "publickey")
            session.userInfo = TrustOnFirstUse
        }

        override fun createDefaultJSch(fs: FS): JSch {
            val jsch = JSch()
            sshDir.mkdirs()
            if (!knownHostsFile.exists()) knownHostsFile.createNewFile()
            jsch.setKnownHosts(knownHostsFile.absolutePath)
            if (!hasKey()) throw GitOperationException("No SSH key yet. Generate one in the Git panel and add the public key to your Git host.")
            jsch.addIdentity(privateKeyFile.absolutePath)
            return jsch
        }
    }

    /** Accepts unknown hosts (and records them) but never a host whose key has changed. */
    private object TrustOnFirstUse : UserInfo {
        override fun getPassphrase(): String? = null
        override fun getPassword(): String? = null
        override fun promptPassword(message: String?): Boolean = false
        override fun promptPassphrase(message: String?): Boolean = false
        override fun promptYesNo(message: String?): Boolean {
            val text = message.orEmpty()
            if (text.contains("HAS CHANGED", ignoreCase = true) || text.contains("man-in-the-middle", ignoreCase = true)) return false
            return text.contains("can't be established", ignoreCase = true) || text.contains("does not exist", ignoreCase = true)
        }
        override fun showMessage(message: String?) = Unit
    }

    private fun writePrivate(file: File, bytes: ByteArray) {
        file.delete()
        file.writeBytes(bytes)
        file.setReadable(false, false)
        file.setReadable(true, true)
        file.setWritable(false, false)
        file.setWritable(true, true)
    }

    companion object {
        const val KEY_BITS = 3072

        /** True for `user@host:path` scp-style URLs and `ssh://` URLs. */
        fun isSshUrl(url: String): Boolean {
            val u = url.trim()
            if (u.startsWith("ssh://") || u.startsWith("git+ssh://")) return true
            if (u.contains("://")) return false
            return Regex("""^[A-Za-z0-9._-]+@[A-Za-z0-9.-]+:.+""").matches(u)
        }
    }
}
