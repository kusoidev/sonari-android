package com.kusoi.sonari.music.Models

import android.content.Context
import java.io.File

/**
 * Single source for the app's on-disk locations. Initialized once from Application.onCreate.
 * iOS used the Documents directory; Android's equivalent private storage is filesDir.
 */
object SonariPaths {
    private lateinit var appFiles: File

    fun init(context: Context) {
        appFiles = context.filesDir
    }

    val documents: File
        get() = appFiles

    val thumbnails: File
        get() = File(appFiles, "thumbnails").apply { mkdirs() }

    val lyricsCache: File
        get() = File(appFiles, "lyrics_cache").apply { mkdirs() }
}