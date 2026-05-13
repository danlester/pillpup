package com.ideonate.pillpup

import android.content.Context
import org.json.JSONException
import org.json.JSONObject

/**
 * History of taken/skipped doses, keyed by day then med id.
 * Absence on a past day is interpreted as "missed" by the UI.
 *
 * On-disk shape:
 *   { "2026-05-08": { "<medId>": { "at": 1715175600000, "status": "taken" } } }
 */
data class BacklogResult(val count: Int, val mostRecentDay: String?)

class HistoryStore(ctx: Context) {
    private val app = ctx.applicationContext
    private val prefs = app.getSharedPreferences("pillpup", Context.MODE_PRIVATE)

    fun get(day: String, medId: String): DoseRecord? {
        val root = root()
        val dayObj = root.optJSONObject(day) ?: return null
        val rec = dayObj.optJSONObject(medId) ?: return null
        val statusStr = rec.optString("status")
        val status = when (statusStr) {
            "taken" -> DoseStatus.TAKEN
            "skipped" -> DoseStatus.SKIPPED
            else -> return null
        }
        return DoseRecord(status, rec.optLong("at"))
    }

    fun forDay(day: String): Map<String, DoseRecord> {
        val dayObj = root().optJSONObject(day) ?: return emptyMap()
        val out = HashMap<String, DoseRecord>()
        val keys = dayObj.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val rec = dayObj.optJSONObject(id) ?: continue
            val status = when (rec.optString("status")) {
                "taken" -> DoseStatus.TAKEN
                "skipped" -> DoseStatus.SKIPPED
                else -> continue
            }
            out[id] = DoseRecord(status, rec.optLong("at"))
        }
        return out
    }

    fun set(day: String, medId: String, status: DoseStatus, atMillis: Long) {
        val root = root()
        val dayObj = root.optJSONObject(day) ?: JSONObject().also { root.put(day, it) }
        dayObj.put(
            medId,
            JSONObject()
                .put("at", atMillis)
                .put("status", if (status == DoseStatus.TAKEN) "taken" else "skipped")
        )
        save(root)
    }

    fun remove(day: String, medId: String) {
        val root = root()
        val dayObj = root.optJSONObject(day) ?: return
        if (!dayObj.has(medId)) return
        dayObj.remove(medId)
        if (dayObj.length() == 0) root.remove(day)
        save(root)
    }

    /**
     * Counts unresolved (med, day) pairs that are *now considered missed*.
     *
     * A dose is missed at the next [cutoffMinutes]-of-day strictly after its
     * scheduled time. So with cutoff 21:00 a 07:00 med misses at 21:00 same
     * day; with cutoff 00:00 (midnight) it misses at the start of the next
     * day — equivalent to the pre-cutoff behaviour. With cutoff 02:00 a
     * 07:00 med misses at 02:00 the next day.
     *
     * The walk includes today (today's early meds can be missed in the
     * evening) and goes back BACKLOG_WINDOW_DAYS so the banner doesn't
     * nag about ancient gaps. The day-list past that horizon still works
     * if the user navigates back manually.
     *
     * Returns the count and the most-recent (largest) day that contributes,
     * so the banner can jump straight to it.
     */
    fun computeBacklog(
        meds: List<Med>,
        today: String,
        cutoffMinutes: Int,
        now: Long = System.currentTimeMillis()
    ): BacklogResult {
        if (meds.isEmpty()) return BacklogResult(0, null)
        val windowStart = Days.shift(today, -BACKLOG_WINDOW_DAYS)
        val earliestCreated = meds.minOf { it.createdDay }
        val lowerBound = if (earliestCreated > windowStart) earliestCreated else windowStart
        val root = root()
        var count = 0
        var mostRecent: String? = null
        var day = today
        while (day >= lowerBound) {
            val dayObj = root.optJSONObject(day)
            for (med in meds) {
                if (med.createdDay > day) continue
                if (now < missedAt(day, med, cutoffMinutes)) continue
                val rec = dayObj?.optJSONObject(med.id)
                val status = rec?.optString("status")
                val resolved = status == "taken" || status == "skipped"
                if (!resolved) {
                    count++
                    if (mostRecent == null) mostRecent = day
                }
            }
            day = Days.shift(day, -1)
        }
        return BacklogResult(count, mostRecent)
    }

    private fun missedAt(day: String, med: Med, cutoffMinutes: Int): Long {
        val medMins = med.hour * 60 + med.minute
        val onDay = if (cutoffMinutes > medMins) day else Days.shift(day, 1)
        return Days.atTimeOnDay(onDay, cutoffMinutes / 60, cutoffMinutes % 60)
    }

    fun clearMed(medId: String) {
        val root = root()
        val dayKeys = root.keys().asSequence().toList()
        var changed = false
        for (day in dayKeys) {
            val dayObj = root.optJSONObject(day) ?: continue
            if (dayObj.has(medId)) {
                dayObj.remove(medId)
                changed = true
            }
            if (dayObj.length() == 0) {
                root.remove(day)
                changed = true
            }
        }
        if (changed) save(root)
    }

    private fun root(): JSONObject {
        val s = prefs.getString(KEY_HISTORY, "{}") ?: "{}"
        return try {
            JSONObject(s)
        } catch (e: JSONException) {
            DataHealth.markCorrupt(app, "history", e)
            throw e
        }
    }

    private fun save(root: JSONObject) {
        // Refuse to overwrite a corrupt blob; preserve it for inspection.
        if (DataHealth.isCorrupt()) return
        // commit(): dose take/skip/undo is the data we least want to lose.
        prefs.edit().putString(KEY_HISTORY, root.toString()).commit()
    }

    companion object {
        private const val KEY_HISTORY = "history"
        const val BACKLOG_WINDOW_DAYS = 30
    }
}
