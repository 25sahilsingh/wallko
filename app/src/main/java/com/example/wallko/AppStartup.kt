package com.example.wallko

import android.util.Log
import com.example.wallko.data.plants.PlantRepository
import com.example.wallko.data.wallet.WalletRepository
import com.example.wallko.game.GameClock
import com.example.wallko.walk.StepSync
import com.example.wallko.walk.WalkSession
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything that must happen each time the app comes to the foreground, in dependency order:
 * species exist before plants reference them, the wallet exists before anything credits it,
 * missed days are applied before today's steps are credited.
 */
@Singleton
class AppStartup @Inject constructor(
    private val plants: PlantRepository,
    private val wallet: WalletRepository,
    private val gameClock: GameClock,
    private val stepSync: StepSync,
    private val walkSession: WalkSession,
) {
    suspend fun onForeground() {
        step("seed species") { plants.ensureSeeded() }
        step("init wallet") { wallet.ensureInitialized() }
        step("catch up") { gameClock.catchUp() }
        step("sync steps") { stepSync.sync() }
        step("resume walk") { walkSession.resumeIfInterrupted() }
    }

    // One failing step (e.g. Health Connect missing) must not block the others.
    private inline fun step(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w("AppStartup", "$name failed", e)
        }
    }
}
