package com.juwon.toastdesk

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var spProvider: Spinner
    private lateinit var baseUrl: EditText
    private lateinit var model: EditText
    private lateinit var apiKey: EditText
    private lateinit var btnSave: Button
    private lateinit var btnTest: Button
    private lateinit var btnBrowse: Button
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView

    private val PROVIDERS = arrayOf(
        "OpenRouter",
        "xAI (Grok)",
        "OpenAI",
        "Gemini (Google)",
        "Custom (OpenAI-compatible)")

    private val DEFAULTS = mapOf(
        "xAI (Grok)" to Pair("https://api.x.ai/v1", "grok-4"),
        "OpenRouter" to Pair("https://openrouter.ai/api/v1", "nvidia/nemotron-3.5-lightning:free"),
        "OpenAI" to Pair("https://api.openai.com/v1", "gpt-4o-mini"),
        "Gemini (Google)" to Pair("https://generativelanguage.googleapis.com/v1beta", "gemini-2.0-flash"),
        "Custom (OpenAI-compatible)" to Pair("", ""))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        spProvider = findViewById(R.id.spProvider)
        baseUrl = findViewById(R.id.baseUrl)
        model = findViewById(R.id.model)
        apiKey = findViewById(R.id.apiKey)
        btnSave = findViewById(R.id.btnSave)
        btnTest = findViewById(R.id.btnTest)
        btnBrowse = findViewById(R.id.btnBrowse)
        progress = findViewById(R.id.progress)
        status = findViewById(R.id.status)

        spProvider.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, PROVIDERS).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val s = getSharedPreferences("toastdesk.settings", Context.MODE_PRIVATE)
        val savedProvider = s.getString("provider", "OpenRouter") ?: "OpenRouter"
        val idx = PROVIDERS.indexOf(savedProvider)
        spProvider.setSelection(if (idx >= 0) idx else 0)
        baseUrl.setText(s.getString("baseUrl", "") ?: "")
        model.setText(s.getString("model", "") ?: "")
        apiKey.setText(s.getString("apiKey", "") ?: "")
        if (baseUrl.text.isBlank() && model.text.isBlank()) applyPreset()

        spProvider.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) { }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) { }
        }
        // apply preset only when the provider actually changes
        var lastSelected = spProvider.selectedItemPosition
        spProvider.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos != lastSelected) {
                    lastSelected = pos
                    applyPreset()
                }
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) { }
        }

        btnSave.setOnClickListener { save(); status.text = "Saved." }
        btnTest.setOnClickListener { test() }
        btnBrowse.setOnClickListener { browseFree() }
    }

    private fun applyPreset() {
        val d = DEFAULTS[spProvider.selectedItem?.toString()] ?: return
        if (d.first.isNotBlank()) baseUrl.setText(d.first)
        if (d.second.isNotBlank()) model.setText(d.second)
    }

    private fun save() {
        getSharedPreferences("toastdesk.settings", Context.MODE_PRIVATE).edit()
            .putString("provider", spProvider.selectedItem?.toString())
            .putString("baseUrl", baseUrl.text.toString().trim())
            .putString("model", model.text.toString().trim())
            .putString("apiKey", apiKey.text.toString().trim())
            .apply()
    }

    private fun test() {
        save()
        val s = com.juwon.toastdesk.Api.Settings(
            spProvider.selectedItem?.toString() ?: "",
            baseUrl.text.toString().trim(),
            model.text.toString().trim(),
            apiKey.text.toString().trim())
        if (s.apiKey.isBlank() || s.model.isBlank()) {
            status.text = "Fill the model and API key first."
            return
        }
        btnTest.isEnabled = false
        progress.visibility = View.VISIBLE
        status.text = "Testing..."
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val r = withContext(Dispatchers.IO) {
                    Api.call(s, "You are a connection test. Reply with exactly: OK", "test")
                }
                val first = r.replies.firstOrNull()?.text?.trim()?.take(60) ?: ""
                status.text = if (first.equals("OK", true)) "Connected. $s works."
                              else "Connected, but it replied: \"$first\""
            } catch (e: Exception) {
                status.text = "Failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                btnTest.isEnabled = true
                progress.visibility = View.GONE
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun browseFree() {
        if (!(spProvider.selectedItem?.toString() ?: "").startsWith("OpenRouter")) {
            status.text = "The free-model list works with the OpenRouter provider only."
            return
        }
        btnBrowse.isEnabled = false
        progress.visibility = View.VISIBLE
        status.text = "Loading free models..."
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val ids = withContext(Dispatchers.IO) { Api.freeOpenRouterModels() }
                if (ids.isEmpty()) {
                    status.text = "No free models listed right now."
                } else {
                    status.text = ""
                    AlertDialog.Builder(this@SettingsActivity)
                        .setTitle("Free models (" + ids.size + ")")
                        .setItems(ids.toTypedArray()) { _, w -> model.setText(ids[w]) }
                        .setNegativeButton("Close", null)
                        .show()
                }
            } catch (e: Exception) {
                status.text = "Couldn't load the list: ${e.message?.take(140) ?: e.javaClass.simpleName}"
            } finally {
                btnBrowse.isEnabled = true
                progress.visibility = View.GONE
            }
        }
    }
}
