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
        ReminderScheduler.rescheduleAll(this)
    }

    companion object {
        @Volatile private var started: Int = 0
        @Volatile var isForeground: Boolean = false
            private set
    }
}
