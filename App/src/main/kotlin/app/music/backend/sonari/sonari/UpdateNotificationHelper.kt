package app.music.backend.sonari.sonari

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.music.backend.sonari.MainActivity
import app.music.backend.sonari.R
import app.music.backend.sonari.sonari.updater.UpdateSource
import app.music.backend.sonari.sonari.updater.getNotifiedUpdateVersion
import app.music.backend.sonari.sonari.updater.saveNotifiedUpdateVersion

object UpdateNotificationHelper {
  private const val CHANNEL_ID = "updates"
  private const val NOTIFICATION_ID = 1001

  /**
   * Tells the user once per release that a newer Sonari exists; tapping it opens the update screen,
   * which downloads the APK from the GitHub release.
   */
  fun showUpdateNotification(context: Context, versionName: String) {
    val version = UpdateSource.cleanTag(versionName)
    if (version.isEmpty() || version == getNotifiedUpdateVersion(context)) return

    val nm = context.getSystemService(NotificationManager::class.java)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
          CHANNEL_ID,
          context.getString(R.string.app_updates_title),
          NotificationManager.IMPORTANCE_DEFAULT
        )
      nm.createNotificationChannel(channel)
    }

    val intent =
      Intent(context, MainActivity::class.java).apply {
        action = MainActivity.ACTION_UPDATE
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }

    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    val pending = PendingIntent.getActivity(context, NOTIFICATION_ID, intent, flags)

    val notif =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_nobg)
        .setContentTitle(context.getString(R.string.update_available_title))
        .setContentText(context.getString(R.string.update_to_v, version))
        .setContentIntent(pending)
        .setAutoCancel(true)
        .build()

    if (
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
    ) {
      NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notif)
      saveNotifiedUpdateVersion(context, version)
    }
  }
}
