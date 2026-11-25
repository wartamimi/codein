
# Android replacement for IntelliJ Unsafe, linked by the dexed Kotlin compiler at runtime.
-keep class org.jetbrains.kotlin.com.intellij.util.containers.Unsafe { *; }
# Stand-in for Swing's Icon so the dexed Kotlin compiler verifies on Android 11 and older.
-keep interface javax.swing.Icon { *; }
-keep class com.codein.ide.compiler.ecj.EcjCompat { *; }
