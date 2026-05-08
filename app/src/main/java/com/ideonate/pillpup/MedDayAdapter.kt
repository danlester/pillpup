package com.ideonate.pillpup

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MedDayRow(
    val med: Med,
    val record: DoseRecord?
)

class MedDayAdapter(
    private var rows: List<MedDayRow>,
    private var displayedDay: String,
    private val onTake: (Med) -> Unit,
    private val onSkip: (Med) -> Unit,
    private val onUndo: (Med) -> Unit
) : RecyclerView.Adapter<MedDayAdapter.VH>() {

    fun submit(rows: List<MedDayRow>, displayedDay: String) {
        this.rows = rows
        this.displayedDay = displayedDay
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_med, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val row = rows[pos]
        h.bind(row, displayedDay, onTake, onSkip, onUndo)
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val name: TextView = v.findViewById(R.id.medName)
        private val time: TextView = v.findViewById(R.id.medTime)
        private val slider: SlideToTakeView = v.findViewById(R.id.slider)
        private val skipBtn: Button = v.findViewById(R.id.skipBtn)
        private val statusText: TextView = v.findViewById(R.id.statusText)
        private val actionRow: View = v.findViewById(R.id.actionRow)

        fun bind(
            row: MedDayRow,
            displayedDay: String,
            onTake: (Med) -> Unit,
            onSkip: (Med) -> Unit,
            onUndo: (Med) -> Unit
        ) {
            name.text = row.med.name
            time.text = formatTime(row.med.hour, row.med.minute)

            val ctx = itemView.context
            slider.reset()
            statusText.setOnClickListener(null)
            statusText.isClickable = false

            val rec = row.record
            if (rec == null) {
                actionRow.visibility = View.VISIBLE
                statusText.visibility = View.GONE
                slider.onTaken = { onTake(row.med) }
                skipBtn.setOnClickListener { onSkip(row.med) }
            } else {
                actionRow.visibility = View.GONE
                statusText.visibility = View.VISIBLE
                val stamp = formatStamp(rec.atMillis, displayedDay)
                val (textRes, colorRes) = when (rec.status) {
                    DoseStatus.TAKEN ->
                        ctx.getString(R.string.status_taken_at, stamp) to R.color.status_taken
                    DoseStatus.SKIPPED ->
                        ctx.getString(R.string.status_skipped_at, stamp) to R.color.status_skipped
                }
                statusText.text = textRes
                statusText.setTextColor(ctx.getColor(colorRes))
                statusText.isClickable = true
                statusText.setOnClickListener { onUndo(row.med) }
            }
        }

        private fun formatTime(hour: Int, minute: Int): String =
            String.format(Locale.US, "%02d:%02d", hour, minute)

        // HH:mm if the record was made on the displayed day; otherwise prefix
        // "d MMM" (and the year, when it differs from the displayed day's
        // year) so a backlog row resolved later doesn't masquerade as a
        // same-day timestamp. Records are never purged and the date-stepper
        // has no lower bound, so cross-year resolution is possible.
        private fun formatStamp(millis: Long, displayedDay: String): String {
            val clock = SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))
            if (Days.key(millis) == displayedDay) return clock
            val displayedYear = displayedDay.substring(0, 4)
            val datePattern = if (Days.key(millis).startsWith(displayedYear)) "d MMM" else "d MMM yyyy"
            val date = SimpleDateFormat(datePattern, Locale.getDefault()).format(Date(millis))
            return "$date $clock"
        }
    }
}
