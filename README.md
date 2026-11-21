# Sonari

An Android music app built with Kotlin and Jetpack Compose: local playback, YouTube streaming,
synchronised lyrics, and a hand-built Now Playing screen.

This repository is the full Android project, including playback, library, lyrics and related modules.

## Credits

Sonari is a **fork of [Echo Music](https://github.com/EchoMusicApp/Echo-Music)**, not an original
codebase. Most of what is in this repository is their work, licensed GPL-3.0, and it stays that way.
Two projects in particular made Sonari possible:

- **[Echo Music](https://github.com/EchoMusicApp/Echo-Music)** — the base of the app. Playback,
  streaming, library, downloads, lyrics plumbing and the module layout all come from here.
- **[Mochy Music](https://github.com/realidkroo/Mochy-Music)** — the project Echo itself is built on,
  and the inspiration and reference for the boot animation and the lyric animations.

Lyrics providers used through Echo: **[LrcLib](https://lrclib.net)**, KuGou, BetterLyrics, YouLyPlus,
Paxsenix, Unison and **[SimpMusic](https://github.com/maxrave-dev/SimpMusic)**.

## What Sonari changes

- A rebuilt Now Playing screen and a shuffle wheel for the library.
- New lyric renderers: a word-sweep line renderer and a letter-by-letter bloom style, selectable in
  Appearance settings, with ripple follow-through between lines.
- Streamlined settings and navigation: removed modules and screens that were not used, in favour of a
  smaller, faster app.
- Audio and battery work: playback, DSP and background behaviour tuned for long sessions.

The exact list of changes is in the git history, module by module.

## Requirements

- Android Studio (latest stable recommended) or command-line Android SDK tools
- JDK 21
- Android SDK with:
  - Android SDK Platform 36 (target/compile per project config)
  - Build tools / NDK `28.2.13676358` (as configured in `App/build.gradle.kts`)
- Git

## Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/kusoidev/sonari-android.git
   cd sonari-android
   ```

2. **Configure local SDK path**
   ```bash
   cp local.properties.template local.properties
   ```
   Edit `local.properties` and set your Android SDK location:
   ```properties
   sdk.dir=/path/to/your/android/sdk
   ```
   Examples:
   - macOS: `/Users/<you>/Library/Android/sdk`
   - Linux: `/home/<you>/Android/sdk`
   - Windows: `C:\\Users\\<you>\\AppData\\Local\\Android\\sdk`

3. **(Optional) Gradle/signing properties**
   If you need custom build properties, copy the template and adjust as needed:
   ```bash
   cp gradle.properties.template gradle.properties
   ```
   Do **not** commit real signing credentials.

4. **(Optional) Firebase / Google services**
   The project can be built without Firebase configuration. If you want Firebase features, add your
   own `App/google-services.json` locally (it is gitignored). Release builds and some CI flows may
   expect signing/Firebase secrets when configured.

## Build & Run

### From Android Studio
1. Open the project folder in Android Studio.
2. Let Gradle sync complete.
3. Select a device/emulator and run the `App` configuration.

### From the command line

Debug build (FOSS variant, no Google Play Services/Cast dependency path used by default flavoring):
```bash
./gradlew assembleUniversalFossDebug
```

Debug build (GMS variant):
```bash
./gradlew assembleUniversalGmsDebug
```

Release build (requires proper signing configuration):
```bash
./gradlew assembleUniversalGmsRelease
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

APKs are generated under:
```
App/build/outputs/apk/
```

## Tests

Run unit tests (examples):
```bash
./gradlew :Core:testDebugUnitTest :Lyrics:testDebugUnitTest :App:testUniversalGmsDebugUnitTest
```

## Project notes

- **App ID:** `com.kusoi.sonari` (debug builds use the `.debug` suffix)
- **Version:** see `App/build.gradle.kts` (`versionCode` / `versionName`)
- **Min SDK:** 26
- **Toolchain:** JDK 21, Gradle wrapper included

## Local files (not committed)

These are ignored by git and should remain local:
- `local.properties` (SDK path)
- `App/google-services.json` / signing keys (`*.keystore`, `*.jks`)
- build outputs (`build/`, `.gradle/`, etc.)
- any local debug dumps or secrets

## License

GPL-3.0, as inherited from Echo Music. See [LICENSE](LICENSE).
