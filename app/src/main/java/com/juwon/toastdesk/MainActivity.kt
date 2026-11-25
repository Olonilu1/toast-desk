package com.juwon.toastdesk

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var her: EditText
    private lateinit var notes: EditText
    private lateinit var myDraft: EditText
    private lateinit var spTone: Spinner
    private lateinit var spLang: Spinner
    private lateinit var spStage: Spinner
    private lateinit var spGoal: Spinner
    private lateinit var spLen: Spinner
    private lateinit var btnGo: Button
    private lateinit var btnClear: Button
    private lateinit var btnCatch: Button
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var resultsBox: LinearLayout
    private var job: Job? = null

    private val TONES = arrayOf("Playful", "Smooth", "Sweet", "Funny", "Bold", "Calm")
    private val LANGS = arrayOf("Match her style", "English", "Nigerian Pidgin", "English with a little Yoruba")
    private val STAGES = arrayOf("Just started talking", "Been chatting a while", "Already close")
    private val GOALS = arrayOf("Keep the chat going", "Make her laugh", "Compliment her", "Ask her out", "Fix an awkward moment")
    private val LENS = arrayOf("Short", "Medium")

    private val TONE_MEANING = mapOf(
        "Playful" to "teasing, light banter, a little cheeky",
        "Smooth" to "confident, relaxed, understated charm",
        "Sweet" to "warm, caring, genuine and kind",
        "Funny" to "humour first, makes her smile or laugh",
        "Bold" to "clear about his interest, still respectful and not pushy",
        "Calm" to "low pressure, mature, steady and easy to talk to")
    private val LANG_MEANING = mapOf(
        "Match her style" to "Write in the same language and register she used (English, Pidgin, or a mix).",
        "English" to "Plain, natural English.",
        "Nigerian Pidgin" to "Natural Nigerian Pidgin, the way people really text it. Not exaggerated.",
        "English with a little Yoruba" to "Natural English with a light touch of Yoruba words or phrases. Only where it sounds real, never forced.")
    private val LEN_MEANING = mapOf(
        "Short" to "One short line, like a quick WhatsApp reply.",
        "Medium" to "Two or three short lines at most.")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        her = findViewById(R.id.her)
        notes = findViewById(R.id.notes)
        myDraft = findViewById(R.id.myDraft)
        spTone = findViewById(R.id.spTone)
        spLang = findViewById(R.id.spLang)
        spStage = findViewById(R.id.spStage)
        spGoal = findViewById(R.id.spGoal)
        spLen = findViewById(R.id.spLen)
        btnGo = findViewById(R.id.btnGo)
        btnClear = findViewById(R.id.btnClear)
        btnCatch = findViewById(R.id.btnCatch)
        progress = findViewById(R.id.progress)
        status = findViewById(R.id.status)
        resultsBox = findViewById(R.id.resultsBox)

        fill(spTone, TONES)
        fill(spLang, LANGS)
        fill(spStage, STAGES)
        fill(spGoal, GOALS)
        fill(spLen, LENS)
        loadPrefs()

        btnGo.setOnClickListener { generate() }
        btnClear.setOnClickListener { clearAll() }
        btnCatch.setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }

        val shared = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (!shared.isNullOrBlank()) {
            her.setText(shared)
            status.text = "Loaded from the share sheet. Check the options, then get replies."
        }
    }

    override fun onResume() {
        super.onResume()
        loadCaught()
        btnCatch.visibility = if (listenerEnabled()) View.GONE else View.VISIBLE
    }

    private fun listenerEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return s.contains(packageName)
    }

    private fun loadCaught() {
        val p = getSharedPreferences("toastdesk.catcher", Context.MODE_PRIVATE)
        if (!p.getBoolean("pending", false)) return
        p.edit().putBoolean("pending", false).apply()
        val body = p.getString("last", "") ?: return
        if (body.isBlank()) return
        val title = p.getString("lastTitle", "") ?: ""
        val full = if (title.isNotBlank() && !body.startsWith("$title:")) "$title:\n$body" else body
        her.setText(full)
        status.text = "Loaded automatically from WhatsApp. Check the options, then get replies."
    }

    private fun fill(sp: Spinner, items: Array<String>) {
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun loadPrefs() {
        val p = getSharedPreferences("toastdesk.prefs", Context.MODE_PRIVATE)
        pick(spTone, TONES, p.getString("tone", "Playful"))
        pick(spLang, LANGS, p.getString("lang", "Match her style"))
        pick(spStage, STAGES, p.getString("stage", "Just started talking"))
        pick(spGoal, GOALS, p.getString("goal", "Keep the chat going"))
        pick(spLen, LENS, p.getString("len", "Short"))
    }

    private fun pick(sp: Spinner, items: Array<String>, value: String?) {
        val i = items.indexOf(value)
        if (i >= 0) sp.setSelection(i)
    }

    private fun savePrefs() {
        getSharedPreferences("toastdesk.prefs", Context.MODE_PRIVATE).edit()
            .putString("tone", spTone.selectedItem?.toString())
            .putString("lang", spLang.selectedItem?.toString())
            .putString("stage", spStage.selectedItem?.toString())
            .putString("goal", spGoal.selectedItem?.toString())
            .putString("len", spLen.selectedItem?.toString())
            .apply()
    }

    private fun settings(): Api.Settings {
        val s = getSharedPreferences("toastdesk.settings", Context.MODE_PRIVATE)
        return Api.Settings(
            provider = s.getString("provider", "OpenRouter") ?: "OpenRouter",
            baseUrl = s.getString("baseUrl", "") ?: "",
            model = s.getString("model", "") ?: "",
            apiKey = s.getString("apiKey", "") ?: "")
    }

    private fun clearAll() {
        her.setText("")
        notes.setText("")
        myDraft.setText("")
        resultsBox.removeAllViews()
        status.text = ""
    }

    private fun systemPrompt(): String = """
        You are a warm, witty, emotionally intelligent texting coach. A man in Nigeria wants to "toast" (flirt with and court) a woman on WhatsApp. He gives you what she sent. Write replies he can send as himself.

        RULES
        - Reply to the LATEST message from her. The pasted text may be a raw WhatsApp chat export with lines like "[10/4/26, 8:12 PM] Name: text". In that case the user's own lines are his; ignore timestamps, system notices and "<Media omitted>".
        - Sound like a real person texting on WhatsApp. No cheesy pickup lines, no cliches, no lectures.
        - Mirror her energy and length. Never sound more eager than she does.
        - Always be respectful: no pressure, guilt-tripping, put-downs, manipulation, or sexual content.
        - If she is not interested or the chat looks dead, say so honestly in the tip. Never manufacture hope.
        - If he provides his own draft (WHAT HE WANTS TO SEND), respect what he wants to say: keep his meaning and intent, only polish the wording and delivery. Never replace his draft with something he did not say.
        - Follow the requested tone, language, stage, goal and length exactly.
        - Vary the replies so they are genuinely different strategies, not the same line reworded.

        OUTPUT FORMAT
        Return ONLY valid JSON, no markdown fences, no commentary, in this exact shape:
        {"replies":[{"label":"Warm","text":"...","why":"one short line on why it works"},{"label":"Playful","text":"...","why":"..."},{"label":"Smooth","text":"...","why":"..."},{"label":"Direct","text":"...","why":"..."}],"tip":"one practical tip for where to take the chat next"}
        Normally exactly four replies with those labels. If he provided his own draft, add a FIFTH reply labeled "His draft, polished" whose text is his draft improved (same meaning, better delivery).
        Each "text" is the message he would actually send. "why" is one short line.
    """.trimIndent()

    private fun userPrompt(): String {
        val tone = spTone.selectedItem?.toString() ?: "Playful"
        val lang = spLang.selectedItem?.toString() ?: "Match her style"
        val stage = spStage.selectedItem?.toString() ?: "Just started talking"
        val goal = spGoal.selectedItem?.toString() ?: "Keep the chat going"
        val len = spLen.selectedItem?.toString() ?: "Short"
        val notesTxt = notes.text.toString().trim().ifBlank { "none" }
        val draft = myDraft.text.toString().trim().ifBlank { "none" }
        val herText = her.text.toString().trim()
        return """
            TONE: $tone (${TONE_MEANING[tone] ?: ""})
            LANGUAGE: $lang. ${LANG_MEANING[lang] ?: ""}
            WHERE YOU TWO ARE: $stage
            WHAT YOU WANT: $goal
            LENGTH: $len. ${LEN_MEANING[len] ?: ""}
            NOTES FROM HIM: $notesTxt
            WHAT HE WANTS TO SEND (his own draft, optional): $draft

            HER MESSAGE (may be a full chat export; act on the latest message from her):
            ---
            $herText
            ---
        """.trimIndent()
    }

    private fun generate() {
        savePrefs()
        val herText = her.text.toString().trim()
        if (herText.isEmpty()) {
            status.text = "Paste what she sent first."
            return
        }
        val s = settings()
        if (s.apiKey.isBlank() || s.model.isBlank()) {
            status.text = "Add your API key and model first (menu, top right)."
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }

        btnGo.isEnabled = false
        btnClear.isEnabled = false
        progress.visibility = View.VISIBLE
        status.text = "Drafting..."
        resultsBox.removeAllViews()

        val sys = systemPrompt()
        val usr = userPrompt()
        val opts = "${spTone.selectedItem} / ${spLang.selectedItem} / ${spStage.selectedItem} / ${spGoal.selectedItem} / ${spLen.selectedItem}"
        val herSnapshot = herText.take(2000)
        job = CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = withContext(Dispatchers.IO) { Api.call(s, sys, usr) }
                render(result)
                status.text = "Done. Read each reply before you send it."
                HistoryStore.add(applicationContext, HistoryStore.Session(
                    System.currentTimeMillis(), herSnapshot, result.replies, result.tip, opts, s.model))
            } catch (e: Exception) {
                status.text = "Failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                btnGo.isEnabled = true
                btnClear.isEnabled = true
                progress.visibility = View.GONE
            }
        }
    }

    private fun render(result: Api.Result) {
        val inf = LayoutInflater.from(this)
        for (r in result.replies) {
            val v = inf.inflate(R.layout.item_reply, resultsBox, false)
            v.findViewById<TextView>(R.id.tvLabel).text = r.label
            v.findViewById<TextView>(R.id.tvText).text = r.text
            v.findViewById<TextView>(R.id.tvWhy).text = if (r.why.isBlank()) "" else r.why
            v.findViewById<Button>(R.id.btnCopy).setOnClickListener { copy(r.text) }
            resultsBox.addView(v)
        }
        result.tip?.let {
            val tv = TextView(this)
            tv.text = "Tip: $it"
            tv.setTextColor(getColor(R.color.dim))
            tv.textSize = 13f
            tv.setPadding(4, 18, 4, 8)
            resultsBox.addView(tv)
        }
        val regen = Button(this)
        regen.text = "NEW BATCH"
        regen.setTextColor(getColor(R.color.accent))
        regen.background = null
        regen.setOnClickListener { generate() }
        resultsBox.addView(regen)
        val wa = Button(this)
        wa.text = "OPEN WHATSAPP"
        wa.setTextColor(getColor(R.color.accent))
        wa.background = null
        wa.setOnClickListener { openWhatsApp() }
        resultsBox.addView(wa)
    }

    private fun openWhatsApp() {
        val intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            Toast.makeText(this, "WhatsApp is not installed.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copy(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("reply", text))
        Toast.makeText(this, "Copied. Paste it in the chat.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_history -> { startActivity(Intent(this, HistoryActivity::class.java)); true }
            R.id.action_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
