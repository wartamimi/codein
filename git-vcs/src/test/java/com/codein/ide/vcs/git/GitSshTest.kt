package com.codein.ide.vcs.git

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class GitSshTest {
    @Test
    fun detectsSshUrls() {
        assertTrue(GitSsh.isSshUrl("git@github.com:user/repo.git"))
        assertTrue(GitSsh.isSshUrl("ssh://git@gitlab.com/group/repo.git"))
        assertFalse(GitSsh.isSshUrl("https://github.com/user/repo.git"))
        assertFalse(GitSsh.isSshUrl("/local/path"))
    }

    @Test
    fun generatesOpenSshKeyPair() {
        val dir = Files.createTempDirectory("ssh").toFile()
        val ssh = GitSsh(dir)
        val pub = ssh.generateKey("codein@test")
        assertTrue(pub.startsWith("ssh-rsa "))
        assertTrue(pub.endsWith("codein@test"))
        assertTrue(ssh.privateKeyFile.readText().contains("PRIVATE KEY"))
        ssh.deleteKey()
        assertFalse(ssh.hasKey())
        dir.deleteRecursively()
    }
}
