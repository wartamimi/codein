package com.codein.ide.workspace

/** "New from template" kinds in the file explorer. */
enum class FileTemplateKind(val title: String, val hint: String) {
    KotlinClass("Kotlin class", "MyClass"),
    JavaClass("Java class", "MyClass"),
    ViewsActivity("Activity + layout", "DetailActivity"),
    ComposeActivity("Compose activity", "DetailActivity"),
    ComposeScreen("Compose screen", "DetailScreen"),
    Fragment("Fragment + layout", "DetailFragment"),
    Layout("Layout XML", "item_row"),
    ValuesXml("Values XML", "dimens"),
}

/** A file to create, relative to the project root, plus an optional manifest activity to register. */
data class TemplateOutput(val files: Map<String, String>, val activityToRegister: String? = null)

/**
 * Starter files for the explorer's "New from template". [parentRelative] is the folder the user
 * long-pressed; the package is derived from it when it is under `src/<set>/java|kotlin/`.
 */
object FileTemplates {

    fun packageOf(parentRelative: String): String? =
        Regex("""src/\w+/(?:java|kotlin)/(.+)$""").find(parentRelative.trimEnd('/'))?.groupValues?.get(1)?.replace('/', '.')

    /** `DetailActivity` → `activity_detail`, `DetailFragment` → `fragment_detail`. */
    fun layoutNameFor(className: String, suffix: String, prefix: String): String {
        val base = className.removeSuffix(suffix).ifEmpty { className }
        return prefix + "_" + base.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), "_").lowercase()
    }

    fun generate(kind: FileTemplateKind, parentRelative: String, rawName: String, namespace: String?): TemplateOutput {
        val name = rawName.trim().removeSuffix(".kt").removeSuffix(".java").removeSuffix(".xml")
        require(name.isNotEmpty() && Regex("[A-Za-z_][A-Za-z0-9_]*").matches(name)) { "Use letters, digits and _ only, starting with a letter" }
        val pkg = packageOf(parentRelative)
        val dir = parentRelative.trimEnd('/')
        val resDir = Regex("""^(.*src/\w+)/""").find("$dir/")?.groupValues?.get(1)?.let { "$it/res" } ?: "app/src/main/res"
        val kotlinDir = dir.contains("/kotlin/") || dir.endsWith("/kotlin")
        // R lives in the namespace package; it only needs an import from other packages.
        val rImport = namespace?.takeIf { it != pkg }?.let { "$it.R" }
        fun kotlin(imports: List<String>, body: String) = source(pkg, imports + listOfNotNull(rImport.takeIf { body.contains("R.") }), body, java = false)
        fun java(imports: List<String>, body: String) = source(pkg, imports + listOfNotNull(rImport.takeIf { body.contains("R.") }), body, java = true)
        return when (kind) {
            FileTemplateKind.KotlinClass -> TemplateOutput(mapOf("$dir/$name.kt" to kotlin(emptyList(), "class $name {\n}\n")))
            FileTemplateKind.JavaClass -> TemplateOutput(mapOf("$dir/$name.java" to java(emptyList(), "public class $name {\n}\n")))
            FileTemplateKind.Layout -> TemplateOutput(mapOf("$resDir/layout/${name.lowercase()}.xml" to linearLayout(null)))
            FileTemplateKind.ValuesXml -> TemplateOutput(mapOf("$resDir/values/${name.lowercase()}.xml" to "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>\n"))
            FileTemplateKind.ComposeScreen -> TemplateOutput(
                mapOf(
                    "$dir/$name.kt" to kotlin(
                        listOf(
                            "androidx.compose.foundation.layout.Arrangement", "androidx.compose.foundation.layout.Column",
                            "androidx.compose.foundation.layout.fillMaxSize", "androidx.compose.foundation.layout.padding",
                            "androidx.compose.material3.MaterialTheme", "androidx.compose.material3.Text",
                            "androidx.compose.runtime.Composable", "androidx.compose.ui.Modifier", "androidx.compose.ui.unit.dp",
                        ),
                        "@Composable\n" +
                            "fun $name(modifier: Modifier = Modifier) {\n" +
                            "    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {\n" +
                            "        Text(\"$name\", style = MaterialTheme.typography.headlineSmall)\n" +
                            "    }\n" +
                            "}\n",
                    ),
                ),
            )
            FileTemplateKind.ComposeActivity -> TemplateOutput(
                mapOf(
                    "$dir/$name.kt" to kotlin(
                        listOf(
                            "android.os.Bundle", "androidx.activity.ComponentActivity", "androidx.activity.compose.setContent",
                            "androidx.activity.enableEdgeToEdge", "androidx.compose.foundation.layout.fillMaxSize",
                            "androidx.compose.foundation.layout.padding", "androidx.compose.material3.MaterialTheme",
                            "androidx.compose.material3.Scaffold", "androidx.compose.material3.Text", "androidx.compose.ui.Modifier",
                        ),
                        "class $name : ComponentActivity() {\n" +
                            "    override fun onCreate(savedInstanceState: Bundle?) {\n" +
                            "        enableEdgeToEdge()\n" +
                            "        super.onCreate(savedInstanceState)\n" +
                            "        setContent {\n" +
                            "            MaterialTheme {\n" +
                            "                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->\n" +
                            "                    Text(\"$name\", modifier = Modifier.padding(padding))\n" +
                            "                }\n" +
                            "            }\n" +
                            "        }\n" +
                            "    }\n" +
                            "}\n",
                    ),
                ),
                activityToRegister = pkg?.let { "$it.$name" } ?: name,
            )
            FileTemplateKind.ViewsActivity -> {
                val layout = layoutNameFor(name, "Activity", "activity")
                val source = if (kotlinDir) {
                    "$dir/$name.kt" to kotlin(
                        listOf("android.os.Bundle", "androidx.appcompat.app.AppCompatActivity"),
                        "class $name : AppCompatActivity() {\n" +
                            "    override fun onCreate(savedInstanceState: Bundle?) {\n" +
                            "        super.onCreate(savedInstanceState)\n" +
                            "        setContentView(R.layout.$layout)\n" +
                            "    }\n" +
                            "}\n",
                    )
                } else {
                    "$dir/$name.java" to java(
                        listOf("android.os.Bundle", "androidx.appcompat.app.AppCompatActivity"),
                        "public class $name extends AppCompatActivity {\n" +
                            "    @Override\n" +
                            "    protected void onCreate(Bundle savedInstanceState) {\n" +
                            "        super.onCreate(savedInstanceState);\n" +
                            "        setContentView(R.layout.$layout);\n" +
                            "    }\n" +
                            "}\n",
                    )
                }
                TemplateOutput(mapOf(source, "$resDir/layout/$layout.xml" to linearLayout(name)), pkg?.let { "$it.$name" } ?: name)
            }
            FileTemplateKind.Fragment -> {
                val layout = layoutNameFor(name, "Fragment", "fragment")
                val source = if (kotlinDir) {
                    "$dir/$name.kt" to kotlin(listOf("androidx.fragment.app.Fragment"), "class $name : Fragment(R.layout.$layout)\n")
                } else {
                    "$dir/$name.java" to java(
                        listOf("androidx.fragment.app.Fragment"),
                        "public class $name extends Fragment {\n" +
                            "    public $name() {\n" +
                            "        super(R.layout.$layout);\n" +
                            "    }\n" +
                            "}\n",
                    )
                }
                TemplateOutput(mapOf(source, "$resDir/layout/$layout.xml" to linearLayout(name)))
            }
        }
    }

    private fun source(pkg: String?, imports: List<String>, body: String, java: Boolean): String = buildString {
        val end = if (java) ";" else ""
        if (pkg != null) append("package ").append(pkg).append(end).append("\n\n")
        imports.distinct().sorted().forEach { append("import ").append(it).append(end).append('\n') }
        if (imports.isNotEmpty()) append('\n')
        append(body)
    }

    /**
     * Registers [activityClass] in the manifest, before `</application>`. Returns the manifest
     * unchanged when the activity is already there.
     */
    fun registerActivity(manifest: String, activityClass: String, appPackage: String?): String {
        val short = if (appPackage != null && activityClass.startsWith("$appPackage.")) activityClass.removePrefix(appPackage) else activityClass
        if (manifest.contains("android:name=\"$short\"") || manifest.contains("android:name=\"$activityClass\"")) return manifest
        val index = manifest.lastIndexOf("</application>")
        if (index < 0) return manifest
        return manifest.substring(0, index) + "    <activity android:name=\"$short\" android:exported=\"false\" />\n    " + manifest.substring(index)
    }

    private fun linearLayout(owner: String?): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        append("<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n")
        append("    android:layout_width=\"match_parent\"\n")
        append("    android:layout_height=\"match_parent\"\n")
        append("    android:orientation=\"vertical\"\n")
        append("    android:padding=\"16dp\">\n\n")
        if (owner != null) {
            append("    <TextView\n")
            append("        android:layout_width=\"wrap_content\"\n")
            append("        android:layout_height=\"wrap_content\"\n")
            append("        android:text=\"$owner\"\n")
            append("        android:textSize=\"22sp\" />\n\n")
        }
        append("</LinearLayout>\n")
    }
}
