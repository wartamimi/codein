package com.codein.ide.project

enum class ProjectLanguage(val displayName: String) { Kotlin("Kotlin"), Java("Java") }

enum class ProjectTemplate(val displayName: String, val description: String, val supportsJava: Boolean) {
    ComposeMaterial3("Compose Material 3", "Single-activity Jetpack Compose app with a Material 3 theme and dynamic color.", supportsJava = false),
    EmptyViewsActivity("Empty Views Activity", "Classic AppCompat activity with an XML layout and Material Components theme.", supportsJava = true),
    BottomNavigation("Bottom Navigation", "Views app with a Material BottomNavigationView switching between three screens.", supportsJava = true),
}

/** Everything the wizard collects. */
data class ProjectSpec(
    val name: String,
    val packageName: String,
    val language: ProjectLanguage,
    val template: ProjectTemplate,
    val minSdk: Int,
    val targetSdk: Int,
) {
    val effectiveLanguage: ProjectLanguage get() = if (!template.supportsJava) ProjectLanguage.Kotlin else language

    companion object {
        val packageRegex = Regex("""^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$""")
        private val reserved = setOf("abstract", "class", "default", "do", "for", "if", "import", "int", "new", "package", "public", "return", "static", "switch", "this", "void", "fun", "val", "var", "object", "in", "is", "as", "when")

        fun validatePackage(pkg: String): String? = when {
            pkg.isBlank() -> "Package name is required"
            !packageRegex.matches(pkg) -> "Use at least two segments, e.g. com.example.app"
            pkg.split('.').any { it in reserved } -> "A segment is a reserved word"
            else -> null
        }

        fun validateName(name: String): String? = when {
            name.isBlank() -> "Project name is required"
            name.length > 50 -> "Keep it under 50 characters"
            !Regex("""^[\w .\-]+$""").matches(name) -> "Use letters, digits, spaces, dots, dashes or underscores"
            else -> null
        }

        fun suggestPackage(name: String): String {
            val slug = name.lowercase().replace(Regex("[^a-z0-9]"), "").ifEmpty { "app" }
            return "com.example." + if (slug.first().isDigit()) "app$slug" else slug
        }
    }
}

/**
 * Generates complete, buildable project sources for each template. Dependency versions are
 * pinned explicitly (no BOM) because the on-device Maven resolver resolves exact coordinates.
 */
object ProjectTemplates {

    private const val D = "$"

    /** Adds `implementation("…")` lines to the `dependencies` block of a module build script. */
    fun withDependencies(buildScript: String, dependencies: List<String>): String {
        val marker = "dependencies {\n"
        val index = buildScript.lastIndexOf(marker)
        val existing = buildScript.substring(maxOf(index, 0))
        val lines = dependencies.filter { d -> !existing.contains("\"$d\"") }.joinToString("") { "    implementation(\"$it\")\n" }
        if (lines.isEmpty()) return buildScript
        return if (index < 0) buildScript + "\ndependencies {\n$lines}\n" else buildScript.substring(0, index + marker.length) + lines + buildScript.substring(index + marker.length)
    }

    fun generate(spec: ProjectSpec): Map<String, String> {
        val files = LinkedHashMap<String, String>()
        val lang = spec.effectiveLanguage
        val pkgPath = spec.packageName.replace('.', '/')
        val srcDir = if (lang == ProjectLanguage.Kotlin) "app/src/main/kotlin/$pkgPath" else "app/src/main/java/$pkgPath"
        val compose = spec.template == ProjectTemplate.ComposeMaterial3

        files["settings.gradle.kts"] = settings(spec)
        files["build.gradle.kts"] = rootBuild(lang)
        files["gradle.properties"] = "org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8\nandroid.useAndroidX=true\nandroid.nonTransitiveRClass=true\n"
        files[".gitignore"] = ".gradle/\nbuild/\n.idea/\nlocal.properties\n*.keystore\n*.jks\n"
        files["app/proguard-rules.pro"] = "# Add project specific ProGuard rules here.\n"
        files["app/build.gradle.kts"] = appBuild(spec, lang, compose)
        files["app/src/main/AndroidManifest.xml"] = manifest(spec, compose)
        files["app/src/main/res/values/strings.xml"] = strings(spec)
        files["app/src/main/res/values/colors.xml"] = colors()
        files["app/src/main/res/values/themes.xml"] = themes(spec, compose)
        files["app/src/main/res/drawable/ic_launcher_foreground.xml"] = launcherForeground()
        files["app/src/main/res/drawable/ic_launcher_background.xml"] = launcherBackground()
        files["app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml"] = adaptiveIcon()
        files["app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml"] = adaptiveIcon()

        when (spec.template) {
            ProjectTemplate.ComposeMaterial3 -> {
                files["$srcDir/MainActivity.kt"] = composeActivity(spec)
                files["$srcDir/ui/theme/Theme.kt"] = composeTheme(spec)
            }
            ProjectTemplate.EmptyViewsActivity -> {
                files["app/src/main/res/layout/activity_main.xml"] = emptyLayout()
                files[if (lang == ProjectLanguage.Kotlin) "$srcDir/MainActivity.kt" else "$srcDir/MainActivity.java"] =
                    if (lang == ProjectLanguage.Kotlin) emptyActivityKotlin(spec) else emptyActivityJava(spec)
            }
            ProjectTemplate.BottomNavigation -> {
                files["app/src/main/res/layout/activity_main.xml"] = bottomNavLayout()
                files["app/src/main/res/menu/bottom_nav_menu.xml"] = bottomNavMenu()
                files["app/src/main/res/drawable/ic_home.xml"] = icon("M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z")
                files["app/src/main/res/drawable/ic_dashboard.xml"] = icon("M3,13h8L11,3L3,3v10zM3,21h8v-6L3,15v6zM13,21h8L21,11h-8v10zM13,3v6h8L21,3h-8z")
                files["app/src/main/res/drawable/ic_notifications.xml"] = icon("M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.9,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32L13.5,4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z")
                files[if (lang == ProjectLanguage.Kotlin) "$srcDir/MainActivity.kt" else "$srcDir/MainActivity.java"] =
                    if (lang == ProjectLanguage.Kotlin) bottomNavKotlin(spec) else bottomNavJava(spec)
            }
        }
        return files
    }

    private fun settings(spec: ProjectSpec) = """
        pluginManagement {
            repositories {
                google()
                mavenCentral()
                gradlePluginPortal()
            }
        }
        dependencyResolutionManagement {
            repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            repositories {
                google()
                mavenCentral()
            }
        }

        rootProject.name = "${spec.name}"
        include(":app")
    """.trimIndent() + "\n"

    private fun rootBuild(lang: ProjectLanguage) = buildString {
        append("plugins {\n")
        append("    id(\"com.android.application\") version \"8.9.1\" apply false\n")
        if (lang == ProjectLanguage.Kotlin) {
            append("    id(\"org.jetbrains.kotlin.android\") version \"2.1.21\" apply false\n")
            append("    id(\"org.jetbrains.kotlin.plugin.compose\") version \"2.1.21\" apply false\n")
        }
        append("}\n")
    }

    private fun appBuild(spec: ProjectSpec, lang: ProjectLanguage, compose: Boolean) = buildString {
        append("plugins {\n    id(\"com.android.application\")\n")
        if (lang == ProjectLanguage.Kotlin) append("    id(\"org.jetbrains.kotlin.android\")\n")
        if (compose) append("    id(\"org.jetbrains.kotlin.plugin.compose\")\n")
        append("}\n\n")
        append(
            """
            android {
                namespace = "${spec.packageName}"
                compileSdk = ${spec.targetSdk}

                defaultConfig {
                    applicationId = "${spec.packageName}"
                    minSdk = ${spec.minSdk}
                    targetSdk = ${spec.targetSdk}
                    versionCode = 1
                    versionName = "1.0"
                }

                buildTypes {
                    release {
                        isMinifyEnabled = false
                        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                    }
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_1_8
                    targetCompatibility = JavaVersion.VERSION_1_8
                }
            """.trimIndent(),
        )
        append('\n')
        if (compose) append("\n    buildFeatures {\n        compose = true\n    }\n")
        append("}\n\n")
        if (lang == ProjectLanguage.Kotlin) append("kotlin {\n    jvmToolchain(17)\n}\n\n")
        append("dependencies {\n")
        if (compose) {
            append("    implementation(\"androidx.core:core-ktx:1.16.0\")\n")
            append("    implementation(\"androidx.lifecycle:lifecycle-runtime-ktx:2.9.0\")\n")
            append("    implementation(\"androidx.activity:activity-compose:1.10.1\")\n")
            append("    implementation(\"androidx.compose.ui:ui:1.8.1\")\n")
            append("    implementation(\"androidx.compose.ui:ui-graphics:1.8.1\")\n")
            append("    implementation(\"androidx.compose.ui:ui-tooling-preview:1.8.1\")\n")
            append("    implementation(\"androidx.compose.material3:material3:1.3.2\")\n")
        } else {
            if (lang == ProjectLanguage.Kotlin) append("    implementation(\"androidx.core:core-ktx:1.16.0\")\n")
            append("    implementation(\"androidx.appcompat:appcompat:1.7.0\")\n")
            append("    implementation(\"com.google.android.material:material:1.12.0\")\n")
            append("    implementation(\"androidx.constraintlayout:constraintlayout:2.2.1\")\n")
        }
        append("}\n")
    }

    private fun manifest(spec: ProjectSpec, compose: Boolean) = """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">

            <application
                android:allowBackup="true"
                android:icon="@mipmap/ic_launcher"
                android:roundIcon="@mipmap/ic_launcher_round"
                android:label="@string/app_name"
                android:supportsRtl="true"
                android:theme="@style/Theme.${themeName(spec)}">
                <activity
                    android:name=".MainActivity"
                    android:exported="true"${if (compose) "\n                    android:windowSoftInputMode=\"adjustResize\"" else ""}>
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
            </application>

        </manifest>
    """.trimIndent() + "\n"

    private fun themeName(spec: ProjectSpec): String =
        spec.name.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }.joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
            .ifEmpty { "App" }.let { if (it.first().isDigit()) "App$it" else it }

    private fun strings(spec: ProjectSpec) = buildString {
        append("<resources>\n")
        append("    <string name=\"app_name\">").append(spec.name.replace("&", "&amp;").replace("<", "&lt;").replace("'", "\\'")).append("</string>\n")
        if (spec.template == ProjectTemplate.BottomNavigation) {
            append("    <string name=\"title_home\">Home</string>\n")
            append("    <string name=\"title_dashboard\">Dashboard</string>\n")
            append("    <string name=\"title_notifications\">Notifications</string>\n")
        }
        if (spec.template != ProjectTemplate.ComposeMaterial3) append("    <string name=\"hello\">Hello from Codein!</string>\n")
        append("</resources>\n")
    }

    private fun colors() = """
        <?xml version="1.0" encoding="utf-8"?>
        <resources>
            <color name="brand_primary">#FF6750A4</color>
            <color name="launcher_background">#FF1E222E</color>
            <color name="white">#FFFFFFFF</color>
        </resources>
    """.trimIndent() + "\n"

    private fun themes(spec: ProjectSpec, compose: Boolean) = if (compose) """
        <?xml version="1.0" encoding="utf-8"?>
        <resources>
            <style name="Theme.${themeName(spec)}" parent="android:Theme.Material.Light.NoActionBar" />
        </resources>
    """.trimIndent() + "\n" else """
        <?xml version="1.0" encoding="utf-8"?>
        <resources>
            <style name="Theme.${themeName(spec)}" parent="Theme.Material3.DayNight.NoActionBar">
                <item name="colorPrimary">@color/brand_primary</item>
            </style>
        </resources>
    """.trimIndent() + "\n"

    private fun launcherForeground() = """
        <?xml version="1.0" encoding="utf-8"?>
        <vector xmlns:android="http://schemas.android.com/apk/res/android"
            android:width="108dp"
            android:height="108dp"
            android:viewportWidth="108"
            android:viewportHeight="108">
            <path
                android:fillColor="@color/white"
                android:pathData="M44,38 L34,54 L44,70 L48,67 L40,54 L48,41 Z M64,38 L60,41 L68,54 L60,67 L64,70 L74,54 Z" />
        </vector>
    """.trimIndent() + "\n"

    private fun launcherBackground() = """
        <?xml version="1.0" encoding="utf-8"?>
        <vector xmlns:android="http://schemas.android.com/apk/res/android"
            android:width="108dp"
            android:height="108dp"
            android:viewportWidth="108"
            android:viewportHeight="108">
            <path android:fillColor="@color/launcher_background" android:pathData="M0,0h108v108h-108z" />
        </vector>
    """.trimIndent() + "\n"

    private fun adaptiveIcon() = """
        <?xml version="1.0" encoding="utf-8"?>
        <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
            <background android:drawable="@drawable/ic_launcher_background" />
            <foreground android:drawable="@drawable/ic_launcher_foreground" />
        </adaptive-icon>
    """.trimIndent() + "\n"

    private fun icon(path: String) = """
        <?xml version="1.0" encoding="utf-8"?>
        <vector xmlns:android="http://schemas.android.com/apk/res/android"
            android:width="24dp"
            android:height="24dp"
            android:viewportWidth="24"
            android:viewportHeight="24"
            android:tint="?attr/colorControlNormal">
            <path android:fillColor="@android:color/white" android:pathData="$path" />
        </vector>
    """.trimIndent() + "\n"

    // ------------------------------------------------------------------ Compose

    private fun composeActivity(spec: ProjectSpec) = """
        package ${spec.packageName}

        import android.os.Bundle
        import androidx.activity.ComponentActivity
        import androidx.activity.compose.setContent
        import androidx.activity.enableEdgeToEdge
        import androidx.compose.foundation.layout.Arrangement
        import androidx.compose.foundation.layout.Column
        import androidx.compose.foundation.layout.fillMaxSize
        import androidx.compose.foundation.layout.padding
        import androidx.compose.material3.Button
        import androidx.compose.material3.MaterialTheme
        import androidx.compose.material3.Scaffold
        import androidx.compose.material3.Text
        import androidx.compose.runtime.Composable
        import androidx.compose.runtime.getValue
        import androidx.compose.runtime.mutableIntStateOf
        import androidx.compose.runtime.saveable.rememberSaveable
        import androidx.compose.runtime.setValue
        import androidx.compose.ui.Alignment
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.ui.unit.dp
        import ${spec.packageName}.ui.theme.AppTheme

        class MainActivity : ComponentActivity() {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                enableEdgeToEdge()
                setContent {
                    AppTheme {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            Greeting(name = "Codein", modifier = Modifier.padding(innerPadding))
                        }
                    }
                }
            }
        }

        @Composable
        fun Greeting(name: String, modifier: Modifier = Modifier) {
            var taps by rememberSaveable { mutableIntStateOf(0) }
            Column(
                modifier = modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "Hello ${D}name!", style = MaterialTheme.typography.headlineMedium)
                Text(text = "Tapped ${D}taps times", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { taps++ }) { Text("Tap me") }
            }
        }

        @Preview(showBackground = true)
        @Composable
        fun GreetingPreview() {
            AppTheme { Greeting("Android") }
        }
    """.trimIndent() + "\n"

    private fun composeTheme(spec: ProjectSpec) = """
        package ${spec.packageName}.ui.theme

        import android.os.Build
        import androidx.compose.foundation.isSystemInDarkTheme
        import androidx.compose.material3.MaterialTheme
        import androidx.compose.material3.darkColorScheme
        import androidx.compose.material3.dynamicDarkColorScheme
        import androidx.compose.material3.dynamicLightColorScheme
        import androidx.compose.material3.lightColorScheme
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.graphics.Color
        import androidx.compose.ui.platform.LocalContext

        private val DarkColors = darkColorScheme(primary = Color(0xFFD0BCFF), secondary = Color(0xFFCCC2DC), tertiary = Color(0xFFEFB8C8))
        private val LightColors = lightColorScheme(primary = Color(0xFF6650A4), secondary = Color(0xFF625B71), tertiary = Color(0xFF7D5260))

        @Composable
        fun AppTheme(
            darkTheme: Boolean = isSystemInDarkTheme(),
            dynamicColor: Boolean = true,
            content: @Composable () -> Unit,
        ) {
            val colorScheme = when {
                dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val context = LocalContext.current
                    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                darkTheme -> DarkColors
                else -> LightColors
            }
            MaterialTheme(colorScheme = colorScheme, content = content)
        }
    """.trimIndent() + "\n"

    // ------------------------------------------------------------------ Empty Views Activity

    private fun emptyLayout() = """
        <?xml version="1.0" encoding="utf-8"?>
        <androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            android:id="@+id/main"
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <TextView
                android:id="@+id/message"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/hello"
                android:textAppearance="?attr/textAppearanceHeadlineSmall"
                app:layout_constraintBottom_toTopOf="@id/button"
                app:layout_constraintEnd_toEndOf="parent"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintTop_toTopOf="parent"
                app:layout_constraintVertical_chainStyle="packed" />

            <com.google.android.material.button.MaterialButton
                android:id="@+id/button"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                android:text="Tap me"
                app:layout_constraintBottom_toBottomOf="parent"
                app:layout_constraintEnd_toEndOf="parent"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintTop_toBottomOf="@id/message" />

        </androidx.constraintlayout.widget.ConstraintLayout>
    """.trimIndent() + "\n"

    private fun emptyActivityKotlin(spec: ProjectSpec) = """
        package ${spec.packageName}

        import android.os.Bundle
        import android.widget.Button
        import android.widget.TextView
        import androidx.appcompat.app.AppCompatActivity

        class MainActivity : AppCompatActivity() {

            private var taps = 0

            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContentView(R.layout.activity_main)
                taps = savedInstanceState?.getInt(KEY_TAPS) ?: 0
                val message = findViewById<TextView>(R.id.message)
                findViewById<Button>(R.id.button).setOnClickListener {
                    taps++
                    message.text = "Tapped ${D}taps times"
                }
            }

            override fun onSaveInstanceState(outState: Bundle) {
                super.onSaveInstanceState(outState)
                outState.putInt(KEY_TAPS, taps)
            }

            private companion object {
                const val KEY_TAPS = "taps"
            }
        }
    """.trimIndent() + "\n"

    private fun emptyActivityJava(spec: ProjectSpec) = """
        package ${spec.packageName};

        import android.os.Bundle;
        import android.widget.Button;
        import android.widget.TextView;

        import androidx.appcompat.app.AppCompatActivity;

        public class MainActivity extends AppCompatActivity {

            private static final String KEY_TAPS = "taps";
            private int taps;

            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_main);
                taps = savedInstanceState != null ? savedInstanceState.getInt(KEY_TAPS) : 0;
                final TextView message = findViewById(R.id.message);
                Button button = findViewById(R.id.button);
                button.setOnClickListener(v -> {
                    taps++;
                    message.setText("Tapped " + taps + " times");
                });
            }

            @Override
            protected void onSaveInstanceState(Bundle outState) {
                super.onSaveInstanceState(outState);
                outState.putInt(KEY_TAPS, taps);
            }
        }
    """.trimIndent() + "\n"

    // ------------------------------------------------------------------ Bottom Navigation

    private fun bottomNavLayout() = """
        <?xml version="1.0" encoding="utf-8"?>
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:orientation="vertical">

            <TextView
                android:id="@+id/screen_title"
                android:layout_width="match_parent"
                android:layout_height="0dp"
                android:layout_weight="1"
                android:gravity="center"
                android:text="@string/title_home"
                android:textAppearance="?attr/textAppearanceHeadlineMedium" />

            <com.google.android.material.bottomnavigation.BottomNavigationView
                android:id="@+id/nav_view"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                app:menu="@menu/bottom_nav_menu" />

        </LinearLayout>
    """.trimIndent() + "\n"

    private fun bottomNavMenu() = """
        <?xml version="1.0" encoding="utf-8"?>
        <menu xmlns:android="http://schemas.android.com/apk/res/android">
            <item
                android:id="@+id/navigation_home"
                android:icon="@drawable/ic_home"
                android:title="@string/title_home" />
            <item
                android:id="@+id/navigation_dashboard"
                android:icon="@drawable/ic_dashboard"
                android:title="@string/title_dashboard" />
            <item
                android:id="@+id/navigation_notifications"
                android:icon="@drawable/ic_notifications"
                android:title="@string/title_notifications" />
        </menu>
    """.trimIndent() + "\n"

    private fun bottomNavKotlin(spec: ProjectSpec) = """
        package ${spec.packageName}

        import android.os.Bundle
        import android.widget.TextView
        import androidx.appcompat.app.AppCompatActivity
        import com.google.android.material.bottomnavigation.BottomNavigationView

        class MainActivity : AppCompatActivity() {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContentView(R.layout.activity_main)
                val title = findViewById<TextView>(R.id.screen_title)
                val nav = findViewById<BottomNavigationView>(R.id.nav_view)
                nav.setOnItemSelectedListener { item ->
                    title.setText(
                        when (item.itemId) {
                            R.id.navigation_dashboard -> R.string.title_dashboard
                            R.id.navigation_notifications -> R.string.title_notifications
                            else -> R.string.title_home
                        },
                    )
                    true
                }
                if (savedInstanceState == null) nav.selectedItemId = R.id.navigation_home
            }
        }
    """.trimIndent() + "\n"

    private fun bottomNavJava(spec: ProjectSpec) = """
        package ${spec.packageName};

        import android.os.Bundle;
        import android.widget.TextView;

        import androidx.appcompat.app.AppCompatActivity;

        import com.google.android.material.bottomnavigation.BottomNavigationView;

        public class MainActivity extends AppCompatActivity {
            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_main);
                final TextView title = findViewById(R.id.screen_title);
                BottomNavigationView nav = findViewById(R.id.nav_view);
                nav.setOnItemSelectedListener(item -> {
                    int id = item.getItemId();
                    if (id == R.id.navigation_dashboard) {
                        title.setText(R.string.title_dashboard);
                    } else if (id == R.id.navigation_notifications) {
                        title.setText(R.string.title_notifications);
                    } else {
                        title.setText(R.string.title_home);
                    }
                    return true;
                });
                if (savedInstanceState == null) {
                    nav.setSelectedItemId(R.id.navigation_home);
                }
            }
        }
    """.trimIndent() + "\n"
}
