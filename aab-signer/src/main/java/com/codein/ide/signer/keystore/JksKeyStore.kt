package com.codein.ide.signer.keystore

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.pkcs.EncryptedPrivateKeyInfo
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Reader/writer for the Java KeyStore (`.jks`) file format.
 *
 * Android's security providers do not ship a `JKS` KeyStore implementation, so Codein implements the
 * format directly: magic `0xFEEDFEED`, version 2, private key entries protected with Sun's
 * `KeyProtector` scheme (OID 1.3.6.1.4.1.42.2.17.1.1, SHA-1 keystream XOR), and a trailing SHA-1
 * integrity digest keyed with the store password and the "Mighty Aphrodite" whitener. Files written
 * here open in `keytool`, Android Studio and Gradle signing configs.
 */
object JksKeyStore {

    private const val MAGIC = 0xFEEDFEED.toInt()
    private const val VERSION_2 = 2
    private const val TAG_PRIVATE_KEY = 1
    private const val TAG_TRUSTED_CERT = 2
    private const val KEY_PROTECTOR_OID = "1.3.6.1.4.1.42.2.17.1.1"
    private const val DIGEST_LEN = 20
    private const val SALT_LEN = 20

    sealed interface Entry {
        val alias: String
        val creationDate: Long

        data class PrivateKeyEntry(
            override val alias: String,
            override val creationDate: Long,
            val privateKey: PrivateKey,
            val chain: List<Certificate>,
        ) : Entry

        data class TrustedCertEntry(override val alias: String, override val creationDate: Long, val certificate: Certificate) : Entry
    }

    private fun passwordBytes(password: CharArray): ByteArray {
        val bytes = ByteArray(password.size * 2)
        password.forEachIndexed { i, c ->
            bytes[i * 2] = (c.code shr 8).toByte()
            bytes[i * 2 + 1] = c.code.toByte()
        }
        return bytes
    }

    private fun integrityDigest(storePassword: CharArray): MessageDigest = MessageDigest.getInstance("SHA-1").apply {
        update(passwordBytes(storePassword))
        update("Mighty Aphrodite".toByteArray(Charsets.UTF_8))
    }

    /** Sun KeyProtector `protect()`. */
    fun protectKey(plainKey: ByteArray, keyPassword: CharArray, random: SecureRandom = SecureRandom()): ByteArray {
        val passwd = passwordBytes(keyPassword)
        val sha = MessageDigest.getInstance("SHA-1")
        val salt = ByteArray(SALT_LEN).also(random::nextBytes)
        val rounds = (plainKey.size + DIGEST_LEN - 1) / DIGEST_LEN
        val xorKey = ByteArray(plainKey.size)
        var digest = salt
        var offset = 0
        for (i in 0 until rounds) {
            sha.update(passwd)
            sha.update(digest)
            digest = sha.digest()
            val len = minOf(DIGEST_LEN, plainKey.size - offset)
            System.arraycopy(digest, 0, xorKey, offset, len)
            offset += len
        }
        val encrypted = ByteArray(plainKey.size) { (plainKey[it].toInt() xor xorKey[it].toInt()).toByte() }
        sha.update(passwd)
        sha.update(plainKey)
        val check = sha.digest()
        val protectedKey = ByteArray(SALT_LEN + encrypted.size + DIGEST_LEN)
        System.arraycopy(salt, 0, protectedKey, 0, SALT_LEN)
        System.arraycopy(encrypted, 0, protectedKey, SALT_LEN, encrypted.size)
        System.arraycopy(check, 0, protectedKey, SALT_LEN + encrypted.size, DIGEST_LEN)
        val algorithm = AlgorithmIdentifier(ASN1ObjectIdentifier(KEY_PROTECTOR_OID), DERNull.INSTANCE)
        return EncryptedPrivateKeyInfo(algorithm, protectedKey).encoded
    }

    /** Sun KeyProtector `recover()`; returns the PKCS#8 encoding. */
    fun recoverKey(encryptedPrivateKeyInfo: ByteArray, keyPassword: CharArray): ByteArray {
        val info = EncryptedPrivateKeyInfo.getInstance(encryptedPrivateKeyInfo)
        require(info.encryptionAlgorithm.algorithm.id == KEY_PROTECTOR_OID) {
            "Unsupported JKS key protection algorithm ${info.encryptionAlgorithm.algorithm.id}"
        }
        val protectedKey = info.encryptedData
        val passwd = passwordBytes(keyPassword)
        val salt = protectedKey.copyOfRange(0, SALT_LEN)
        val encrypted = protectedKey.copyOfRange(SALT_LEN, protectedKey.size - DIGEST_LEN)
        val expectedCheck = protectedKey.copyOfRange(protectedKey.size - DIGEST_LEN, protectedKey.size)
        val sha = MessageDigest.getInstance("SHA-1")
        val xorKey = ByteArray(encrypted.size)
        var digest = salt
        var offset = 0
        while (offset < encrypted.size) {
            sha.update(passwd)
            sha.update(digest)
            digest = sha.digest()
            val len = minOf(DIGEST_LEN, encrypted.size - offset)
            System.arraycopy(digest, 0, xorKey, offset, len)
            offset += len
        }
        val plain = ByteArray(encrypted.size) { (encrypted[it].toInt() xor xorKey[it].toInt()).toByte() }
        sha.update(passwd)
        sha.update(plain)
        require(MessageDigest.isEqual(sha.digest(), expectedCheck)) { "Wrong key password" }
        return plain
    }

    fun write(output: OutputStream, entries: List<Entry>, storePassword: CharArray, keyPassword: CharArray) {
        val body = ByteArrayOutputStream()
        DataOutputStream(body).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION_2)
            out.writeInt(entries.size)
            for (entry in entries) {
                when (entry) {
                    is Entry.PrivateKeyEntry -> {
                        out.writeInt(TAG_PRIVATE_KEY)
                        out.writeUTF(entry.alias.lowercase())
                        out.writeLong(entry.creationDate)
                        val protectedKey = protectKey(entry.privateKey.encoded, keyPassword)
                        out.writeInt(protectedKey.size)
                        out.write(protectedKey)
                        out.writeInt(entry.chain.size)
                        for (cert in entry.chain) {
                            out.writeUTF(cert.type)
                            val encoded = cert.encoded
                            out.writeInt(encoded.size)
                            out.write(encoded)
                        }
                    }
                    is Entry.TrustedCertEntry -> {
                        out.writeInt(TAG_TRUSTED_CERT)
                        out.writeUTF(entry.alias.lowercase())
                        out.writeLong(entry.creationDate)
                        out.writeUTF(entry.certificate.type)
                        val encoded = entry.certificate.encoded
                        out.writeInt(encoded.size)
                        out.write(encoded)
                    }
                }
            }
            out.flush()
        }
        val bytes = body.toByteArray()
        val digest = integrityDigest(storePassword).apply { update(bytes) }.digest()
        output.write(bytes)
        output.write(digest)
        output.flush()
    }

    fun read(input: InputStream, storePassword: CharArray, keyPassword: CharArray): List<Entry> {
        val all = input.readBytes()
        require(all.size > DIGEST_LEN) { "Keystore file is truncated" }
        val body = all.copyOfRange(0, all.size - DIGEST_LEN)
        val storedDigest = all.copyOfRange(all.size - DIGEST_LEN, all.size)
        val computed = integrityDigest(storePassword).apply { update(body) }.digest()
        require(MessageDigest.isEqual(computed, storedDigest)) { "Keystore was tampered with, or password was incorrect" }

        val certFactory = CertificateFactory.getInstance("X.509")
        val entries = mutableListOf<Entry>()
        DataInputStream(body.inputStream()).use { input ->
            require(input.readInt() == MAGIC) { "Not a JKS keystore" }
            val version = input.readInt()
            require(version == 1 || version == 2) { "Unsupported JKS version $version" }
            val count = input.readInt()
            repeat(count) {
                when (val tag = input.readInt()) {
                    TAG_PRIVATE_KEY -> {
                        val alias = input.readUTF()
                        val date = input.readLong()
                        val keyBytes = ByteArray(input.readInt()).also(input::readFully)
                        val chainSize = input.readInt()
                        val chain = (0 until chainSize).map {
                            if (version == 2) input.readUTF()
                            val encoded = ByteArray(input.readInt()).also(input::readFully)
                            certFactory.generateCertificate(encoded.inputStream())
                        }
                        val pkcs8 = recoverKey(keyBytes, keyPassword)
                        val algorithm = chain.firstOrNull()?.publicKey?.algorithm ?: "RSA"
                        val privateKey = KeyFactory.getInstance(algorithm).generatePrivate(PKCS8EncodedKeySpec(pkcs8))
                        entries += Entry.PrivateKeyEntry(alias, date, privateKey, chain)
                    }
                    TAG_TRUSTED_CERT -> {
                        val alias = input.readUTF()
                        val date = input.readLong()
                        if (version == 2) input.readUTF()
                        val encoded = ByteArray(input.readInt()).also(input::readFully)
                        entries += Entry.TrustedCertEntry(alias, date, certFactory.generateCertificate(encoded.inputStream()))
                    }
                    else -> throw IllegalArgumentException("Unknown JKS entry tag $tag")
                }
            }
        }
        return entries
    }

    fun isJks(header: ByteArray): Boolean =
        header.size >= 4 && header[0] == 0xFE.toByte() && header[1] == 0xED.toByte() && header[2] == 0xFE.toByte() && header[3] == 0xED.toByte()
}
