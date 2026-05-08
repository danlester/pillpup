package com.ideonate.pillpup

import android.content.Context
import org.json.JSONException
import org.json.JSONObject

/**
 * Transient state used by the reminder engine:
 *   - snoozeStartedAt / snoozeUntil  : global snooze window
 *   - dueSince[medId]                : when a med first became due today (millis)
 *
 * Persisted so the engine survives reboots and process death.
 */
class ReminderState(ctx: Context) {
    private val app = ctx.applicationContext
    private val prefs = app.getSharedPreferences("pillpup", Context.MODE_PRIVATE)

    fun snoozeStartedAt(): Long = prefs.getLong(KEY_SNOOZE_AT, 0L)
    fun snoozeUntil(): Long = prefs.getLong(KEY_SNOOZE_UNTIL, 0L)

    fun setSnooze(startedAt: Long, until: Long) {
        if (DataHealth.isCorrupt()) return
        prefs.edit()
            .putLong(KEY_SNOOZE_AT, startedAt)
            .putLong(KEY_SNOOZE_UNTIL, until)
            .apply()
    }

    fun clearSnooze() = setSnooze(0L, 0L)

    fun dueSinceMap(): Map<String, Long> {
        val obj = readDueSince()
        val out = HashMap<String, Long>()
        val it = obj.keys()
        while (it.hasNext()) {
            val k = it.next()
            out[k] = obj.optLong(k)
        }
        return out
    }

    fun setDueSince(medId: String, atMillis: Long) {
        if (DataHealth.isCorrupt()) return
        val obj = readDueSince()
        if (!obj.has(medId)) {
            obj.put(medId, atMillis)
            prefs.edit().putString(KEY_DUE_SINCE, obj.toString()).apply()
        }
    }

    fun clearDueSince(medId: String) {
        if (DataHealth.isCorrupt()) return
        val obj = readDueSince()
        if (obj.has(medId)) {
            obj.remove(medId)
            prefs.edit().putString(KEY_DUE_SINCE, obj.toString()).apply()
        }
    }

    private fun readDueSince(): JSONObject {
        val s = prefs.getString(KEY_DUE_SINCE, "{}") ?: "{}"
        return try {
            JSONObject(s)
        } catch (e: JSONException) {
            DataHealth.markCorrupt(app, "dueSince", e)
            throw e
        }
    }

    fun clearAllDueSince() {
        if (DataHealth.isCorrupt()) return
        prefs.edit().putString(KEY_DUE_SINCE, "{}").apply()
    }

    /** Day that the dueSince map is associated with; cleared on rollover. */
    fun dueSinceDay(): String? = prefs.getString(KEY_DUE_DAY, null)
    fun setDueSinceDay(day: String) {
        if (DataHealth.isCorrupt()) return
        prefs.edit().putString(KEY_DUE_DAY, day).apply()
    }

    companion object {
        private const val KEY_SNOOZE_AT = "snoozeStartedAt"
        private const val KEY_SNOOZE_UNTIL = "snoozeUntil"
        private const val KEY_DUE_SINCE = "dueSince"
        private const val KEY_DUE_DAY = "dueSinceDay"
    }
}
