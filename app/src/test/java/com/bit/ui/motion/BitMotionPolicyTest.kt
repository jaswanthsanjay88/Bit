package com.bit.ui.motion

import com.bit.ui.theme.Motion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BitMotionPolicyTest {

    @Test
    fun defaultPolicy_allowsAllMotion() {
        val policy = BitMotionPolicy.Default
        assertFalse(policy.reduceMotion)
        assertTrue(policy.allowSpatialTransitions)
        assertTrue(policy.allowContinuousMotion)
        assertTrue(policy.allowProgrammaticScrollMotion)
    }

    @Test
    fun resolvePolicy_enabledWhenBothDisabled() {
        val policy = resolveBitMotionPolicy(
            appReduceMotion = false,
            systemAnimationsDisabled = false
        )
        assertFalse(policy.reduceMotion)
        assertTrue(policy.allowSpatialTransitions)
        assertTrue(policy.allowContinuousMotion)
        assertTrue(policy.allowProgrammaticScrollMotion)
    }

    @Test
    fun resolvePolicy_reducedWhenAppReduceMotionEnabled() {
        val policy = resolveBitMotionPolicy(
            appReduceMotion = true,
            systemAnimationsDisabled = false
        )
        assertTrue(policy.reduceMotion)
        assertFalse(policy.allowSpatialTransitions)
        assertFalse(policy.allowContinuousMotion)
        assertFalse(policy.allowProgrammaticScrollMotion)
    }

    @Test
    fun resolvePolicy_reducedWhenSystemAnimationsDisabled() {
        val policy = resolveBitMotionPolicy(
            appReduceMotion = false,
            systemAnimationsDisabled = true
        )
        assertTrue(policy.reduceMotion)
        assertFalse(policy.allowSpatialTransitions)
        assertFalse(policy.allowContinuousMotion)
        assertFalse(policy.allowProgrammaticScrollMotion)
    }

    @Test
    fun motionConstants_matchPhysicsSpec() {
        assertEquals(0.22f, Motion.NavEnterSlideFraction, 0.0001f)
        assertEquals(0.12f, Motion.NavExitSlideFraction, 0.0001f)
        assertEquals(0.96f, Motion.NavScaleDepth, 0.0001f)
        assertEquals(240, Motion.NavFadeInDuration)
        assertEquals(180, Motion.NavFadeOutDuration)
    }
}
