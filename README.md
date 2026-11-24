# Codein

Codein is an on-device Android IDE written in Kotlin and Jetpack Compose (Material 3). It edits, builds, signs and installs Android apps directly on a phone or tablet.

## Architecture

Clean Architecture with MVI view models, Hilt for DI, Room for project metadata and DataStore for preferences and encrypted secrets.

| Module | What it contains |
| --- | --- |
| `:app` | Home, new-project wizard, adaptive workspace (phone drawer / tablet three-pane), build console, Logcat, settings, navigation, Room database |
| `:core-ui` | Theme (dark palette, 5 editor schemes, Material You), bundled coding fonts, vector icon set, shared components, editor preferences |
| `:editor` | Sora-based code editor, Extra Keys bar, find & replace (regex), project search, command palette, preferences sheet |
| `:compiler-engine` | Build pipeline: manifest merger, AAPT2 compile/link, ECJ (Java), kotlinc (Kotlin), D8, APK packaging, toolchain management |
| `:maven-resolver` | Maven Central / Google Maven resolution with transitive dependencies, AAR extraction and a dependency explorer |
| `:aab-signer` | Keystore vault (generate/import, Android Keystore encrypted passwords), zipalign, apksig v1–v3 signing, AAB via bundletool |
| `:ai-assistant` | Streaming chat with Anthropic, OpenAI-compatible and Gemini APIs, context chips, markdown rendering, unified-diff apply |
| `:git-vcs` | JGit: init, clone, status, stage, commit, branches, push/pull over HTTPS with token auth |

## Android storage and binary execution

- Projects live in app-private storage (`filesDir/projects`). Folders outside are imported through the Storage Access Framework, so no broad storage permission is needed on Android 10–15.
- Android only allows executing native binaries that ship inside the APK's `nativeLibraryDir`. AAPT2 is therefore expected as `libaapt2.so` under `app/src/main/jniLibs/<abi>/`, with `jniLibs.useLegacyPackaging = true` so it is extracted to disk. Codein never copies a binary into `filesDir` and tries to `exec` it.
- Java tools (ECJ, D8, apksig, bundletool, JGit) run in-process.

## Setup

1. **AAPT2** (required for resource builds): take `aapt2` for `arm64-v8a` (and `x86_64` for emulators) from a static Android build of aapt2, rename to `libaapt2.so`, place in `app/src/main/jniLibs/<abi>/`, and rebuild Codein.
2. **android.jar** (required): in Codein, open Settings → Toolchain → Import android.jar and pick `platforms/android-35/android.jar` from an Android SDK. Alternatively bundle it at `app/src/main/assets/sdk/android.jar`.
3. **Kotlin compiler** (only for Kotlin projects): Settings → Toolchain → Import jars, selecting `kotlin-compiler-embeddable`, `kotlin-stdlib`, `kotlin-script-runtime`, `kotlin-reflect`, `kotlinx-coroutines-core-jvm`, `trove4j` and `annotations` (plus the Compose compiler plugin for Compose projects).
4. **AI**: Settings → AI providers. Keys are encrypted with the Android Keystore.
5. **Git**: open the Git panel in a project and save a username and personal access token.

Build Codein itself with `./gradlew assembleDebug`. Unit tests: `./gradlew testDebugUnitTest`.

## Known limitations

- AAPT2 and `android.jar` are not bundled in this repository (licensing and size); resource builds need them as described above.
- Kotlin and Compose projects need the imported Kotlin compiler. Running kotlinc on ART is experimental and memory-hungry; Java + Views projects are the most reliable path.
- Without `READ_LOGS` (granted only via `adb shell pm grant com.codein.ide android.permission.READ_LOGS`), Logcat shows only Codein's own process logs.
- The Sora editor version used has no code folding.
- Git works over HTTPS only (no SSH keys).
- The installed app is launched by the system installer; Codein cannot attach a debugger.
