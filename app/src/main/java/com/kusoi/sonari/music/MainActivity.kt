package com.kusoi.sonari.music

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.kusoi.sonari.music.Models.SonariPaths
import com.kusoi.sonari.music.Services.LibraryManager
import com.kusoi.sonari.music.Services.PlaybackService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        SonariPaths.init(applicationContext)
        LibraryManager.load()

        /** Start playback service so the MediaSession exists before first play. */
        ContextCompat.startForegroundService(this, Intent(this, PlaybackService::class.java))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        setContent {
            SonariRoot()
        }
    }
}