<div align="center">

# Sonari

**An Android music player that treats lyrics as part of the music.**

Local files, folders and YouTube in one library, with lyrics that move with the song.

<br/>

<a href="https://github.com/kusoidev/sonari-android/releases/latest">
  <img src="https://img.shields.io/github/v/release/kusoidev/sonari-android?label=release&style=flat-square&color=2f6f4f" alt="Latest release">
</a>
<a href="https://github.com/kusoidev/sonari-android/releases">
  <img src="https://img.shields.io/github/downloads/kusoidev/sonari-android/total?label=downloads&style=flat-square&color=444444" alt="Downloads">
</a>
<a href="https://github.com/kusoidev/sonari-android/stargazers">
  <img src="https://img.shields.io/github/stars/kusoidev/sonari-android?label=stars&style=flat-square&color=444444" alt="Stars">
</a>
<a href="https://github.com/kusoidev/sonari-android/network/members">
  <img src="https://img.shields.io/github/forks/kusoidev/sonari-android?label=forks&style=flat-square&color=444444" alt="Forks">
</a>
<img src="https://img.shields.io/badge/Android-8.0%2B-444444?style=flat-square" alt="Android 8.0+">
<a href="LICENSE">
  <img src="https://img.shields.io/github/license/kusoidev/sonari-android?style=flat-square&color=444444" alt="GPL-3.0">
</a>

</div>

> [!TIP]
> Download the app from the [releases page](https://github.com/kusoidev/sonari-android/releases/latest). An APK from anywhere else may have been modified, so stick to this repository.

---

## Install

1. Open the [latest release](https://github.com/kusoidev/sonari-android/releases/latest) on your phone.
2. Download the `.apk` you want:
   - `Sonari-ver.X.X.X-release.apk` — the app itself. This is the one you want unless you have a reason not to.
   - `Sonari-ver.X.X.X-debug.apk` — a test build. It installs next to the release one as a separate app (`Sonari debug`), so both can stay on the phone; use it to reproduce a bug or grab logs.
   - `Sonari-ver.X.X.X-universal.apk` — for older or 32-bit phones where the release build refuses to install. It runs anywhere, but it is a bigger download.
3. Open the downloaded file. Android will ask you to allow installs from your browser or file manager; allow it once, then install.
4. Open Sonari. No account is needed to start listening.

To update, install the new APK over the old one. Your library, playlists and settings stay.

> [!NOTE]
> Sonari requires Android 8.0 or newer (API 26). It is not on Google Play; the APK is the way in.

---

## What it does

| | |
|---|---|
| **Play anything** | Your own files and folders, plus YouTube search and streaming, in one library. |
| **Lyrics worth watching** | Word-by-word sweep, a letter-by-letter bloom, and per-line ripple motion as the song moves. Timing-synced through LrcLib, KuGou, BetterLyrics, YouLyPlus, Paxsenix, Unison and SimpMusic. |
| **A Now Playing screen built by hand** | Cover art that reacts to the beat, a focus blur that pulls to the line being sung, colour taken from the artwork. |
| **Shuffle that feels like a wheel** | Spin it, let go, and it lands where it should. |
| **Downloads** | Keep tracks offline, including while they stream. |
| **Made for long sessions** | Playback, DSP and background work tuned for hours rather than minutes. |

---

## FAQ

**Do I need an account?**
No. Search, playback, lyrics and downloads work without signing in.

**Where do the lyrics come from?**
From public providers (listed above) that Sonari queries when a track needs them. Nothing is bundled with the app.

**Will updating lose my library?**
No. Every release since 1.4.8 is signed with the same key as the builds before it, so a new APK
installs straight over the old one and your library, playlists and downloads stay. Android only
refuses with a bare "App not installed" when the two builds are signed with different keys, so that
key never changes now: losing it would mean no more updates, which is why it is kept with the project
and should be backed up.

**Why does Android warn me about the download?**
Because the APK does not come from Google Play. The warning is about the source, not the app; the code is here for you to read.

**Something is broken. Where do I say so?**
Open an [issue](https://github.com/kusoidev/sonari-android/issues) with your phone model, Android version and what happened.

---

## Build from source

Only needed if you want to change the code.

**Requirements:** JDK 21, Android SDK Platform 36, NDK `28.2.13676358`, Android Studio or the SDK command line tools.

```bash
git clone https://github.com/kusoidev/sonari-android.git
cd sonari-android
cp local.properties.template local.properties   # then set sdk.dir
```

```bash
./gradlew assembleUniversalGmsRelease     # signed release, Google Play services
./gradlew assembleUniversalFossDebug      # debug, without Play services
```

APKs land in `App/build/outputs/apk/`. Unit tests:

```bash
./gradlew :Core:testDebugUnitTest :Lyrics:testDebugUnitTest :App:testUniversalGmsDebugUnitTest
```

On Windows use `gradlew.bat`. The project is Kotlin with Jetpack Compose, app ID `com.kusoi.sonari`, minimum SDK 26.

---

### Versioning

Versions follow semver: **MAJOR** for a change that breaks compatibility, **MINOR** for new features
that keep it, **PATCH** for backwards-compatible fixes. `versionName` and `versionCode` both live in
`App/build.gradle.kts`, and the code goes up by one with every release.

## Credits

Sonari is a fork of **[Echo Music](https://github.com/EchoMusicApp/Echo-Music)** and is not an original codebase. Most of what is here is their work, under GPL-3.0, and it stays that way.

- **[Echo Music](https://github.com/EchoMusicApp/Echo-Music)** — the base of the app: playback, streaming, library, downloads, lyrics plumbing and the module layout.
- **[Mochy Music](https://github.com/realidkroo/Mochy-Music)** — the project Echo itself is built on, and the reference and inspiration for the boot animation and the lyric animations.

Lyrics providers carried over from Echo: **[LrcLib](https://lrclib.net)**, KuGou, BetterLyrics, YouLyPlus, Paxsenix, Unison and **[SimpMusic](https://github.com/maxrave-dev/SimpMusic)**.

If you fork Sonari, keep these credits and keep it free.

---

## Disclaimer

Sonari is an independent, unofficial project for personal use, provided as is and without warranty.

- It is not affiliated with, endorsed by or sponsored by YouTube, Google or any other service it can reach. All names and brands belong to their owners.
- Sonari does not host or distribute music. Everything is fetched from third-party services when you ask for it, and those services can change or stop working at any time.
- You are responsible for how you use the app, including the terms of service of the services you use and the copyright law where you live.
- If you listen to an artist regularly, pay for their work somewhere. That is what keeps the music coming.

---

## Star history

If Sonari is useful to you, a star helps other people find it.

<a href="https://www.star-history.com/?repos=kusoidev%2Fsonari-android&type=date&legend=top-left">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=kusoidev/sonari-android&type=date&theme=dark&legend=top-left" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=kusoidev/sonari-android&type=date&theme=light&legend=top-left" />
    <img alt="Star history chart" src="https://api.star-history.com/chart?repos=kusoidev/sonari-android&type=date&theme=dark&legend=top-left" />
  </picture>
</a>

## License

GPL-3.0, as inherited from Echo Music. See [LICENSE](LICENSE).
