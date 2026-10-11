package com.example.wallko.notifications

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.wallko.core.prefs.SettingsStore
import com.example.wallko.core.time.Clock
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Posts the morning walk nudge, then re-enqueues itself for the next day's reminder time. */
@HiltWorker
class MorningNudgeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifier: ReminderNotifier,
    private val scheduler: MorningNudgeScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            notifier.postMorningNudge()
        } catch (e: Exception) {
            Log.w("MorningNudgeWorker", "nudge failed", e)
        }
        // APPEND_OR_REPLACE: the next run waits for this one to finish instead of cancelling it. Always succeed
        // so the appended request is not failed along with this one.
        scheduler.enqueueNext(ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}

/**
 * Keeps exactly one pending [MorningNudgeWorker] (unique work [WORK_NAME]) due at the SettingsStore reminder time.
 * The nudge always stays scheduled; whether it posts is decided when it runs (switch, first plant, permission).
 */
@Singleton
class MorningNudgeScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    /** Makes sure a nudge is pending; leaves an existing one alone. Called by the daily worker. */
    suspend fun ensureScheduled() = enqueueNext(ExistingWorkPolicy.KEEP)

    /** Call after the reminder time (or switch) changes in Settings. */
    suspend fun reschedule() = enqueueNext(ExistingWorkPolicy.REPLACE)

    internal suspend fun enqueueNext(policy: ExistingWorkPolicy) {
        val minute = settings.reminderMinuteOfDay.first()
        val delay = delayUntilNext(clock.now(), clock.zone(), minute)
        val request = OneTimeWorkRequestBuilder<MorningNudgeWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }

    companion object {
        const val WORK_NAME = "morning_nudge"

        /** Time from [now] until the next occurrence of [minuteOfDay] local time (strictly in the future). */
        fun delayUntilNext(now: Instant, zone: ZoneId, minuteOfDay: Int): Duration {
            val m = minuteOfDay.coerceIn(0, 24 * 60 - 1)
            val local = now.atZone(zone)
            var target = local.toLocalDate().atTime(LocalTime.of(m / 60, m % 60)).atZone(zone)
            if (!target.isAfter(local)) {
                target = local.toLocalDate().plusDays(1).atTime(LocalTime.of(m / 60, m % 60)).atZone(zone)
            }
            return Duration.between(now, target.toInstant())
        }
    }
}
