package com.ideonate.pillpup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * AlarmManager glue. Uses setWindow with a 5-minute flex window (no
 * SCHEDULE_EXACT_ALARM permission needed). One-shots are re-armed by the
 * receiver after they fire.
 *
 * Slots:
 *   - per-med scheduled alarm (request code = medId.hashCode())
 *   - global "check" alarm     (REQ_CHECK)    : snooze expiry & foreground re-poll
 *   - global "midnight" alarm  (REQ_MIDNIGHT) : day rollover
 */
object ReminderScheduler {
    const val WINDOW_MS = 5 * 60_000L
    const val SNOOZE_SHORT_MS = 15 * 60_000L
    const val SNOOZE_LONG_MS = 60 * 60_000L
    const val FOREGROUND_REPOLL_MS = 5 * 60_000L

    private const val REQ_CHECK = 1
    private const val REQ_MIDNIGHT = 2

    fun rescheduleAll(context: Context) {
        val app = context.applicationContext
        for (med in MedStore(app).list()) {
            scheduleNextFor(app, med)
        }
        scheduleMidnightRoll(app)
    }

    fun scheduleNextFor(
        context: Context,
        med: Med,
        now: Long = System.currentTimeMillis()
    ) {
        val app = context.applicationContext
        val today = Days.today()
        val todayAt = Days.atTimeOnDay(today, med.hour, med.minute)
        val target = if (todayAt > now) {
            todayAt
        } else {
            Days.atTimeOnDay(Days.shift(today, 1), med.hour, med.minute)
        }
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        am.setWindow(AlarmManager.RTC_WAKEUP, target, WINDOW_MS, medPendingIntent(app, med.id))
    }

    fun cancelMed(context: Context, medId: String) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(medPendingIntent(app, medId))
    }

    fun scheduleSnoozeCheck(context: Context, atMillis: Long) =
        scheduleCheck(context, atMillis)

    fun scheduleForegroundRepoll(context: Context, atMillis: Long) =
        scheduleCheck(context, atMillis)

    // windowMs == 0 silently upgrades to an exact alarm, which needs
    // SCHEDULE_EXACT_ALARM — a permission we deliberately don't request.
    // Always pass WINDOW_MS so AlarmManager treats it as inexact.
    private fun scheduleCheck(context: Context, atMillis: Long) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        am.setWindow(AlarmManager.RTC_WAKEUP, atMillis, WINDOW_MS, checkPendingIntent(app))
    }

    fun cancelCheck(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(checkPendingIntent(app))
    }

    fun scheduleMidnightRoll(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val tomorrow = Days.startOfNextDay(Days.today())
        am.setWindow(AlarmManager.RTC_WAKEUP, tomorrow, WINDOW_MS, midnightPendingIntent(app))
    }

    private fun medPendingIntent(context: Context, medId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_MED_DUE
            putExtra(ReminderReceiver.EXTRA_MED_ID, medId)
            // Action+data make the PI distinct so FLAG_UPDATE_CURRENT swaps cleanly.
            data = android.net.Uri.parse("pillpup://med/$medId")
        }
        return PendingIntent.getBroadcast(
            context, medId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun checkPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_CHECK
        }
        return PendingIntent.getBroadcast(
            context, REQ_CHECK, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun midnightPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_MIDNIGHT
        }
        return PendingIntent.getBroadcast(
            context, REQ_MIDNIGHT, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
