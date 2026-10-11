package com.example.wallko.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.wallko.MainActivity
import com.example.wallko.R
import com.example.wallko.core.db.PlantState
import com.example.wallko.core.db.WallkoDatabase
import com.example.wallko.core.prefs.SettingsStore
import com.example.wallko.game.GameMappers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the morning walk nudge and the wilting alert (decisions.md § Reminders): both obey the reminders
 * switch, and nothing is posted until the first plant exists. Missing POST_NOTIFICATIONS (API 33+) or
 * notifications disabled by the user simply means nothing is posted; this class never throws for that.
 */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: WallkoDatabase,
    private val settings: SettingsStore,
) {
    /** Reminders switch on and at least one plant was ever owned. Does not check the OS permission. */
    suspend fun remindersAllowed(): Boolean =
        settings.remindersEnabled.first() && db.ownedPlantDao().getAll().isNotEmpty()

    /** @return true if the nudge was posted. */
    suspend fun postMorningNudge(): Boolean {
        if (!remindersAllowed()) return false
        val notification = NotificationCompat.Builder(context, NotificationChannels.WALK_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification_drip)
            .setContentTitle(context.getString(R.string.notif_nudge_title))
            .setContentText(context.getString(R.string.notif_nudge_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notif_nudge_text)))
            .setContentIntent(openAppIntent(REQUEST_NUDGE))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        return post(NotificationChannels.WALK_REMINDERS, ID_MORNING_NUDGE, notification)
    }

    /**
     * Posts (or refreshes) the wilting alert if any garden plant is WILTING; cancels a stale alert otherwise.
     * @return true if an alert was posted.
     */
    suspend fun postWiltingAlertIfNeeded(): Boolean {
        val wilting = db.ownedPlantDao().getAlive().filter { it.state == PlantState.WILTING }
        if (wilting.isEmpty()) {
            NotificationManagerCompat.from(context).cancel(ID_WILTING)
            return false
        }
        if (!remindersAllowed()) return false
        val speciesDao = db.plantSpeciesDao()
        val names = wilting.map { p ->
            speciesDao.byIndex(p.speciesIndex)?.let(GameMappers::displayName) ?: context.getString(R.string.app_name)
        }.distinct()
        val count = wilting.size
        val text = context.resources.getQuantityString(R.plurals.notif_wilting_text, count, names.joinToString(", "))
        val notification = NotificationCompat.Builder(context, NotificationChannels.PLANT_ALERTS)
            .setSmallIcon(R.drawable.ic_notification_drip)
            .setContentTitle(context.resources.getQuantityString(R.plurals.notif_wilting_title, count, count))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(REQUEST_WILTING))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        return post(NotificationChannels.PLANT_ALERTS, ID_WILTING, notification)
    }

    /** True if the app may post on [channelId] right now (runtime permission, app-level and channel-level switches). */
    fun canPost(channelId: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(channelId)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    @SuppressLint("MissingPermission") // checked in canPost; SecurityException caught as a last resort.
    private fun post(channelId: String, id: Int, notification: android.app.Notification): Boolean {
        NotificationChannels.ensureCreated(context)
        if (!canPost(channelId)) return false
        return try {
            NotificationManagerCompat.from(context).notify(id, notification)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "notification permission revoked", e)
            false
        }
    }

    private fun openAppIntent(requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val TAG = "ReminderNotifier"
        const val ID_MORNING_NUDGE = 1001
        const val ID_WILTING = 1002
        const val REQUEST_NUDGE = 1
        const val REQUEST_WILTING = 2
    }
}
