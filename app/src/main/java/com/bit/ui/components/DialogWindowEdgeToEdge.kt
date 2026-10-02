package com.bit.ui.components

import android.graphics.Color
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider

internal val BottomSheetMaxWidth = 640.dp

/** Top-corner shape shared by bottom sheets (Material 3 extra-large 28dp corner). */
internal val BOTTOM_SHEET_SHAPE = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/**
 * Restores edge-to-edge / transparent navigation bar inside a Compose dialog or ModalBottomSheet window.
 *
 * Such composables render in their own Window, which does not inherit the Activity window's
 * edge-to-edge configuration. On API 29+ the new window defaults `isNavigationBarContrastEnforced = true`,
 * causing the system to paint a translucent scrim behind the nav bar. Call this as the first line
 * inside dialog/sheet content.
 */
@Composable
fun DialogWindowEdgeToEdge() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    SideEffect {
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }
}

/**
 * Removes Android's independently applied Dialog dim layer so Compose can own the backdrop scrim animation
 * without introducing a 1-frame black flash.
 */
@Composable
fun DialogWindowNoSystemDim() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    SideEffect {
        window.setDimAmount(0f)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }
}

/**
 * Disables conflicting native window enter/exit animations so Compose handles transitions smoothly.
 */
@Composable
fun DialogWindowNoSystemAnimation() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    SideEffect {
        window.setWindowAnimations(0)
    }
}
