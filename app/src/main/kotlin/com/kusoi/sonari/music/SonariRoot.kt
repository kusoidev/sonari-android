package com.kusoi.sonari.music

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class Tab(val label: String, val icon: ImageVector) {
    Search("Search", Icons.Rounded.Search),
    Library("Library", Icons.Rounded.LibraryMusic),
    Lyrics("Lyrics", Icons.Rounded.Equalizer),
    Player("Player", Icons.Rounded.MusicNote),
    Share("Share", Icons.Rounded.SwapHoriz)
}

/**
 * App shell. Backdrop, the active screen, then a floating pill nav that sits
 * over the content rather than owning a strip at the bottom. Only the active
 * tab composes, so search state lives above this switch.
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

        Box(Modifier.fillMaxSize().padding(bottom = 96.dp)) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val dir = if (forward) 1 else -1
                    (fadeIn(Motion.quickFade()) + slideInHorizontally(Motion.quickFade()) { it / 14 * dir })
                        .togetherWith(fadeOut(Motion.quickFade()) + slideOutHorizontally(Motion.quickFade()) { -it / 14 * dir })
                },
                label = "tabContent"
            ) { tab ->
                when (tab) {
                    Tab.Search -> SearchScreen(engine, onPlay = { selectedTab = Tab.Player })
                    Tab.Library -> LibraryScreen(engine, onPlay = { selectedTab = Tab.Player })
                    Tab.Lyrics -> LyricsScreen(engine)
                    Tab.Player -> PlayerScreen(engine)
                    Tab.Share -> ShareScreen(engine, onAutoPlay = { selectedTab = Tab.Player })
                }
            }
        }

        // Navigation always carries the house violet. A sampled accent is
        // right for the player where the sleeve is the whole point, but here
        // it went pale on a light sleeve and read as a disabled control.
        FloatingTabBar(
            selected = selectedTab,
            accent = SonariSurface.accent,
            modifier = Modifier.align(Alignment.BottomCenter),
            onSelect = { selectedTab = it }
        )
    }
}

/**
 * The nav from the mockup: a dark capsule where the active icon rides a
 * violet bubble that slides between slots. The bubble is one animated offset
 * rather than five per-tab backgrounds, so switching never double-fades.
 */
@Composable
private fun FloatingTabBar(
    selected: Tab,
    accent: Color,
    modifier: Modifier = Modifier,
    onSelect: (Tab) -> Unit
) {
    val tabs = Tab.entries
    val selectedIndex = tabs.indexOf(selected)
    val bubble by animateDpAsState(72.dp * selectedIndex, Motion.chip(), label = "bubble")

    Box(
        modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp, start = 24.dp, end = 24.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(24.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = accent.copy(alpha = 0.18f))
                .clip(CircleShape)
                .background(SonariSurface.glass),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.fillMaxWidth().height(64.dp)) {
                Box(
                    Modifier
                        .offset(x = bubble)
                        .size(64.dp)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    tabs.forEach { tab ->
                        val isSelected = tab == selected
                        val tint by animateColorAsState(
                            if (isSelected) SonariText.onAccent else SonariText.secondary,
                            Motion.chip(),
                            label = "tabTint"
                        )
                        val scale by animateFloatAsState(if (isSelected) 1f else 0.92f, Motion.chip(), label = "tabScale")
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onSelect(tab) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                tab.icon,
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = Modifier.size(24.dp).scale(scale)
                            )
                        }
                    }
                }
            }
        }
    }
}
