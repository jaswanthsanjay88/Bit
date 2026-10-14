package com.bit.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bit.models.messages.MessageVersionInfo

/**
 * Agora / ChatGPT style version pagination control:
 * Displays `< 1 / 2 >` or `< 2 / 2 >` allowing users to switch between
 * sibling responses generated for the same turn.
 */
@Composable
fun VersionPager(
    currentVersion: Int,
    totalVersions: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (totalVersions <= 1) return

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            PagerNavButton(
                icon = Icons.AutoMirrored.Filled.NavigateBefore,
                enabled = currentVersion > 1,
                onClick = onPrevious,
                contentDescription = "Previous response version"
            )

            AnimatedContent(
                targetState = currentVersion,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "VersionPagerCounter"
            ) { ver ->
                Text(
                    text = "$ver / $totalVersions",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            PagerNavButton(
                icon = Icons.AutoMirrored.Filled.NavigateNext,
                enabled = currentVersion < totalVersions,
                onClick = onNext,
                contentDescription = "Next response version"
            )
        }
    }
}

@Composable
private fun PagerNavButton(
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isActive = isPressed || isHovered

    val iconColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
        isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    }

    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(radius = 12.dp),
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )
    }
}
