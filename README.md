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
- Android only allows executing native binaries that ship inside the APK's `nativeLibraryDir`. AAPT2 therefore ships as `lib/<abi>/libaapt2.so` with `useLegacyPackaging = true` so it is extracted to disk. Codein never copies a binary into `filesDir` and tries to `exec` it.
- Java tools (ECJ, D8, apksig, bundletool, JGit) run in-process.

## Bundled toolchain

The Gradle build of Codein provisions everything an on-device build needs:

| Piece | Where it comes from |
| --- | --- |
| `aapt2` (arm64-v8a, x86_64) | Downloaded at build time by the `fetchAapt2` task from the static Android builds published at [lzhiyong/android-sdk-tools](https://github.com/lzhiyong/android-sdk-tools) (Apache-2.0, built from AOSP). Pin a release with `-Pcodein.aapt2.release=<tag>`, or drop your own `app/src/main/jniLibs/<abi>/libaapt2.so` to skip the download. On CI a failed download fails the build. |
| `android.jar` (API 35) | Copied by the `bundleAndroidJar` task from the Android SDK used to build Codein into `assets/sdk/android.jar`, extracted on first use. |
| Kotlin compiler | Downloaded in the app on demand (Settings → Toolchain → Download Kotlin compiler) from Maven Central, with SHA-1 verification: `kotlin-compiler-embeddable` 2.1.21 and its runtime jars, plus the Compose compiler plugin when selected. |

## Setup

1. Build Codein with `./gradlew assembleDebug` (needs network the first time for aapt2). Unit tests: `./gradlew testDebugUnitTest`. On-device tests: `./gradlew connectedDebugAndroidTest`.
2. **Kotlin projects**: in Codein, open Settings → Toolchain → Download Kotlin compiler. Java projects need nothing extra.
3. **AI**: Settings → AI providers. Keys are encrypted with the Android Keystore.
4. **Git**: in a project's Git panel, open credentials. Save a username and personal access token for HTTPS remotes, or generate an SSH key and add its public key to your Git host for `git@host:user/repo.git` remotes.
5. **Logcat for other apps**: grant once with `adb shell pm grant com.codein.ide android.permission.READ_LOGS` (the Logcat panel shows the exact command and a copy button).

## CI

- `build`: `assembleDebug`, unit tests, and the debug APK as an artifact.
- `device-test`: boots an API 34 x86_64 emulator and runs `connectedDebugAndroidTest`, which checks that the bundled aapt2 executes from `nativeLibraryDir`, that `android.jar` is available, that the app launches, and that a Java Views template project builds into a signed APK on the device.

## Known limitations

- **Code folding is not available.** The Sora editor (0.23.x) has no folding support and adding it means forking the editor's layout engine. Instead, Codein has *Go to symbol* (Ctrl+O, or `#` in the command palette) listing the classes, functions and properties of the current file.
- **Kotlin on the device is experimental.** The compiler is converted to DEX on the first Kotlin build, which takes several minutes and needs a lot of free RAM; low-memory phones may fail. Java + Views projects are the most reliable path.
- **Logcat** shows only Codein's own logs until `READ_LOGS` is granted over adb; Android gives that permission to normal apps no other way.
- **SSH** uses an RSA-3072 key (Ed25519 needs crypto that Android's JSch support lacks). Host keys are trusted on first use and a changed host key is rejected.
- The installed app is launched by the system installer; Codein cannot attach a debugger.
