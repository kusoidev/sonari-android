package com.kusoi.sonari.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    /** NEARBY_WIFI_DEVICES gates NSD discovery on Android 13+, and a denied
     *  grant returns an empty peer list with no error, so ask up front. */
    private val nearbyWifiPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) nearbyWifiPermission.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val engine = PlayerEngine(applicationContext)
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF050506)) {
                val theme = engine.theme.collectAsStateWithLifecycle().value
                Box(Modifier.fillMaxSize().background(theme.backgroundBottom)) {
                    SonariRoot(engine)
                }
            }
        }
    }
}
