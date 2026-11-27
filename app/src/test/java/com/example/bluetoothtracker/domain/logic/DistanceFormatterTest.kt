package com.example.bluetoothtracker.domain.logic

import com.example.bluetoothtracker.domain.model.DistanceUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class DistanceFormatterTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `formats meters and feet accurately`() {
        assertEquals("2.50 meter", DistanceFormatter.format(2.5, DistanceUnit.METER))
        assertEquals("3.28 ft", DistanceFormatter.format(1.0, DistanceUnit.FOOT))
    }
}
