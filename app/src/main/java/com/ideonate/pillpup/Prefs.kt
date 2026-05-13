package com.ideonate.pillpup

import android.content.Context

/**
 * User preferences. Lives in the same `pillpup` SharedPreferences file as the
 * rest of the app so it rides on Auto Backup and the corruption story.
 *
 * Keys:
 *   - eveningCutoffMinutes (Int, minute-of-day 0..1439). Default 21:00.
 *     Each dose is treated as missed at the next cutoff strictly after its
 *     scheduled time. So a 07:00 med with a 21:00 cutoff misses at 21:00
 *     same day; with a 00:00 cutoff it misses at midnight (original
 *     behaviour); with a 02:00 cutoff it misses at 02:00 the next day.
 */
class Prefs(ctx: Context) {
    private val prefs = ctx.applicationContext
        .getSharedPreferences("pillpup", Context.MODE_PRIVATE)

    fun eveningCutoffMinutes(): Int =
        prefs.getInt(KEY_CUTOFF, DEFAULT_CUTOFF_MINUTES).coerceIn(0, 1439)

    fun setEveningCutoffMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_CUTOFF, minutes.coerceIn(0, 1439)).apply()
    }

    companion object {
        private const val KEY_CUTOFF = "eveningCutoffMinutes"
        const val DEFAULT_CUTOFF_MINUTES = 21 * 60
    }
}
