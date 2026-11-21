package app.music.backend.sonari.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import app.music.backend.sonari.core.repository.PlaylistRepositoryImpl
import app.music.backend.sonari.core.repository.SongRepositoryImpl
import app.music.backend.sonari.domain.repositories.PlaylistRepository
import app.music.backend.sonari.domain.repositories.SongRepository
import app.music.backend.sonari.musicflow.MusicFlowFeedSource
import app.music.backend.sonari.musicflow.MusicFlowRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DomainBindingsModule {

  @Binds
  @Singleton
  abstract fun bindSongRepository(
    impl: SongRepositoryImpl
  ): SongRepository

  @Binds
  @Singleton
  abstract fun bindPlaylistRepository(
    impl: PlaylistRepositoryImpl
  ): PlaylistRepository

  @Binds
  @Singleton
  abstract fun bindMusicFlowFeedSource(
    impl: MusicFlowRepository
  ): MusicFlowFeedSource
}
