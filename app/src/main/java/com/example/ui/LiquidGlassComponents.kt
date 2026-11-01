package com.example.ui

import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.compose.foundation.shape.CornerBasedShape
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.intellij.lang.annotations.Language

@Language("AGSL")
private const val ROUNDED_RECT_REFRACTION_WITH_DISPERSION_SHADER = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);
    
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);
    
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));
    
    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;
    
    half4 color = half4(0.0);
    
    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;
    
    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;
    
    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;
    
    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;
    
    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;
    
    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;
    
    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;
    
    return color;
}
"""

@Language("AGSL")
private const val AMBIENT_HIGHLIGHT_SHADER = """
uniform float2 size;
uniform float4 cornerRadii;
uniform float angle;
uniform float falloff;

float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(coord, cornerRadii);
    
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float2 normal = float2(cos(angle), sin(angle));
    float d = dot(grad, normal);
    float intensity = pow(abs(d), falloff);
    float t = step(0.0, d);
    return half4(t, t, t, 1.0) * intensity;
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object AgslLiquidGlass {
    val refractionShader by lazy {
        try {
            RuntimeShader(ROUNDED_RECT_REFRACTION_WITH_DISPERSION_SHADER)
        } catch (_: Throwable) {
            null
        }
    }

    val highlightShader by lazy {
        try {
            RuntimeShader(AMBIENT_HIGHLIGHT_SHADER)
        } catch (_: Throwable) {
            null
        }
    }
}

private fun extractCornerRadii(
    shape: Shape,
    size: Size,
    density: Float
): FloatArray {
    val minDimension = kotlin.math.min(size.width, size.height)
    if (shape == CircleShape) {
        val r = minDimension / 2f
        return floatArrayOf(r, r, r, r)
    }
    if (shape is CornerBasedShape) {
        val d = Density(density)
        val ts = shape.topStart.toPx(size, d)
        val te = shape.topEnd.toPx(size, d)
        val be = shape.bottomEnd.toPx(size, d)
        val bs = shape.bottomStart.toPx(size, d)
        val maxR = minDimension / 2f
        return floatArrayOf(
            ts.coerceIn(0f, maxR),
            te.coerceIn(0f, maxR),
            be.coerceIn(0f, maxR),
            bs.coerceIn(0f, maxR)
        )
    }
    val fallback = (24f * density).coerceIn(0f, minDimension / 2f)
    return floatArrayOf(fallback, fallback, fallback, fallback)
}

/**
 * Authentic SimpMusic / Kyant Liquid Glass modifier for Material Design 3.
 * Powered by hardware AGSL RuntimeShader on Android 13+ (SDF refraction, chromatic dispersion, and directional ambient highlight),
 * with graceful translucent depth fallback on older Android versions.
 */
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(24.dp),
    tintColor: Color = Color.White,
    alpha: Float = 0.09f,
    borderAlpha: Float = 0.22f,
    isMine: Boolean = false
): Modifier = this
    .clip(shape)
    .then(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Modifier.graphicsLayer {
                val shader = AgslLiquidGlass.refractionShader
                if (shader != null && size.width > 0f && size.height > 0f) {
                    val radii = extractCornerRadii(shape, size, density)
                    shader.setFloatUniform("size", size.width, size.height)
                    shader.setFloatUniform("offset", 0f, 0f)
                    shader.setFloatUniform("cornerRadii", radii[0], radii[1], radii[2], radii[3])
                    shader.setFloatUniform("refractionHeight", 20f * density)
                    shader.setFloatUniform("refractionAmount", 14f * density)
                    shader.setFloatUniform("depthEffect", 0.30f)
                    shader.setFloatUniform("chromaticAberration", 0.45f)
                    val effect = RenderEffect.createRuntimeShaderEffect(shader, "content")
                    renderEffect = effect.asComposeRenderEffect()
                }
            }
        } else {
            Modifier
        }
    )
    .background(
        brush = if (isMine) {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF3B82F6).copy(alpha = 0.68f),
                    Color(0xFF1D4ED8).copy(alpha = 0.78f)
                ),
                start = Offset.Zero,
                end = Offset.Infinite
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    tintColor.copy(alpha = (alpha * 1.25f).coerceIn(0.06f, 0.35f)),
                    tintColor.copy(alpha = (alpha * 0.70f).coerceIn(0.03f, 0.20f))
                )
            )
        },
        shape = shape
    )
    .drawWithContent {
        drawContent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && size.width > 0f && size.height > 0f) {
            val hlShader = AgslLiquidGlass.highlightShader
            if (hlShader != null) {
                val radii = extractCornerRadii(shape, size, density)
                hlShader.setFloatUniform("size", size.width, size.height)
                hlShader.setFloatUniform("cornerRadii", radii[0], radii[1], radii[2], radii[3])
                hlShader.setFloatUniform("angle", (-45.0 * Math.PI / 180.0).toFloat())
                hlShader.setFloatUniform("falloff", 1.8f)
                val paint = Paint().apply {
                    this.shader = hlShader
                    this.alpha = if (isMine) (0.35f * 255).toInt() else (borderAlpha * 0.75f * 255).toInt()
                }
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
                }
            }
        }
    }
    .border(
        border = BorderStroke(
            width = 0.85.dp,
            brush = Brush.linearGradient(
                colors = if (isMine) {
                    listOf(
                        Color.White.copy(alpha = 0.55f),
                        Color(0xFF93C5FD).copy(alpha = 0.20f)
                    )
                } else {
                    listOf(
                        Color.White.copy(alpha = borderAlpha),
                        Color.White.copy(alpha = borderAlpha * 0.25f)
                    )
                },
                start = Offset.Zero,
                end = Offset.Infinite
            )
        ),
        shape = shape
    )

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
