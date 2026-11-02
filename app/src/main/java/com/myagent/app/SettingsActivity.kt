package com.myagent.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.doAfterTextChanged

/** Saari settings, sections me (title dabao to khulta/band hota hai). */
class SettingsActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var cur: LinearLayout
    private val prefs: Prefs get() = Hub.prefs
    private var preview: OrbView? = null

    private val white = Color.WHITE
    private val grey = Color.rgb(140, 150, 185)
    private val cyan = Palettes.current().c1

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sv = ScrollView(this)
        sv.setBackgroundColor(Palettes.current().bgBottom)
        sv.fitsSystemWindows = true
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(16), dp(16), dp(48))
        sv.addView(root)
        setContentView(sv)
        build()
    }

    override fun onPause() {
        super.onPause()
        Hub.voice.applyConfig()
        Hub.bubble.update()
        if (prefs.masterOn) Hub.startAgentService()
    }

    // ------------------------------------------------------------ screen
    private fun build() {
        text(root, "⚙ Settings", 22f, white, true)
        label(root, "Section ke title par dabao - khulega/band hoga. Sab kuch phone ke andar hi save hota hai.")

        section("🏷 Naam", false)
        val c0 = card()
        label(c0, "App ka naam (isi naam se bulao: \"<naam> WhatsApp kholo\")")
        edit(c0, "App name", prefs.appName, false, false) { prefs.appName = it }
        label(c0, "Aapka naam (assistant aapko isi se bulayega)")
        edit(c0, "Aapka naam", prefs.ownerName, false, false) { prefs.ownerName = it }

        section("🧠 AI APIs (har ek ka alag ON/OFF)", true)
        for (s in Slots.all) {
            if (s.future && s.id == "future1") label(root, "➕ Future ke liye slots (kisi bhi OpenAI-compatible API ka naam/URL/model daalo)")
            apiCard(s)
        }
        val ct = card()
        switchRow(ct, "💸 Token saver (kam token: chhoti baat seedhi, screen sirf zarurat par, purane steps ka summary)", prefs.tokenSaver) { prefs.tokenSaver = it }
        switchRow(ct, "🤝 Saari ON APIs aapas me salah karke task karein (jyada token lagte hain)", prefs.collab) { prefs.collab = it }
        label(ct, "API beech me fail ho (limit/net/key) to agli API pichla poora progress leke wahin se chalegi. Limit ke wait wale error me app khud ruk kar retry karti hai.")
        usageLabel(ct)

        section("📷 Photo API", false)
        val cp = card()
        switchRow(cp, "Photo API ON", prefs.photoOn) { prefs.photoOn = it }
        label(cp, "Screenshot samajhne ke liye (Gemini vision). Jab screen ka text kaafi na ho tab 'look' use hota hai.")
        edit(cp, "API key", prefs.photoKey, true, false) { prefs.photoKey = it }
        edit(cp, "Model", prefs.photoModel, false, false) { prefs.photoModel = it }
        edit(cp, "Endpoint", prefs.photoEndpoint, false, false) { prefs.photoEndpoint = it }
        button(cp, "🧪 Photo API test") { testPhoto() }

        section("🎨 Look, orb aur panel", false)
        val cl = card()
        label(cl, "Rang (theme)")
        spinner(cl, Palettes.all.map { it.name }, prefs.themeIdx) { prefs.themeIdx = it; preview?.applyPalette() }
        label(cl, "3D orb ka style (neeche live preview)")
        spinner(cl, listOf("1. Neural gola (jaal + bhanwar)", "2. Gyroscope rings", "3. Hologram cube", "4. Galaxy"), prefs.orbStyle) {
            prefs.orbStyle = it
            preview?.styleOverride = it
            preview?.invalidate()
        }
        val pv = OrbView(this)
        pv.setActive(true)
        pv.styleOverride = prefs.orbStyle
        preview = pv
        cl.addView(pv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(170)))
        switchRow(cl, "Floating chhota orb (doosri app me; agent OFF hote hi gayab)", prefs.floatingOn) { prefs.floatingOn = it; Hub.bubble.update() }
        switchRow(cl, "Bubble dabane par chhota panel (poori app nahi kholta)", prefs.panelOn) { prefs.panelOn = it }
        label(cl, "Panel: type + mic. Kisi bhi app ke upar aata hai, kaam disturb nahi hota. Panel ko ⤢ se poori app me khol sakte ho.")
        switchRow(cl, "Battery saver (animation aur steps ki raftaar kam)", prefs.batterySaver) { prefs.batterySaver = it }

        section("🎙 Voice (awaaz, character, naam se bulana)", false)
        val cv = card()
        switchRow(cv, "Jawab bol kar sunao", prefs.ttsEnabled) { prefs.ttsEnabled = it }
        label(cv, "Character (awaaz ka andaaz)")
        spinner(cv, Characters.all.map { it.name }, prefs.character) { prefs.character = it; Hub.voice.applyConfig() }
        button(cv, "▶ Is character ko suno") {
            Hub.voice.applyConfig()
            Hub.voice.speakDemo(Characters.current().demo)
        }
        button(cv, "▶▶ Saare characters ek ke baad ek suno") { playAllCharacters() }
        label(cv, "Character pitch/speed badalta hai. Asli ladka/ladki awaaz phone ki installed voices par nirbhar hai - neeche list se chuno (Settings -> Language -> Text-to-speech me aur voices download ho sakti hain).")
        val vs = Hub.voice.availableVoices()
        spinner(cv, listOf("Auto") + vs, (vs.indexOf(prefs.voiceName) + 1).coerceAtLeast(0)) { prefs.voiceName = if (it == 0) "" else vs[it - 1] }
        label(cv, "Bhasha")
        val langs = listOf("Hindi (hi-IN)", "English India (en-IN)", "English US (en-US)", "English UK (en-GB)")
        val codes = listOf("hi-IN", "en-IN", "en-US", "en-GB")
        spinner(cv, langs, codes.indexOf(prefs.lang).coerceAtLeast(0)) { prefs.lang = codes[it]; Hub.voice.applyConfig() }
        seek(cv, "Awaaz ki raftaar", prefs.rate) { prefs.rate = it }
        seek(cv, "Awaaz ki pitch", prefs.pitch) { prefs.pitch = it }
        switchRow(cv, "Bolne ke baad khud sunna shuru karo (voice chat)", prefs.autoListen) { prefs.autoListen = it }
        switchRow(cv, "Sirf naam bolne par jawab (mic wale mode me)", prefs.requireName) { prefs.requireName = it }
        switchRow(cv, "👂 Hamesha suno: kisi bhi screen par naam bolo (battery zyada)", prefs.wakeAlways) {
            prefs.wakeAlways = it
            if (it && !Hub.prefs.masterOn) Toast.makeText(this, "Agent ON karo tab sunna shuru hoga", Toast.LENGTH_LONG).show()
            if (prefs.masterOn) { Hub.startAgentService(); if (!it) WakeListener.stop() }
        }
        label(cv, "Hamesha-suno: ek notification dikhti rehti hai aur mic ka indicator jalta hai. Screen band hone par ruk jata hai. Naam saaf aur alag-sa rakho. Nahi chalta to: Digital assistant app (Permissions screen) ya notification/Quick-Settings ka 🎙 Bolo use karo.")
        button(cv, "🎙 Mic test (5 sec bolo)") { micTest() }

        section("📱 Apps (phone ki SAARI installed apps)", false)
        val ca0 = card()
        val n = Apps.all(this, true)
        label(ca0, "Mili apps: " + n.size + " (user ki: " + n.count { !it.system } + ", system: " + n.count { it.system } + ")")
        button(ca0, "📋 Saari apps dekho / test-open") { showAllApps() }
        label(ca0, "Trading apps (agent in apps ko trading ke liye pehchanega)")
        val tv = text(ca0, appsSummary(prefs.tradeApps), 13f, white, false)
        button(ca0, "📈 Trading apps chuno") { pickApps("Trading apps chuno", prefs.tradeApps) { prefs.tradeApps = it; tv.text = appsSummary(it) } }
        label(ca0, "Social apps (Instagram, YouTube, X, LinkedIn...)")
        val sv2 = text(ca0, appsSummary(prefs.socialApps), 13f, white, false)
        button(ca0, "📱 Social apps chuno") { pickApps("Social apps chuno", prefs.socialApps) { prefs.socialApps = it; sv2.text = appsSummary(it) } }
        label(ca0, "Nicknames (chhota naam = app): jaise  mw=com.marketwolf.app ;  insta=Instagram   (; se alag)")
        edit(ca0, "mw=PackageYaNaam;insta=Instagram", prefs.nicknames, false, false) { prefs.nicknames = it }

        section("🤖 Agent aur trading", false)
        val ca = card()
        switchRow(ca, "Risky kaam (payment/order/delete/post) se pehle meri haan maango", prefs.confirmRisky) { prefs.confirmRisky = it }
        label(ca, "Max steps ek task me (phir ruk kar poochhta hai)")
        edit(ca, "40", prefs.maxSteps.toString(), false, true) { v -> v.toIntOrNull()?.let { prefs.maxSteps = it.coerceIn(1, 500) } }
        label(ca, "Har step ke baad ruko (milliseconds)")
        edit(ca, "800", prefs.stepDelayMs.toString(), false, true) { v -> v.toIntOrNull()?.let { prefs.stepDelayMs = it.coerceIn(100, 10000) } }
        label(ca, "Trading: capital aur har trade me risk % (qty ka hisaab)")
        edit(ca, "100000", prefs.capital.toInt().toString(), false, true) { v -> v.toFloatOrNull()?.let { if (it > 0) prefs.capital = it } }
        edit(ca, "1", prefs.riskPct.toString(), false, true) { v -> v.toFloatOrNull()?.let { if (it > 0 && it <= 100) prefs.riskPct = it } }
        label(ca, "Meri websites/apps ke domains (security audit sirf inhi par; comma se alag)")
        edit(ca, "example.com, app.example.com", prefs.mySites, false, false) { prefs.mySites = it }
        label(ca, "⚠ Trading me nuksan ho sakta hai. Agent sirf analysis/plan deta hai; order tabhi lagata hai jab aap 'haan' bolo. Kamai ki guarantee nahi.")

        section("🌐 Extra APIs aur Playbooks (bina update ke naye skills)", false)
        val cx = card()
        label(cx, "Koi bhi API: naam + key + (optional) base URL. Agent use {{KEY:naam}} likhega - asli key use nahi dikhti. Nayi API dhundne ko agent public-apis list khud dekhta hai.")
        val en = edit(cx, "naam (jaise newsapi)", "", false, false) { }
        val ek = edit(cx, "API key", "", true, false) { }
        val eb = edit(cx, "base URL (optional)", "", false, false) { }
        val savedTv = text(cx, Extras.status(), 12f, grey, false)
        button(cx, "💾 Key save karo") {
            if (en.text.isNullOrBlank() || ek.text.isNullOrBlank()) Toast.makeText(this, "Naam aur key dono daalo", Toast.LENGTH_SHORT).show()
            else { Extras.put(en.text.toString(), ek.text.toString(), eb.text.toString()); en.setText(""); ek.setText(""); eb.setText(""); savedTv.text = Extras.status() }
        }
        button(cx, "🗑 Ek key hatao (naam likh kar)") {
            if (!en.text.isNullOrBlank()) { Extras.put(en.text.toString(), "", ""); savedTv.text = Extras.status() }
        }
        label(cx, "Playbook URL (apni GitHub repo ki raw .json: {\"naam\":\"tareeka text\"}). Isse naye skills app update ke bina aate hain.")
        edit(cx, "https://raw.githubusercontent.com/.../playbooks.json", prefs.playbookUrl, false, false) { prefs.playbookUrl = it }
        button(cx, "⬇ Playbooks abhi laao") {
            Thread {
                val r = Playbooks.refreshRemote(true)
                runOnUiThread { Toast.makeText(this, r, Toast.LENGTH_LONG).show() }
            }.start()
        }
        label(cx, "Built-in playbooks: " + Playbooks.names().joinToString(", "))

        section("📣 Mere social media accounts", false)
        val so = card()
        label(so, "Yahan apne accounts likho. Agent inhi ke hisaab se content banayega, post/comment/analytics sambhalega (post karne se pehle tumse 'haan' lega). Ye sirf tumhare phone me rehta hai. Login tum khud karo - password agent ko mat do.")
        for ((k, hint) in Social.platforms) edit(so, hint, Social.get(k), false, false) { Social.set(k, it) }
        label(so, "Tumhara vishay / niche (jaise: tech, fitness, trading, comedy)")
        edit(so, "niche", Social.get("niche"), false, false) { Social.set("niche", it) }
        label(so, "Bhasha aur andaaz")
        edit(so, "Hinglish, friendly, short", Social.get("tone"), false, false) { Social.set("tone", it) }
        label(so, "Lakshya (followers, sales, brand...) aur posting routine")
        edit(so, "goal", Social.get("goal"), false, false) { Social.set("goal", it) }
        edit(so, "hafte me 4 reel, roz 1 story", Social.get("schedule"), false, false) { Social.set("schedule", it) }
        button(so, "📱 Social apps chuno (Instagram, YouTube...)") { pickApps("Social apps chuno", prefs.socialApps) { prefs.socialApps = it } }
        label(so, "Chat me bolo: \"is hafte ka content calendar banao\", \"mere last reel ka analytics batao\", \"comments ke reply draft karo\", \"thumbnail image banao\".")

        section("✈ Telegram (alert + door se control)", false)
        val tg = card()
        label(tg, "1) Telegram me @BotFather se bot banao -> token yahan paste. 2) Apne bot ko 'hi' bhejo. 3) 'Chat-id pehchano' dabao. Phir agent ke alerts/jawab Telegram par aayenge, aur Telegram se likhne par wo kaam karega (sirf tumhari chat-id ki baat suni jati hai).")
        edit(tg, "Bot token", prefs.str("tg_token"), true, false) { prefs.putStr("tg_token", it.trim()) }
        edit(tg, "Chat-id", prefs.str("tg_chat"), false, false) { prefs.putStr("tg_chat", it.trim()) }
        button(tg, "🔍 Chat-id pehchano") { Thread { val m = Telegram.detectChat(); runOnUiThread { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); recreate() } }.start() }
        switchRow(tg, "Telegram se kaam lena (agent ON ho tab; thodi battery lagti hai)", prefs.bool("tg_control", false)) { prefs.putBool("tg_control", it); if (it) Telegram.start() else Telegram.stop() }
        button(tg, "📨 Test message bhejo") { Thread { val m = Telegram.send("✅ " + prefs.appName + " Telegram se jud gaya"); runOnUiThread { Toast.makeText(this, m, Toast.LENGTH_LONG).show() } }.start() }

        section("💻 PC link (apna laptop/PC chalana)", false)
        val cpc = card()
        label(cpc, "PC par pc_agent/pc_agent.py chalao (README me hai). Phir yahan uska URL aur token daalo. Dono same WiFi par ho.")
        edit(cpc, "http://192.168.1.10:8765", prefs.pcUrl, false, false) { prefs.pcUrl = it }
        edit(cpc, "token", prefs.pcToken, true, false) { prefs.pcToken = it }
        button(cpc, "🧪 PC test (list)") {
            Thread {
                val r = PcLink.call("list", ".", "")
                runOnUiThread { Toast.makeText(this, r.take(200), Toast.LENGTH_LONG).show() }
            }.start()
        }

        section("⏰ Scheduled kaam aur watch", false)
        val cs = card()
        val st = text(cs, Sched.list(), 13f, white, false)
        button(cs, "🔄 List taaza karo") { st.text = Sched.list() }
        val sid = edit(cs, "cancel karne ke liye # number", "", false, true) { }
        button(cs, "❌ Cancel") {
            val id = sid.text.toString().toIntOrNull()
            if (id != null) { Toast.makeText(this, Sched.cancel(id), Toast.LENGTH_SHORT).show(); st.text = Sched.list() }
        }
        label(cs, "Naya schedule chat me bolo: \"roz subah 8 baje digest do\" ya \"NIFTY 25000 ke upar jaye to batao\".")

        section("🔐 Permissions, sehat aur safai", false)
        val cm = card()
        button(cm, "🩺 Sehat-jaanch") { Hub.log("🩺 Sehat-jaanch:\n" + Scanner.report(this)); Toast.makeText(this, Scanner.report(this), Toast.LENGTH_LONG).show() }
        button(cm, "🔐 Permissions kholo") { startActivity(Intent(this, PermissionsActivity::class.java)) }
        button(cm, "📈 Trading Hub kholo") { startActivity(Intent(this, TradingActivity::class.java)) }
        button(cm, "🗑 Chalta task bhula do") { Hub.engine.forgetTask() }
        button(cm, "🧹 Chat log saaf") { Hub.clearLog() }
        button(cm, "💾 Settings backup (keys ke bina) Download/MyAgent me") {
            try {
                val p = FileStore.save(this, "Backup", "myagent_settings.json", prefs.exportAll())
                Toast.makeText(this, "Saved: $p", Toast.LENGTH_LONG).show()
            } catch (e: Exception) { Toast.makeText(this, "Backup fail: " + (e.message ?: ""), Toast.LENGTH_LONG).show() }
        }
        button(cm, "♻ Saari settings reset (keys bhi mit jayengi)") {
            AlertDialog.Builder(this).setTitle("Reset?").setMessage("Saari settings, keys aur yaaddasht mit jayengi.")
                .setPositiveButton("Haan reset") { _, _ -> prefs.clearAll(); Hub.engine.forgetTask(); recreate() }
                .setNegativeButton("Nahi", null).show()
        }
    }

    // ------------------------------------------------------------ pieces
    private fun usageLabel(parent: LinearLayout) {
        val sb = StringBuilder("📊 Aaj ka token use (API ke asli count se): ").append(prefs.usageAll()).append(" | calls ").append(prefs.callsToday())
        for (s in Slots.all) if (prefs.usageToday(s.id) > 0) sb.append("\n  ").append(prefs.name(s)).append(": ").append(prefs.usageToday(s.id))
        label(parent, sb.toString())
    }

    private fun appsSummary(csv: String): String {
        val set = Apps.selected(csv)
        if (set.isEmpty()) return "(abhi koi nahi chuni)"
        return set.joinToString(", ") { Apps.labelOf(this, it) }
    }

    private fun showAllApps() {
        val apps = Apps.all(this, true)
        val labels = apps.map { (if (it.system) "" else "★ ") + it.label + "\n" + it.pkg }
        val lv = ListView(this)
        lv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        lv.setOnItemClickListener { _, _, pos, _ ->
            val r = Apps.launch(this, apps[pos].pkg)
            Toast.makeText(this, r, Toast.LENGTH_SHORT).show()
        }
        AlertDialog.Builder(this).setTitle("Saari apps (" + apps.size + ") - ★ = aapki download ki hui").setView(lv).setPositiveButton("Band", null).show()
    }

    private fun pickApps(title: String, current: String, onSave: (String) -> Unit) {
        val all = Apps.all(this, true)
        val sel = Apps.selected(current)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(12), dp(8), dp(12), 0)
        val search = EditText(this)
        search.hint = "naam se dhundo..."
        search.setSingleLine(true)
        box.addView(search)
        val lv = ListView(this)
        lv.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        box.addView(lv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(380)))
        var shown: List<AppInfo> = all
        fun refill(q: String) {
            val n = Apps.norm(q)
            shown = if (n.isEmpty()) all else all.filter { Apps.norm(it.label).contains(n) || it.pkg.contains(q.trim().lowercase()) }
            lv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, shown.map { (if (it.system) "" else "★ ") + it.label })
            for (i in shown.indices) lv.setItemChecked(i, sel.contains(shown[i].pkg))
        }
        refill("")
        lv.setOnItemClickListener { _, _, pos, _ ->
            val pkg = shown[pos].pkg
            if (lv.isItemChecked(pos)) sel.add(pkg) else sel.remove(pkg)
        }
        search.doAfterTextChanged { refill(it?.toString() ?: "") }
        AlertDialog.Builder(this).setTitle(title).setView(box)
            .setPositiveButton("Save") { _, _ -> onSave(sel.joinToString(",")) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun playAllCharacters() {
        val keep = prefs.character
        var delay = 0L
        for ((i, ch) in Characters.all.withIndex()) {
            Hub.mainHandler.postDelayed({
                prefs.character = i
                Hub.voice.applyConfig()
                Hub.voice.speakDemo(ch.name.substringBefore(" -") + ". " + ch.demo)
            }, delay)
            delay += 5200L
        }
        Hub.mainHandler.postDelayed({ prefs.character = keep; Hub.voice.applyConfig() }, delay)
    }

    private fun micTest() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Pehle Permissions me Microphone allow karo", Toast.LENGTH_LONG).show()
            return
        }
        val v = VoiceIn(this)
        Toast.makeText(this, "Ab bolo...", Toast.LENGTH_SHORT).show()
        v.start(object : VoiceIn.CB {
            override fun onPartial(text: String) {}
            override fun onState(text: String) {}
            override fun onFinal(text: String) { Toast.makeText(this@SettingsActivity, "✅ Suna: $text", Toast.LENGTH_LONG).show(); v.destroy() }
            override fun onFail(msg: String, needFallback: Boolean) {
                Toast.makeText(this@SettingsActivity, "❌ $msg", Toast.LENGTH_LONG).show()
                v.destroy()
                if (needFallback) try { startActivity(Intent(this@SettingsActivity, VoiceActivity::class.java)) } catch (e: Exception) { }
            }
        })
    }

    private fun apiCard(s: Slot) {
        val c = card()
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        if (s.future) {
            val et = EditText(this)
            et.setText(prefs.name(s))
            et.hint = "Is API ka naam"
            et.setTextColor(white)
            et.setHintTextColor(grey)
            et.textSize = 16f
            et.doAfterTextChanged { prefs.setName(s, it?.toString() ?: "") }
            head.addView(et, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        } else {
            val tv = TextView(this)
            tv.text = s.defName
            tv.setTextColor(white)
            tv.textSize = 17f
            tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
            head.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        val sw = SwitchCompat(this)
        sw.isChecked = prefs.on(s)
        sw.setOnCheckedChangeListener { _, b -> prefs.setOn(s, b) }
        head.addView(sw)
        c.addView(head)
        if (s.id == "ollama") label(c, "Apne PC par Ollama/LM Studio chala kar uska URL daalo (jaise http://192.168.1.10:11434/v1/chat/completions). Key me 'none' likho. Free, bina limit, par PC chalu aur same WiFi chahiye.")
        if (s.id == "openrouter") label(c, "openrouter.ai par free account; model ke aage ':free' wale models bina paise ke chalte hain.")

        lateinit var modelEt: EditText
        lateinit var epEt: EditText
        val keyEt = edit(c, "API key (paste karo - model khud pehchan liya jayega)", prefs.key(s), true, false) { v ->
            prefs.setKey(s, v)
            scheduleAuto(s, v) { m, e -> modelEt.setText(m); epEt.setText(e) }
        }
        modelEt = edit(c, "Model (khali chhodo = auto)", prefs.str("slot.${s.id}.model").ifBlank { if (s.future) "" else prefs.model(s) }, false, false) { prefs.setModel(s, it) }
        epEt = edit(c, "Endpoint" + if (s.future || s.id == "ollama") " (OpenAI-compatible chat/completions URL)" else "", prefs.str("slot.${s.id}.endpoint").ifBlank { if (s.future) "" else prefs.endpoint(s) }, false, false) { prefs.setEndpoint(s, it) }
        button(c, "🔍 Key se model khud pehchano") {
            lastAuto[s.id] = keyEt.text.toString().trim()
            runAuto(s, keyEt.text.toString()) { m, e -> modelEt.setText(m); epEt.setText(e) }
        }
        button(c, "🧪 Test") { testSlot(s) }
    }

    private val lastAuto = HashMap<String, String>()
    private val autoH = android.os.Handler(android.os.Looper.getMainLooper())
    private val autoR = HashMap<String, Runnable>()

    /** Key paste hote hi (1.2 sec ruk kar) auto-detect. */
    private fun scheduleAuto(s: Slot, key: String, fill: (String, String) -> Unit) {
        autoR[s.id]?.let { autoH.removeCallbacks(it) }
        val k = key.trim()
        if (k.length < 20 || lastAuto[s.id] == k) return
        val r = Runnable { lastAuto[s.id] = k; runAuto(s, k, fill) }
        autoR[s.id] = r
        autoH.postDelayed(r, 1200)
    }

    private fun runAuto(s: Slot, key: String, fill: (String, String) -> Unit) {
        Toast.makeText(this, "🔍 Key pehchani ja rahi hai...", Toast.LENGTH_SHORT).show()
        Thread {
            val r = try { AutoDetect.run(s, key, prefs) } catch (e: Exception) { AutoDetect.Result(false, "Error: " + (e.message ?: e.javaClass.simpleName), null) }
            runOnUiThread {
                Toast.makeText(this, r.msg, Toast.LENGTH_LONG).show()
                if (r.routedTo != null) recreate() else fill(prefs.model(s), prefs.endpoint(s))
            }
        }.start()
    }

    private fun testSlot(s: Slot) {
        Toast.makeText(this, prefs.name(s) + " test ho raha hai...", Toast.LENGTH_SHORT).show()
        Thread {
            val msg = try {
                val r = ApiClient.chat(s, prefs, listOf(Msg("user", "Reply with the single word OK")), 40)
                "✅ " + prefs.name(s) + " chal rahi hai: " + r.take(40) + "  (model: " + prefs.model(s) + ")"
            } catch (e: ApiException) {
                "❌ " + prefs.name(s) + " [" + e.code + "] " + (e.message ?: "").take(120)
            }
            runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        }.start()
    }

    private fun testPhoto() {
        Toast.makeText(this, "Photo API test ho raha hai...", Toast.LENGTH_SHORT).show()
        Thread {
            val png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
            val msg = try {
                val r = ApiClient.describeImage(prefs, png, "Is image ka rang batao (1 shabd).", "image/png")
                "✅ Photo API chal rahi hai: " + r.take(40)
            } catch (e: ApiException) {
                "❌ Photo API [" + e.code + "] " + (e.message ?: "").take(120)
            }
            runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        }.start()
    }

    // ------------------------------------------------------------ small UI helpers
    private fun text(parent: LinearLayout, t: String, size: Float, color: Int, bold: Boolean): TextView {
        val tv = TextView(this)
        tv.text = t
        tv.textSize = size
        tv.setTextColor(color)
        if (bold) tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
        parent.addView(tv)
        return tv
    }

    private fun section(t: String, open: Boolean) {
        val head = TextView(this)
        head.textSize = 16f
        head.setTextColor(cyan)
        head.setTypeface(head.typeface, android.graphics.Typeface.BOLD)
        head.setPadding(0, dp(10), 0, dp(10))
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.visibility = if (open) View.VISIBLE else View.GONE
        head.text = (if (open) "▾ " else "▸ ") + t
        head.setOnClickListener {
            val show = body.visibility != View.VISIBLE
            body.visibility = if (show) View.VISIBLE else View.GONE
            head.text = (if (show) "▾ " else "▸ ") + t
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(14)
        root.addView(head, lp)
        root.addView(body)
        cur = body
    }

    private fun label(parent: LinearLayout, t: String) {
        val tv = text(parent, t, 12f, grey, false)
        (tv.layoutParams as LinearLayout.LayoutParams).topMargin = dp(8)
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(14), dp(12), dp(14), dp(12))
        val bg = GradientDrawable()
        bg.setColor(Color.rgb(21, 24, 38))
        bg.cornerRadius = dp(14).toFloat()
        bg.setStroke(dp(1), Palettes.alpha(Palettes.current().c1, 100))
        c.background = bg
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(8)
        cur.addView(c, lp)
        return c
    }

    private fun switchRow(parent: LinearLayout, t: String, value: Boolean, onChange: (Boolean) -> Unit) {
        val sw = SwitchCompat(this)
        sw.text = t
        sw.setTextColor(white)
        sw.isChecked = value
        sw.setOnCheckedChangeListener { _, b -> onChange(b) }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(6)
        parent.addView(sw, lp)
    }

    private fun edit(parent: LinearLayout, hint: String, value: String, password: Boolean, numeric: Boolean, onChange: (String) -> Unit): EditText {
        val et = EditText(this)
        et.setText(value)
        et.hint = hint
        et.setTextColor(white)
        et.setHintTextColor(grey)
        et.textSize = 14f
        et.setSingleLine(true)
        et.inputType = when {
            password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            numeric -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        et.doAfterTextChanged { onChange(it?.toString() ?: "") }
        parent.addView(et)
        return et
    }

    private fun spinner(parent: LinearLayout, items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        sp.setSelection(selected.coerceIn(0, maxOf(0, items.size - 1)))
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { onSelect(pos) }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        parent.addView(sp)
    }

    /** 0.5 se 2.0 tak, 0.05 ke step. */
    private fun seek(parent: LinearLayout, title: String, value: Float, onChange: (Float) -> Unit) {
        val tv = text(parent, title + ": " + String.format("%.2f", value), 13f, white, false)
        (tv.layoutParams as LinearLayout.LayoutParams).topMargin = dp(10)
        val sb = SeekBar(this)
        sb.max = 30
        sb.progress = Math.round((value - 0.5f) / 0.05f).coerceIn(0, 30)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val v = 0.5f + p * 0.05f
                tv.text = title + ": " + String.format("%.2f", v)
                if (fromUser) onChange(v)
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        parent.addView(sb)
    }

    private fun button(parent: LinearLayout, t: String, onClick: () -> Unit) {
        val b = Button(this)
        b.text = t
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        parent.addView(b)
    }
}
