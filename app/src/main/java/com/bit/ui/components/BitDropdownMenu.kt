package com.bit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

val DROPDOWN_CORNER = 24.dp
val DROPDOWN_MENU_SHAPE = RoundedCornerShape(DROPDOWN_CORNER)
val DROPDOWN_ITEM_SHAPE = RoundedCornerShape(DROPDOWN_CORNER)
val DROPDOWN_ITEM_INSET = 8.dp
private val DROPDOWN_ITEM_CONTENT_PADDING = PaddingValues(horizontal = 12.dp - DROPDOWN_ITEM_INSET)

val OPTION_HIGHLIGHT_SHAPE = DROPDOWN_ITEM_SHAPE
val SHEET_OPTION_INSET = DROPDOWN_ITEM_INSET

/**
 * Clickable option row whose ripple highlight is clipped to a 24dp capsule instead of a sharp rectangle.
 */
fun Modifier.optionClickable(
    enabled: Boolean = true,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = clip(OPTION_HIGHLIGHT_SHAPE).clickable(enabled = enabled, role = role, onClick = onClick)

/**
 * [optionClickable] for rows that span a bottom sheet's full width, inset by 8dp so the ripple
 * doesn't bleed against the screen edge.
 */
fun Modifier.sheetOptionClickable(
    enabled: Boolean = true,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = padding(horizontal = SHEET_OPTION_INSET).optionClickable(enabled, role, onClick)

/**
 * Expressive 24dp rounded dropdown menu with capsule-highlighted items.
 */
@Composable
fun BitDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    containerColor: Color = MenuDefaults.containerColor,
    tonalElevation: Dp = MenuDefaults.TonalElevation,
    shadowElevation: Dp = MenuDefaults.ShadowElevation,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.clip(DROPDOWN_MENU_SHAPE),
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        shape = DROPDOWN_MENU_SHAPE,
        containerColor = containerColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        content = content,
    )
}

/**
 * Dropdown menu item with 24dp capsule ripple highlight inset from menu sides.
 */
@Composable
fun BitDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(),
    contentPadding: PaddingValues = DROPDOWN_ITEM_CONTENT_PADDING,
    interactionSource: MutableInteractionSource? = null,
) {
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = DROPDOWN_ITEM_INSET)
            .clip(DROPDOWN_ITEM_SHAPE),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
    )
}
