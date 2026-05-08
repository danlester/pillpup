package com.ideonate.pillpup

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Days {

    private fun fmt(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }

    fun key(millis: Long): String = fmt().format(Date(millis))

    fun today(): String = key(System.currentTimeMillis())

    fun parse(key: String): Long = fmt().parse(key)?.time ?: 0L

    /** First millisecond of the day after [key], in local time. */
    fun startOfNextDay(key: String): Long {
        val cal = Calendar.getInstance()
        cal.time = fmt().parse(key) ?: return 0L
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis
    }

    fun shift(key: String, deltaDays: Int): String {
        val cal = Calendar.getInstance()
        cal.time = fmt().parse(key) ?: return key
        cal.add(Calendar.DAY_OF_MONTH, deltaDays)
        return key(cal.timeInMillis)
    }

    /**
     * Millis at hour:minute on the day represented by [dayKey], local time.
     */
    fun atTimeOnDay(dayKey: String, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.time = fmt().parse(dayKey) ?: return 0L
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
