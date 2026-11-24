package com.codein.ide.core.ui.icons

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codein.ide.core.ui.theme.FileTypeColors
import com.codein.ide.core.ui.theme.GitStatusColors
import com.codein.ide.core.ui.theme.PlusJakartaSans

/** File categories with their brand color and a short badge label. */
enum class FileType(val label: String, val color: Color) {
    Kotlin("KT", FileTypeColors.Kotlin),
    KotlinScript("KTS", FileTypeColors.Gradle),
    Java("J", FileTypeColors.Java),
    Xml("</>", FileTypeColors.Xml),
    Gradle("G", FileTypeColors.Gradle),
    Json("{}", FileTypeColors.Json),
    Archive("AR", FileTypeColors.Archive),
    Image("IMG", FileTypeColors.Image),
    Markdown("MD", FileTypeColors.Markdown),
    Properties("PR", FileTypeColors.Properties),
    Text("TXT", FileTypeColors.Text),
    ;

    companion object {
        fun fromFileName(name: String): FileType {
            val lower = name.lowercase()
            return when {
                lower.endsWith(".gradle.kts") || lower.endsWith(".gradle") -> Gradle
                lower.endsWith(".kts") -> KotlinScript
                lower.endsWith(".kt") -> Kotlin
                lower.endsWith(".java") -> Java
                lower.endsWith(".xml") -> Xml
                lower.endsWith(".json") -> Json
                lower.endsWith(".toml") || lower.endsWith(".properties") || lower.endsWith(".pro") -> Properties
                lower.endsWith(".aar") || lower.endsWith(".jar") || lower.endsWith(".apk") ||
                    lower.endsWith(".aab") || lower.endsWith(".zip") || lower.endsWith(".dex") ||
                    lower.endsWith(".jks") || lower.endsWith(".keystore") || lower.endsWith(".p12") -> Archive
                lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                    lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".svg") -> Image
                lower.endsWith(".md") -> Markdown
                else -> Text
            }
        }
    }
}

/** Git working-tree status shown as a single-letter badge in the explorer. */
enum class GitFileStatus(val letter: String, val color: Color) {
    Modified("M", GitStatusColors.Modified),
    Added("A", GitStatusColors.Added),
    Deleted("D", GitStatusColors.Deleted),
    Untracked("U", GitStatusColors.Untracked),
    Conflicting("C", GitStatusColors.Conflict),
}

/**
 * A color-coded file-type badge: a rounded square tinted with the type's color and a compact label.
 */
@Composable
fun FileTypeBadge(fileName: String, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val type = FileType.fromFileName(fileName)
    val iconVector: ImageVector? = when (type) {
        FileType.Image -> CodeinIcons.Image
        FileType.Archive -> CodeinIcons.Archive
        else -> null
    }
    Box(
        modifier = modifier
            .size(size)
            .background(type.color.copy(alpha = 0.16f), RoundedCornerShape(size * 0.28f))
            .border(1.dp, type.color.copy(alpha = 0.45f), RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        if (iconVector != null) {
            Icon(iconVector, contentDescription = type.name, tint = type.color, modifier = Modifier.size(size * 0.72f))
        } else {
            val fontSize = when (type.label.length) {
                1 -> size.value * 0.55f
                2 -> size.value * 0.42f
                else -> size.value * 0.34f
            }
            Text(
                text = type.label,
                color = type.color,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize.sp,
                lineHeight = fontSize.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/** A folder glyph paired with an animated expand/collapse chevron. */
@Composable
fun FolderChevron(expanded: Boolean, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val rotation by animateFloatAsState(targetValue = if (expanded) 90f else 0f, animationSpec = tween(180), label = "chevron")
    Icon(
        imageVector = CodeinIcons.ChevronRight,
        contentDescription = if (expanded) "Collapse" else "Expand",
        tint = tint,
        modifier = modifier.size(16.dp).rotate(rotation),
    )
}

@Composable
fun FolderIcon(expanded: Boolean, modifier: Modifier = Modifier) {
    Icon(
        imageVector = if (expanded) CodeinIcons.FolderOpen else CodeinIcons.Folder,
        contentDescription = null,
        tint = FileTypeColors.Folder,
        modifier = modifier.size(18.dp),
    )
}

@Composable
fun GitStatusBadge(status: GitFileStatus, modifier: Modifier = Modifier) {
    Text(
        text = status.letter,
        color = status.color,
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = modifier,
    )
}
