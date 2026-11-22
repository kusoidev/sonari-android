package app.music.backend.sonari.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.net.Uri
import app.music.backend.sonari.MainActivity
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * A launcher icon made from a photo the user picked.
 *
 * The app icon of an installed package is baked into its resources, so it cannot be swapped for an
 * arbitrary bitmap at runtime. What can be done, and what this does, is save the cropped image and
 * push a home screen shortcut that carries it: the launcher then shows a Sonari icon with that photo
 * exactly like an app icon, and it opens the app.
 */
object CustomAppIcon {
  const val ICON_SIZE = 512

  private const val FILE_NAME = "custom_app_icon.png"
  private const val SHORTCUT_ID = "sonari_custom_icon"

  fun file(context: Context): File = File(context.filesDir, FILE_NAME)

  fun exists(context: Context): Boolean = file(context).isFile

  fun load(context: Context): Bitmap? =
    runCatching { file(context).takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.absolutePath) } }
      .getOrNull()

  fun save(context: Context, bitmap: Bitmap): Boolean =
    runCatching {
        file(context).outputStream().use { stream ->
          bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
          stream.flush()
        }
        true
      }
      .getOrDefault(false)

  fun delete(context: Context): Boolean {
    runCatching {
      val manager = context.getSystemService(ShortcutManager::class.java)
      manager?.removeDynamicShortcuts(listOf(SHORTCUT_ID))
    }
    return runCatching { !file(context).exists() || file(context).delete() }.getOrDefault(false)
  }

  /** Reads a picked photo, downsampled so a 48 megapixel camera shot does not eat the heap. */
  fun readSource(context: Context, uri: Uri, maxSize: Int = 1600): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSize || bounds.outHeight / (sample * 2) >= maxSize) {
      sample *= 2
    }

    val options =
      BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
      }
      .getOrNull()
  }

  /**
   * Draws what the editor previewed: the source scaled to cover a square, zoomed and moved by the
   * user, cut out at [outSize].
   *
   * [offsetX] and [offsetY] are in the preview's pixels, [previewSizePx] is the preview's side.
   */
  fun render(
    source: Bitmap,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
    previewSizePx: Float,
    outSize: Int = ICON_SIZE
  ): Bitmap {
    val baseScale = max(previewSizePx / source.width, previewSizePx / source.height)
    val scale = baseScale * zoom
    val drawnWidth = source.width * scale
    val drawnHeight = source.height * scale
    val ratio = outSize / previewSizePx

    val output = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)
    canvas.scale(ratio, ratio)
    val matrix =
      Matrix().apply {
        setScale(scale, scale)
        postTranslate(
          previewSizePx / 2f + offsetX - drawnWidth / 2f,
          previewSizePx / 2f + offsetY - drawnHeight / 2f
        )
      }
    canvas.drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    return output
  }

  /** Adds the image to the home screen as a pinned shortcut. False when the launcher says no. */
  fun pin(context: Context, bitmap: Bitmap): Boolean {
    val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
    if (!manager.isRequestPinShortcutSupported) return false

    val label = context.getString(app.music.backend.sonari.R.string.app_name)
    val shortcut =
      ShortcutInfo.Builder(context, SHORTCUT_ID)
        .setShortLabel(label)
        .setLongLabel(label)
        .setIcon(Icon.createWithBitmap(bitmap))
        .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
        .build()

    return runCatching { manager.requestPinShortcut(shortcut, null) }.getOrDefault(false)
  }
}
