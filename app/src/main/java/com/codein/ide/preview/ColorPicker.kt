package com.codein.ide.preview

import com.codein.ide.core.ui.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** A `#RGB`/`#ARGB`/`#RRGGBB`/`#AARRGGBB` literal in a line, as character offsets into the document. */
data class ColorLiteral(val start: Int, val end: Int, val text: String) {
    val argb: Long? get() = LayoutXml.color(text, ResourceValues())
}

object ColorLiterals {
    private val pattern = Regex("""#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{4}|[0-9a-fA-F]{3})\b""")
    private val kotlinColor = Regex("""Color\(0x([0-9a-fA-F]{8})\)""")

    /** The color literal touching 0-based [line]/[column] of [text], or null. Also finds Compose `Color(0xAARRGGBB)`. */
    fun at(text: String, line: Int, column: Int): ColorLiteral? {
        var offset = 0
        var current = 0
        while (current < line) {
            val next = text.indexOf('\n', offset)
            if (next < 0) return null
            offset = next + 1
            current++
        }
        val lineEnd = text.indexOf('\n', offset).let { if (it < 0) text.length else it }
        val lineText = text.substring(offset, lineEnd)
        pattern.findAll(lineText).firstOrNull { column in it.range.first..(it.range.last + 1) }?.let {
            return ColorLiteral(offset + it.range.first, offset + it.range.last + 1, it.value)
        }
        kotlinColor.findAll(lineText).firstOrNull { column in it.range.first..(it.range.last + 1) }?.let {
            val hex = it.groups[1]!!
            return ColorLiteral(offset + hex.range.first - 2, offset + hex.range.last + 1, "#" + hex.value)
        }
        return null
    }

    /** `#AARRGGBB`, or `#RRGGBB` when fully opaque. */
    fun format(argb: Long): String {
        val a = (argb shr 24) and 0xFF
        return if (a == 0xFFL) "#%06X".format(argb and 0xFFFFFF) else "#%08X".format(argb and 0xFFFFFFFFL)
    }
}

/**
 * Hue / saturation / brightness / alpha sliders and a hex field. [onPick] gets the new literal in
 * the same notation as the original (`#…` or `0x…` for Compose).
 */
@Composable
fun ColorPickerDialog(literal: ColorLiteral, composeStyle: Boolean, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val initial = literal.argb ?: 0xFF6750A4
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toInt(), it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    var alpha by remember { mutableFloatStateOf(((initial shr 24) and 0xFF) / 255f) }
    val argb = (android.graphics.Color.HSVToColor((alpha * 255).toInt(), floatArrayOf(hue, sat, value)).toLong() and 0xFFFFFFFFL)
    var hex by remember(argb) { mutableStateOf(ColorLiterals.format(argb)) }
    fun result(): String {
        val text = ColorLiterals.format(argb)
        return if (composeStyle) "0x" + "%08X".format(argb) else text
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Pick a color")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(56.dp).background(Color(literal.argb?.toInt() ?: 0), RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)))
                    Text("→", style = MaterialTheme.typography.titleLarge)
                    Box(Modifier.size(56.dp).background(Color(argb.toInt()), RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)))
                }
                LabeledSlider("Hue", hue, 0f..360f) { hue = it }
                Box(Modifier.fillMaxWidth().height(6.dp).background(androidx.compose.ui.graphics.Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }), RoundedCornerShape(3.dp)))
                LabeledSlider("Saturation", sat, 0f..1f) { sat = it }
                LabeledSlider("Brightness", value, 0f..1f) { value = it }
                LabeledSlider("Opacity", alpha, 0f..1f) { alpha = it }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { text ->
                        hex = text
                        LayoutXml.color(text.trim(), ResourceValues())?.let { parsed ->
                            val out = FloatArray(3)
                            android.graphics.Color.colorToHSV(parsed.toInt(), out)
                            hue = out[0]; sat = out[1]; value = out[2]; alpha = ((parsed shr 24) and 0xFF) / 255f
                        }
                    },
                    label = { Text(tr("Hex")) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(result()) }) { Text("Use ${ColorLiterals.format(argb)}") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(84.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}
