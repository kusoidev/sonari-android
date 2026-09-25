package com.kusoi.sonari.music.Models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID

/**
 * A downloaded or streaming track. Mirrors the Swift Track decoder, including the
 * legacy playlistID to playlistIDs migration and the playCount default.
 */
@Serializable(with = TrackSerializer::class)
data class Track(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val youtubeID: String,
    val thumbnailURL: String? = null,
    val artist: String? = null,
    val duration: Double? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val playlistIDs: List<String> = emptyList(),
    val streamingURL: String? = null,
    val playCount: Int = 0
) {
    val localFileURL: File?
        get() {
            val name = fileName ?: return null
            return File(SonariPaths.documents, name)
        }

    val isDownloaded: Boolean
        get() = localFileURL?.exists() == true

    val formattedDuration: String
        get() {
            val d = duration ?: return "--:--"
            val mins = d.toInt() / 60
            val secs = d.toInt() % 60
            return "%d:%02d".format(mins, secs)
        }
}

/**
 * Reads the Swift-written JSON. addedAt is a Double (Foundation Date timeIntervalSinceReferenceDate)
 * on iOS, but the Android library is its own file so we store epoch millis. The serializer reads
 * both shapes so a library exported from iOS could still load.
 */
object TrackSerializer : KSerializer<Track> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Track")

    override fun deserialize(decoder: Decoder): Track {
        val input = decoder as JsonDecoder
        val obj = input.decodeJsonElement().jsonObject

        val playlistIDs: List<String> = when {
            obj.containsKey("playlistIDs") ->
                obj["playlistIDs"]!!.jsonArray.map { it.jsonPrimitive.content }
            obj.containsKey("playlistID") -> {
                val legacy = obj["playlistID"]?.jsonPrimitive?.content
                if (legacy.isNullOrEmpty()) emptyList() else listOf(legacy)
            }
            else -> emptyList()
        }

        return Track(
            id = obj["id"]?.jsonPrimitive?.content ?: UUID.randomUUID().toString(),
            title = obj["title"]?.jsonPrimitive?.content ?: "",
            youtubeID = obj["youtubeID"]?.jsonPrimitive?.content ?: "",
            thumbnailURL = obj["thumbnailURL"]?.jsonPrimitive?.contentOrNull,
            artist = obj["artist"]?.jsonPrimitive?.contentOrNull,
            duration = obj["duration"]?.jsonPrimitive?.doubleOrNull,
            fileName = obj["fileName"]?.jsonPrimitive?.contentOrNull,
            fileSize = obj["fileSize"]?.jsonPrimitive?.longOrNull,
            addedAt = obj["addedAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis(),
            playlistIDs = playlistIDs,
            streamingURL = obj["streamingURL"]?.jsonPrimitive?.contentOrNull,
            playCount = obj["playCount"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0
        )
    }

    override fun serialize(encoder: Encoder, value: Track) {
        val output = encoder as JsonEncoder
        val obj = buildJsonObject {
            put("id", value.id)
            put("title", value.title)
            put("youtubeID", value.youtubeID)
            put("thumbnailURL", value.thumbnailURL)
            put("artist", value.artist)
            put("duration", value.duration)
            put("fileName", value.fileName)
            put("fileSize", value.fileSize)
            put("addedAt", value.addedAt)
            put("playlistIDs", buildJsonArray { value.playlistIDs.forEach { add(JsonPrimitive(it)) } })
            put("streamingURL", value.streamingURL)
            put("playCount", value.playCount)
        }
        output.encodeJsonElement(obj)
    }
}

private val JsonPrimitive.contentOrNull: String?
    get() = if (this is kotlinx.serialization.json.JsonNull) null else content