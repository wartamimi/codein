package com.codein.ide.editor.sora

import java.io.File

/**
 * Words offered by the editor's completion popup on top of the identifiers already in the open
 * file: the language's keywords, the Android and Kotlin/Java classes people type most, and the
 * symbols declared anywhere in the project ([projectSymbols]).
 */
object CompletionWords {

    private val kotlinKeywords = listOf(
        "abstract", "annotation", "as", "break", "by", "catch", "class", "companion", "const", "constructor",
        "continue", "crossinline", "data", "do", "else", "enum", "external", "false", "final", "finally", "for",
        "fun", "get", "if", "import", "in", "infix", "init", "inline", "inner", "interface", "internal", "is",
        "lateinit", "noinline", "null", "object", "open", "operator", "out", "override", "package", "private",
        "protected", "public", "reified", "return", "sealed", "set", "super", "suspend", "tailrec", "this",
        "throw", "true", "try", "typealias", "val", "value", "var", "vararg", "when", "where", "while",
    )

    private val javaKeywords = listOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "continue",
        "default", "do", "double", "else", "enum", "extends", "false", "final", "finally", "float", "for", "if",
        "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "null", "package",
        "private", "protected", "public", "record", "return", "short", "static", "super", "switch",
        "synchronized", "this", "throw", "throws", "transient", "true", "try", "var", "void", "volatile", "while",
    )

    private val kotlinLibrary = listOf(
        "String", "Int", "Long", "Boolean", "Float", "Double", "Char", "Unit", "Any", "Nothing", "List", "MutableList",
        "Map", "MutableMap", "Set", "MutableSet", "Array", "IntArray", "Pair", "Triple", "Result", "Sequence",
        "listOf", "mutableListOf", "mapOf", "mutableMapOf", "setOf", "mutableSetOf", "emptyList", "emptyMap",
        "arrayOf", "println", "require", "requireNotNull", "check", "error", "lazy", "repeat", "buildString",
        "let", "also", "apply", "run", "with", "takeIf", "takeUnless", "forEach", "map", "filter", "first",
        "firstOrNull", "last", "lastOrNull", "joinToString", "isEmpty", "isNotEmpty", "isNullOrEmpty", "isBlank",
        "isNotBlank", "toInt", "toIntOrNull", "toDouble", "toDoubleOrNull", "toString", "toList", "toMutableList",
        "sortedBy", "groupBy", "associate", "sumOf", "count", "contains", "launch", "async", "withContext",
        "Dispatchers", "CoroutineScope", "delay", "Flow", "StateFlow", "MutableStateFlow", "collect", "asStateFlow",
        "viewModelScope", "lifecycleScope",
    )

    private val javaLibrary = listOf(
        "String", "Integer", "Long", "Boolean", "Float", "Double", "Character", "Object", "List", "ArrayList",
        "Map", "HashMap", "Set", "HashSet", "Arrays", "Collections", "StringBuilder", "Math", "System", "Thread",
        "Runnable", "Exception", "RuntimeException", "IllegalArgumentException", "IllegalStateException",
        "Override", "Nullable", "NonNull", "Locale", "Objects", "Optional",
    )

    private val android = listOf(
        "Activity", "AppCompatActivity", "ComponentActivity", "Fragment", "Bundle", "Intent", "Context", "View",
        "ViewGroup", "TextView", "Button", "EditText", "ImageView", "RecyclerView", "LinearLayout",
        "ConstraintLayout", "FrameLayout", "Toast", "Log", "Handler", "Looper", "SharedPreferences",
        "LayoutInflater", "ViewModel", "LiveData", "MutableLiveData", "R", "onCreate", "onStart", "onResume",
        "onPause", "onStop", "onDestroy", "onCreateView", "onViewCreated", "setContentView", "findViewById",
        "setOnClickListener", "getString", "startActivity", "makeText", "LENGTH_SHORT", "LENGTH_LONG",
        "BottomNavigationView", "NavController", "MaterialButton", "Snackbar",
    )

    private val compose = listOf(
        "Composable", "Preview", "setContent", "remember", "rememberSaveable", "mutableStateOf", "mutableIntStateOf",
        "derivedStateOf", "LaunchedEffect", "DisposableEffect", "collectAsState", "collectAsStateWithLifecycle",
        "Modifier", "Column", "Row", "Box", "Spacer", "LazyColumn", "LazyRow", "items", "Text", "Button",
        "TextButton", "OutlinedButton", "IconButton", "Icon", "Icons", "Image", "Card", "Surface", "Scaffold",
        "TopAppBar", "CenterAlignedTopAppBar", "NavigationBar", "NavigationBarItem", "FloatingActionButton",
        "TextField", "OutlinedTextField", "Checkbox", "Switch", "Slider", "AlertDialog", "MaterialTheme",
        "Alignment", "Arrangement", "padding", "fillMaxSize", "fillMaxWidth", "fillMaxHeight", "height", "width",
        "size", "background", "clickable", "dp", "sp", "Color", "FontWeight", "TextAlign", "PaddingValues",
        "getValue", "setValue", "enableEdgeToEdge",
    )

    private val xml = listOf(
        "LinearLayout", "FrameLayout", "RelativeLayout", "ConstraintLayout", "ScrollView", "TextView", "Button",
        "EditText", "ImageView", "CheckBox", "Switch", "RecyclerView", "com.google.android.material.button.MaterialButton",
        "androidx.constraintlayout.widget.ConstraintLayout", "android:id", "android:layout_width",
        "android:layout_height", "android:orientation", "android:text", "android:textSize", "android:textColor",
        "android:padding", "android:layout_margin", "android:gravity", "android:layout_gravity", "android:hint",
        "android:src", "android:background", "android:visibility", "android:inputType", "android:onClick",
        "match_parent", "wrap_content", "vertical", "horizontal", "center", "center_horizontal",
        "center_vertical", "app:layout_constraintTop_toTopOf", "app:layout_constraintBottom_toBottomOf",
        "app:layout_constraintStart_toStartOf", "app:layout_constraintEnd_toEndOf", "parent", "@string/",
        "@color/", "@dimen/", "@drawable/", "@+id/", "?attr/", "tools:context",
    )

    /** Built-in words for [language], without project symbols. */
    fun builtIn(language: EditorLanguage): List<String> = when (language) {
        EditorLanguage.Kotlin -> kotlinKeywords + kotlinLibrary + android + compose
        EditorLanguage.Java -> javaKeywords + javaLibrary + android
        EditorLanguage.Xml -> xml
        EditorLanguage.Groovy -> listOf("dependencies", "implementation", "android", "defaultConfig", "plugins", "id", "namespace")
        EditorLanguage.Json, EditorLanguage.PlainText -> emptyList()
    }

    private val declaration = Regex(
        """\b(?:class|interface|object|enum\s+class|data\s+class|sealed\s+class|fun|val|var|typealias|record|@interface|enum)\s+(?:<[^>]*>\s*)?([A-Za-z_][A-Za-z0-9_]*)""",
    )
    private val javaMember = Regex("""\b(?:public|private|protected|static|final)\s+[\w<>\[\], ?]+\s+([a-zA-Z_][A-Za-z0-9_]*)\s*[(=;]""")
    private val resourceName = Regex("""name="([A-Za-z_][A-Za-z0-9_.]*)"""")
    private val viewId = Regex("""@\+id/([A-Za-z_][A-Za-z0-9_]*)""")

    /**
     * Declared names in the project's own sources and resource names/ids, so `MainViewModel`,
     * `titleText` or `app_name` complete from any file. Reads at most [maxFiles] files.
     */
    fun projectSymbols(projectDir: File, maxFiles: Int = 800): List<String> {
        val src = File(projectDir, "app/src/main")
        if (!src.isDirectory) return emptyList()
        val out = LinkedHashSet<String>()
        src.walkTopDown()
            .onEnter { it.name != "build" }
            .filter { it.isFile && it.length() < 512_000 && (it.extension == "kt" || it.extension == "java" || it.extension == "xml") }
            .take(maxFiles)
            .forEach { file ->
                val text = runCatching { file.readText() }.getOrNull() ?: return@forEach
                if (file.extension == "xml") {
                    resourceName.findAll(text).forEach { out += it.groupValues[1].replace('.', '_') }
                    viewId.findAll(text).forEach { out += it.groupValues[1] }
                    if (file.parentFile?.name?.startsWith("layout") == true) out += file.nameWithoutExtension
                } else {
                    declaration.findAll(text).forEach { out += it.groupValues[1] }
                    if (file.extension == "java") javaMember.findAll(text).forEach { out += it.groupValues[1] }
                }
            }
        return out.filter { it.length > 1 }.take(4000)
    }
}
