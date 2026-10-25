package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.BlindRelayRepository
import com.example.ui.MomentChatScreen
import com.example.ui.MomentHomeScreen
import com.example.ui.theme.MomentRoundedBoldFontFamily
import com.example.ui.theme.MomentTheme
import com.example.ui.theme.extractWallpaperSeedColor
import com.example.util.HapticEngine
import com.example.viewmodel.MomentViewModel
import com.example.viewmodel.SessionPhase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var hardwareFlagSecureEnabled by remember { mutableStateOf(false) }
            var autoShredOnBackground by remember { mutableStateOf(false) }
            var customWallpaperSeed by remember { mutableStateOf<Color?>(null) }

            LaunchedEffect(hardwareFlagSecureEnabled) {
                if (hardwareFlagSecureEnabled) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            MomentTheme(
                dynamicColor = true,
                customWallpaperSeed = customWallpaperSeed
            ) {
                MomentZeroKnowledgeApp(
                    hardwareFlagSecureEnabled = hardwareFlagSecureEnabled,
                    onToggleHardwareFlagSecure = { hardwareFlagSecureEnabled = it },
                    autoShredOnBackground = autoShredOnBackground,
                    onToggleAutoShredOnBackground = { autoShredOnBackground = it },
                    customWallpaperSeed = customWallpaperSeed,
                    onSelectSystemMonet = { customWallpaperSeed = null },
                    onSelectCustomSeed = { customWallpaperSeed = it },
                    onPickWallpaperUri = { uri: Uri ->
                        scope.launch {
                            val extracted = extractWallpaperSeedColor(context, uri)
                            if (extracted != null) {
                                customWallpaperSeed = extracted
                            }
                        }
                    }
                )
            }
        }
    }
}

private fun scrubSystemClipboard(context: Context) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}

@Composable
fun MomentZeroKnowledgeApp(
    hardwareFlagSecureEnabled: Boolean = false,
    onToggleHardwareFlagSecure: (Boolean) -> Unit = {},
    autoShredOnBackground: Boolean = false,
    onToggleAutoShredOnBackground: (Boolean) -> Unit = {},
    customWallpaperSeed: Color? = null,
    onSelectSystemMonet: () -> Unit = {},
    onSelectCustomSeed: (Color) -> Unit = {},
    onPickWallpaperUri: (Uri) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hapticEngine = remember(context) { HapticEngine(context) }

    var isAppInBackground by remember { mutableStateOf(false) }
    var ephemeralNodeId by remember {
        mutableStateOf("zk_" + UUID.randomUUID().toString().replace("-", "").take(16))
    }

    val viewModel: MomentViewModel = viewModel(
        key = ephemeralNodeId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MomentViewModel(
                    repository = BlindRelayRepository(db),
                    initialUserId = ephemeralNodeId
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner, autoShredOnBackground) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    isAppInBackground = true
                    if (autoShredOnBackground && uiState.phase != SessionPhase.IDLE) {
                        scrubSystemClipboard(context)
                        viewModel.exitToMainMenu()
                    }
                }
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                    isAppInBackground = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.phase) {
        if (uiState.phase == SessionPhase.ACTIVE_CHAT && uiState.isPeerConnected) {
            hapticEngine.onHandshakeConnected()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Only enter MomentChatScreen when a REAL peer has connected and verified ECDH keys
        val showActiveChat = uiState.phase == SessionPhase.ACTIVE_CHAT && uiState.isPeerConnected

        AnimatedContent(
            targetState = showActiveChat,
            transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(180))
            },
            label = "moment_screen_transition"
        ) { isInActiveChat ->
            if (isInActiveChat) {
                MomentChatScreen(
                    uiState = uiState,
                    hapticEngine = hapticEngine,
                    onSendMessage = { text -> viewModel.sendMessage(text) },
                    onUserTypingChanged = { text -> viewModel.onUserTypingChanged(text) },
                    onSetReply = { msg -> viewModel.setReplyDraft(msg) },
                    onToggleReaction = { msgId, emoji -> viewModel.toggleReaction(msgId, emoji) },
                    onCycleTtl = {
                        val next = when (uiState.defaultTtlSeconds) {
                            0 -> 15
                            15 -> 30
                            30 -> 60
                            else -> 0
                        }
                        viewModel.setDefaultTtlSeconds(next)
                    },
                    onSwitchToNewPartner = {
                        scrubSystemClipboard(context)
                        viewModel.switchToNewPartner()
                    },
                    onExitToMainMenu = {
                        scrubSystemClipboard(context)
                        viewModel.exitToMainMenu()
                    }
                )
            } else {
                MomentHomeScreen(
                    uiState = uiState,
                    hapticEngine = hapticEngine,
                    hardwareFlagSecureEnabled = hardwareFlagSecureEnabled,
                    onToggleHardwareFlagSecure = onToggleHardwareFlagSecure,
                    autoShredOnBackground = autoShredOnBackground,
                    onToggleAutoShredOnBackground = onToggleAutoShredOnBackground,
                    customWallpaperSeed = customWallpaperSeed,
                    onSelectSystemMonet = onSelectSystemMonet,
                    onSelectCustomSeed = onSelectCustomSeed,
                    onPickWallpaperUri = onPickWallpaperUri,
                    onStartClicked = { viewModel.startMatchmaking() },
                    onCancelSearch = {
                        scrubSystemClipboard(context)
                        viewModel.exitToMainMenu()
                    },
                    onSelectTtl = { seconds -> viewModel.setDefaultTtlSeconds(seconds) },
                    onResetIdentity = {
                        hapticEngine.onSessionShredded()
                        scrubSystemClipboard(context)
                        viewModel.exitToMainMenu()
                        ephemeralNodeId = "zk_" + UUID.randomUUID().toString().replace("-", "").take(16)
                    }
                )
            }
        }

        if (isAppInBackground) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Moment",
                    fontFamily = MomentRoundedBoldFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 32.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
