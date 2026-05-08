package com.ideonate.pillpup

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

class MedStore(ctx: Context) {
    private val app = ctx.applicationContext
    private val prefs = app.getSharedPreferences("pillpup", Context.MODE_PRIVATE)

    fun list(): List<Med> {
        val s = prefs.getString(KEY_MEDS, "[]") ?: "[]"
        val arr = try {
            JSONArray(s)
        } catch (e: JSONException) {
            DataHealth.markCorrupt(app, "meds", e)
            throw e
        }
        val out = ArrayList<Med>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                Med(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    hour = o.getInt("hour"),
                    minute = o.getInt("minute"),
                    createdDay = o.optString("createdDay", Days.today())
                )
            )
        }
        out.sortWith(compareBy({ it.hour }, { it.minute }, { it.name.lowercase() }))
        return out
    }

    fun byId(id: String): Med? = list().firstOrNull { it.id == id }

    fun add(name: String, hour: Int, minute: Int): Med {
        val med = Med(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            hour = hour,
            minute = minute,
            createdDay = Days.today()
        )
        val current = list().toMutableList()
        current.add(med)
        save(current)
        return med
    }

    fun update(med: Med) {
        val updated = list().map { if (it.id == med.id) med else it }
        save(updated)
    }

    fun remove(id: String) {
        save(list().filterNot { it.id == id })
    }

    private fun save(meds: List<Med>) {
        // Refuse to overwrite once corruption has been detected — preserve the
        // bad blob on disk so it can be inspected / recovered.
        if (DataHealth.isCorrupt()) return
        val arr = JSONArray()
        meds.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("hour", it.hour)
                    .put("minute", it.minute)
                    .put("createdDay", it.createdDay)
            )
        }
        // commit(): user-initiated add/edit/delete — worth a synchronous flush so the
        // change survives if the process is killed immediately after.
        prefs.edit().putString(KEY_MEDS, arr.toString()).commit()
    }

    companion object {
        private const val KEY_MEDS = "meds"
    }
}
