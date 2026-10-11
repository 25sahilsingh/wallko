package com.example.wallko.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.example.wallko.R

/** The two notification channels (minSdk 29, so channels always exist). Creating them again is a no-op. */
object NotificationChannels {
    const val WALK_REMINDERS = "walk_reminders"
    const val PLANT_ALERTS = "plant_alerts"

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    WALK_REMINDERS,
                    context.getString(R.string.notif_channel_walk_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.notif_channel_walk_description) },
                NotificationChannel(
                    PLANT_ALERTS,
                    context.getString(R.string.notif_channel_plants_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.notif_channel_plants_description) },
            ),
        )
    }
}
