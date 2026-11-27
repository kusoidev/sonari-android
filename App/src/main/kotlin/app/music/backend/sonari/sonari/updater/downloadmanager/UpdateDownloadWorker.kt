package app.music.backend.sonari.sonari.updater.downloadmanager

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.music.backend.sonari.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateDownloadWorker(private val context: Context, workerParams: WorkerParameters) :
  CoroutineWorker(context, workerParams) {

  override suspend fun doWork(): Result =
    withContext(Dispatchers.IO) {
      val apkUrl = inputData.getString("apk_url") ?: return@withContext Result.failure()
      val version = inputData.getString("version") ?: "unknown"
      val fileSize = inputData.getString("file_size") ?: ""

      try {
        val startingNotification =
          DownloadNotificationManager.getDownloadStartingNotification(version, fileSize)
        val foregroundInfo =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
              DownloadNotificationManager.NOTIFICATION_ID,
              startingNotification,
              ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
          } else {
            ForegroundInfo(DownloadNotificationManager.NOTIFICATION_ID, startingNotification)
          }
        setForeground(foregroundInfo)
      } catch (e: Exception) {}

      val downloadDir =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "sonari_updates")
      if (!downloadDir.exists()) {
        downloadDir.mkdirs()
      }

      val isZip = apkUrl.contains("nightly.link") || apkUrl.endsWith(".zip")
      val apkFile = File(downloadDir, "sonari.apk")
      // The transfer is written beside the APK and only takes its place once it is whole: a
      // download cut short used to sit on disk as the APK itself, and the installer then called
      // it invalid instead of downloading it again.
      val partialFile =
        if (isZip) File(downloadDir, "sonari_temp.zip") else File(downloadDir, "sonari.apk.part")
      partialFile.delete()

      try {
        val url = URL(apkUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.connect()

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
          DownloadNotificationManager.showDownloadFailed(
            version,
            context.getString(R.string.server_error, connection.responseCode)
          )
          if (connection.responseCode >= 500) {
            return@withContext Result.retry()
          }
          return@withContext Result.failure()
        }

        val fileLength = connection.contentLength
        val inputStream = connection.inputStream
        val outputStream = FileOutputStream(partialFile)

        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalBytesRead: Long = 0
        var lastProgress = -1
        var lastNotificationTime = 0L

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
          if (isStopped) {
            outputStream.close()
            inputStream.close()
            connection.disconnect()
            partialFile.delete()
            return@withContext Result.retry()
          }

          outputStream.write(buffer, 0, bytesRead)
          totalBytesRead += bytesRead

          if (fileLength > 0) {
            val progress = (totalBytesRead.toFloat() / fileLength.toFloat() * 100).toInt()
            val currentTime = System.currentTimeMillis()

            if (progress > lastProgress && currentTime - lastNotificationTime >= 1000) {
              lastProgress = progress
              lastNotificationTime = currentTime

              val progressNotification =
                DownloadNotificationManager.getDownloadProgressNotification(progress, version)
              val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE)
                  as android.app.NotificationManager
              notificationManager.notify(
                DownloadNotificationManager.NOTIFICATION_ID,
                progressNotification
              )

              setProgress(workDataOf("progress" to progress.toFloat() / 100f))
            }
          }
        }

        outputStream.flush()
        outputStream.close()
        inputStream.close()
        connection.disconnect()

        // A transfer the server closed early ends cleanly as far as the socket is concerned, so
        // what the release says the file weighs is what decides whether all of it arrived.
        if (fileLength > 0 && totalBytesRead != fileLength.toLong()) {
          partialFile.delete()
          DownloadNotificationManager.showDownloadFailed(
            version,
            context.getString(R.string.download_incomplete)
          )
          return@withContext Result.retry()
        }

        apkFile.delete()
        val placed =
          if (isZip) {
            val extracted = extractApk(partialFile, apkFile)
            partialFile.delete()
            extracted
          } else {
            partialFile.renameTo(apkFile)
          }

        // A file the installer cannot read at all is never handed to it.
        if (!placed || !isInstallable(apkFile)) {
          apkFile.delete()
          DownloadNotificationManager.showDownloadFailed(
            version,
            context.getString(R.string.download_incomplete)
          )
          return@withContext Result.retry()
        }

        if (version.startsWith("nightly-r")) {
          val runNumberString = version.removePrefix("nightly-r")
          val runNumber = runNumberString.toIntOrNull()
          if (runNumber != null) {
            val sharedPreferences =
              context.getSharedPreferences("update_settings", Context.MODE_PRIVATE)
            sharedPreferences.edit().putInt("last_installed_nightly_run", runNumber).apply()
          }
        }

        DownloadNotificationManager.showDownloadComplete(version, apkFile.absolutePath)

        Result.success(workDataOf("file_path" to apkFile.absolutePath))
      } catch (e: IOException) {
        partialFile.delete()
        DownloadNotificationManager.showDownloadFailed(
          version,
          e.message ?: context.getString(R.string.download_failed)
        )
        Result.retry()
      } catch (e: Exception) {
        partialFile.delete()
        DownloadNotificationManager.showDownloadFailed(
          version,
          e.message ?: context.getString(R.string.download_failed)
        )
        Result.failure()
      }
    }

  /** Copies the APK out of a nightly zip, and says whether there was one to copy. */
  private fun extractApk(zip: File, target: File): Boolean =
    runCatching {
        ZipInputStream(zip.inputStream()).use { zis ->
          var entry = zis.nextEntry
          while (entry != null) {
            if (!entry.isDirectory && entry.name.endsWith(".apk")) {
              FileOutputStream(target).use { fos -> zis.copyTo(fos) }
              return true
            }
            entry = zis.nextEntry
          }
        }
        false
      }
      .getOrDefault(false)

  /** Whether the installer can parse this file at all. */
  private fun isInstallable(file: File): Boolean =
    file.length() > 0L &&
      runCatching { context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) != null }
        .getOrDefault(false)
}
