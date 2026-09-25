package com.kusoi.sonari.music.Services

import androidx.media3.exoplayer.ExoPlayer

/**
 * Shared ExoPlayer instance created by PlaybackService. The ViewModel talks to this
 * directly so transport and queue logic live in one place.
 */
object PlayerHolder {
    var player: ExoPlayer? = null
}