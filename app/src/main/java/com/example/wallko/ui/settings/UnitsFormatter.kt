package com.example.wallko.ui.settings

import java.util.Locale

/** Distance units chosen in Settings. Persisted by name in SettingsStore.units. */
enum class UnitSystem {
    METRIC,
    IMPERIAL,
    ;

    companion object {
        /** Unknown or missing values fall back to metric. */
        fun fromStored(value: String?): UnitSystem = entries.firstOrNull { it.name == value } ?: METRIC
    }
}

/**
 * Pure distance formatting for both unit systems. Metric matches WalkFormat.distance
 * ("850 m", "1.24 km", "12.3 km"); imperial uses feet below 1,000 ft, then miles.
 * Small values are floored so a short walk is never overstated.
 */
object UnitsFormatter {
    const val METERS_PER_MILE = 1_609.344
    const val METERS_PER_FOOT = 0.3048

    /** Walk/route distance: "850 m" / "1.24 km", or "820 ft" / "1.24 mi". */
    fun distance(meters: Double, units: UnitSystem, locale: Locale = Locale.getDefault()): String {
        val m = sanitize(meters)
        return when (units) {
            UnitSystem.METRIC -> when {
                m < 1_000.0 -> "${m.toInt()} m"
                else -> large(m / 1_000.0, "km", locale)
            }
            UnitSystem.IMPERIAL -> {
                val feet = m / METERS_PER_FOOT
                if (feet < 1_000.0) "${feet.toInt()} ft" else large(m / METERS_PER_MILE, "mi", locale)
            }
        }
    }

    /** Short lengths that never switch to km/mi (e.g. a 100 m map cell): "100 m" / "328 ft". */
    fun shortLength(meters: Double, units: UnitSystem): String {
        val m = sanitize(meters)
        return when (units) {
            UnitSystem.METRIC -> "${Math.round(m)} m"
            UnitSystem.IMPERIAL -> "${Math.round(m / METERS_PER_FOOT)} ft"
        }
    }

    /** Unit label for headers and goals: "km" / "mi". */
    fun longUnit(units: UnitSystem): String = if (units == UnitSystem.METRIC) "km" else "mi"

    private fun sanitize(meters: Double): Double = if (meters.isFinite()) meters.coerceAtLeast(0.0) else 0.0

    /** Two decimals below 10, one above; the switch happens where two decimals would round up to 10.00. */
    private fun large(value: Double, unit: String, locale: Locale): String =
        if (value < 9.995) String.format(locale, "%.2f %s", value, unit)
        else String.format(locale, "%.1f %s", value, unit)
}
