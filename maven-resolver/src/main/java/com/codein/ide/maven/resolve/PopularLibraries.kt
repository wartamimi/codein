package com.codein.ide.maven.resolve

import com.codein.ide.maven.model.MavenRepository

/**
 * Libraries people ask for by their everyday name ("retrofit", "glide", "image loading", "room
 * database") rather than by Maven coordinates. A plain-name query matches these first; the
 * version listed here is only a fallback when the repository's metadata cannot be read.
 */
object PopularLibraries {

    data class Library(
        val group: String,
        val artifact: String,
        val fallbackVersion: String,
        val description: String,
        val keywords: List<String>,
        val repository: MavenRepository = MavenRepository.Central,
        /** Extra coordinates usually added together, such as an annotation processor. */
        val companions: List<String> = emptyList(),
    )

    val all: List<Library> = listOf(
        Library("com.squareup.retrofit2", "retrofit", "2.11.0", "Type-safe HTTP client for REST APIs", listOf("retrofit", "http", "rest", "api", "network", "networking"), companions = listOf("com.squareup.retrofit2:converter-gson")),
        Library("com.squareup.retrofit2", "converter-gson", "2.11.0", "Retrofit JSON converter using Gson", listOf("retrofit", "gson", "converter")),
        Library("com.squareup.okhttp3", "okhttp", "4.12.0", "HTTP client", listOf("okhttp", "http", "network", "networking", "websocket")),
        Library("com.squareup.okhttp3", "logging-interceptor", "4.12.0", "Logs OkHttp requests and responses", listOf("okhttp", "logging", "interceptor", "network")),
        Library("com.google.code.gson", "gson", "2.11.0", "JSON to Java/Kotlin objects and back", listOf("gson", "json", "parse", "parser")),
        Library("com.squareup.moshi", "moshi-kotlin", "1.15.1", "Modern JSON library for Kotlin", listOf("moshi", "json", "parse")),
        Library("org.jetbrains.kotlinx", "kotlinx-serialization-json", "1.7.3", "Kotlin JSON serialization", listOf("serialization", "json", "kotlinx")),
        Library("com.github.bumptech.glide", "glide", "4.16.0", "Image loading and caching for views", listOf("glide", "image", "images", "image loading", "picture", "photo", "load image")),
        Library("io.coil-kt", "coil", "2.7.0", "Image loading for Android backed by coroutines", listOf("coil", "image", "images", "image loading", "load image")),
        Library("io.coil-kt", "coil-compose", "2.7.0", "Image loading for Jetpack Compose (AsyncImage)", listOf("coil", "compose", "image", "asyncimage", "image loading")),
        Library("com.squareup.picasso", "picasso", "2.8", "Simple image loading", listOf("picasso", "image", "image loading")),
        Library("com.airbnb.android", "lottie", "6.5.2", "Lottie JSON animations", listOf("lottie", "animation", "animations")),
        Library("androidx.room", "room-runtime", "2.6.1", "SQLite database with objects (DAO, Entity)", listOf("room", "database", "sqlite", "db", "offline"), MavenRepository.Google, listOf("androidx.room:room-ktx", "androidx.room:room-compiler")),
        Library("androidx.room", "room-ktx", "2.6.1", "Coroutine and Flow support for Room", listOf("room", "database", "coroutines"), MavenRepository.Google),
        Library("androidx.datastore", "datastore-preferences", "1.1.1", "Key-value settings storage (replaces SharedPreferences)", listOf("datastore", "preferences", "settings", "sharedpreferences", "storage"), MavenRepository.Google),
        Library("androidx.navigation", "navigation-compose", "2.8.4", "Screen navigation for Compose", listOf("navigation", "navigate", "screens", "compose", "nav"), MavenRepository.Google),
        Library("androidx.navigation", "navigation-fragment-ktx", "2.8.4", "Screen navigation for fragments", listOf("navigation", "fragment", "nav"), MavenRepository.Google),
        Library("androidx.lifecycle", "lifecycle-viewmodel-ktx", "2.8.7", "ViewModel with viewModelScope", listOf("viewmodel", "lifecycle", "mvvm"), MavenRepository.Google),
        Library("androidx.lifecycle", "lifecycle-viewmodel-compose", "2.8.7", "viewModel() for Compose", listOf("viewmodel", "compose", "lifecycle"), MavenRepository.Google),
        Library("androidx.lifecycle", "lifecycle-livedata-ktx", "2.8.7", "LiveData with coroutines", listOf("livedata", "lifecycle", "mvvm"), MavenRepository.Google),
        Library("org.jetbrains.kotlinx", "kotlinx-coroutines-android", "1.9.0", "Coroutines on Android's main thread", listOf("coroutines", "coroutine", "async", "background", "thread")),
        Library("androidx.recyclerview", "recyclerview", "1.3.2", "Scrolling lists of views", listOf("recyclerview", "list", "lists", "adapter"), MavenRepository.Google),
        Library("androidx.constraintlayout", "constraintlayout", "2.2.0", "Flexible view layouts with constraints", listOf("constraintlayout", "constraint", "layout"), MavenRepository.Google),
        Library("com.google.android.material", "material", "1.12.0", "Material Design components for views", listOf("material", "design", "button", "card", "components"), MavenRepository.Google),
        Library("androidx.swiperefreshlayout", "swiperefreshlayout", "1.1.0", "Pull to refresh", listOf("swipe", "refresh", "pull to refresh"), MavenRepository.Google),
        Library("androidx.viewpager2", "viewpager2", "1.1.0", "Swipeable pages and tabs", listOf("viewpager", "pager", "tabs", "slider", "swipe"), MavenRepository.Google),
        Library("androidx.work", "work-runtime-ktx", "2.10.0", "Background work that survives restarts", listOf("workmanager", "work", "background", "job", "scheduler"), MavenRepository.Google),
        Library("androidx.paging", "paging-runtime-ktx", "3.3.4", "Load large lists page by page", listOf("paging", "pagination", "infinite scroll"), MavenRepository.Google),
        Library("androidx.camera", "camera-camera2", "1.4.0", "CameraX camera access", listOf("camera", "camerax", "photo", "scan"), MavenRepository.Google, listOf("androidx.camera:camera-lifecycle", "androidx.camera:camera-view")),
        Library("androidx.media3", "media3-exoplayer", "1.5.0", "Video and audio player (ExoPlayer)", listOf("exoplayer", "media3", "video", "audio", "player", "music", "stream"), MavenRepository.Google, listOf("androidx.media3:media3-ui")),
        Library("androidx.biometric", "biometric", "1.1.0", "Fingerprint and face unlock", listOf("biometric", "fingerprint", "face", "auth"), MavenRepository.Google),
        Library("androidx.browser", "browser", "1.8.0", "Open web pages in Custom Tabs", listOf("browser", "custom tabs", "web"), MavenRepository.Google),
        Library("androidx.webkit", "webkit", "1.12.1", "Modern WebView features", listOf("webview", "web", "webkit"), MavenRepository.Google),
        Library("androidx.core", "core-splashscreen", "1.0.1", "Splash screen on every Android version", listOf("splash", "splashscreen", "launch screen"), MavenRepository.Google),
        Library("com.google.dagger", "hilt-android", "2.52", "Dependency injection", listOf("hilt", "dagger", "dependency injection", "di", "inject"), MavenRepository.Central, listOf("com.google.dagger:hilt-compiler")),
        Library("io.insert-koin", "koin-android", "4.0.0", "Lightweight dependency injection", listOf("koin", "dependency injection", "di")),
        Library("com.jakewharton.timber", "timber", "5.0.1", "Better logging", listOf("timber", "log", "logging")),
        Library("com.squareup.leakcanary", "leakcanary-android", "2.14", "Finds memory leaks (debug builds)", listOf("leakcanary", "leak", "memory")),
        Library("com.google.zxing", "core", "3.5.3", "QR code and barcode encoding/decoding", listOf("zxing", "qr", "qrcode", "barcode", "scan", "scanner")),
        Library("com.journeyapps", "zxing-android-embedded", "4.3.0", "QR and barcode scanner screen", listOf("zxing", "qr", "qrcode", "barcode", "scanner", "scan")),
        Library("com.github.PhilJay", "MPAndroidChart", "v3.1.0", "Charts and graphs", listOf("chart", "charts", "graph", "mpandroidchart", "pie", "bar chart"), MavenRepository.JitPack),
        Library("com.google.firebase", "firebase-bom", "33.6.0", "Firebase version catalog (add firebase-* without versions)", listOf("firebase", "bom"), MavenRepository.Google),
        Library("com.google.firebase", "firebase-auth", "23.1.0", "Firebase sign-in", listOf("firebase", "auth", "login", "sign in", "authentication"), MavenRepository.Google),
        Library("com.google.firebase", "firebase-firestore", "25.1.1", "Firebase cloud database", listOf("firebase", "firestore", "database", "cloud"), MavenRepository.Google),
        Library("com.google.firebase", "firebase-messaging", "24.1.0", "Push notifications (FCM)", listOf("firebase", "fcm", "push", "notification", "notifications", "messaging"), MavenRepository.Google),
        Library("com.google.android.gms", "play-services-maps", "19.0.0", "Google Maps", listOf("maps", "map", "google maps", "location"), MavenRepository.Google),
        Library("com.google.android.gms", "play-services-location", "21.3.0", "Device location (GPS)", listOf("location", "gps", "fused", "geolocation"), MavenRepository.Google),
        Library("com.google.android.gms", "play-services-ads", "23.5.0", "AdMob ads", listOf("ads", "admob", "ad", "banner", "monetize"), MavenRepository.Google),
        Library("junit", "junit", "4.13.2", "Unit tests", listOf("junit", "test", "testing", "unit test")),
        Library("io.mockk", "mockk", "1.13.13", "Mocking for Kotlin tests", listOf("mockk", "mock", "test", "testing")),
    )

    /** Libraries matching [query] by name or keyword, best first. */
    fun match(query: String): List<Library> {
        val q = query.trim().lowercase()
        if (q.length < 2 || q.contains(':')) return emptyList()
        val words = q.split(Regex("\\s+")).filter { it.length >= 2 }
        return all.mapNotNull { lib ->
            val score = when {
                lib.artifact.lowercase() == q -> 100
                lib.keywords.any { it == q } -> 90
                lib.artifact.lowercase().startsWith(q) -> 80
                lib.keywords.any { k -> k.startsWith(q) } -> 60
                words.isNotEmpty() && words.all { w -> lib.keywords.any { it.contains(w) } || lib.description.lowercase().contains(w) } -> 40
                else -> 0
            }
            if (score > 0) lib to score else null
        }.sortedByDescending { it.second }.map { it.first }
    }
}
