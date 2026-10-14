package com.bit.ui.motion

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Centralized motion policy for BIT.
 * Automatically checks system animation settings and user accessibility preferences.
 */
@Immutable
data class BitMotionPolicy(
    val reduceMotion: Boolean = false,
) {
    val allowContinuousMotion: Boolean
        get() = !reduceMotion

    val allowSpatialTransitions: Boolean
        get() = !reduceMotion

    val allowProgrammaticScrollMotion: Boolean
        get() = !reduceMotion

    companion object {
        val Default = BitMotionPolicy(reduceMotion = false)
    }
}

val LocalBitMotionPolicy = staticCompositionLocalOf { BitMotionPolicy.Default }

internal fun resolveBitMotionPolicy(
    appReduceMotion: Boolean,
    systemAnimationsDisabled: Boolean,
): BitMotionPolicy = BitMotionPolicy(
    reduceMotion = appReduceMotion || systemAnimationsDisabled,
)

@Composable
fun ProvideBitMotionPolicy(
    appReduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemAnimationsDisabled = rememberSystemAnimationsDisabled()
    val policy = remember(appReduceMotion, systemAnimationsDisabled) {
        resolveBitMotionPolicy(
            appReduceMotion = appReduceMotion,
            systemAnimationsDisabled = systemAnimationsDisabled,
        )
    }
    CompositionLocalProvider(LocalBitMotionPolicy provides policy, content = content)
}

@Composable
private fun rememberSystemAnimationsDisabled(): Boolean {
    val context = LocalContext.current.applicationContext
    val resolver = context.contentResolver
    var disabled by remember(resolver) {
        mutableStateOf(readSystemAnimationsDisabled(resolver))
    }

    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                disabled = readSystemAnimationsDisabled(resolver)
            }
        }
        systemAnimationScaleKeys.forEach { key ->
            resolver.registerContentObserver(
                Settings.Global.getUriFor(key),
                false,
                observer,
            )
        }
        onDispose {
            resolver.unregisterContentObserver(observer)
        }
    }

    return disabled
}

private val systemAnimationScaleKeys = listOf(
    Settings.Global.ANIMATOR_DURATION_SCALE,
    Settings.Global.TRANSITION_ANIMATION_SCALE,
    Settings.Global.WINDOW_ANIMATION_SCALE,
)

private fun readSystemAnimationsDisabled(resolver: ContentResolver): Boolean =
    systemAnimationScaleKeys.any { key ->
        Settings.Global.getFloat(resolver, key, 1f) == 0f
    }
