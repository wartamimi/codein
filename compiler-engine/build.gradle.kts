import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.codein.ide.compiler"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

}

kotlin {
    jvmToolchain(17)
}

/**
 * ECJ 3.33 calls `InputStream.readAllBytes()` / `readNBytes(int)` (Java 9+), which Android only
 * has from API 33, so every Java build crashed on Android 12L and older. D8 does not backport
 * these, so the ECJ jar is rewritten to call `com.codein.ide.compiler.ecj.EcjCompat` instead.
 */
@CacheableTask
abstract class PatchEcjTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val original: ConfigurableFileCollection

    @get:OutputFile
    abstract val patched: RegularFileProperty

    @TaskAction
    fun patch() {
        val compat = "com/codein/ide/compiler/ecj/EcjCompat"
        val out = patched.get().asFile.apply { parentFile.mkdirs() }
        var rewrites = 0
        ZipFile(original.singleFile).use { zip ->
            ZipOutputStream(out.outputStream().buffered()).use { zos ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory) continue
                    // Signature files would no longer match the rewritten classes.
                    if (entry.name.startsWith("META-INF/") && (entry.name.endsWith(".SF") || entry.name.endsWith(".RSA") || entry.name.endsWith(".DSA"))) continue
                    var bytes = zip.getInputStream(entry).use { it.readBytes() }
                    if (entry.name.endsWith(".class")) {
                        val reader = ClassReader(bytes)
                        val writer = ClassWriter(reader, 0)
                        var changed = false
                        reader.accept(object : ClassVisitor(Opcodes.ASM9, writer) {
                            override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                                val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                                return object : MethodVisitor(Opcodes.ASM9, mv) {
                                    override fun visitMethodInsn(opcode: Int, owner: String, method: String, desc: String, isInterface: Boolean) {
                                        if (opcode == Opcodes.INVOKEVIRTUAL && owner == "java/io/InputStream" && method == "readAllBytes" && desc == "()[B") {
                                            changed = true
                                            super.visitMethodInsn(Opcodes.INVOKESTATIC, compat, "readAllBytes", "(Ljava/io/InputStream;)[B", false)
                                        } else if (opcode == Opcodes.INVOKEVIRTUAL && owner == "java/io/InputStream" && method == "readNBytes" && desc == "(I)[B") {
                                            changed = true
                                            super.visitMethodInsn(Opcodes.INVOKESTATIC, compat, "readNBytes", "(Ljava/io/InputStream;I)[B", false)
                                        } else {
                                            super.visitMethodInsn(opcode, owner, method, desc, isInterface)
                                        }
                                    }
                                }
                            }
                        }, 0)
                        if (changed) {
                            bytes = writer.toByteArray()
                            rewrites++
                        }
                    }
                    zos.putNextEntry(ZipEntry(entry.name))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
        }
        check(rewrites > 0) { "ECJ patch found nothing to rewrite; check the ECJ version" }
        logger.lifecycle("Patched $rewrites ECJ classes for Android API < 33")
    }
}

val ecjOriginal: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}
val patchedEcj = layout.buildDirectory.file("patched-ecj/ecj-android.jar")
val patchEcj = tasks.register<PatchEcjTask>("patchEcj") {
    original.from(ecjOriginal)
    patched.set(patchedEcj)
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    api(project(":maven-resolver"))
    api(project(":aab-signer"))
    implementation(project(":core-ui"))
    api(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    ecjOriginal(libs.ecj)
    implementation(files(patchedEcj).builtBy(patchEcj))
    implementation(libs.r8)
    implementation(libs.hiddenapibypass)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
}
