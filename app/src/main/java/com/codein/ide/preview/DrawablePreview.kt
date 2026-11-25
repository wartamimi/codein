package com.codein.ide.preview

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.codein.ide.core.ui.theme.CodeinTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** True for `res/drawable*` / `res/mipmap*` XML files the drawable preview can draw. */
fun isDrawableXml(relativePath: String?): Boolean =
    relativePath != null && relativePath.endsWith(".xml") && Regex("""(^|/)res/(drawable|mipmap)[^/]*/""").containsMatchIn(relativePath)

/** Image files the explorer opens in an image viewer instead of the text editor. */
fun isImageFile(name: String): Boolean = name.substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

/**
 * Draws a `<vector>` (paths with fill/stroke, groups with transforms) or a `<shape>` (rectangle or
 * oval with solid color, corners and stroke) at several sizes on light and dark backgrounds.
 */
@Composable
fun DrawablePreview(xml: String, projectDir: File?, modifier: Modifier = Modifier) {
    val resources by produceState(ResourceValues(), projectDir) { value = withContext(Dispatchers.IO) { ResourceValues.load(projectDir) } }
    val parsed = remember(xml) { LayoutXml.parse(xml) }
    Column(modifier.background(CodeinTheme.colors.base).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val root = parsed.getOrNull()
        if (root == null) {
            Text("Cannot preview: ${parsed.exceptionOrNull()?.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        if (root.tag != "vector" && root.tag != "shape") {
            Text("Preview supports <vector> and <shape> drawables. This is <${root.tag}>.", style = MaterialTheme.typography.bodySmall, color = CodeinTheme.colors.textMuted)
            return@Column
        }
        Text(
            if (root.tag == "vector") "Vector · ${root["width"] ?: "?"} × ${root["height"] ?: "?"}" else "Shape · ${root["shape"] ?: "rectangle"}",
            style = MaterialTheme.typography.labelMedium,
            color = CodeinTheme.colors.textMuted,
        )
        listOf(Color.White, Color(0xFF1B1B1F)).forEach { bg ->
            Row(
                Modifier.fillMaxWidth().background(bg, MaterialTheme.shapes.medium).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(24, 48, 96).forEach { sizeDp ->
                    Box(Modifier.size(sizeDp.dp)) {
                        Canvas(Modifier.fillMaxSize()) {
                            if (root.tag == "vector") drawVector(root, resources) else drawShape(root, resources)
                        }
                    }
                }
            }
        }
    }
}

/** Shows a PNG/JPG/WebP file scaled to fit, with its pixel size. */
@Composable
fun ImageFilePreview(file: File, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull() }
    }
    Column(modifier.background(CodeinTheme.colors.base).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val b = bitmap
        if (b == null) {
            Text("Loading ${file.name}…", style = MaterialTheme.typography.bodySmall, color = CodeinTheme.colors.textMuted)
        } else {
            Text("${file.name} · ${b.width} × ${b.height} px · ${file.length() / 1024} KB", style = MaterialTheme.typography.labelMedium, color = CodeinTheme.colors.textMuted)
            Image(b, contentDescription = file.name, modifier = Modifier.weight(1f, fill = false).fillMaxWidth())
        }
    }
}

private fun colorOf(value: String?, res: ResourceValues): Color? =
    LayoutXml.color(value, res)?.let { Color(it.toInt()) }

private fun DrawScope.drawVector(root: LayoutNode, res: ResourceValues) {
    val vw = root["viewportWidth"]?.toFloatOrNull() ?: LayoutXml.dimension(root["width"], res) ?: 24f
    val vh = root["viewportHeight"]?.toFloatOrNull() ?: LayoutXml.dimension(root["height"], res) ?: 24f
    val scale = minOf(size.width / vw, size.height / vh)
    withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
        root.children.forEach { drawVectorNode(it, res) }
    }
}

private fun DrawScope.drawVectorNode(node: LayoutNode, res: ResourceValues) {
    when (node.tag) {
        "group" -> {
            val px = node["pivotX"]?.toFloatOrNull() ?: 0f
            val py = node["pivotY"]?.toFloatOrNull() ?: 0f
            val matrix = Matrix().apply {
                translate(node["translateX"]?.toFloatOrNull() ?: 0f, node["translateY"]?.toFloatOrNull() ?: 0f)
                translate(px, py)
                rotateZ(node["rotation"]?.toFloatOrNull() ?: 0f)
                scale(node["scaleX"]?.toFloatOrNull() ?: 1f, node["scaleY"]?.toFloatOrNull() ?: 1f)
                translate(-px, -py)
            }
            withTransform({ transform(matrix) }) { node.children.forEach { drawVectorNode(it, res) } }
        }
        "path" -> {
            val data = node["pathData"] ?: return
            val path: Path = runCatching { PathParser().parsePathString(data).toPath() }.getOrNull() ?: return
            if (node["fillType"] == "evenOdd") path.fillType = PathFillType.EvenOdd
            val alpha = node["fillAlpha"]?.toFloatOrNull() ?: 1f
            colorOf(node["fillColor"], res)?.let { drawPath(path, it.copy(alpha = it.alpha * alpha)) }
            val strokeWidth = node["strokeWidth"]?.toFloatOrNull() ?: 0f
            if (strokeWidth > 0f) {
                colorOf(node["strokeColor"], res)?.let { c ->
                    drawPath(
                        path,
                        c.copy(alpha = c.alpha * (node["strokeAlpha"]?.toFloatOrNull() ?: 1f)),
                        style = Stroke(
                            width = strokeWidth,
                            cap = when (node["strokeLineCap"]) { "round" -> StrokeCap.Round; "square" -> StrokeCap.Square; else -> StrokeCap.Butt },
                            join = when (node["strokeLineJoin"]) { "round" -> StrokeJoin.Round; "bevel" -> StrokeJoin.Bevel; else -> StrokeJoin.Miter },
                        ),
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawShape(root: LayoutNode, res: ResourceValues) {
    val solid = root.children.firstOrNull { it.tag == "solid" }
    val stroke = root.children.firstOrNull { it.tag == "stroke" }
    val corners = root.children.firstOrNull { it.tag == "corners" }
    val gradient = root.children.firstOrNull { it.tag == "gradient" }
    val fill = colorOf(solid?.get("color"), res)
        ?: gradient?.let { g -> colorOf(g["startColor"], res) }
        ?: Color.Transparent
    val radius = (LayoutXml.dimension(corners?.get("radius"), res) ?: 0f) * density
    val strokeWidth = (LayoutXml.dimension(stroke?.get("width"), res) ?: 0f) * density
    val strokeColor = colorOf(stroke?.get("color"), res)
    val oval = root["shape"] == "oval"
    if (oval) {
        drawOval(fill)
        if (strokeWidth > 0 && strokeColor != null) drawOval(strokeColor, style = Stroke(strokeWidth))
    } else {
        val inset = strokeWidth / 2
        drawRoundRect(fill, cornerRadius = CornerRadius(radius))
        if (strokeWidth > 0 && strokeColor != null) {
            drawRoundRect(strokeColor, topLeft = Offset(inset, inset), size = Size(size.width - strokeWidth, size.height - strokeWidth), cornerRadius = CornerRadius(radius), style = Stroke(strokeWidth))
        }
    }
}
