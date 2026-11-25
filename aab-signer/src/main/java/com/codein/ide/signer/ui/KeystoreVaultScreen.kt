package com.codein.ide.signer.ui

import com.codein.ide.core.ui.i18n.tr
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import com.codein.ide.core.ui.theme.metricsTextStyle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codein.ide.core.ui.components.CodeinCard
import com.codein.ide.core.ui.components.CountBadge
import com.codein.ide.core.ui.components.PanelHeader
import com.codein.ide.core.ui.components.PillChip
import com.codein.ide.core.ui.components.ToolbarIconButton
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.core.ui.theme.codeTextStyle
import com.codein.ide.signer.keystore.KeystoreType
import com.codein.ide.signer.keystore.VaultKeystore
import kotlin.math.roundToInt

/** Keystore Vault: list, generate, import and inspect signing keys. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeystoreVaultScreen(
    state: KeystoreVaultState,
    onIntent: (KeystoreVaultIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        PanelHeader(tr("Keystore Vault"), icon = CodeinIcons.Key) {
            ToolbarIconButton(CodeinIcons.Download, tr("Import keystore"), size = 32.dp) { onIntent(KeystoreVaultIntent.ShowImport(true)) }
            ToolbarIconButton(CodeinIcons.Plus, tr("Create keystore"), size = 32.dp) { onIntent(KeystoreVaultIntent.ShowCreate(true)) }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.keystores, key = { it.id }) { ks ->
                KeystoreCard(
                    keystore = ks,
                    selectedForRelease = ks.id == state.selectedReleaseId,
                    onSelect = { onIntent(KeystoreVaultIntent.SelectRelease(ks.id)) },
                    onDelete = { onIntent(KeystoreVaultIntent.Delete(ks.id)) },
                    onRegenerate = { onIntent(KeystoreVaultIntent.RegenerateDebug) },
                )
            }
        }
    }

    if (state.showCreate) {
        ModalBottomSheet(
            onDismissRequest = { onIntent(KeystoreVaultIntent.ShowCreate(false)) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            CreateKeystoreSheet(state, onIntent)
        }
    }
    if (state.showImport) {
        ModalBottomSheet(
            onDismissRequest = { onIntent(KeystoreVaultIntent.ShowImport(false)) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            ImportKeystoreSheet(state, onIntent)
        }
    }
}

@Composable
private fun KeystoreCard(
    keystore: VaultKeystore,
    selectedForRelease: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
) {
    val context = LocalContext.current
    CodeinCard(selected = selectedForRelease) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (keystore.isDebug) CodeinIcons.Bug else CodeinIcons.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(keystore.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("${keystore.alias} · ${keystore.type.displayName} · valid until ${keystore.validUntil}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (keystore.isDebug) {
                CountBadge("DEBUG", CodeinTheme.colors.warning)
            } else if (selectedForRelease) {
                CountBadge("RELEASE", CodeinTheme.colors.success)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(keystore.subject, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        FingerprintRow("SHA-1", keystore.fingerprints.sha1) { copy(context, "SHA-1", keystore.fingerprints.sha1) }
        FingerprintRow("SHA-256", keystore.fingerprints.sha256) { copy(context, "SHA-256", keystore.fingerprints.sha256) }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
            if (keystore.isDebug) {
                TextButton(onClick = onRegenerate) { Text(tr("Regenerate")) }
            } else {
                TextButton(onClick = onDelete) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onSelect, enabled = !selectedForRelease) { Text(if (selectedForRelease) "Release key" else "Use for release") }
            }
        }
    }
}

/** One-tap fingerprint copy chip: shows the algorithm and a shortened hash, flips to "Copied". */
@Composable
private fun FingerprintRow(label: String, value: String, onCopy: () -> Unit) {
    var copied by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(copied) {
        if (copied) { kotlinx.coroutines.delay(1500); copied = false }
    }
    val accent = if (copied) CodeinTheme.colors.success else MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .background(CodeinTheme.colors.base)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClickLabel = "Copy $label") { onCopy(); copied = true }
            .heightIn(min = 44.dp)
            .padding(horizontal = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = accent, modifier = Modifier.width(64.dp))
        Text(value, style = metricsTextStyle(CodeinTheme.editorPreferences.codingFont, 10.5f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Icon(if (copied) CodeinIcons.Check else CodeinIcons.Copy, null, tint = accent, modifier = Modifier.size(16.dp))
    }
}

private fun copy(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

@Composable
private fun CreateKeystoreSheet(state: KeystoreVaultState, onIntent: (KeystoreVaultIntent) -> Unit) {
    val f = state.createForm
    fun update(block: CreateKeystoreForm.() -> CreateKeystoreForm) = onIntent(KeystoreVaultIntent.UpdateCreate(f.block()))
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(tr("New release keystore"), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeystoreType.entries.forEach { t -> PillChip(t.displayName, selected = f.type == t, onClick = { update { copy(type = t) } }) }
        }
        Field("Display name", f.displayName) { update { copy(displayName = it) } }
        Field("Key alias", f.alias, error = if (f.alias.isBlank()) "Required" else null) { update { copy(alias = it) } }
        Field("Keystore password", f.storePassword, password = true, error = if (f.storePassword.length < 6) "At least 6 characters" else null) { update { copy(storePassword = it) } }
        if (f.type == KeystoreType.Jks) Field("Key password", f.keyPassword, password = true, error = if (f.keyPassword.isNotEmpty() && f.keyPassword.length < 6) "At least 6 characters" else null) { update { copy(keyPassword = it) } }
        Text(tr("Key size"), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(2048, 4096).forEach { size -> PillChip("RSA $size", selected = f.keySize == size, onClick = { update { copy(keySize = size) } }) }
        }
        Text("Validity: ${f.validityYears} years", style = MaterialTheme.typography.labelLarge)
        Slider(value = f.validityYears.toFloat(), onValueChange = { update { copy(validityYears = it.roundToInt()) } }, valueRange = 1f..50f, steps = 48)
        Text(tr("Certificate"), style = MaterialTheme.typography.labelLarge)
        Field("First and last name (CN)", f.commonName) { update { copy(commonName = it) } }
        Field("Organizational unit (OU)", f.organizationalUnit) { update { copy(organizationalUnit = it) } }
        Field("Organization (O)", f.organization) { update { copy(organization = it) } }
        Field("City or locality (L)", f.locality) { update { copy(locality = it) } }
        Field("State or province (ST)", f.state) { update { copy(state = it) } }
        Field("Country code (C)", f.country, error = if (f.country.isNotEmpty() && !f.country.matches(Regex("[A-Za-z]{2}"))) "Two letters, e.g. ID" else null) { update { copy(country = it.take(2)) } }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            OutlinedButton(onClick = { onIntent(KeystoreVaultIntent.ShowCreate(false)) }) { Text(tr("Cancel")) }
            Button(onClick = { onIntent(KeystoreVaultIntent.SubmitCreate) }, enabled = !state.working) { Text(tr("Generate")) }
        }
    }
}

@Composable
private fun ImportKeystoreSheet(state: KeystoreVaultState, onIntent: (KeystoreVaultIntent) -> Unit) {
    val f = state.importForm
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "keystore"
            onIntent(KeystoreVaultIntent.UpdateImport(f.copy(uri = uri, fileName = name, displayName = f.displayName.ifBlank { name })))
        }
    }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(tr("Import keystore"), style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Icon(CodeinIcons.FolderOpen, null, Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (f.uri == null) "Choose .jks / .p12 / .keystore" else f.fileName)
        }
        Field("Display name", f.displayName) { onIntent(KeystoreVaultIntent.UpdateImport(f.copy(displayName = it))) }
        Field("Keystore password", f.storePassword, password = true) { onIntent(KeystoreVaultIntent.UpdateImport(f.copy(storePassword = it))) }
        Field("Key alias", f.alias) { onIntent(KeystoreVaultIntent.UpdateImport(f.copy(alias = it))) }
        Field("Key password (blank = keystore password)", f.keyPassword, password = true) { onIntent(KeystoreVaultIntent.UpdateImport(f.copy(keyPassword = it))) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            OutlinedButton(onClick = { onIntent(KeystoreVaultIntent.ShowImport(false)) }) { Text(tr("Cancel")) }
            Button(onClick = { onIntent(KeystoreVaultIntent.SubmitImport) }, enabled = !state.working) { Text(tr("Import")) }
        }
    }
}

@Composable
private fun Field(label: String, value: String, password: Boolean = false, error: String? = null, onChange: (String) -> Unit) {
    // Errors only show once the user has typed, so a fresh form is not a wall of red.
    val showError = error != null && value.isNotEmpty()
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = showError,
        supportingText = if (showError) ({ Text(error.orEmpty()) }) else null,
        shape = RoundedCornerShape(10.dp),
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = if (password) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth(),
    )
}
