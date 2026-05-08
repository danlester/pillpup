package com.ideonate.pillpup

import android.content.Context

/**
 * Decides when to fire (and when to suppress) a notification, based on:
 *   - which meds are currently due (scheduled time today has passed, no taken/skipped record)
 *   - the global snooze window
 *   - whether the app is in the foreground
 *
 * Stateless apart from ReminderState (persisted) and PillpupApp.isForeground.
 */
object Engine {

    fun checkAndNotify(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val today = Days.today()
        val state = ReminderState(app)

        if (state.dueSinceDay() != today) {
            state.clearAllDueSince()
            state.setDueSinceDay(today)
            state.clearSnooze()
        }

        val due = computeDueMeds(app, today, now, state)
        if (due.isEmpty()) {
            Notifications.cancel(app)
            return
        }

        if (PillpupApp.isForeground) {
            Notifications.cancel(app)
            ReminderScheduler.scheduleForegroundRepoll(
                app,
                now + ReminderScheduler.FOREGROUND_REPOLL_MS
            )
            return
        }

        val snoozeUntil = state.snoozeUntil()
        val snoozeAt = state.snoozeStartedAt()
        val dueSince = state.dueSinceMap()
        val newDuringSnooze = now < snoozeUntil &&
            due.any { (dueSince[it.id] ?: 0L) > snoozeAt }

        if (now >= snoozeUntil || newDuringSnooze) {
            Notifications.post(app, due.size)
            state.clearSnooze()
        }
        // else: snoozed and nothing newly due — the snooze-expiry alarm will trigger us again.
    }

    fun onMedTakenOrSkipped(context: Context, medId: String) {
        val app = context.applicationContext
        ReminderState(app).clearDueSince(medId)
        checkAndNotify(app)
    }

    fun onMedUndone(context: Context, medId: String) {
        val app = context.applicationContext
        ReminderState(app).clearDueSince(medId)
        checkAndNotify(app)
    }

    fun onMedRemoved(context: Context, medId: String) {
        val app = context.applicationContext
        ReminderScheduler.cancelMed(app, medId)
        ReminderState(app).clearDueSince(medId)
        HistoryStore(app).clearMed(medId)
        checkAndNotify(app)
    }

    /**
     * Returns due meds and records dueSince for any newly observed.
     * "Due" = exists today, scheduled time has passed, no taken/skipped record yet.
     */
    private fun computeDueMeds(
        app: Context,
        today: String,
        now: Long,
        state: ReminderState
    ): List<Med> {
        val history = HistoryStore(app).forDay(today)
        val out = ArrayList<Med>()
        for (med in MedStore(app).list()) {
            if (med.createdDay > today) continue
            val scheduled = Days.atTimeOnDay(today, med.hour, med.minute)
            if (scheduled > now) continue
            if (history.containsKey(med.id)) continue
            state.setDueSince(med.id, scheduled.coerceAtMost(now))
            out.add(med)
        }
        return out
    }
}
