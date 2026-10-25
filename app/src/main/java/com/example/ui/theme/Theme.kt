package com.example.ui.theme

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val BaselineDarkColorScheme = darkColorScheme(
    primary = Md3PrimaryDark,
    onPrimary = Md3OnPrimaryDark,
    primaryContainer = Md3PrimaryContainerDark,
    onPrimaryContainer = Md3OnPrimaryContainerDark,
    secondary = Md3SecondaryDark,
    onSecondary = Md3OnSecondaryDark,
    secondaryContainer = Md3SecondaryContainerDark,
    onSecondaryContainer = Md3OnSecondaryContainerDark,
    tertiary = Color(0xFFE2BADA),
    onTertiary = Md3OnTertiaryDark,
    tertiaryContainer = Md3TertiaryContainerDark,
    onTertiaryContainer = Md3OnTertiaryContainerDark,
    background = Md3SurfaceDark,
    surface = Md3SurfaceDark,
    onSurface = Md3OnSurfaceDark,
    onSurfaceVariant = Md3OnSurfaceVariantDark,
    surfaceContainerLowest = Md3SurfaceContainerLowestDark,
    surfaceContainerLow = Md3SurfaceContainerLowDark,
    surfaceContainer = Md3SurfaceContainerDark,
    surfaceContainerHigh = Md3SurfaceContainerHighDark,
    surfaceContainerHighest = Md3SurfaceContainerHighestDark
)

private val BaselineLightColorScheme = lightColorScheme(
    primary = Md3PrimaryLight,
    onPrimary = Md3OnPrimaryLight,
    primaryContainer = Md3PrimaryContainerLight,
    onPrimaryContainer = Md3OnPrimaryContainerLight,
    secondary = Md3SecondaryLight,
    onSecondary = Md3OnSecondaryLight,
    secondaryContainer = Md3SecondaryContainerLight,
    onSecondaryContainer = Md3OnSecondaryContainerLight,
    tertiary = Md3TertiaryLight,
    onTertiary = Md3OnTertiaryLight,
    tertiaryContainer = Md3TertiaryContainerLight,
    onTertiaryContainer = Md3OnTertiaryContainerLight,
    background = Md3SurfaceLight,
    surface = Md3SurfaceLight,
    onSurface = Md3OnSurfaceLight,
    onSurfaceVariant = Md3OnSurfaceVariantLight,
    surfaceContainerLowest = Md3SurfaceContainerLowestLight,
    surfaceContainerLow = Md3SurfaceContainerLowLight,
    surfaceContainer = Md3SurfaceContainerLight,
    surfaceContainerHigh = Md3SurfaceContainerHighLight,
    surfaceContainerHighest = Md3SurfaceContainerHighestLight
)

val MomentExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * Generates a complete Material Design 3 Monet tonal ColorScheme from any wallpaper seed color.
 */
fun generateMonetColorSchemeFromSeed(seedColor: Color, darkTheme: Boolean): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seedColor.toArgb(), hsl)
    val hue = hsl[0]
    val secondaryHue = (hue + 18f) % 360f
    val tertiaryHue = (hue + 60f) % 360f

    fun tone(h: Float, s: Float, l: Float): Color {
        val rgb = ColorUtils.HSLToColor(floatArrayOf(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f)))
        return Color(rgb)
    }

    return if (darkTheme) {
        darkColorScheme(
            primary = tone(hue, 0.78f, 0.80f),
            onPrimary = tone(hue, 0.85f, 0.18f),
            primaryContainer = tone(hue, 0.70f, 0.28f),
            onPrimaryContainer = tone(hue, 0.85f, 0.90f),
            secondary = tone(secondaryHue, 0.32f, 0.78f),
            onSecondary = tone(secondaryHue, 0.40f, 0.20f),
            secondaryContainer = tone(secondaryHue, 0.35f, 0.28f),
            onSecondaryContainer = tone(secondaryHue, 0.45f, 0.88f),
            tertiary = tone(tertiaryHue, 0.48f, 0.80f),
            onTertiary = tone(tertiaryHue, 0.55f, 0.20f),
            tertiaryContainer = tone(tertiaryHue, 0.45f, 0.30f),
            onTertiaryContainer = tone(tertiaryHue, 0.60f, 0.90f),
            background = tone(hue, 0.14f, 0.08f),
            surface = tone(hue, 0.14f, 0.08f),
            onSurface = tone(hue, 0.12f, 0.90f),
            onSurfaceVariant = tone(hue, 0.14f, 0.76f),
            surfaceContainerLowest = tone(hue, 0.14f, 0.05f),
            surfaceContainerLow = tone(hue, 0.14f, 0.11f),
            surfaceContainer = tone(hue, 0.15f, 0.13f),
            surfaceContainerHigh = tone(hue, 0.15f, 0.17f),
            surfaceContainerHighest = tone(hue, 0.16f, 0.22f)
        )
    } else {
        lightColorScheme(
            primary = tone(hue, 0.72f, 0.38f),
            onPrimary = Color.White,
            primaryContainer = tone(hue, 0.78f, 0.90f),
            onPrimaryContainer = tone(hue, 0.85f, 0.14f),
            secondary = tone(secondaryHue, 0.30f, 0.40f),
            onSecondary = Color.White,
            secondaryContainer = tone(secondaryHue, 0.42f, 0.90f),
            onSecondaryContainer = tone(secondaryHue, 0.48f, 0.15f),
            tertiary = tone(tertiaryHue, 0.45f, 0.40f),
            onTertiary = Color.White,
            tertiaryContainer = tone(tertiaryHue, 0.55f, 0.90f),
            onTertiaryContainer = tone(tertiaryHue, 0.60f, 0.16f),
            background = tone(hue, 0.25f, 0.98f),
            surface = tone(hue, 0.25f, 0.98f),
            onSurface = tone(hue, 0.18f, 0.11f),
            onSurfaceVariant = tone(hue, 0.15f, 0.32f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = tone(hue, 0.24f, 0.95f),
            surfaceContainer = tone(hue, 0.24f, 0.93f),
            surfaceContainerHigh = tone(hue, 0.22f, 0.90f),
            surfaceContainerHighest = tone(hue, 0.22f, 0.87f)
        )
    }
}

/**
 * Extracts the dominant chromatic Monet seed color from a user-selected wallpaper image URI.
 */
suspend fun extractWallpaperSeedColor(context: Context, uri: Uri): Color? = withContext(Dispatchers.IO) {
    try {
        val options = BitmapFactory.Options().apply { inSampleSize = 8 }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return@withContext null

        val width = bitmap.width
        val height = bitmap.height
        val stepX = (width / 24).coerceAtLeast(1)
        val stepY = (height / 24).coerceAtLeast(1)

        val hsl = FloatArray(3)
        // Quantize hues into 36 buckets of 10 degrees each, weighted by saturation & mid-lightness
        val hueWeights = FloatArray(36)
        val hueSumR = FloatArray(36)
        val hueSumG = FloatArray(36)
        val hueSumB = FloatArray(36)

        var x = 0
        while (x < width) {
            var y = 0
            while (y < height) {
                val pixel = bitmap.getPixel(x, y)
                ColorUtils.colorToHSL(pixel, hsl)
                val sat = hsl[1]
                val light = hsl[2]
                // Prefer chromatic, non-black, non-white wallpaper pixels
                val weight = sat * (1f - kotlin.math.abs(light - 0.5f) * 1.6f).coerceAtLeast(0.1f)
                if (weight > 0.05f) {
                    val bucket = ((hsl[0].toInt() % 360) / 10).coerceIn(0, 35)
                    hueWeights[bucket] += weight
                    hueSumR[bucket] += ((pixel shr 16) and 0xFF) * weight
                    hueSumG[bucket] += ((pixel shr 8) and 0xFF) * weight
                    hueSumB[bucket] += (pixel and 0xFF) * weight
                }
                y += stepY
            }
            x += stepX
        }
        bitmap.recycle()

        var bestBucket = 0
        var maxWeight = -1f
        for (i in 0 until 36) {
            if (hueWeights[i] > maxWeight) {
                maxWeight = hueWeights[i]
                bestBucket = i
            }
        }

        if (maxWeight <= 0f) return@withContext null
        val r = (hueSumR[bestBucket] / maxWeight).toInt().coerceIn(0, 255)
        val g = (hueSumG[bestBucket] / maxWeight).toInt().coerceIn(0, 255)
        val b = (hueSumB[bestBucket] / maxWeight).toInt().coerceIn(0, 255)
        Color(android.graphics.Color.rgb(r, g, b))
    } catch (_: Exception) {
        null
    }
}

@Composable
fun MomentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    customWallpaperSeed: Color? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        customWallpaperSeed != null -> generateMonetColorSchemeFromSeed(customWallpaperSeed, darkTheme)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> BaselineDarkColorScheme
        else -> BaselineLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = MomentExpressiveShapes,
        content = content
    )
}
