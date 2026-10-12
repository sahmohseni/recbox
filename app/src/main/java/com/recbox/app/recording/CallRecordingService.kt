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
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.recbox.app.MainActivity
import com.recbox.app.R
import com.recbox.app.data.CallRecordingStore
import com.recbox.app.data.ContactItem
import java.io.File

class CallRecordingService : LifecycleService() {
    private var recorder: MediaRecorder? = null
    private var recorderStarted = false
    private var callBecameActive = false
    private var isFinishing = false
    private var activeOutputFile: File? = null
    private var contact = ContactItem(0L, "مخاطب", "")
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: TelephonyCallback? = null
    private val stateHandler = Handler(Looper.getMainLooper())
    private val statePoller = object : Runnable {
        override fun run() {
            if (!isFinishing) {
                handleCallState(getSystemService(TelephonyManager::class.java).callState)
                stateHandler.postDelayed(this, 750L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        contact = ContactItem(
            0L,
            intent?.getStringExtra(EXTRA_NAME) ?: "مخاطب",
            intent?.getStringExtra(EXTRA_NUMBER) ?: "",
        )
        startForeground(NOTIFICATION_ID, buildActiveNotification())
        registerCallState()
        stateHandler.post(statePoller)
        return START_NOT_STICKY
    }

    private fun registerCallState() {
        unregisterCallState()
        val manager = getSystemService(TelephonyManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = handleCallState(state)
            }
            telephonyCallback = callback
            manager.registerTelephonyCallback(ContextCompat.getMainExecutor(this), callback)
        } else {
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String) = handleCallState(state)
            }
            phoneStateListener = listener
            @Suppress("DEPRECATION")
            manager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun handleCallState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                callBecameActive = true
                startRecorder()
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                // Android sends an initial IDLE immediately after registration.
                // It must not terminate the service before the outgoing call starts.
                if (callBecameActive) stopRecorderAndService()
            }
        }
    }

    private fun startRecorder() {
        if (recorderStarted) return
        val outputFile = File(File(filesDir, "calls").apply { mkdirs() }, "call_${System.currentTimeMillis()}.m4a")
        activeOutputFile = outputFile
        // Method 2: ask Android for the telephony audio path directly.
        // The accessibility service is required because Android treats it as a
        // privileged audio-capture client while a cellular call is active.
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_DOWNLINK,
            MediaRecorder.AudioSource.VOICE_UPLINK,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        )
        for (source in sources) {
            runCatching {
                MediaRecorder().apply {
                    setAudioSource(source)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(outputFile.absolutePath)
                    prepare()
                    start()
                    recorder = this
                    recorderStarted = true
                }
                return
            }
        }

        activeOutputFile = null
        getSystemService(NotificationManager::class.java).notify(
            COMPLETED_NOTIFICATION_ID,
            buildErrorNotification(),
        )
    }

    private fun stopRecorderAndService() {
        if (isFinishing) return
        isFinishing = true
        stateHandler.removeCallbacks(statePoller)
        if (recorderStarted) {
            runCatching { recorder?.stop() }
            recorder?.release()
            recorder = null
            recorderStarted = false
            val file = activeOutputFile
            if (file?.exists() == true) {
                CallRecordingStore.save(this, file, contact)
                getSystemService(NotificationManager::class.java).notify(COMPLETED_NOTIFICATION_ID, buildCompletedNotification())
            }
        }
        activeOutputFile = null
        unregisterCallState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun unregisterCallState() {
        val manager = getSystemService(TelephonyManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { manager.unregisterTelephonyCallback(it) }
            telephonyCallback = null
        } else {
            @Suppress("DEPRECATION")
            phoneStateListener?.let { manager.listen(it, PhoneStateListener.LISTEN_NONE) }
            phoneStateListener = null
        }
    }

    override fun onDestroy() {
        stateHandler.removeCallbacks(statePoller)
        if (recorderStarted) {
            runCatching { recorder?.stop() }
            recorder?.release()
            recorder = null
            recorderStarted = false
        }
        activeOutputFile = null
        unregisterCallState()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    private fun buildActiveNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_call)
        .setContentTitle("ضبط مستقیم مکالمه فعال است")
        .setContentText("تماس با ${contact.name}")
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun buildCompletedNotification(): Notification {
        val intent = PendingIntent.getActivity(
            this,
            30,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_CALL_PREVIEW, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("ضبط مکالمه آماده است")
            .setContentText("مکالمه با ${contact.name} ذخیره شد")
            .setContentIntent(intent)
            .addAction(android.R.drawable.ic_media_play, "گوش دادن به مکالمه", intent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun buildErrorNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle("ضبط مکالمه شروع نشد")
        .setContentText("دسترسی سرویس ویژه یا منبع صدای تماس توسط دستگاه محدود شده است")
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.recording_channel_name), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "recording_status"
        private const val NOTIFICATION_ID = 1003
        private const val COMPLETED_NOTIFICATION_ID = 1004
        private const val EXTRA_NAME = "contact_name"
        private const val EXTRA_NUMBER = "contact_number"

        fun start(context: Context, contact: ContactItem) {
            val intent = Intent(context, CallRecordingService::class.java)
                .putExtra(EXTRA_NAME, contact.name)
                .putExtra(EXTRA_NUMBER, contact.phoneNumber)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
