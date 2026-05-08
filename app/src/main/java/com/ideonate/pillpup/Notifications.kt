package com.ideonate.pillpup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object Notifications {
    private const val CHANNEL_ID = "pillpup_reminders"
    private const val NOTIFICATION_ID = 1
    private const val NOTIFICATION_ID_REVIEW = 2
    private const val NOTIFICATION_ID_CORRUPT = 3

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_reminders),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_reminders_desc)
                // Do NOT call setBypassDnd(true) — we want to respect DND.
            }
            nm.createNotificationChannel(ch)
        }
    }

    fun post(context: Context, dueCount: Int) {
        val app = context.applicationContext
        ensureChannel(app)

        val openIntent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            app, 10, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(app, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_SNOOZE
        }
        val snoozePi = PendingIntent.getBroadcast(
            app, 11, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = app.getString(R.string.notif_title)
        val text = app.resources.getQuantityString(
            R.plurals.notif_text_due, dueCount, dueCount
        )

        val n = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, app.getString(R.string.action_snooze), snoozePi)
            .addAction(0, app.getString(R.string.action_open), openPi)
            .build()

        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, n)
    }

    fun cancel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.cancel(NOTIFICATION_ID)
    }

    /**
     * Posted (once) when DataHealth detects a corrupt SharedPreferences blob.
     * Reuses the reminders channel — same posture as the rest of the app.
     */
    fun postCorruption(context: Context, blob: String, message: String) {
        val app = context.applicationContext
        ensureChannel(app)

        val openIntent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            app, 13, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = app.getString(R.string.notif_corrupt_title)
        val text = app.getString(R.string.notif_corrupt_text, blob)
        val big = app.getString(R.string.notif_corrupt_bigtext, blob, message)

        val n = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setContentIntent(openPi)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID_CORRUPT, n)
    }

    /**
     * One-off "review backlog" notification, fired when the system timezone
     * changes and the user has unresolved past doses. Distinct ID so it
     * coexists with a regular due-now notification.
     */
    fun postBacklogReview(context: Context, count: Int) {
        if (count <= 0) return
        val app = context.applicationContext
        ensureChannel(app)

        val openIntent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            app, 12, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = app.getString(R.string.notif_review_title)
        val text = app.resources.getQuantityString(
            R.plurals.notif_review_text, count, count
        )

        val n = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID_REVIEW, n)
    }
}
