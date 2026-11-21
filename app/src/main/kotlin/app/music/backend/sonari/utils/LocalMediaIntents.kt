package app.music.backend.sonari.utils

import androidx.core.net.toUri
import java.util.Locale

fun String.isLocalMediaId(): Boolean {
  return runCatching {
      when (toUri().scheme?.lowercase(Locale.US)) {
        "content",
        "file",
        "android.resource" -> true
        else -> false
      }
    }
    .getOrDefault(false)
}
