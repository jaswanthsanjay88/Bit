package com.bit.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material Design 3 Shape Scale for BIT.
 */
val BitShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object BitShapeScale {
    val extraSmall: CornerBasedShape get() = BitShapes.extraSmall
    val small: CornerBasedShape get() = BitShapes.small
    val medium: CornerBasedShape get() = BitShapes.medium
    val large: CornerBasedShape get() = BitShapes.large
    val extraLarge: CornerBasedShape get() = BitShapes.extraLarge
    val full: CornerBasedShape = CircleShape
}
