package app.music.backend.sonari.ui.screens.library

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import app.music.backend.sonari.LocalPlayerAwareWindowInsets
import app.music.backend.sonari.LocalPlayerConnection
import app.music.backend.sonari.R
import app.music.backend.sonari.db.entities.Playlist
import app.music.backend.sonari.db.entities.Song
import app.music.backend.sonari.extensions.toMediaItem
import app.music.backend.sonari.playback.queues.ListQueue
import app.music.backend.sonari.viewmodels.LibraryMixViewModel
import kotlinx.coroutines.delay

private data class HeroItem(
  val id: String,
  val title: String,
  val artist: String,
  val thumbnailUrl: String?,
  val isCurrent: Boolean,
)

@Composable
fun LibraryHomeScreen(
  navController: NavController,
  filterContent: @Composable () -> Unit,
  viewModel: LibraryMixViewModel = hiltViewModel(),
) {
  val playerConnection = LocalPlayerConnection.current
  val mediaMetadata by
    (playerConnection?.mediaMetadata ?: kotlinx.coroutines.flow.flowOf(null))
      .collectAsState(initial = null)
  val isPlaying by
    (playerConnection?.isEffectivelyPlaying ?: kotlinx.coroutines.flow.flowOf(false))
      .collectAsState(initial = false)

  val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
  val playlists by viewModel.playlists.collectAsState()
  val allSongsCount by viewModel.allSongsCount.collectAsState()
  val listState = rememberLazyListState()
  val haptic = LocalHapticFeedback.current

  val current = mediaMetadata
  val hero =
    if (current != null) {
      HeroItem(
        id = current.id,
        title = current.title,
        artist = current.artists.joinToString { it.name },
        thumbnailUrl = current.thumbnailUrl,
        isCurrent = true,
      )
    } else {
      recentlyPlayed.firstOrNull()?.let { song ->
        HeroItem(
          id = song.id,
          title = song.song.title,
          artist = song.artists.joinToString { it.name },
          thumbnailUrl = song.thumbnailUrl,
          isCurrent = false,
        )
      }
    }

  val playFromRecent: (Song) -> Unit = { song ->
    if (playerConnection != null && recentlyPlayed.isNotEmpty()) {
      val index = recentlyPlayed.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
      playerConnection.playQueue(
        ListQueue(
          title = "Recently played",
          items = recentlyPlayed.map { it.toMediaItem() },
          startIndex = index,
        )
      )
    }
  }

  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize(),
    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
  ) {
    item(key = "filter") { filterContent() }

    hero?.let { item ->
      item(key = "hero") {
        HeroCard(
          hero = item,
          isPlaying = isPlaying,
          listState = listState,
          onPlay = {
            if (playerConnection == null) return@HeroCard
            if (item.isCurrent) {
              playerConnection.togglePlayPause()
            } else {
              val index = recentlyPlayed.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
              if (recentlyPlayed.isNotEmpty()) {
                playerConnection.playQueue(
                  ListQueue(
                    title = "Recently played",
                    items = recentlyPlayed.map { it.toMediaItem() },
                    startIndex = index,
                  )
                )
              }
            }
          },
        )
      }
    }

    item(key = "quick_access") {
      QuickAccessRow(
        allSongsCount = allSongsCount,
        onLiked = { navController.navigate("auto_playlist/liked") },
        onDownloaded = { navController.navigate("auto_playlist/downloaded") },
        onHistory = { navController.navigate("history") },
        onAll = { navController.navigate("auto_playlist/all") },
      )
    }

    if (recentlyPlayed.isNotEmpty()) {
      item(key = "recent_header") {
        SectionHeader(
          title = "Recently Played",
          onClick = { navController.navigate("history") },
        )
      }
      item(key = "recent_row") {
        LazyRow(
          contentPadding = PaddingValues(horizontal = 20.dp),
          horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          itemsIndexed(
            items = recentlyPlayed,
            key = { _, song -> "recent_${song.id}" },
          ) { index, song ->
            RecentCard(
              song = song,
              index = index,
              isCurrent = song.id == current?.id,
              isPlaying = isPlaying,
              onClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                playFromRecent(song)
              },
            )
          }
        }
      }
    }

    if (playlists.isNotEmpty()) {
      item(key = "playlists_header") {
        SectionHeader(
          title = stringResource(R.string.filter_playlists),
        )
      }
      item(key = "playlists_row") {
        LazyRow(
          contentPadding = PaddingValues(horizontal = 20.dp),
          horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          items(
            items = playlists,
            key = { "home_playlist_${it.id}" },
          ) { playlist ->
            PlaylistCard(
              playlist = playlist,
              onClick = { navController.navigate("local_playlist/${playlist.id}") },
            )
          }
        }
      }
    }

    item(key = "bottom_spacer") { Spacer(Modifier.height(24.dp)) }
  }
}

@Composable
private fun HeroCard(
  hero: HeroItem,
  isPlaying: Boolean,
  listState: LazyListState,
  onPlay: () -> Unit,
) {
  val shape = RoundedCornerShape(30.dp)
  // Track the hero card's own position in the list so the scroll-driven
  // transform fades smoothly as it leaves the viewport, instead of snapping
  // to a fully-collapsed state the moment the first list item scrolls away.
  val heroInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "hero" }
  var maxHeroOffset by remember { mutableStateOf<Int?>(null) }
  val rawProgress =
    if (heroInfo == null) {
      1f
    } else {
      val offset = heroInfo.offset
      if (maxHeroOffset == null || offset > maxHeroOffset!!) {
        maxHeroOffset = offset
      }
      val base = maxHeroOffset ?: offset
      ((base - offset) / heroInfo.size.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    }
  val progress by animateFloatAsState(
    targetValue = rawProgress,
    animationSpec = tween(durationMillis = 80),
    label = "heroScrollProgress",
  )
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .height(430.dp)
        .graphicsLayer {
          val p = progress.coerceIn(0f, 1f)
          scaleX = 1f - 0.06f * p
          scaleY = 1f - 0.06f * p
          translationY = -28.dp.toPx() * p
          alpha = 1f - 0.35f * p
        }
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
  ) {
    AsyncImage(
      model = hero.thumbnailUrl,
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.verticalGradient(
              0f to Color.Black.copy(alpha = 0.08f),
              0.42f to Color.Transparent,
              0.72f to Color.Black.copy(alpha = 0.55f),
              1f to Color.Black.copy(alpha = 0.92f),
            )
          )
    )
    Column(
      modifier = Modifier.align(Alignment.BottomStart).padding(22.dp),
    ) {
      Text(
        text = if (hero.isCurrent && isPlaying) "Now playing" else "Continue",
        style = MaterialTheme.typography.labelLarge,
        color = Color.White.copy(alpha = 0.7f),
      )
      Spacer(Modifier.height(4.dp))
      Text(
        text = hero.title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(2.dp))
      Text(
        text = hero.artist,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.72f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(16.dp))
      Surface(
        onClick = onPlay,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.82f),
        contentColor = Color(0xFF161616),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
          Icon(
            painter = painterResource(R.drawable.play),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(8.dp))
          Text(
            text = "Play",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }
  }
}

@Composable
private fun QuickAccessRow(
  allSongsCount: Int,
  onLiked: () -> Unit,
  onDownloaded: () -> Unit,
  onHistory: () -> Unit,
  onAll: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    QuickTile(
      label = stringResource(R.string.liked),
      icon = R.drawable.favorite,
      onClick = onLiked,
      modifier = Modifier.weight(1f),
    )
    QuickTile(
      label = stringResource(R.string.offline),
      icon = R.drawable.offline,
      onClick = onDownloaded,
      modifier = Modifier.weight(1f),
    )
    QuickTile(
      label = stringResource(R.string.history),
      icon = R.drawable.history,
      onClick = onHistory,
      modifier = Modifier.weight(1f),
    )
    QuickTile(
      label = stringResource(R.string.filter_all),
      icon = R.drawable.music_note,
      onClick = onAll,
      modifier = Modifier.weight(1f),
      badge = allSongsCount.toString(),
    )
  }
}

@Composable
private fun QuickTile(
  label: String,
  icon: Int,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  badge: String? = null,
) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (pressed) 0.94f else 1f,
      animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
      label = "quickTileScale",
    )
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    interactionSource = interaction,
    modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.padding(vertical = 14.dp),
    ) {
      Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(22.dp),
      )
      Spacer(Modifier.height(6.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun SectionHeader(title: String, onClick: (() -> Unit)? = null) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 20.dp, vertical = 10.dp),
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
    )
    if (onClick != null) {
      Spacer(Modifier.width(4.dp))
      Icon(
        painter = painterResource(R.drawable.ic_nav_chevron_right),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

@Composable
private fun RecentCard(
  song: Song,
  index: Int,
  isCurrent: Boolean,
  isPlaying: Boolean,
  onClick: () -> Unit,
) {
  var appeared by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    delay((index.coerceAtMost(8) * 45).toLong())
    appeared = true
  }
  val enter by animateFloatAsState(if (appeared) 1f else 0f, tween(320), label = "recentEnter")
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (pressed) 0.95f else 1f,
      animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
      label = "recentScale",
    )

  Column(
    modifier =
      Modifier.width(148.dp)
        .graphicsLayer {
          alpha = enter
          translationY = (1f - enter) * 28.dp.toPx()
          scaleX = scale
          scaleY = scale
        }
        .clickable(
          interactionSource = interaction,
          indication = null,
          onClick = onClick,
        )
  ) {
    Box(
      modifier =
        Modifier.size(148.dp)
          .clip(RoundedCornerShape(24.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
      AsyncImage(
        model = song.thumbnailUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
      if (isCurrent) {
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(Color.Black.copy(alpha = if (isPlaying) 0.12f else 0.4f))
        )
        Icon(
          painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.align(Alignment.Center).size(34.dp),
        )
      }
    }
    Spacer(Modifier.height(8.dp))
    Text(
      text = song.song.title,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = song.artists.joinToString { it.name },
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
  var appeared by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    delay(60)
    appeared = true
  }
  val enter by animateFloatAsState(if (appeared) 1f else 0f, tween(320), label = "playlistEnter")
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (pressed) 0.95f else 1f,
      animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
      label = "playlistScale",
    )
  val cover = playlist.thumbnails.firstOrNull() ?: playlist.playlist.thumbnailUrl

  Column(
    modifier =
      Modifier.width(148.dp)
        .graphicsLayer {
          alpha = enter
          translationY = (1f - enter) * 28.dp.toPx()
          scaleX = scale
          scaleY = scale
        }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
  ) {
    Box(
      modifier =
        Modifier.size(148.dp)
          .clip(RoundedCornerShape(24.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHigh),
      contentAlignment = Alignment.Center,
    ) {
      if (cover != null) {
        AsyncImage(
          model = cover,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        Icon(
          painter = painterResource(R.drawable.ic_launcher_nobg),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
          modifier = Modifier.size(46.dp),
        )
      }
    }
    Spacer(Modifier.height(8.dp))
    Text(
      text = playlist.playlist.name,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text =
        pluralStringResource(
          R.plurals.n_song,
          playlist.songCount,
          playlist.songCount,
        ),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
    )
  }
}
