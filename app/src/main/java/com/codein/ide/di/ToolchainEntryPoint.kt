package com.codein.ide.di

import com.codein.ide.compiler.BuildOrchestrator
import com.codein.ide.compiler.sdk.BuildToolchain
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Gives non-injected callers (on-device instrumentation tests) access to the build engine. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ToolchainEntryPoint {
    fun buildOrchestrator(): BuildOrchestrator
    fun buildToolchain(): BuildToolchain
}
