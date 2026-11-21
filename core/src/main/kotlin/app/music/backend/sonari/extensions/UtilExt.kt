package app.music.backend.sonari.extensions

fun <T> tryOrNull(block: () -> T): T? =
  try {
    block()
  } catch (e: Exception) {
    null
  }
