package com.kusoi.sonari.music

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kusoi.sonari.music.ViewModels.PlayerViewModel
import com.kusoi.sonari.music.Views.DownloadScreen
import com.kusoi.sonari.music.Views.LibraryScreen
import com.kusoi.sonari.music.Views.LyricsScreen
import com.kusoi.sonari.music.Views.PlayerScreen
import com.kusoi.sonari.music.Views.SearchScreen

enum class Tab(val label: String, val icon: ImageVector) {
    SEARCH("Search", Icons.Filled.Search),
    LIBRARY("Library", Icons.Filled.LibraryMusic),
    LYRICS("Lyrics", Icons.Filled.Subtitles),
    PLAYER("Player", Icons.Filled.PlayArrow),
    DOWNLOAD("Download", Icons.Filled.ArrowDownward)
}

@Composable
fun SonariRoot() {
    var selectedTab by remember { mutableStateOf(Tab.LIBRARY) }
    val theme by PlayerViewModel.theme.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        /** Root gradient that animates as the theme changes with the playing track. */
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(theme.backgroundTop, theme.backgroundBottom)
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 78.dp)
        ) {
            /** Active-tab-only rendering. Matches the iOS crash fix: never mount all tabs. */
            when (selectedTab) {
                Tab.SEARCH -> SearchScreen()
                Tab.LIBRARY -> LibraryScreen()
                Tab.LYRICS -> LyricsScreen(isActive = selectedTab == Tab.LYRICS)
                Tab.PLAYER -> PlayerScreen()
                Tab.DOWNLOAD -> DownloadScreen()
            }
        }

        TabBar(
            selected = selectedTab,
            accent = theme.accent,
            onSelect = { selectedTab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun TabBar(
    selected: Tab,
    accent: Color,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x0A0A0B))
                .navigationBarsPadding()
                .padding(top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Tab.entries.forEach { tab ->
                TabItem(
                    tab = tab,
                    isSelected = selected == tab,
                    accent = accent,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: Tab,
    isSelected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color by animateColorAsState(
        targetValue = if (isSelected) accent else Color.White.copy(alpha = 0.4f),
        animationSpec = tween(300),
        label = "tabColor"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.92f,
        animationSpec = tween(300),
        label = "tabScale"
    )

    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = tab.label,
            color = color,
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}