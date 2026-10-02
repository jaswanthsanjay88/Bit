package com.bit.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class MaterialUiElementsTest {

    @Test
    fun testUserBubbleCornerRadiusCalculation() {
        val normalSize = Size(600f, 200f)
        val requestedPx = 60f
        val computed = userBubbleCornerRadiusPx(requestedPx, normalSize)
        assertEquals(60f, computed, 0.001f)

        // Short bubble where minDimension / 2 is smaller than requestedPx
        val shortBubbleSize = Size(100f, 40f) // minDimension is 40f -> half is 20f
        val computedClamped = userBubbleCornerRadiusPx(requestedPx, shortBubbleSize)
        assertEquals(20f, computedClamped, 0.001f)

        // Zero size edge case
        val zeroSize = Size(0f, 0f)
        val computedZero = userBubbleCornerRadiusPx(requestedPx, zeroSize)
        assertEquals(0f, computedZero, 0.001f)
    }

    @Test
    fun testUserBubbleShapeOutline() {
        val shape = UserBubbleShape(cornerRadius = 24.dp, tailRadius = 6.dp)
        val density = Density(density = 2f)
        val size = Size(400f, 160f)

        val outline = shape.createOutline(size, LayoutDirection.Ltr, density)
        assertNotNull(outline)
    }

    @Test
    fun testDropdownGeometryConstants() {
        assertEquals(24.dp, DROPDOWN_CORNER)
        assertEquals(8.dp, DROPDOWN_ITEM_INSET)
        assertEquals(DROPDOWN_ITEM_SHAPE, OPTION_HIGHLIGHT_SHAPE)
        assertEquals(DROPDOWN_ITEM_INSET, SHEET_OPTION_INSET)
    }

    @Test
    fun testBottomSheetConstants() {
        assertEquals(640.dp, BottomSheetMaxWidth)
        assertEquals(28.dp, BOTTOM_SHEET_SHAPE.topStart.toPx(Size(100f, 100f), Density(1f)).dp)
        assertEquals(28.dp, BOTTOM_SHEET_SHAPE.topEnd.toPx(Size(100f, 100f), Density(1f)).dp)
    }

    @Test
    fun testContextBudgetCompactLabel() {
        assertEquals("0", ContextBudget.compactLabel(0))
        assertEquals("850", ContextBudget.compactLabel(850))
        assertEquals("1K", ContextBudget.compactLabel(1024))
        assertEquals("1.2K", ContextBudget.compactLabel(1228))
        assertEquals("4K", ContextBudget.compactLabel(4096))
        assertEquals("8K", ContextBudget.compactLabel(8192))
        assertEquals("32K", ContextBudget.compactLabel(32768))
        assertEquals("1M", ContextBudget.compactLabel(1048576))
    }

    @Test
    fun testHeuristicTextTokenCounter() {
        assertEquals(0L, HeuristicTextTokenCounter.count(""))
        val wordTokens = HeuristicTextTokenCounter.count("Hello world")
        assertTrue(wordTokens > 0L)
        // Chinese characters cost 1 token each
        val cjkTokens = HeuristicTextTokenCounter.count("你好世界")
        assertEquals(4L, cjkTokens)
    }
}
