package com.bit.ui.motion

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// M3 Expressive Spring Physics Tokens (BIT Motion Brief)
// ─────────────────────────────────────────────────────────────────────────────

/** Fluid expansion and spatial navigation transitions */
val SpatialSpring = spring<Float>(
    dampingRatio = 0.85f,
    stiffness = 380f
)

/** Card expansion / collapse spring */
val ExpandSpring = spring<IntSize>(
    dampingRatio = 1.0f,
    stiffness = 400f
)

/** Snappy micro-interactions (chevrons, icons, badges) */
val MicroSpring = spring<Float>(
    dampingRatio = 0.75f,
    stiffness = 500f
)

/**
 * Monitors device thermal status, battery saver, and animator duration scale.
 * When active, disables continuous shimmer sweep to prevent thermal throttling and frame drops.
 */
@Composable
fun rememberCalmMode(): State<Boolean> {
    val context = LocalContext.current
    return produceState(initialValue = false, context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val contentResolver = context.contentResolver

        fun check(): Boolean {
            val isPowerSave = powerManager?.isPowerSaveMode == true
            val isThermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                (powerManager?.currentThermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_MODERATE
            } else false
            val animScale = runCatching {
                Settings.Global.getFloat(
                    contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1.0f
                )
            }.getOrDefault(1.0f)
            return isPowerSave || isThermal || animScale == 0f
        }

        value = check()
        while (true) {
            delay(5000L)
            val current = check()
            if (value != current) {
                value = current
            }
        }
    }
}

/**
 * Draw-phase shimmer sweep.
 * Runs strictly in the Draw phase with zero recompositions.
 * Uses Offscreen compositing and SrcAtop blend mode to clip shimmer strictly to content pixels.
 */
@Composable
fun Modifier.shimmerSweep(
    isShimmering: Boolean,
    highlightColor: Color = Color.White.copy(alpha = 0.45f),
    durationMs: Int = 1600,
    calmMode: Boolean = false
): Modifier {
    if (!isShimmering || calmMode) return this

    val infiniteTransition = rememberInfiniteTransition(label = "shimmerTransition")
    val progress by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    return this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val w = size.width
            if (w > 0f) {
                val band = w * 0.6f
                val cx = w * progress
                val brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        highlightColor,
                        Color.Transparent
                    ),
                    start = Offset(cx - band / 2f, 0f),
                    end = Offset(cx + band / 2f, 0f)
                )
                drawRect(
                    brush = brush,
                    blendMode = BlendMode.SrcAtop
                )
            }
        }
}

/**
 * Creates a single block-level shimmer transition state.
 * Shared between header label and active step row for synchronous phase sweep.
 */
@Composable
fun rememberShimmerProgress(
    isShimmering: Boolean,
    calmMode: Boolean = false,
    durationMs: Int = 1600
): State<Float>? {
    if (!isShimmering || calmMode) return null
    val infiniteTransition = rememberInfiniteTransition(label = "blockShimmerTransition")
    return infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )
}

/**
 * Draw-phase shimmer modifier reading a shared progress state.
 * Clips highlight band strictly to text content with zero recompositions.
 */
fun Modifier.syncedShimmer(
    progressState: State<Float>?,
    strength: Float = 1f,
    highlightColor: Color = Color.White.copy(alpha = 0.9f),
    bandFraction: Float = 0.45f
): Modifier {
    if (progressState == null || strength <= 0f) return this
    return this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val w = size.width
            if (w > 0f) {
                val band = w * bandFraction
                val cx = w * progressState.value
                val peak = highlightColor.copy(alpha = highlightColor.alpha * strength)
                val brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        peak,
                        Color.Transparent
                    ),
                    start = Offset(cx - band / 2f, 0f),
                    end = Offset(cx + band / 2f, 0f)
                )
                drawRect(
                    brush = brush,
                    blendMode = BlendMode.SrcAtop
                )
            }
        }
}

// ─────────────────────────────────────────────────────────────────────────────
// Logo Choreography & Presence States (BIT Motion Brief §2)
// ─────────────────────────────────────────────────────────────────────────────

enum class BitPresence {
    Idle,
    Thinking,
    Streaming
}

/**
 * Top-bar logo component that responds to live harness presence states:
 * - Idle: Steady glyph, resting contrast (onSurface).
 * - Thinking: Subtle breathing luminance pulse (1600ms cycle) synchronized with reasoning tokens.
 * - Streaming: Focused active glow (primary) during text generation.
 * If calm mode is active, the continuous breathing pulse is disabled.
 */
@Composable
fun BitPresenceLogo(
    presence: BitPresence,
    modifier: Modifier = Modifier,
    calm: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bitPresencePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bitLogoPulse"
    )

    val logoColor = when (presence) {
        BitPresence.Idle -> MaterialTheme.colorScheme.onSurface
        BitPresence.Thinking -> {
            if (calm) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
            }
        }
        BitPresence.Streaming -> MaterialTheme.colorScheme.primary
    }

    com.bit.ui.screen.intro.AnimatedLogo(
        modifier = modifier.size(30.dp),
        color = logoColor
    )
}

