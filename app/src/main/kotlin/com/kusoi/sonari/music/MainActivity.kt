package com.kusoi.sonari.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
