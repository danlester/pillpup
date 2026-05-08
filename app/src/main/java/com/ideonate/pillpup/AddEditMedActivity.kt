package com.ideonate.pillpup

import android.app.AlertDialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ideonate.pillpup.databinding.ActivityAddeditBinding

class AddEditMedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddeditBinding
    private var existing: Med? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddeditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val medId = intent.getStringExtra(EXTRA_MED_ID)
        existing = medId?.let { MedStore(this).byId(it) }

        binding.timePicker.setIs24HourView(true)
        existing?.let { med ->
            binding.toolbar.title = getString(R.string.title_edit_med)
            binding.nameInput.setText(med.name)
            binding.timePicker.hour = med.hour
            binding.timePicker.minute = med.minute
            binding.deleteBtn.visibility = android.view.View.VISIBLE
        } ?: run {
            binding.toolbar.title = getString(R.string.title_add_med)
            binding.timePicker.hour = 9
            binding.timePicker.minute = 0
            binding.deleteBtn.visibility = android.view.View.GONE
        }

        binding.saveBtn.setOnClickListener { save() }
        binding.deleteBtn.setOnClickListener { confirmDelete() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun save() {
        val name = binding.nameInput.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            binding.nameLayout.error = getString(R.string.err_name_required)
            return
        }
        binding.nameLayout.error = null
        val hour = binding.timePicker.hour
        val minute = binding.timePicker.minute
        val store = MedStore(this)
        val med = existing
        if (med == null) {
            val created = store.add(name, hour, minute)
            ReminderScheduler.scheduleNextFor(this, created)
            Engine.checkAndNotify(this)
        } else {
            val updated = med.copy(name = name, hour = hour, minute = minute)
            store.update(updated)
            ReminderScheduler.scheduleNextFor(this, updated)
            Engine.checkAndNotify(this)
        }
        finish()
    }

    private fun confirmDelete() {
        val med = existing ?: return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_confirm_title, med.name))
            .setMessage(R.string.delete_confirm_msg)
            .setPositiveButton(R.string.delete) { _, _ ->
                MedStore(this).remove(med.id)
                Engine.onMedRemoved(this, med.id)
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_MED_ID = "medId"
    }
}
