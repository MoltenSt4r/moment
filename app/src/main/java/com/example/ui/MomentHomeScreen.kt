package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.MomentRoundedBoldFontFamily
import com.example.util.HapticEngine
import com.example.viewmodel.MomentUiState
import com.example.viewmodel.SessionPhase

private val WALLPAPER_MONET_SEEDS = listOf(
    Color(0xFF4F86F7) to "Океан",
    Color(0xFF3E9B76) to "Шалфей",
    Color(0xFF8B6CEF) to "Лаванда",
    Color(0xFFE07A5F) to "Терракота",
    Color(0xFFD4A359) to "Песок"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentHomeScreen(
    uiState: MomentUiState,
    hapticEngine: HapticEngine,
    hardwareFlagSecureEnabled: Boolean = false,
    onToggleHardwareFlagSecure: (Boolean) -> Unit = {},
    autoShredOnBackground: Boolean = false,
    onToggleAutoShredOnBackground: (Boolean) -> Unit = {},
    customWallpaperSeed: Color? = null,
    customWallpaperBitmap: androidx.compose.ui.graphics.ImageBitmap? = null,
    hasCustomWallpaper: Boolean = false,
    onClearWallpaper: () -> Unit = {},
    isLiquidGlassEnabled: Boolean = true,
    onToggleLiquidGlass: (Boolean) -> Unit = {},
    onSelectSystemMonet: () -> Unit = {},
    onSelectCustomSeed: (Color) -> Unit = {},
    onPickWallpaperUri: (Uri) -> Unit = {},
    onStartClicked: () -> Unit,
    onCancelSearch: () -> Unit,
    onSelectTtl: (Int) -> Unit,
    onResetIdentity: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var showMonetSheet by remember { mutableStateOf(false) }
    val isSearching = uiState.phase == SessionPhase.SEARCHING || uiState.phase == SessionPhase.HANDSHAKING

    val wallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            hapticEngine.onReactionToggled()
            onPickWallpaperUri(uri)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (customWallpaperBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = customWallpaperBitmap,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isLiquidGlassEnabled) 0.38f else 0.50f))
            )
        } else if (isLiquidGlassEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF070B19),
                                Color(0xFF0F172A),
                                Color(0xFF0B1120)
                            )
                        )
                    )
            )
        }

        Scaffold(
            containerColor = if (customWallpaperBitmap != null || isLiquidGlassEnabled) Color.Transparent else colorScheme.surface,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {},
                    actions = {
                        IconButton(
                            onClick = {
                                hapticEngine.onReactionToggled()
                                showMonetSheet = true
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .then(
                                    if (isLiquidGlassEnabled) {
                                        Modifier.liquidGlass(
                                            shape = CircleShape,
                                            alpha = 0.12f,
                                            borderAlpha = 0.22f
                                        )
                                    } else Modifier
                                )
                                .testTag("monet_palette_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Palette,
                                contentDescription = "Цвета Monet и приватность",
                                tint = if (customWallpaperBitmap != null || isLiquidGlassEnabled) Color.White else colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onResetIdentity,
                            modifier = Modifier
                                .size(44.dp)
                                .then(
                                    if (isLiquidGlassEnabled) {
                                        Modifier.liquidGlass(
                                            shape = CircleShape,
                                            alpha = 0.12f,
                                            borderAlpha = 0.22f
                                        )
                                    } else Modifier
                                )
                                .testTag("reset_identity_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Сбросить ключи",
                                tint = if (customWallpaperBitmap != null || isLiquidGlassEnabled) Color.White else colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Centerpiece: Pure Typographic "Moment" Wordmark + Flat MD3 Expressive Morphing Start Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.brand_wordmark),
                    fontFamily = MomentRoundedBoldFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 56.sp,
                    letterSpacing = (-1.8).sp,
                    color = if (isLiquidGlassEnabled || customWallpaperBitmap != null) Color.White else colorScheme.onSurface,
                    modifier = Modifier.testTag("moment_wordmark_home")
                )

                Spacer(modifier = Modifier.height(48.dp))

                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()

                val buttonScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.95f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "md3_start_scale"
                )

                val cornerRadius by animateDpAsState(
                    targetValue = when {
                        isPressed -> 28.dp
                        isSearching -> 32.dp
                        else -> 80.dp
                    },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "md3_start_corner"
                )

                val buttonWidth by animateDpAsState(
                    targetValue = if (isSearching) 236.dp else 200.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "md3_start_width"
                )

                val startButtonModifier = if (isLiquidGlassEnabled) {
                    Modifier
                        .width(buttonWidth)
                        .height(88.dp)
                        .scale(buttonScale)
                        .liquidGlass(
                            shape = RoundedCornerShape(cornerRadius),
                            alpha = if (isSearching) 0.16f else 0.85f,
                            borderAlpha = if (isSearching) 0.28f else 0.40f,
                            isMine = !isSearching
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            if (isSearching) {
                                hapticEngine.onSessionShredded()
                                onCancelSearch()
                            } else {
                                hapticEngine.onStartMatchmaking()
                                onStartClicked()
                            }
                        }
                        .testTag("start_matchmaking_button")
                } else {
                    Modifier
                        .width(buttonWidth)
                        .height(88.dp)
                        .scale(buttonScale)
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(if (isSearching) colorScheme.secondaryContainer else colorScheme.primary)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            if (isSearching) {
                                hapticEngine.onSessionShredded()
                                onCancelSearch()
                            } else {
                                hapticEngine.onStartMatchmaking()
                                onStartClicked()
                            }
                        }
                        .testTag("start_matchmaking_button")
                }

                Box(
                    modifier = startButtonModifier,
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = uiState.phase,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                        label = "start_content"
                    ) { phase ->
                        when (phase) {
                            SessionPhase.SEARCHING -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = if (isLiquidGlassEnabled) Color.White else colorScheme.onSecondaryContainer,
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Ожидание…",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isLiquidGlassEnabled) Color.White else colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            SessionPhase.HANDSHAKING -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = if (isLiquidGlassEnabled) Color.White else colorScheme.onSecondaryContainer,
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Обмен ключами…",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isLiquidGlassEnabled) Color.White else colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            else -> {
                                Text(
                                    text = stringResource(R.string.start_button),
                                    fontFamily = MomentRoundedBoldFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 26.sp,
                                    letterSpacing = (-0.5).sp,
                                    color = if (isLiquidGlassEnabled) Color.White else colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }

                if (isSearching) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            hapticEngine.onSessionShredded()
                            onCancelSearch()
                        },
                        modifier = Modifier.testTag("cancel_search_button")
                    ) {
                        Text(
                            text = stringResource(R.string.cancel_search),
                            color = if (isLiquidGlassEnabled || customWallpaperBitmap != null) Color.White.copy(alpha = 0.85f) else colorScheme.primary
                        )
                    }
                }

                AnimatedVisibility(visible = !uiState.statusBannerText.isNullOrBlank() && !isSearching) {
                    uiState.statusBannerText?.let { banner ->
                        Box(
                            modifier = Modifier
                                .padding(top = 18.dp)
                                .then(
                                    if (isLiquidGlassEnabled) {
                                        Modifier.liquidGlass(
                                            shape = RoundedCornerShape(16.dp),
                                            alpha = 0.10f,
                                            borderAlpha = 0.20f
                                        )
                                    } else {
                                        Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(colorScheme.surfaceContainerHigh)
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = banner,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isLiquidGlassEnabled || customWallpaperBitmap != null) Color.White.copy(alpha = 0.85f) else colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Bottom: Liquid Glass capsule or MD3 SegmentedButton for Disappearing Messages Timer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 380.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val ttlOptions = listOf(
                    0 to stringResource(R.string.ttl_off),
                    15 to "15с",
                    30 to "30с",
                    60 to "60с"
                )

                if (isLiquidGlassEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .liquidGlass(
                                shape = CircleShape,
                                alpha = 0.10f,
                                borderAlpha = 0.20f
                            )
                            .padding(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ttlOptions.forEach { (seconds, label) ->
                                val isSelected = uiState.defaultTtlSeconds == seconds
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) {
                                                Color(0xFF3B82F6).copy(alpha = 0.45f)
                                            } else {
                                                Color.Transparent
                                            }
                                        )
                                        .clickable {
                                            hapticEngine.onReactionToggled()
                                            onSelectTtl(seconds)
                                        }
                                        .testTag("ttl_chip_$seconds"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ttlOptions.forEachIndexed { index, (seconds, label) ->
                            SegmentedButton(
                                selected = uiState.defaultTtlSeconds == seconds,
                                onClick = {
                                    hapticEngine.onReactionToggled()
                                    onSelectTtl(seconds)
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ttlOptions.size
                                ),
                                modifier = Modifier.testTag("ttl_chip_$seconds")
                            ) {
                                Text(text = label)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMonetSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMonetSheet = false },
            containerColor = colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    text = "Цвета Monet и анонимность",
                    style = MaterialTheme.typography.titleLarge,
                    color = colorScheme.onSurface
                )

                // 1. System Wallpaper vs Custom Wallpaper Image Picker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            hapticEngine.onReactionToggled()
                            onSelectSystemMonet()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("system_monet_button")
                    ) {
                        if (customWallpaperSeed == null) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(text = "Системные обои")
                    }

                    FilledTonalButton(
                        onClick = {
                            wallpaperPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_wallpaper_image_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Свои обои")
                    }
                }

                // 2. Quick Monet Wallpaper Seed Swatches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WALLPAPER_MONET_SEEDS.forEach { (seed, name) ->
                        val isSelected = customWallpaperSeed == seed
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(seed)
                                .clickable {
                                    hapticEngine.onReactionToggled()
                                    onSelectCustomSeed(seed)
                                }
                                .testTag("monet_swatch_$name")
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = name,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Liquid Glass Mode Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Режим Liquid Glass",
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "Эффект жидкого стекла с прозрачностью и глянцевым преломлением",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = isLiquidGlassEnabled,
                        onCheckedChange = onToggleLiquidGlass
                    )
                }

                if (hasCustomWallpaper) {
                    FilledTonalButton(
                        onClick = {
                            hapticEngine.onSessionShredded()
                            onClearWallpaper()
                        },
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = colorScheme.errorContainer.copy(alpha = 0.7f),
                            contentColor = colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Удалить свои обои (сбросить на системные)")
                    }
                }

                // 4. Auto-shred on Background Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Сброс чата при сворачивании",
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "Мгновенно уничтожает ключи и переписку при уходе в фон",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = autoShredOnBackground,
                        onCheckedChange = onToggleAutoShredOnBackground
                    )
                }

                // 5. Hardware FLAG_SECURE Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Блокировка скриншотов (FLAG_SECURE)",
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "Запрещает захват экрана на устройстве",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = hardwareFlagSecureEnabled,
                        onCheckedChange = onToggleHardwareFlagSecure
                    )
                }
            }
        }
    }
    }
}
