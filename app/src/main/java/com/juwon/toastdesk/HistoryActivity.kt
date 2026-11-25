package com.juwon.toastdesk

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var list: ListView
    private var sessions: MutableList<HistoryStore.Session> = mutableListOf()
    private val fmt = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        list = findViewById(R.id.list)
        reload()
    }

    private fun reload() {
        sessions = HistoryStore.load(this)
        list.adapter = object : BaseAdapter() {
            override fun getCount() = sessions.size
            override fun getItem(i: Int) = sessions[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, conv: View?, parent: ViewGroup): View {
                val v = conv ?: LayoutInflater.from(this@HistoryActivity)
                    .inflate(R.layout.item_history, parent, false)
                val s = sessions[i]
                v.findViewById<TextView>(R.id.tvWhen).text = fmt.format(Date(s.ts))
                v.findViewById<TextView>(R.id.tvHer).text =
                    s.her.replace("\n", " ").let { if (it.length > 120) it.take(120) + "..." else it }
                v.findViewById<TextView>(R.id.tvCount).text =
                    "${s.replies.size} replies / ${s.opts} / ${s.model}"
                return v
            }
        }
        list.setOnItemClickListener { _, _, i, _ -> show(sessions[i]) }
        if (sessions.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No sessions yet. Generate replies and they will be saved here."
            empty.setTextColor(getColor(R.color.dim))
            empty.setPadding(48, 48, 48, 48)
            list.emptyView = empty
        }
    }

    private fun show(s: HistoryStore.Session) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(36, 24, 36, 8)
        for (r in s.replies) {
            val v = layoutInflater.inflate(R.layout.item_reply, box, false)
            v.findViewById<TextView>(R.id.tvLabel).text = r.label
            v.findViewById<TextView>(R.id.tvText).text = r.text
            v.findViewById<TextView>(R.id.tvWhy).text = if (r.why.isBlank()) "" else r.why
            v.findViewById<View>(R.id.btnCopy).setOnClickListener { copy(r.text) }
            box.addView(v)
        }
        s.tip?.let {
            val tv = TextView(this)
            tv.text = "Tip: $it"
            tv.setTextColor(getColor(R.color.dim))
            tv.textSize = 13f
            tv.setPadding(4, 24, 4, 8)
            box.addView(tv)
        }
        AlertDialog.Builder(this)
            .setTitle(fmt.format(Date(s.ts)))
            .setView(box)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun copy(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("reply", text))
        Toast.makeText(this, "Copied.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_history, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_history -> {
                AlertDialog.Builder(this)
                    .setTitle("Clear history")
                    .setMessage("Delete every saved session?")
                    .setPositiveButton("Delete") { _, _ ->
                        HistoryStore.clear(this)
                        reload()
                    }
                    .setNegativeButton("Keep", null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
