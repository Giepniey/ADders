package com.adders.app

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
