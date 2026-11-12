package app.music.backend.sonari.innertube.models.response

import kotlinx.serialization.Serializable

@Serializable data class ImageUploadResponse(val encryptedBlobId: String)
