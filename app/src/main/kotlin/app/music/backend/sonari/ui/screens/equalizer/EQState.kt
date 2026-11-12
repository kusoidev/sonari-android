package app.music.backend.sonari.ui.screens.equalizer

import app.music.backend.sonari.dsp.core.models.SavedEQProfile

data class EQState(
  val profiles: List<SavedEQProfile> = emptyList(),
  val activeProfileId: String? = null,
  val importStatus: String? = null,
  val error: String? = null
)
