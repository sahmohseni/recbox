package com.recbox.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.recbox.app.ui.RecboxApp
import com.recbox.app.ui.theme.RecboxTheme

class MainActivity : ComponentActivity() {
    private var openVideoPreview = false
    private var openPhotoPreview = false
    private var openCallPreview = false
    private var openAudioPreview = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openVideoPreview = savedInstanceState?.getBoolean(EXTRA_OPEN_VIDEO_PREVIEW)
            ?: intent.getBooleanExtra(EXTRA_OPEN_VIDEO_PREVIEW, false)
        openPhotoPreview = savedInstanceState?.getBoolean(EXTRA_OPEN_PHOTO_PREVIEW)
            ?: intent.getBooleanExtra(EXTRA_OPEN_PHOTO_PREVIEW, false)
        openCallPreview = savedInstanceState?.getBoolean(EXTRA_OPEN_CALL_PREVIEW)
            ?: intent.getBooleanExtra(EXTRA_OPEN_CALL_PREVIEW, false)
        openAudioPreview = savedInstanceState?.getBoolean(EXTRA_OPEN_AUDIO_PREVIEW)
            ?: intent.getBooleanExtra(EXTRA_OPEN_AUDIO_PREVIEW, false)
        renderContent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openVideoPreview = intent.getBooleanExtra(EXTRA_OPEN_VIDEO_PREVIEW, false)
        openPhotoPreview = intent.getBooleanExtra(EXTRA_OPEN_PHOTO_PREVIEW, false)
        openCallPreview = intent.getBooleanExtra(EXTRA_OPEN_CALL_PREVIEW, false)
        openAudioPreview = intent.getBooleanExtra(EXTRA_OPEN_AUDIO_PREVIEW, false)
        renderContent()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(EXTRA_OPEN_VIDEO_PREVIEW, openVideoPreview)
        outState.putBoolean(EXTRA_OPEN_PHOTO_PREVIEW, openPhotoPreview)
        outState.putBoolean(EXTRA_OPEN_CALL_PREVIEW, openCallPreview)
        outState.putBoolean(EXTRA_OPEN_AUDIO_PREVIEW, openAudioPreview)
        super.onSaveInstanceState(outState)
    }

    private fun renderContent() {
        enableEdgeToEdge()
        setContent {
            RecboxTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    RecboxApp(openVideoPreview = openVideoPreview, openPhotoPreview = openPhotoPreview, openCallPreview = openCallPreview, openAudioPreview = openAudioPreview)
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_VIDEO_PREVIEW = "open_video_preview"
        const val EXTRA_OPEN_PHOTO_PREVIEW = "open_photo_preview"
        const val EXTRA_OPEN_CALL_PREVIEW = "open_call_preview"
        const val EXTRA_OPEN_AUDIO_PREVIEW = "open_audio_preview"
    }
}
