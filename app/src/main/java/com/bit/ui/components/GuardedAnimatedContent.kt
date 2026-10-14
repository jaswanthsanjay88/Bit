package com.bit.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.bit.ui.motion.LocalBitMotionPolicy
import com.bit.ui.theme.Motion
import com.bit.ui.theme.MotionEasing

/**
 * High-performance animated content container with hardware-accelerated transitions
 * and pointer gating to eliminate frame drops and accidental multi-taps during transitions.
 */
@Composable
fun <T> GuardedAnimatedContent(
    targetState: T,
    forward: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    val allowSpatial = LocalBitMotionPolicy.current.allowSpatialTransitions

    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            val enter = if (allowSpatial) {
                slideInHorizontally(
                    animationSpec = Motion.pageIntOffsetSpring(),
                    initialOffsetX = { width ->
                        if (forward) (width * Motion.NavEnterSlideFraction).toInt()
                        else -(width * Motion.NavExitSlideFraction).toInt()
                    }
                ) + scaleIn(
                    animationSpec = Motion.pageFloatSpring(),
                    initialScale = Motion.NavScaleDepth
                ) + fadeIn(
                    animationSpec = tween(Motion.NavFadeInDuration, easing = MotionEasing.standard)
                )
            } else {
                fadeIn(animationSpec = tween(Motion.NavFadeInDuration, easing = MotionEasing.standard))
            }

            val exit = if (allowSpatial) {
                slideOutHorizontally(
                    animationSpec = Motion.pageIntOffsetSpring(),
                    targetOffsetX = { width ->
                        if (forward) -(width * Motion.NavExitSlideFraction).toInt()
                        else (width * Motion.NavEnterSlideFraction).toInt()
                    }
                ) + scaleOut(
                    animationSpec = Motion.pageFloatSpring(),
                    targetScale = Motion.NavScaleDepth
                ) + fadeOut(
                    animationSpec = tween(Motion.NavFadeOutDuration, easing = MotionEasing.standard)
                )
            } else {
                fadeOut(animationSpec = tween(Motion.NavFadeOutDuration, easing = MotionEasing.standard))
            }

            enter togetherWith exit
        },
        label = "guarded_animated_content"
    ) { state ->
        val isTarget = state == targetState
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!isTarget) Modifier.consumePointerInput() else Modifier)
        ) {
            content(state)
        }
    }
}

private fun Modifier.consumePointerInput(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            }
        }
    }
