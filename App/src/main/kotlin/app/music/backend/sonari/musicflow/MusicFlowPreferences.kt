package app.music.backend.sonari.musicflow

import androidx.datastore.preferences.core.Preferences
import app.music.backend.sonari.constants.CanvasThumbnailAnimationKey
import app.music.backend.sonari.constants.DataSaverEnabledKey
import app.music.backend.sonari.constants.MusicFlowAnimatedBackgroundKey
import app.music.backend.sonari.constants.MusicFlowAutoNextKey
import app.music.backend.sonari.constants.MusicFlowAutoplayKey
import app.music.backend.sonari.constants.MusicFlowCellularPrefetchKey
import app.music.backend.sonari.constants.MusicFlowEnabledKey
import app.music.backend.sonari.constants.MusicFlowHideRecentKey
import app.music.backend.sonari.constants.MusicFlowLyricsKey

data class MusicFlowPreferences(
  val enabled: Boolean = true,
  val autoplay: Boolean = true,
  val autoNext: Boolean = true,
  val animatedBackground: Boolean = false,
  val lyrics: Boolean = true,
  val cellularPrefetch: Boolean = true,
  val hideRecent: Boolean = true,
  val dataSaver: Boolean = false,
) {
  companion object {
    fun from(preferences: Preferences) =
      MusicFlowPreferences(
        enabled = preferences[MusicFlowEnabledKey] ?: true,
        autoplay = preferences[MusicFlowAutoplayKey] ?: true,
        autoNext = preferences[MusicFlowAutoNextKey] ?: true,
        animatedBackground =
          preferences[MusicFlowAnimatedBackgroundKey]
            ?: preferences[CanvasThumbnailAnimationKey]
            ?: false,
        lyrics = preferences[MusicFlowLyricsKey] ?: true,
        cellularPrefetch = preferences[MusicFlowCellularPrefetchKey] ?: true,
        hideRecent = preferences[MusicFlowHideRecentKey] ?: true,
        dataSaver = preferences[DataSaverEnabledKey] ?: false,
      )
  }
}
