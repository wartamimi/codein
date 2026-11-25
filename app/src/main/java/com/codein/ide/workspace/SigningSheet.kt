package com.codein.ide.workspace

import com.codein.ide.core.ui.i18n.tr
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codein.ide.compiler.model.BuildResult
import com.codein.ide.compiler.model.BuildVariant
import com.codein.ide.core.ui.components.BuildButtonState
import com.codein.ide.core.ui.components.BuildRunPill
import com.codein.ide.core.ui.components.CodeinCard
import com.codein.ide.core.ui.components.CountBadge
import com.codein.ide.core.ui.components.DragHandle
import com.codein.ide.core.ui.components.HairlineDivider
import com.codein.ide.core.ui.components.PillChip
import com.codein.ide.core.ui.components.SegmentedTabs
import com.codein.ide.core.ui.components.StudioSectionCard
import com.codein.ide.core.ui.components.formatBytes
import com.codein.ide.core.ui.components.formatDuration
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.core.ui.theme.Motion
import com.codein.ide.core.ui.theme.Sizes
import com.codein.ide.core.ui.theme.Spacing
import com.codein.ide.signer.ui.KeystoreVaultIntent
import com.codein.ide.signer.ui.KeystoreVaultScreen
import com.codein.ide.signer.ui.KeystoreVaultState

private enum class SigningTab(val title: String) { Keys("Debug & Release Keys"), Export("Build APK / Play Store AAB") }

/**
 * Keystore Vault, Signing & Play Store AAB sheet: two tabs, one for managing debug/release keys
 * (generate, import, SHA fingerprints) and one for picking an output and exporting a signed AAB.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SigningSheet(
    vault: KeystoreVaultState,
    onVaultIntent: (KeystoreVaultIntent) -> Unit,
    build: BuildUiState,
    onIntent: (WorkspaceIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(SigningTab.Keys) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CodeinTheme.colors.tier1,
        dragHandle = { DragHandle(Modifier.padding(vertical = Spacing.md)) },
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).navigationBarsPadding()) {
            Text(tr("Signing & release"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = Spacing.lg))
            SegmentedTabs(
                items = SigningTab.entries.toList(),
                selected = tab,
                onSelect = { tab = it },
                icon = { if (it == SigningTab.Keys) CodeinIcons.Key else CodeinIcons.Package },
                label = { it.title },
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            )
            HairlineDivider()
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(Motion.switch()) togetherWith fadeOut(Motion.switch()) },
                label = "signing-tab",
                modifier = Modifier.weight(1f),
            ) { t ->
                when (t) {
                    SigningTab.Keys -> KeystoreVaultScreen(vault, onVaultIntent, Modifier.fillMaxSize())
                    SigningTab.Export -> ExportTab(
                        vault = vault,
                        build = build,
                        onIntent = onIntent,
                        onDismiss = onDismiss,
                        onCreateKey = {
                            tab = SigningTab.Keys
                            onVaultIntent(KeystoreVaultIntent.ShowCreate(true))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportTab(
    vault: KeystoreVaultState,
    build: BuildUiState,
    onIntent: (WorkspaceIntent) -> Unit,
    onDismiss: () -> Unit,
    onCreateKey: () -> Unit,
) {
    val releaseKey = vault.keystores.firstOrNull { it.id == vault.selectedReleaseId }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        StudioSectionCard(
            "Release signing key",
            icon = CodeinIcons.Shield,
            subtitle = releaseKey?.let { "${it.displayName} · ${it.alias} · ${it.type.displayName}" }
                ?: "No release key selected. Release APK and AAB builds need one.",
            accent = if (releaseKey != null) CodeinTheme.colors.success else CodeinTheme.colors.warning,
        ) {
            if (releaseKey != null) {
                Text("SHA-256  ${releaseKey.fingerprints.sha256}", style = com.codein.ide.core.ui.theme.metricsTextStyle(CodeinTheme.editorPreferences.codingFont, 10.5f), color = CodeinTheme.colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                PillChip(tr("Create a release keystore"), icon = CodeinIcons.Plus, selected = true, onClick = onCreateKey)
            }
        }

        Text(tr("Output"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BuildVariant.entries.forEach { v ->
            CodeinCard(Modifier.fillMaxWidth(), selected = build.variant == v, onClick = { onIntent(WorkspaceIntent.SetVariant(v)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (v.producesBundle) CodeinIcons.Package else CodeinIcons.Smartphone, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Sizes.IconLg))
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(v.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            when (v) {
                                BuildVariant.DebugApk -> "Signed with the auto-generated debug key. Installs directly."
                                BuildVariant.ReleaseApk -> "Signed with your release key (v1 + v2 + v3)."
                                BuildVariant.PlayStoreAab -> "Bundletool .aab signed for Play Console upload."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = CodeinTheme.colors.textMuted,
                        )
                    }
                    if (v.isRelease && releaseKey == null) CountBadge("NEEDS KEY", CodeinTheme.colors.warning)
                }
            }
        }

        ExportAabCard(
            enabled = releaseKey != null && !build.running,
            running = build.running && build.variant == BuildVariant.PlayStoreAab,
            build = build,
            onExport = {
                onIntent(WorkspaceIntent.SetVariant(BuildVariant.PlayStoreAab))
                onIntent(WorkspaceIntent.Build)
                onIntent(WorkspaceIntent.ShowDock(DockTab.Build))
                onDismiss()
            },
            onCancel = { onIntent(WorkspaceIntent.CancelBuild) },
        )

        (build.result as? BuildResult.Success)?.let { result ->
            StudioSectionCard(
                "Last output",
                icon = CodeinIcons.CheckCircle,
                subtitle = "${result.output.name} · ${formatBytes(result.output.length())} · ${formatDuration(result.durationMillis)}",
                accent = CodeinTheme.colors.success,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (!result.variant.producesBundle) PillChip(tr("Install"), icon = CodeinIcons.Download, onClick = { onIntent(WorkspaceIntent.InstallOutput) })
                    PillChip(tr("Share"), icon = CodeinIcons.Share, onClick = { onIntent(WorkspaceIntent.ShareOutput) })
                }
            }
        }
    }
}

/** The prominent "Export Signed .AAB" action card. */
@Composable
private fun ExportAabCard(enabled: Boolean, running: Boolean, build: BuildUiState, onExport: () -> Unit, onCancel: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.22f), CodeinTheme.colors.tier2)))
            .border(1.dp, accent.copy(alpha = 0.5f), shape)
            .clickable(enabled = enabled, onClick = onExport)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) { Icon(CodeinIcons.Export, null, tint = accent, modifier = Modifier.size(Sizes.IconLg)) }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(tr("Export Signed .AAB"), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (enabled || running) "Maven → AAPT2 → ECJ → D8 → Bundletool, signed with your release key."
                    else "Select a release key in Debug & Release Keys first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = Sizes.MinTouch), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            BuildRunPill(
                state = if (running) buildButtonState(build) else BuildButtonState.Idle,
                onBuild = { if (enabled) onExport() },
                onCancel = onCancel,
            )
        }
    }
}
