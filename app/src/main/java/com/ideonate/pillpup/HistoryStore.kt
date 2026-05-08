package com.ideonate.pillpup

import android.content.Context
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
    private val prefs = ctx.applicationContext.getSharedPreferences("pillpup", Context.MODE_PRIVATE)

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
     * Count of (med, day) pairs in [med.createdDay, today-1] with no taken/skipped record,
     * plus the most-recent unresolved day (or null if backlog is empty).
     * Walked from yesterday backwards so the first hit is the most recent.
     */
    fun computeBacklog(meds: List<Med>, today: String): BacklogResult {
        if (meds.isEmpty()) return BacklogResult(0, null)
        val earliestCreated = meds.minOf { it.createdDay }
        val root = root()
        var count = 0
        var mostRecent: String? = null
        var day = Days.shift(today, -1)
        while (day >= earliestCreated) {
            val dayObj = root.optJSONObject(day)
            for (med in meds) {
                if (med.createdDay > day) continue
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
        return JSONObject(s)
    }

    private fun save(root: JSONObject) {
        prefs.edit().putString(KEY_HISTORY, root.toString()).apply()
    }

    companion object {
        private const val KEY_HISTORY = "history"
    }
}
