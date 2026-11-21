package app.music.backend.sonari.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import app.music.backend.sonari.lyrics.LyricsHelper

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LyricsHelperEntryPoint {
  fun lyricsHelper(): LyricsHelper
}
