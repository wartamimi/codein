package com.codein.ide.home

import com.codein.ide.core.ui.theme.CodeinShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.codein.ide.core.ui.i18n.I18n
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.theme.CodeinTheme
import com.codein.ide.core.ui.theme.Motion

private data class TourPage(val icon: ImageVector, val title: String, val titleId: String, val body: String, val bodyId: String)

private val TourPages = listOf(
    TourPage(
        CodeinIcons.FolderPlus,
        "Start a project", "Mulai proyek",
        "Pick a template, import a ZIP, clone a Git repository, or describe an app and let Codein AI write the starting code.",
        "Pilih templat, impor ZIP, clone repositori Git, atau jelaskan aplikasinya dan biarkan Codein AI menulis kode awalnya.",
    ),
    TourPage(
        CodeinIcons.Code,
        "Write code anywhere", "Menulis kode di mana saja",
        "The key bar above the keyboard has Tab, arrows and symbols. Open the command palette from the toolbar to find any file or action. Select text to ask the AI to explain, fix or refactor it.",
        "Bilah tombol di atas keyboard berisi Tab, panah dan simbol. Buka palet perintah dari toolbar untuk mencari file atau aksi apa pun. Pilih teks untuk meminta AI menjelaskan, memperbaiki atau merapikannya.",
    ),
    TourPage(
        CodeinIcons.Hammer,
        "Build and run on this phone", "Build dan jalankan di ponsel ini",
        "Press Run to compile, sign and install the APK. Errors are listed under Problems; tap one to jump to the line or let the AI fix it. History shows past builds and APK size.",
        "Tekan Run untuk mengompilasi, menandatangani dan memasang APK. Error tampil di Problems; ketuk untuk lompat ke barisnya atau biarkan AI memperbaikinya. History menampilkan build sebelumnya dan ukuran APK.",
    ),
    TourPage(
        CodeinIcons.Sparkle,
        "Codein AI", "Codein AI",
        "Add your own API key in Settings. The assistant sees the open file, proposes changes as a diff you can apply hunk by hunk, and every change can be undone.",
        "Masukkan API key Anda sendiri di Pengaturan. Asisten melihat file yang terbuka, mengusulkan perubahan berupa diff yang bisa diterapkan per bagian, dan setiap perubahan bisa dibatalkan.",
    ),
    TourPage(
        CodeinIcons.GitBranch,
        "Git, history and settings", "Git, riwayat dan pengaturan",
        "Commit, push and pull from the Git panel. Local history keeps the last 30 versions of every file. Settings has the language, one-handed mode and this tour.",
        "Commit, push dan pull dari panel Git. Riwayat lokal menyimpan 30 versi terakhir setiap file. Pengaturan berisi bahasa, mode satu tangan dan tur ini.",
    ),
)

/** Five short cards shown once on the first launch, and again from Settings > General. */
@Composable
fun FirstRunTour(onFinish: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val id = I18n.indonesian
    val last = page == TourPages.lastIndex
    Dialog(onDismissRequest = onFinish, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(16.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .clip(CodeinShape.Surface)
                .background(CodeinTheme.colors.tier1)
                .padding(24.dp),
        ) {
            AnimatedContent(page, transitionSpec = { fadeIn(Motion.switch()) togetherWith fadeOut(Motion.switch()) }, label = "tour-page") { index ->
                val p = TourPages[index]
                Column {
                    Box(
                        Modifier.size(56.dp).clip(CodeinShape.Surface).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(p.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(if (id) p.titleId else p.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(if (id) p.bodyId else p.body, style = MaterialTheme.typography.bodyMedium, color = CodeinTheme.colors.textMuted)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.semantics { contentDescription = "${page + 1} / ${TourPages.size}" },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TourPages.indices.forEach { i ->
                    Box(
                        Modifier
                            .height(6.dp)
                            .width(if (i == page) 20.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!last) TextButton(onClick = onFinish) { Text(if (id) "Lewati" else "Skip") }
                if (page > 0) TextButton(onClick = { page-- }) { Text(if (id) "Kembali" else "Back") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { if (last) onFinish() else page++ }) {
                    Text(if (last) (if (id) "Mulai" else "Get started") else (if (id) "Berikutnya" else "Next"))
                }
            }
        }
    }
}
