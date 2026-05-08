package com.ideonate.pillpup

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ideonate.pillpup.databinding.ActivityMedsListBinding
import java.util.Locale

class MedsListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMedsListBinding
    private lateinit var adapter: Adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMedsListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = Adapter(emptyList()) { med ->
            startActivity(
                Intent(this, AddEditMedActivity::class.java)
                    .putExtra(AddEditMedActivity.EXTRA_MED_ID, med.id)
            )
        }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddEditMedActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val meds = MedStore(this).list()
        adapter.submit(meds)
        binding.empty.visibility = if (meds.isEmpty()) View.VISIBLE else View.GONE
    }

    private class Adapter(
        private var meds: List<Med>,
        private val onClick: (Med) -> Unit
    ) : RecyclerView.Adapter<Adapter.VH>() {

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
