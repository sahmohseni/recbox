package com.recbox.app.recording

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.recbox.app.MainActivity
import com.recbox.app.data.VideoLens
import com.recbox.app.data.VideoQuality

/** Starts the camera only after the user explicitly taps the notification action. */
class QuickVideoStartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val audioGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (cameraGranted && audioGranted) {
            val lens = intent.getStringExtra(EXTRA_LENS)?.let { VideoLens.valueOf(it) } ?: VideoLens.Back
            val quality = intent.getStringExtra(EXTRA_QUALITY)?.let { VideoQuality.valueOf(it) } ?: VideoQuality.FullHd
            RecordingForegroundService.startVideo(this, lens, quality)
            finish()
        } else {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    companion object {
        private const val EXTRA_LENS = "extra_lens"
        private const val EXTRA_QUALITY = "extra_quality"
    }
}
