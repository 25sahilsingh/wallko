package com.example.wallko.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class UnitsFormatterTest {
    private val us = Locale.US

    @Test fun storedValues() {
        assertEquals(UnitSystem.METRIC, UnitSystem.fromStored("METRIC"))
        assertEquals(UnitSystem.IMPERIAL, UnitSystem.fromStored("IMPERIAL"))
        assertEquals(UnitSystem.METRIC, UnitSystem.fromStored(null))
        assertEquals(UnitSystem.METRIC, UnitSystem.fromStored("furlongs"))
    }

    @Test fun metricDistance() {
        val m = UnitSystem.METRIC
        assertEquals("0 m", UnitsFormatter.distance(0.0, m, us))
        assertEquals("0 m", UnitsFormatter.distance(-3.0, m, us))
        assertEquals("0 m", UnitsFormatter.distance(Double.NaN, m, us))
        assertEquals("999 m", UnitsFormatter.distance(999.9, m, us))
        assertEquals("1.00 km", UnitsFormatter.distance(1_000.0, m, us))
        assertEquals("1.24 km", UnitsFormatter.distance(1_240.0, m, us))
        assertEquals("10.0 km", UnitsFormatter.distance(9_999.0, m, us))
        assertEquals("12.3 km", UnitsFormatter.distance(12_345.0, m, us))
    }

    @Test fun imperialDistance() {
        val i = UnitSystem.IMPERIAL
        assertEquals("0 ft", UnitsFormatter.distance(0.0, i, us))
        assertEquals("328 ft", UnitsFormatter.distance(100.0, i, us))
        assertEquals("999 ft", UnitsFormatter.distance(304.7, i, us))
        assertEquals("0.19 mi", UnitsFormatter.distance(304.8, i, us))
        assertEquals("1.00 mi", UnitsFormatter.distance(1_609.344, i, us))
        assertEquals("6.21 mi", UnitsFormatter.distance(10_000.0, i, us))
        assertEquals("12.4 mi", UnitsFormatter.distance(20_000.0, i, us))
    }

    @Test fun localeDecimalSeparator() {
        assertEquals("1,24 km", UnitsFormatter.distance(1_240.0, UnitSystem.METRIC, Locale.GERMANY))
    }

    @Test fun shortLengthAndLabels() {
        assertEquals("100 m", UnitsFormatter.shortLength(100.0, UnitSystem.METRIC))
        assertEquals("328 ft", UnitsFormatter.shortLength(100.0, UnitSystem.IMPERIAL))
        assertEquals("km", UnitsFormatter.longUnit(UnitSystem.METRIC))
        assertEquals("mi", UnitsFormatter.longUnit(UnitSystem.IMPERIAL))
    }
}
