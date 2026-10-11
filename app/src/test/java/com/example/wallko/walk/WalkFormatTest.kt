package com.example.wallko.walk

import com.example.wallko.ui.settings.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.util.Locale

class WalkFormatTest {
    private val us = Locale.US
    private val metric = UnitSystem.METRIC
    private val imperial = UnitSystem.IMPERIAL

    @Test fun distance() {
        assertEquals("0 m", WalkFormat.distance(0.0, metric, us))
        assertEquals("0 m", WalkFormat.distance(-5.0, metric, us))
        assertEquals("0 m", WalkFormat.distance(Double.NaN, metric, us))
        assertEquals("999 m", WalkFormat.distance(999.9, metric, us))
        assertEquals("1.00 km", WalkFormat.distance(1_000.0, metric, us))
        assertEquals("1.24 km", WalkFormat.distance(1_240.0, metric, us))
        assertEquals("12.3 km", WalkFormat.distance(12_345.0, metric, us))
    }

    @Test fun distanceUsesLocaleDecimalSeparator() {
        assertEquals("1,24 km", WalkFormat.distance(1_240.0, metric, Locale.GERMANY))
    }

    @Test fun distanceImperial() {
        assertEquals("0 ft", WalkFormat.distance(0.0, imperial, us))
        assertEquals("328 ft", WalkFormat.distance(100.0, imperial, us))
        assertEquals("1.00 mi", WalkFormat.distance(1_609.344, imperial, us))
        assertEquals("12.4 mi", WalkFormat.distance(20_000.0, imperial, us))
    }

    @Test fun elapsed() {
        assertEquals("0:00", WalkFormat.elapsed(Duration.ZERO))
        assertEquals("0:00", WalkFormat.elapsed(Duration.ofSeconds(-3)))
        assertEquals("0:07", WalkFormat.elapsed(Duration.ofSeconds(7)))
        assertEquals("12:34", WalkFormat.elapsed(Duration.ofSeconds(754)))
        assertEquals("1:02:03", WalkFormat.elapsed(Duration.ofSeconds(3_723)))
        assertEquals("4:00:00", WalkFormat.elapsed(Duration.ofHours(4)))
    }

    @Test fun notificationText() {
        assertEquals("1.24 km · 12 drops · 1,530 steps", WalkFormat.notificationText(1_240.0, 12, 1_530, metric, us))
        assertEquals("850 m · 1 drop · 1 step", WalkFormat.notificationText(850.0, 1, 1, metric, us))
        assertEquals("0 m · 0 drops", WalkFormat.notificationText(0.0, 0, null, metric, us))
        assertEquals("1.24 mi · 12 drops · 1,530 steps", WalkFormat.notificationText(1_995.6, 12, 1_530, imperial, us))
    }

    @Test fun notificationTitle() {
        assertEquals("Walking with Drip (warming up)", WalkFormat.notificationTitle(dawn = true, counting = false))
        assertEquals("Early-bird walk ×2", WalkFormat.notificationTitle(dawn = true, counting = true))
        assertEquals("Walking with Drip", WalkFormat.notificationTitle(dawn = false, counting = true))
    }
}
