package com.recbox.app.data

enum class RecordingMode(val label: String) {
    AmbientAudio("صدای محیط"),
    Video("ویدیو"),
    Photo("عکس"),
    Call("مکالمه")
}

enum class VideoLens(val label: String) {
    Back("دوربین پشت"),
    Front("دوربین جلو"),
}

enum class VideoQuality(val label: String, val cameraQualityName: String) {
    Hd("HD · 720p", "HD"),
    FullHd("Full HD · 1080p", "Full HD"),
    Uhd("Ultra HD · 4K", "4K"),
}

enum class RecordingStatus { Idle, Recording, Paused, Saved, Error }

data class RecordingItem(
    val id: String,
    val title: String,
    val mode: RecordingMode,
    val durationSeconds: Long = 0,
    val createdAtLabel: String = "همین حالا",
    val localUri: String? = null,
)

data class ContactItem(
    val id: Long,
    val name: String,
    val phoneNumber: String,
)
