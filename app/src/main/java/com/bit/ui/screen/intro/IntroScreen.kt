package com.bit.ui.screen.intro

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// MOTION TOKENS — one easing curve used everywhere, this is what makes the
// whole reveal feel like ONE designed system instead of separate toys.
// ---------------------------------------------------------------------------
val EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0f, 1.0f)
val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

// ---------------------------------------------------------------------------
// LOGO GEOMETRY — bar + top block + bottom block, no outer frame.
// Proportions match the canonical 120x120 BIT mark with sleek rounded corners.
// ---------------------------------------------------------------------------
private data class LogoShape(val path: Path, val bounds: Rect, val revealFromTop: Boolean)

private fun buildLogoShapes(canvasSize: Size): List<LogoShape> {
    val w = canvasSize.width
    val h = canvasSize.height

    val barLeft = w * 0.28f
    val barWidth = w * 0.12f
    val barTop = h * 0.22f
    val barBottom = h * 0.78f

    val blockLeft = w * 0.47f
    val blockRight = w * 0.72f
    val blockTop = h * 0.22f
    val blockMidGapTop = h * 0.485f
    val blockMidGapBottom = h * 0.515f
    val blockBottom = h * 0.78f

    val barRect = Rect(barLeft, barTop, barLeft + barWidth, barBottom)
    val topBlockRect = Rect(blockLeft, blockTop, blockRight, blockMidGapTop)
    val bottomBlockRect = Rect(blockLeft, blockMidGapBottom, blockRight, blockBottom)

    val cornerRadius = CornerRadius(w * 0.024f, w * 0.024f)

    return listOf(
        LogoShape(
            Path().apply { addRoundRect(RoundRect(barRect, cornerRadius)) },
            barRect,
            revealFromTop = false
        ), // bar rises bottom->top
        LogoShape(
            Path().apply { addRoundRect(RoundRect(topBlockRect, cornerRadius)) },
            topBlockRect,
            revealFromTop = true
        ),
        LogoShape(
            Path().apply { addRoundRect(RoundRect(bottomBlockRect, cornerRadius)) },
            bottomBlockRect,
            revealFromTop = true
        )
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun IntroScreen(
    innerPadding: PaddingValues,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    targetDestination: String?,
    onFinish: (String) -> Unit = {}
) {
    var animationFinished by remember { mutableStateOf(false) }

    // Navigate only when BOTH the animation has finished AND the target destination is loaded/resolved
    LaunchedEffect(targetDestination, animationFinished) {
        if (animationFinished && targetDestination != null) {
            onFinish(targetDestination)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(innerPadding),
        contentAlignment = Alignment.Center
    ) {
        with(sharedTransitionScope) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .sharedElement(
                        sharedTransitionScope.rememberSharedContentState(key = "bit_mark"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
            ) {
                ElementLogoReveal(
                    modifier = Modifier.fillMaxSize(),
                    logoColor = MaterialTheme.colorScheme.primary,
                    onFinished = {
                        animationFinished = true
                    }
                )
            }
        }
    }
}

// Each shape is revealed via a clip-wipe (the shape is always full size and
// in its final position — a mask simply slides across it), not a scale-pop.
// All three shapes use the SAME easing curve and SAME duration; only their
// START TIME is staggered. That single-easing-curve rule is what keeps this
// from looking like three unrelated animations bolted together.
@Composable
fun ElementLogoReveal(
    modifier: Modifier = Modifier,
    logoColor: Color = MaterialTheme.colorScheme.primary,
    onFinished: () -> Unit = {}
) {
    // One progress value per shape, all driven by the same tween spec
    val barReveal = remember { Animatable(0f) }
    val topBlockReveal = remember { Animatable(0f) }
    val bottomBlockReveal = remember { Animatable(0f) }

    // Ambient radial aura that blooms behind the mark
    val ambientAlpha = remember { Animatable(0f) }

    // Subtle outline glow that traces alongside the reveal front
    val glowAlpha = remember { Animatable(0.45f) }

    // Final settle — delicate spring-damped pulse
    val settleScale = remember { Animatable(1f) }

    val revealDuration = 280 // crisp and snappy
    val stagger = 50 // fast fluid stagger

    LaunchedEffect(Unit) {
        delay(40) // minimal hold for initial frame settle

        // Ambient theme aura blooms
        launch { ambientAlpha.animateTo(1f, tween(300, easing = EmphasizedEasing)) }

        // Pillar rises from bottom to top
        launch { barReveal.animateTo(1f, tween(revealDuration, easing = EmphasizedEasing)) }
        delay(stagger.toLong())
        // Top block cascades down
        launch { topBlockReveal.animateTo(1f, tween(revealDuration, easing = EmphasizedEasing)) }
        delay(stagger.toLong())
        // Bottom block cascades down
        launch { bottomBlockReveal.animateTo(1f, tween(revealDuration, easing = EmphasizedEasing)) }

        // Wait for reveals to conclude
        delay(revealDuration.toLong())

        // Glow and aura settle
        launch { glowAlpha.animateTo(0f, tween(160, easing = EmphasizedEasing)) }
        launch { ambientAlpha.animateTo(0.4f, tween(180, easing = EmphasizedDecelerate)) }

        // Snappy organic settle
        settleScale.animateTo(1.025f, tween(80, easing = EmphasizedEasing))
        settleScale.animateTo(1f, tween(110, easing = EmphasizedDecelerate))

        delay(40)
        onFinished()
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = settleScale.value
                    scaleY = settleScale.value
                }
        ) {
            val shapes = buildLogoShapes(size)
            val progresses = listOf(barReveal.value, topBlockReveal.value, bottomBlockReveal.value)

            // 1. Theme ambient radial lighting aura behind the mark
            if (ambientAlpha.value > 0.001f) {
                val auraRadius = size.minDimension * 0.72f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            logoColor.copy(alpha = 0.12f * ambientAlpha.value),
                            logoColor.copy(alpha = 0.03f * ambientAlpha.value),
                            Color.Transparent
                        ),
                        center = center,
                        radius = auraRadius
                    ),
                    radius = auraRadius,
                    center = center
                )
            }

            // 2. Geometric logo shapes reveal
            shapes.forEachIndexed { index, shape ->
                val progress = progresses[index]
                if (progress <= 0f) return@forEachIndexed

                val b = shape.bounds
                val clipRect = if (shape.revealFromTop) {
                    Rect(b.left, b.top, b.right, b.top + (b.height * progress))
                } else {
                    Rect(b.left, b.bottom - (b.height * progress), b.right, b.bottom)
                }

                clipRect(clipRect.left, clipRect.top, clipRect.right, clipRect.bottom) {
                    // Soft glow trace along the leading edge
                    if (glowAlpha.value > 0.01f) {
                        val glowHeight = b.height * 0.12f
                        val edgeGlowRect = if (shape.revealFromTop) {
                            val y = b.top + (b.height * progress)
                            Rect(b.left, y - glowHeight, b.right, y + 2f)
                        } else {
                            val y = b.bottom - (b.height * progress)
                            Rect(b.left, y - 2f, b.right, y + glowHeight)
                        }
                        drawRoundRect(
                            color = logoColor.copy(alpha = glowAlpha.value * 0.35f),
                            topLeft = Offset(edgeGlowRect.left, edgeGlowRect.top),
                            size = Size(edgeGlowRect.width, edgeGlowRect.height),
                            cornerRadius = CornerRadius(size.width * 0.02f, size.width * 0.02f)
                        )
                    }

                    // Crisp solid fill adapting to active theme
                    drawPath(path = shape.path, color = logoColor, style = Fill)
                }
            }
        }
    }
}

@Composable
fun AnimatedLogo(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier = modifier) {
        val shapes = buildLogoShapes(size)
        shapes.forEach { shape ->
            drawPath(path = shape.path, color = color, style = Fill)
        }
    }
}
