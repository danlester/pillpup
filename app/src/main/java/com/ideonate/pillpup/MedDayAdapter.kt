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
    val record: DoseRecord?,    // null = no record on this day
    val isPastDay: Boolean,     // can't change records on past days
    val isToday: Boolean
)

class MedDayAdapter(
    private var rows: List<MedDayRow>,
    private val onTake: (Med) -> Unit,
    private val onSkip: (Med) -> Unit,
    private val onUndo: (Med) -> Unit
) : RecyclerView.Adapter<MedDayAdapter.VH>() {

    fun submit(rows: List<MedDayRow>) {
        this.rows = rows
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
        h.bind(row, onTake, onSkip, onUndo)
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val name: TextView = v.findViewById(R.id.medName)
        private val time: TextView = v.findViewById(R.id.medTime)
        private val slider: SlideToTakeView = v.findViewById(R.id.slider)
        private val skipBtn: Button = v.findViewById(R.id.skipBtn)
        private val statusText: TextView = v.findViewById(R.id.statusText)
        private val tapZone: View = v.findViewById(R.id.tapZone)

        fun bind(
            row: MedDayRow,
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

            val actionRow: View = itemView.findViewById(R.id.actionRow)

            fun showActions() {
                actionRow.visibility = View.VISIBLE
                statusText.visibility = View.GONE
            }
            fun showStatus(textRes: String, colorRes: Int, undoable: Boolean) {
                actionRow.visibility = View.GONE
                statusText.visibility = View.VISIBLE
                statusText.text = textRes
                statusText.setTextColor(ctx.getColor(colorRes))
                if (undoable) {
                    statusText.isClickable = true
                    statusText.setOnClickListener { onUndo(row.med) }
                }
            }

            when {
                row.record?.status == DoseStatus.TAKEN -> {
                    showStatus(
                        ctx.getString(R.string.status_taken_at, formatClock(row.record.atMillis)),
                        R.color.status_taken,
                        undoable = true
                    )
                }
                row.record?.status == DoseStatus.SKIPPED -> {
                    showStatus(
                        ctx.getString(R.string.status_skipped_at, formatClock(row.record.atMillis)),
                        R.color.status_skipped,
                        undoable = true
                    )
                }
                row.isPastDay -> {
                    showStatus(
                        ctx.getString(R.string.status_missed),
                        R.color.status_missed,
                        undoable = false
                    )
                }
                row.isToday -> {
                    showActions()
                    slider.onTaken = { onTake(row.med) }
                    skipBtn.setOnClickListener { onSkip(row.med) }
                }
                else -> {
                    actionRow.visibility = View.GONE
                    statusText.visibility = View.GONE
                }
            }
        }

        private fun formatTime(hour: Int, minute: Int): String =
            String.format(Locale.US, "%02d:%02d", hour, minute)

        private fun formatClock(millis: Long): String =
            SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))
    }
}
