package com.kusoi.sonari.music.Models

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val trackIDs: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    fun addTrack(trackID: String): Playlist =
        if (trackIDs.contains(trackID)) this else copy(trackIDs = trackIDs + trackID)

    fun removeTrack(trackID: String): Playlist =
        copy(trackIDs = trackIDs.filterNot { it == trackID })
}