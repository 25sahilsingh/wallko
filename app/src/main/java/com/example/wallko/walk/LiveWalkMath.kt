package com.example.wallko.walk

import com.example.wallko.core.config.GameRules
import java.time.Duration

/** Pure numbers shown while a walk is still running. Final rewards always come from [WalkRewards.summarize]. */
object LiveWalkMath {

    /**
     * Drops this walk will pay if stopped now and valid: distance drops + new-cell drops, doubled at dawn.
     * Matches [WalkRewards.summarize]'s total for a valid walk.
     */
    fun projectedDrops(acc: WalkAccumulator, dawn: Boolean): Int {
        val base = GameRules.dropsForDistance(acc.distanceM) + acc.newCells.size * GameRules.Earning.DROPS_PER_NEW_CELL
        return if (dawn) base * GameRules.Earning.DAWN_MULTIPLIER else base
    }

    /** True once the walk is long enough (time and distance) to earn anything. */
    fun isCounting(elapsed: Duration, acc: WalkAccumulator): Boolean = GameRules.isValidWalk(elapsed, acc.distanceM)

    /** Total metres toward elixir: carried-over progress + this walk's new-cell metres. */
    fun elixirMeters(priorProgressM: Double, acc: WalkAccumulator): Double =
        priorProgressM.coerceAtLeast(0.0) + acc.newCellDistanceM

    /** Whole elixirs contained in [elixirMeters]. */
    fun elixirCount(elixirMeters: Double): Int =
        (elixirMeters.coerceAtLeast(0.0) / GameRules.Elixir.METERS_IN_NEW_CELLS_PER_ELIXIR).toInt()

    /** 0..1 progress toward the next elixir. */
    fun elixirFraction(elixirMeters: Double): Float {
        val per = GameRules.Elixir.METERS_IN_NEW_CELLS_PER_ELIXIR
        return ((elixirMeters.coerceAtLeast(0.0) % per) / per).toFloat().coerceIn(0f, 1f)
    }
}
