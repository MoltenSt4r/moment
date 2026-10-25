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

    Scaffold(
        containerColor = colorScheme.surface,
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
                        modifier = Modifier.testTag("monet_palette_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Palette,
                            contentDescription = "Цвета Monet и приватность",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onResetIdentity,
                        modifier = Modifier.testTag("reset_identity_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Сбросить ключи",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface
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
                    color = colorScheme.onSurface,
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

                Surface(
                    onClick = {
                        if (isSearching) {
                            hapticEngine.onSessionShredded()
                            onCancelSearch()
                        } else {
                            hapticEngine.onStartMatchmaking()
                            onStartClicked()
                        }
                    },
                    interactionSource = interactionSource,
                    shape = RoundedCornerShape(cornerRadius),
                    color = if (isSearching) colorScheme.secondaryContainer else colorScheme.primary,
                    contentColor = if (isSearching) colorScheme.onSecondaryContainer else colorScheme.onPrimary,
                    modifier = Modifier
                        .width(buttonWidth)
                        .height(88.dp)
                        .scale(buttonScale)
                        .testTag("start_matchmaking_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
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
                                            color = colorScheme.onSecondaryContainer,
                                            strokeWidth = 2.5.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Ожидание…",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = colorScheme.onSecondaryContainer
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
                                            color = colorScheme.onSecondaryContainer,
                                            strokeWidth = 2.5.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Обмен ключами…",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = colorScheme.onSecondaryContainer
                                        )
                                    }
                                }

                                else -> {
                                    Text(
                                        text = stringResource(R.string.start_button),
                                        fontFamily = MomentRoundedBoldFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 26.sp,
                                        letterSpacing = (-0.5).sp
                                    )
                                }
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
                        Text(text = stringResource(R.string.cancel_search))
                    }
                }

                AnimatedVisibility(visible = !uiState.statusBannerText.isNullOrBlank() && !isSearching) {
                    uiState.statusBannerText?.let { banner ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colorScheme.surfaceContainerHigh,
                            modifier = Modifier.padding(top = 18.dp)
                        ) {
                            Text(
                                text = banner,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Bottom: Standard MD3 SegmentedButtonRow for Disappearing Messages Timer
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

                // 3. Auto-shred on Background Switch
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

                // 4. Hardware FLAG_SECURE Switch
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
