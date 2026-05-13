package com.ideonate.pillpup

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.ideonate.pillpup.databinding.ActivitySettingsBinding
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var medsAdapter: MedsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        medsAdapter = MedsAdapter(emptyList()) { med ->
            startActivity(
                Intent(this, AddEditMedActivity::class.java)
                    .putExtra(AddEditMedActivity.EXTRA_MED_ID, med.id)
            )
        }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = medsAdapter

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddEditMedActivity::class.java))
        }

        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = showTab(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        binding.cutoffRow.setOnClickListener { showCutoffPicker() }
    }

    override fun onResume() {
        super.onResume()
        refreshMeds()
        refreshPrefs()
    }

    private fun showTab(position: Int) {
        binding.tabMeds.visibility = if (position == 0) View.VISIBLE else View.GONE
        binding.tabPrefs.visibility = if (position == 1) View.VISIBLE else View.GONE
    }

    private fun refreshMeds() {
        val meds = MedStore(this).list()
        medsAdapter.submit(meds)
        binding.empty.visibility = if (meds.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun refreshPrefs() {
        val mins = Prefs(this).eveningCutoffMinutes()
        binding.cutoffValue.text = formatHm(mins)
    }

    private fun showCutoffPicker() {
        val prefs = Prefs(this)
        val current = prefs.eveningCutoffMinutes()
        TimePickerDialog(
            this,
            { _, h, m ->
                prefs.setEveningCutoffMinutes(h * 60 + m)
                ReminderScheduler.scheduleEveningCutoff(this)
                refreshPrefs()
            },
            current / 60, current % 60, true
        ).show()
    }

    private fun formatHm(minutes: Int): String =
        String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)

    private class MedsAdapter(
        private var meds: List<Med>,
        private val onClick: (Med) -> Unit
    ) : RecyclerView.Adapter<MedsAdapter.VH>() {

        fun submit(list: List<Med>) {
            this.meds = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_med_simple, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, pos: Int) {
            val med = meds[pos]
            h.name.text = med.name
            h.time.text = String.format(Locale.US, "%02d:%02d", med.hour, med.minute)
            h.itemView.setOnClickListener { onClick(med) }
        }

        override fun getItemCount(): Int = meds.size

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.medName)
            val time: TextView = v.findViewById(R.id.medTime)
        }
    }
}
