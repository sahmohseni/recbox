package com.recbox.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.recbox.app.R
import com.recbox.app.data.RecordingMode
import com.recbox.app.data.AudioRecordingStore
import com.recbox.app.data.ArchiveRepository
import com.recbox.app.data.ArchivedMedia
import com.recbox.app.data.ContactItem
import com.recbox.app.data.ContactRepository
import com.recbox.app.data.CallRecordingStore
import com.recbox.app.data.VideoLens
import com.recbox.app.data.VideoQuality
import com.recbox.app.data.VideoRecordingStore
import com.recbox.app.data.PhotoStore
import com.recbox.app.recording.CallAudioAccess
import com.recbox.app.recording.RecordingForegroundService
import com.recbox.app.recording.CallRecordingService
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private object Routes {
    const val Welcome = "welcome"
    const val Home = "home"
    const val VideoSetup = "video_setup"
    const val VideoPreview = "video_preview"
    const val AudioSetup = "audio_setup"
    const val AudioPreview = "audio_preview"
    const val PhotoSetup = "photo_setup"
    const val PhotoPreview = "photo_preview"
    const val CallContacts = "call_contacts"
    const val CallActive = "call_active"
    const val CallPreview = "call_preview"
    const val Library = "library"
    const val Settings = "settings"
    const val Recording = "recording/{mode}"
}

private data class QuickStartRequest(
    val mode: RecordingMode,
    val lens: VideoLens? = null,
    val quality: VideoQuality? = null,
)

@Composable
fun RecboxApp(
    openVideoPreview: Boolean = false,
    openPhotoPreview: Boolean = false,
    openCallPreview: Boolean = false,
    openAudioPreview: Boolean = false,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    var activeMode by rememberSaveable { mutableStateOf(RecordingMode.AmbientAudio.name) }
    var recordingSeconds by rememberSaveable { mutableIntStateOf(0) }
    var isPaused by rememberSaveable { mutableStateOf(false) }
    var videoSelection by remember { mutableStateOf<Pair<VideoLens, VideoQuality>?>(null) }
    var photoSelection by remember { mutableStateOf<VideoLens?>(null) }
    var pendingCallContact by remember { mutableStateOf<ContactItem?>(null) }
    var pendingQuickStart by remember { mutableStateOf<QuickStartRequest?>(null) }
    val permissionsForMode = remember {
        { mode: RecordingMode ->
            buildList {
                if (mode != RecordingMode.Photo) add(Manifest.permission.RECORD_AUDIO)
                if (mode == RecordingMode.Video || mode == RecordingMode.Photo) add(Manifest.permission.CAMERA)
                if (mode == RecordingMode.Call) add(Manifest.permission.READ_PHONE_STATE)
                if (mode == RecordingMode.Call) add(Manifest.permission.CALL_PHONE)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray()
        }
    }
    var pendingMode by remember { mutableStateOf<RecordingMode?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (pendingQuickStart != null) {
            val request = pendingQuickStart!!
            val granted = permissionsForMode(request.mode).all { permission ->
                permissions[permission] == true || ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
            if (granted) {
                when (request.mode) {
                    RecordingMode.Video -> RecordingForegroundService.armVideo(
                        context,
                        request.lens ?: VideoLens.Back,
                        request.quality ?: VideoQuality.FullHd,
                    )
                    RecordingMode.Photo -> RecordingForegroundService.armPhoto(context, request.lens ?: VideoLens.Back)
                    RecordingMode.AmbientAudio -> RecordingForegroundService.armAudio(context)
                    else -> Unit
                }
            }
            pendingQuickStart = null
        } else if (pendingCallContact != null) {
            val contact = pendingCallContact!!
            val requestedPermissions = permissionsForMode(RecordingMode.Call)
            val granted = requestedPermissions.all { permission ->
                permissions[permission] == true || ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
            if (granted) {
                CallRecordingStore.clear(context)
                CallRecordingService.start(context, contact)
                context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${contact.phoneNumber}")))
                navController.navigate(Routes.CallActive)
            }
            pendingCallContact = null
            pendingMode = null
        } else if (photoSelection != null) {
            val lens = photoSelection!!
            val requestedPermissions = permissionsForMode(RecordingMode.Photo)
            val granted = requestedPermissions.all { permission ->
                permissions[permission] == true || ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
            if (granted) {
                PhotoStore.clear(context)
                activeMode = RecordingMode.Photo.name
                recordingSeconds = 0
                isPaused = false
                RecordingForegroundService.startPhoto(context, lens)
                navController.navigate(Routes.PhotoPreview)
            }
            photoSelection = null
            pendingMode = null
        } else if (videoSelection != null) {
            val (lens, quality) = videoSelection!!
            val requestedPermissions = permissionsForMode(RecordingMode.Video)
            val granted = requestedPermissions.all { permission ->
                permissions[permission] == true || ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
            if (granted) {
                VideoRecordingStore.clear(context)
                activeMode = RecordingMode.Video.name
                recordingSeconds = 0
                isPaused = false
                RecordingForegroundService.startVideo(context, lens, quality)
                navController.navigate(Routes.Recording.replace("{mode}", RecordingMode.Video.name))
            }
            videoSelection = null
            pendingMode = null
        } else {
            val requestedPermissions = permissionsForMode(pendingMode ?: RecordingMode.AmbientAudio)
            val granted = requestedPermissions.all { permission ->
                permissions[permission] == true || ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
            pendingMode?.takeIf { granted }?.let { mode ->
                activeMode = mode.name
                recordingSeconds = 0
                isPaused = false
                RecordingForegroundService.start(context, mode)
                navController.navigate("recording/${mode.name}")
            }
            pendingMode = null
        }
    }

    fun requestQuickStart(mode: RecordingMode, lens: VideoLens? = null, quality: VideoQuality? = null) {
        val missing = permissionsForMode(mode).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            when (mode) {
                RecordingMode.Video -> RecordingForegroundService.armVideo(context, lens ?: VideoLens.Back, quality ?: VideoQuality.FullHd)
                RecordingMode.Photo -> RecordingForegroundService.armPhoto(context, lens ?: VideoLens.Back)
                RecordingMode.AmbientAudio -> RecordingForegroundService.armAudio(context)
                else -> Unit
            }
        } else {
            pendingQuickStart = QuickStartRequest(mode, lens, quality)
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    fun requestRecording(mode: RecordingMode) {
        if (mode == RecordingMode.AmbientAudio) {
            navController.navigate(Routes.AudioSetup)
            return
        }
        if (mode == RecordingMode.Video) {
            navController.navigate(Routes.VideoSetup)
            return
        }
        if (mode == RecordingMode.Photo) {
            navController.navigate(Routes.PhotoSetup)
            return
        }
        if (mode == RecordingMode.Call) {
            navController.navigate(Routes.CallContacts)
            return
        }
        pendingMode = mode
        val missing = permissionsForMode(mode).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            pendingMode = null
            activeMode = mode.name
            recordingSeconds = 0
            isPaused = false
            RecordingForegroundService.start(context, mode)
            navController.navigate("recording/${mode.name}")
        } else permissionLauncher.launch(missing.toTypedArray())
    }

    Surface(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        color = MaterialTheme.colorScheme.background,
    ) {
        NavHost(navController = navController, startDestination = when {
            openVideoPreview -> Routes.VideoPreview
            openPhotoPreview -> Routes.PhotoPreview
            openCallPreview -> Routes.CallPreview
            openAudioPreview -> Routes.AudioPreview
            else -> Routes.Welcome
        }) {
            composable(Routes.Welcome) {
                WelcomeScreen {
                    navController.navigate(Routes.Home) {
                        popUpTo(Routes.Welcome) { inclusive = true }
                    }
                }
            }
            composable(Routes.Home) {
                HomeScreen(
                    onModeSelected = ::requestRecording,
                    onLibrary = { navController.navigate(Routes.Library) },
                    onSettings = { navController.navigate(Routes.Settings) },
                )
            }
            composable(Routes.VideoSetup) {
                VideoSetupScreen(
                    onBack = { navController.popBackStack() },
                    onQuickStartChange = { enabled, lens, quality ->
                        if (enabled) requestQuickStart(RecordingMode.Video, lens, quality)
                        else RecordingForegroundService.stop(context)
                    },
                    onStart = { lens, quality ->
                        videoSelection = lens to quality
                        val missing = permissionsForMode(RecordingMode.Video).filter {
                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                        }
                        if (missing.isEmpty()) {
                            videoSelection = null
                            VideoRecordingStore.clear(context)
                            activeMode = RecordingMode.Video.name
                            recordingSeconds = 0
                            isPaused = false
                            RecordingForegroundService.startVideo(context, lens, quality)
                            navController.navigate(Routes.Recording.replace("{mode}", RecordingMode.Video.name))
                        } else permissionLauncher.launch(missing.toTypedArray())
                    },
                )
            }
            composable(Routes.AudioSetup) {
                AudioSetupScreen(
                    onBack = { navController.popBackStack() },
                    onQuickStartChange = { enabled ->
                        if (enabled) requestQuickStart(RecordingMode.AmbientAudio)
                        else RecordingForegroundService.stop(context)
                    },
                    onStart = {
                        pendingMode = RecordingMode.AmbientAudio
                        val missing = permissionsForMode(RecordingMode.AmbientAudio).filter {
                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                        }
                        if (missing.isEmpty()) {
                            pendingMode = null
                            AudioRecordingStore.clear(context)
                            activeMode = RecordingMode.AmbientAudio.name
                            recordingSeconds = 0
                            isPaused = false
                            RecordingForegroundService.start(context, RecordingMode.AmbientAudio)
                            navController.navigate(Routes.Recording.replace("{mode}", RecordingMode.AmbientAudio.name))
                        } else permissionLauncher.launch(missing.toTypedArray())
                    },
                )
            }
            composable(Routes.PhotoSetup) {
                PhotoSetupScreen(
                    onBack = { navController.popBackStack() },
                    onQuickStartChange = { enabled, lens ->
                        if (enabled) requestQuickStart(RecordingMode.Photo, lens)
                        else RecordingForegroundService.stop(context)
                    },
                    onStart = { lens ->
                        photoSelection = lens
                        val missing = permissionsForMode(RecordingMode.Photo).filter {
                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                        }
                        if (missing.isEmpty()) {
                            photoSelection = null
                            PhotoStore.clear(context)
                            activeMode = RecordingMode.Photo.name
                            recordingSeconds = 0
                            isPaused = false
                            RecordingForegroundService.startPhoto(context, lens)
                            navController.navigate(Routes.PhotoPreview)
                        } else permissionLauncher.launch(missing.toTypedArray())
                    },
                )
            }
            composable(Routes.CallContacts) {
                var contacts by remember { mutableStateOf(emptyList<ContactItem>()) }
                var accessibilityEnabled by remember { mutableStateOf(CallAudioAccess.isEnabled(context)) }
                val lifecycleOwner = LocalLifecycleOwner.current
                val contactsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (granted) contacts = ContactRepository.load(context)
                }
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            accessibilityEnabled = CallAudioAccess.isEnabled(context)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                LaunchedEffect(Unit) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                        contacts = ContactRepository.load(context)
                    } else {
                        contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                }
                ContactPickerScreen(
                    contacts = contacts,
                    accessibilityEnabled = accessibilityEnabled,
                    onEnableAccessibility = { CallAudioAccess.openSettings(context) },
                    onBack = { navController.popBackStack() },
                    onStartCall = { contact ->
                        if (!CallAudioAccess.isEnabled(context)) {
                            CallAudioAccess.openSettings(context)
                        } else {
                            pendingCallContact = contact
                            val missing = permissionsForMode(RecordingMode.Call).filter {
                                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                            }
                            if (missing.isEmpty()) {
                                pendingCallContact = null
                                CallRecordingStore.clear(context)
                                CallRecordingService.start(context, contact)
                                context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${contact.phoneNumber}")))
                                navController.navigate(Routes.CallActive)
                            } else permissionLauncher.launch(missing.toTypedArray())
                        }
                    },
                )
            }
            composable(Routes.CallActive) {
                LaunchedEffect(Unit) {
                    repeat(180) {
                        if (CallRecordingStore.last(context) != null) {
                            navController.navigate(Routes.CallPreview) { popUpTo(Routes.CallActive) { inclusive = true } }
                            return@LaunchedEffect
                        }
                        delay(1000)
                    }
                }
                CallActiveScreen(onBack = { navController.navigate(Routes.Home) { popUpTo(Routes.Home) { inclusive = true } } })
            }
            composable(Routes.Library) {
                LibraryScreen(
                    onHome = { navController.navigate(Routes.Home) { popUpTo(navController.graph.findStartDestination().id) } },
                )
            }
            composable(Routes.Settings) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onHome = { navController.navigate(Routes.Home) { popUpTo(navController.graph.findStartDestination().id) } },
                    onLibrary = { navController.navigate(Routes.Library) },
                )
            }
            composable(Routes.Recording) {
                LaunchedEffect(isPaused) {
                    if (activeMode != RecordingMode.Photo.name) {
                        while (!isPaused) { delay(1000); recordingSeconds++ }
                    }
                }
                RecordingScreen(
                    mode = RecordingMode.entries.firstOrNull { it.name == activeMode } ?: RecordingMode.AmbientAudio,
                    seconds = recordingSeconds.toLong(),
                    paused = isPaused,
                    onPause = { isPaused = !isPaused; if (isPaused) RecordingForegroundService.pause(context) else RecordingForegroundService.resume(context) },
                    onStop = { RecordingForegroundService.stop(context); when (activeMode) {
                        RecordingMode.Video.name -> navController.navigate(Routes.VideoPreview)
                        RecordingMode.Photo.name -> navController.navigate(Routes.PhotoPreview)
                        RecordingMode.AmbientAudio.name -> navController.navigate(Routes.AudioPreview)
                        else -> navController.navigate(Routes.Home) { popUpTo(Routes.Home) { inclusive = true } }
                    } },
                    onClose = { RecordingForegroundService.stop(context); navController.popBackStack() },
                )
            }
            composable(Routes.VideoPreview) {
                VideoPreviewScreen(
                    onBack = { navController.navigate(Routes.Home) { popUpTo(Routes.Home) { inclusive = true } } },
                )
            }
            composable(Routes.PhotoPreview) {
                PhotoPreviewScreen(
                    onBack = { navController.navigate(Routes.Home) { popUpTo(Routes.Home) { inclusive = true } } },
                )
            }
            composable(Routes.CallPreview) {
                CallPreviewScreen(
                    onBack = { navController.navigate(Routes.Home) { popUpTo(Routes.Home) { inclusive = true } } },
                )
            }
            composable(Routes.AudioPreview) {
                AudioPreviewScreen(
                    onBack = { navController.navigate(Routes.Home) { popUpTo(Routes.Home) { inclusive = true } } },
                )
            }
        }
    }
}

private val pagePadding = Modifier.padding(horizontal = 22.dp)

@Composable
private fun WelcomeScreen(onContinue: () -> Unit) {
    ScreenColumn { 
        Spacer(Modifier.height(30.dp))
        Brand()
        Spacer(Modifier.height(90.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(150.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .18f)))
            Image(
                painter = painterResource(R.drawable.ic_recbox_launcher),
                contentDescription = "RecBox",
                modifier = Modifier.size(104.dp).clip(RoundedCornerShape(30.dp)),
            )
        }
        Spacer(Modifier.weight(1f))
        Text("لحظه‌ها، امن و بی‌صدا", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("هر چیزی که مهم است،\nدر لحظه ثبت کن.", fontSize = 30.sp, lineHeight = 43.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text("ضبط صدا، تصویر و تماس با کنترل کامل روی حریم خصوصی شما.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 25.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(28.dp))
        PrimaryButton("شروع کنید", onContinue)
        Text("با ادامه دادن، شرایط استفاده را می‌پذیرید.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.fillMaxWidth().padding(top = 13.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun AudioSetupScreen(
    onBack: () -> Unit,
    onQuickStartChange: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    var quickStartEnabled by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "بازگشت") }
            Text("تنظیم ضبط صدای محیط", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                        Text("ضبط صدای محیط", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("میکروفون گوشی برای ثبت صدای اطراف استفاده می‌شود.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 17.sp)
                    }
                }
                Text("کیفیت صدا: AAC · ۱۲۸ کیلوبیت بر ثانیه", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
        }
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("شروع سریع از اعلان", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("پس از بستن اپ، ضبط صدا را از اعلان شروع و متوقف کنید", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 17.sp)
            }
            Switch(
                checked = quickStartEnabled,
                onCheckedChange = { enabled ->
                    quickStartEnabled = enabled
                    onQuickStartChange(enabled)
                },
            )
        }
        Text("با فعال بودن اعلان، سرویس ضبط در پس‌زمینه اجرا می‌شود و صفحه‌ی گوشی می‌تواند خاموش باشد.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 19.sp)
        Spacer(Modifier.weight(1f))
        PrimaryButton("شروع ضبط صدا", onStart)
    }
}

@Composable
private fun VideoSetupScreen(
    onBack: () -> Unit,
    onQuickStartChange: (Boolean, VideoLens, VideoQuality) -> Unit,
    onStart: (VideoLens, VideoQuality) -> Unit,
) {
    var selectedLens by rememberSaveable { mutableStateOf(VideoLens.Back.name) }
    var selectedQuality by rememberSaveable { mutableStateOf(VideoQuality.FullHd.name) }
    var quickStartEnabled by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "بازگشت") }
            Text("تنظیم ضبط ویدیو", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("شروع سریع از اعلان", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("پس از بستن اپ، دوربین را از خود اعلان انتخاب کنید", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
            Switch(
                checked = quickStartEnabled,
                onCheckedChange = { enabled ->
                    quickStartEnabled = enabled
                    onQuickStartChange(enabled, VideoLens.valueOf(selectedLens), VideoQuality.valueOf(selectedQuality))
                },
            )
        }
        Text("دوربین موردنظر را انتخاب کنید", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        VideoOptionCard(VideoLens.Back.label, "مناسب برای ضبط محیط", selectedLens == VideoLens.Back.name) { selectedLens = VideoLens.Back.name }
        VideoOptionCard(VideoLens.Front.label, "مناسب برای ضبط روبه‌رو", selectedLens == VideoLens.Front.name) { selectedLens = VideoLens.Front.name }
        Spacer(Modifier.height(10.dp))
        Text("کیفیت ویدیو", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        VideoQuality.entries.forEach { quality ->
            VideoOptionCard(quality.label, if (quality == VideoQuality.Uhd) "مصرف باتری و فضای بیشتر" else "تعادل کیفیت و حجم فایل", selectedQuality == quality.name) { selectedQuality = quality.name }
        }
        Spacer(Modifier.weight(1f))
        Text("ضبط با اعلان دائمی ادامه پیدا می‌کند و پس از خاموش شدن صفحه نیز فعال می‌ماند.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 19.sp)
        PrimaryButton("شروع ضبط ویدیو", { onStart(VideoLens.valueOf(selectedLens), VideoQuality.valueOf(selectedQuality)) })
    }
}

@Composable
private fun PhotoSetupScreen(
    onBack: () -> Unit,
    onQuickStartChange: (Boolean, VideoLens) -> Unit,
    onStart: (VideoLens) -> Unit,
) {
    var selectedLens by rememberSaveable { mutableStateOf(VideoLens.Back.name) }
    var quickStartEnabled by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "بازگشت") }
            Text("تنظیم ثبت عکس", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("شروع سریع از اعلان", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("پس از بستن اپ، دوربین را از خود اعلان انتخاب کنید", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
            Switch(
                checked = quickStartEnabled,
                onCheckedChange = { enabled ->
                    quickStartEnabled = enabled
                    onQuickStartChange(enabled, VideoLens.valueOf(selectedLens))
                },
            )
        }
        Text("دوربین موردنظر را انتخاب کنید", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        VideoOptionCard(VideoLens.Back.label, "دوربین اصلی گوشی", selectedLens == VideoLens.Back.name) { selectedLens = VideoLens.Back.name }
        VideoOptionCard(VideoLens.Front.label, "دوربین سلفی", selectedLens == VideoLens.Front.name) { selectedLens = VideoLens.Front.name }
        Spacer(Modifier.weight(1f))
        Text("با لمس دکمه، یک عکس با دوربین انتخاب‌شده ثبت می‌شود.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        PrimaryButton("ثبت عکس", { onStart(VideoLens.valueOf(selectedLens)) })
    }
}

@Composable
private fun VideoOptionCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .18f) else MaterialTheme.colorScheme.surface), border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Box(Modifier.size(20.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                if (selected) Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ContactPickerScreen(
    contacts: List<ContactItem>,
    accessibilityEnabled: Boolean,
    onEnableAccessibility: () -> Unit,
    onBack: () -> Unit,
    onStartCall: (ContactItem) -> Unit,
) {
    var selected by remember { mutableStateOf<ContactItem?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredContacts = contacts.filter { contact ->
        searchQuery.isBlank() || contact.name.contains(searchQuery.trim(), ignoreCase = true) || contact.phoneNumber.contains(searchQuery.trim())
    }
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "بازگشت") }
            Text("انتخاب مخاطب", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Text("مخاطبی را که می‌خواهید با او تماس بگیرید انتخاب کنید.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp, bottom = 14.dp))
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (accessibilityEnabled) MaterialTheme.colorScheme.secondary.copy(alpha = .12f) else MaterialTheme.colorScheme.primary.copy(alpha = .12f)),
        ) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = if (accessibilityEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(if (accessibilityEnabled) "دسترسی ضبط مستقیم فعال است" else "فعال‌سازی ضبط مستقیم تماس", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(if (accessibilityEnabled) "منبع صدای داخلی تماس آماده‌ی استفاده است." else "برای روش دوم، سرویس دسترسی ویژه‌ی خاموش را یک‌بار فعال کنید.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, lineHeight = 16.sp)
                }
                if (!accessibilityEnabled) {
                    TextButton(onClick = onEnableAccessibility) { Text("فعال‌سازی", fontSize = 10.sp) }
                }
            }
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            singleLine = true,
            placeholder = { Text("جست‌وجوی نام یا شماره") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        )
        if (filteredContacts.isEmpty()) {
            Text("مخاطبی برای نمایش پیدا نشد یا مجوز مخاطبین داده نشده است.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 40.dp))
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredContacts, key = { "${it.id}_${it.phoneNumber}" }) { contact ->
                    Card(onClick = { selected = contact }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (selected == contact) MaterialTheme.colorScheme.primary.copy(alpha = .18f) else MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) { Text(contact.name.take(1), fontWeight = FontWeight.Bold) }
                            Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                                Text(contact.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(contact.phoneNumber, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr), modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                            }
                            Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
        Text("در این روش صدا از مسیر داخلی تماس دریافت می‌شود و نیازی به بلندگو نیست. نتیجه به نسخه‌ی Android و مدل گوشی وابسته است.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 12.dp))
        PrimaryButton("تماس و شروع ضبط", { selected?.let(onStartCall) })
    }
}

@Composable
private fun CallActiveScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "بازگشت") }
            Text("تماس در حال انجام", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(112.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(48.dp))
        }
        Text("ضبط مکالمه فعال است", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp))
        Text("پس از پایان تماس، فایل ضبط‌شده برای شما نمایش داده می‌شود.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        Text("برای پایان تماس از برنامه‌ی تلفن استفاده کنید.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    }
}

@Composable
private fun CallPreviewScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val call = remember { CallRecordingStore.last(context) }
    val file = call?.first
    val contact = call?.second
    val uri = file?.let { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
    val player = remember(uri) { uri?.let { android.media.MediaPlayer.create(context, it) } }
    var playing by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    DisposableEffect(player) { onDispose { player?.release() } }

    Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "بازگشت") }
            Text("ضبط مکالمه", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(110.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .18f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Phone, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(46.dp)) }
        Text(contact?.name ?: "مکالمه", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(contact?.phoneNumber.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Text("فایل ضبط‌شده آماده است", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        PrimaryButton(if (playing) "توقف پخش" else "گوش دادن به مکالمه", {
            player?.let {
                if (it.isPlaying) { it.pause(); playing = false } else { it.start(); playing = true }
            }
        })
        PrimaryButton("ذخیره در گالری", {
            if (file != null && contact != null && CallRecordingStore.copyToGallery(context, file, contact) != null) saved = true
        })
        if (saved) Text("فایل در پوشه Music/Recbox ذخیره شد.", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun VideoPreviewScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var videoPath by remember { mutableStateOf<String?>(null) }
    var duration by remember { mutableStateOf(0L) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repeat(12) {
            VideoRecordingStore.last(context)?.let { (file, seconds) ->
                videoPath = file.absolutePath
                duration = seconds
                return@LaunchedEffect
            }
            delay(250)
        }
    }

    val file = videoPath?.let(::File)
    val videoUri = file?.let { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "بازگشت") }
            Text("پیش‌نمایش ویدیو", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (videoUri != null) {
            AndroidView(
                factory = { viewContext ->
                    android.widget.VideoView(viewContext).apply {
                        setVideoURI(videoUri)
                        setMediaController(android.widget.MediaController(viewContext).also { it.setAnchorView(this) })
                        setOnPreparedListener { it.isLooping = false; start() }
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(Color.Black),
                update = { it.setVideoURI(videoUri) },
            )
            Text("مدت ضبط: ${formatDuration(duration)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            PrimaryButton("ذخیره در گالری", {
                file?.let { source ->
                    val uri = VideoRecordingStore.copyToGallery(context, source)
                    if (uri != null) saved = true
                }
            })
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, videoUri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("باز کردن با برنامه ویدیو") }
            if (saved) Text("ویدیو در پوشه Movies/Recbox ذخیره شد.", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            Spacer(Modifier.weight(1f))
            Text("ویدیو در حال آماده‌سازی است…", modifier = Modifier.align(Alignment.CenterHorizontally), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PhotoPreviewScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var photoPath by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repeat(12) {
            PhotoStore.last(context)?.let {
                photoPath = it.absolutePath
                return@LaunchedEffect
            }
            delay(250)
        }
    }

    val file = photoPath?.let(::File)
    val photoUri = file?.let { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "بازگشت") }
            Text("پیش‌نمایش عکس", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (photoUri != null) {
            AndroidView(
                factory = { viewContext -> android.widget.ImageView(viewContext).apply { scaleType = android.widget.ImageView.ScaleType.CENTER_CROP; setImageURI(photoUri) } },
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(Color.Black),
                update = { it.setImageURI(photoUri) },
            )
            PrimaryButton("ذخیره در گالری", {
                file?.let { source ->
                    if (PhotoStore.copyToGallery(context, source) != null) saved = true
                }
            })
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, photoUri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("باز کردن با برنامه عکس") }
            if (saved) Text("عکس در پوشه Pictures/Recbox ذخیره شد.", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            Spacer(Modifier.weight(1f))
            Text("در حال ثبت عکس…", modifier = Modifier.align(Alignment.CenterHorizontally), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun AudioPreviewScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var audioPath by remember { mutableStateOf<String?>(null) }
    var duration by remember { mutableStateOf(0L) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repeat(20) {
            AudioRecordingStore.last(context)?.let { (file, seconds) ->
                audioPath = file.absolutePath
                duration = seconds
                return@LaunchedEffect
            }
            delay(250)
        }
    }

    val file = audioPath?.let(::File)
    val audioUri = file?.let { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
    val player = remember(audioUri) { audioUri?.let { android.media.MediaPlayer.create(context, it) } }
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        player?.setOnCompletionListener { playing = false }
        onDispose { player?.release() }
    }

    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "بازگشت") }
            Text("پیش‌نمایش صدای محیط", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (audioUri != null) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(82.dp))
            }
            Text("فایل صدای محیط آماده است", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("مدت ضبط: ${formatDuration(duration)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.weight(1f))
            PrimaryButton(if (playing) "توقف پخش" else "گوش دادن به صدا", {
                player?.let {
                    if (it.isPlaying) {
                        it.pause()
                        playing = false
                    } else {
                        it.start()
                        playing = true
                    }
                }
            })
            PrimaryButton("ذخیره در موسیقی", {
                file?.let { if (AudioRecordingStore.copyToGallery(context, it) != null) saved = true }
            })
            if (saved) Text("فایل در پوشه Music/Recbox ذخیره شد.", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            Spacer(Modifier.weight(1f))
            Text("فایل صوتی در حال آماده‌سازی است…", modifier = Modifier.align(Alignment.CenterHorizontally), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeScreen(onModeSelected: (RecordingMode) -> Unit, onLibrary: () -> Unit, onSettings: () -> Unit) {
    Scaffold(bottomBar = { BottomNav(selected = 0, onHome = {}, onLibrary = onLibrary) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).then(pagePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Spacer(Modifier.height(12.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text("عصر بخیر،", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp); Text("آرمان ✦", fontSize = 23.sp, fontWeight = FontWeight.Bold) }; IconButton(onClick = onSettings) { Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) { Text("آ", fontWeight = FontWeight.Bold) } } } }
            item { PrivacyPill() }
            item { HeroCard() }
            item { SectionTitle("ضبط جدید", "۴ حالت") }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { ModeCard(RecordingMode.AmbientAudio, Icons.Default.GraphicEq, onModeSelected, Modifier.weight(1f)); ModeCard(RecordingMode.Video, Icons.Default.Videocam, onModeSelected, Modifier.weight(1f)) } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { ModeCard(RecordingMode.Photo, Icons.Default.CameraAlt, onModeSelected, Modifier.weight(1f)); ModeCard(RecordingMode.Call, Icons.Default.Phone, onModeSelected, Modifier.weight(1f)) } }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun LibraryScreen(onHome: () -> Unit) {
    val context = LocalContext.current
    var selectedFilter by rememberSaveable { mutableStateOf("همه") }
    var recordings by remember { mutableStateOf(ArchiveRepository.load(context)) }
    var savedFiles by remember { mutableStateOf(emptySet<String>()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) recordings = ArchiveRepository.load(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val filters = listOf("همه", "صدا", "ویدیو", "عکس", "مکالمه")
    val filteredRecordings = recordings.filter { item ->
        selectedFilter == "همه" || item.mode.label == selectedFilter
    }

    fun openMedia(item: ArchivedMedia) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", item.file)
        val mimeType = when (item.mode) {
            RecordingMode.Video -> "video/mp4"
            RecordingMode.Photo -> "image/jpeg"
            RecordingMode.Call, RecordingMode.AmbientAudio -> "audio/mp4"
        }
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri)
                    .setDataAndType(uri, mimeType)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }
    }

    fun saveMedia(item: ArchivedMedia) {
        val saved = when (item.mode) {
            RecordingMode.Video -> VideoRecordingStore.copyToGallery(context, item.file)
            RecordingMode.Photo -> PhotoStore.copyToGallery(context, item.file)
            RecordingMode.AmbientAudio -> AudioRecordingStore.copyToGallery(context, item.file)
            RecordingMode.Call -> CallRecordingStore.copyToGallery(context, item.file, ContactItem(0L, "مکالمه", ""))
        } != null
        if (saved) savedFiles = savedFiles + item.file.absolutePath
    }

    Scaffold(bottomBar = { BottomNav(selected = 1, onHome = onHome, onLibrary = {}) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).then(pagePadding)) {
            Spacer(Modifier.height(18.dp))
            PageHeader("کتابخانه شخصی", "آرشیو من")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 18.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                filters.forEach { filter ->
                    FilterChip(filter, selectedFilter == filter) { selectedFilter = filter }
                }
            }
            Text(
                "${filteredRecordings.size} فایل",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            if (filteredRecordings.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                        Text("هنوز فایلی در این بخش وجود ندارد", fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                        Text("ضبط‌ها و تصاویر جدید اینجا نمایش داده می‌شوند.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredRecordings, key = { it.file.absolutePath }) { item ->
                        ArchiveRow(
                            item = item,
                            saved = item.file.absolutePath in savedFiles,
                            onOpen = { openMedia(item) },
                            onSave = { saveMedia(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit, onHome: () -> Unit, onLibrary: () -> Unit) {
    Scaffold(bottomBar = { BottomNav(selected = -1, onHome = onHome, onLibrary = onLibrary) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).then(pagePadding), verticalArrangement = Arrangement.spacedBy(8.dp)) { item { Spacer(Modifier.height(18.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { PageHeader("پروفایل و کنترل", "تنظیمات"); IconButton(onClick = onBack) { Icon(Icons.Default.Close, null) } } }; item { ProfileCard() }; item { SettingsGroup("ضبط و حریم خصوصی", listOf("مجوزهای دسترسی" to "دوربین، میکروفون و اعلان‌ها", "نشانگر ضبط" to "نمایش وضعیت هنگام ضبط", "کیفیت ضبط" to "بالا · فضای ذخیره‌سازی بهینه")) }; item { SettingsGroup("عمومی", listOf("حالت تاریک" to "فعال", "راهنما و پشتیبانی" to "پاسخ به پرسش‌های شما")) } }
    }
}

@Composable
private fun RecordingScreen(mode: RecordingMode, seconds: Long, paused: Boolean, onPause: () -> Unit, onStop: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(22.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onClose) { Icon(Icons.Default.Close, null) }; Text(if (paused) "ضبط متوقف" else "●  در حال ضبط", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp); IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, null) } }; Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(mode.label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp); Text(formatDuration(seconds), fontSize = 45.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 20.dp)); Text("〰〰〰〰〰〰〰", color = MaterialTheme.colorScheme.primary, fontSize = 23.sp); Text("●  ضبط در پس‌زمینه فعال است", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, modifier = Modifier.padding(top = 30.dp)); Spacer(Modifier.weight(1f)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onPause) { Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }; IconButton(onClick = onStop, modifier = Modifier.size(74.dp).clip(CircleShape).background(Color(0xFFEF6F7F))) { Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(30.dp)) }; IconButton(onClick = {}) { Icon(Icons.Default.Archive, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
}

@Composable private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxSize().then(pagePadding), content = content) }
@Composable private fun Brand() { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(11.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)); Spacer(Modifier.width(8.dp)); Text("RecBox", fontSize = 19.sp, fontWeight = FontWeight.Bold) } }
@Composable private fun PrimaryButton(text: String, onClick: () -> Unit) { Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) { Text("$text  ←", fontWeight = FontWeight.Bold) } }
@Composable private fun PrivacyPill() { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondary.copy(alpha = .08f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary)); Spacer(Modifier.width(7.dp)); Text("حریم خصوصی فعال است", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp); Spacer(Modifier.weight(1f)); TextButton(onClick = {}) { Text("جزئیات", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp) } } }
@Composable private fun HeroCard() { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF26223E)), shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(20.dp)) { Text("کنترل کامل در دستان شما", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp); Text("چه چیزی را\nثبت کنیم؟", fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)); Text("یک حالت را انتخاب کنید تا شروع کنیم.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp)) } } }
@Composable private fun SectionTitle(title: String, action: String, onClick: (() -> Unit)? = null) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold); TextButton(onClick = { onClick?.invoke() }, enabled = onClick != null) { Text(action, color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp) } } }
@Composable private fun ModeCard(mode: RecordingMode, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: (RecordingMode) -> Unit, modifier: Modifier) { Card(onClick = { onClick(mode) }, modifier = modifier.height(118.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1E2B))) { Column(Modifier.padding(14.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp)); Spacer(Modifier.height(10.dp)); Text(mode.label, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(if (mode == RecordingMode.Video) "با صفحه خاموش" else "ثبت سریع", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp)); Spacer(Modifier.weight(1f)); Icon(Icons.Default.KeyboardArrowLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)) } } }
@Composable
private fun ArchiveRow(item: ArchivedMedia, saved: Boolean, onOpen: () -> Unit, onSave: () -> Unit) {
    val icon = when (item.mode) {
        RecordingMode.Video -> Icons.Default.Videocam
        RecordingMode.Photo -> Icons.Default.CameraAlt
        RecordingMode.Call -> Icons.Default.Phone
        RecordingMode.AmbientAudio -> Icons.Default.GraphicEq
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF151722)), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(item.mode.label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        formatArchiveDate(item.createdAtMillis),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(formatFileSize(item.file.length()), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
            }
            Text(
                "${if (item.durationSeconds > 0) "مدت ${formatDuration(item.durationSeconds)}" else "تصویر"}  ·  ${item.file.name}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                modifier = Modifier.padding(top = 9.dp),
                maxLines = 1,
            )
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpen) { Text(if (item.mode == RecordingMode.Photo || item.mode == RecordingMode.Video) "مشاهده" else "گوش دادن", fontSize = 10.sp) }
                TextButton(onClick = onSave, enabled = !saved) { Text(if (saved) "ذخیره شد" else "ذخیره در گالری", fontSize = 10.sp) }
            }
        }
    }
}
@Composable private fun BottomNav(selected: Int, onHome: () -> Unit, onLibrary: () -> Unit) { NavigationBar(containerColor = MaterialTheme.colorScheme.background) { NavigationBarItem(selected == 0, onHome, icon = { Icon(Icons.Default.Home, null) }, label = { Text("خانه", fontSize = 9.sp) }); NavigationBarItem(selected == 1, onLibrary, icon = { Icon(Icons.Default.Archive, null) }, label = { Text("آرشیو", fontSize = 9.sp) }) } }
@Composable private fun PageHeader(eyebrow: String, title: String) { Column { Text(eyebrow, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp); Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) } }
@Composable private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit = {}) { Surface(modifier = Modifier.clickable(onClick = onClick), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp)) { Text(text, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)) } }
@Composable private fun ProfileCard() { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) { Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) { Text("آ", fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("آرمان رضایی", fontSize = 12.sp, fontWeight = FontWeight.Bold); Text("۰۹۱۲ ۱۲۳ ۴۵۶۷", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp)) }; Text("ویرایش", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp) } } }
@Composable private fun SettingsGroup(title: String, rows: List<Pair<String, String>>) { Column { Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.padding(start = 5.dp, bottom = 4.dp)); rows.forEach { (name, description) -> Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(31.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .13f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp)) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(name, fontSize = 11.sp); Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp)) }; Text("‹", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 20.sp) } } } }
private fun formatDuration(seconds: Long): String { val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60; return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s) }
private fun formatArchiveDate(millis: Long): String = SimpleDateFormat("yyyy/MM/dd · HH:mm", Locale("fa", "IR")).format(Date(millis))
private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(Locale.US, bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.0f KB".format(Locale.US, bytes / 1024.0)
    else -> "$bytes B"
}
