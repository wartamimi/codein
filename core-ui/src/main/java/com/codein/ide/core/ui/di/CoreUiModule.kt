package com.codein.ide.core.ui.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.codein.ide.core.ui.prefs.EditorPreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

private val Context.editorPrefsStore: DataStore<Preferences> by preferencesDataStore(name = "codein_editor_prefs")
private val Context.secretsStore: DataStore<Preferences> by preferencesDataStore(name = "codein_secrets")

const val SECRETS_PREFS = "secrets_prefs"

@Module
@InstallIn(SingletonComponent::class)
object CoreUiModule {

    @Provides
    @Singleton
    @Named(EditorPreferencesRepository.EDITOR_PREFS)
    fun provideEditorPrefsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.editorPrefsStore

    @Provides
    @Singleton
    @Named(SECRETS_PREFS)
    fun provideSecretsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.secretsStore
}
