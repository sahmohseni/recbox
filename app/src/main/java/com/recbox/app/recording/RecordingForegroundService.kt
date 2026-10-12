package com.recbox.app.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.recbox.app.MainActivity
import com.recbox.app.R
import com.recbox.app.data.AudioRecordingStore
import com.recbox.app.data.RecordingMode
import com.recbox.app.data.VideoLens
import com.recbox.app.data.VideoQuality
import com.recbox.app.data.VideoRecordingStore
import com.recbox.app.data.PhotoStore
import java.io.File

class RecordingForegroundService : LifecycleService() {
    private var activeRecording: Recording? = null
    private var activeAudioRecorder: MediaRecorder? = null
    private var activeAudioFile: File? = null
    private var audioStartedAtElapsed = 0L
    private var startedAtElapsed = 0L
    private var armedLens = VideoLens.Back
    private var armedQuality = VideoQuality.FullHd
    private var armedForPhoto = false
    private var armedForAudio = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_ARM_VIDEO -> {
                armedForPhoto = false
                armedForAudio = false
                armedLens = intent.getStringExtra(EXTRA_LENS)?.let { VideoLens.valueOf(it) } ?: VideoLens.Back
                armedQuality = intent.getStringExtra(EXTRA_QUALITY)?.let { VideoQuality.valueOf(it) } ?: VideoQuality.FullHd
                startForeground(NOTIFICATION_ID, buildNotification("شروع سریع ویدیو", armed = true))
            }
            ACTION_ARM_PHOTO -> {
                armedForPhoto = true
                armedForAudio = false
                armedLens = intent.getStringExtra(EXTRA_LENS)?.let { VideoLens.valueOf(it) } ?: VideoLens.Back
                startForeground(NOTIFICATION_ID, buildNotification("شروع سریع عکس", armed = true))
            }
            ACTION_ARM_AUDIO -> {
                armedForPhoto = false
                armedForAudio = true
                startForeground(NOTIFICATION_ID, buildNotification("شروع سریع صدای محیط", armed = true))
            }
            ACTION_START_VIDEO -> {
                armedForPhoto = false
                armedForAudio = false
                startForeground(NOTIFICATION_ID, buildNotification("ویدیو", armed = false))
                startVideo(
                    lens = intent.getStringExtra(EXTRA_LENS)?.let { VideoLens.valueOf(it) } ?: VideoLens.Back,
                    quality = intent.getStringExtra(EXTRA_QUALITY)?.let { VideoQuality.valueOf(it) } ?: VideoQuality.FullHd,
                )
            }
            ACTION_START_PHOTO -> {
                armedForAudio = false
                startForeground(NOTIFICATION_ID, buildNotification("عکس", armed = false))
                startPhoto(intent.getStringExtra(EXTRA_LENS)?.let { VideoLens.valueOf(it) } ?: VideoLens.Back)
            }
            ACTION_START -> {
                val label = intent.getStringExtra(EXTRA_MODE)?.let { value -> RecordingMode.entries.firstOrNull { it.name == value }?.label }
                    ?: RecordingMode.AmbientAudio.label
                startForeground(NOTIFICATION_ID, buildNotification(label, armed = false))
                if (intent.getStringExtra(EXTRA_MODE) == RecordingMode.AmbientAudio.name) startAudio()
            }
            ACTION_START_AUDIO -> {
                armedForAudio = false
                startForeground(NOTIFICATION_ID, buildNotification(RecordingMode.AmbientAudio.label, armed = false))
                startAudio()
            }
            ACTION_PAUSE -> activeRecording?.pause() ?: activeAudioRecorder?.pause()
            ACTION_RESUME -> activeRecording?.resume() ?: activeAudioRecorder?.resume()
            ACTION_DISARM -> stopRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        activeRecording?.close()
        activeRecording = null
        activeAudioRecorder?.runCatching { stop() }
        activeAudioRecorder?.release()
        activeAudioRecorder = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    private fun startVideo(lens: VideoLens, quality: VideoQuality) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            runCatching {
                val provider = cameraProviderFuture.get()
                val selector = if (lens == VideoLens.Front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                val cameraQuality = when (quality) {
                    VideoQuality.Hd -> Quality.HD
                    VideoQuality.FullHd -> Quality.FHD
                    VideoQuality.Uhd -> Quality.UHD
                }
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(cameraQuality, FallbackStrategy.lowerQualityOrHigherThan(cameraQuality)))
                    .build()
                val videoCapture = VideoCapture.withOutput(recorder)
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, videoCapture)

                val outputFile = File(File(filesDir, "videos").apply { mkdirs() }, "recbox_${System.currentTimeMillis()}.mp4")
                startedAtElapsed = SystemClock.elapsedRealtime()
                activeRecording = videoCapture.output
                    .prepareRecording(this, FileOutputOptions.Builder(outputFile).build())
                    .withAudioEnabled()
                    .start(ContextCompat.getMainExecutor(this)) { event ->
                        if (event is VideoRecordEvent.Finalize) {
                            if (event.error == VideoRecordEvent.Finalize.ERROR_NONE && outputFile.exists()) {
                                val duration = (SystemClock.elapsedRealtime() - startedAtElapsed) / 1000L
                                VideoRecordingStore.save(this, outputFile, duration)
                                getSystemService(NotificationManager::class.java).notify(
                                    COMPLETED_NOTIFICATION_ID,
                                    buildCompletedNotification(isPhoto = false),
                                )
                            }
                            activeRecording = null
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
            }.onFailure {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopRecording() {
        activeRecording?.stop() ?: activeAudioRecorder?.let { recorder ->
            val outputFile = activeAudioFile
            val duration = (SystemClock.elapsedRealtime() - audioStartedAtElapsed) / 1000L
            runCatching { recorder.stop() }
                .onSuccess {
                    if (outputFile?.exists() == true && outputFile.length() > 0L) {
                        AudioRecordingStore.save(this, outputFile, duration)
                        getSystemService(NotificationManager::class.java).notify(
                            COMPLETED_NOTIFICATION_ID,
                            buildCompletedNotification(isAudio = true),
                        )
                    }
                }
            recorder.release()
            activeAudioRecorder = null
            activeAudioFile = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } ?: run {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startAudio() {
        if (activeAudioRecorder != null) return
        val outputFile = File(File(filesDir, "audio").apply { mkdirs() }, "recbox_${System.currentTimeMillis()}.m4a")
        runCatching {
            MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
        }.onSuccess { recorder ->
            activeAudioRecorder = recorder
            activeAudioFile = outputFile
            audioStartedAtElapsed = SystemClock.elapsedRealtime()
        }.onFailure {
            getSystemService(NotificationManager::class.java).notify(ERROR_NOTIFICATION_ID, buildErrorNotification())
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startPhoto(lens: VideoLens) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            runCatching {
                val provider = cameraProviderFuture.get()
                val selector = if (lens == VideoLens.Front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, imageCapture)
                val outputFile = File(File(filesDir, "photos").apply { mkdirs() }, "recbox_${System.currentTimeMillis()}.jpg")
                imageCapture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(outputFile).build(),
                    ContextCompat.getMainExecutor(this),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            PhotoStore.save(this@RecordingForegroundService, outputFile)
                            getSystemService(NotificationManager::class.java).notify(
                                COMPLETED_NOTIFICATION_ID,
                                buildCompletedNotification(isPhoto = true),
                            )
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }

                        override fun onError(exception: ImageCaptureException) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    },
                )
            }.onFailure {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun buildNotification(label: String, armed: Boolean): Notification {
        val openAppIntent = PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = PendingIntent.getService(this, 11, commandIntent(this, ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val disarmIntent = PendingIntent.getService(this, 16, commandIntent(this, ACTION_DISARM), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(if (armed) "شروع سریع آماده است" else "ضبط فعال است")
            .setContentText(if (armed) "$label · آماده برای شروع" else "$label · صفحه خاموش")
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (armed && armedForAudio) {
            val startAudioIntent = PendingIntent.getService(
                this,
                14,
                commandIntent(this, ACTION_START_AUDIO),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(android.R.drawable.ic_media_play, "شروع ضبط صدا", startAudioIntent)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "خاموش‌کردن اعلان", disarmIntent)
        } else if (armed) {
            val targetActivity = if (armedForPhoto) QuickPhotoStartActivity::class.java else QuickVideoStartActivity::class.java
            val backCameraIntent = PendingIntent.getActivity(
                this,
                12,
                Intent(this, targetActivity)
                    .putExtra(EXTRA_LENS, VideoLens.Back.name)
                    .putExtra(EXTRA_QUALITY, armedQuality.name),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val frontCameraIntent = PendingIntent.getActivity(
                this,
                13,
                Intent(this, targetActivity)
                    .putExtra(EXTRA_LENS, VideoLens.Front.name)
                    .putExtra(EXTRA_QUALITY, armedQuality.name),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(android.R.drawable.ic_media_play, if (armedForPhoto) "عکس با دوربین پشت" else "ویدیو با دوربین پشت", backCameraIntent)
            builder.addAction(android.R.drawable.ic_media_play, if (armedForPhoto) "عکس با دوربین جلو" else "ویدیو با دوربین جلو", frontCameraIntent)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "خاموش‌کردن اعلان", disarmIntent)
        } else {
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "پایان ضبط", stopIntent)
        }
        return builder.build().also { notification ->
            // setOngoing prevents normal swipe-to-dismiss; FLAG_NO_CLEAR also protects
            // the notification from notification shade "clear all" operations.
            notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR
        }
    }

    private fun buildCompletedNotification(isPhoto: Boolean = false, isAudio: Boolean = false): Notification {
        val previewIntent = PendingIntent.getActivity(
            this,
            13,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_VIDEO_PREVIEW, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val photoIntent = PendingIntent.getActivity(
            this,
            14,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_PHOTO_PREVIEW, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val audioIntent = PendingIntent.getActivity(
            this,
            15,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_AUDIO_PREVIEW, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contentIntent = when {
            isPhoto -> photoIntent
            isAudio -> audioIntent
            else -> previewIntent
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(when { isPhoto -> "عکس آماده است"; isAudio -> "صدای محیط آماده است"; else -> "ویدیو آماده است" })
            .setContentText(when { isPhoto -> "عکس با موفقیت ثبت شد"; isAudio -> "ضبط صدا با موفقیت پایان یافت"; else -> "ضبط ویدیو با موفقیت پایان یافت" })
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_media_play, when { isPhoto -> "مشاهده عکس"; isAudio -> "گوش دادن به صدا"; else -> "مشاهده ویدیو" }, contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun buildErrorNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle("ضبط صدا شروع نشد")
        .setContentText("مجوز میکروفون را بررسی کنید و دوباره تلاش کنید.")
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.recording_channel_name), NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "recording_status"
        private const val NOTIFICATION_ID = 1001
        private const val COMPLETED_NOTIFICATION_ID = 1002
        private const val ERROR_NOTIFICATION_ID = 1003
        private const val ACTION_START = "com.recbox.app.recording.START"
        private const val ACTION_ARM_VIDEO = "com.recbox.app.recording.ARM_VIDEO"
        private const val ACTION_ARM_PHOTO = "com.recbox.app.recording.ARM_PHOTO"
        private const val ACTION_ARM_AUDIO = "com.recbox.app.recording.ARM_AUDIO"
        private const val ACTION_START_VIDEO = "com.recbox.app.recording.START_VIDEO"
        private const val ACTION_START_PHOTO = "com.recbox.app.recording.START_PHOTO"
        private const val ACTION_START_AUDIO = "com.recbox.app.recording.START_AUDIO"
        private const val ACTION_PAUSE = "com.recbox.app.recording.PAUSE"
        private const val ACTION_RESUME = "com.recbox.app.recording.RESUME"
        private const val ACTION_DISARM = "com.recbox.app.recording.DISARM"
        private const val ACTION_STOP = "com.recbox.app.recording.STOP"
        private const val EXTRA_MODE = "extra_mode"
        private const val EXTRA_LENS = "extra_lens"
        private const val EXTRA_QUALITY = "extra_quality"

        fun start(context: Context, mode: RecordingMode) {
            ContextCompat.startForegroundService(context, commandIntent(context, ACTION_START).putExtra(EXTRA_MODE, mode.name))
        }

        fun startVideo(context: Context, lens: VideoLens, quality: VideoQuality) {
            val intent = commandIntent(context, ACTION_START_VIDEO)
                .putExtra(EXTRA_LENS, lens.name)
                .putExtra(EXTRA_QUALITY, quality.name)
            ContextCompat.startForegroundService(context, intent)
        }

        fun armVideo(context: Context, lens: VideoLens, quality: VideoQuality) {
            val intent = commandIntent(context, ACTION_ARM_VIDEO)
                .putExtra(EXTRA_LENS, lens.name)
                .putExtra(EXTRA_QUALITY, quality.name)
            ContextCompat.startForegroundService(context, intent)
        }

        fun armPhoto(context: Context, lens: VideoLens) {
            ContextCompat.startForegroundService(context, commandIntent(context, ACTION_ARM_PHOTO).putExtra(EXTRA_LENS, lens.name))
        }

        fun armAudio(context: Context) {
            ContextCompat.startForegroundService(context, commandIntent(context, ACTION_ARM_AUDIO))
        }

        fun startPhoto(context: Context, lens: VideoLens) {
            ContextCompat.startForegroundService(context, commandIntent(context, ACTION_START_PHOTO).putExtra(EXTRA_LENS, lens.name))
        }

        fun pause(context: Context) { context.startService(commandIntent(context, ACTION_PAUSE)) }
        fun resume(context: Context) { context.startService(commandIntent(context, ACTION_RESUME)) }
        fun stop(context: Context) { context.startService(commandIntent(context, ACTION_STOP)) }

        private fun commandIntent(context: Context, action: String) = Intent(context, RecordingForegroundService::class.java).setAction(action)
    }
}
