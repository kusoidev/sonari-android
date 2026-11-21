# Sonari

An Android music player for people who actually listen: local files and YouTube, gapless playback,
and lyrics that move with the song.

## Features

- **Play from anywhere.** Your own library, folders on the device, or stream from YouTube.
- **Lyrics worth watching.** Word-by-word sweep, a letter-by-letter bloom style, per-line ripple as
  the song moves, and synced providers (LrcLib, KuGou, BetterLyrics, YouLyPlus, Paxsenix, Unison,
  SimpMusic).
- **A Now Playing screen built by hand.** Album art that follows the beat, a focus blur that pulls the
  eye to the line being sung, and colour taken from the cover.
- **Shuffle that feels like a wheel.** Spin it and it lands where you let go.
- **Downloads.** Keep tracks offline, including while they stream.
- **Built to last a session.** DSP, playback and background work tuned for hours, not minutes.

## Build

**Requirements:** JDK 21, Android SDK Platform 36, NDK `28.2.13676358`, Android Studio (or the SDK
command-line tools).

```bash
git clone https://github.com/kusoidev/sonari-android.git
cd sonari-android
cp local.properties.template local.properties   # then set sdk.dir
```

From Android Studio, open the folder, let Gradle sync, and run the `App` configuration. From the
command line:

```bash
./gradlew assembleUniversalGmsDebug      # with Google Play services
./gradlew assembleUniversalFossDebug     # without
./gradlew assembleUniversalGmsRelease    # signed release
```

APKs land in `App/build/outputs/apk/`. Unit tests:

```bash
./gradlew :Core:testDebugUnitTest :Lyrics:testDebugUnitTest :App:testUniversalGmsDebugUnitTest
```

On Windows use `gradlew.bat`.

## Project

| | |
|---|---|
| App ID | `com.kusoi.sonari` |
| Min SDK | 26 |
| Version | `App/build.gradle.kts` (`versionName` / `versionCode`) |
| Language | Kotlin, Jetpack Compose |
| Toolchain | JDK 21, Gradle wrapper included |

## Credits

Sonari is a fork of **[Echo Music](https://github.com/EchoMusicApp/Echo-Music)**, not an original
codebase. Most of what is here is their work, under GPL-3.0, and it stays that way. Two projects in
particular made Sonari possible:

- **[Echo Music](https://github.com/EchoMusicApp/Echo-Music)** — the base of the app: playback,
  streaming, library, downloads, lyrics plumbing and the module layout.
- **[Mochy Music](https://github.com/realidkroo/Mochy-Music)** — the project Echo itself is built on,
  and the reference and inspiration for the boot animation and the lyric animations.

Lyrics come through providers carried over from Echo: **[LrcLib](https://lrclib.net)**, KuGou,
BetterLyrics, YouLyPlus, Paxsenix, Unison and **[SimpMusic](https://github.com/maxrave-dev/SimpMusic)**.

## License

GPL-3.0, as inherited from Echo Music. See [LICENSE](LICENSE).
