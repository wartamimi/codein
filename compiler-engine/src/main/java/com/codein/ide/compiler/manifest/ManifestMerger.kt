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
 * - Relative class names (`.MainActivity`, `MainActivity`) are fully qualified against the
 *   declaring manifest's own package (the app namespace, or the library's package), before the
 *   merged manifest's `package` is set to the application id. Without this, a library component
 *   or an app with an `applicationIdSuffix` points at classes that do not exist and the app
 *   crashes at launch with ClassNotFoundException.
 * - When a library declares a component the app (or an earlier library) already has, such as the
 *   shared `androidx.startup.InitializationProvider`, its `meta-data` children and missing
 *   attributes are merged into the existing node instead of being dropped.
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
        namespace: String = packageName,
    ): MergeResult {
        val warnings = mutableListOf<String>()
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val builder = factory.newDocumentBuilder()
        val allPlaceholders = placeholders + ("applicationId" to packageName) + ("packageName" to packageName)

        val appDoc = builder.parse(applyPlaceholders(appManifest.readText(), allPlaceholders).byteInputStream())
        val appRoot = appDoc.documentElement
        val appNamespace = appRoot.getAttribute("package").ifEmpty { namespace }
        qualifyClassNames(appRoot, appNamespace)
        appRoot.setAttribute("package", packageName)
        removeChildren(appRoot, "uses-sdk")
        val appApplication = firstChild(appRoot, "application") ?: appDoc.createElement("application").also { appRoot.appendChild(it) }

        val removedNames = collectRemovedNames(appRoot)
        val existingRootNames = rootChildNames(appRoot)
        val existingAppNodes = elementChildren(appApplication).associateBy { it.tagName + "|" + androidName(it) }.toMutableMap()

        for (libManifest in libraryManifests) {
            if (!libManifest.isFile) continue
            val parsed = runCatching { builder.parse(applyPlaceholders(libManifest.readText(), allPlaceholders).byteInputStream()) }
            val libDoc = parsed.getOrNull()
            if (libDoc == null) {
                warnings += "Skipping unreadable manifest ${libManifest.path}: ${parsed.exceptionOrNull()?.message}"
                continue
            }
            val libRoot = libDoc.documentElement
            val libPackage = libRoot.getAttribute("package")
            if (libPackage.isNotEmpty()) qualifyClassNames(libRoot, libPackage)
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
                            if (name.isNotEmpty() && name in removedNames) continue
                            val existing = if (name.isNotEmpty()) existingAppNodes[key] else null
                            if (existing != null) {
                                mergeInto(existing, component, appDoc)
                                continue
                            }
                            val imported = appDoc.importNode(component, true) as Element
                            stripToolsAttributes(imported)
                            appApplication.appendChild(imported)
                            if (name.isNotEmpty()) existingAppNodes[key] = imported
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

    /** Adds [incoming]'s missing attributes and new `meta-data` children to [existing]. */
    private fun mergeInto(existing: Element, incoming: Element, doc: Document) {
        val attrs = incoming.attributes
        for (i in 0 until attrs.length) {
            val a = attrs.item(i)
            if (a.namespaceURI == TOOLS_NS || a.nodeName.startsWith("tools:") || a.nodeName.startsWith("xmlns")) continue
            val present = if (a.namespaceURI != null) existing.hasAttributeNS(a.namespaceURI, a.localName) else existing.hasAttribute(a.nodeName)
            if (!present) existing.setAttributeNodeNS(doc.importNode(a, true) as org.w3c.dom.Attr)
        }
        val existingMeta = elementChildren(existing).filter { it.tagName == "meta-data" }.map { androidName(it) }.toMutableSet()
        for (child in elementChildren(incoming)) {
            if (child.tagName != "meta-data") continue
            val name = androidName(child)
            if (toolsNode(child) == "remove" || !existingMeta.add(name)) continue
            val imported = doc.importNode(child, true) as Element
            stripToolsAttributes(imported)
            existing.appendChild(imported)
        }
    }

    /**
     * Fully qualifies relative component class names under `<application>` (and the
     * application's own name and backup agent) against [pkg], as AGP's merger does.
     */
    private fun qualifyClassNames(root: Element, pkg: String) {
        val application = firstChild(root, "application") ?: return
        fun qualify(element: Element, attr: String) {
            val value = element.getAttributeNS(ANDROID_NS, attr)
            if (value.isEmpty() || value.contains("\${")) return
            val qualified = when {
                value.startsWith(".") -> pkg + value
                !value.contains('.') -> "$pkg.$value"
                else -> return
            }
            element.setAttributeNS(ANDROID_NS, "android:$attr", qualified)
        }
        qualify(application, "name")
        qualify(application, "backupAgent")
        for (component in elementChildren(application)) {
            when (component.tagName) {
                "activity", "service", "receiver", "provider" -> qualify(component, "name")
                "activity-alias" -> { qualify(component, "name"); qualify(component, "targetActivity") }
            }
            if (component.tagName == "activity") qualify(component, "parentActivityName")
        }
        for (instrumentation in elementChildren(root).filter { it.tagName == "instrumentation" }) qualify(instrumentation, "name")
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
