package com.codein.ide.vcs.git

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.codein.ide.core.ui.di.SECRETS_PREFS
import com.codein.ide.core.ui.prefs.SecureValueCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Persists the Git identity and an encrypted HTTPS personal access token. */
@Singleton
class GitCredentialsRepository @Inject constructor(
    @Named(SECRETS_PREFS) private val store: DataStore<Preferences>,
    private val cipher: SecureValueCipher,
) {
    val credentials: Flow<GitCredentials> = store.data.map { prefs ->
        GitCredentials(
            username = prefs[USERNAME].orEmpty(),
            token = prefs[TOKEN]?.let { cipher.decrypt(it) }.orEmpty(),
            authorName = prefs[AUTHOR_NAME].orEmpty(),
            authorEmail = prefs[AUTHOR_EMAIL].orEmpty(),
        )
    }

    suspend fun current(): GitCredentials = credentials.first()

    suspend fun save(username: String, token: String?, authorName: String, authorEmail: String) {
        store.edit { prefs ->
            prefs[USERNAME] = username.trim()
            prefs[AUTHOR_NAME] = authorName.trim()
            prefs[AUTHOR_EMAIL] = authorEmail.trim()
            if (token != null) {
                if (token.isBlank()) prefs.remove(TOKEN) else prefs[TOKEN] = cipher.encrypt(token.trim())
            }
        }
    }

    private companion object {
        val USERNAME = stringPreferencesKey("git_username")
        val TOKEN = stringPreferencesKey("git_token")
        val AUTHOR_NAME = stringPreferencesKey("git_author_name")
        val AUTHOR_EMAIL = stringPreferencesKey("git_author_email")
    }
}
