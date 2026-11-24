package com.codein.ide.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.codein.ide.ai.ui.AiProviderSettings
import com.codein.ide.ai.ui.AiSettingsViewModel
import com.codein.ide.compiler.kotlin.KotlinToolchainInstaller
import com.codein.ide.compiler.sdk.BuildToolchain
import com.codein.ide.compiler.sdk.ToolchainStatus
import com.codein.ide.core.ui.components.CodeinCard
import com.codein.ide.core.ui.components.PillChip
import com.codein.ide.core.ui.components.ToolbarIconButton
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.prefs.EditorPreferences
import com.codein.ide.core.ui.prefs.EditorPreferencesRepository
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.editor.settings.EditorPreferencesSheet
import com.codein.ide.editor.settings.PreferenceChange
import com.codein.ide.signer.ui.KeystoreVaultScreen
import com.codein.ide.signer.ui.KeystoreVaultViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val toolchain: BuildToolchain,
    private val kotlinInstaller: KotlinToolchainInstaller,
    private val preferencesRepository: EditorPreferencesRepository,
) : ViewModel() {

    private val _status = MutableStateFlow(ToolchainStatus(null, null, null))
    val status: StateFlow<ToolchainStatus> = _status.asStateFlow()

    private val _kotlinJars = MutableStateFlow<List<String>>(emptyList())
    val kotlinJars: StateFlow<List<String>> = _kotlinJars.asStateFlow()

    val preferences: StateFlow<EditorPreferences> = preferencesRepository.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, EditorPreferences())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Non-null while the Kotlin toolchain download runs: label and 0..1 progress. */
    private val _kotlinDownload = MutableStateFlow<Pair<String, Float>?>(null)
    val kotlinDownload: StateFlow<Pair<String, Float>?> = _kotlinDownload.asStateFlow()
    private var downloadJob: Job? = null

    init {
        refresh()
    }

    val nativeLibraryDir: String get() = toolchain.aapt2Binary()?.parent ?: "nativeLibraryDir"

    fun importAndroidJar(uri: Uri) = viewModelScope.launch {
        toolchain.importAndroidJar(uri, BuildToolchain.DEFAULT_COMPILE_SDK).fold(
            onSuccess = { _messages.emit("Imported android.jar (${it.length() / 1024} KB)") },
            onFailure = { _messages.emit("Import failed: ${it.message}") },
        )
        refresh()
    }

    fun importKotlinJars(uris: List<Uri>, names: List<String>) = viewModelScope.launch {
        var ok = 0
        uris.forEachIndexed { i, uri ->
            toolchain.importKotlinCompilerJar(uri, names.getOrElse(i) { "kotlin-$i.jar" }).onSuccess { ok++ }
        }
        _messages.emit("Imported $ok of ${uris.size} Kotlin compiler jar(s)")
        refresh()
    }

    fun downloadKotlinCompiler(includeCompose: Boolean) {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            _kotlinDownload.value = "Connecting to Maven Central" to 0f
            val result = kotlinInstaller.install(includeCompose) { p ->
                _kotlinDownload.value = "${p.artifact} (${p.index}/${p.total})" to p.fraction
            }
            _kotlinDownload.value = null
            result.fold(
                onSuccess = { _messages.emit("Kotlin compiler ${KotlinToolchainInstaller.KOTLIN_VERSION} installed (${it.size} jars)") },
                onFailure = { _messages.emit("Download failed: ${it.message}") },
            )
            refresh()
        }
    }

    fun cancelKotlinDownload() {
        downloadJob?.cancel()
        _kotlinDownload.value = null
    }

    fun clearKotlinCompiler() = viewModelScope.launch {
        toolchain.kotlinCompilerDir.deleteRecursively()
        _messages.emit("Removed the Kotlin compiler")
        refresh()
    }

    fun onPreference(change: PreferenceChange) = viewModelScope.launch {
        when (change) {
            is PreferenceChange.Font -> preferencesRepository.setCodingFont(change.font)
            is PreferenceChange.FontSize -> preferencesRepository.setFontSize(change.sp)
            is PreferenceChange.LineHeight -> preferencesRepository.setLineHeight(change.multiplier)
            is PreferenceChange.Ligatures -> preferencesRepository.setLigatures(change.enabled)
            is PreferenceChange.TabSize -> preferencesRepository.setTabSize(change.size)
            is PreferenceChange.SoftWrap -> preferencesRepository.setSoftWrap(change.enabled)
            is PreferenceChange.Scheme -> preferencesRepository.setEditorScheme(change.id)
            is PreferenceChange.DynamicColor -> preferencesRepository.setDynamicColor(change.enabled)
            is PreferenceChange.ThemeMode -> preferencesRepository.setThemeMode(change.mode)
            is PreferenceChange.AutoSave -> preferencesRepository.setAutoSave(change.enabled)
            is PreferenceChange.IndentGuides -> preferencesRepository.setIndentGuides(change.enabled)
            is PreferenceChange.Haptics -> preferencesRepository.setHapticExtraKeys(change.enabled)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val (status, jars) = withContext(Dispatchers.IO) { toolchain.status() to listKotlinJars() }
            _status.value = status
            _kotlinJars.value = jars
        }
    }

    private fun listKotlinJars(): List<String> =
        toolchain.kotlinCompilerDir.listFiles().orEmpty().filter { it.extension == "jar" }.map { it.name }.sorted()
}

private enum class SettingsTab(val title: String, val icon: ImageVector) {
    Toolchain("Toolchain", CodeinIcons.Hammer),
    Ai("AI providers", CodeinIcons.Sparkle),
    Signing("Signing", CodeinIcons.Key),
    Editor("Editor", CodeinIcons.Palette),
}

/** Settings: toolchain imports (SAF), AI providers, keystore vault and editor appearance. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    aiSettingsViewModel: AiSettingsViewModel = hiltViewModel(),
    vaultViewModel: KeystoreVaultViewModel = hiltViewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(SettingsTab.Toolchain) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { vaultViewModel.messages.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolbarIconButton(CodeinIcons.ArrowLeft, "Back", onClick = onBack)
                Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsTab.entries.forEach { t -> PillChip(t.title, icon = t.icon, selected = t == tab, onClick = { tab = t }) }
            }
            Spacer(Modifier.size(8.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                when (tab) {
                    SettingsTab.Toolchain -> ToolchainSection(viewModel)
                    SettingsTab.Ai -> {
                        val ai by aiSettingsViewModel.settings.collectAsStateWithLifecycle()
                        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            AiProviderSettings(
                                settings = ai,
                                onSelectProvider = { aiSettingsViewModel.selectProvider(it) },
                                onSave = { p, key, model, url -> aiSettingsViewModel.save(p, key, model, url) },
                                onClearKey = { aiSettingsViewModel.clearKey(it) },
                                onMaxTokens = { aiSettingsViewModel.setMaxTokens(it) },
                            )
                        }
                    }
                    SettingsTab.Signing -> {
                        val vault by vaultViewModel.state.collectAsStateWithLifecycle()
                        KeystoreVaultScreen(vault, vaultViewModel::onIntent, Modifier.widthIn(max = 840.dp).fillMaxSize())
                    }
                    SettingsTab.Editor -> EditorSection(viewModel)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
private fun ToolchainSection(viewModel: SettingsViewModel) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val jars by viewModel.kotlinJars.collectAsStateWithLifecycle()
    val download by viewModel.kotlinDownload.collectAsStateWithLifecycle()
    var withCompose by rememberSaveable { mutableStateOf(true) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val androidJarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importAndroidJar) }
    val kotlinPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val names = uris.map { uri ->
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: (uri.lastPathSegment?.substringAfterLast('/') ?: "kotlin.jar")
            }
            viewModel.importKotlinJars(uris, names)
        }
    }
    LaunchedEffect(Unit) { viewModel.refresh() }
    Column(
        Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ToolCard(
            title = "AAPT2",
            ok = status.aapt2 != null,
            detail = status.aapt2?.absolutePath
                ?: "This build of Codein shipped without aapt2 for this CPU (it is bundled for arm64-v8a and x86_64). Android only lets apps execute binaries from ${viewModel.nativeLibraryDir}, so it cannot be added at runtime.",
        )
        ToolCard(
            title = "android.jar (platform stubs)",
            ok = status.androidJar != null,
            detail = status.androidJar?.let { "${it.absolutePath} · ${it.length() / 1024} KB" }
                ?: "Bundled with Codein and extracted on first use. You can also import another platforms/android-<api>/android.jar.",
        ) {
            Button(onClick = { androidJarPicker.launch(arrayOf("application/java-archive", "application/zip", "application/octet-stream")) }) {
                Text(if (status.androidJar != null) "Replace android.jar" else "Import android.jar")
            }
        }
        ToolCard(
            title = "Kotlin compiler",
            ok = jars.any { it.startsWith("kotlin-compiler") },
            detail = if (jars.isEmpty()) {
                "Needed only for Kotlin projects. Codein downloads kotlin-compiler-embeddable ${KotlinToolchainInstaller.KOTLIN_VERSION} and its runtime jars from Maven Central (about 70 MB, checksums verified) and converts them for ART on the first Kotlin build. Running kotlinc on Android is experimental and needs plenty of free RAM."
            } else jars.joinToString("\n"),
        ) {
            val progress = download
            if (progress != null) {
                Text(progress.first, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.size(4.dp))
                LinearProgressIndicator(progress = { progress.second }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = { viewModel.cancelKotlinDownload() }) { Text("Cancel") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Include Jetpack Compose compiler plugin", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = withCompose, onCheckedChange = { withCompose = it })
                }
                Spacer(Modifier.size(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.downloadKotlinCompiler(withCompose) }) {
                        Text(if (jars.isEmpty()) "Download Kotlin compiler" else "Download missing jars")
                    }
                    OutlinedButton(onClick = { kotlinPicker.launch(arrayOf("application/java-archive", "application/zip", "application/octet-stream")) }) { Text("Import jars") }
                }
                if (jars.isNotEmpty()) {
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(onClick = { viewModel.clearKotlinCompiler() }) { Text("Remove") }
                }
            }
        }
        ToolCard(title = "Java compiler (ECJ), D8, apksig, bundletool", ok = true, detail = "Bundled with Codein and always available.")
        Text(
            if (status.missing.isEmpty()) "Toolchain ready." else "Missing: ${status.missing.joinToString()}",
            style = MaterialTheme.typography.labelLarge,
            color = if (status.missing.isEmpty()) CodeinTheme.colors.success else CodeinTheme.colors.warning,
        )
    }
}

@Composable
private fun ToolCard(title: String, ok: Boolean, detail: String, actions: @Composable () -> Unit = {}) {
    CodeinCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (ok) CodeinIcons.CheckCircle else CodeinIcons.AlertTriangle,
                null,
                tint = if (ok) CodeinTheme.colors.success else CodeinTheme.colors.warning,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.size(6.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = CodeinTheme.colors.textMuted)
        Spacer(Modifier.size(8.dp))
        actions()
    }
}

@Composable
private fun EditorSection(viewModel: SettingsViewModel) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CodeinCard(Modifier.fillMaxWidth()) {
            Text("${prefs.codingFont.displayName} · ${prefs.fontSizeSp.toInt()} sp · ${prefs.editorScheme.displayName}", style = MaterialTheme.typography.titleSmall)
            Text("Tab ${prefs.tabSize} · ${if (prefs.softWrap) "wrap" else "no wrap"} · ${if (prefs.ligatures) "ligatures" else "no ligatures"} · theme ${prefs.themeMode.name}", style = MaterialTheme.typography.bodySmall, color = CodeinTheme.colors.textMuted)
            Spacer(Modifier.size(8.dp))
            Button(onClick = { open = true }) { Text("Customize editor") }
        }
        Text("Fonts: JetBrains Mono, Fira Code, Cascadia Code, Source Code Pro and Plus Jakarta Sans are bundled under the SIL Open Font License 1.1.", style = MaterialTheme.typography.bodySmall, color = CodeinTheme.colors.textMuted)
    }
    if (open) EditorPreferencesSheet(prefs, onChange = { viewModel.onPreference(it) }, onDismiss = { open = false })
}

