package com.ideonate.pillpup

import android.content.Context
import android.util.Log

/**
 * Process-wide flag for "we couldn't read one of our SharedPreferences blobs".
 *
 * Detection is intentionally loud: the offending blob name and the parser's
 * message are surfaced to the user via a notification (posted on first
 * detection only) and a non-dismissable dialog in MainActivity. The store
 * layer also refuses to write while this flag is set, so the corrupt blob
 * is preserved on disk for inspection / recovery rather than being
 * overwritten by an empty default.
 *
 * Flag lives in memory only — restarting the process clears it. If the
 * underlying corruption is still present, the next read will set it again.
 */
object DataHealth {
    data class Corruption(val blob: String, val message: String, val firstSeen: Long)

    @Volatile
    var corruption: Corruption? = null
        private set

    fun isCorrupt(): Boolean = corruption != null

    fun markCorrupt(context: Context, blob: String, e: Throwable) {
        if (corruption != null) return
        val msg = e.message ?: e.javaClass.simpleName
        corruption = Corruption(blob, msg, System.currentTimeMillis())
        Log.e("DataHealth", "Corrupt $blob blob: $msg", e)
        try {
            Notifications.postCorruption(context.applicationContext, blob, msg)
        } catch (t: Throwable) {
            Log.e("DataHealth", "Failed to post corruption notification", t)
        }
    }
}
