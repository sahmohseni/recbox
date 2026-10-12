package com.recbox.app.recording

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.recbox.app.MainActivity
import com.recbox.app.data.VideoLens

class QuickPhotoStartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val lens = intent.getStringExtra(EXTRA_LENS)?.let { VideoLens.valueOf(it) } ?: VideoLens.Back
            RecordingForegroundService.startPhoto(this, lens)
            finish()
        } else {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    companion object {
        private const val EXTRA_LENS = "extra_lens"
    }
}
