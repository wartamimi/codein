package com.codein.ide.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Codein's unified line icon set. Every glyph is drawn on a 24x24 grid with a uniform 1.5 stroke,
 * round caps and round joins (Lucide / Material Symbols Rounded inspired). Icons use a black stroke
 * so that `Icon(tint = ...)` recolors them.
 */
object CodeinIcons {

    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name = "Codein.$name", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
            .build()

    private fun filledIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name = "Codein.$name", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .path(fill = SolidColor(Color.Black), pathBuilder = block)
            .build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
        close()
    }

    private fun PathBuilder.line(x1: Float, y1: Float, x2: Float, y2: Float) {
        moveTo(x1, y1)
        lineTo(x2, y2)
    }

    private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, rad: Float) {
        moveTo(l + rad, t)
        lineTo(r - rad, t)
        arcTo(rad, rad, 0f, false, true, r, t + rad)
        lineTo(r, b - rad)
        arcTo(rad, rad, 0f, false, true, r - rad, b)
        lineTo(l + rad, b)
        arcTo(rad, rad, 0f, false, true, l, b - rad)
        lineTo(l, t + rad)
        arcTo(rad, rad, 0f, false, true, l + rad, t)
        close()
    }

    /** Geometric brand mark: two interlocking angle brackets around a diamond core. */
    val BrandMark: ImageVector by lazy {
        icon("BrandMark") {
            moveTo(8f, 5f); lineTo(2.5f, 12f); lineTo(8f, 19f)
            moveTo(16f, 5f); lineTo(21.5f, 12f); lineTo(16f, 19f)
            moveTo(12f, 8.5f); lineTo(15.5f, 12f); lineTo(12f, 15.5f); lineTo(8.5f, 12f); close()
        }
    }

    val Menu by lazy { icon("Menu") { line(4f, 6f, 20f, 6f); line(4f, 12f, 20f, 12f); line(4f, 18f, 14f, 18f) } }
    val Close by lazy { icon("Close") { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) } }
    val Plus by lazy { icon("Plus") { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) } }
    val Minus by lazy { icon("Minus") { line(5f, 12f, 19f, 12f) } }
    val Check by lazy { icon("Check") { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) } }
    val ChevronRight by lazy { icon("ChevronRight") { moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f) } }
    val ChevronDown by lazy { icon("ChevronDown") { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) } }
    val ChevronUp by lazy { icon("ChevronUp") { moveTo(6f, 15f); lineTo(12f, 9f); lineTo(18f, 15f) } }
    val ChevronLeft by lazy { icon("ChevronLeft") { moveTo(15f, 6f); lineTo(9f, 12f); lineTo(15f, 18f) } }
    val ArrowUp by lazy { icon("ArrowUp") { line(12f, 19f, 12f, 5f); moveTo(6f, 11f); lineTo(12f, 5f); lineTo(18f, 11f) } }
    val ArrowDown by lazy { icon("ArrowDown") { line(12f, 5f, 12f, 19f); moveTo(6f, 13f); lineTo(12f, 19f); lineTo(18f, 13f) } }
    val ArrowRight by lazy { icon("ArrowRight") { line(5f, 12f, 19f, 12f); moveTo(13f, 6f); lineTo(19f, 12f); lineTo(13f, 18f) } }
    val ArrowLeft by lazy { icon("ArrowLeft") { line(19f, 12f, 5f, 12f); moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f) } }
    val MoreVertical by lazy {
        icon("MoreVertical") { circle(12f, 5.5f, 0.75f); circle(12f, 12f, 0.75f); circle(12f, 18.5f, 0.75f) }
    }

    val Folder by lazy {
        icon("Folder") {
            moveTo(3f, 7f)
            arcTo(2f, 2f, 0f, false, true, 5f, 5f)
            lineTo(9.5f, 5f); lineTo(11.5f, 7.5f); lineTo(19f, 7.5f)
            arcTo(2f, 2f, 0f, false, true, 21f, 9.5f)
            lineTo(21f, 17f)
            arcTo(2f, 2f, 0f, false, true, 19f, 19f)
            lineTo(5f, 19f)
            arcTo(2f, 2f, 0f, false, true, 3f, 17f)
            close()
        }
    }
    val FolderOpen by lazy {
        icon("FolderOpen") {
            moveTo(3f, 17f); lineTo(3f, 7f)
            arcTo(2f, 2f, 0f, false, true, 5f, 5f)
            lineTo(9.5f, 5f); lineTo(11.5f, 7.5f); lineTo(17f, 7.5f)
            arcTo(2f, 2f, 0f, false, true, 19f, 9.5f); lineTo(19f, 10.5f)
            moveTo(3f, 17f); lineTo(5.6f, 11.6f)
            arcTo(2f, 2f, 0f, false, true, 7.4f, 10.5f)
            lineTo(20.5f, 10.5f)
            arcTo(1f, 1f, 0f, false, true, 21.4f, 11.9f)
            lineTo(18.6f, 17.9f)
            arcTo(2f, 2f, 0f, false, true, 16.8f, 19f)
            lineTo(5f, 19f)
            arcTo(2f, 2f, 0f, false, true, 3f, 17f)
        }
    }
    val File by lazy {
        icon("File") {
            moveTo(14f, 3f); lineTo(7f, 3f)
            arcTo(2f, 2f, 0f, false, false, 5f, 5f); lineTo(5f, 19f)
            arcTo(2f, 2f, 0f, false, false, 7f, 21f); lineTo(17f, 21f)
            arcTo(2f, 2f, 0f, false, false, 19f, 19f); lineTo(19f, 8f); close()
            moveTo(14f, 3f); lineTo(14f, 8f); lineTo(19f, 8f)
        }
    }
    val FilePlus by lazy {
        icon("FilePlus") {
            moveTo(14f, 3f); lineTo(7f, 3f)
            arcTo(2f, 2f, 0f, false, false, 5f, 5f); lineTo(5f, 19f)
            arcTo(2f, 2f, 0f, false, false, 7f, 21f); lineTo(17f, 21f)
            arcTo(2f, 2f, 0f, false, false, 19f, 19f); lineTo(19f, 8f); close()
            moveTo(14f, 3f); lineTo(14f, 8f); lineTo(19f, 8f)
            line(12f, 11.5f, 12f, 17.5f); line(9f, 14.5f, 15f, 14.5f)
        }
    }
    val FolderPlus by lazy {
        icon("FolderPlus") {
            moveTo(3f, 7f)
            arcTo(2f, 2f, 0f, false, true, 5f, 5f)
            lineTo(9.5f, 5f); lineTo(11.5f, 7.5f); lineTo(19f, 7.5f)
            arcTo(2f, 2f, 0f, false, true, 21f, 9.5f); lineTo(21f, 17f)
            arcTo(2f, 2f, 0f, false, true, 19f, 19f); lineTo(5f, 19f)
            arcTo(2f, 2f, 0f, false, true, 3f, 17f); close()
            line(12f, 10.5f, 12f, 16f); line(9.25f, 13.25f, 14.75f, 13.25f)
        }
    }

    val Play by lazy { icon("Play") { moveTo(7f, 4.5f); lineTo(19f, 12f); lineTo(7f, 19.5f); close() } }
    val PlayFilled by lazy { filledIcon("PlayFilled") { moveTo(7f, 4.5f); lineTo(19.5f, 12f); lineTo(7f, 19.5f); close() } }
    val Stop by lazy { icon("Stop") { roundRect(6f, 6f, 18f, 18f, 2f) } }
    val Hammer by lazy {
        icon("Hammer") {
            moveTo(15f, 12f); lineTo(6.5f, 20.5f)
            arcTo(2.12f, 2.12f, 0f, false, true, 3.5f, 17.5f); lineTo(12f, 9f)
            moveTo(17.64f, 15f); lineTo(22f, 10.64f)
            moveTo(20.91f, 11.7f); lineTo(19.66f, 10.45f)
            arcTo(2f, 2f, 0f, false, true, 19.07f, 9.03f); lineTo(19.07f, 7.7f)
            lineTo(16.8f, 5.43f)
            arcTo(6f, 6f, 0f, false, false, 12.56f, 3.67f); lineTo(9f, 3.67f)
            lineTo(9.92f, 4.4f)
            arcTo(6.6f, 6.6f, 0f, false, true, 12.4f, 9.6f); lineTo(12.4f, 11.1f)
            lineTo(14.4f, 13.1f); lineTo(15.7f, 13.1f)
            arcTo(2f, 2f, 0f, false, true, 17.1f, 13.7f); lineTo(18.35f, 14.95f)
        }
    }
    val Search by lazy { icon("Search") { circle(11f, 11f, 6.5f); line(16f, 16f, 20.5f, 20.5f) } }
    val Replace by lazy {
        icon("Replace") {
            moveTo(4f, 9f); lineTo(4f, 6f); arcTo(2f, 2f, 0f, false, true, 6f, 4f); lineTo(15f, 4f)
            moveTo(12f, 1.5f); lineTo(15f, 4f); lineTo(12f, 6.5f)
            moveTo(20f, 15f); lineTo(20f, 18f); arcTo(2f, 2f, 0f, false, true, 18f, 20f); lineTo(9f, 20f)
            moveTo(12f, 17.5f); lineTo(9f, 20f); lineTo(12f, 22.5f)
            roundRect(4f, 12f, 10f, 18f, 1.5f)
            roundRect(14f, 6f, 20f, 12f, 1.5f)
        }
    }
    val Sparkle by lazy {
        icon("Sparkle") {
            moveTo(12f, 3f)
            curveTo(12.6f, 7.8f, 14.2f, 9.4f, 19f, 10f)
            curveTo(14.2f, 10.6f, 12.6f, 12.2f, 12f, 17f)
            curveTo(11.4f, 12.2f, 9.8f, 10.6f, 5f, 10f)
            curveTo(9.8f, 9.4f, 11.4f, 7.8f, 12f, 3f)
            close()
            line(19f, 16f, 19f, 21f); line(16.5f, 18.5f, 21.5f, 18.5f)
            line(5f, 17f, 5f, 20f); line(3.5f, 18.5f, 6.5f, 18.5f)
        }
    }
    val GitBranch by lazy {
        icon("GitBranch") {
            line(6f, 3f, 6f, 15f)
            circle(6f, 18f, 3f)
            circle(18f, 6f, 3f)
            moveTo(18f, 9f)
            arcTo(9f, 9f, 0f, false, true, 9f, 18f)
        }
    }
    val GitCommit by lazy { icon("GitCommit") { circle(12f, 12f, 3.5f); line(3f, 12f, 8.5f, 12f); line(15.5f, 12f, 21f, 12f) } }
    val GitPull by lazy {
        icon("GitPull") {
            line(12f, 3f, 12f, 15f); moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
            moveTo(4f, 17f); lineTo(4f, 19f); arcTo(2f, 2f, 0f, false, false, 6f, 21f); lineTo(18f, 21f)
            arcTo(2f, 2f, 0f, false, false, 20f, 19f); lineTo(20f, 17f)
        }
    }
    val GitPush by lazy {
        icon("GitPush") {
            line(12f, 15f, 12f, 3f); moveTo(7f, 8f); lineTo(12f, 3f); lineTo(17f, 8f)
            moveTo(4f, 17f); lineTo(4f, 19f); arcTo(2f, 2f, 0f, false, false, 6f, 21f); lineTo(18f, 21f)
            arcTo(2f, 2f, 0f, false, false, 20f, 19f); lineTo(20f, 17f)
        }
    }
    val Package by lazy {
        icon("Package") {
            moveTo(12f, 2.75f); lineTo(20.5f, 7.25f); lineTo(20.5f, 16.75f); lineTo(12f, 21.25f)
            lineTo(3.5f, 16.75f); lineTo(3.5f, 7.25f); close()
            moveTo(3.5f, 7.25f); lineTo(12f, 11.75f); lineTo(20.5f, 7.25f)
            line(12f, 11.75f, 12f, 21.25f)
            line(7.75f, 5f, 16.25f, 9.5f)
        }
    }
    val Key by lazy {
        icon("Key") {
            circle(7.5f, 15.5f, 4.5f)
            line(10.7f, 12.3f, 20f, 3f)
            line(16f, 7f, 19f, 10f)
            line(18f, 5f, 21f, 8f)
        }
    }
    val Shield by lazy {
        icon("Shield") {
            moveTo(12f, 21.5f)
            curveTo(7f, 19.5f, 4f, 16f, 4f, 11f)
            lineTo(4f, 5.5f); lineTo(12f, 2.5f); lineTo(20f, 5.5f); lineTo(20f, 11f)
            curveTo(20f, 16f, 17f, 19.5f, 12f, 21.5f); close()
            moveTo(9f, 12f); lineTo(11f, 14f); lineTo(15f, 10f)
        }
    }
    val Terminal by lazy {
        icon("Terminal") {
            roundRect(3f, 4f, 21f, 20f, 2.5f)
            moveTo(7f, 9f); lineTo(10f, 12f); lineTo(7f, 15f)
            line(12.5f, 15f, 17f, 15f)
        }
    }
    val Settings by lazy {
        icon("Settings") {
            circle(12f, 12f, 3f)
            moveTo(12f, 2.5f); lineTo(14f, 4.6f); lineTo(16.9f, 4.1f); lineTo(17.6f, 6.9f); lineTo(20.3f, 8f)
            lineTo(19.6f, 10.8f); lineTo(21.5f, 12.9f); lineTo(19.5f, 15f); lineTo(19.8f, 17.9f); lineTo(17f, 18.4f)
            lineTo(15.7f, 21f); lineTo(13f, 20f); lineTo(10.3f, 21.3f); lineTo(9f, 18.7f); lineTo(6.2f, 18f)
            lineTo(6.4f, 15.1f); lineTo(4.4f, 13f); lineTo(6f, 10.6f); lineTo(5.5f, 7.8f); lineTo(8.2f, 7f)
            lineTo(9.4f, 4.3f); close()
        }
    }
    val Undo by lazy {
        icon("Undo") {
            moveTo(9f, 14f); lineTo(4f, 9f); lineTo(9f, 4f)
            moveTo(4f, 9f); lineTo(14.5f, 9f)
            arcTo(5.5f, 5.5f, 0f, false, true, 14.5f, 20f); lineTo(11f, 20f)
        }
    }
    val Redo by lazy {
        icon("Redo") {
            moveTo(15f, 14f); lineTo(20f, 9f); lineTo(15f, 4f)
            moveTo(20f, 9f); lineTo(9.5f, 9f)
            arcTo(5.5f, 5.5f, 0f, false, false, 9.5f, 20f); lineTo(13f, 20f)
        }
    }
    val Comment by lazy {
        icon("Comment") { line(10f, 4f, 6f, 20f); line(18f, 4f, 14f, 20f) }
    }
    val Code by lazy { icon("Code") { moveTo(8f, 6f); lineTo(2.5f, 12f); lineTo(8f, 18f); moveTo(16f, 6f); lineTo(21.5f, 12f); lineTo(16f, 18f) } }
    val Regex by lazy {
        icon("Regex") {
            line(17f, 3f, 17f, 13f); line(12.7f, 5.5f, 21.3f, 10.5f); line(12.7f, 10.5f, 21.3f, 5.5f)
            roundRect(3f, 15f, 9f, 21f, 1.5f)
        }
    }
    val CaseSensitive by lazy {
        icon("CaseSensitive") {
            moveTo(2.5f, 17f); lineTo(6.5f, 7f); lineTo(10.5f, 17f)
            line(4f, 13.5f, 9f, 13.5f)
            circle(17f, 14f, 3f)
            line(20f, 11f, 20f, 17f)
        }
    }
    val WholeWord by lazy {
        icon("WholeWord") {
            circle(7f, 12f, 2.5f); line(9.5f, 9.5f, 9.5f, 14.5f)
            line(14f, 7f, 14f, 14.5f); circle(16.5f, 12f, 2.5f)
            moveTo(3f, 17.5f); lineTo(3f, 19f); lineTo(21f, 19f); lineTo(21f, 17.5f)
        }
    }
    val AlertCircle by lazy { icon("AlertCircle") { circle(12f, 12f, 9f); line(12f, 7.5f, 12f, 13f); line(12f, 16.5f, 12.01f, 16.5f) } }
    val AlertTriangle by lazy {
        icon("AlertTriangle") {
            moveTo(10.3f, 4f); lineTo(2.5f, 18f)
            arcTo(2f, 2f, 0f, false, false, 4.2f, 21f); lineTo(19.8f, 21f)
            arcTo(2f, 2f, 0f, false, false, 21.5f, 18f); lineTo(13.7f, 4f)
            arcTo(2f, 2f, 0f, false, false, 10.3f, 4f); close()
            line(12f, 9.5f, 12f, 13.5f); line(12f, 17f, 12.01f, 17f)
        }
    }
    val Info by lazy { icon("Info") { circle(12f, 12f, 9f); line(12f, 11f, 12f, 16.5f); line(12f, 7.5f, 12.01f, 7.5f) } }
    val CheckCircle by lazy { icon("CheckCircle") { circle(12f, 12f, 9f); moveTo(8f, 12.5f); lineTo(11f, 15.5f); lineTo(16f, 9f) } }
    val Download by lazy {
        icon("Download") {
            line(12f, 3f, 12f, 15f); moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
            line(4f, 20f, 20f, 20f)
        }
    }
    val Share by lazy {
        icon("Share") {
            circle(18f, 5f, 2.5f); circle(6f, 12f, 2.5f); circle(18f, 19f, 2.5f)
            line(8.2f, 13.3f, 15.8f, 17.7f); line(15.8f, 6.3f, 8.2f, 10.7f)
        }
    }
    val Copy by lazy {
        icon("Copy") {
            roundRect(8f, 8f, 21f, 21f, 2f)
            moveTo(16f, 8f); lineTo(16f, 5f); arcTo(2f, 2f, 0f, false, false, 14f, 3f); lineTo(5f, 3f)
            arcTo(2f, 2f, 0f, false, false, 3f, 5f); lineTo(3f, 14f); arcTo(2f, 2f, 0f, false, false, 5f, 16f); lineTo(8f, 16f)
        }
    }
    val Trash by lazy {
        icon("Trash") {
            line(3.5f, 6f, 20.5f, 6f)
            moveTo(9f, 6f); lineTo(9f, 4f); arcTo(1f, 1f, 0f, false, true, 10f, 3f); lineTo(14f, 3f)
            arcTo(1f, 1f, 0f, false, true, 15f, 4f); lineTo(15f, 6f)
            moveTo(5.5f, 6f); lineTo(6.5f, 19.2f); arcTo(2f, 2f, 0f, false, false, 8.5f, 21f); lineTo(15.5f, 21f)
            arcTo(2f, 2f, 0f, false, false, 17.5f, 19.2f); lineTo(18.5f, 6f)
            line(10f, 10.5f, 10f, 16.5f); line(14f, 10.5f, 14f, 16.5f)
        }
    }
    val Send by lazy {
        icon("Send") {
            moveTo(21.5f, 2.5f); lineTo(10.5f, 13.5f)
            moveTo(21.5f, 2.5f); lineTo(14.5f, 21.5f); lineTo(10.5f, 13.5f); lineTo(2.5f, 9.5f); close()
        }
    }
    val Refresh by lazy {
        icon("Refresh") {
            moveTo(20f, 11f); arcTo(8f, 8f, 0f, false, false, 5.6f, 7.2f)
            moveTo(4f, 3.5f); lineTo(4f, 8f); lineTo(8.5f, 8f)
            moveTo(4f, 13f); arcTo(8f, 8f, 0f, false, false, 18.4f, 16.8f)
            moveTo(20f, 20.5f); lineTo(20f, 16f); lineTo(15.5f, 16f)
        }
    }
    val Type by lazy {
        icon("Type") {
            moveTo(4f, 7f); lineTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 7f)
            line(12f, 4f, 12f, 20f); line(9f, 20f, 15f, 20f)
        }
    }
    val Palette by lazy {
        icon("Palette") {
            moveTo(12f, 21f)
            arcTo(9f, 9f, 0f, true, true, 21f, 12f)
            curveTo(21f, 14.2f, 19.2f, 15f, 17.5f, 15f); lineTo(15.5f, 15f)
            arcTo(1.75f, 1.75f, 0f, false, false, 14.3f, 18f)
            curveTo(15.2f, 19f, 14.5f, 21f, 12f, 21f); close()
            circle(7.5f, 11f, 0.9f); circle(10.5f, 7f, 0.9f); circle(15f, 7.5f, 0.9f)
        }
    }
    val Command by lazy {
        icon("Command") {
            moveTo(9f, 9f); lineTo(9f, 6f); arcTo(3f, 3f, 0f, true, false, 6f, 9f); lineTo(18f, 9f)
            arcTo(3f, 3f, 0f, true, false, 15f, 6f); lineTo(15f, 18f)
            arcTo(3f, 3f, 0f, true, false, 18f, 15f); lineTo(6f, 15f)
            arcTo(3f, 3f, 0f, true, false, 9f, 18f); close()
        }
    }
    val Layers by lazy {
        icon("Layers") {
            moveTo(12f, 3f); lineTo(21.5f, 8f); lineTo(12f, 13f); lineTo(2.5f, 8f); close()
            moveTo(2.5f, 12.5f); lineTo(12f, 17.5f); lineTo(21.5f, 12.5f)
            moveTo(2.5f, 16.5f); lineTo(12f, 21.5f); lineTo(21.5f, 16.5f)
        }
    }
    val Smartphone by lazy { icon("Smartphone") { roundRect(6f, 2.5f, 18f, 21.5f, 2.5f); line(11f, 18.5f, 13f, 18.5f) } }
    val Save by lazy {
        icon("Save") {
            moveTo(5f, 3f); lineTo(16f, 3f); lineTo(21f, 8f); lineTo(21f, 19f)
            arcTo(2f, 2f, 0f, false, true, 19f, 21f); lineTo(5f, 21f)
            arcTo(2f, 2f, 0f, false, true, 3f, 19f); lineTo(3f, 5f)
            arcTo(2f, 2f, 0f, false, true, 5f, 3f); close()
            moveTo(7f, 21f); lineTo(7f, 14f); lineTo(17f, 14f); lineTo(17f, 21f)
            moveTo(7f, 3f); lineTo(7f, 7.5f); lineTo(14f, 7.5f)
        }
    }
    val Diff by lazy {
        icon("Diff") {
            line(12f, 3f, 12f, 10f); line(8.5f, 6.5f, 15.5f, 6.5f)
            line(8.5f, 14.5f, 15.5f, 14.5f)
            line(6f, 20.5f, 18f, 20.5f)
        }
    }
    val Bug by lazy {
        icon("Bug") {
            roundRect(7f, 7f, 17f, 20.5f, 5f)
            line(12f, 11f, 12f, 20.5f)
            line(3f, 12.5f, 7f, 12.5f); line(17f, 12.5f, 21f, 12.5f)
            line(4f, 7f, 7.5f, 9f); line(20f, 7f, 16.5f, 9f)
            line(4f, 18.5f, 7.3f, 16.5f); line(20f, 18.5f, 16.7f, 16.5f)
            moveTo(9f, 7.5f); lineTo(9f, 6f); arcTo(3f, 3f, 0f, false, true, 15f, 6f); lineTo(15f, 7.5f)
        }
    }
    val Filter by lazy { icon("Filter") { moveTo(3f, 4f); lineTo(21f, 4f); lineTo(14f, 12.5f); lineTo(14f, 19f); lineTo(10f, 21f); lineTo(10f, 12.5f); close() } }
    val Lock by lazy {
        icon("Lock") {
            roundRect(4f, 11f, 20f, 21f, 2f)
            moveTo(7.5f, 11f); lineTo(7.5f, 7.5f); arcTo(4.5f, 4.5f, 0f, false, true, 16.5f, 7.5f); lineTo(16.5f, 11f)
        }
    }
    val Unlock by lazy {
        icon("Unlock") {
            roundRect(4f, 11f, 20f, 21f, 2f)
            moveTo(7.5f, 11f); lineTo(7.5f, 7.5f); arcTo(4.5f, 4.5f, 0f, false, true, 16.2f, 6f)
        }
    }
    val Pause by lazy { icon("Pause") { line(9f, 5f, 9f, 19f); line(15f, 5f, 15f, 19f) } }
    val Clock by lazy { icon("Clock") { circle(12f, 12f, 9f); moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15.5f, 14f) } }
    val Image by lazy {
        icon("Image") {
            roundRect(3f, 3f, 21f, 21f, 2.5f)
            circle(9f, 9f, 1.75f)
            moveTo(21f, 15.5f); lineTo(16f, 10.5f); lineTo(5f, 21f)
        }
    }
    val Archive by lazy {
        icon("Archive") {
            roundRect(2.5f, 3.5f, 21.5f, 8f, 1f)
            moveTo(4f, 8f); lineTo(4f, 19f); arcTo(2f, 2f, 0f, false, false, 6f, 21f); lineTo(18f, 21f)
            arcTo(2f, 2f, 0f, false, false, 20f, 19f); lineTo(20f, 8f)
            line(10f, 12f, 14f, 12f)
        }
    }
    val Wrap by lazy {
        icon("Wrap") {
            line(3f, 6f, 21f, 6f); line(3f, 18f, 9f, 18f)
            moveTo(3f, 12f); lineTo(18f, 12f); arcTo(3f, 3f, 0f, false, true, 18f, 18f); lineTo(13f, 18f)
            moveTo(15f, 16f); lineTo(13f, 18f); lineTo(15f, 20f)
        }
    }
    val Keyboard by lazy {
        icon("Keyboard") {
            roundRect(2.5f, 5.5f, 21.5f, 18.5f, 2f)
            line(6f, 9.5f, 6.01f, 9.5f); line(10f, 9.5f, 10.01f, 9.5f); line(14f, 9.5f, 14.01f, 9.5f); line(18f, 9.5f, 18.01f, 9.5f)
            line(8f, 14.5f, 16f, 14.5f)
        }
    }
    val Tab by lazy { icon("Tab") { line(3f, 12f, 17f, 12f); moveTo(13f, 8f); lineTo(17f, 12f); lineTo(13f, 16f); line(21f, 6f, 21f, 18f) } }
    val Wand by lazy {
        icon("Wand") {
            line(3f, 21f, 15f, 9f)
            moveTo(15f, 4f); lineTo(15f, 6f); moveTo(15f, 12f); lineTo(15f, 14f)
            moveTo(10f, 9f); lineTo(12f, 9f); moveTo(18f, 9f); lineTo(20f, 9f)
            line(17.5f, 6.5f, 19f, 5f); line(12.5f, 6.5f, 11f, 5f); line(17.5f, 11.5f, 19f, 13f)
        }
    }
    val History by lazy {
        icon("History") {
            moveTo(3f, 12f); arcTo(9f, 9f, 0f, true, false, 5.6f, 5.6f)
            moveTo(3f, 3.5f); lineTo(3f, 8f); lineTo(7.5f, 8f)
            moveTo(12f, 7.5f); lineTo(12f, 12f); lineTo(15f, 14f)
        }
    }
    val Sidebar by lazy { icon("Sidebar") { roundRect(3f, 4f, 21f, 20f, 2.5f); line(9f, 4f, 9f, 20f) } }
    val PanelBottom by lazy { icon("PanelBottom") { roundRect(3f, 4f, 21f, 20f, 2.5f); line(3f, 14.5f, 21f, 14.5f) } }
    val Rocket by lazy {
        icon("Rocket") {
            moveTo(12f, 15f); lineTo(9f, 12f)
            curveTo(10.5f, 7f, 14.5f, 3.5f, 20.5f, 3.5f)
            curveTo(20.5f, 9.5f, 17f, 13.5f, 12f, 15f); close()
            moveTo(9f, 12f); lineTo(5f, 12f); lineTo(7f, 8.5f); lineTo(11f, 8.5f)
            moveTo(12f, 15f); lineTo(12f, 19f); lineTo(15.5f, 17f); lineTo(15.5f, 13f)
            moveTo(5.5f, 16.5f); curveTo(4f, 17.8f, 3.5f, 20.5f, 3.5f, 20.5f); curveTo(3.5f, 20.5f, 6.2f, 20f, 7.5f, 18.5f)
        }
    }
}
