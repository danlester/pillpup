package com.ideonate.pillpup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        try {
            handle(app, intent)
        } catch (e: org.json.JSONException) {
            // DataHealth.markCorrupt has fired the user-facing notification.
            // Swallow so the receiver doesn't crash the process.
            android.util.Log.e("ReminderReceiver", "skipped ${intent.action}: data corrupt", e)
        }
    }

    private fun handle(app: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MED_DUE -> {
                val medId = intent.getStringExtra(EXTRA_MED_ID) ?: return
                MedStore(app).byId(medId)?.let {
                    // Re-arm for tomorrow. setWindow is one-shot.
                    ReminderScheduler.scheduleNextFor(app, it)
                }
                Engine.checkAndNotify(app)
            }
            ACTION_CHECK -> Engine.checkAndNotify(app)
            ACTION_SNOOZE -> {
                val now = System.currentTimeMillis()
                val until = now + ReminderScheduler.SNOOZE_MS
                ReminderState(app).setSnooze(now, until)
                Notifications.cancel(app)
                ReminderScheduler.scheduleSnoozeCheck(app, until)
            }
            ACTION_MIDNIGHT -> {
                val state = ReminderState(app)
                state.clearAllDueSince()
                state.setDueSinceDay(Days.today())
                state.clearSnooze()
                Notifications.cancel(app)
                ReminderScheduler.rescheduleAll(app)
                Engine.checkAndNotify(app)
            }
        }
    }

    companion object {
        const val ACTION_MED_DUE = "com.ideonate.pillpup.MED_DUE"
        const val ACTION_CHECK = "com.ideonate.pillpup.CHECK"
        const val ACTION_SNOOZE = "com.ideonate.pillpup.SNOOZE"
        const val ACTION_MIDNIGHT = "com.ideonate.pillpup.MIDNIGHT"
        const val EXTRA_MED_ID = "medId"
    }
}
