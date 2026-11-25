package com.codein.ide.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codein.ide.core.ui.icons.CodeinIcons
import com.codein.ide.core.ui.theme.CodeinTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** True for files under `res/layout*` that the wireframe preview can show. */
fun isLayoutFile(relativePath: String?): Boolean =
    relativePath != null && relativePath.endsWith(".xml") && Regex("""(^|/)res/layout[^/]*/""").containsMatchIn(relativePath)

/**
 * A wireframe preview of an Android layout file, drawn with Compose straight from the XML (no
 * build). Common views and containers are approximated: LinearLayout orientation, sizes, padding,
 * margins, gravity, text, colors and visibility are honored; ConstraintLayout and RelativeLayout
 * children are stacked vertically, since their constraints are not solved.
 */
@Composable
fun LayoutPreview(xml: String, projectDir: File?, modifier: Modifier = Modifier) {
    val resources by produceState(ResourceValues(), projectDir) { value = withContext(Dispatchers.IO) { ResourceValues.load(projectDir) } }
    val parsed = remember(xml) { LayoutXml.parse(xml) }
    Column(modifier.background(CodeinTheme.colors.base)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(CodeinIcons.Smartphone, null, Modifier.size(14.dp), tint = CodeinTheme.colors.textMuted)
            Spacer(Modifier.width(6.dp))
            Text(
                "Wireframe preview · constraints are not solved; build and run for the real look",
                style = MaterialTheme.typography.labelSmall,
                color = CodeinTheme.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), contentAlignment = Alignment.TopCenter) {
            parsed.fold(
                onSuccess = { root ->
                    Box(
                        Modifier.widthIn(max = 380.dp).fillMaxWidth().heightIn(min = 560.dp)
                            .clip(RoundedCornerShape(18.dp)).background(Color(0xFFFDFBFF))
                            .border(BorderStroke(6.dp, Color(0xFF2B2B30)), RoundedCornerShape(18.dp)).padding(6.dp),
                    ) {
                        Render(root, resources, projectDir, depth = 0, parentIsRow = false)
                    }
                },
                onFailure = { e ->
                    Text("Cannot preview: ${e.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                },
            )
        }
    }
}

private val InkColor = Color(0xFF1B1B1F)
private val MutedInk = Color(0xFF6B6B74)
private val Accent = Color(0xFF6750A4)
private val Outline = Color(0xFFB9B6C2)

@Composable
private fun Render(node: LayoutNode, res: ResourceValues, projectDir: File?, depth: Int, parentIsRow: Boolean, scope: Any? = null) {
    if (depth > 30) return
    if (node["visibility"] == "gone") return
    val hidden = node["visibility"] == "invisible"
    val base = sizeModifier(node, res, parentIsRow, scope)
        .then(marginModifier(node, res))
        .then(backgroundModifier(node, res))
        .then(paddingModifier(node, res))
        .then(if (hidden) Modifier.alpha(0f) else Modifier)
    val tag = node.simpleTag
    when {
        tag == "merge" || tag == "layout" -> Column(base) { node.children.forEach { Render(it, res, projectDir, depth + 1, false, this) } }
        tag == "include" -> IncludeView(node, res, projectDir, depth, base)
        tag == "LinearLayout" || tag == "LinearLayoutCompat" || tag == "RadioGroup" || tag == "TableRow" || tag == "TableLayout" ->
            // LinearLayout and LinearLayoutCompat default to horizontal; RadioGroup and TableLayout to vertical.
            if (node["orientation"] == "horizontal" || tag == "TableRow" || (node["orientation"] == null && tag.startsWith("LinearLayout"))) {
                Row(base, verticalAlignment = verticalGravity(node["gravity"]), horizontalArrangement = horizontalArrangement(node["gravity"])) {
                    node.children.forEach { Render(it, res, projectDir, depth + 1, true, this) }
                }
            } else {
                Column(base, horizontalAlignment = horizontalGravity(node["gravity"]), verticalArrangement = verticalArrangement(node["gravity"])) {
                    node.children.forEach { Render(it, res, projectDir, depth + 1, false, this) }
                }
            }
        tag == "ScrollView" || tag == "NestedScrollView" ->
            Column(base) { node.children.forEach { Render(it, res, projectDir, depth + 1, false, this) } }
        tag == "HorizontalScrollView" ->
            Row(base.horizontalScroll(rememberScrollState())) { node.children.forEach { Render(it, res, projectDir, depth + 1, true, this) } }
        tag == "FrameLayout" || tag == "CoordinatorLayout" || tag == "CardView" || tag == "MaterialCardView" ->
            Box(if (tag.endsWith("CardView")) base.clip(RoundedCornerShape(12.dp)).border(1.dp, Outline, RoundedCornerShape(12.dp)) else base, contentAlignment = boxGravity(node["gravity"] ?: node["foregroundGravity"])) {
                node.children.forEach { Render(it, res, projectDir, depth + 1, false, this) }
            }
        tag == "ConstraintLayout" || tag == "RelativeLayout" || tag == "AppBarLayout" || tag == "MotionLayout" || tag == "DrawerLayout" ->
            Column(base, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                node.children.forEach { child ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = constraintAlignment(child)) { Render(child, res, projectDir, depth + 1, false, this) }
                }
            }
        tag in TextTags -> TextLike(node, res, base)
        tag in ButtonTags -> ButtonLike(node, res, base)
        tag in InputTags -> InputLike(node, res, base)
        tag == "TextInputLayout" -> Column(base) {
            val hint = res.resolve(node["hint"])
            node.children.forEach { child -> Render(child.copy(attrs = child.attrs + listOfNotNull(hint?.let { "android:hint" to it })), res, projectDir, depth + 1, false, this) }
        }
        tag in CheckTags -> CheckLike(node, res, base, round = tag.contains("Radio"), switch = tag.contains("Switch"))
        tag == "ImageView" || tag == "ShapeableImageView" || tag == "ImageButton" -> Placeholder(base, "Image", defaultSize = 48)
        tag == "ProgressBar" || tag == "CircularProgressIndicator" -> Box(base.size(36.dp).border(3.dp, Accent, CircleShape))
        tag == "LinearProgressIndicator" || tag == "SeekBar" || tag == "Slider" -> Box(base.height(4.dp).fillMaxWidth().background(Accent.copy(alpha = 0.35f)))
        tag == "View" -> Box(base.then(if (node["background"] == null) Modifier.background(Outline) else Modifier).heightIn(min = 1.dp))
        tag == "Space" -> Spacer(base)
        tag in ListTags -> Column(base, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFF1EEF6)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(Outline))
                    Spacer(Modifier.width(10.dp))
                    Text("${node["id"]?.substringAfter('/') ?: tag} item ${i + 1}", fontSize = 13.sp, color = MutedInk)
                }
            }
        }
        tag == "BottomNavigationView" || tag == "NavigationBarView" -> Row(
            base.fillMaxWidth().background(Color(0xFFF3EDF7)).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf("Home", "Dashboard", "Notifications").forEach { Text(it, fontSize = 12.sp, color = MutedInk) }
        }
        tag == "Toolbar" || tag == "MaterialToolbar" -> Box(base.fillMaxWidth().height(56.dp).background(Color(0xFFF3EDF7)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            Text(res.resolve(node["title"]) ?: "Toolbar", fontSize = 20.sp, color = InkColor)
        }
        tag == "FloatingActionButton" || tag == "ExtendedFloatingActionButton" -> Box(
            base.clip(RoundedCornerShape(16.dp)).background(Color(0xFFEADDFF)).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) { Text(res.resolve(node["text"]) ?: "+", color = Color(0xFF21005D), fontWeight = FontWeight.Medium) }
        tag == "fragment" || tag == "FragmentContainerView" -> Placeholder(base.heightIn(min = 120.dp), "Fragment " + (node["name"]?.substringAfterLast('.') ?: ""), defaultSize = 0)
        tag == "ComposeView" -> Placeholder(base.heightIn(min = 120.dp), "ComposeView", defaultSize = 0)
        node.children.isNotEmpty() -> Column(base) { node.children.forEach { Render(it, res, projectDir, depth + 1, false, this) } }
        else -> Placeholder(base, tag, defaultSize = 40)
    }
}

private val TextTags = setOf("TextView", "AppCompatTextView", "MaterialTextView", "Chip")
private val ButtonTags = setOf("Button", "AppCompatButton", "MaterialButton")
private val InputTags = setOf("EditText", "AppCompatEditText", "TextInputEditText", "AutoCompleteTextView")
private val CheckTags = setOf("CheckBox", "MaterialCheckBox", "RadioButton", "MaterialRadioButton", "Switch", "SwitchCompat", "SwitchMaterial", "MaterialSwitch", "ToggleButton")
private val ListTags = setOf("RecyclerView", "ListView", "GridView", "ViewPager", "ViewPager2")

@Composable
private fun TextLike(node: LayoutNode, res: ResourceValues, modifier: Modifier) {
    val text = res.resolve(node["text"]) ?: res.resolve(node["hint"]) ?: tools(node, "text") ?: ""
    val color = LayoutXml.color(node["textColor"], res)?.let { Color(it) } ?: if (node["text"] == null) MutedInk else InkColor
    Text(
        text,
        modifier = modifier,
        fontSize = (LayoutXml.dimension(node["textSize"], res) ?: textAppearanceSize(node["textAppearance"])).sp,
        color = color,
        fontWeight = if (node["textStyle"]?.contains("bold") == true) FontWeight.Bold else null,
        fontStyle = if (node["textStyle"]?.contains("italic") == true) FontStyle.Italic else null,
        textAlign = when {
            node["textAlignment"] == "center" || node["gravity"]?.contains("center") == true -> TextAlign.Center
            node["gravity"]?.contains("end") == true || node["gravity"]?.contains("right") == true -> TextAlign.End
            else -> null
        },
        maxLines = node["maxLines"]?.toIntOrNull() ?: if (node["singleLine"] == "true") 1 else Int.MAX_VALUE,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ButtonLike(node: LayoutNode, res: ResourceValues, modifier: Modifier) {
    val outlined = node.attrs["style"]?.contains("Outlined") == true
    val textStyle = node.attrs["style"]?.contains("TextButton") == true
    val bg = LayoutXml.color(node["backgroundTint"] ?: node["background"], res)?.let { Color(it) } ?: Accent
    Box(
        modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp))
            .then(
                when {
                    textStyle -> Modifier
                    outlined -> Modifier.border(1.dp, Outline, RoundedCornerShape(20.dp))
                    else -> Modifier.background(bg)
                },
            ).padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            res.resolve(node["text"]) ?: tools(node, "text") ?: "Button",
            color = LayoutXml.color(node["textColor"], res)?.let { Color(it) } ?: if (outlined || textStyle) Accent else Color.White,
            fontSize = (LayoutXml.dimension(node["textSize"], res) ?: 14f).sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InputLike(node: LayoutNode, res: ResourceValues, modifier: Modifier) {
    val text = res.resolve(node["text"])
    Box(modifier.heightIn(min = 48.dp).border(1.dp, Outline, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
        Text(text ?: res.resolve(node["hint"]) ?: "", color = if (text != null) InkColor else MutedInk, fontSize = (LayoutXml.dimension(node["textSize"], res) ?: 16f).sp, maxLines = 1)
    }
}

@Composable
private fun CheckLike(node: LayoutNode, res: ResourceValues, modifier: Modifier, round: Boolean, switch: Boolean) {
    val checked = node["checked"] == "true"
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val label = res.resolve(node["text"]).orEmpty()
        if (switch) {
            if (label.isNotEmpty()) Text(label, Modifier.weight(1f, fill = false), fontSize = 14.sp, color = InkColor)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.width(40.dp).height(22.dp).clip(CircleShape).background(if (checked) Accent else Outline), contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
                Box(Modifier.padding(3.dp).size(16.dp).clip(CircleShape).background(Color.White))
            }
        } else {
            val shape = if (round) CircleShape else RoundedCornerShape(3.dp)
            Box(Modifier.size(18.dp).clip(shape).then(if (checked) Modifier.background(Accent) else Modifier.border(2.dp, MutedInk, shape)))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 14.sp, color = InkColor)
        }
    }
}

@Composable
private fun Placeholder(modifier: Modifier, label: String, defaultSize: Int) {
    Box(
        modifier.then(if (defaultSize > 0) Modifier.heightIn(min = defaultSize.dp).widthIn(min = defaultSize.dp) else Modifier)
            .background(Color(0xFFECE9F1)).border(1.dp, Outline),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 10.sp, color = MutedInk, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(2.dp))
    }
}

@Composable
private fun IncludeView(node: LayoutNode, res: ResourceValues, projectDir: File?, depth: Int, modifier: Modifier) {
    val name = node.attrs["layout"]?.substringAfter("@layout/")
    val included by produceState<LayoutNode?>(null, name, projectDir) {
        value = withContext(Dispatchers.IO) {
            val file = projectDir?.let { File(it, "app/src/main/res/layout/$name.xml") }?.takeIf { it.isFile } ?: return@withContext null
            LayoutXml.parse(file.readText()).getOrNull()
        }
    }
    val child = included
    if (child == null) Placeholder(modifier.fillMaxWidth().heightIn(min = 48.dp), "include $name", defaultSize = 0)
    else Box(modifier) { Render(child, res, projectDir, depth + 1, false, this) }
}

private fun tools(node: LayoutNode, name: String): String? = node.attrs["tools:$name"]

private fun textAppearanceSize(appearance: String?): Float = when {
    appearance == null -> 14f
    appearance.contains("Display") -> 36f
    appearance.contains("Headline") -> 24f
    appearance.contains("Title") -> 20f
    appearance.contains("Large") -> 22f
    appearance.contains("Medium") -> 18f
    appearance.contains("Small") || appearance.contains("Caption") -> 12f
    else -> 14f
}

private fun sizeModifier(node: LayoutNode, res: ResourceValues, parentIsRow: Boolean, scope: Any?): Modifier {
    val weight = node["layout_weight"]?.toFloatOrNull()
    var m: Modifier = Modifier
    if (weight != null && weight > 0f) {
        m = when (scope) {
            is RowScope -> with(scope) { Modifier.weight(weight) }
            is ColumnScope -> with(scope) { Modifier.weight(weight) }
            else -> m
        }
    }
    val w = node["layout_width"]
    val h = node["layout_height"]
    m = when {
        weight != null && parentIsRow && (w == "0dp" || w == "0dip") -> m
        w == "match_parent" || w == "fill_parent" -> if (scope is RowScope) with(scope) { m.weight(1f) } else m.fillMaxWidth()
        else -> LayoutXml.dimension(w, res)?.takeIf { it > 0 }?.let { m.width(it.dp) } ?: m
    }
    m = when {
        weight != null && !parentIsRow && (h == "0dp" || h == "0dip") -> m
        h == "match_parent" || h == "fill_parent" -> if (scope is ColumnScope) m else m.fillMaxHeight()
        else -> LayoutXml.dimension(h, res)?.takeIf { it > 0 }?.let { m.height(it.dp) } ?: m
    }
    if (scope is BoxScope && node["layout_gravity"] != null) {
        m = with(scope) { m.align(boxGravity(node["layout_gravity"])) }
    }
    return m
}

private fun marginModifier(node: LayoutNode, res: ResourceValues): Modifier = edges(node, res, "layout_margin")
private fun paddingModifier(node: LayoutNode, res: ResourceValues): Modifier = edges(node, res, "padding")

private fun edges(node: LayoutNode, res: ResourceValues, prefix: String): Modifier {
    val all = LayoutXml.dimension(node[prefix], res)
    val h = LayoutXml.dimension(node["${prefix}Horizontal"], res)
    val v = LayoutXml.dimension(node["${prefix}Vertical"], res)
    val start = LayoutXml.dimension(node["${prefix}Start"] ?: node["${prefix}Left"], res) ?: h ?: all ?: 0f
    val end = LayoutXml.dimension(node["${prefix}End"] ?: node["${prefix}Right"], res) ?: h ?: all ?: 0f
    val top = LayoutXml.dimension(node["${prefix}Top"], res) ?: v ?: all ?: 0f
    val bottom = LayoutXml.dimension(node["${prefix}Bottom"], res) ?: v ?: all ?: 0f
    if (start == 0f && end == 0f && top == 0f && bottom == 0f) return Modifier
    return Modifier.padding(PaddingValues(start.coerceAtLeast(0f).dp, top.coerceAtLeast(0f).dp, end.coerceAtLeast(0f).dp, bottom.coerceAtLeast(0f).dp))
}

private fun backgroundModifier(node: LayoutNode, res: ResourceValues): Modifier {
    if (node.simpleTag in ButtonTags) return Modifier
    val color = LayoutXml.color(node["background"], res) ?: return Modifier
    return Modifier.background(Color(color))
}

private fun Set<String>.hasAny(vararg names: String) = names.any { it in this }
private fun parts(gravity: String?): Set<String> = gravity?.split('|')?.map { it.trim() }?.toSet().orEmpty()

private fun horizontalGravity(gravity: String?): Alignment.Horizontal {
    val p = parts(gravity)
    return when {
        p.hasAny("center", "center_horizontal") -> Alignment.CenterHorizontally
        p.hasAny("end", "right") -> Alignment.End
        else -> Alignment.Start
    }
}

private fun verticalGravity(gravity: String?): Alignment.Vertical {
    val p = parts(gravity)
    return when {
        p.hasAny("center", "center_vertical") -> Alignment.CenterVertically
        p.contains("bottom") -> Alignment.Bottom
        else -> Alignment.Top
    }
}

private fun verticalArrangement(gravity: String?): Arrangement.Vertical {
    val p = parts(gravity)
    return when {
        p.hasAny("center", "center_vertical") -> Arrangement.Center
        p.contains("bottom") -> Arrangement.Bottom
        else -> Arrangement.Top
    }
}

private fun horizontalArrangement(gravity: String?): Arrangement.Horizontal {
    val p = parts(gravity)
    return when {
        p.hasAny("center", "center_horizontal") -> Arrangement.Center
        p.hasAny("end", "right") -> Arrangement.End
        else -> Arrangement.Start
    }
}

private fun boxGravity(gravity: String?): Alignment {
    val p = parts(gravity)
    val vertical = when {
        p.hasAny("center", "center_vertical") -> 0
        p.contains("bottom") -> 1
        else -> -1
    }
    val horizontal = when {
        p.hasAny("center", "center_horizontal") -> 0
        p.hasAny("end", "right") -> 1
        else -> -1
    }
    return when (vertical to horizontal) {
        -1 to -1 -> Alignment.TopStart
        -1 to 0 -> Alignment.TopCenter
        -1 to 1 -> Alignment.TopEnd
        0 to -1 -> Alignment.CenterStart
        0 to 0 -> Alignment.Center
        0 to 1 -> Alignment.CenterEnd
        1 to -1 -> Alignment.BottomStart
        1 to 0 -> Alignment.BottomCenter
        else -> Alignment.BottomEnd
    }
}

/** Horizontal placement of a ConstraintLayout/RelativeLayout child from its start/end constraints. */
private fun constraintAlignment(child: LayoutNode): Alignment {
    val a = child.attrs
    val start = a["app:layout_constraintStart_toStartOf"] == "parent" || a["app:layout_constraintLeft_toLeftOf"] == "parent"
    val end = a["app:layout_constraintEnd_toEndOf"] == "parent" || a["app:layout_constraintRight_toRightOf"] == "parent"
    return when {
        a["android:layout_centerHorizontal"] == "true" || a["android:layout_centerInParent"] == "true" || (start && end) -> Alignment.TopCenter
        end || a["android:layout_alignParentEnd"] == "true" || a["android:layout_alignParentRight"] == "true" -> Alignment.TopEnd
        else -> Alignment.TopStart
    }
}
