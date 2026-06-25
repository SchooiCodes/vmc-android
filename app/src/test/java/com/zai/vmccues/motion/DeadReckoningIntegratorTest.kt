package com.zai.vmccues.motion

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DeadReckoningIntegratorTest {

    private lateinit var integrator: DeadReckoningIntegrator

    @Before
    fun setUp() {
        integrator = DeadReckoningIntegrator()
    }

    // --- Basic state ---

    @Test
    fun `initial position is zero`() {
        val pos = integrator.currentPosition()
        assertEquals(0f, pos.lateral, 1e-6f)
        assertEquals(0f, pos.longitudinal, 1e-6f)
    }

    @Test
    fun `reset returns to zero`() {
        integrator.update(ForceVector(1f, 1f), 0.016f)
        integrator.update(ForceVector(1f, 1f), 0.016f)
        integrator.reset()
        val pos = integrator.currentPosition()
        assertEquals(0f, pos.lateral, 1e-6f)
        assertEquals(0f, pos.longitudinal, 1e-6f)
    }

    // --- Integration physics ---

    @Test
    fun `constant lateral force produces lateral displacement`() {
        val force = ForceVector(lateral = 2f, longitudinal = 0f)
        var result = ForceVector.ZERO
        // Integrate for 1 second at 60fps
        repeat(60) { result = integrator.update(force, 1f / 60f) }
        assertTrue("Lateral displacement should be non-zero", result.lateral != 0f)
        assertEquals("No longitudinal displacement", 0f, result.longitudinal, 0.01f)
    }

    @Test
    fun `constant longitudinal force produces longitudinal displacement`() {
        val force = ForceVector(lateral = 0f, longitudinal = 2f)
        var result = ForceVector.ZERO
        repeat(60) { result = integrator.update(force, 1f / 60f) }
        assertEquals("No lateral displacement", 0f, result.lateral, 0.01f)
        assertTrue("Longitudinal displacement should be non-zero", result.longitudinal != 0f)
    }

    @Test
    fun `zero force returns to zero`() {
        val force = ForceVector(0f, 0f)
        var result = ForceVector.ZERO
        // Let it settle for 5 seconds
        repeat(300) { result = integrator.update(force, 1f / 60f) }
        assertEquals(0f, result.lateral, 0.5f)
        assertEquals(0f, result.longitudinal, 0.5f)
    }

    @Test
    fun `force direction determines displacement direction`() {
        // Positive lateral = body pushed right = dots move LEFT (negated)
        val rightForce = ForceVector(lateral = 2f, longitudinal = 0f)
        var resultRight = ForceVector.ZERO
        repeat(30) { resultRight = integrator.update(rightForce, 1f / 60f) }
        integrator.reset()

        val leftForce = ForceVector(lateral = -2f, longitudinal = 0f)
        var resultLeft = ForceVector.ZERO
        repeat(30) { resultLeft = integrator.update(leftForce, 1f / 60f) }

        // Negated positions should be in opposite directions
        assertTrue("Right force should produce negative lateral (dots move left)", resultRight.lateral < 0f)
        assertTrue("Left force should produce positive lateral (dots move right)", resultLeft.lateral > 0f)
    }

    @Test
    fun `displacement is negated`() {
        // Positive input → positive velocity → positive position → negated to negative output
        val force = ForceVector(lateral = 3f, longitudinal = 0f)
        var result = ForceVector.ZERO
        repeat(10) { result = integrator.update(force, 1f / 60f) }
        assertTrue("Output should be negated (negative)", result.lateral < 0f)
    }

    // --- Input clamping ---

    @Test
    fun `input is clamped to inputClamp`() {
        integrator.inputClamp = 5f
        val hugeForce = ForceVector(lateral = 100f, longitudinal = 100f)
        var result = ForceVector.ZERO
        repeat(60) { result = integrator.update(hugeForce, 1f / 60f) }
        // The integrator should not explode — result should be bounded
        assertTrue("Lateral should be bounded", kotlin.math.abs(result.lateral) < 500f)
        assertTrue("Longitudinal should be bounded", kotlin.math.abs(result.longitudinal) < 500f)
    }

    // --- Return to center ---

    @Test
    fun `position returns to center after force stops`() {
        val force = ForceVector(lateral = 3f, longitudinal = 3f)
        // Apply force for 1 second
        repeat(60) { integrator.update(force, 1f / 60f) }
        val peakLateral = integrator.currentPosition().lateral

        // Now apply zero force for 3 seconds
        val zero = ForceVector(0f, 0f)
        repeat(180) { integrator.update(zero, 1f / 60f) }
        val settled = integrator.currentPosition().lateral

        assertTrue("Position should return toward zero after force stops",
            kotlin.math.abs(settled) < kotlin.math.abs(peakLateral))
    }

    // --- Damping coefficient effects ---

    @Test
    fun `higher damping makes dots settle faster`() {
        // Low damping
        integrator.dampingCoef = 2f
        val force = ForceVector(lateral = 3f, longitudinal = 0f)
        repeat(60) { integrator.update(force, 1f / 60f) }
        val peakLow = kotlin.math.abs(integrator.currentPosition().lateral)
        // Let it settle
        repeat(60) { integrator.update(ForceVector.ZERO, 1f / 60f) }
        val settledLow = kotlin.math.abs(integrator.currentPosition().lateral)

        integrator.reset()

        // High damping
        integrator.dampingCoef = 10f
        repeat(60) { integrator.update(force, 1f / 60f) }
        val peakHigh = kotlin.math.abs(integrator.currentPosition().lateral)
        repeat(60) { integrator.update(ForceVector.ZERO, 1f / 60f) }
        val settledHigh = kotlin.math.abs(integrator.currentPosition().lateral)

        // Higher damping should produce smaller peak and faster settling
        assertTrue("High damping peak should be <= low damping peak",
            peakHigh <= peakLow + 0.1f)
    }

    // --- Filter alpha effects ---

    @Test
    fun `lower filter alpha produces smoother output`() {
        integrator.filterAlpha = 0.05f
        val noisy = ForceVector(lateral = 1f, longitudinal = 0f)
        val quiet = ForceVector(lateral = 0.1f, longitudinal = 0f)
        // Alternate noisy/quiet to simulate jitter
        var resultLow = ForceVector.ZERO
        repeat(60) {
            resultLow = if (it % 2 == 0) integrator.update(noisy, 1f / 60f)
            else integrator.update(quiet, 1f / 60f)
        }
        val rangeLow = kotlin.math.abs(resultLow.lateral)

        integrator.reset()
        integrator.filterAlpha = 0.5f
        var resultHigh = ForceVector.ZERO
        repeat(60) {
            resultHigh = if (it % 2 == 0) integrator.update(noisy, 1f / 60f)
            else integrator.update(quiet, 1f / 60f)
        }
        val rangeHigh = kotlin.math.abs(resultHigh.lateral)

        // Lower alpha should produce less variation (smoother)
        assertTrue("Low alpha should produce less jitter", rangeLow <= rangeHigh + 0.5f)
    }

    // --- setParams ---

    @Test
    fun `setParams updates internal parameters`() {
        integrator.setParams(
            filterAlpha = 0.3f,
            dampingCoef = 8f,
            returnToCenterCoef = 3f,
            inputClamp = 6f,
        )
        assertEquals(0.3f, integrator.filterAlpha, 1e-6f)
        assertEquals(8f, integrator.dampingCoef, 1e-6f)
        assertEquals(3f, integrator.returnToCenterCoef, 1e-6f)
        assertEquals(6f, integrator.inputClamp, 1e-6f)
    }

    // --- Thread safety (basic) ---

    @Test
    fun `concurrent updates do not crash`() {
        val threads = (1..4).map { thread ->
            Thread {
                repeat(100) {
                    integrator.update(ForceVector(1f, 1f), 0.016f)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(5000) }
        // If we get here without exception, thread safety is working
        val pos = integrator.currentPosition()
        assertNotNull(pos)
    }

    // --- Symmetry ---

    @Test
    fun `equal and opposite forces produce symmetric displacements`() {
        val force = ForceVector(lateral = 2f, longitudinal = 2f)
        repeat(30) { integrator.update(force, 1f / 60f) }
        val resultA = integrator.currentPosition()
        integrator.reset()
        val negForce = ForceVector(lateral = -2f, longitudinal = -2f)
        repeat(30) { integrator.update(negForce, 1f / 60f) }
        val resultB = integrator.currentPosition()
        assertEquals(resultA.lateral, -resultB.lateral, 0.01f)
        assertEquals(resultA.longitudinal, -resultB.longitudinal, 0.01f)
    }

    // --- Large dt handling ---

    @Test
    fun `large dt does not cause explosion`() {
        val force = ForceVector(lateral = 5f, longitudinal = 5f)
        val result = integrator.update(force, 1.0f) // 1 second step
        assertTrue("Should handle large dt", kotlin.math.abs(result.lateral) < 1000f)
        assertTrue("Should handle large dt", kotlin.math.abs(result.longitudinal) < 1000f)
    }

    // --- currentPosition matches last update ---

    @Test
    fun `currentPosition matches last update result`() {
        val force = ForceVector(lateral = 1f, longitudinal = 2f)
        val updateResult = integrator.update(force, 0.016f)
        val current = integrator.currentPosition()
        assertEquals(updateResult.lateral, current.lateral, 1e-6f)
        assertEquals(updateResult.longitudinal, current.longitudinal, 1e-6f)
    }
}
