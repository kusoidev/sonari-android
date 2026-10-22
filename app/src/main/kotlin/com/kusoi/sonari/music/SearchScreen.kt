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
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
 * Port of Sonari/Views/SearchView.swift. One query field, a Cancel that slides
 * in when focused, then rows with a duration chip. Playing a row queues every
 * result from the tapped one.
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
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Artists, songs, videos...", color = Color.White.copy(alpha = 0.3f), fontSize = 15.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp)) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Clear",
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp).clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { query = "" }
                        )
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                    focusedBorderColor = Color.White.copy(alpha = 0.2f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = theme.accent
                ),
                modifier = Modifier.weight(1f)
            )
            AnimatedVisibility(
                visible = focused || query.isNotEmpty(),
                enter = slideInHorizontally { it / 2 } + fadeIn(),
                exit = slideOutHorizontally { it / 2 } + fadeOut()
            ) {
                Text(
                    "Cancel",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(start = 10.dp)
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
            errorMessage != null -> ErrorSearch(errorMessage!!)
            results.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                items(results) { result ->
                    SearchRow(
                        result = result,
                        accent = theme.accent,
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
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPlay() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Box(
                Modifier
                    .size(width = 100.dp, height = 58.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                if (result.thumbnailURL != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(result.thumbnailURL).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(16.dp))
                }
            }
            result.duration?.let {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(it, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                result.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            result.channelName?.let {
                Text(it, color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            androidx.compose.material.icons.Icons.Rounded.ArrowDownward,
            contentDescription = "Download",
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDownload() }
        )
        Spacer(Modifier.width(12.dp))
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = "Play",
            tint = accent.copy(alpha = 0.9f),
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun LoadingSearch() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(14.dp))
        Text("Searching...", color = Color.White.copy(alpha = 0.35f), fontSize = 14.sp)
    }
}

@Composable
private fun ErrorSearch(message: String) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, color = Color.White.copy(alpha = 0.35f), fontSize = 14.sp)
    }
}

@Composable
private fun EmptySearch() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.1f),
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text("Find your music", color = Color.White.copy(alpha = 0.3f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Search YouTube and play anything instantly.",
            color = Color.White.copy(alpha = 0.18f),
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 48.dp)
        )
    }
}
