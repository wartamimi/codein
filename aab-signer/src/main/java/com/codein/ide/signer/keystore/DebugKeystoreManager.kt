package com.codein.ide.signer.keystore

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Codein's built-in `debug.keystore`, mirroring the Android SDK convention:
 * alias `androiddebugkey`, store/key password `android`, `CN=Android Debug,O=Android,C=US`,
 * RSA 2048, 30 years validity. It is generated on first use and registered in the vault.
 */
@Singleton
class DebugKeystoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val generator: KeystoreGenerator,
    private val vault: KeystoreVault,
) {
    private val mutex = Mutex()
    private var cached: SigningKey? = null

    val keystoreFile: File get() = File(File(context.filesDir, "keystores"), "debug.keystore")

    suspend fun getOrCreate(): SigningKey = mutex.withLock {
        cached?.let { return it }
        withContext(Dispatchers.Default) {
            val file = keystoreFile
            val key = if (file.isFile) {
                runCatching { generator.load(file, PASSWORD.toCharArray(), ALIAS, PASSWORD.toCharArray()) }.getOrNull()
            } else null
            val result = key ?: run {
                file.delete()
                generator.create(
                    KeystoreSpec(
                        type = KeystoreType.Pkcs12,
                        alias = ALIAS,
                        storePassword = PASSWORD.toCharArray(),
                        keyPassword = PASSWORD.toCharArray(),
                        keySize = 2048,
                        validityYears = 30,
                        dn = DistinguishedName(commonName = "Android Debug", organization = "Android", country = "US"),
                    ),
                    file,
                )
            }
            vault.registerDebugKeystore(file, ALIAS, PASSWORD, result.certificate)
            cached = result
            result
        }
    }

    suspend fun regenerate(): SigningKey {
        mutex.withLock {
            cached = null
            keystoreFile.delete()
        }
        return getOrCreate()
    }

    companion object {
        const val ALIAS = "androiddebugkey"
        const val PASSWORD = "android"
    }
}
