package app.music.backend.sonari.ui.screens.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Shared between the library screen's shuffle FAB and the songs list. The FAB only bumps
 * [tick]; whichever songs screen is mounted watches it and runs the shuffle.
 */
class ShuffleRouletteState {
  var tick: Int by mutableIntStateOf(0)
    private set

  /** Called by the shuffle FAB. No-op animations later read the tick. */
  fun begin() {
    tick++
  }
}
