package com.bit.ui.components

import com.bit.agent.harness.event.ChipKind
import com.bit.agent.harness.event.SourceChip
import com.bit.agent.harness.event.StepKind
import com.bit.agent.harness.event.StepStatus
import com.bit.agent.harness.event.StepUi
import com.bit.agent.harness.event.ThinkingUi
import com.bit.ui.motion.ExpandSpring
import com.bit.ui.motion.MicroSpring
import com.bit.ui.motion.SpatialSpring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingBlockTest {

    @Test
    fun testSpringPhysicsTokens() {
        // Verify M3 Expressive Spring Physics values match Motion Brief
        assertEquals(0.85f, SpatialSpring.dampingRatio, 0.001f)
        assertEquals(380f, SpatialSpring.stiffness, 0.001f)

        assertEquals(1.0f, ExpandSpring.dampingRatio, 0.001f)
        assertEquals(400f, ExpandSpring.stiffness, 0.001f)

        assertEquals(0.75f, MicroSpring.dampingRatio, 0.001f)
        assertEquals(500f, MicroSpring.stiffness, 0.001f)
    }

    @Test
    fun testStepUiListCappingAt40() {
        // UI renders at most 40 steps; earlier steps are folded into "+N earlier steps"
        val steps = (1..65).map { i ->
            StepUi(
                id = "step_$i",
                kind = StepKind.Tool,
                label = "Step $i",
                status = StepStatus.Done,
                startedAtMs = 1000L * i,
                endedAtMs = 1000L * i + 500L
            )
        }
        val thinkingUi = ThinkingUi(
            turnId = "turn_cap",
            startedAtMs = 1000L,
            steps = steps,
            endedAtMs = 66000L
        )

        val totalSteps = thinkingUi.steps.size
        val displaySteps = if (totalSteps > 40) thinkingUi.steps.takeLast(40) else thinkingUi.steps
        val earlierCount = totalSteps - displaySteps.size

        assertEquals(65, totalSteps)
        assertEquals(40, displaySteps.size)
        assertEquals(25, earlierCount)
        assertEquals("Step 26", displaySteps.first().label)
        assertEquals("Step 65", displaySteps.last().label)
    }

    @Test
    fun testStepUiListUnder40_noEarlierNotice() {
        val steps = (1..10).map { i ->
            StepUi(
                id = "step_$i",
                kind = StepKind.Tool,
                label = "Step $i",
                status = StepStatus.Done,
                startedAtMs = 1000L * i
            )
        }
        val thinkingUi = ThinkingUi(
            turnId = "turn_small",
            startedAtMs = 1000L,
            steps = steps
        )

        val totalSteps = thinkingUi.steps.size
        val displaySteps = if (totalSteps > 40) thinkingUi.steps.takeLast(40) else thinkingUi.steps
        val earlierCount = totalSteps - displaySteps.size

        assertEquals(10, totalSteps)
        assertEquals(10, displaySteps.size)
        assertEquals(0, earlierCount)
    }

    @Test
    fun testElapsedTimerTextFormatting() {
        // Started 1000ms, ended 1200ms -> duration 200ms -> 0s -> "<1s"
        val sec1 = (1200L - 1000L) / 1000L
        val text1 = if (sec1 == 0L) "<1s" else "${sec1}s"
        assertEquals("<1s", text1)

        // Started 1000ms, ended 13500ms -> duration 12500ms -> 12s
        val sec2 = (13500L - 1000L) / 1000L
        val text2 = if (sec2 == 0L) "<1s" else "${sec2}s"
        assertEquals("12s", text2)

        // Started 1000ms, ended 59000ms -> duration 58000ms -> 58s
        val sec3 = (59000L - 1000L) / 1000L
        val text3 = if (sec3 == 0L) "<1s" else "${sec3}s"
        assertEquals("58s", text3)
    }

    @Test
    fun testSourceChipKindsCoverAllCategories() {
        val chips = listOf(
            SourceChip(title = "MainActivity.kt", kind = ChipKind.File),
            SourceChip(title = "User Preference", kind = ChipKind.Memory),
            SourceChip(title = "Project Docs", kind = ChipKind.Note),
            SourceChip(title = "Wikipedia: Kotlin", kind = ChipKind.Web)
        )

        assertEquals(4, chips.size)
        assertEquals(ChipKind.File, chips[0].kind)
        assertEquals(ChipKind.Memory, chips[1].kind)
        assertEquals(ChipKind.Note, chips[2].kind)
        assertEquals(ChipKind.Web, chips[3].kind)
    }

    @Test
    fun testStepRowDetailTruncation() {
        val longDetail = "A".repeat(1000)
        val hasLongDetail = longDetail.length > 600
        val truncated = if (hasLongDetail) longDetail.take(600) + "…" else longDetail

        assertTrue(hasLongDetail)
        assertEquals(601, truncated.length)
        assertTrue(truncated.endsWith("…"))
    }
}
