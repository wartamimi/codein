package com.codein.ide.maven.pom

import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory

data class PomDependency(
    val group: String,
    val artifact: String,
    val version: String?,
    val scope: String,
    val type: String?,
    val classifier: String?,
    val optional: Boolean,
    val exclusions: Set<String>,
) {
    val groupArtifact: String get() = "$group:$artifact"
}

data class PomParent(val group: String, val artifact: String, val version: String)

/** Raw, un-interpolated view of a POM file. */
data class RawPom(
    val group: String?,
    val artifact: String,
    val version: String?,
    val packaging: String?,
    val parent: PomParent?,
    val properties: Map<String, String>,
    val dependencyManagement: List<PomDependency>,
    val dependencies: List<PomDependency>,
)

/** Fully interpolated POM after parent inheritance and BOM imports. */
data class EffectivePom(
    val group: String,
    val artifact: String,
    val version: String,
    val packaging: String,
    val dependencies: List<PomDependency>,
    val managedVersions: Map<String, String>,
)

object PomParser {

    fun parse(xml: String): RawPom {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
        }
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
        val project = doc.documentElement
        val parent = child(project, "parent")?.let { p ->
            PomParent(text(p, "groupId") ?: "", text(p, "artifactId") ?: "", text(p, "version") ?: "")
        }
        val properties = mutableMapOf<String, String>()
        child(project, "properties")?.let { props ->
            children(props).forEach { properties[it.tagName] = it.textContent.trim() }
        }
        val management = child(project, "dependencyManagement")?.let { dm -> child(dm, "dependencies") }
            ?.let(::parseDependencies).orEmpty()
        val dependencies = child(project, "dependencies")?.let(::parseDependencies).orEmpty()
        return RawPom(
            group = text(project, "groupId"),
            artifact = text(project, "artifactId") ?: "",
            version = text(project, "version"),
            packaging = text(project, "packaging"),
            parent = parent,
            properties = properties,
            dependencyManagement = management,
            dependencies = dependencies,
        )
    }

    private fun parseDependencies(element: Element): List<PomDependency> =
        children(element).filter { it.tagName == "dependency" }.map { d ->
            val exclusions = child(d, "exclusions")?.let { ex ->
                children(ex).map { "${text(it, "groupId")}:${text(it, "artifactId")}" }.toSet()
            }.orEmpty()
            PomDependency(
                group = text(d, "groupId") ?: "",
                artifact = text(d, "artifactId") ?: "",
                version = text(d, "version"),
                scope = text(d, "scope") ?: "compile",
                type = text(d, "type"),
                classifier = text(d, "classifier"),
                optional = text(d, "optional")?.equals("true", ignoreCase = true) == true,
                exclusions = exclusions,
            )
        }

    private fun children(element: Element): List<Element> {
        val list = mutableListOf<Element>()
        val nodes = element.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.nodeType == Node.ELEMENT_NODE) list += n as Element
        }
        return list
    }

    private fun child(element: Element, tag: String): Element? = children(element).firstOrNull { it.tagName == tag }

    private fun text(element: Element, tag: String): String? = child(element, tag)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private val PROPERTY = Regex("""\$\{([^}]+)}""")

    /** Replaces `${...}` references using [props]; unresolved references are left intact. */
    fun interpolate(value: String?, props: Map<String, String>): String? {
        if (value == null) return null
        var current: String = value
        repeat(5) {
            val next = PROPERTY.replace(current) { m -> props[m.groupValues[1]] ?: m.value }
            if (next == current) return next
            current = next
        }
        return current
    }
}
