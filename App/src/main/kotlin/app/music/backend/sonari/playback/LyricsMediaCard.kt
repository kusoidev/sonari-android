package app.music.backend.sonari.playback

/**
 * The lyric line the system media card is currently showing, if any.
 *
 * The card an OEM draws for playback - OnePlus' Live Alert, a lock screen, a car head unit - is
 * built from what the app publishes in its MediaSession, and the only line of text it offers beside
 * the title is the artist. So when lyrics are being shown there, this holds the line that stands in
 * the artist's place.
 *
 * It is read by [SessionArtworkPlayer] on the way out to the session and nowhere else: the app's own
 * UI and the database keep the real artist. `null` means the card shows the artist as usual.
 */
object LyricsMediaCard {
  @Volatile var line: String? = null
}
