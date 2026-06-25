package com.zai.vmccues.motion

import org.junit.Assert.*
import org.junit.Test

class VehicleFrameTest {

    // --- smoothDeadzone tests ---

    @Test
    fun `smoothDeadzone returns zero for zero input`() {
        assertEquals(0f, VehicleFrame.smoothDeadzone(0f, 0.25f), 1e-6f)
    }

    @Test
    fun `smoothDeadzone returns small value below deadzone`() {
        // smoothDeadzone ramps from 0 to full at deadzone boundary, so values
        // between 0 and deadzone produce a small but non-zero output.
        val result = VehicleFrame.smoothDeadzone(0.1f, 0.25f)
        assertTrue("Should be small but positive", result > 0f && result < 0.1f)
    }

    @Test
    fun `smoothDeadzone returns full value above deadzone`() {
        val input = 0.5f
        assertEquals(input, VehicleFrame.smoothDeadzone(input, 0.25f), 1e-6f)
    }

    @Test
    fun `smoothDeadzone returns negative full value below negative deadzone`() {
        val input = -0.5f
        assertEquals(input, VehicleFrame.smoothDeadzone(input, 0.25f), 1e-6f)
    }

    @Test
    fun `smoothDeadzone returns small negative value below deadzone`() {
        val result = VehicleFrame.smoothDeadzone(-0.1f, 0.25f)
        assertTrue("Should be small but negative", result < 0f && result > -0.1f)
    }

    @Test
    fun `smoothDeadzone ramps smoothly at boundary`() {
        // At exactly the deadzone boundary, output should equal input
        val dz = 0.25f
        assertEquals(dz, VehicleFrame.smoothDeadzone(dz, dz), 1e-6f)
    }

    @Test
    fun `smoothDeadzone at half deadzone is about 37 percent of input`() {
        val dz = 0.25f
        val halfDz = dz / 2f
        val result = VehicleFrame.smoothDeadzone(halfDz, dz)
        // smoothstep(t=0.5) = 0.5^2 * (3 - 2*0.5) = 0.25 * 2 = 0.5
        // so result = sign * smooth * abs = 0.5 * 0.125 = 0.0625
        val expected = 0.5f * halfDz
        assertEquals(expected, result, 1e-4f)
    }

    @Test
    fun `smoothDeadzone with zero deadzone returns value unchanged`() {
        assertEquals(0.5f, VehicleFrame.smoothDeadzone(0.5f, 0f), 1e-6f)
        assertEquals(-0.3f, VehicleFrame.smoothDeadzone(-0.3f, 0f), 1e-6f)
    }

    @Test
    fun `smoothDeadzone is continuous at zero`() {
        val dz = 0.25f
        val nearZero = 0.001f
        val result = VehicleFrame.smoothDeadzone(nearZero, dz)
        assertTrue("Result should be small but positive", result > 0f)
        assertTrue("Result should be less than input", result < nearZero)
    }

    @Test
    fun `smoothDeadzone preserves sign`() {
        val dz = 0.25f
        assertTrue(VehicleFrame.smoothDeadzone(0.3f, dz) > 0f)
        assertTrue(VehicleFrame.smoothDeadzone(-0.3f, dz) < 0f)
    }

    // --- forceToDisplacement tests ---

    @Test
    fun `forceToDisplacement returns zero below deadzone`() {
        assertEquals(0f, VehicleFrame.forceToDisplacement(0.1f, 1.0f, 0.25f), 1e-6f)
    }

    @Test
    fun `forceToDisplacement returns non-zero above deadzone`() {
        val result = VehicleFrame.forceToDisplacement(1.0f, 1.0f, 0.25f)
        assertTrue(result > 0f)
    }

    @Test
    fun `forceToDisplacement clamps to maxPx`() {
        val result = VehicleFrame.forceToDisplacement(100f, 1.0f, 0.25f, 120f)
        assertEquals(120f, result, 1e-6f)
    }

    @Test
    fun `forceToDisplacement clamps negative to -maxPx`() {
        val result = VehicleFrame.forceToDisplacement(-100f, 1.0f, 0.25f, 120f)
        assertEquals(-120f, result, 1e-6f)
    }

    @Test
    fun `forceToDisplacement scales with sensitivity`() {
        val low = VehicleFrame.forceToDisplacement(1.0f, 0.5f, 0.25f)
        val high = VehicleFrame.forceToDisplacement(1.0f, 2.0f, 0.25f)
        assertTrue(high > low)
    }

    @Test
    fun `forceToDisplacement is linear above deadzone`() {
        val f1 = VehicleFrame.forceToDisplacement(1.0f, 1.0f, 0.25f)
        val f2 = VehicleFrame.forceToDisplacement(2.0f, 1.0f, 0.25f)
        val f3 = VehicleFrame.forceToDisplacement(3.0f, 1.0f, 0.25f)
        // f2 - f1 should equal f3 - f2 (linear)
        assertEquals(f2 - f1, f3 - f2, 1e-4f)
    }

    @Test
    fun `forceToDisplacement handles zero force`() {
        assertEquals(0f, VehicleFrame.forceToDisplacement(0f, 1.0f), 1e-6f)
    }

    @Test
    fun `forceToDisplacement handles negative force`() {
        val result = VehicleFrame.forceToDisplacement(-1.0f, 1.0f, 0.25f)
        assertTrue(result < 0f)
    }

    @Test
    fun `forceToDisplacement symmetry`() {
        val pos = VehicleFrame.forceToDisplacement(1.0f, 1.0f, 0.25f)
        val neg = VehicleFrame.forceToDisplacement(-1.0f, 1.0f, 0.25f)
        assertEquals(pos, -neg, 1e-6f)
    }

    // --- forceToIntensity tests ---

    @Test
    fun `forceToIntensity of zero force is zero`() {
        assertEquals(0f, VehicleFrame.forceToIntensity(ForceVector.ZERO), 1e-6f)
    }

    @Test
    fun `forceToIntensity is clamped to one`() {
        val huge = ForceVector(100f, 100f)
        assertEquals(1f, VehicleFrame.forceToIntensity(huge), 1e-6f)
    }

    @Test
    fun `forceToIntensity increases with magnitude`() {
        val small = ForceVector(0.5f, 0f)
        val large = ForceVector(2.0f, 0f)
        assertTrue(VehicleFrame.forceToIntensity(large) > VehicleFrame.forceToIntensity(small))
    }

    @Test
    fun `forceToIntensity of 2_5 magnitude is one`() {
        val v = ForceVector(2.5f, 0f)
        assertEquals(1f, VehicleFrame.forceToIntensity(v), 1e-6f)
    }

    @Test
    fun `forceToIntensity of 1_25 magnitude is 0_5`() {
        val v = ForceVector(1.25f, 0f)
        assertEquals(0.5f, VehicleFrame.forceToIntensity(v), 1e-6f)
    }

    @Test
    fun `forceToIntensity is non-negative`() {
        val v = ForceVector(-5f, -5f)
        assertTrue(VehicleFrame.forceToIntensity(v) >= 0f)
    }
}
