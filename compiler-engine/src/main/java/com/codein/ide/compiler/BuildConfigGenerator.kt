package com.codein.ide.compiler

import java.io.File

/** Generates the `BuildConfig.java` class that AGP normally produces. */
object BuildConfigGenerator {

    fun generate(
        outputDir: File,
        packageName: String,
        applicationId: String,
        buildType: String,
        debug: Boolean,
        versionCode: Int,
        versionName: String,
    ): File {
        val dir = File(outputDir, packageName.replace('.', '/')).apply { mkdirs() }
        val file = File(dir, "BuildConfig.java")
        file.writeText(
            """
            |/**
            | * Automatically generated file. DO NOT MODIFY
            | */
            |package $packageName;
            |
            |public final class BuildConfig {
            |  public static final boolean DEBUG = $debug;
            |  public static final String APPLICATION_ID = "${escape(applicationId)}";
            |  public static final String BUILD_TYPE = "${escape(buildType)}";
            |  public static final int VERSION_CODE = $versionCode;
            |  public static final String VERSION_NAME = "${escape(versionName)}";
            |}
            |""".trimMargin(),
        )
        return file
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
