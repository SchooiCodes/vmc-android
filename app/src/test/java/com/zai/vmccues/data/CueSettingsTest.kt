package com.zai.vmccues.data

import org.junit.Assert.*
import org.junit.Test

class CueSettingsTest {

    @Test
    fun `default settings have reasonable values`() {
        val s = CueSettings()
        assertEquals(ActivationMode.ON, s.mode)
        assertEquals(DotPattern.REGULAR, s.pattern)
        assertTrue(s.autoContrast)
        assertTrue(s.adaptiveContrast)
        assertFalse(s.largerDots)
        assertFalse(s.moreDots)
        assertFalse(s.safetyAcknowledged)
    }

    @Test
    fun `default sensitivity is in valid range`() {
        val s = CueSettings()
        assertTrue(s.sensitivity >= 0.5f)
        assertTrue(s.sensitivity <= 2.0f)
    }

    @Test
    fun `default filterAlpha is in valid range`() {
        val s = CueSettings()
        assertTrue(s.filterAlpha >= 0.10f)
        assertTrue(s.filterAlpha <= 0.30f)
    }

    @Test
    fun `default dampingCoef is in valid range`() {
        val s = CueSettings()
        assertTrue(s.dampingCoef >= 2.0f)
        assertTrue(s.dampingCoef <= 10.0f)
    }

    @Test
    fun `default returnToCenterCoef is in valid range`() {
        val s = CueSettings()
        assertTrue(s.returnToCenterCoef >= 0.5f)
        assertTrue(s.returnToCenterCoef <= 5.0f)
    }

    @Test
    fun `default inputClamp is in valid range`() {
        val s = CueSettings()
        assertTrue(s.inputClamp >= 3.0f)
        assertTrue(s.inputClamp <= 12.0f)
    }

    @Test
    fun `default deadzone is in valid range`() {
        val s = CueSettings()
        assertTrue(s.deadzone >= 0.05f)
        assertTrue(s.deadzone <= 0.5f)
    }

    @Test
    fun `default dotOpacity is in valid range`() {
        val s = CueSettings()
        assertTrue(s.dotOpacity >= 0.1f)
        assertTrue(s.dotOpacity <= 1.0f)
    }

    @Test
    fun `default dotInsetDp is in valid range`() {
        val s = CueSettings()
        assertTrue(s.dotInsetDp >= 8f)
        assertTrue(s.dotInsetDp <= 40f)
    }

    @Test
    fun `default intensityResponse is in valid range`() {
        val s = CueSettings()
        assertTrue(s.intensityResponse >= 0.3f)
        assertTrue(s.intensityResponse <= 1.0f)
    }

    @Test
    fun `default gateEntryDelayMs is positive`() {
        val s = CueSettings()
        assertTrue(s.gateEntryDelayMs > 0)
    }

    @Test
    fun `default gateExitGraceMs is positive`() {
        val s = CueSettings()
        assertTrue(s.gateExitGraceMs > 0)
    }

    @Test
    fun `data class equality`() {
        val a = CueSettings()
        val b = CueSettings()
        assertEquals(a, b)
    }

    @Test
    fun `data class copy preserves unchanged fields`() {
        val original = CueSettings(sensitivity = 1.5f)
        val modified = original.copy(mode = ActivationMode.OFF)
        assertEquals(ActivationMode.OFF, modified.mode)
        assertEquals(1.5f, modified.sensitivity)
    }

    @Test
    fun `custom settings can be created`() {
        val s = CueSettings(
            mode = ActivationMode.AUTOMATIC,
            pattern = DotPattern.DYNAMIC,
            sensitivity = 1.8f,
            filterAlpha = 0.25f,
            dampingCoef = 7f,
            deadzone = 0.3f,
            largerDots = true,
            moreDots = true,
        )
        assertEquals(ActivationMode.AUTOMATIC, s.mode)
        assertEquals(DotPattern.DYNAMIC, s.pattern)
        assertEquals(1.8f, s.sensitivity)
        assertEquals(0.25f, s.filterAlpha)
        assertEquals(7f, s.dampingCoef)
        assertEquals(0.3f, s.deadzone)
        assertTrue(s.largerDots)
        assertTrue(s.moreDots)
    }

    @Test
    fun `dotColor default is light gray`() {
        val s = CueSettings()
        // 0xFFF5F5F5 = -250815 in signed int
        assertEquals(0xFFF5F5F5.toInt(), s.dotColor)
    }
}
