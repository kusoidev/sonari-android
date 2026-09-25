package com.kusoi.sonari.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Root composable. Placeholder until the tab shell is built; keeps the app
 * compiling while the rest of the port lands.
 */
@Composable
fun SonariRoot() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF141416)),
        contentAlignment = Alignment.Center
    ) {
        Text("Sonari", color = Color.White)
    }
}