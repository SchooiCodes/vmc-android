package com.zai.vmccues.data

import org.junit.Assert.*
import org.junit.Test

class EnumsTest {

    // --- ActivationMode ---

    @Test
    fun `ActivationMode has all three values`() {
        assertEquals(3, ActivationMode.entries.size)
    }

    @Test
    fun `ActivationMode DEFAULT is ON`() {
        assertEquals(ActivationMode.ON, ActivationMode.DEFAULT)
    }

    @Test
    fun `ActivationMode fromName with valid names`() {
        assertEquals(ActivationMode.OFF, ActivationMode.fromName("OFF"))
        assertEquals(ActivationMode.ON, ActivationMode.fromName("ON"))
        assertEquals(ActivationMode.AUTOMATIC, ActivationMode.fromName("AUTOMATIC"))
    }

    @Test
    fun `ActivationMode fromName with null returns default`() {
        assertEquals(ActivationMode.DEFAULT, ActivationMode.fromName(null))
    }

    @Test
    fun `ActivationMode fromName with invalid name returns default`() {
        assertEquals(ActivationMode.DEFAULT, ActivationMode.fromName("INVALID"))
        assertEquals(ActivationMode.DEFAULT, ActivationMode.fromName(""))
    }

    @Test
    fun `ActivationMode fromName is case-sensitive`() {
        assertEquals(ActivationMode.DEFAULT, ActivationMode.fromName("on"))
        assertEquals(ActivationMode.DEFAULT, ActivationMode.fromName("Off"))
    }

    @Test
    fun `ActivationMode entries are distinct`() {
        val names = ActivationMode.entries.map { it.name }.toSet()
        assertEquals(3, names.size)
    }

    // --- DotPattern ---

    @Test
    fun `DotPattern has two values`() {
        assertEquals(2, DotPattern.entries.size)
    }

    @Test
    fun `DotPattern DEFAULT is REGULAR`() {
        assertEquals(DotPattern.REGULAR, DotPattern.DEFAULT)
    }

    @Test
    fun `DotPattern fromName with valid names`() {
        assertEquals(DotPattern.REGULAR, DotPattern.fromName("REGULAR"))
        assertEquals(DotPattern.DYNAMIC, DotPattern.fromName("DYNAMIC"))
    }

    @Test
    fun `DotPattern fromName with null returns default`() {
        assertEquals(DotPattern.DEFAULT, DotPattern.fromName(null))
    }

    @Test
    fun `DotPattern fromName with invalid name returns default`() {
        assertEquals(DotPattern.DEFAULT, DotPattern.fromName("INVALID"))
        assertEquals(DotPattern.DEFAULT, DotPattern.fromName(""))
    }

    @Test
    fun `DotPattern fromName is case-sensitive`() {
        assertEquals(DotPattern.DEFAULT, DotPattern.fromName("regular"))
        assertEquals(DotPattern.DEFAULT, DotPattern.fromName("Dynamic"))
    }

    @Test
    fun `DotPattern entries are distinct`() {
        val names = DotPattern.entries.map { it.name }.toSet()
        assertEquals(2, names.size)
    }

    // --- DotVisibility ---

    @Test
    fun `DotVisibility has three values`() {
        assertEquals(3, DotVisibility.entries.size)
    }

    @Test
    fun `DotVisibility DEFAULT is STANDARD`() {
        assertEquals(DotVisibility.STANDARD, DotVisibility.DEFAULT)
    }

    @Test
    fun `DotVisibility fromName with valid names`() {
        assertEquals(DotVisibility.STANDARD, DotVisibility.fromName("STANDARD"))
        assertEquals(DotVisibility.LARGER_DOTS, DotVisibility.fromName("LARGER_DOTS"))
        assertEquals(DotVisibility.MORE_DOTS, DotVisibility.fromName("MORE_DOTS"))
    }

    @Test
    fun `DotVisibility fromName with null returns default`() {
        assertEquals(DotVisibility.DEFAULT, DotVisibility.fromName(null))
    }

    @Test
    fun `DotVisibility fromName with invalid name returns default`() {
        assertEquals(DotVisibility.DEFAULT, DotVisibility.fromName("INVALID"))
        assertEquals(DotVisibility.DEFAULT, DotVisibility.fromName(""))
    }

    @Test
    fun `DotVisibility entries are distinct`() {
        val names = DotVisibility.entries.map { it.name }.toSet()
        assertEquals(3, names.size)
    }
}
