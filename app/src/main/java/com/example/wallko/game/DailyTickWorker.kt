package com.example.wallko.game

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.wallko.notifications.MorningNudgeScheduler
import com.example.wallko.notifications.ReminderNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Once a day: catch the garden up to now, post the wilting alert if needed, keep the morning nudge scheduled. */
@HiltWorker
class DailyTickWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val gameClock: GameClock,
    private val notifier: ReminderNotifier,
    private val nudgeScheduler: MorningNudgeScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        gameClock.catchUp()
        notifier.postWiltingAlertIfNeeded()
        nudgeScheduler.ensureScheduled()
        Result.success()
    } catch (e: Exception) {
        Log.w(TAG, "daily tick failed (attempt $runAttemptCount)", e)
        if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
    }

    private companion object {
        const val TAG = "DailyTickWorker"
        const val MAX_RETRIES = 3
    }
}

/** Enqueues the unique 24 h [DailyTickWorker]. Safe to call on every app start (KEEP policy). */
object DailyTickScheduler {
    const val WORK_NAME = "daily_tick"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<DailyTickWorker>(
            24, TimeUnit.HOURS,
            FLEX_HOURS, TimeUnit.HOURS,
        )
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private const val FLEX_HOURS = 3L
}
