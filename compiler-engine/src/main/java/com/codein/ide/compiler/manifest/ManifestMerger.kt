package com.codein.ide.compiler.manifest

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * A pragmatic manifest merger for on-device builds. It merges library (AAR) manifests into the
 * application manifest following the most important rules of the AGP merger:
 *
 * - `uses-permission`, `uses-feature`, `queries` entries are unioned (deduplicated by name).
 * - `application` children (`activity`, `service`, `receiver`, `provider`, `meta-data`,
 *   `uses-library`, `activity-alias`, `profileable`) are added when the app does not already
 *   declare a node with the same `android:name`.
 * - `tools:node="remove"` on an app node removes library nodes with the same name.
 * - `${applicationId}` and other placeholders are substituted.
 * - `uses-sdk` is stripped (aapt2 receives min/target SDK from the Gradle model).
 */
@Singleton
class ManifestMerger @Inject constructor() {

    data class MergeResult(val mergedManifest: File, val warnings: List<String>)

    fun merge(
        appManifest: File,
        libraryManifests: List<File>,
        output: File,
        packageName: String,
        placeholders: Map<String, String> = emptyMap(),
    ): MergeResult {
        val warnings = mutableListOf<String>()
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val builder = factory.newDocumentBuilder()
        val allPlaceholders = placeholders + ("applicationId" to packageName) + ("packageName" to packageName)

        val appDoc = builder.parse(applyPlaceholders(appManifest.readText(), allPlaceholders).byteInputStream())
        val appRoot = appDoc.documentElement
        appRoot.setAttribute("package", packageName)
        removeChildren(appRoot, "uses-sdk")
        val appApplication = firstChild(appRoot, "application") ?: appDoc.createElement("application").also { appRoot.appendChild(it) }

        val removedNames = collectRemovedNames(appRoot)
        val existingRootNames = rootChildNames(appRoot)
        val existingAppNames = applicationChildNames(appApplication)

        for (libManifest in libraryManifests) {
            if (!libManifest.isFile) continue
            val libDoc = runCatching { builder.parse(applyPlaceholders(libManifest.readText(), allPlaceholders).byteInputStream()) }
                .getOrElse { e ->
                    warnings += "Skipping unreadable manifest ${libManifest.path}: ${e.message}"
                    continue
                }
            val libRoot = libDoc.documentElement
            for (child in elementChildren(libRoot)) {
                when (child.tagName) {
                    "uses-permission", "uses-permission-sdk-23", "uses-feature", "permission", "permission-group", "permission-tree" -> {
                        val key = child.tagName + "|" + androidName(child)
                        if (key !in existingRootNames && androidName(child) !in removedNames) {
                            appRoot.insertBefore(appDoc.importNode(child, true), appApplication)
                            existingRootNames += key
                        }
                    }
                    "queries" -> {
                        val target = firstChild(appRoot, "queries") ?: appDoc.createElement("queries").also { appRoot.insertBefore(it, appApplication) }
                        for (q in elementChildren(child)) target.appendChild(appDoc.importNode(q, true))
                    }
                    "application" -> {
                        for (component in elementChildren(child)) {
                            val name = androidName(component)
                            val key = component.tagName + "|" + name
                            if (name.isNotEmpty() && (key in existingAppNames || name in removedNames)) continue
                            val imported = appDoc.importNode(component, true) as Element
                            stripToolsAttributes(imported)
                            appApplication.appendChild(imported)
                            existingAppNames += key
                        }
                    }
                }
            }
        }

        removeToolsRemoveNodes(appRoot)
        stripToolsAttributes(appRoot)
        output.parentFile?.mkdirs()
        writeDocument(appDoc, output)
        return MergeResult(output, warnings)
    }

    private fun applyPlaceholders(text: String, placeholders: Map<String, String>): String =
        placeholders.entries.fold(text) { acc, (key, value) -> acc.replace("\${$key}", value) }

    private fun elementChildren(parent: Element): List<Element> {
        val list = mutableListOf<Element>()
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.nodeType == Node.ELEMENT_NODE) list += n as Element
        }
        return list
    }

    private fun firstChild(parent: Element, tag: String): Element? = elementChildren(parent).firstOrNull { it.tagName == tag }

    private fun removeChildren(parent: Element, tag: String) {
        elementChildren(parent).filter { it.tagName == tag }.forEach { parent.removeChild(it) }
    }

    private fun androidName(element: Element): String = element.getAttributeNS(ANDROID_NS, "name").ifEmpty { element.getAttribute("android:name") }

    private fun toolsNode(element: Element): String = element.getAttributeNS(TOOLS_NS, "node").ifEmpty { element.getAttribute("tools:node") }

    private fun rootChildNames(root: Element): MutableSet<String> =
        elementChildren(root).map { it.tagName + "|" + androidName(it) }.toMutableSet()

    private fun applicationChildNames(application: Element): MutableSet<String> =
        elementChildren(application).map { it.tagName + "|" + androidName(it) }.toMutableSet()

    private fun collectRemovedNames(root: Element): Set<String> {
        val result = mutableSetOf<String>()
        fun visit(e: Element) {
            if (toolsNode(e) == "remove") androidName(e).takeIf { it.isNotEmpty() }?.let(result::add)
            elementChildren(e).forEach(::visit)
        }
        visit(root)
        return result
    }

    private fun removeToolsRemoveNodes(root: Element) {
        elementChildren(root).forEach { child ->
            if (toolsNode(child) == "remove" || toolsNode(child) == "removeAll") root.removeChild(child) else removeToolsRemoveNodes(child)
        }
    }

    private fun stripToolsAttributes(element: Element) {
        val attrs = element.attributes
        val toRemove = mutableListOf<Node>()
        for (i in 0 until attrs.length) {
            val a = attrs.item(i)
            if (a.namespaceURI == TOOLS_NS || a.nodeName.startsWith("tools:") || a.nodeName == "xmlns:tools") toRemove += a
        }
        toRemove.forEach { element.removeAttributeNode(it as org.w3c.dom.Attr) }
        elementChildren(element).forEach(::stripToolsAttributes)
    }

    private fun writeDocument(doc: Document, output: File) {
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        }
        output.outputStream().use { transformer.transform(DOMSource(doc), StreamResult(it)) }
    }

    companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val TOOLS_NS = "http://schemas.android.com/tools"
    }
}
