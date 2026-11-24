package com.codein.ide.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Codein's icon set. Every glyph comes from Lucide (ISC license, https://lucide.dev), so they all
 * share one 24x24 grid, one optical size, round caps and round joins. The stroke is drawn at 1.75
 * instead of Lucide's 2 so that icons at the 16 to 20dp sizes used in the chrome read as balanced
 * next to 13 to 14sp text instead of heavy. Paths are drawn black so `Icon(tint = ...)` recolors them.
 */
object CodeinIcons {

    private const val STROKE = 1.75f

    private fun builder(name: String) = ImageVector.Builder(
        name = "Codein.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )

    /** Outline icon from one or more SVG path strings (one per Lucide element). */
    private fun stroke(name: String, vararg paths: String): ImageVector = builder(name).apply {
        paths.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

    /** Solid icon: filled and stroked with the same paint so corners stay as round as the outline set. */
    private fun solid(name: String, vararg paths: String): ImageVector = builder(name).apply {
        paths.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                fill = SolidColor(Color.Black),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

    /** Codein brand mark: angle brackets around a diamond core, on the same grid and stroke. */
    val BrandMark: ImageVector by lazy { stroke("BrandMark", "M8 5 2.5 12 8 19", "M16 5l5.5 7-5.5 7", "M12 8.5 15.5 12 12 15.5 8.5 12Z") }

    /** Filled play triangle for the Build & Run pill (Lucide `play`). */
    val PlayFilled: ImageVector by lazy { solid("PlayFilled", "M6 3 20 12 6 21Z") }

    /** Filled rounded square used as the stop/cancel glyph. */
    val Stop: ImageVector by lazy { solid("Stop", "M7 5h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Z") }

    val Menu: ImageVector by lazy { stroke("Menu", "M4 12L20 12", "M4 6L20 6", "M4 18L20 18") }
    val Close: ImageVector by lazy { stroke("Close", "M18 6 6 18", "m6 6 12 12") }
    val Plus: ImageVector by lazy { stroke("Plus", "M5 12h14", "M12 5v14") }
    val Minus: ImageVector by lazy { stroke("Minus", "M5 12h14") }
    val Check: ImageVector by lazy { stroke("Check", "M20 6 9 17l-5-5") }
    val ChevronRight: ImageVector by lazy { stroke("ChevronRight", "m9 18 6-6-6-6") }
    val ChevronDown: ImageVector by lazy { stroke("ChevronDown", "m6 9 6 6 6-6") }
    val ChevronUp: ImageVector by lazy { stroke("ChevronUp", "m18 15-6-6-6 6") }
    val ChevronLeft: ImageVector by lazy { stroke("ChevronLeft", "m15 18-6-6 6-6") }
    val ArrowUp: ImageVector by lazy { stroke("ArrowUp", "m5 12 7-7 7 7", "M12 19V5") }
    val ArrowDown: ImageVector by lazy { stroke("ArrowDown", "M12 5v14", "m19 12-7 7-7-7") }
    val ArrowRight: ImageVector by lazy { stroke("ArrowRight", "M5 12h14", "m12 5 7 7-7 7") }
    val ArrowLeft: ImageVector by lazy { stroke("ArrowLeft", "m12 19-7-7 7-7", "M19 12H5") }
    val MoreVertical: ImageVector by lazy { stroke("MoreVertical", "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0Z", "M11 5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0Z", "M11 19a1 1 0 1 0 2 0a1 1 0 1 0 -2 0Z") }
    val Folder: ImageVector by lazy { stroke("Folder", "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z") }
    val FolderOpen: ImageVector by lazy { stroke("FolderOpen", "m6 14 1.5-2.9A2 2 0 0 1 9.24 10H20a2 2 0 0 1 1.94 2.5l-1.54 6a2 2 0 0 1-1.95 1.5H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h3.9a2 2 0 0 1 1.69.9l.81 1.2a2 2 0 0 0 1.67.9H18a2 2 0 0 1 2 2v2") }
    val File: ImageVector by lazy { stroke("File", "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z", "M14 2v4a2 2 0 0 0 2 2h4") }
    val FilePlus: ImageVector by lazy { stroke("FilePlus", "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z", "M14 2v4a2 2 0 0 0 2 2h4", "M9 15h6", "M12 18v-6") }
    val FolderPlus: ImageVector by lazy { stroke("FolderPlus", "M12 10v6", "M9 13h6", "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z") }
    val Play: ImageVector by lazy { stroke("Play", "M6 3 L20 12 L6 21 L6 3Z") }
    val Hammer: ImageVector by lazy { stroke("Hammer", "m15 12-8.373 8.373a1 1 0 1 1-3-3L12 9", "m18 15 4-4", "m21.5 11.5-1.914-1.914A2 2 0 0 1 19 8.172V7l-2.26-2.26a6 6 0 0 0-4.202-1.756L9 2.96l.92.82A6.18 6.18 0 0 1 12 8.4V10l2 2h1.172a2 2 0 0 1 1.414.586L18.5 14.5") }
    val Search: ImageVector by lazy { stroke("Search", "M3 11a8 8 0 1 0 16 0a8 8 0 1 0 -16 0Z", "m21 21-4.3-4.3") }
    val Replace: ImageVector by lazy { stroke("Replace", "M14 4a2 2 0 0 1 2-2", "M16 10a2 2 0 0 1-2-2", "M20 2a2 2 0 0 1 2 2", "M22 8a2 2 0 0 1-2 2", "m3 7 3 3 3-3", "M6 10V5a3 3 0 0 1 3-3h1", "M4 14h4a2 2 0 0 1 2 2v4a2 2 0 0 1 -2 2h-4a2 2 0 0 1 -2 -2v-4a2 2 0 0 1 2 -2Z") }
    val Sparkle: ImageVector by lazy { stroke("Sparkle", "M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z", "M20 3v4", "M22 5h-4", "M4 17v2", "M5 18H3") }
    val GitBranch: ImageVector by lazy { stroke("GitBranch", "M6 3L6 15", "M15 6a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z", "M3 18a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z", "M18 9a9 9 0 0 1-9 9") }
    val GitCommit: ImageVector by lazy { stroke("GitCommit", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z", "M3 12L9 12", "M15 12L21 12") }
    val GitPull: ImageVector by lazy { stroke("GitPull", "M12 17V3", "m6 11 6 6 6-6", "M19 21H5") }
    val GitPush: ImageVector by lazy { stroke("GitPush", "m18 9-6-6-6 6", "M12 3v14", "M5 21h14") }
    val Package: ImageVector by lazy { stroke("Package", "M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z", "M12 22V12", "m3.3 7 7.703 4.734a2 2 0 0 0 1.994 0L20.7 7", "m7.5 4.27 9 5.15") }
    val Key: ImageVector by lazy { stroke("Key", "M2.586 17.414A2 2 0 0 0 2 18.828V21a1 1 0 0 0 1 1h3a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h1a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h.172a2 2 0 0 0 1.414-.586l.814-.814a6.5 6.5 0 1 0-4-4z", "M16 7.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0Z") }
    val Shield: ImageVector by lazy { stroke("Shield", "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z", "m9 12 2 2 4-4") }
    val Terminal: ImageVector by lazy { stroke("Terminal", "m7 11 2-2-2-2", "M11 13h4", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2Z") }
    val Settings: ImageVector by lazy { stroke("Settings", "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z") }
    val Undo: ImageVector by lazy { stroke("Undo", "M9 14 4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11") }
    val Redo: ImageVector by lazy { stroke("Redo", "m15 14 5-5-5-5", "M20 9H9.5A5.5 5.5 0 0 0 4 14.5A5.5 5.5 0 0 0 9.5 20H13") }
    val Comment: ImageVector by lazy { stroke("Comment", "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z") }
    val Code: ImageVector by lazy { stroke("Code", "M16 18 L22 12 L16 6", "M8 6 L2 12 L8 18") }
    val Regex: ImageVector by lazy { stroke("Regex", "M17 3v10", "m12.67 5.5 8.66 5", "m12.67 10.5 8.66-5", "M9 17a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v2a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2v-2z") }
    val CaseSensitive: ImageVector by lazy { stroke("CaseSensitive", "m3 15 4-8 4 8", "M4 13h6", "M15 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z", "M21 9v6") }
    val WholeWord: ImageVector by lazy { stroke("WholeWord", "M4 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z", "M10 9v6", "M14 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z", "M14 7v8", "M22 17v1c0 .5-.5 1-1 1H3c-.5 0-1-.5-1-1v-1") }
    val AlertCircle: ImageVector by lazy { stroke("AlertCircle", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z", "M12 8L12 12", "M12 16L12.01 16") }
    val AlertTriangle: ImageVector by lazy { stroke("AlertTriangle", "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3", "M12 9v4", "M12 17h.01") }
    val Info: ImageVector by lazy { stroke("Info", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z", "M12 16v-4", "M12 8h.01") }
    val CheckCircle: ImageVector by lazy { stroke("CheckCircle", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z", "m9 12 2 2 4-4") }
    val Download: ImageVector by lazy { stroke("Download", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M7 10 L12 15 L17 10", "M12 15L12 3") }
    val Share: ImageVector by lazy { stroke("Share", "M15 5a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z", "M3 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z", "M15 19a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z", "M8.59 13.51L15.42 17.49", "M15.41 6.51L8.59 10.49") }
    val Copy: ImageVector by lazy { stroke("Copy", "M10 8h10a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2Z", "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2") }
    val Trash: ImageVector by lazy { stroke("Trash", "M3 6h18", "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6", "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2", "M10 11L10 17", "M14 11L14 17") }
    val Send: ImageVector by lazy { stroke("Send", "M3.714 3.048a.498.498 0 0 0-.683.627l2.843 7.627a2 2 0 0 1 0 1.396l-2.842 7.627a.498.498 0 0 0 .682.627l18-8.5a.5.5 0 0 0 0-.904z", "M6 12h16") }
    val Refresh: ImageVector by lazy { stroke("Refresh", "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8", "M21 3v5h-5", "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16", "M8 16H3v5") }
    val Type: ImageVector by lazy { stroke("Type", "M4 7 L4 4 L20 4 L20 7", "M9 20L15 20", "M12 4L12 20") }
    val Palette: ImageVector by lazy { stroke("Palette", "M13 6.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0Z", "M17 10.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0Z", "M8 7.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0Z", "M6 12.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0Z", "M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10c.926 0 1.648-.746 1.648-1.688 0-.437-.18-.835-.437-1.125-.29-.289-.438-.652-.438-1.125a1.64 1.64 0 0 1 1.668-1.668h1.996c3.051 0 5.555-2.503 5.555-5.554C21.965 6.012 17.461 2 12 2z") }
    val Command: ImageVector by lazy { stroke("Command", "M15 6v12a3 3 0 1 0 3-3H6a3 3 0 1 0 3 3V6a3 3 0 1 0-3 3h12a3 3 0 1 0-3-3") }
    val Layers: ImageVector by lazy { stroke("Layers", "m12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83Z", "m22 17.65-9.17 4.16a2 2 0 0 1-1.66 0L2 17.65", "m22 12.65-9.17 4.16a2 2 0 0 1-1.66 0L2 12.65") }
    val Smartphone: ImageVector by lazy { stroke("Smartphone", "M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-16a2 2 0 0 1 2 -2Z", "M12 18h.01") }
    val Save: ImageVector by lazy { stroke("Save", "M15.2 3a2 2 0 0 1 1.4.6l3.8 3.8a2 2 0 0 1 .6 1.4V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M17 21v-7a1 1 0 0 0-1-1H8a1 1 0 0 0-1 1v7", "M7 3v4a1 1 0 0 0 1 1h7") }
    val Diff: ImageVector by lazy { stroke("Diff", "M12 3v14", "M5 10h14", "M5 21h14") }
    val Bug: ImageVector by lazy { stroke("Bug", "m8 2 1.88 1.88", "M14.12 3.88 16 2", "M9 7.13v-1a3.003 3.003 0 1 1 6 0v1", "M12 20c-3.3 0-6-2.7-6-6v-3a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v3c0 3.3-2.7 6-6 6", "M12 20v-9", "M6.53 9C4.6 8.8 3 7.1 3 5", "M6 13H2", "M3 21c0-2.1 1.7-3.9 3.8-4", "M20.97 5c0 2.1-1.6 3.8-3.5 4", "M22 13h-4", "M17.2 17c2.1.1 3.8 1.9 3.8 4") }
    val Filter: ImageVector by lazy { stroke("Filter", "M3 6h18", "M7 12h10", "M10 18h4") }
    val Lock: ImageVector by lazy { stroke("Lock", "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-7a2 2 0 0 1 2 -2Z", "M7 11V7a5 5 0 0 1 10 0v4") }
    val Unlock: ImageVector by lazy { stroke("Unlock", "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-7a2 2 0 0 1 2 -2Z", "M7 11V7a5 5 0 0 1 9.9-1") }
    val Pause: ImageVector by lazy { stroke("Pause", "M15 4h2a1 1 0 0 1 1 1v14a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1v-14a1 1 0 0 1 1 -1Z", "M7 4h2a1 1 0 0 1 1 1v14a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1v-14a1 1 0 0 1 1 -1Z") }
    val Clock: ImageVector by lazy { stroke("Clock", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z", "M12 6 L12 12 L16 14") }
    val Image: ImageVector by lazy { stroke("Image", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2Z", "M7 9a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z", "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21") }
    val Archive: ImageVector by lazy { stroke("Archive", "M3 3h18a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-18a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1Z", "M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8", "M10 12h4") }
    val Wrap: ImageVector by lazy { stroke("Wrap", "M3 6L21 6", "M3 12h15a3 3 0 1 1 0 6h-4", "M16 16 L14 18 L16 20", "M3 18L10 18") }
    val Keyboard: ImageVector by lazy { stroke("Keyboard", "M10 8h.01", "M12 12h.01", "M14 8h.01", "M16 12h.01", "M18 8h.01", "M6 8h.01", "M7 16h10", "M8 12h.01", "M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-12a2 2 0 0 1 2 -2Z") }
    val Tab: ImageVector by lazy { stroke("Tab", "M17 12H3", "m11 18 6-6-6-6", "M21 5v14") }
    val Wand: ImageVector by lazy { stroke("Wand", "m21.64 3.64-1.28-1.28a1.21 1.21 0 0 0-1.72 0L2.36 18.64a1.21 1.21 0 0 0 0 1.72l1.28 1.28a1.2 1.2 0 0 0 1.72 0L21.64 5.36a1.2 1.2 0 0 0 0-1.72", "m14 7 3 3", "M5 6v4", "M19 14v4", "M10 2v2", "M7 8H3", "M21 16h-4", "M11 3H9") }
    val History: ImageVector by lazy { stroke("History", "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5", "M12 7v5l4 2") }
    val Sidebar: ImageVector by lazy { stroke("Sidebar", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2Z", "M9 3v18") }
    val PanelBottom: ImageVector by lazy { stroke("PanelBottom", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2Z", "M3 15h18") }
    val Rocket: ImageVector by lazy { stroke("Rocket", "M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 0 0-2.91-.09z", "m12 15-3-3a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.35 22.35 0 0 1-4 2z", "M9 12H4s.55-3.03 2-4c1.62-1.08 5 0 5 0", "M12 15v5s3.03-.55 4-2c1.08-1.62 0-5 0-5") }
    val PanelRight: ImageVector by lazy { stroke("PanelRight", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2Z", "M15 3v18") }
    val CollapseAll: ImageVector by lazy { stroke("CollapseAll", "m7 20 5-5 5 5", "m7 4 5 5 5-5") }
    val Export: ImageVector by lazy { stroke("Export", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M17 8 L12 3 L7 8", "M12 3L12 15") }
    val ScrollLock: ImageVector by lazy { stroke("ScrollLock", "M12 2v14", "m19 9-7 7-7-7", "M11 21a1 1 0 1 0 2 0a1 1 0 1 0 -2 0Z") }
    val Server: ImageVector by lazy { stroke("Server", "M4 2h16a2 2 0 0 1 2 2v4a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-4a2 2 0 0 1 2 -2Z", "M4 14h16a2 2 0 0 1 2 2v4a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-4a2 2 0 0 1 2 -2Z", "M6 6L6.01 6", "M6 18L6.01 18") }
    val Eye: ImageVector by lazy { stroke("Eye", "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z") }
    val EyeOff: ImageVector by lazy { stroke("EyeOff", "M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49", "M14.084 14.158a3 3 0 0 1-4.242-4.242", "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143", "m2 2 20 20") }
    val Globe: ImageVector by lazy { stroke("Globe", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z", "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20", "M2 12h20") }
    val Bot: ImageVector by lazy { stroke("Bot", "M12 8V4H8", "M6 8h12a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2Z", "M2 14h2", "M20 14h2", "M15 13v2", "M9 13v2") }
}
