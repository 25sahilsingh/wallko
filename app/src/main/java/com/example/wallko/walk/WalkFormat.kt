package com.example.wallko.walk

import com.example.wallko.ui.settings.UnitSystem
import com.example.wallko.ui.settings.UnitsFormatter
import java.text.NumberFormat
import java.time.Duration
import java.util.Locale

/** Pure text formatting for the walk notification and screen. */
object WalkFormat {

    /** "850 m" / "1.24 km" / "12.3 km", or "820 ft" / "1.24 mi" in [UnitSystem.IMPERIAL]; see [UnitsFormatter.distance]. */
    fun distance(meters: Double, units: UnitSystem, locale: Locale = Locale.getDefault()): String =
        UnitsFormatter.distance(meters, units, locale)

    /** "0:07", "12:34", "1:02:03". Negative durations show as 0:00. */
    fun elapsed(duration: Duration): String {
        val total = duration.seconds.coerceAtLeast(0)
        val h = total / 3_600
        val m = (total % 3_600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%d:%02d".format(Locale.ROOT, m, s)
    }

    /** Grouped integer, e.g. "12,345" in en-US. */
    fun count(n: Int, locale: Locale = Locale.getDefault()): String = NumberFormat.getIntegerInstance(locale).format(n)

    /** Ongoing-notification body: "1.24 km · 12 drops · 1,530 steps". Steps are left out when the sensor is off. */
    fun notificationText(
        distanceM: Double,
        drops: Int,
        steps: Int?,
        units: UnitSystem,
        locale: Locale = Locale.getDefault(),
    ): String {
        val parts = mutableListOf(distance(distanceM, units, locale), plural(drops, "drop", "drops", locale))
        if (steps != null) parts += plural(steps, "step", "steps", locale)
        return parts.joinToString(" · ")
    }

    /** Ongoing-notification title. */
    fun notificationTitle(dawn: Boolean, counting: Boolean): String = when {
        !counting -> "Walking with Drip (warming up)"
        dawn -> "Early-bird walk ×2"
        else -> "Walking with Drip"
    }

    private fun plural(n: Int, one: String, many: String, locale: Locale): String =
        "${count(n, locale)} ${if (n == 1) one else many}"
}
