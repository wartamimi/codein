package com.codein.ide.signer.apk

import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.codein.ide.signer.keystore.SigningKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class SigningSchemes(val v1: Boolean = true, val v2: Boolean = true, val v3: Boolean = true)

data class VerificationReport(
    val verified: Boolean,
    val v1: Boolean,
    val v2: Boolean,
    val v3: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
)

/** Signs APKs (v1/v2/v3) and App Bundles (JAR signature) with Google's `apksig` library. */
@Singleton
class ApkSignerService @Inject constructor() {

    private val aligner = ZipAligner()

    /** zipaligns [unsignedApk] then signs it into [outputApk]. */
    suspend fun alignAndSign(
        unsignedApk: File,
        outputApk: File,
        key: SigningKey,
        minSdk: Int,
        schemes: SigningSchemes = SigningSchemes(),
    ): VerificationReport = withContext(Dispatchers.Default) {
        val aligned = File(outputApk.parentFile, outputApk.nameWithoutExtension + "-aligned.apk")
        aligner.align(unsignedApk, aligned)
        try {
            sign(aligned, outputApk, key, minSdk, schemes)
        } finally {
            aligned.delete()
        }
        verify(outputApk, minSdk)
    }

    fun sign(input: File, output: File, key: SigningKey, minSdk: Int, schemes: SigningSchemes) {
        val signerConfig = ApkSigner.SignerConfig.Builder(key.alias.uppercase().take(8).ifBlank { "CERT" }, key.privateKey, key.certificates).build()
        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(input)
            .setOutputApk(output)
            .setMinSdkVersion(minSdk)
            .setV1SigningEnabled(schemes.v1)
            .setV2SigningEnabled(schemes.v2)
            .setV3SigningEnabled(schemes.v3)
            .setCreatedBy("Codein IDE")
            .build()
            .sign()
    }

    /**
     * Signs an Android App Bundle. Play expects a JAR (v1-style) signature on `.aab` files, so only
     * the v1 scheme is enabled; APK Signature Scheme v2+ do not apply to bundles.
     */
    suspend fun signBundle(inputAab: File, outputAab: File, key: SigningKey, minSdk: Int) = withContext(Dispatchers.Default) {
        sign(inputAab, outputAab, key, minSdk, SigningSchemes(v1 = true, v2 = false, v3 = false))
    }

    fun verify(apk: File, minSdk: Int): VerificationReport {
        val result = ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(minSdk).build().verify()
        return VerificationReport(
            verified = result.isVerified,
            v1 = result.isVerifiedUsingV1Scheme,
            v2 = result.isVerifiedUsingV2Scheme,
            v3 = result.isVerifiedUsingV3Scheme,
            errors = result.errors.map { it.toString() },
            warnings = result.warnings.map { it.toString() },
        )
    }
}
