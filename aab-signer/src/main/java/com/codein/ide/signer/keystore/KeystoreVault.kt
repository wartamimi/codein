package com.codein.ide.signer.keystore

import android.content.Context
import android.net.Uri
import com.codein.ide.core.ui.prefs.SecureValueCipher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Metadata for a keystore stored in the vault. Passwords are kept encrypted. */
data class VaultKeystore(
    val id: String,
    val displayName: String,
    val type: KeystoreType,
    val alias: String,
    val file: File,
    val subject: String,
    val validUntil: String,
    val fingerprints: CertificateFingerprints,
    val isDebug: Boolean,
    internal val encryptedStorePassword: String,
    internal val encryptedKeyPassword: String,
)

/**
 * The Keystore Vault: generated and imported keystores live in app-private storage
 * (`filesDir/keystores`), metadata in `vault.json`, and passwords are encrypted with an
 * Android Keystore-backed AES-GCM key via [SecureValueCipher].
 */
@Singleton
class KeystoreVault @Inject constructor(
    @ApplicationContext private val context: Context,
    private val generator: KeystoreGenerator,
    private val cipher: SecureValueCipher,
) {
    private val dir: File get() = File(context.filesDir, "keystores").apply { mkdirs() }
    private val indexFile: File get() = File(dir, "vault.json")
    private val mutex = Mutex()
    private val _keystores = MutableStateFlow<List<VaultKeystore>>(emptyList())
    val keystores: StateFlow<List<VaultKeystore>> = _keystores.asStateFlow()

    private val releaseFile: File get() = File(dir, "release.id")
    private val _releaseKeystoreId = MutableStateFlow<String?>(null)

    /** The keystore used to sign Release APK and Play Store AAB builds. */
    val releaseKeystoreId: StateFlow<String?> = _releaseKeystoreId.asStateFlow()

    suspend fun setReleaseKeystore(id: String?) = withContext(Dispatchers.IO) {
        if (id == null) releaseFile.delete() else releaseFile.writeText(id)
        _releaseKeystoreId.value = id
    }

    suspend fun refresh(): List<VaultKeystore> = mutex.withLock {
        withContext(Dispatchers.IO) {
            _releaseKeystoreId.value = releaseFile.takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null }
            readIndex()
        }.also { _keystores.value = it }
    }

    suspend fun create(spec: KeystoreSpec, displayName: String): VaultKeystore = withContext(Dispatchers.Default) {
        val id = UUID.randomUUID().toString()
        val file = File(dir, "$id.${spec.type.extension}")
        val key = generator.create(spec, file)
        val effectiveKeyPassword = if (spec.type == KeystoreType.Pkcs12) spec.storePassword else spec.keyPassword
        addEntry(id, displayName, spec.type, spec.alias, file, key.certificate, String(spec.storePassword), String(effectiveKeyPassword), isDebug = false)
    }

    /** Imports a keystore picked via SAF, verifying passwords and alias before storing it. */
    suspend fun import(uri: Uri, displayName: String, storePassword: String, alias: String, keyPassword: String): VaultKeystore = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val temp = File(dir, "$id.import")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open the selected keystore" }
            temp.outputStream().use { input.copyTo(it) }
        }
        try {
            val type = generator.detectType(temp)
            val key = generator.load(temp, storePassword.toCharArray(), alias, keyPassword.toCharArray())
            val file = File(dir, "$id.${type.extension}")
            check(temp.renameTo(file)) { "Unable to store keystore" }
            addEntry(id, displayName, type, alias, file, key.certificate, storePassword, keyPassword, isDebug = false)
        } finally {
            temp.delete()
        }
    }

    suspend fun registerDebugKeystore(file: File, alias: String, password: String, certificate: X509Certificate): VaultKeystore {
        readIndex().firstOrNull { it.isDebug }?.let { return it }
        return addEntry(DEBUG_ID, "Codein debug keystore", generator.detectType(file), alias, file, certificate, password, password, isDebug = true)
    }

    suspend fun delete(id: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val entries = readIndex()
            entries.firstOrNull { it.id == id && !it.isDebug }?.file?.delete()
            writeIndex(entries.filterNot { it.id == id && !it.isDebug })
            if (_releaseKeystoreId.value == id) {
                releaseFile.delete()
                _releaseKeystoreId.value = null
            }
            _keystores.value = readIndex()
        }
    }

    /** Decrypts passwords and loads the signing key for [id]. */
    suspend fun signingKey(id: String): SigningKey = withContext(Dispatchers.IO) {
        val entry = readIndex().firstOrNull { it.id == id } ?: throw IllegalArgumentException("Keystore $id not found")
        generator.load(entry.file, cipher.decrypt(entry.encryptedStorePassword).toCharArray(), entry.alias, cipher.decrypt(entry.encryptedKeyPassword).toCharArray())
    }

    fun exportFile(id: String): File? = _keystores.value.firstOrNull { it.id == id }?.file

    private suspend fun addEntry(
        id: String,
        displayName: String,
        type: KeystoreType,
        alias: String,
        file: File,
        certificate: X509Certificate,
        storePassword: String,
        keyPassword: String,
        isDebug: Boolean,
    ): VaultKeystore = mutex.withLock {
        withContext(Dispatchers.IO) {
            val entry = VaultKeystore(
                id = id,
                displayName = displayName.ifBlank { alias },
                type = type,
                alias = alias,
                file = file,
                subject = certificate.subjectX500Principal.name,
                validUntil = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(certificate.notAfter),
                fingerprints = CertificateFingerprints.of(certificate),
                isDebug = isDebug,
                encryptedStorePassword = cipher.encrypt(storePassword),
                encryptedKeyPassword = cipher.encrypt(keyPassword),
            )
            val all = readIndex().filterNot { it.id == id } + entry
            writeIndex(all)
            _keystores.value = all
            entry
        }
    }

    private fun readIndex(): List<VaultKeystore> {
        if (!indexFile.isFile) return emptyList()
        val array = runCatching { JSONArray(indexFile.readText()) }.getOrElse { return emptyList() }
        return (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            val file = File(o.getString("file"))
            if (!file.isFile) return@mapNotNull null
            VaultKeystore(
                id = o.getString("id"),
                displayName = o.getString("displayName"),
                type = runCatching { KeystoreType.valueOf(o.getString("type")) }.getOrDefault(KeystoreType.Pkcs12),
                alias = o.getString("alias"),
                file = file,
                subject = o.optString("subject"),
                validUntil = o.optString("validUntil"),
                fingerprints = CertificateFingerprints(o.optString("sha1"), o.optString("sha256"), o.optString("md5")),
                isDebug = o.optBoolean("debug"),
                encryptedStorePassword = o.optString("storePassword"),
                encryptedKeyPassword = o.optString("keyPassword"),
            )
        }
    }

    private fun writeIndex(entries: List<VaultKeystore>) {
        val array = JSONArray()
        entries.forEach { e ->
            array.put(
                JSONObject()
                    .put("id", e.id)
                    .put("displayName", e.displayName)
                    .put("type", e.type.name)
                    .put("alias", e.alias)
                    .put("file", e.file.absolutePath)
                    .put("subject", e.subject)
                    .put("validUntil", e.validUntil)
                    .put("sha1", e.fingerprints.sha1)
                    .put("sha256", e.fingerprints.sha256)
                    .put("md5", e.fingerprints.md5)
                    .put("debug", e.isDebug)
                    .put("storePassword", e.encryptedStorePassword)
                    .put("keyPassword", e.encryptedKeyPassword),
            )
        }
        val temp = File(dir, "vault.json.tmp")
        temp.writeText(array.toString(2))
        if (indexFile.exists()) indexFile.delete()
        temp.renameTo(indexFile)
    }

    companion object {
        const val DEBUG_ID = "debug"
    }
}
