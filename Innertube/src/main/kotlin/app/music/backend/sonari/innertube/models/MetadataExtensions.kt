package app.music.backend.sonari.innertube.models

import app.music.backend.sonari.metadata.cleaner.MetadataCleaner
import app.music.backend.sonari.metadata.models.CleanedMetadata

val SongItem.cleanedMetadata: CleanedMetadata
  get() = MetadataCleaner.clean(title, artists.firstOrNull()?.name.orEmpty())

val SongItem.displayTitle: String
  get() = cleanedMetadata.toDisplayTitle()

val SongItem.cleanArtistName: String
  get() = cleanedMetadata.primaryArtist
