package com.kusoi.sonari.music.Views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kusoi.sonari.music.Models.Track
import com.kusoi.sonari.music.Services.DownloadService
import com.kusoi.sonari.music.Services.LibraryManager
import com.kusoi.sonari.music.Services.YouTubeSearchResult
import com.kusoi.sonari.music.ViewModels.PlayerViewModel
import com.kusoi.sonari.music.ViewModels.SearchViewModel

@Composable
fun SearchScreen() {
    val query by SearchViewModel.query.collectAsState()
    val results by SearchViewModel.results.collectAsState()
    val isLoading by SearchViewModel.isLoading.collectAsState()
    val errorMessage by SearchViewModel.errorMessage.collectAsState()
    var isFocused by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            text = "Search",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        SearchBar(
            query = query,
            onQueryChange = { SearchViewModel.setQuery(it) },
            isFocused = isFocused,
            onFocusChange = { isFocused = it },
            onClear = { SearchViewModel.setQuery("") },
            onSearch = { SearchViewModel.search() }
        )

        when {
            isLoading -> LoadingState()
            errorMessage != null -> ErrorState(errorMessage!!)
            results.isNotEmpty() -> ResultsList(results)
            else -> EmptyState()
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Artists, songs, videos...", color = Color.White.copy(alpha = 0.3f)) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { onFocusChange(it.isFocused) }
            )
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onClear() }
                )
            }
        }

        if (isFocused || query.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Cancel",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable {
                    onClear()
                }
            )
        }
    }
}

@Composable
private fun ResultsList(results: List<YouTubeSearchResult>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(results, key = { it.id }) { result ->
            ResultRow(result)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 128.dp)
                    .height(0.5.dp)
                    .background(Color.White.copy(alpha = 0.05f))
            )
        }
    }
}

@Composable
private fun ResultRow(result: YouTubeSearchResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { streamNow(result) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            CachedImage(
                youtubeID = result.id,
                remoteURL = result.thumbnailURL,
                size = 58.dp,
                corner = 8.dp
            )
            if (result.duration != null) {
                Text(
                    text = result.duration,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (result.channelName != null) {
                Text(
                    text = result.channelName,
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(4.dp))

        Icon(
            imageVector = Icons.Filled.ArrowDownward,
            contentDescription = "Download",
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier
                .size(24.dp)
                .clickable { enqueueDownload(result) }
        )

        Spacer(Modifier.width(12.dp))

        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Play",
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
        Spacer(Modifier.height(14.dp))
        Text("Searching...", color = Color.White.copy(alpha = 0.35f), fontSize = 14.sp)
    }
}

@Composable
private fun ErrorState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.WifiOff,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.1f),
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text("Find your music", color = Color.White.copy(alpha = 0.3f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Search YouTube and play anything instantly.",
            color = Color.White.copy(alpha = 0.18f),
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private fun streamNow(result: YouTubeSearchResult) {
    val existing = LibraryManager.tracks.value.firstOrNull { it.youtubeID == result.id }
    val existingFile = existing?.localFileURL
    if (existing != null && existingFile != null && existingFile.exists()) {
        PlayerViewModel.play(existing)
        return
    }

    val track = Track(
        title = result.title,
        youtubeID = result.id,
        thumbnailURL = result.thumbnailURL,
        artist = result.channelName,
        streamingURL = com.kusoi.sonari.music.Services.ApiService.serveURL(result.id)
    )
    PlayerViewModel.play(track)
}

private fun enqueueDownload(result: YouTubeSearchResult) {
    DownloadService.enqueue(
        videoID = result.id,
        title = result.title,
        thumbnailURL = result.thumbnailURL,
        artist = result.channelName
    )
}