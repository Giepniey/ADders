package com.adders.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ClampFadeDurationTest {
    @Test fun zeroRemainsZero() =
        assertEquals(0, clampFadeDuration(0))

    @Test fun oneMigratesToThree() =
        assertEquals(3, clampFadeDuration(1))

    @Test fun twoMigratesToThree() =
        assertEquals(3, clampFadeDuration(2))

    @Test fun threeToFifteenStaySame() {
        assertEquals(3, clampFadeDuration(3))
        assertEquals(7, clampFadeDuration(7))
        assertEquals(15, clampFadeDuration(15))
    }

    @Test fun overFifteenClampsToFifteen() =
        assertEquals(15, clampFadeDuration(20))

    @Test fun negativeValuesMigrateToThree() =
        assertEquals(3, clampFadeDuration(-5))
}

class VolumeRampTest {
    @Test fun startsAtStartingVolume() =
        assertEquals(20, VolumeRamp.value(20, 50, 0f))

    @Test fun endsAtTargetVolume() =
        assertEquals(50, VolumeRamp.value(20, 50, 1f))

    @Test fun interpolatesLinearly() {
        assertEquals(35, VolumeRamp.value(20, 50, 0.5f))
        assertEquals(50, VolumeRamp.value(50, 0, 0f))
        assertEquals(0, VolumeRamp.value(50, 0, 1f))
        assertEquals(25, VolumeRamp.value(50, 0, 0.5f))
    }

    @Test fun clampsFractionAndStaysInRange() {
        assertEquals(20, VolumeRamp.value(20, 50, -1f))
        assertEquals(50, VolumeRamp.value(20, 50, 2f))
        assertEquals(0, VolumeRamp.value(0, 100, 0f))
        assertEquals(100, VolumeRamp.value(0, 100, 1f))
    }

    @Test fun startEqualsTargetReturnsTarget() {
        assertEquals(50, VolumeRamp.value(50, 50, 0f))
        assertEquals(50, VolumeRamp.value(50, 50, 0.5f))
        assertEquals(50, VolumeRamp.value(50, 50, 1f))
    }
}

class VolumeRampStepTest {
    @Test fun stepIntervalWithOneStepIsZero() =
        assertEquals(0L, VolumeRamp.stepIntervalMs(10000L, 1))

    @Test fun stepIntervalWithTwoStepsIsFullDuration() =
        assertEquals(10000L, VolumeRamp.stepIntervalMs(10000L, 2))

    @Test fun stepIntervalDividesEvenly() =
        assertEquals(1000L, VolumeRamp.stepIntervalMs(15000L, 16)) // 15s / 15 steps = 1000ms

    @Test fun stepNumberAtStartIsZero() =
        assertEquals(0, VolumeRamp.stepNumber(0L, 10000L, 10))

    @Test fun stepNumberAtEndIsLastStep() =
        assertEquals(9, VolumeRamp.stepNumber(10000L, 10000L, 10))

    @Test fun stepNumberAtHalfway() =
        assertEquals(5, VolumeRamp.stepNumber(5000L, 10000L, 11)) // 10 steps of 1s each, 5s = step 5

    @Test fun stepNumberAtExactStepTime() {
        // 10s duration, 16 steps (15 intervals of 666.67ms each)
        // At 3000ms (exactly 4.5 steps), should round to step 4
        assertEquals(4, VolumeRamp.stepNumber(3000L, 10000L, 16))
    }

    @Test fun stepNumberLastStepLandsAtDuration() {
        // With 16 steps over 15s, the last step should be exactly at 15s
        val durationMs = 15000L
        val steps = 16
        val lastStep = VolumeRamp.stepNumber(durationMs, durationMs, steps)
        assertEquals(15, lastStep)
    }

    @Test fun stepScheduleEvenlySpaced() {
        // Verify that with N steps, the last step lands at exactly N seconds
        val durationSec = 5
        val durationMs = durationSec * 1000L
        val maxVolume = 15 // typical max volume steps
        val steps = maxVolume + 1

        // First step should be at time 0
        assertEquals(0, VolumeRamp.stepNumber(0L, durationMs, steps))

        // Last step should be at time durationMs
        assertEquals(steps - 1, VolumeRamp.stepNumber(durationMs, durationMs, steps))

        // Middle step should be at half duration
        val halfStep = (steps - 1) / 2
        assertEquals(halfStep, VolumeRamp.stepNumber(durationMs / 2, durationMs, steps))
    }
}
