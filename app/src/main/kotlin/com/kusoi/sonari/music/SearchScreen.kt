package com.kusoi.sonari.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch

/**
 * Search. One query field, a Cancel that slides in when focused, then result
 * cards with a download chip. Playing a row queues every result from the
 * tapped one, unchanged from the previous layout.
 */
@Composable
fun SearchScreen(engine: PlayerEngine, onPlay: () -> Unit) {
    val theme by engine.theme.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<YouTubeSearchResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var focused by remember { mutableStateOf(false) }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        keyboard?.hide()
        scope.launch {
            isLoading = true
            errorMessage = null
            runCatching { ApiClient.search(q) }
                .onSuccess { results = it; if (it.isEmpty()) errorMessage = "No results found." }
                .onFailure { errorMessage = it.message ?: "Search failed"; results = emptyList() }
            isLoading = false
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "Search",
            color = SonariText.primary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 16.dp)
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Songs, artists, videos", color = SonariText.tertiary, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = SonariText.tertiary, modifier = Modifier.size(18.dp)) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Clear",
                            tint = SonariText.tertiary,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { query = "" }
                                .padding(6.dp)
                        )
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SonariSurface.raised,
                    unfocusedContainerColor = SonariSurface.raised,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = SonariText.primary,
                    unfocusedTextColor = SonariText.primary,
                    cursorColor = SonariSurface.accent
                ),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focused = it.isFocused }
            )
            AnimatedVisibility(
                visible = focused || query.isNotEmpty(),
                enter = slideInHorizontally { it / 2 } + fadeIn(),
                exit = slideOutHorizontally { it / 2 } + fadeOut()
            ) {
                Text(
                    "Cancel",
                    color = SonariText.secondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            query = ""
                            focused = false
                            keyboard?.hide()
                        }
                )
            }
        }

        when {
            isLoading -> LoadingSearch()
            errorMessage != null && results.isEmpty() -> ErrorSearch(errorMessage!!)
            results.isNotEmpty() -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(results) { result ->
                    SearchRow(
                        result = result,
                        accent = theme.accent,
                        onAccent = theme.onAccent,
                        onPlay = {
                            val queue = results.map {
                                Track(title = it.title, youtubeID = it.id, thumbnailURL = it.thumbnailURL, artist = it.channelName)
                            }
                            val idx = results.indexOfFirst { it.id == result.id }
                            engine.setQueue(queue, if (idx >= 0) idx else 0)
                            val existing = LibraryStore.tracks.value.firstOrNull { it.youtubeID == result.id && it.localFileURL != null }
                            engine.play(existing ?: queue[if (idx >= 0) idx else 0])
                            onPlay()
                        },
                        onDownload = {
                            DownloadService.enqueue(result.id, result.title, result.thumbnailURL, result.channelName)
                        }
                    )
                }
            }
            else -> EmptySearch()
        }
    }
}

@Composable
private fun SearchRow(
    result: YouTubeSearchResult,
    accent: Color,
    onAccent: Color,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPlay() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Box(
                Modifier
                    .size(width = 108.dp, height = 62.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SonariSurface.raised),
                contentAlignment = Alignment.Center
            ) {
                if (result.thumbnailURL != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(result.thumbnailURL).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            result.duration?.let {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(it, color = SonariText.primary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                result.title,
                color = SonariText.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            result.channelName?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, color = SonariText.tertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Rounded.ArrowDownward,
            contentDescription = "Download",
            tint = SonariText.secondary,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDownload() }
                .padding(7.dp)
        )
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = onAccent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun LoadingSearch() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = SonariSurface.accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(14.dp))
        Text("Searching", color = SonariText.tertiary, fontSize = 14.sp)
    }
}

@Composable
private fun ErrorSearch(message: String) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, color = SonariText.tertiary, fontSize = 14.sp)
    }
}

@Composable
private fun EmptySearch() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(SonariSurface.raised),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = SonariText.tertiary, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("Find something to play", color = SonariText.primary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Search YouTube and start listening.",
            color = SonariText.tertiary,
            fontSize = 13.sp
        )
    }
}
