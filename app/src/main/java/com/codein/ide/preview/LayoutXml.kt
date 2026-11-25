package com.codein.ide.preview

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/** One element of a layout file: its tag and attributes (keys keep their prefix, e.g. `android:text`). */
data class LayoutNode(
    val tag: String,
    val attrs: Map<String, String>,
    val children: List<LayoutNode>,
) {
    val simpleTag: String get() = tag.substringAfterLast('.')
    operator fun get(name: String): String? = attrs["android:$name"] ?: attrs["app:$name"] ?: attrs[name]
}

/** Values from the project's `res/values*` files, used to show real text and colors in the preview. */
data class ResourceValues(
    val strings: Map<String, String> = emptyMap(),
    val colors: Map<String, String> = emptyMap(),
    val dimens: Map<String, String> = emptyMap(),
) {
    /** Resolves `@string/x`, `@color/x`, `@dimen/x` and `@android:color/…` one or more levels deep. */
    fun resolve(value: String?, depth: Int = 0): String? {
        if (value == null || depth > 8) return value
        val ref = Regex("""^@(?:\+)?(android:)?(string|color|dimen)/(.+)$""").matchEntire(value.trim()) ?: return value
        val (framework, type, name) = ref.destructured
        if (framework.isNotEmpty()) return AndroidColors[name] ?: value
        val found = when (type) {
            "string" -> strings[name]
            "color" -> colors[name]
            else -> dimens[name]
        } ?: return value
        return resolve(found, depth + 1)
    }

    companion object {
        private val AndroidColors = mapOf("white" to "#FFFFFF", "black" to "#000000", "transparent" to "#00000000", "holo_blue_light" to "#33B5E5", "holo_red_light" to "#FF4444")

        /** Reads `app/src/main/res/values/` (the default locale) of [projectDir]. */
        fun load(projectDir: File?): ResourceValues {
            val dir = projectDir?.let { File(it, "app/src/main/res/values") } ?: return ResourceValues()
            if (!dir.isDirectory) return ResourceValues()
            val strings = HashMap<String, String>()
            val colors = HashMap<String, String>()
            val dimens = HashMap<String, String>()
            dir.listFiles { f -> f.extension == "xml" }.orEmpty().forEach { file ->
                val doc = runCatching { newBuilder().parse(file) }.getOrNull() ?: return@forEach
                val items = doc.documentElement?.childNodes ?: return@forEach
                for (i in 0 until items.length) {
                    val el = items.item(i) as? Element ?: continue
                    val name = el.getAttribute("name").takeIf { it.isNotEmpty() } ?: continue
                    val text = el.textContent.trim().replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")
                    when (el.tagName) {
                        "string" -> strings[name] = text
                        "color" -> colors[name] = text
                        "dimen" -> dimens[name] = text
                    }
                }
            }
            return ResourceValues(strings, colors, dimens)
        }
    }
}

object LayoutXml {
    /** Parses layout XML into a tree, or returns the parser's message for a malformed file. */
    fun parse(xml: String): Result<LayoutNode> = runCatching {
        val doc = newBuilder().parse(InputSource(StringReader(xml)))
        toNode(doc.documentElement ?: error("The file has no root element"))
    }.recoverCatching { e ->
        throw IllegalArgumentException(e.message?.substringAfter("; ")?.trim() ?: "Invalid XML", e)
    }

    private fun toNode(el: Element): LayoutNode {
        val attrs = LinkedHashMap<String, String>()
        val map = el.attributes
        for (i in 0 until map.length) {
            val a = map.item(i)
            attrs[a.nodeName] = a.nodeValue
        }
        val children = ArrayList<LayoutNode>()
        val nodes = el.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.nodeType == Node.ELEMENT_NODE) children += toNode(n as Element)
        }
        return LayoutNode(el.tagName, attrs, children)
    }

    /** Converts `16dp`, `14sp`, `8px` or `@dimen/x` to a number of dp/sp, or null. */
    fun dimension(value: String?, res: ResourceValues): Float? {
        val v = res.resolve(value)?.trim() ?: return null
        return Regex("""^(-?\d+(?:\.\d+)?)\s*(dp|dip|sp|px|pt)?$""").matchEntire(v)?.groupValues?.get(1)?.toFloatOrNull()
    }

    /** Parses `#RGB`, `#ARGB`, `#RRGGBB`, `#AARRGGBB` (after resolving references) to ARGB, or null. */
    fun color(value: String?, res: ResourceValues): Long? {
        val v = res.resolve(value)?.trim() ?: return null
        if (!v.startsWith("#")) return null
        val hex = v.drop(1)
        val full = when (hex.length) {
            3 -> "FF" + hex.map { "$it$it" }.joinToString("")
            4 -> hex.map { "$it$it" }.joinToString("")
            6 -> "FF$hex"
            8 -> hex
            else -> return null
        }
        return full.toLongOrNull(16)
    }
}

private fun newBuilder() = DocumentBuilderFactory.newInstance().apply {
    isNamespaceAware = false
    isValidating = false
    runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    runCatching { isExpandEntityReferences = false }
}.newDocumentBuilder().apply {
    // Report problems through the thrown exception only, not on stderr.
    setErrorHandler(object : org.xml.sax.ErrorHandler {
        override fun warning(e: org.xml.sax.SAXParseException) = Unit
        override fun error(e: org.xml.sax.SAXParseException) = throw e
        override fun fatalError(e: org.xml.sax.SAXParseException) = throw e
    })
}
