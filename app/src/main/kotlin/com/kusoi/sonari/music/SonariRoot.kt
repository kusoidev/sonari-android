package com.kusoi.sonari.music

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class Tab(val label: String, val icon: ImageVector) {
    Search("Search", Icons.Rounded.Search),
    Library("Library", Icons.Rounded.MusicNote),
    Lyrics("Lyrics", Icons.Rounded.TextFields),
    Player("Player", Icons.Rounded.PlayArrow),
    Share("Share", Icons.Rounded.SwapHoriz)
}

/**
 * Mirrors ContentView.swift: a themed gradient, the active tab, and the custom
 * tab bar pinned to the bottom. Only the active tab composes, so search state
 * has to live above this switch.
 */
@Composable
fun SonariRoot(engine: PlayerEngine) {
    var selectedTab by remember { mutableStateOf(Tab.Library) }
    val theme by engine.theme.collectAsStateWithLifecycle()

    val top by animateColorAsState(theme.backgroundTop, Motion.theme(), label = "bgTop")
    val bottom by animateColorAsState(theme.backgroundBottom, Motion.theme(), label = "bgBottom")
    val animatedTheme = theme.copy(backgroundTop = top, backgroundBottom = bottom)

    Box(Modifier.fillMaxSize()) {
        ThemeBackground(animatedTheme)

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (selectedTab) {
                    Tab.Search -> SearchScreen(engine, onPlay = { selectedTab = Tab.Player })
                    Tab.Library -> LibraryScreen(engine, onPlay = { selectedTab = Tab.Player })
                    Tab.Lyrics -> LyricsScreen(engine)
                    Tab.Player -> PlayerScreen(engine)
                    Tab.Share -> ShareScreen(engine, onAutoPlay = { selectedTab = Tab.Player })
                }
            }
            TabBar(selected = selectedTab, accent = theme.accent) { selectedTab = it }
        }
    }
}

/** HStack of equal-width tabs, accent tint, 0.5pt hairline on top. */
@Composable
private fun TabBar(selected: Tab, accent: Color, onSelect: (Tab) -> Unit) {
    val bottomColor = Color(0xFF08080A)
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(bottomColor)
                .navigationBarsPadding()
                .padding(top = 10.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Tab.entries.forEach { tab ->
                val isSelected = tab == selected
                val tint by animateColorAsState(
                    if (isSelected) accent else Color.White.copy(alpha = 0.4f),
                    Motion.chip(),
                    label = "tabTint"
                )
                val iconSize by animateDpAsState(if (isSelected) 21.dp else 19.dp, label = "tabIcon")
                val weight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        tint = tint,
                        modifier = Modifier.size(iconSize)
                    )
                    Text(
                        tab.label,
                        color = tint,
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Ink.hairline)
        )
    }
}
