package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animated Particle for Reaction Explosion
 */
private data class BurstEmojiParticle(
    val emoji: String,
    val angleRad: Float,
    val speed: Float,
    val scaleTarget: Float,
    val rotationTarget: Float,
    val distanceMaxDp: Float
)

@Composable
fun ReactionBurstOverlay(
    emoji: String?,
    onAnimationEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (emoji == null) return

    val progress = remember(emoji) { Animatable(0f) }
    val particles = remember(emoji) {
        val rng = Random(System.currentTimeMillis())
        List(14) { i ->
            val baseAngle = (i.toFloat() / 14f) * 2f * Math.PI.toFloat()
            val angleJitter = (rng.nextFloat() - 0.5f) * 0.4f
            BurstEmojiParticle(
                emoji = emoji,
                angleRad = baseAngle + angleJitter,
                speed = 0.7f + rng.nextFloat() * 0.6f,
                scaleTarget = 0.8f + rng.nextFloat() * 0.8f,
                rotationTarget = (rng.nextFloat() - 0.5f) * 70f,
                distanceMaxDp = 50f + rng.nextFloat() * 70f
            )
        }
    }

    LaunchedEffect(emoji) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 680, easing = LinearOutSlowInEasing)
        )
        onAnimationEnd()
    }

    val p = progress.value
    if (p in 0.001f..0.999f) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            for (pt in particles) {
                val travel = p * pt.distanceMaxDp * pt.speed
                val offsetX = cos(pt.angleRad) * travel
                val offsetY = sin(pt.angleRad) * travel - (p * 20f)
                val currentScale = pt.scaleTarget * (1f - (p * 0.4f))

                Text(
                    text = pt.emoji,
                    fontSize = 26.sp,
                    modifier = Modifier
                        .offset { IntOffset(offsetX.dp.roundToPx(), offsetY.dp.roundToPx()) }
                        .scale(currentScale)
                        .rotate(pt.rotationTarget * p)
                        .drawBehind { drawCircle(Color.Transparent) }
                )
            }
        }
    }
}

/**
 * Categorized Emoji database covering full emoji reactions
 */
val ALL_REACTION_CATEGORIES = listOf(
    "Популярные" to listOf("👍", "❤️", "😂", "🔥", "😮", "🙏", "⚡", "💀", "💯", "🎉", "🥺", "👀", "👏", "🚀", "💔", "😍", "🤝", "🤩", "😭", "🤔", "😎", "🥳", "🤐", "🤫", "🥱", "🤤", "😴", "🫡", "🫠", "✨"),
    "Смайлы" to listOf("😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩", "😘", "😗", "😚", "😙", "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "🤥", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕", "🤢", "🤮", "🤧", "🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥳", "🥸", "😎", "🤓", "🧐", "😕", "😟", "🙁", "☹️", "😮", "😯", "😲", "😳", "🥺", "😦", "😧", "😨", "😰", "😥", "😢", "😭", "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤", "😡", "😠", "🤬", "😈", "👿", "💀", "☠️", "💩", "🤡", "👹", "👺", "👻", "👽", "👾", "🤖"),
    "Жесты" to listOf("👋", "🤚", "🖐️", "✋", "🖖", "🫱", "🫲", "🫳", "🫴", "👌", "🤌", "🤏", "✌️", "🤞", "🫰", "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️", "🫵", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "🫶", "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳", "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻", "👃", "🧠", "🦷", "🦴", "👀", "👁️", "👅", "👄", "🫦"),
    "Сердца" to listOf("❤️", "🧡", "💛", "💚", "💙", "💜", "🤎", "🖤", "🤍", "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "🫀", "🔥", "💥", "💫", "⭐", "🌟", "✨", "⚡", "💢", "💋", "💌"),
    "Предметы" to listOf("💯", "💬", "💭", "🗯️", "💤", "💣", "💥", "🪄", "🔮", "🧿", "🎯", "🎲", "🎮", "🕹️", "🏆", "🥇", "🥈", "🥉", "👑", "💎", "🔔", "💡", "🕯️", "📱", "💻", "✉️", "📦", "🎁", "🎈", "🎉", "🎊", "🪅", "🪩", "🪐", "🌍", "🌙", "☀️", "☕", "🍺", "🥂", "🍕", "🍔")
)

/**
 * Full-screen modal bottom sheet for picking ANY emoji reaction
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentFullEmojiPickerSheet(
    onDismissRequest: () -> Unit,
    onEmojiSelected: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCategoryIndex by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val colorScheme = MaterialTheme.colorScheme

    val currentEmojis = remember(selectedCategoryIndex, searchQuery) {
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            ALL_REACTION_CATEGORIES.flatMap { it.second }.distinct().filter {
                it.contains(q) || q.contains(it)
            }
        } else {
            ALL_REACTION_CATEGORIES.getOrNull(selectedCategoryIndex)?.second ?: emptyList()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Реакция",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Поиск эмодзи…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colorScheme.onSurface),
                            cursorBrush = SolidColor(colorScheme.primary),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Очистить",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ALL_REACTION_CATEGORIES.forEachIndexed { idx, (catName, _) ->
                    FilterChip(
                        selected = selectedCategoryIndex == idx && searchQuery.isEmpty(),
                        onClick = {
                            searchQuery = ""
                            selectedCategoryIndex = idx
                        },
                        label = { Text(catName, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colorScheme.primaryContainer,
                            selectedLabelColor = colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 44.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .testTag("full_emoji_picker_grid")
            ) {
                items(currentEmojis, key = { it }) { emoji ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable {
                                onEmojiSelected(emoji)
                                onDismissRequest()
                            }
                    ) {
                        Text(
                            text = emoji,
                            fontSize = 24.sp
                        )
                    }
                }
            }
        }
    }
}
