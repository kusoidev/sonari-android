package com.kusoi.sonari.music

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Port of Sonari/Views/DownloadView.swift. Paste a link, queue it, watch rows
 * move through queued / preparing / downloading / saved.
 */
@Composable
fun DownloadScreen(engine: PlayerEngine) {
    val jobs by DownloadService.jobs.collectAsStateWithLifecycle()
    val theme by engine.theme.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var urlInput by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Text(
            "Downloads",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Text(
            "PASTE A YOUTUBE LINK",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                placeholder = { Text("youtube.com/watch?v=...", color = Color.White.copy(alpha = 0.3f), fontSize = 15.sp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
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
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .primaryClip?.getItemAt(0)?.text?.toString()
                        if (clip != null) urlInput = clip
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(20.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (urlInput.isEmpty()) 0.4f else 1f))
                .clickable(enabled = urlInput.isNotEmpty(), interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    val id = ApiClient.extractVideoID(urlInput) ?: return@clickable
                    if (LibraryStore.tracks.value.none { it.youtubeID == id }) {
                        DownloadService.enqueue(id, "track_$id", null, null)
                    }
                    urlInput = ""
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ArrowDownward, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add to Queue", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (jobs.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Queue", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (jobs.any { it.isFinished }) {
                    Text(
                        "Clear finished",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { DownloadService.removeFinished() }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(jobs) { job -> DownloadRow(job) }
            }
        }
    }
}

@Composable
private fun DownloadRow(job: DownloadService.Job) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (icon, tint) = when (job.state) {
                DownloadService.Job.State.Queued -> Icons.Rounded.HourglassEmpty to Color.White.copy(alpha = 0.35f)
                DownloadService.Job.State.Preparing -> Icons.Rounded.Sync to Color.White.copy(alpha = 0.6f)
                DownloadService.Job.State.Downloading -> Icons.Rounded.ArrowDownward to Color.White
                DownloadService.Job.State.Completed -> Icons.Rounded.CheckCircle to Color(0xFF66BB6A)
                DownloadService.Job.State.Failed -> Icons.Rounded.Error to Color(0xFFEF5350)
            }
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                job.title,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                when (job.state) {
                    DownloadService.Job.State.Downloading -> "${(job.progress * 100).toInt()}%"
                    else -> job.state.label
                },
                color = if (job.state == DownloadService.Job.State.Completed) Color(0xFF81C784) else Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        if (job.state == DownloadService.Job.State.Downloading) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
                Box(Modifier.fillMaxWidth(job.progress.toFloat().coerceIn(0f, 1f)).height(3.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}
