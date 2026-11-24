package com.codein.ide.signer.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codein.ide.signer.keystore.DebugKeystoreManager
import com.codein.ide.signer.keystore.DistinguishedName
import com.codein.ide.signer.keystore.KeystoreSpec
import com.codein.ide.signer.keystore.KeystoreType
import com.codein.ide.signer.keystore.KeystoreVault
import com.codein.ide.signer.keystore.VaultKeystore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateKeystoreForm(
    val displayName: String = "Release key",
    val type: KeystoreType = KeystoreType.Jks,
    val alias: String = "release",
    val storePassword: String = "",
    val keyPassword: String = "",
    val keySize: Int = 2048,
    val validityYears: Int = 25,
    val commonName: String = "",
    val organizationalUnit: String = "",
    val organization: String = "",
    val locality: String = "",
    val state: String = "",
    val country: String = "",
)

data class ImportKeystoreForm(
    val uri: Uri? = null,
    val fileName: String = "",
    val displayName: String = "",
    val storePassword: String = "",
    val alias: String = "",
    val keyPassword: String = "",
)

data class KeystoreVaultState(
    val keystores: List<VaultKeystore> = emptyList(),
    val selectedReleaseId: String? = null,
    val createForm: CreateKeystoreForm = CreateKeystoreForm(),
    val importForm: ImportKeystoreForm = ImportKeystoreForm(),
    val showCreate: Boolean = false,
    val showImport: Boolean = false,
    val working: Boolean = false,
    val error: String? = null,
)

sealed interface KeystoreVaultIntent {
    data object Load : KeystoreVaultIntent
    data class ShowCreate(val show: Boolean) : KeystoreVaultIntent
    data class ShowImport(val show: Boolean) : KeystoreVaultIntent
    data class UpdateCreate(val form: CreateKeystoreForm) : KeystoreVaultIntent
    data class UpdateImport(val form: ImportKeystoreForm) : KeystoreVaultIntent
    data object SubmitCreate : KeystoreVaultIntent
    data object SubmitImport : KeystoreVaultIntent
    data class Delete(val id: String) : KeystoreVaultIntent
    data class SelectRelease(val id: String) : KeystoreVaultIntent
    data object RegenerateDebug : KeystoreVaultIntent
}

@HiltViewModel
class KeystoreVaultViewModel @Inject constructor(
    private val vault: KeystoreVault,
    private val debugKeystore: DebugKeystoreManager,
) : ViewModel() {

    private val _state = MutableStateFlow(KeystoreVaultState())
    val state: StateFlow<KeystoreVaultState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch { vault.keystores.collect { list -> _state.update { it.copy(keystores = list) } } }
        viewModelScope.launch { vault.releaseKeystoreId.collect { id -> _state.update { it.copy(selectedReleaseId = id) } } }
        onIntent(KeystoreVaultIntent.Load)
    }

    fun onIntent(intent: KeystoreVaultIntent) {
        when (intent) {
            KeystoreVaultIntent.Load -> viewModelScope.launch {
                runCatching { debugKeystore.getOrCreate() }
                vault.refresh()
            }
            is KeystoreVaultIntent.ShowCreate -> _state.update { it.copy(showCreate = intent.show, error = null) }
            is KeystoreVaultIntent.ShowImport -> _state.update { it.copy(showImport = intent.show, error = null) }
            is KeystoreVaultIntent.UpdateCreate -> _state.update { it.copy(createForm = intent.form) }
            is KeystoreVaultIntent.UpdateImport -> _state.update { it.copy(importForm = intent.form) }
            KeystoreVaultIntent.SubmitCreate -> submitCreate()
            KeystoreVaultIntent.SubmitImport -> submitImport()
            is KeystoreVaultIntent.Delete -> viewModelScope.launch { vault.delete(intent.id) }
            is KeystoreVaultIntent.SelectRelease -> viewModelScope.launch {
                vault.setReleaseKeystore(intent.id)
                _messages.tryEmit("Release builds will be signed with this key")
            }
            KeystoreVaultIntent.RegenerateDebug -> viewModelScope.launch {
                runCatching { debugKeystore.regenerate() }
                    .onSuccess { _messages.tryEmit("Debug keystore regenerated. Reinstall debug builds.") }
                    .onFailure { e -> _messages.tryEmit("Could not regenerate: ${e.message}") }
                vault.refresh()
            }
        }
    }

    private fun submitCreate() {
        val f = _state.value.createForm
        viewModelScope.launch {
            _state.update { it.copy(working = true, error = null) }
            val result = runCatching {
                val spec = KeystoreSpec(
                    type = f.type,
                    alias = f.alias.trim(),
                    storePassword = f.storePassword.toCharArray(),
                    keyPassword = (if (f.type == KeystoreType.Pkcs12) f.storePassword else f.keyPassword).toCharArray(),
                    keySize = f.keySize,
                    validityYears = f.validityYears,
                    dn = DistinguishedName(f.commonName.trim(), f.organizationalUnit.trim(), f.organization.trim(), f.locality.trim(), f.state.trim(), f.country.trim()),
                )
                vault.create(spec, f.displayName.trim())
            }
            result.onSuccess { created ->
                _state.update { it.copy(working = false, showCreate = false, createForm = CreateKeystoreForm()) }
                _messages.tryEmit("Created ${created.displayName} (${created.type.displayName})")
            }.onFailure { e -> _state.update { it.copy(working = false, error = e.message ?: e.javaClass.simpleName) } }
        }
    }

    private fun submitImport() {
        val f = _state.value.importForm
        val uri = f.uri ?: run {
            _state.update { it.copy(error = "Choose a keystore file first") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(working = true, error = null) }
            runCatching { vault.import(uri, f.displayName.ifBlank { f.fileName }, f.storePassword, f.alias.trim(), f.keyPassword.ifEmpty { f.storePassword }) }
                .onSuccess { imported ->
                    _state.update { it.copy(working = false, showImport = false, importForm = ImportKeystoreForm()) }
                    _messages.tryEmit("Imported ${imported.displayName}")
                }
                .onFailure { e -> _state.update { it.copy(working = false, error = e.message ?: e.javaClass.simpleName) } }
        }
    }
}
