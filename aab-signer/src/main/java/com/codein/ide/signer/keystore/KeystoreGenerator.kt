package com.codein.ide.signer.keystore

import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.InputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class KeystoreType(val extension: String, val displayName: String) {
    Jks("jks", "JKS (.jks)"),
    Pkcs12("p12", "PKCS#12 (.p12 / .pkcs12)"),
}

/** X.500 distinguished name fields. */
data class DistinguishedName(
    val commonName: String,
    val organizationalUnit: String = "",
    val organization: String = "",
    val locality: String = "",
    val state: String = "",
    val country: String = "",
) {
    fun toX500(): org.bouncycastle.asn1.x500.X500Name = X500NameBuilder(BCStyle.INSTANCE).apply {
        if (commonName.isNotBlank()) addRDN(BCStyle.CN, commonName)
        if (organizationalUnit.isNotBlank()) addRDN(BCStyle.OU, organizationalUnit)
        if (organization.isNotBlank()) addRDN(BCStyle.O, organization)
        if (locality.isNotBlank()) addRDN(BCStyle.L, locality)
        if (state.isNotBlank()) addRDN(BCStyle.ST, state)
        if (country.isNotBlank()) addRDN(BCStyle.C, country.uppercase(Locale.US).take(2))
    }.build()
}

data class KeystoreSpec(
    val type: KeystoreType,
    val alias: String,
    val storePassword: CharArray,
    val keyPassword: CharArray,
    val keySize: Int,
    val validityYears: Int,
    val dn: DistinguishedName,
) {
    init {
        require(alias.isNotBlank()) { "Alias is required" }
        require(storePassword.size >= 6) { "Keystore password must be at least 6 characters" }
        require(keyPassword.size >= 6) { "Key password must be at least 6 characters" }
        require(keySize == 2048 || keySize == 3072 || keySize == 4096) { "Key size must be 2048, 3072 or 4096" }
        require(validityYears in 1..100) { "Validity must be between 1 and 100 years" }
        require(dn.commonName.isNotBlank()) { "Common name (CN) is required" }
    }
}

/** A private key and certificate chain ready for signing. */
data class SigningKey(val alias: String, val privateKey: PrivateKey, val certificates: List<X509Certificate>) {
    val certificate: X509Certificate get() = certificates.first()
}

data class CertificateFingerprints(val sha1: String, val sha256: String, val md5: String) {
    companion object {
        fun of(cert: X509Certificate): CertificateFingerprints {
            fun digest(alg: String) = MessageDigest.getInstance(alg).digest(cert.encoded).joinToString(":") { "%02X".format(it) }
            return CertificateFingerprints(digest("SHA-1"), digest("SHA-256"), digest("MD5"))
        }
    }
}

/** Creates and loads `.jks` / `.pkcs12` keystores using BouncyCastle for certificate generation. */
@Singleton
class KeystoreGenerator @Inject constructor() {

    private val random = SecureRandom()

    fun generateSigningKey(spec: KeystoreSpec): SigningKey {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(spec.keySize, random)
        val keyPair = generator.generateKeyPair()
        val now = System.currentTimeMillis()
        val notBefore = Date(now - TimeUnit.DAYS.toMillis(1))
        val notAfter = Date(now + TimeUnit.DAYS.toMillis(365L * spec.validityYears))
        val subject = spec.dn.toX500()
        val serial = BigInteger(64, random).abs().add(BigInteger.ONE)
        val builder = JcaX509v3CertificateBuilder(subject, serial, notBefore, notAfter, subject, keyPair.public)
            .addExtension(Extension.basicConstraints, false, BasicConstraints(false))
            .addExtension(Extension.keyUsage, false, KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment))
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val certificate = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        certificate.verify(keyPair.public)
        return SigningKey(spec.alias, keyPair.private, listOf(certificate))
    }

    /** Generates a key pair + self-signed certificate and writes the keystore file. */
    fun create(spec: KeystoreSpec, output: File): SigningKey {
        val key = generateSigningKey(spec)
        write(key, spec.type, spec.storePassword, spec.keyPassword, output)
        return key
    }

    fun write(key: SigningKey, type: KeystoreType, storePassword: CharArray, keyPassword: CharArray, output: File) {
        output.parentFile?.mkdirs()
        val temp = File(output.parentFile, output.name + ".tmp")
        temp.outputStream().use { out ->
            when (type) {
                KeystoreType.Jks -> JksKeyStore.write(
                    out,
                    listOf(JksKeyStore.Entry.PrivateKeyEntry(key.alias, System.currentTimeMillis(), key.privateKey, key.certificates)),
                    storePassword,
                    keyPassword,
                )
                KeystoreType.Pkcs12 -> {
                    val ks = KeyStore.getInstance("PKCS12")
                    ks.load(null, null)
                    // PKCS#12 stores use a single password for store and key, matching keytool behavior.
                    ks.setKeyEntry(key.alias, key.privateKey, storePassword, key.certificates.toTypedArray())
                    ks.store(out, storePassword)
                }
            }
        }
        if (output.exists()) output.delete()
        check(temp.renameTo(output)) { "Unable to write ${output.path}" }
    }

    /** Detects the keystore format from its header bytes. */
    fun detectType(file: File): KeystoreType = file.inputStream().use { detectType(it.readNBytesCompat(4)) }

    fun detectType(header: ByteArray): KeystoreType = if (JksKeyStore.isJks(header)) KeystoreType.Jks else KeystoreType.Pkcs12

    /** Lists aliases holding private keys. */
    fun listAliases(file: File, storePassword: CharArray): List<String> = when (detectType(file)) {
        KeystoreType.Jks -> file.inputStream().use { input ->
            // Aliases are readable with the store password alone; key recovery uses the same password as a best guess.
            runCatching { JksKeyStore.read(input, storePassword, storePassword) }.getOrNull()
                ?.map { it.alias }
                ?: listJksAliasesWithoutKeys(file)
        }
        KeystoreType.Pkcs12 -> {
            val ks = KeyStore.getInstance("PKCS12")
            file.inputStream().use { ks.load(it, storePassword) }
            ks.aliases().toList().filter { ks.isKeyEntry(it) }
        }
    }

    private fun listJksAliasesWithoutKeys(file: File): List<String> {
        val bytes = file.readBytes()
        val body = bytes.copyOfRange(0, bytes.size - 20)
        val aliases = mutableListOf<String>()
        java.io.DataInputStream(body.inputStream()).use { input ->
            input.readInt()
            val version = input.readInt()
            val count = input.readInt()
            repeat(count) {
                when (input.readInt()) {
                    1 -> {
                        aliases += input.readUTF()
                        input.readLong()
                        input.skipBytes(input.readInt())
                        repeat(input.readInt()) {
                            if (version == 2) input.readUTF()
                            input.skipBytes(input.readInt())
                        }
                    }
                    2 -> {
                        input.readUTF(); input.readLong()
                        if (version == 2) input.readUTF()
                        input.skipBytes(input.readInt())
                    }
                }
            }
        }
        return aliases
    }

    fun load(file: File, storePassword: CharArray, alias: String, keyPassword: CharArray): SigningKey = when (detectType(file)) {
        KeystoreType.Jks -> {
            val entries = file.inputStream().use { JksKeyStore.read(it, storePassword, keyPassword) }
            val entry = entries.filterIsInstance<JksKeyStore.Entry.PrivateKeyEntry>()
                .firstOrNull { it.alias.equals(alias, ignoreCase = true) }
                ?: throw IllegalArgumentException("Alias '$alias' not found in ${file.name}")
            SigningKey(alias, entry.privateKey, entry.chain.map { it as X509Certificate })
        }
        KeystoreType.Pkcs12 -> {
            val ks = KeyStore.getInstance("PKCS12")
            file.inputStream().use { ks.load(it, storePassword) }
            val key = ks.getKey(alias, keyPassword) as? PrivateKey
                ?: ks.getKey(alias, storePassword) as? PrivateKey
                ?: throw IllegalArgumentException("Alias '$alias' not found in ${file.name}")
            val chain = ks.getCertificateChain(alias).map { it as X509Certificate }
            SigningKey(alias, key, chain)
        }
    }
}

private fun InputStream.readNBytesCompat(n: Int): ByteArray {
    val buffer = ByteArray(n)
    var read = 0
    while (read < n) {
        val r = read(buffer, read, n - read)
        if (r < 0) break
        read += r
    }
    return buffer.copyOf(read)
}
