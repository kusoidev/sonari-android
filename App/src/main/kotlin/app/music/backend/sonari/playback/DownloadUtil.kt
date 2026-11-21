package app.music.backend.sonari.playback

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.scheduler.Requirements
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import app.music.backend.sonari.innertube.YouTube
import app.music.backend.sonari.innertube.models.IpVersion
import dagger.hilt.android.qualifiers.ApplicationContext
import app.music.backend.sonari.constants.AudioQuality
import app.music.backend.sonari.constants.DownloadOnWifiOnlyKey
import app.music.backend.sonari.constants.DownloadQuality
import app.music.backend.sonari.constants.DownloadQualityKey
import app.music.backend.sonari.constants.DownloadWithMetadataKey
import app.music.backend.sonari.constants.IpVersionKey
import app.music.backend.sonari.db.MusicDatabase
import app.music.backend.sonari.db.entities.ArtistEntity
import app.music.backend.sonari.db.entities.FormatEntity
import app.music.backend.sonari.db.entities.LyricsEntity
import app.music.backend.sonari.db.entities.SongArtistMap
import app.music.backend.sonari.db.entities.SongEntity
import app.music.backend.sonari.di.DownloadCache
import app.music.backend.sonari.di.PlayerCache
import app.music.backend.sonari.lyrics.LyricsProviderRegistry
import app.music.backend.sonari.lyrics.LyricsUtils
import app.music.backend.sonari.ui.utils.resize
import app.music.backend.sonari.utils.YTPlayerUtils
import app.music.backend.sonari.utils.dataStore
import app.music.backend.sonari.utils.enumPreference
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient

@Singleton
class DownloadUtil
@Inject
constructor(
  @ApplicationContext private val context: Context,
  val database: MusicDatabase,
  val databaseProvider: DatabaseProvider,
  @DownloadCache val downloadCache: SimpleCache,
  @PlayerCache val playerCache: SimpleCache,
) {
  private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
  private val downloadQuality by
    enumPreference(
      context,
      DownloadQualityKey,
      DownloadQuality.YOUTUBE
    )
  private val ipVersion by enumPreference(context, IpVersionKey, IpVersion.AUTO)
  private val songUrlCache = ConcurrentHashMap<String, ResolvedStream>()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  val downloads = MutableStateFlow<Map<String, Download>>(emptyMap())

  private val dataSourceFactory: androidx.media3.datasource.DataSource.Factory =
    ResolvingDataSource.Factory(
      ChunkingDataSourceFactory(
        // Read already-streamed bytes from playerCache instead of re-downloading them:
        // a song played before being downloaded would otherwise be fetched twice.
        CacheDataSource.Factory()
          .setCache(playerCache)
          .setUpstreamDataSourceFactory(
            OkHttpDataSource.Factory(
              OkHttpClient.Builder()
                .dns(
                  object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                      val addresses = Dns.SYSTEM.lookup(hostname)
                      return when (this@DownloadUtil.ipVersion) {
                        IpVersion.IPV4 ->
                          addresses.filter { it is Inet4Address }.ifEmpty { addresses }
                        IpVersion.IPV6 ->
                          addresses.filter { it is Inet6Address }.ifEmpty { addresses }
                        IpVersion.AUTO -> addresses
                      }
                    }
                  }
                )
                .proxy(YouTube.proxy)
                .proxyAuthenticator { _, response ->
                  YouTube.proxyAuth?.let { auth ->
                    response.request.newBuilder().header("Proxy-Authorization", auth).build()
                  } ?: response.request
                }
                .build(),
            )
          )
          .setCacheWriteDataSinkFactory(null)
          .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
      )
    ) { dataSpec ->
      // If this is a Canvas request (e.g., Apple Music or Tidal), bypass YouTube resolution!
      if (
        dataSpec.uri.toString().contains("apple.com") ||
          dataSpec.uri.toString().contains("music.apple") ||
          dataSpec.uri.toString().contains(".m3u8") ||
          dataSpec.uri.toString().contains(".mp4") ||
          dataSpec.key?.endsWith("_canvas") == true
      ) {
        return@Factory dataSpec
      }

      val mediaId = dataSpec.key ?: error("No media id")

      songUrlCache["${mediaId}_${downloadQuality.name}"]
        ?.takeIf { it.expiresAtMs > System.currentTimeMillis() }
        ?.let {
          return@Factory it.resolve(dataSpec)
        }

      val playbackData =
        runBlocking(Dispatchers.IO) {
            YTPlayerUtils.playerResponseForPlayback(
              videoId = mediaId,
              audioQuality = AudioQuality.AUTO,
              connectivityManager = connectivityManager
            )
          }
          .getOrThrow()
      val format = playbackData.format

      // playerResponseForPlayback() deliberately returns videoDetails = null (it only
      // resolves the stream), so a download started without the song already in the
      // database used to be inserted as title "Unknown", duration 0, no artist — and
      // lyrics were then searched with an empty title/artist too. Fetch the metadata
      // separately whenever the existing row is missing or incomplete.
      val existingSongInfo = database.getSongByIdBlocking(mediaId)
      val existing = existingSongInfo?.song
      val needsMetadata =
        existing == null ||
          existing.title.isBlank() ||
          existing.title.equals("Unknown", ignoreCase = true) ||
          existing.duration <= 0 ||
          existingSongInfo.artists.isEmpty()
      val videoDetails =
        playbackData.videoDetails
          ?: if (needsMetadata) {
            runBlocking(Dispatchers.IO) {
              YTPlayerUtils.playerResponseForMetadata(mediaId).getOrNull()?.videoDetails
            }
          } else {
            null
          }
      val resolvedTitle =
        existing?.title?.takeIf { it.isNotBlank() && !it.equals("Unknown", true) }
          ?: videoDetails?.title?.takeIf { it.isNotBlank() }
          ?: "Unknown"
      val resolvedArtist =
        existingSongInfo?.artists?.joinToString { it.name }?.takeIf { it.isNotBlank() }
          ?: videoDetails?.author?.takeIf { it.isNotBlank() }
      val resolvedDuration =
        videoDetails?.lengthSeconds?.toIntOrNull()?.takeIf { it > 0 }
          ?: existing?.duration?.takeIf { it > 0 }
          ?: 0
      val resolvedThumbnail =
        existing?.thumbnailUrl
          ?: videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url?.resize(1200, 1200)

      database.query {
        upsert(
          FormatEntity(
            id = mediaId,
            itag = format.itag,
            mimeType = format.mimeType.split(";")[0],
            codecs =
              format.mimeType.split("codecs=").getOrNull(1)?.removeSurrounding("\"") ?: "opus",
            bitrate = format.bitrate,
            sampleRate = format.audioSampleRate,
            contentLength = format.contentLength ?: 0L,
            loudnessDb = playbackData.audioConfig?.loudnessDb,
            perceptualLoudnessDb = playbackData.audioConfig?.perceptualLoudnessDb,
            playbackUrl = playbackData.playbackTracking?.videostatsPlaybackUrl?.baseUrl
          ),
        )

        val downloadMetadata = runBlocking {
          context.dataStore.data.first()[DownloadWithMetadataKey] ?: true
        }
        if (downloadMetadata) {
          scope.launch(Dispatchers.IO) {
            try {
              fetchAndStoreLyrics(
                mediaId = mediaId,
                title = resolvedTitle,
                artist = resolvedArtist ?: "",
                duration = resolvedDuration,
                album = existingSongInfo?.album?.title,
              )
            } catch (e: Exception) {
              e.printStackTrace()
            }
          }
        }

        val thumbnailUrl = resolvedThumbnail
        if (existing != null) {
          // Targeted update so a concurrent download-completion flag can't be reverted.
          updateSongMetadata(
            id = mediaId,
            title = resolvedTitle,
            duration = resolvedDuration,
            thumbnailUrl = thumbnailUrl,
          )
        } else {
          insert(
            SongEntity(
              id = mediaId,
              title = resolvedTitle,
              duration = resolvedDuration,
              thumbnailUrl = thumbnailUrl,
              dateDownload = null,
              isDownloaded = false,
            )
          )
        }

        val metadataAuthor = videoDetails?.author?.takeIf { it.isNotBlank() }
        if (existingSongInfo?.artists.isNullOrEmpty() && metadataAuthor != null) {
          val channelId = videoDetails?.channelId
          val artistId =
            channelId?.takeIf { it.isNotBlank() } ?: ArtistEntity.generateArtistId()
          insert(
            ArtistEntity(
              id = artistId,
              name = metadataAuthor,
              channelId = channelId,
            )
          )
          insert(SongArtistMap(songId = mediaId, artistId = artistId, position = 0))
        }

        thumbnailUrl?.let { url ->
          val request =
            ImageRequest.Builder(context)
              .data(url)
              .memoryCachePolicy(CachePolicy.ENABLED)
              .diskCachePolicy(CachePolicy.ENABLED)
              .build()
          SingletonImageLoader.get(context).enqueue(request)
        }
      }

      val stream = ResolvedStream.from(playbackData)
      songUrlCache["${mediaId}_${downloadQuality.name}"] = stream
      stream.resolve(dataSpec)
    }

  val downloadNotificationHelper =
    DownloadNotificationHelper(context, ExoDownloadService.CHANNEL_ID)

  @OptIn(DelicateCoroutinesApi::class)
  val downloadManager: DownloadManager =
    DownloadManager(
        context,
        databaseProvider,
        downloadCache,
        dataSourceFactory,
        Executors.newFixedThreadPool(3)
      )
      .apply {
        maxParallelDownloads = 3
        addListener(
          object : DownloadManager.Listener {
            override fun onDownloadChanged(
              downloadManager: DownloadManager,
              download: Download,
              finalException: Exception?,
            ) {
              downloads.update { map ->
                map.toMutableMap().apply { set(download.request.id, download) }
              }

              scope.launch {
                when (download.state) {
                  Download.STATE_COMPLETED -> {
                    try {
                      ensureDownloadedSongMetadata(download)
                    } catch (e: Exception) {
                      timber.log.Timber.w(e, "download completion repair failed: %s", download.request.id)
                    }
                    reconcileLibraryWithDownloads()
                  }
                  Download.STATE_FAILED -> {
                    // A download that fails here is usually a song whose stream could not be
                    // resolved (region locked, members only, age gated, taken down). It used to
                    // fail silently, so it looked like tapping Download did nothing at all. Log the
                    // reason, keep the failure notification, and drop the entry so the next play or
                    // the next tap on Download really retries instead of hitting a dead record.
                    val title =
                      runCatching {
                          androidx.media3.common.util.Util.fromUtf8Bytes(download.request.data)
                        }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: download.request.id
                    timber.log.Timber.w(
                      finalException,
                      "Download failed for %s (%s): %s",
                      download.request.id,
                      title,
                      download.failureReason,
                    )
                    app.music.backend.sonari.utils.PlaybackLogManager.log(
                      app.music.backend.sonari.utils.PlaybackLogLevel.ERROR,
                      "Download failed",
                      "$title (${download.request.id}) — " +
                        (finalException?.message ?: "reason ${download.failureReason}"),
                    )
                    database.updateDownloadedInfo(download.request.id, false, null)
                    runCatching { downloadManager.removeDownload(download.request.id) }
                    reconcileLibraryWithDownloads()
                  }
                  Download.STATE_REMOVING -> {
                    database.updateDownloadedInfo(download.request.id, false, null)
                    reconcileLibraryWithDownloads()
                  }
                  else -> {}
                }
              }
            }

            override fun onDownloadRemoved(
              downloadManager: DownloadManager,
              download: Download,
            ) {
              downloads.update { map ->
                map.toMutableMap().apply { remove(download.request.id) }
              }
              scope.launch {
                database.updateDownloadedInfo(download.request.id, false, null)
                reconcileLibraryWithDownloads()
              }
            }
          }
        )
        // The failure notification helper existed but was never attached, so a download that could
        // not be resolved failed without telling anyone.
        addListener(
          ExoDownloadService.TerminalStateNotificationHelper(
            context,
            downloadNotificationHelper,
            7000,
          )
        )
      }

  init {
    val result = mutableMapOf<String, Download>()
    downloadManager.downloadIndex.getDownloads().use { cursor ->
      while (cursor.moveToNext()) {
        result[cursor.download.request.id] = cursor.download
      }
    }
    downloads.value = result

    scope.launch(Dispatchers.IO) {
      result.values.forEach { download ->
        if (download.state == Download.STATE_COMPLETED) {
          ensureDownloadedSongMetadata(download)
        }
      }
      reconcileLibraryWithDownloads()
    }

    // Wi-Fi-only downloads: DownloadManager pauses queued downloads whenever the
    // active requirements aren't met, so flipping this pref mid-download stops it
    // on mobile data without losing progress.
    scope.launch {
      context.dataStore.data
        .map { it[DownloadOnWifiOnlyKey] ?: false }
        .distinctUntilChanged()
        .collectLatest { wifiOnly ->
          downloadManager.requirements =
            Requirements(if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)
        }
    }
  }

  fun getDownload(songId: String): Flow<Download?> = downloads.map { it[songId] }

  /**
   * Re-syncs the download flags with the download index right now. Called when the library's "All"
   * or "Downloaded" list opens, so a song that is already on disk can never be missing from either.
   */
  fun reconcileNow() {
    scope.launch(Dispatchers.IO) { reconcileLibraryWithDownloads() }
  }

  fun release() {
    scope.cancel()
  }

  /** The title stored in the download request when the download was queued. */
  private fun downloadTitle(download: Download): String =
    runCatching { androidx.media3.common.util.Util.fromUtf8Bytes(download.request.data) }
      .getOrNull()
      ?.takeIf { it.isNotBlank() }
      ?: download.request.id

  /**
   * Guarantees a downloaded song has a row in the database with the download flags set. This is
   * offline and cheap on purpose: the library's "All" list only shows songs with `isDownloaded` or
   * `dateDownload` set, so a download whose metadata lookup never finished stayed invisible even
   * though the file was sitting on the device.
   */
  private fun insertDownloadedSong(download: Download) {
    val mediaId = download.request.id
    val downloadDate = downloadDateOf(download)
    database.query {
      // insert() ignores conflicts, so this only creates the row when it is missing.
      insert(
        SongEntity(
          id = mediaId,
          title = downloadTitle(download),
          duration = 0,
          dateDownload = downloadDate,
          isDownloaded = true,
        )
      )
      updateDownloadedInfo(mediaId, true, downloadDate)
    }
  }

  /**
   * Ensures a completed download has usable song metadata in the database. Older downloads (and
   * any whose stream resolved before metadata was fetched) were stored as "Unknown" with a 0:00
   * duration and no artist, which also broke the lyrics lookup. Repairing here means the song is
   * fixed in place instead of the user having to delete and re-download it.
   */
  private suspend fun ensureDownloadedSongMetadata(download: Download) {
    val mediaId = download.request.id
    val downloadDate = downloadDateOf(download)
    // Whatever happens below, the song must exist and be flagged as downloaded, otherwise it never
    // shows up in the library's "All" list even though the file is on the device.
    insertDownloadedSong(download)
    val existingSongInfo = database.getSongByIdBlocking(mediaId)
    val existing = existingSongInfo?.song
    val fromData = downloadTitle(download).takeIf { it != mediaId }
    val needsRepair =
      existingSongInfo == null ||
        existing == null ||
        existing.title.isBlank() ||
        existing.title.equals("Unknown", ignoreCase = true) ||
        existing.duration <= 0 ||
        existingSongInfo.artists.isEmpty()

    if (!needsRepair) {
      database.updateDownloadedInfo(mediaId, true, downloadDate)
      return
    }

    // A metadata lookup that throws used to abort the whole repair, which is why some songs stayed
    // out of the library forever. Fall back to what the download request and the row already say.
    val videoDetails =
      try {
        YTPlayerUtils.playerResponseForMetadata(mediaId).getOrNull()?.videoDetails
      } catch (e: Exception) {
        timber.log.Timber.w(e, "metadata lookup failed for %s", mediaId)
        null
      }
    val title =
      videoDetails?.title?.takeIf { it.isNotBlank() }
        ?: fromData
        ?: existing?.title?.takeIf { it.isNotBlank() && !it.equals("Unknown", true) }
        ?: "Unknown"
    val duration =
      videoDetails?.lengthSeconds?.toIntOrNull()?.takeIf { it > 0 }
        ?: existing?.duration?.takeIf { it > 0 }
        ?: -1
    val thumbnail =
      videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url?.resize(1200, 1200)
        ?: existing?.thumbnailUrl
    val artist =
      videoDetails?.author?.takeIf { it.isNotBlank() }
        ?: existingSongInfo?.artists?.joinToString { it.name }?.takeIf { it.isNotBlank() }

    database.transaction {
      upsert(
        existing?.copy(
          title = title,
          duration = duration,
          thumbnailUrl = thumbnail,
          dateDownload = downloadDate,
          isDownloaded = true,
        )
          ?: SongEntity(
            id = mediaId,
            title = title,
            duration = duration,
            thumbnailUrl = thumbnail,
            dateDownload = downloadDate,
            isDownloaded = true,
          )
      )
      val metadataAuthor = videoDetails?.author?.takeIf { it.isNotBlank() }
      if (existingSongInfo?.artists.isNullOrEmpty() && metadataAuthor != null) {
        val channelId = videoDetails?.channelId
        val artistId =
          channelId?.takeIf { it.isNotBlank() } ?: ArtistEntity.generateArtistId()
        insert(
          ArtistEntity(
            id = artistId,
            name = metadataAuthor,
            channelId = channelId,
          )
        )
        insert(SongArtistMap(songId = mediaId, artistId = artistId, position = 0))
      }
    }

    if (title != "Unknown" && artist != null) {
      try {
        fetchAndStoreLyrics(
          mediaId = mediaId,
          title = title,
          artist = artist,
          duration = duration.takeIf { it > 0 } ?: 0,
          album = existingSongInfo?.album?.title,
        )
      } catch (e: Exception) {
        // Lyrics are a nice-to-have; a provider blowing up must not cost the song its row.
        timber.log.Timber.w(e, "lyrics fetch failed for %s", mediaId)
      }
    }
  }

  /**
   * Makes the `song` table agree with what is really on the device.
   *
   * The `isDownloaded` / `dateDownload` flags were only ever written from the live
   * [DownloadManager] listener, so any change the app missed while it was not running (a download
   * that finished, or a file that was removed) left the database lying: the finished download never
   * turned up in the library's "All" list, and the removed one stayed in it for good. Walking the
   * download index instead of trusting the flags fixes both directions.
   */
  private suspend fun reconcileLibraryWithDownloads() {
    val completed = mutableListOf<Download>()
    downloadManager.downloadIndex.getDownloads().use { cursor ->
      while (cursor.moveToNext()) {
        val download = cursor.download
        if (download.state == Download.STATE_COMPLETED) {
          completed.add(download)
        }
      }
    }

    val completedIds = completed.mapTo(HashSet()) { it.request.id }
    val flagged = database.downloadedSongIdsBlocking().toHashSet()
    val known = database.allSongIdsBlocking().toHashSet()

    completed.forEach { download ->
      val mediaId = download.request.id
      // One song that cannot be repaired must not stop the rest: each one is isolated here.
      try {
        if (mediaId !in known) {
          // The file is on disk but the song never made it into the database (an interrupted
          // metadata write, or a row that was dropped). create it right away so it shows up in
          // "All" even if the metadata lookup below never finishes.
          insertDownloadedSong(download)
        }
        if (mediaId !in flagged) {
          database.updateDownloadedInfo(mediaId, true, downloadDateOf(download))
        }
        ensureDownloadedSongMetadata(download)
      } catch (e: Exception) {
        timber.log.Timber.w(e, "could not reconcile download %s", mediaId)
        app.music.backend.sonari.utils.PlaybackLogManager.log(
          app.music.backend.sonari.utils.PlaybackLogLevel.ERROR,
          "Download repair failed",
          "$mediaId — ${e.message ?: e.javaClass.simpleName}",
        )
      }
    }

    // Still flagged but gone from the index: the file was removed, so stop calling it downloaded.
    (flagged - completedIds).forEach { songId ->
      database.updateDownloadedInfo(songId, false, null)
    }
  }

  private suspend fun fetchAndStoreLyrics(
    mediaId: String,
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
  ) {
    if (title.isBlank() || title.equals("Unknown", ignoreCase = true)) return

    val existingLyrics = database.lyrics(mediaId).first()
    if (
      existingLyrics?.lyrics?.let {
        it.isNotBlank() && it != LyricsEntity.LYRICS_NOT_FOUND
      } == true
    ) {
      return
    }

    val providers =
      LyricsProviderRegistry.getDefaultProviderOrder().mapNotNull {
        LyricsProviderRegistry.getProviderByName(it)
      }
    var foundLyrics: String? = null
    var foundProvider: String? = null
    for (provider in providers) {
      if (!provider.isEnabled(context)) continue
      val result = provider.getLyrics(mediaId, title, artist, duration, album).getOrNull()
      if (
        result != null &&
          result != LyricsEntity.LYRICS_NOT_FOUND &&
          result.isNotBlank()
      ) {
        foundLyrics = result
        foundProvider = provider.name
        if (LyricsUtils.hasSyncedLyrics(result)) break
      }
    }
    if (foundLyrics != null && foundProvider != null) {
      database.query {
        upsert(LyricsEntity(id = mediaId, lyrics = foundLyrics, provider = foundProvider))
      }
    }
  }

  private fun downloadDateOf(download: Download): LocalDateTime {
    val timestamp =
      if (download.updateTimeMs > 0) download.updateTimeMs else System.currentTimeMillis()
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC)
  }

  companion object {
    /**
     * Computes the aggregated download state for a collection of song IDs.
     * - [Download.STATE_COMPLETED] if all songs are completed.
     * - [Download.STATE_DOWNLOADING] if at least one song is currently queued or downloading.
     * - [Download.STATE_STOPPED] otherwise (idle, stopped, or empty).
     */
    fun computeDownloadState(
      songIds: Collection<String>?,
      downloads: Map<String, Download>,
    ): Int {
      if (songIds.isNullOrEmpty()) return Download.STATE_STOPPED
      return if (songIds.all { downloads[it]?.state == Download.STATE_COMPLETED }) {
        Download.STATE_COMPLETED
      } else if (
        songIds.any {
          downloads[it]?.state == Download.STATE_QUEUED ||
            downloads[it]?.state == Download.STATE_DOWNLOADING
        }
      ) {
        Download.STATE_DOWNLOADING
      } else {
        Download.STATE_STOPPED
      }
    }

    /**
     * Returns true if the song is currently completed, queued, or downloading.
     */
    fun hasDownload(songId: String, downloads: Map<String, Download>): Boolean {
      val state = downloads[songId]?.state
      return state == Download.STATE_COMPLETED ||
        state == Download.STATE_QUEUED ||
        state == Download.STATE_DOWNLOADING
    }
  }
}
