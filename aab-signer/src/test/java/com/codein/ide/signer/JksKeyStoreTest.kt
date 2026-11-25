package com.codein.ide.signer

import com.codein.ide.signer.apk.LocalHeaderScanner
import com.codein.ide.signer.apk.ZipAligner
import com.codein.ide.signer.keystore.DistinguishedName
import com.codein.ide.signer.keystore.JksKeyStore
import com.codein.ide.signer.keystore.KeystoreGenerator
import com.codein.ide.signer.keystore.KeystoreSpec
import com.codein.ide.signer.keystore.KeystoreType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.KeyStore
import java.security.PrivateKey
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class JksKeyStoreTest {

    private val spec = KeystoreSpec(
        type = KeystoreType.Jks,
        alias = "release",
        storePassword = "storepass".toCharArray(),
        keyPassword = "keypass1".toCharArray(),
        keySize = 2048,
        validityYears = 25,
        dn = DistinguishedName("Codein Test", "Eng", "Codein", "Jakarta", "DKI", "ID"),
    )

    @Test
    fun jdkKeyStoreReadsCodeinJks() {
        val key = KeystoreGenerator().generateSigningKey(spec)
        val bytes = ByteArrayOutputStream().also {
            JksKeyStore.write(it, listOf(JksKeyStore.Entry.PrivateKeyEntry("release", 0L, key.privateKey, key.certificates)), spec.storePassword, spec.keyPassword)
        }.toByteArray()
        val jdk = KeyStore.getInstance("JKS")
        jdk.load(bytes.inputStream(), spec.storePassword)
        val recovered = jdk.getKey("release", spec.keyPassword) as PrivateKey
        assertArrayEquals(key.privateKey.encoded, recovered.encoded)
        assertEquals(key.certificate, jdk.getCertificate("release"))
    }

    @Test
    fun codeinReadsJdkJks() {
        val key = KeystoreGenerator().generateSigningKey(spec)
        val jdk = KeyStore.getInstance("JKS")
        jdk.load(null, null)
        jdk.setKeyEntry("upload", key.privateKey, spec.keyPassword, key.certificates.toTypedArray())
        val out = ByteArrayOutputStream()
        jdk.store(out, spec.storePassword)
        val entries = JksKeyStore.read(out.toByteArray().inputStream(), spec.storePassword, spec.keyPassword)
        val entry = entries.single() as JksKeyStore.Entry.PrivateKeyEntry
        assertEquals("upload", entry.alias)
        assertArrayEquals(key.privateKey.encoded, entry.privateKey.encoded)
    }

    @Test
    fun pkcs12RoundTrip() {
        val file = File.createTempFile("codein", ".p12")
        val p12 = spec.copy(type = KeystoreType.Pkcs12, keyPassword = spec.storePassword)
        val generator = KeystoreGenerator()
        val created = generator.create(p12, file)
        assertEquals(KeystoreType.Pkcs12, generator.detectType(file))
        val loaded = generator.load(file, p12.storePassword, "release", p12.storePassword)
        assertEquals(created.certificate, loaded.certificate)
        file.delete()
    }

    @Test
    fun zipAlignerAlignsStoredEntries() {
        val input = File.createTempFile("unaligned", ".zip")
        ZipOutputStream(input.outputStream()).use { zip ->
            listOf("a.txt" to "x", "res/raw/b.bin" to "hello world", "lib/arm64-v8a/libfoo.so" to "ELF....").forEachIndexed { i, (name, content) ->
                val data = content.toByteArray()
                val entry = ZipEntry(name)
                if (i != 0) {
                    entry.method = ZipEntry.STORED
                    entry.size = data.size.toLong()
                    entry.compressedSize = data.size.toLong()
                    entry.crc = CRC32().apply { update(data) }.value
                }
                zip.putNextEntry(entry)
                zip.write(data)
                zip.closeEntry()
            }
        }
        val output = File.createTempFile("aligned", ".zip")
        val aligner = ZipAligner()
        aligner.align(input, output)
        assertTrue(aligner.verify(output).isEmpty())
        RandomAccessFile(output, "r").use { raf ->
            val offsets = LocalHeaderScanner.dataOffsets(raf)
            assertEquals(0L, offsets.getValue("lib/arm64-v8a/libfoo.so") % ZipAligner.PAGE_ALIGNMENT_16K)
        }
        input.delete()
        output.delete()
    }
}
