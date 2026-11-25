package com.codein.ide.editor.sora

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.codein.ide.core.ui.theme.EditorColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IThemeSource
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/** Languages Codein highlights through TextMate grammars bundled in `assets/textmate`. */
enum class EditorLanguage(val scopeName: String, val displayName: String, val lineComment: String?, val blockComment: Pair<String, String>?) {
    Kotlin("source.kotlin", "Kotlin", "//", "/*" to "*/"),
    Java("source.java", "Java", "//", "/*" to "*/"),
    Xml("text.xml", "XML", null, "<!--" to "-->"),
    Json("source.json", "JSON", null, null),
    Groovy("source.groovy", "Groovy", "//", "/*" to "*/"),
    PlainText("", "Plain text", null, null),
    ;

    companion object {
        fun forFileName(name: String): EditorLanguage {
            val lower = name.lowercase()
            return when {
                lower.endsWith(".kt") || lower.endsWith(".kts") -> Kotlin
                lower.endsWith(".java") -> Java
                lower.endsWith(".xml") || lower.endsWith(".svg") -> Xml
                lower.endsWith(".json") -> Json
                lower.endsWith(".gradle") || lower.endsWith(".groovy") -> Groovy
                else -> PlainText
            }
        }
    }
}

/**
 * Registers TextMate grammars once per process and turns Codein [EditorColorScheme]s into VS Code
 * style TextMate themes so Sora Editor can render them.
 */
object TextMateSetup {
    private val initialized = AtomicBoolean(false)
    private val loadedThemes = mutableSetOf<String>()

    fun ensureInitialized(context: Context) {
        if (initialized.compareAndSet(false, true)) {
            FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(context.applicationContext.assets))
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        }
    }

    /** Loads (once) and activates the TextMate theme generated from [scheme]. */
    @Synchronized
    fun applyTheme(scheme: EditorColorScheme) {
        val registry = ThemeRegistry.getInstance()
        if (scheme.id !in loadedThemes) {
            val json = themeJson(scheme).toString()
            val source = IThemeSource.fromInputStream(json.byteInputStream(), "${scheme.id}.json", null)
            val model = ThemeModel(source, scheme.id).apply { isDark = scheme.isDark }
            registry.loadTheme(model)
            loadedThemes += scheme.id
        }
        registry.setTheme(scheme.id)
    }

    private fun hex(color: Color): String = String.format("#%08X", color.toArgb()).let { argb ->
        // ARGB -> RRGGBBAA as used by VS Code themes.
        "#" + argb.substring(3) + argb.substring(1, 3)
    }

    private fun rule(scopes: List<String>, color: Color, fontStyle: String? = null): JSONObject =
        JSONObject()
            .put("scope", JSONArray(scopes))
            .put("settings", JSONObject().put("foreground", hex(color)).apply { if (fontStyle != null) put("fontStyle", fontStyle) })

    fun themeJson(s: EditorColorScheme): JSONObject {
        val colors = JSONObject()
            .put("editor.background", hex(s.background))
            .put("editor.foreground", hex(s.foreground))
            .put("editorLineNumber.foreground", hex(s.lineNumber))
            .put("editorLineNumber.activeForeground", hex(s.lineNumberActive))
            .put("editor.lineHighlightBackground", hex(s.currentLine))
            .put("editor.selectionBackground", hex(s.selection))
            .put("editorCursor.foreground", hex(s.cursor))
            .put("editorIndentGuide.background", hex(s.indentGuide))
            .put("editorIndentGuide.activeBackground", hex(s.lineNumber))
            .put("editorGutter.background", hex(s.gutterBackground))
        val tokens = JSONArray()
            .put(JSONObject().put("settings", JSONObject().put("foreground", hex(s.foreground)).put("background", hex(s.background))))
            .put(rule(listOf("comment", "punctuation.definition.comment", "comment.block.documentation"), s.comment, "italic"))
            .put(rule(listOf("keyword", "storage.type", "storage.modifier", "keyword.control", "keyword.other", "variable.language.this", "variable.language.super"), s.keyword))
            .put(rule(listOf("string", "string.quoted", "string.template", "punctuation.definition.string"), s.string))
            .put(rule(listOf("constant.character.escape", "constant.language", "constant.other", "support.constant", "variable.other.constant", "variable.other.enummember"), s.constant))
            .put(rule(listOf("constant.numeric"), s.number))
            .put(rule(listOf("entity.name.function", "support.function", "meta.function-call", "entity.name.method"), s.function))
            .put(rule(listOf("entity.name.type", "entity.name.class", "support.class", "support.type", "storage.type.java", "entity.other.inherited-class", "storage.type.generic"), s.type))
            .put(rule(listOf("variable", "variable.parameter", "variable.other", "meta.definition.variable"), s.variable))
            .put(rule(listOf("keyword.operator", "punctuation.accessor", "keyword.operator.arrow"), s.operator))
            .put(rule(listOf("storage.type.annotation", "meta.declaration.annotation", "punctuation.definition.annotation", "entity.name.annotation"), s.annotation))
            .put(rule(listOf("entity.name.tag", "meta.tag", "punctuation.definition.tag"), s.tag))
            .put(rule(listOf("entity.other.attribute-name", "support.type.property-name.json", "meta.structure.dictionary.key.json string"), s.attribute))
            .put(rule(listOf("punctuation", "meta.brace", "punctuation.section", "punctuation.separator", "punctuation.terminator"), s.punctuation))
            .put(rule(listOf("invalid", "invalid.illegal"), s.error))
        return JSONObject()
            .put("name", s.displayName)
            .put("type", if (s.isDark) "dark" else "light")
            .put("colors", colors)
            .put("tokenColors", tokens)
    }
}
