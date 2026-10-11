package com.example.wallko.core.config

import java.time.Duration
import java.time.LocalTime

/**
 * Every tunable number in Wallko. Nothing else in the codebase may hard-code these.
 *
 * Sources: `docs/game-rules.md` (gameplay), `docs/ml-pipeline.md` (ML), `docs/risks.md` (GPS hygiene).
 */
object GameRules {

    /** Starting state. See game-rules.md § Numbers. */
    object Start {
        const val TANK_CAPACITY = 50
        const val STARTER_DROPS = 20
        const val FREE_SCANS = 1
    }

    /** Drop income. See game-rules.md § Earning. */
    object Earning {
        /** Passive steps: [DROPS_PER_STEP_BLOCK] drops per [STEPS_PER_BLOCK] steps. */
        const val STEPS_PER_BLOCK = 100
        const val DROPS_PER_STEP_BLOCK = 1
        const val DAILY_STEP_CAP = 15_000

        /** GPS walk distance: [DROPS_PER_DISTANCE_BLOCK] drops per [METERS_PER_BLOCK] m. Replaces steps for that walk. */
        const val METERS_PER_BLOCK = 100.0
        const val DROPS_PER_DISTANCE_BLOCK = 2

        /** First-ever visit to a cell. */
        const val DROPS_PER_NEW_CELL = 3

        /** Dawn window [DAWN_START] (inclusive) to [DAWN_END] (exclusive), local time; multiplies walk drops. */
        val DAWN_START: LocalTime = LocalTime.of(4, 0)
        val DAWN_END: LocalTime = LocalTime.of(7, 0)
        const val DAWN_MULTIPLIER = 2

        /** One-time bonus when a Rare-or-better card is found. */
        const val RARE_FIND_BONUS = 10
    }

    /** See game-rules.md § Elixir. */
    object Elixir {
        /** Meters walked inside never-visited cells per elixir. Partial progress persists. */
        const val METERS_IN_NEW_CELLS_PER_ELIXIR = 500.0
        const val REVIVE_COST = 1
    }

    /** See game-rules.md § Drinking. */
    object Drinking {
        val THIRST_RANGE: IntRange = 1..5
        /** Per distinct species ever owned (see [tankCapacityFor]). */
        const val TANK_CAPACITY_PER_PLANT = 10
        const val MAX_TANK_CAPACITY = 500
    }

    /** See game-rules.md § Wilt and wither. Counted in consecutive unwatered days. */
    object Health {
        const val WILT_AFTER_DAYS = 3
        const val WITHER_AFTER_DAYS = 7
    }

    /** Watered days needed to reach GROWN. See game-rules.md § Growth. */
    object Growth {
        const val FAST_DAYS = 3
        const val MEDIUM_DAYS = 7
        const val SLOW_DAYS = 14

        /** Watered days that still show as SEEDLING (design-system.md: "Seedling (day 0–1)"). */
        const val SEEDLING_MAX_GROWTH_DAYS = 1
    }

    /**
     * Upper percentile bounds (exclusive) of observation-count rank for each tier, ascending.
     * Common [0,40), Uncommon [40,70), Rare [70,90), Epic [90,98), Legendary [98,100].
     * See game-rules.md § Rarity tiers.
     */
    object Rarity {
        const val COMMON_MAX_PERCENTILE = 40
        const val UNCOMMON_MAX_PERCENTILE = 70
        const val RARE_MAX_PERCENTILE = 90
        const val EPIC_MAX_PERCENTILE = 98
    }

    /** Walk validity and GPS hygiene. See game-rules.md § Anti-silliness and risks.md. */
    object Walk {
        /** GPS points faster than this are ignored (driving). */
        const val MAX_SPEED_KMH = 12.0
        val MIN_DURATION: Duration = Duration.ofMinutes(2)
        const val MIN_DISTANCE_METERS = 100.0

        /** Points with reported accuracy worse than this are ignored. */
        const val MAX_ACCURACY_METERS = 30.0

        /** Consecutive accepted points required inside a cell before it counts as visited. */
        const val CONSECUTIVE_POINTS_PER_CELL = 2

        val LOCATION_INTERVAL: Duration = Duration.ofSeconds(5)
        const val LOCATION_MIN_DISTANCE_METERS = 10f

        /** The walk service stops itself after this long. */
        val MAX_DURATION: Duration = Duration.ofHours(4)

        /** Edge length of an exploration cell. See core/geo/Cells.kt. */
        const val CELL_SIZE_METERS = 100.0
    }

    /** See ml-pipeline.md. */
    object Ml {
        const val CLASSIFIER_INPUT_SIZE = 224
        const val SPRITE_SIZE = 384
        const val MIN_TOP1_CONFIDENCE = 0.35f
        const val MIN_TOP1_MARGIN = 0.10f
        /** Background ("not a plant") score at or above this → no guesses at all. Rooms/walls score 0.6–0.8. */
        const val MAX_BACKGROUND_SCORE = 0.30f
        /**
         * Each guess must score at least this to be shown at all, so the player can't pick a weak
         * (often rare) species. Measured: tulsi (not in AIY) → wrong guess at 0.11; a real match → 0.96.
         * No guess left → "No plant found".
         */
        const val MIN_GUESS_SCORE = 0.30f
        /** BioCLIP image side (CLIP: short side resized to this, then centre-cropped square). */
        const val BIOCLIP_INPUT_SIZE = 224
        /** BioCLIP score = softmax(this × dot(image, text)); CLIP's learned logit scale. */
        const val BIOCLIP_LOGIT_SCALE = 100f
        /** ONNX Runtime intra-op threads for the BioCLIP encoder. */
        const val BIOCLIP_THREADS = 4
        /** How much of the art style to mix into the photo's own style (1 = pure style, which washes plants pink). */
        const val STYLE_STRENGTH = 0.4f
    }

    /** One game day; the unit for drinking, wilting and growth. */
    val DAY: Duration = Duration.ofDays(1)

    // ---- Formula helpers ----

    /**
     * Tank capacity: 50 + 10 per **distinct species ever owned** (withered included), capped at 500.
     * Pass a count of distinct species, not of plant rows: re-planting a withered species adds nothing.
     */
    fun tankCapacityFor(distinctSpeciesOwned: Int): Int =
        (Start.TANK_CAPACITY + Drinking.TANK_CAPACITY_PER_PLANT * distinctSpeciesOwned.coerceAtLeast(0))
            .coerceAtMost(Drinking.MAX_TANK_CAPACITY)

    /** Drops for a day's passive steps: 1 per full 100 steps, steps capped at 15,000. */
    fun dropsForSteps(steps: Int): Int =
        steps.coerceIn(0, Earning.DAILY_STEP_CAP) / Earning.STEPS_PER_BLOCK * Earning.DROPS_PER_STEP_BLOCK

    /** Drops for walk distance: 2 per full 100 m (before any dawn multiplier). */
    fun dropsForDistance(meters: Double): Int =
        (meters.coerceAtLeast(0.0) / Earning.METERS_PER_BLOCK).toInt() * Earning.DROPS_PER_DISTANCE_BLOCK

    /** True if [time] falls in the dawn window [04:00, 07:00). */
    fun isDawn(time: LocalTime): Boolean = !time.isBefore(Earning.DAWN_START) && time.isBefore(Earning.DAWN_END)

    /** Confidence rule: top-1 ≥ 0.35 and top-1 − top-2 ≥ 0.10 (tiny epsilon absorbs float rounding). */
    fun isConfident(top1: Float, top2: Float): Boolean =
        top1 >= Ml.MIN_TOP1_CONFIDENCE - EPS && top1 - top2 >= Ml.MIN_TOP1_MARGIN - EPS

    private const val EPS = 1e-6f

    /** True if a walk is long enough, in both time and distance, to count. */
    fun isValidWalk(duration: Duration, meters: Double): Boolean =
        duration >= Walk.MIN_DURATION && meters >= Walk.MIN_DISTANCE_METERS
}
