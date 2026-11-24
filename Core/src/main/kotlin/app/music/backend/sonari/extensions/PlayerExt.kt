package app.music.backend.sonari.extensions

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import app.music.backend.sonari.models.MediaMetadata
import java.util.ArrayDeque

fun Player.togglePlayPause() {
  if (!playWhenReady && playbackState == Player.STATE_IDLE) {
    prepare()
  }
  playWhenReady = !playWhenReady
}

fun Player.toggleRepeatMode() {
  repeatMode =
    when (repeatMode) {
      REPEAT_MODE_OFF -> REPEAT_MODE_ALL
      REPEAT_MODE_ALL -> REPEAT_MODE_ONE
      REPEAT_MODE_ONE -> REPEAT_MODE_OFF
      else -> throw IllegalStateException()
    }
}

fun Player.getQueueWindows(): List<Timeline.Window> =
  currentTimeline.getQueueWindows(currentMediaItemIndex, shuffleModeEnabled)

fun Player.getCurrentQueueIndex(): Int =
  currentTimeline.getCurrentQueueIndex(currentMediaItemIndex, shuffleModeEnabled)

/**
 * The queue in playback order, laid out around [fromIndex].
 *
 * It takes a [Timeline] and the two indexes instead of reading them off a player so the caller can
 * do the walk away from the app thread. Walking a long queue steps through both directions and
 * allocates a window for every item in it, which on a few thousand songs is the entire cost of
 * turning shuffle on.
 */
fun Timeline.getQueueWindows(
  fromIndex: Int,
  shuffleModeEnabled: Boolean,
): List<Timeline.Window> {
  if (isEmpty) return emptyList()
  if (fromIndex == C.INDEX_UNSET || fromIndex < 0 || fromIndex >= windowCount) return emptyList()

  val queue = ArrayDeque<Timeline.Window>()
  val queueSize = windowCount

  queue.add(getWindow(fromIndex, Timeline.Window()))

  var firstMediaItemIndex = fromIndex
  var lastMediaItemIndex = fromIndex
  while (
    (firstMediaItemIndex != C.INDEX_UNSET || lastMediaItemIndex != C.INDEX_UNSET) &&
      queue.size < queueSize
  ) {
    if (lastMediaItemIndex != C.INDEX_UNSET) {
      lastMediaItemIndex =
        getNextWindowIndex(lastMediaItemIndex, REPEAT_MODE_OFF, shuffleModeEnabled)
      if (lastMediaItemIndex != C.INDEX_UNSET) {
        queue.add(getWindow(lastMediaItemIndex, Timeline.Window()))
      }
    }
    if (firstMediaItemIndex != C.INDEX_UNSET && queue.size < queueSize) {
      firstMediaItemIndex =
        getPreviousWindowIndex(firstMediaItemIndex, REPEAT_MODE_OFF, shuffleModeEnabled)
      if (firstMediaItemIndex != C.INDEX_UNSET) {
        queue.addFirst(getWindow(firstMediaItemIndex, Timeline.Window()))
      }
    }
  }
  return queue.toList()
}

/** How many items of the current playback order come before [fromIndex]. */
fun Timeline.getCurrentQueueIndex(
  fromIndex: Int,
  shuffleModeEnabled: Boolean,
): Int {
  if (isEmpty) return -1
  if (fromIndex == C.INDEX_UNSET || fromIndex < 0 || fromIndex >= windowCount) return -1

  var index = 0
  var cursor = fromIndex
  while (cursor != C.INDEX_UNSET) {
    cursor = getPreviousWindowIndex(cursor, REPEAT_MODE_OFF, shuffleModeEnabled)
    if (cursor != C.INDEX_UNSET) {
      index++
    }
  }
  return index
}

val Player.currentMetadata: MediaMetadata?
  get() = currentMediaItem?.metadata

val Player.mediaItems: List<MediaItem>
  get() =
    object : AbstractList<MediaItem>() {
      override val size: Int
        get() = mediaItemCount

      override fun get(index: Int): MediaItem = getMediaItemAt(index)
    }

fun Player.findNextMediaItemById(mediaId: String): MediaItem? {
  for (i in currentMediaItemIndex until mediaItemCount) {
    if (getMediaItemAt(i).mediaId == mediaId) {
      return getMediaItemAt(i)
    }
  }
  return null
}

fun Player.setOffloadEnabled(enabled: Boolean) {
  trackSelectionParameters =
    trackSelectionParameters
      .buildUpon()
      .setAudioOffloadPreferences(
        TrackSelectionParameters.AudioOffloadPreferences.Builder()
          .setAudioOffloadMode(
            if (enabled) {
              TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
            } else {
              TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
            }
          )
          .build()
      )
      .build()
}
