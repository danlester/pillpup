package com.ideonate.pillpup

import android.app.Activity
import android.app.Application
import android.os.Bundle

class PillpupApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {
                started++
                isForeground = true
            }
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {
                started--
                if (started <= 0) {
                    started = 0
                    isForeground = false
                }
            }
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        // Best-effort: re-arm alarms whenever the process spins up.
        // Wrapped because rescheduleAll reads MedStore — if the blob is corrupt
        // we want DataHealth to flag it (and post a notification) rather than
        // crash the process before MainActivity can show the error UI.
        try {
            ReminderScheduler.rescheduleAll(this)
        } catch (e: org.json.JSONException) {
            // DataHealth.markCorrupt has already fired the notification.
            android.util.Log.e("PillpupApp", "rescheduleAll skipped: data corrupt", e)
        }
    }

    companion object {
        @Volatile private var started: Int = 0
        @Volatile var isForeground: Boolean = false
            private set
    }
}
