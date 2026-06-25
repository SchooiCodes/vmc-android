package com.zai.vmccues.motion

import org.junit.Assert.*
import org.junit.Test

class ForceVectorTest {

    @Test
    fun `ZERO has both components at zero`() {
        assertEquals(0f, ForceVector.ZERO.lateral)
        assertEquals(0f, ForceVector.ZERO.longitudinal)
    }

    @Test
    fun `magnitude of ZERO is zero`() {
        assertEquals(0f, ForceVector.ZERO.magnitude(), 1e-6f)
    }

    @Test
    fun `magnitude of pure lateral force`() {
        val v = ForceVector(lateral = 3f, longitudinal = 0f)
        assertEquals(3f, v.magnitude(), 1e-6f)
    }

    @Test
    fun `magnitude of pure longitudinal force`() {
        val v = ForceVector(lateral = 0f, longitudinal = 4f)
        assertEquals(4f, v.magnitude(), 1e-6f)
    }

    @Test
    fun `magnitude of 3-4-5 triangle`() {
        val v = ForceVector(lateral = 3f, longitudinal = 4f)
        assertEquals(5f, v.magnitude(), 1e-6f)
    }

    @Test
    fun `magnitude of negative components`() {
        val v = ForceVector(lateral = -3f, longitudinal = -4f)
        assertEquals(5f, v.magnitude(), 1e-6f)
    }

    @Test
    fun `magnitude of very small values`() {
        val v = ForceVector(lateral = 1e-7f, longitudinal = 1e-7f)
        assertEquals(0f, v.magnitude(), 1e-5f)
    }

    @Test
    fun `data class equality`() {
        val a = ForceVector(1f, 2f)
        val b = ForceVector(1f, 2f)
        val c = ForceVector(2f, 1f)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `data class copy`() {
        val original = ForceVector(1f, 2f)
        val copied = original.copy(lateral = 5f)
        assertEquals(5f, copied.lateral)
        assertEquals(2f, copied.longitudinal)
    }

    @Test
    fun `data class toString`() {
        val v = ForceVector(1.5f, 2.5f)
        val str = v.toString()
        assertTrue(str.contains("1.5"))
        assertTrue(str.contains("2.5"))
    }

    @Test
    fun `negative magnitude is impossible`() {
        val v = ForceVector(lateral = -10f, longitudinal = -10f)
        assertTrue(v.magnitude() >= 0f)
    }

    @Test
    fun `magnitude of large values`() {
        val v = ForceVector(lateral = 1000f, longitudinal = 1000f)
        assertEquals(Math.sqrt(2_000_000.0).toFloat(), v.magnitude(), 0.1f)
    }
}
