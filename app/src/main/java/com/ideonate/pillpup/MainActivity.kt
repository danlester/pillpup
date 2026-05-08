package com.ideonate.pillpup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.ideonate.pillpup.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: MedDayAdapter
    private var currentDay: String = Days.today()
    private var corruptDialog: AlertDialog? = null

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignore — UI still works without it */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar?.let { setSupportActionBar(it) }
        binding.manageBtn?.setOnClickListener { openMeds() }

        adapter = MedDayAdapter(
            rows = emptyList(),
            onTake = { med ->
                HistoryStore(this).set(currentDay, med.id, DoseStatus.TAKEN, System.currentTimeMillis())
                Engine.onMedTakenOrSkipped(this, med.id)
                refresh()
            },
            onSkip = { med -> confirmSkip(med) },
            onUndo = { med -> confirmUndo(med) }
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.dayPrev.setOnClickListener {
            currentDay = Days.shift(currentDay, -1)
            refresh()
        }
        binding.dayNext.setOnClickListener {
            val tomorrow = Days.shift(currentDay, 1)
            if (tomorrow <= Days.today()) {
                currentDay = tomorrow
                refresh()
            }
        }
        binding.dayLabel.setOnClickListener {
            currentDay = Days.today()
            refresh()
        }

        ensureNotificationPermission()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_manage -> { openMeds(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun openMeds() {
        startActivity(Intent(this, MedsListActivity::class.java))
    }

    override fun onResume() {
        super.onResume()
        if (DataHealth.isCorrupt()) {
            showCorruptionDialog()
            return
        }
        Notifications.cancel(this)
        // Snap back to today if a day rolled over while we were away.
        if (currentDay > Days.today()) currentDay = Days.today()
        try {
            refresh()
            Engine.checkAndNotify(this)
        } catch (e: org.json.JSONException) {
            // DataHealth.markCorrupt has been called inside the failing store.
            showCorruptionDialog()
        }
    }

    override fun onPause() {
        super.onPause()
        corruptDialog?.dismiss()
        corruptDialog = null
    }

    private fun showCorruptionDialog() {
        if (corruptDialog?.isShowing == true) return
        val c = DataHealth.corruption ?: return
        // Hide stale UI so the user isn't tempted to interact with it.
        adapter.submit(emptyList())
        binding.backlogBanner.visibility = android.view.View.GONE
        binding.empty.visibility = android.view.View.GONE
        val msg = getString(R.string.corrupt_dialog_msg, c.blob, c.message)
        corruptDialog = AlertDialog.Builder(this)
            .setTitle(R.string.corrupt_dialog_title)
            .setMessage(msg)
            .setCancelable(false)
            .setPositiveButton(R.string.corrupt_copy) { _, _ ->
                val cm = getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("PillPup error", msg))
                showCorruptionDialog()
            }
            .setNegativeButton(R.string.corrupt_quit) { _, _ -> finish() }
            .show()
    }

    private fun refresh() {
        val today = Days.today()
        val historyStore = HistoryStore(this)
        val allMeds = MedStore(this).list()
        val medsForDay = allMeds.filter { it.createdDay <= currentDay }
        val history = historyStore.forDay(currentDay)
        val rows = medsForDay.map { med ->
            MedDayRow(med = med, record = history[med.id])
        }
        adapter.submit(rows)
        binding.dayLabel.text = formatDayLabel(currentDay)
        binding.dayNext.isEnabled = currentDay < today
        binding.dayNext.alpha = if (currentDay < today) 1f else 0.3f
        binding.empty.visibility =
            if (rows.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE

        val backlog = historyStore.computeBacklog(allMeds, today)
        if (backlog.count > 0 && backlog.mostRecentDay != null) {
            binding.backlogBanner.visibility = android.view.View.VISIBLE
            binding.backlogBanner.text = resources.getQuantityString(
                R.plurals.backlog_count, backlog.count, backlog.count
            )
            binding.backlogBanner.setOnClickListener {
                currentDay = backlog.mostRecentDay
                refresh()
            }
        } else {
            binding.backlogBanner.visibility = android.view.View.GONE
            binding.backlogBanner.setOnClickListener(null)
        }
    }

    private fun formatDayLabel(day: String): String {
        val ms = Days.parse(day)
        val date = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(ms))
        val today = Days.today()
        return when (day) {
            today -> getString(R.string.day_today_with_date, date)
            Days.shift(today, -1) -> getString(R.string.day_yesterday_with_date, date)
            else -> date
        }
    }

    private fun confirmSkip(med: Med) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.skip_confirm_title, med.name))
            .setMessage(R.string.skip_confirm_msg)
            .setPositiveButton(R.string.skip) { _, _ ->
                HistoryStore(this).set(
                    currentDay, med.id, DoseStatus.SKIPPED, System.currentTimeMillis()
                )
                Engine.onMedTakenOrSkipped(this, med.id)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmUndo(med: Med) {
        AlertDialog.Builder(this)
            .setTitle(R.string.undo_confirm_title)
            .setMessage(R.string.undo_confirm_msg)
            .setPositiveButton(R.string.undo) { _, _ ->
                HistoryStore(this).remove(currentDay, med.id)
                Engine.onMedUndone(this, med.id)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
