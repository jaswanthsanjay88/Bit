package com.bit.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * User bubble outline: top-start, top-end, and bottom-start share one radius, while bottom-end is the tail anchor.
 *
 * RoundedCornerShape shrinks each side's corner pair on its own when the bubble is shorter or
 * narrower than two radii, causing asymmetric distortion on short 1-line messages. Here the shared
 * radius is clamped once to half the smaller dimension so all three large corners always match.
 */
data class UserBubbleShape(
    val cornerRadius: Dp = 24.dp,
    val tailRadius: Dp = 6.dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radius = userBubbleCornerRadiusPx(with(density) { cornerRadius.toPx() }, size)
        val tail = min(with(density) { tailRadius.toPx() }, radius)
        val large = CornerRadius(radius)
        val small = CornerRadius(tail)
        val ltr = layoutDirection == LayoutDirection.Ltr
        return Outline.Rounded(
            RoundRect(
                rect = size.toRect(),
                topLeft = large,
                topRight = large,
                bottomRight = if (ltr) small else large,
                bottomLeft = if (ltr) large else small,
            ),
        )
    }
}

/** Shared radius for the three large corners: capped at half the smaller dimension. */
internal fun userBubbleCornerRadiusPx(requestedPx: Float, size: Size): Float =
    min(requestedPx, size.minDimension / 2f).coerceAtLeast(0f)
