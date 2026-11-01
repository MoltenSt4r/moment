package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.MomentMessage
import com.example.ui.theme.MomentRoundedBoldFontFamily
import com.example.util.HapticEngine
import com.example.viewmodel.MomentUiState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private enum class PullForkStage {
    IDLE,
    PULLING_TO_CHECKMARK,
    ARMED_CHECKMARK,
    DEEP_PULL_NEW_CHAT,
    SIDE_SWIPE_EXIT_MENU
}

private val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "🔥", "😮", "🙏", "⚡", "💀", "🎉")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentChatScreen(
    uiState: MomentUiState,
    hapticEngine: HapticEngine,
    customWallpaperBitmap: ImageBitmap? = null,
    onSendMessage: (String) -> Unit,
    onUserTypingChanged: (String) -> Unit = {},
    onSetReply: (MomentMessage?) -> Unit,
    onToggleReaction: (String, String) -> Unit,
    onCycleTtl: () -> Unit,
    onToggleLiquidGlass: () -> Unit = {},
    onSwitchToNewPartner: () -> Unit,
    onExitToMainMenu: () -> Unit
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val colorScheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var activeReactionMessageId by remember { mutableStateOf<String?>(null) }
    var showFullEmojiPicker by remember { mutableStateOf(false) }
    var burstingEmoji by remember { mutableStateOf<String?>(null) }

    val isLiquidGlass = uiState.isLiquidGlassEnabled

    BackHandler {
        inputText = ""
        hapticEngine.onSessionShredded()
        onExitToMainMenu()
    }

    val checkmarkThresholdPx = with(density) { 92.dp.toPx() }
    val deepPullThresholdPx = with(density) { 172.dp.toPx() }
    val sideSwipeThresholdPx = with(density) { 68.dp.toPx() }

    var pullDragY by remember { mutableFloatStateOf(0f) }
    var pullDragX by remember { mutableFloatStateOf(0f) }
    var checkmarkLatched by remember { mutableStateOf(false) }
    var currentStage by remember { mutableStateOf(PullForkStage.IDLE) }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    fun resetPull() {
        pullDragY = 0f
        pullDragX = 0f
        checkmarkLatched = false
        currentStage = PullForkStage.IDLE
        hapticEngine.resetPullTracking()
    }

    fun evaluateStage(newY: Float, newX: Float, wasLatched: Boolean): PullForkStage {
        val unlatchThreshold = checkmarkThresholdPx * 0.45f
        val latched = if (wasLatched) newY > unlatchThreshold else newY >= checkmarkThresholdPx
        return when {
            !latched && newY <= 10f -> PullForkStage.IDLE
            !latched -> PullForkStage.PULLING_TO_CHECKMARK
            abs(newX) >= sideSwipeThresholdPx -> PullForkStage.SIDE_SWIPE_EXIT_MENU
            newY >= deepPullThresholdPx -> PullForkStage.DEEP_PULL_NEW_CHAT
            else -> PullForkStage.ARMED_CHECKMARK
        }
    }

    fun handleGestureUpdate(deltaX: Float, deltaY: Float) {
        val nextY = (pullDragY + deltaY).coerceIn(0f, deepPullThresholdPx * 1.4f)
        val unlatchThreshold = checkmarkThresholdPx * 0.45f
        val nowLatched = if (checkmarkLatched) nextY > unlatchThreshold else nextY >= checkmarkThresholdPx

        val nextX = if (nowLatched) {
            (pullDragX + deltaX).coerceIn(-sideSwipeThresholdPx * 1.8f, sideSwipeThresholdPx * 1.8f)
        } else {
            0f
        }

        val prevStage = currentStage
        val nextStage = evaluateStage(nextY, nextX, nowLatched)

        pullDragY = nextY
        pullDragX = nextX
        checkmarkLatched = nowLatched
        currentStage = nextStage

        if (nextStage == PullForkStage.PULLING_TO_CHECKMARK) {
            val progress = (nextY / checkmarkThresholdPx).coerceIn(0f, 1f)
            hapticEngine.onPullProgress(progress, view)
        } else if (nextStage != prevStage) {
            when (nextStage) {
                PullForkStage.ARMED_CHECKMARK -> hapticEngine.onCheckmarkArmed(view)
                PullForkStage.DEEP_PULL_NEW_CHAT -> hapticEngine.onDeepPullNextPartnerArmed(view)
                PullForkStage.SIDE_SWIPE_EXIT_MENU -> hapticEngine.onSideSwipeExitArmed(view)
                PullForkStage.IDLE -> hapticEngine.resetPullTracking()
                else -> {}
            }
        }
    }

    fun handleGestureRelease() {
        when (currentStage) {
            PullForkStage.DEEP_PULL_NEW_CHAT -> {
                inputText = ""
                resetPull()
                onSwitchToNewPartner()
            }
            PullForkStage.SIDE_SWIPE_EXIT_MENU -> {
                inputText = ""
                resetPull()
                onExitToMainMenu()
            }
            PullForkStage.ARMED_CHECKMARK -> {
                if (pullDragY < checkmarkThresholdPx * 0.7f) {
                    resetPull()
                } else {
                    pullDragY = checkmarkThresholdPx
                    pullDragX = 0f
                }
            }
            else -> {
                resetPull()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Custom Wallpaper Background Layer
        if (customWallpaperBitmap != null) {
            Image(
                bitmap = customWallpaperBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Dark vignette overlay for pristine contrast and legibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isLiquidGlass) 0.38f else 0.50f))
            )
        }

        Scaffold(
            containerColor = if (customWallpaperBitmap != null) Color.Transparent else colorScheme.surface,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                val topBarModifier = if (isLiquidGlass) {
                    Modifier.liquidGlass(
                        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                        alpha = 0.20f,
                        borderAlpha = 0.35f
                    )
                } else {
                    Modifier
                }

                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.brand_wordmark),
                                fontFamily = MomentRoundedBoldFontFamily,
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp,
                                letterSpacing = (-0.8).sp,
                                color = if (customWallpaperBitmap != null) Color.White else colorScheme.onSurface
                            )
                            if (isLiquidGlass) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = "Liquid Glass",
                                    tint = Color(0xFF60A5FA),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (uiState.isPeerTyping && !uiState.isPeerDisconnected) {
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.peer_typing),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (customWallpaperBitmap != null) Color(0xFF93C5FD) else colorScheme.primary
                                )
                            }
                        }
                    },
                    actions = {
                        AssistChip(
                            onClick = {
                                hapticEngine.onReactionToggled()
                                onCycleTtl()
                            },
                            label = {
                                Text(
                                    text = if (uiState.defaultTtlSeconds > 0) "${uiState.defaultTtlSeconds}с" else "Выкл",
                                    color = if (customWallpaperBitmap != null) Color.White else colorScheme.onSurface
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Timer,
                                    contentDescription = null,
                                    tint = if (customWallpaperBitmap != null) Color(0xFF93C5FD) else colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .testTag("chat_ttl_toggle_pill")
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (customWallpaperBitmap != null || isLiquidGlass) Color.Transparent else colorScheme.surface
                    ),
                    modifier = topBarModifier
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    handleGestureUpdate(dragAmount.x, dragAmount.y)
                                },
                                onDragEnd = { handleGestureRelease() },
                                onDragCancel = { handleGestureRelease() }
                            )
                        }
                        .testTag("chat_top_pull_header")
                )
            },
            bottomBar = {
                Column {
                    // Floating Peer Disconnected Banner if peer left
                    AnimatedVisibility(
                        visible = uiState.isPeerDisconnected,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        PeerDisconnectedBanner(
                            noticeText = uiState.peerDisconnectedNotice ?: "Собеседник покинул чат",
                            onNextPartner = {
                                hapticEngine.onReactionToggled()
                                onSwitchToNewPartner()
                            },
                            onExitMenu = {
                                hapticEngine.onSessionShredded()
                                onExitToMainMenu()
                            }
                        )
                    }

                    MomentMessageComposer(
                        inputText = inputText,
                        isLiquidGlass = isLiquidGlass,
                        isCustomWallpaper = customWallpaperBitmap != null,
                        isDisabled = uiState.isPeerDisconnected,
                        onInputTextChange = { next ->
                            if (!uiState.isPeerDisconnected) {
                                inputText = next
                                onUserTypingChanged(next)
                            }
                        },
                        replyDraft = uiState.replyDraft,
                        onClearReply = { onSetReply(null) },
                        onSend = {
                            if (inputText.isNotBlank() && !uiState.isPeerDisconnected) {
                                hapticEngine.onReactionToggled()
                                onSendMessage(inputText)
                                inputText = ""
                            }
                        }
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { hapticEngine.resetPullTracking() },
                        onDrag = { change, dragAmount ->
                            val isAtTopOfList = listState.firstVisibleItemIndex == 0 &&
                                listState.firstVisibleItemScrollOffset == 0
                            if (checkmarkLatched || pullDragY > 0f || (isAtTopOfList && dragAmount.y > 0f)) {
                                change.consume()
                                handleGestureUpdate(dragAmount.x, dragAmount.y)
                            }
                        },
                        onDragEnd = { handleGestureRelease() },
                        onDragCancel = { handleGestureRelease() }
                    )
                }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 600.dp)
                        .align(Alignment.TopCenter)
                        .testTag("chat_messages_list")
                ) {
                    items(
                        items = uiState.messages,
                        key = { it.id }
                    ) { message ->
                        DustDisintegrationWrapper(
                            messageId = message.id,
                            isDisintegrating = message.isDisintegrating,
                            primaryParticleColor = if (message.isMine) {
                                Color(0xFF3B82F6)
                            } else {
                                colorScheme.surfaceContainerHigh
                            },
                            secondaryParticleColor = if (message.isMine) {
                                Color(0xFF93C5FD)
                            } else {
                                colorScheme.onSurface
                            },
                            onStartDisintegration = { hapticEngine.onReactionToggled() }
                        ) {
                            MomentMessageBubbleItem(
                                message = message,
                                nowEpochMs = uiState.nowEpochMs,
                                isLiquidGlass = isLiquidGlass,
                                isCustomWallpaper = customWallpaperBitmap != null,
                                showReactionPicker = activeReactionMessageId == message.id && !message.isDisintegrating,
                                onSwipeToReply = {
                                    hapticEngine.onReplySnapped()
                                    onSetReply(message)
                                },
                                onToggleReactionPicker = {
                                    hapticEngine.onReactionToggled()
                                    activeReactionMessageId =
                                        if (activeReactionMessageId == message.id) null else message.id
                                },
                                onOpenFullEmojiPicker = {
                                    showFullEmojiPicker = true
                                },
                                onSelectReaction = { emoji ->
                                    hapticEngine.onReactionToggled()
                                    activeReactionMessageId = null
                                    burstingEmoji = emoji
                                    onToggleReaction(message.id, emoji)
                                }
                            )
                        }
                    }
                }

                // Top Pull Indicator with Swipe-Back-Up Cancel capability
                if (currentStage != PullForkStage.IDLE) {
                    Md3PullForkIndicator(
                        stage = currentStage,
                        pullProgress = (pullDragY / checkmarkThresholdPx).coerceIn(0f, 1.5f),
                        dragOffsetX = pullDragX,
                        dragOffsetY = pullDragY,
                        isLiquidGlass = isLiquidGlass,
                        onCancelPull = { resetPull() }
                    )
                }

                // Full Emoji Reaction Picker Modal Sheet
                if (showFullEmojiPicker && activeReactionMessageId != null) {
                    MomentFullEmojiPickerSheet(
                        onDismissRequest = { showFullEmojiPicker = false },
                        onEmojiSelected = { emoji ->
                            val msgId = activeReactionMessageId
                            if (msgId != null) {
                                hapticEngine.onReactionToggled()
                                burstingEmoji = emoji
                                onToggleReaction(msgId, emoji)
                                activeReactionMessageId = null
                            }
                            showFullEmojiPicker = false
                        }
                    )
                }

                // Celebration Reaction Burst Animation
                ReactionBurstOverlay(
                    emoji = burstingEmoji,
                    onAnimationEnd = { burstingEmoji = null }
                )
            }
        }
    }
}

private data class DustGrain(
    val normX: Float,
    val normY: Float,
    val angleRad: Float,
    val speedFactor: Float,
    val radiusDp: Float,
    val useSecondaryColor: Boolean,
    val delayFraction: Float
)

@Composable
private fun DustDisintegrationWrapper(
    messageId: String,
    isDisintegrating: Boolean,
    primaryParticleColor: Color,
    secondaryParticleColor: Color,
    onStartDisintegration: () -> Unit,
    content: @Composable () -> Unit
) {
    val progress = remember(messageId) { Animatable(0f) }

    val grains = remember(messageId) {
        val rng = Random(messageId.hashCode())
        List(120) {
            val nx = rng.nextFloat()
            DustGrain(
                normX = nx,
                normY = rng.nextFloat(),
                angleRad = (-0.95f + rng.nextFloat() * 1.4f),
                speedFactor = 0.45f + rng.nextFloat() * 0.95f,
                radiusDp = 1.4f + rng.nextFloat() * 2.8f,
                useSecondaryColor = rng.nextFloat() > 0.65f,
                delayFraction = (nx * 0.55f).coerceIn(0f, 0.55f)
            )
        }
    }

    LaunchedEffect(isDisintegrating) {
        if (isDisintegrating && progress.value == 0f) {
            onStartDisintegration()
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing)
            )
        }
    }

    val p = progress.value
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                if (p <= 0.001f) {
                    drawContent()
                } else {
                    val w = size.width
                    val h = size.height
                    val cutX = (w * (p * 1.35f)).coerceIn(0f, w)
                    if (cutX < w) {
                        clipRect(
                            left = cutX,
                            top = 0f,
                            right = w,
                            bottom = h
                        ) {
                            this@drawWithContent.drawContent()
                        }
                    }

                    val maxTravelPx = 76.dp.toPx()
                    for (grain in grains) {
                        val localT = ((p - grain.delayFraction) / (1f - grain.delayFraction)).coerceIn(0f, 1f)
                        if (localT > 0f && localT < 1f) {
                            val alpha = (1f - localT * localT).coerceIn(0f, 1f)
                            val dist = localT * maxTravelPx * grain.speedFactor
                            val turbulenceY = sin(localT * 9f + grain.normX * 12f) * 8.dp.toPx() * localT
                            val cx = grain.normX * w + cos(grain.angleRad) * dist
                            val cy = grain.normY * h + sin(grain.angleRad) * dist - (localT * 22.dp.toPx()) + turbulenceY
                            val radiusPx = (grain.radiusDp * (1f - localT * 0.6f)).dp.toPx()
                            val baseColor = if (grain.useSecondaryColor) secondaryParticleColor else primaryParticleColor

                            drawCircle(
                                color = baseColor.copy(alpha = alpha),
                                radius = radiusPx,
                                center = Offset(cx, cy)
                            )
                        }
                    }
                }
            }
    ) {
        content()
    }
}

/**
 * Top Pull Fork Indicator with Interactive Cancel via Swipe Up or Close Tap
 */
@Composable
private fun Md3PullForkIndicator(
    stage: PullForkStage,
    pullProgress: Float,
    dragOffsetX: Float,
    dragOffsetY: Float,
    isLiquidGlass: Boolean,
    onCancelPull: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    val containerColor by animateColorAsState(
        targetValue = when (stage) {
            PullForkStage.PULLING_TO_CHECKMARK -> colorScheme.surfaceContainerHigh
            PullForkStage.ARMED_CHECKMARK -> colorScheme.primaryContainer
            PullForkStage.DEEP_PULL_NEW_CHAT -> colorScheme.primary
            PullForkStage.SIDE_SWIPE_EXIT_MENU -> colorScheme.tertiaryContainer
            PullForkStage.IDLE -> colorScheme.surfaceContainerHigh
        },
        label = "md3_pull_bg"
    )

    val contentColor by animateColorAsState(
        targetValue = when (stage) {
            PullForkStage.PULLING_TO_CHECKMARK -> colorScheme.primary
            PullForkStage.ARMED_CHECKMARK -> colorScheme.onPrimaryContainer
            PullForkStage.DEEP_PULL_NEW_CHAT -> colorScheme.onPrimary
            PullForkStage.SIDE_SWIPE_EXIT_MENU -> colorScheme.onTertiaryContainer
            PullForkStage.IDLE -> colorScheme.primary
        },
        label = "md3_pull_fg"
    )

    val animatedOffsetY by animateDpAsState(
        targetValue = (dragOffsetY * 0.25f).coerceIn(8f, 60f).dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "md3_pull_y"
    )

    val animatedOffsetX by animateFloatAsState(
        targetValue = dragOffsetX * 0.7f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "md3_pull_x"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = animatedOffsetY),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            shape = CircleShape,
            color = if (isLiquidGlass) Color(0xFF1E293B).copy(alpha = 0.90f) else containerColor,
            contentColor = if (isLiquidGlass) Color.White else contentColor,
            border = if (isLiquidGlass) BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)) else null,
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .pointerInput(Unit) {
                    // Detect swipe up directly on the capsule to cancel
                    detectVerticalDragGestures { change, dragAmount ->
                        if (dragAmount < -8f) {
                            change.consume()
                            onCancelPull()
                        }
                    }
                }
                .testTag("pull_fork_gesture_capsule")
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = if (stage == PullForkStage.PULLING_TO_CHECKMARK) 14.dp else 20.dp,
                    vertical = 12.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                when (stage) {
                    PullForkStage.PULLING_TO_CHECKMARK -> {
                        CircularProgressIndicator(
                            progress = { pullProgress.coerceIn(0.1f, 0.98f) },
                            modifier = Modifier.size(24.dp),
                            color = if (isLiquidGlass) Color(0xFF60A5FA) else contentColor,
                            strokeWidth = 2.5.dp
                        )
                    }

                    PullForkStage.ARMED_CHECKMARK -> {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Готово",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Ниже — новый чат • Вбок — выход",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "↑ Смахните вверх для отмены",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isLiquidGlass) Color.White.copy(alpha = 0.65f) else contentColor.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onCancelPull,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Отмена",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    PullForkStage.DEEP_PULL_NEW_CHAT -> {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Новый собеседник",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    PullForkStage.SIDE_SWIPE_EXIT_MENU -> {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Выход в меню",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    PullForkStage.IDLE -> {}
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MomentMessageBubbleItem(
    message: MomentMessage,
    nowEpochMs: Long,
    isLiquidGlass: Boolean,
    isCustomWallpaper: Boolean,
    showReactionPicker: Boolean,
    onSwipeToReply: () -> Unit,
    onToggleReactionPicker: () -> Unit,
    onOpenFullEmojiPicker: () -> Unit,
    onSelectReaction: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var horizontalSwipeOffset by remember { mutableFloatStateOf(0f) }
    var replyTriggeredInDrag by remember { mutableStateOf(false) }

    // System Notification Bubble (e.g. Peer Disconnected Notice)
    if (message.senderUid == "system") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.58f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                )
            }
        }
        return
    }

    val animatedSwipeX by animateFloatAsState(
        targetValue = horizontalSwipeOffset,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "msg_swipe_reply"
    )

    val remainingSeconds = remember(message.expiresAtMs, nowEpochMs) {
        message.expiresAtMs?.let { exp ->
            ((exp - nowEpochMs) / 1000L).coerceAtLeast(0L).toInt()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(message.id) {
                detectHorizontalDragGestures(
                    onDragStart = { replyTriggeredInDrag = false },
                    onHorizontalDrag = { change, dragAmount ->
                        val next = (horizontalSwipeOffset + dragAmount).coerceIn(0f, 120f)
                        if (next > 0f) change.consume()
                        horizontalSwipeOffset = next
                        if (next >= 72f && !replyTriggeredInDrag) {
                            replyTriggeredInDrag = true
                            onSwipeToReply()
                        }
                    },
                    onDragEnd = { horizontalSwipeOffset = 0f },
                    onDragCancel = { horizontalSwipeOffset = 0f }
                )
            },
        horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start
    ) {
        // Quick Reaction Bar with "+ " for Full Emoji Picker
        AnimatedVisibility(
            visible = showReactionPicker,
            enter = fadeIn() + expandVertically() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = CircleShape,
                color = if (isLiquidGlass) Color(0xFF1E293B).copy(alpha = 0.88f) else colorScheme.surfaceContainerHighest,
                border = if (isLiquidGlass) BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)) else null,
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .testTag("reaction_picker_${message.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QUICK_REACTIONS.forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 18.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onSelectReaction(emoji) }
                                .padding(horizontal = 5.dp, vertical = 4.dp)
                        )
                    }

                    // "+" Button for Full Emoji Picker
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isLiquidGlass) Color.White.copy(alpha = 0.15f) else colorScheme.primaryContainer)
                            .clickable { onOpenFullEmojiPicker() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Больше реакций",
                            tint = if (isLiquidGlass) Color.White else colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onSwipeToReply,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = stringResource(R.string.replying_to),
                            tint = if (isLiquidGlass) Color.White.copy(alpha = 0.8f) else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.offset { IntOffset(animatedSwipeX.roundToInt(), 0) }
        ) {
            if (animatedSwipeX > 20f) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    tint = if (isCustomWallpaper) Color.White else colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(18.dp)
                        .scale((animatedSwipeX / 72f).coerceIn(0.7f, 1f))
                )
            }

            val bubbleShape = if (message.isMine) {
                RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp)
            } else {
                RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 6.dp, bottomEnd = 22.dp)
            }

            val bubbleModifier = if (isLiquidGlass) {
                Modifier
                    .widthIn(max = 300.dp)
                    .liquidGlass(
                        shape = bubbleShape,
                        tintColor = if (message.isMine) Color(0xFF2563EB) else Color.White,
                        alpha = if (message.isMine) 0.85f else 0.16f,
                        borderAlpha = if (message.isMine) 0.65f else 0.32f,
                        isMine = message.isMine
                    )
            } else {
                val bubbleContainerColor = if (message.isMine) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh
                Modifier
                    .widthIn(max = 300.dp)
                    .clip(bubbleShape)
                    .background(bubbleContainerColor)
            }

            val bubbleContentColor = if (isLiquidGlass) {
                Color.White
            } else if (message.isMine) {
                colorScheme.onPrimaryContainer
            } else {
                colorScheme.onSurface
            }

            Box(
                modifier = bubbleModifier
                    .combinedClickable(
                        onClick = { onToggleReactionPicker() },
                        onLongClick = { onToggleReactionPicker() }
                    )
                    .testTag("message_bubble_${message.id}")
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                    if (!message.replyPreview.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.22f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = message.replyPreview,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 12.sp,
                                color = bubbleContentColor.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = bubbleContentColor,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (remainingSeconds != null) {
                            Text(
                                text = "${remainingSeconds}с",
                                style = MaterialTheme.typography.labelSmall,
                                color = bubbleContentColor.copy(alpha = 0.70f)
                            )
                        }
                    }
                }
            }
        }

        // Reaction Badges with Liquid Glass Styling
        if (message.reactions.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                message.reactions.forEach { (emoji, reactorSet) ->
                    val badgeModifier = if (isLiquidGlass) {
                        Modifier
                            .clip(CircleShape)
                            .liquidGlass(shape = CircleShape, alpha = 0.22f, borderAlpha = 0.40f)
                            .clickable { onSelectReaction(emoji) }
                    } else {
                        Modifier
                            .clip(CircleShape)
                            .background(colorScheme.secondaryContainer)
                            .clickable { onSelectReaction(emoji) }
                    }

                    Box(
                        modifier = badgeModifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = emoji, fontSize = 13.sp)
                            if (reactorSet.size > 1) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = reactorSet.size.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isLiquidGlass) Color.White else colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MomentMessageComposer(
    inputText: String,
    isLiquidGlass: Boolean,
    isCustomWallpaper: Boolean,
    isDisabled: Boolean = false,
    onInputTextChange: (String) -> Unit,
    replyDraft: com.example.viewmodel.ReplyDraft?,
    onClearReply: () -> Unit,
    onSend: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    val composerContainerModifier = if (isLiquidGlass) {
        Modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                alpha = 0.22f,
                borderAlpha = 0.35f
            )
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .background(if (isCustomWallpaper) colorScheme.surface.copy(alpha = 0.85f) else colorScheme.surface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    }

    Column(modifier = composerContainerModifier) {
        AnimatedVisibility(visible = replyDraft != null) {
            replyDraft?.let { draft ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isLiquidGlass) Color.White.copy(alpha = 0.12f) else colorScheme.surfaceContainerLow,
                    border = if (isLiquidGlass) BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Reply,
                                contentDescription = null,
                                tint = if (isLiquidGlass) Color(0xFF60A5FA) else colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = draft.previewText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isLiquidGlass) Color.White else colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = onClearReply,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Отменить ответ",
                                tint = if (isLiquidGlass) Color.White else colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val inputPillModifier = if (isLiquidGlass) {
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .liquidGlass(
                        shape = RoundedCornerShape(28.dp),
                        alpha = 0.25f,
                        borderAlpha = 0.38f
                    )
            } else {
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(colorScheme.surfaceContainerHigh)
            }

            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = inputPillModifier.padding(horizontal = 18.dp)
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        text = if (isDisabled) "Чат завершен" else "Сообщение",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isLiquidGlass) Color.White.copy(alpha = 0.50f) else colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    enabled = !isDisabled,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = if (isLiquidGlass) Color.White else colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(if (isLiquidGlass) Color(0xFF60A5FA) else colorScheme.primary),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = { if (!isDisabled) onSend() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat_message_input")
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledIconButton(
                onClick = onSend,
                enabled = inputText.isNotBlank() && !isDisabled,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isLiquidGlass) Color(0xFF2563EB) else colorScheme.primary,
                    contentColor = Color.White,
                    disabledContainerColor = if (isLiquidGlass) Color.White.copy(alpha = 0.12f) else colorScheme.surfaceContainerHigh,
                    disabledContentColor = if (isLiquidGlass) Color.White.copy(alpha = 0.30f) else colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("send_message_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Отправить",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
