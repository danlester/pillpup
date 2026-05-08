package com.ideonate.pillpup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                try {
                    ReminderScheduler.rescheduleAll(context)
                    Engine.checkAndNotify(context)
                    val reviewActions = setOf(
                        Intent.ACTION_TIMEZONE_CHANGED,
                        Intent.ACTION_BOOT_COMPLETED,
                        Intent.ACTION_LOCKED_BOOT_COMPLETED,
                    )
                    if (intent.action in reviewActions) {
                        val meds = MedStore(context).list()
                        val backlog = HistoryStore(context).computeBacklog(meds, Days.today())
                        Notifications.postBacklogReview(context, backlog.count)
                    }
                } catch (e: org.json.JSONException) {
                    // DataHealth has fired the corruption notification; don't crash boot.
                    android.util.Log.e("BootReceiver", "skipped ${intent.action}: data corrupt", e)
                }
            }
        }
    }
}
