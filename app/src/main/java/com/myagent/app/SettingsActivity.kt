package com.myagent.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.Button
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** One screen, five pages: api, voice, connect (apps), perm (accessibility etc.), agent. All pure black. */
class SettingsActivity : Activity() {
    private lateinit var content: LinearLayout
    private var keysBox: LinearLayout? = null
    private var charBox: LinearLayout? = null
    private var permBox: LinearLayout? = null
    private var page = "agent"

    private val white = Color.WHITE
    private val grey = Color.parseColor("#8B90B5")
    private val fieldBg = Color.parseColor("#0C0C0C")
    private val line = Color.parseColor("#262626")

    private fun d(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = intent.getStringExtra("page") ?: "agent"
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val sv = ScrollView(this)
        sv.setBackgroundColor(Color.BLACK)
        sv.fitsSystemWindows = true
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(d(18), d(8), d(18), d(48))
        sv.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(sv)

        when (page) {
            "api" -> {
                header("API keys")
                apiPage()
            }
            "voice" -> {
                header("Voice")
                voicePage()
            }
            "connect" -> {
                header("Connected apps")
                connectPage()
            }
            "perm" -> {
                header("Accessibility & permissions")
                permBox = box()
            }
            else -> {
                header("Agent settings")
                agentPage()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPerms()
        refreshKeys()
    }

    // ---------- helpers ----------

    private fun wrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun full() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun header(title: String) {
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val back = Ui.tv(this, "←", 28f, white)
        back.setPadding(0, d(8), d(16), d(8))
        back.setOnClickListener { finish() }
        top.addView(back)
        top.addView(Ui.tv(this, title, 22f, white, true))
        content.addView(top, full())
    }

    private fun section(t: String) {
        val v = Ui.tv(this, t.uppercase(), 12f, Color.parseColor("#9D8CFF"), true)
        v.letterSpacing = 0.1f
        content.addView(v, full().apply { topMargin = d(26); bottomMargin = d(8) })
        val l = View(this)
        l.setBackgroundColor(line)
        content.addView(l, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
    }

    private fun box(): LinearLayout {
        val b = LinearLayout(this)
        b.orientation = LinearLayout.VERTICAL
        content.addView(b, full().apply { topMargin = d(8) })
        return b
    }

    private fun field(hint: String): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setHintTextColor(Color.parseColor("#5C607A"))
        e.setTextColor(white)
        e.textSize = 14f
        e.background = Ui.round(this, fieldBg, 12, Color.parseColor("#2B2B2B"))
        e.setPadding(d(12), d(10), d(12), d(10))
        return e
    }

    private fun switchRow(parent: LinearLayout, label: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit): Switch {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(Ui.tv(this, label, 15f, white))
        if (sub.isNotEmpty()) col.addView(Ui.tv(this, sub, 12f, grey))
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(this)
        sw.isChecked = on
        sw.setOnCheckedChangeListener { _, v -> onChange(v) }
        row.addView(sw)
        parent.addView(row, full().apply { topMargin = d(10); bottomMargin = d(2) })
        return sw
    }

    // ---------- page: API keys (4 separate options) ----------

    private val providers = listOf("GEMINI", "OPENAI", "CLAUDE", "CUSTOM")

    private fun apiPage() {
        content.addView(
            Ui.tv(this, "Tap a provider to add or change its key. Models are detected automatically. If one fails, the next saved one is used.", 12f, grey),
            full().apply { topMargin = d(10) }
        )
        keysBox = box()
        refreshKeys()
    }

    private fun providerTitle(p: String): String = when (p) {
        "GEMINI" -> "Gemini"
        "OPENAI" -> "ChatGPT (OpenAI)"
        "CLAUDE" -> "Claude"
        else -> "My model (custom / local coder)"
    }

    private fun refreshKeys() {
        val kb = keysBox ?: return
        kb.removeAllViews()
        val order = Prefs.savedProviders(this)
        for (p in providers) {
            val saved = p in order
            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.setPadding(d(16), d(14), d(16), d(14))
            card.background = Ui.round(this, Color.parseColor("#0B0B12"), 16, if (saved) Color.parseColor("#3A4A9A") else Color.parseColor("#262626"), 2)
            card.addView(Ui.tv(this, providerTitle(p), 17f, white, true))
            val k = if (saved) Prefs.key(this, p) else ""
            val masked = if (k.length > 8) k.take(4) + "…" + k.takeLast(4) else "••••"
            val model = if (p == "CUSTOM" && Prefs.customModel(this).isNotBlank()) Prefs.customModel(this) else Prefs.model(this, p)
            val sub = if (!saved) "Tap to add" else masked + (if (model.isNotBlank()) "  ·  $model" else "") +
                (if (order.firstOrNull { it in Prefs.LLMS } == p) "  ·  primary" else "")
            card.addView(Ui.tv(this, sub, 13f, if (saved) Color.parseColor("#6EE7A0") else grey))
            card.setOnClickListener { keyDialog(p) }
            kb.addView(card, full().apply { bottomMargin = d(12) })
        }
    }

    private fun keyDialog(p: String) {
        val saved = p in Prefs.savedProviders(this)
        val b = LinearLayout(this)
        b.orientation = LinearLayout.VERTICAL
        b.setPadding(d(20), d(8), d(20), 0)
        fun lab(t: String) = b.addView(Ui.tv(this, t, 12f, grey), full().apply { topMargin = d(12) })

        var baseF: EditText? = null
        var modelF: EditText? = null
        if (p == "CUSTOM") {
            lab("Base URL (OpenAI-compatible, ends with /v1)")
            baseF = field("https://your-server.com/v1")
            baseF.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            baseF.setText(Prefs.customBase(this))
            b.addView(baseF, full())
        }
        lab(if (p == "CUSTOM") "API key (optional)" else if (saved) "API key (leave empty to keep the saved one)" else "API key")
        val keyF = field("paste key here")
        keyF.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        keyF.maxLines = 1
        b.addView(keyF, full())
        if (p == "CUSTOM") {
            lab("Model name (optional, auto-detected if empty)")
            modelF = field("model name")
            modelF.setText(Prefs.customModel(this))
            b.addView(modelF, full())
            val preset = Button(this)
            preset.text = "Fill: local coder (Termux)"
            preset.setOnClickListener {
                baseF?.setText("http://127.0.0.1:11434/v1")
                modelF.setText("qwen2.5-coder:3b")
                keyF.setText("ollama")
            }
            b.addView(preset, full().apply { topMargin = d(12) })
        }
        val primary = CheckBox(this)
        primary.text = "Use this one first"
        primary.setTextColor(white)
        b.addView(primary, full().apply { topMargin = d(12) })

        val dlg = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(providerTitle(p))
            .setView(ScrollView(this).apply { addView(b) })
            .setPositiveButton("Save") { _, _ ->
                val key = keyF.text.toString().trim()
                if (p == "CUSTOM") {
                    val base = baseF?.text.toString().trim()
                    if (!(base.startsWith("http://") || base.startsWith("https://")) || base.contains(" ") || base.contains("curl", true)) {
                        Toast.makeText(this, "Base URL galat hai. Sirf link daalo, jaise https://server.com/v1", Toast.LENGTH_LONG).show()
                        return@setPositiveButton
                    }
                    Prefs.setCustom(this, "", base, modelF?.text.toString().trim())
                    if (key.isEmpty() && !saved) Prefs.setKey(this, p, "none")
                }
                if (key.isNotEmpty()) Prefs.setKey(this, p, key)
                if (primary.isChecked && p in Prefs.savedProviders(this)) Prefs.makePrimary(this, p)
                refreshKeys()
            }
            .setNegativeButton("Cancel", null)
        if (saved) {
            dlg.setNeutralButton("Remove") { _, _ ->
                Prefs.removeKey(this, p)
                refreshKeys()
            }
        }
        dlg.show()
    }

    // ---------- page: agent settings ----------

    private fun agentPage() {
        section("Brain")
        val b = box()
        switchRow(b, "Token saver", "Short memory, compact screen reads, small replies", Prefs.saver(this)) {
            Prefs.setSaver(this, it)
        }
        switchRow(b, "Smart model", "Stronger model, uses more tokens (default: fast and cheap)", Prefs.smart(this)) {
            Prefs.setSmart(this, it)
        }
        switchRow(b, "Backup: phone ke AI apps", "API jawab na de to ChatGPT/Gemini/Claude/Qwen app me puchta hai (slow, backup only)", AppBrain.on(this)) {
            AppBrain.setOn(this, it)
        }
        switchRow(b, "Chhoti window me kholo", "AI app phone ke corner me chhoti floating window me khulta hai (phone ko freeform window support chahiye)", AppBrain.small(this)) {
            AppBrain.setSmall(this, it)
        }
        val appsF = field("ChatGPT,Gemini,Claude,Qwen")
        appsF.setText(AppBrain.apps(this))
        appsF.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                AppBrain.setApps(this@SettingsActivity, s?.toString() ?: "")
            }
        })
        b.addView(appsF, full().apply { topMargin = d(8) })
        val lbl = Ui.tv(this, "", 14f, white)
        val sb = SeekBar(this)
        sb.max = 55
        sb.progress = Prefs.maxSteps(this) - 5
        lbl.text = "Max steps per task: ${Prefs.maxSteps(this)}"
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                lbl.text = "Max steps per task: ${p + 5}"
                if (fromUser) Prefs.setMaxSteps(this@SettingsActivity, p + 5)
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        b.addView(lbl, full().apply { topMargin = d(14) })
        b.addView(sb, full())

        section("Safety")
        content.addView(
            Ui.tv(this, "Allowed domains (one per line). Security checks run only on these. Add only sites you own or whose program rules allow testing.", 12f, grey),
            full().apply { topMargin = d(8) }
        )
        val e = field("example.com")
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        e.minLines = 3
        e.gravity = Gravity.TOP
        e.setText(Prefs.scope(this))
        e.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                Prefs.setScope(this@SettingsActivity, s?.toString() ?: "")
            }
        })
        content.addView(e, full().apply { topMargin = d(8) })

        section("Chat")
        content.addView(Ui.btn(this, "Delete current chat") { Chat.clear() }, wrap().apply { topMargin = d(8) })
        content.addView(Ui.tv(this, "My Agent 1.0", 12f, grey), wrap().apply { topMargin = d(24) })
    }

    // ---------- page: voice ----------

    private fun voicePage() {
        val b = box()
        switchRow(b, "Speak replies", "Agent answers out loud", Prefs.speak(this)) { Prefs.setSpeak(this, it) }
        b.addView(Ui.tv(this, "Voice input language", 14f, white), full().apply { topMargin = d(14) })
        val langs = listOf("hi-IN", "en-IN", "en-US", "en-GB")
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, langs)
        sp.setSelection(langs.indexOf(Prefs.lang(this)).coerceAtLeast(0))
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                Prefs.setLang(this@SettingsActivity, langs[pos])
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        b.addView(sp, full())
        section("Characters")
        content.addView(
            Ui.tv(this, "Tap a name to choose it and hear its own line. With a ChatGPT or Gemini key each character speaks in its own real AI voice. Without a key the phone's built-in voice is used.", 12f, grey),
            full().apply { topMargin = d(8) }
        )
        charBox = box()
        refreshChars()
    }

    private fun lineFor(ch: Persona) = ch.sample.ifBlank { "Hello, this is my voice." }

    private fun refreshChars() {
        val cb = charBox ?: return
        cb.removeAllViews()
        val list = Prefs.characters(this)
        val active = Prefs.activeIndex(this)
        for ((i, ch) in list.withIndex()) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val name = Ui.tv(this, (if (i == active) "●  " else "○  ") + ch.name, 15f, if (i == active) Color.parseColor("#9D8CFF") else white)
            name.setPadding(0, d(10), 0, d(10))
            name.setOnClickListener {
                Prefs.setActiveIndex(this, i)
                Voice.stop()
                Voice.speakWith(ch, lineFor(ch))
                refreshChars()
            }
            row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Ui.btn(this, "Test") {
                Voice.stop()
                Voice.speakWith(ch, lineFor(ch))
            }, wrap().apply { rightMargin = d(6) })
            row.addView(Ui.btn(this, "Edit") { editChar(i) }, wrap())
            cb.addView(row, full())
        }
        cb.addView(Ui.btn(this, "+ Add character") { editChar(-1) }, wrap().apply { topMargin = d(8) })
    }

    private fun editChar(idx: Int) {
        val list = Prefs.characters(this).toMutableList()
        val cur = if (idx >= 0) list[idx] else Persona("New voice", 1f, 1f, "hi-IN", "", 0, "alloy", "Kore", "", "")
        val b = LinearLayout(this)
        b.orientation = LinearLayout.VERTICAL
        b.setPadding(d(20), d(8), d(20), 0)

        fun lab(t: String) = b.addView(Ui.tv(this, t, 12f, grey), full().apply { topMargin = d(12) })

        lab("Name")
        val name = field("Name")
        name.setText(cur.name)
        b.addView(name, full())
        lab("Its own line (what it says when you test it)")
        val sample = field("a line in this character's style")
        sample.setText(cur.sample)
        b.addView(sample, full())
        lab("Language tag (hi-IN, en-US ...)")
        val lang = field("hi-IN")
        lang.setText(cur.lang)
        b.addView(lang, full())
        lab("AI voice for ChatGPT key")
        val oaiSpin = Spinner(this)
        oaiSpin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Voice.OAI_VOICES)
        oaiSpin.setSelection(Voice.OAI_VOICES.indexOf(cur.oai).coerceAtLeast(0))
        b.addView(oaiSpin, full())
        lab("AI voice for Gemini key")
        val gemSpin = Spinner(this)
        gemSpin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Voice.GEM_VOICES)
        gemSpin.setSelection(Voice.GEM_VOICES.indexOf(cur.gem).coerceAtLeast(0))
        b.addView(gemSpin, full())
        lab("Style (how this character speaks)")
        val style = field("e.g. calm wise teacher")
        style.setText(cur.style)
        b.addView(style, full())
        lab("Pitch (phone voice only)")
        val pitch = SeekBar(this)
        pitch.max = 150
        pitch.progress = ((cur.pitch - 0.5f) * 100).toInt().coerceIn(0, 150)
        b.addView(pitch, full())
        lab("Speed")
        val rate = SeekBar(this)
        rate.max = 150
        rate.progress = ((cur.rate - 0.5f) * 100).toInt().coerceIn(0, 150)
        b.addView(rate, full())
        lab("Phone voice number (used only without an AI key)")
        val slots = listOf("Voice 1", "Voice 2", "Voice 3", "Voice 4", "Voice 5", "Voice 6")
        val slotSpin = Spinner(this)
        slotSpin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, slots)
        slotSpin.setSelection(cur.slot.coerceIn(0, 5))
        b.addView(slotSpin, full())

        fun read() = Persona(
            name.text.toString().trim().ifBlank { "Voice" },
            pitch.progress / 100f + 0.5f,
            rate.progress / 100f + 0.5f,
            lang.text.toString().trim().ifBlank { "hi-IN" },
            "",
            slotSpin.selectedItemPosition,
            Voice.OAI_VOICES[oaiSpin.selectedItemPosition],
            Voice.GEM_VOICES[gemSpin.selectedItemPosition],
            style.text.toString().trim(),
            sample.text.toString().trim()
        )
        b.addView(Ui.btn(this, "Test voice") {
            Voice.stop()
            val r = read()
            Voice.speakWith(r, lineFor(r))
        }, wrap().apply { topMargin = d(14) })

        val sv = ScrollView(this)
        sv.addView(b)
        val dlg = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(if (idx >= 0) "Edit character" else "New character")
            .setView(sv)
            .setPositiveButton("Save") { _, _ ->
                if (idx >= 0) list[idx] = read() else list.add(read())
                Prefs.saveCharacters(this, list)
                refreshChars()
            }
            .setNegativeButton("Cancel", null)
        if (idx >= 0 && list.size > 1) {
            dlg.setNeutralButton("Delete") { _, _ ->
                list.removeAt(idx)
                Prefs.saveCharacters(this, list)
                Prefs.setActiveIndex(this, 0)
                refreshChars()
            }
        }
        dlg.show()
    }

    // ---------- page: connected apps ----------

    private fun connectPage() {
        content.addView(
            Ui.tv(this, "Apps that are ON can be opened and operated by the agent. Turn an app OFF to disconnect it: the agent will refuse to touch it.", 12f, grey),
            full().apply { topMargin = d(10) }
        )
        val pm = packageManager
        val probe = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(probe, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { Pair(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
        val blocked = Prefs.blocked(this)
        val b = box()
        for ((label, pkg) in apps) {
            switchRow(b, label, "", pkg !in blocked) { on -> Prefs.setBlocked(this, pkg, !on) }
        }
    }

    // ---------- page: permissions ----------

    private fun refreshPerms() {
        val pb = permBox ?: return
        pb.removeAllViews()
        permRow(
            pb, "Microphone", checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        ) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 8) }
        if (Build.VERSION.SDK_INT >= 33) {
            permRow(
                pb, "Notifications", checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9) }
        }
        permRow(pb, "Display over other apps (floating icon)", Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        permRow(pb, "Accessibility (lets the agent tap, swipe, type)", AgentAccessibilityService.enabled(this)) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        permRow(pb, "Keep running in background", true) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        pb.addView(
            Ui.tv(
                this,
                "If Accessibility is greyed out: Phone Settings > Apps > My Agent > ⋮ (top right) > Allow restricted settings. Then enable My Agent under Accessibility.",
                12f, grey
            ),
            full().apply { topMargin = d(8) }
        )
    }

    private fun permRow(parent: LinearLayout, label: String, ok: Boolean, action: () -> Unit) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val t = Ui.tv(this, (if (ok) "✓  " else "✗  ") + label, 14f, if (ok) Color.parseColor("#6EE7A0") else white)
        row.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Ui.btn(this, if (ok) "Open" else "Enable") { action() }, wrap())
        parent.addView(row, full().apply { bottomMargin = d(10) })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshPerms()
        if (Prefs.enabled(this)) OverlayService.start(this)
    }
}
