package com.myagent.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), AgentEngine.Listener {

    private lateinit var orb: OrbView
    private lateinit var toggle: PowerToggle
    private lateinit var tvName: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvHealth: TextView
    private lateinit var tvLog: TextView
    private lateinit var scroll: ScrollView
    private lateinit var etInput: EditText
    private lateinit var btnMic: ImageButton
    private lateinit var btnSend: ImageButton
    private lateinit var btnMore: ImageButton

    private var voiceIn: VoiceIn? = null
    private var listening = false
    private var visible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        orb = findViewById(R.id.orb)
        toggle = findViewById(R.id.toggle)
        tvName = findViewById(R.id.tvName)
        tvStatus = findViewById(R.id.tvStatus)
        tvHealth = findViewById(R.id.tvHealth)
        tvLog = findViewById(R.id.tvLog)
        scroll = findViewById(R.id.scroll)
        etInput = findViewById(R.id.etInput)
        btnMic = findViewById(R.id.btnMic)
        btnSend = findViewById(R.id.btnSend)
        btnMore = findViewById(R.id.btnMore)

        btnSend.setOnClickListener { sendCurrent() }
        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendCurrent(); true } else false
        }
        btnMic.setOnClickListener { if (listening) stopListening() else startListening() }
        btnMore.setOnClickListener { showMenu(it) }
        tvHealth.setOnClickListener { startActivity(Intent(this, PermissionsActivity::class.java)) }
        toggle.onChange = { on -> if (on != Hub.prefs.masterOn) { Hub.setMaster(on); if (!on) tvStatus.text = "OFF" } }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun applySkin() {
        val k = Skins.current()
        val rootV = (findViewById<android.view.ViewGroup>(android.R.id.content)).getChildAt(0)
        val neon = k.bgTop == 0
        if (!neon) {
            val g = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(k.bgTop, k.bgBottom))
            rootV.background = g
        } else {
            rootV.setBackgroundResource(R.drawable.bg_main)
        }
        findViewById<View>(R.id.bg).visibility = if (k.showCircuit) View.VISIBLE else View.GONE
        orb.visibility = if (k.showOrb) View.VISIBLE else View.GONE
        val lp = toggle.layoutParams
        lp.width = dp(if (k.showOrb) 180 else 110)
        lp.height = dp(if (k.showOrb) 76 else 46)
        toggle.layoutParams = lp
        if (!neon) {
            tvName.paint.shader = null
            tvName.setTextColor(k.accent)
            tvLog.setTextColor(k.text)
            tvStatus.setTextColor(k.sub)
            etInput.setTextColor(k.text)
            etInput.setHintTextColor(k.sub)
            btnSend.setColorFilter(k.accent)
            btnMic.setColorFilter(k.accent2)
            btnMore.setColorFilter(k.text)
            toggle.setAccent(k.accent)
            val bar = GradientDrawable()
            bar.setColor(k.bar)
            bar.cornerRadius = dp(26).toFloat()
            bar.setStroke(dp(1), Skins.alpha(k.sub, 120))
            findViewById<LinearLayout>(R.id.inputBar).background = bar
            val circ = GradientDrawable()
            circ.shape = GradientDrawable.OVAL
            circ.setColor(k.bar)
            btnMore.background = circ
        } else {
            tvLog.setTextColor(Color.rgb(221, 227, 255))
            etInput.setTextColor(Color.WHITE)
            etInput.setHintTextColor(Color.rgb(127, 137, 176))
            btnMore.clearColorFilter()
            btnMore.setBackgroundResource(R.drawable.bg_circle)
        }
        val tf = if (k.mono) android.graphics.Typeface.MONOSPACE else android.graphics.Typeface.DEFAULT
        tvLog.typeface = tf
        etInput.typeface = tf
    }

    private fun applyPalette() {
        val p = Palettes.current()
        findViewById<CircuitBackground>(R.id.bg).applyPalette()
        orb.applyPalette()
        toggle.setAccent(p.c1)
        tvName.paint.shader = LinearGradient(0f, 0f, tvName.paint.measureText(tvName.text.toString()), 0f, p.c1, p.c2, Shader.TileMode.CLAMP)
        tvName.invalidate()
        val bar = GradientDrawable()
        bar.setColor(Color.rgb(20, 24, 41))
        bar.cornerRadius = dp(28).toFloat()
        bar.setStroke(dp(2), p.c1)
        findViewById<LinearLayout>(R.id.inputBar).background = bar
        btnSend.setColorFilter(p.c1)
        btnMic.setColorFilter(p.c2)
        tvStatus.setTextColor(p.c2)
        val chips = findViewById<LinearLayout>(R.id.chips)
        chips.removeAllViews()
        val items = listOf(
            "💻 Code" to "Ye program likho, chala kar test karo aur fir do: ",
            "📈 Trading" to "",
            "📊 Meri trade app" to "Meri trading app kholo, watchlist padho aur candle + indicator analysis karke batao. ",
            "🎬 Film" to "Film/video banane me madad karo: idea se script, shot list aur launch plan tak. Topic: ",
            "📱 Social" to "Social media ke liye content banao aur post karne ka plan do: ",
            "💰 Kamai" to "Internet se imaandari se kamai ke 5 raste dhundo mere skills ke hisaab se: ",
            "🛡 Audit" to "Meri website ka security audit karke poori list do: ",
            "🔎 Search" to "Internet par search karke batao: ",
            "🩺 Check" to "#check"
        )
        for ((label, prefill) in items) {
            val tv = TextView(this)
            tv.text = label
            tv.setTextColor(if (Skins.current().bgTop == 0) Color.WHITE else Skins.current().text)
            tv.setPadding(dp(14), dp(7), dp(14), dp(7))
            val g = GradientDrawable()
            g.setColor(Color.argb(60, 20, 24, 41))
            g.setStroke(dp(1), if (Skins.current().bgTop == 0) p.c1 else Skins.current().accent)
            g.cornerRadius = dp(18).toFloat()
            tv.background = g
            tv.setOnClickListener {
                when {
                    prefill.isEmpty() -> startActivity(Intent(this, TradingActivity::class.java))
                    prefill == "#check" -> { Hub.log("🩺 Sehat-jaanch:\n" + Scanner.report(this)); refreshHealth() }
                    else -> { etInput.setText(prefill); etInput.setSelection(prefill.length); etInput.requestFocus() }
                }
            }
            val lp = LinearLayout.LayoutParams(-2, -2)
            lp.setMargins(0, 0, dp(8), 0)
            chips.addView(tv, lp)
        }
    }

    private fun refreshHealth() {
        val acc = PhoneAccessibilityService.state(this)
        val msg = when {
            Hub.prefs.masterOn && acc != 2 -> PhoneAccessibilityService.statusLine(this)
            Hub.prefs.masterOn && Slots.available(Hub.prefs).isEmpty() -> "⚠ Koi AI API ready nahi - Settings me key daalo (tap)"
            else -> ""
        }
        tvHealth.text = msg + if (msg.isNotEmpty()) "  (tap: Permissions)" else ""
        tvHealth.visibility = if (msg.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun syncMasterUi() {
        val on = Hub.prefs.masterOn
        toggle.setChecked(on, false)
        orb.setActive(on)
        if (!on) tvStatus.text = "OFF"
        refreshHealth()
    }

    override fun onResume() {
        super.onResume()
        visible = true
        Hub.appVisible = true
        Hub.bubble.update()
        tvName.text = Hub.prefs.appName
        applyPalette()
        applySkin()
        orb.setBusy(Hub.engine.isBusy())
        tvStatus.text = if (Hub.prefs.masterOn) Hub.statusText else "OFF"
        Hub.engine.listener = this
        Hub.onMasterUi = { if (visible) syncMasterUi() }
        syncMasterUi()
        Hub.voice.onSpeakDone = {
            if (visible && Hub.prefs.autoListen && Hub.prefs.masterOn && !listening) startListening()
        }
        refreshLog()
        if (Hub.prefs.wakeAlways && !Hub.prefs.bool("mic_asked", false) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Hub.prefs.putBool("mic_asked", true)
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 12)
        }
        if (Hub.prefs.masterOn) Hub.startAgentService()
    }

    override fun onPause() {
        super.onPause()
        visible = false
        Hub.appVisible = false
        Hub.bubble.update()
        Hub.engine.listener = null
        Hub.onMasterUi = null
        Hub.voice.onSpeakDone = null
        stopListening()
    }

    override fun onDestroy() {
        voiceIn?.destroy()
        voiceIn = null
        super.onDestroy()
    }

    // ------------------------------------------------------------ chat
    private fun sendCurrent() {
        val text = etInput.text.toString().trim()
        if (text.isEmpty()) return
        etInput.setText("")
        // OFF ho to Hub.submitText khud ON kar deta hai (pehle sirf "ON dabao" bol kar chhod deta tha)
        Hub.submitText(text)
        tvStatus.text = "🧠 soch raha hoon..."
    }

    private fun refreshLog() {
        tvLog.text = Hub.logText()
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onLog() { runOnUiThread { refreshLog() } }
    override fun onBusy(busy: Boolean) { runOnUiThread { orb.setBusy(busy) } }
    override fun onStatus(text: String) { runOnUiThread { tvStatus.text = text } }

    // ------------------------------------------------------------ 3-dot menu
    private fun showMenu(anchor: View) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add(0, 6, 0, "📈  Trading Hub")
        pm.menu.add(0, 1, 0, "⚙  Settings")
        pm.menu.add(0, 2, 1, "🔐  Permissions")
        pm.menu.add(0, 7, 2, "🪟  Chhota panel (kisi bhi app ke upar)")
        pm.menu.add(0, 8, 3, "🩺  Sehat-jaanch")
        pm.menu.add(0, 3, 4, "⏹  Task roko (save rahega)")
        pm.menu.add(0, 4, 5, "🗑  Task bhulo")
        pm.menu.add(0, 5, 6, "🧹  Chat saaf")
        pm.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> startActivity(Intent(this, SettingsActivity::class.java))
                6 -> startActivity(Intent(this, TradingActivity::class.java))
                2 -> startActivity(Intent(this, PermissionsActivity::class.java))
                7 -> MiniPanel.show(false)
                8 -> { Hub.log("🩺 Sehat-jaanch:\n" + Scanner.report(this)); refreshHealth() }
                3 -> { Hub.engine.pause(); Hub.log("⏹ Task roka (save hai, ON/OFF ya message se aage badhega).") }
                4 -> Hub.engine.forgetTask()
                5 -> Hub.clearLog()
            }
            true
        }
        pm.show()
    }

    // ------------------------------------------------------------ voice input
    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 11)
            return
        }
        Hub.voice.stop()
        val v = voiceIn ?: VoiceIn(this).also { voiceIn = it }
        listening = true
        btnMic.alpha = 0.4f
        v.start(object : VoiceIn.CB {
            override fun onPartial(text: String) { etInput.setText(text); etInput.setSelection(etInput.text.length) }
            override fun onState(text: String) { tvStatus.text = text }
            override fun onFinal(text: String) {
                listening = false
                btnMic.alpha = 1f
                etInput.setText("")
                Hub.onHeard(text, !Hub.prefs.requireName)
                if (Hub.prefs.requireName) relisten()
            }

            override fun onFail(msg: String, needFallback: Boolean) {
                listening = false
                btnMic.alpha = 1f
                tvStatus.text = "⚠ $msg"
                Hub.log("🎙 $msg")
                if (needFallback) {
                    try { startActivity(Intent(this@MainActivity, VoiceActivity::class.java)) } catch (e: Exception) { }
                } else relisten()
            }
        })
    }

    private fun relisten() {
        etInput.postDelayed({
            if (visible && Hub.prefs.autoListen && Hub.prefs.requireName && Hub.prefs.masterOn && !listening) startListening()
        }, 700)
    }

    private fun stopListening() {
        if (listening) voiceIn?.stop()
        listening = false
        btnMic.alpha = 1f
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 11 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else if (requestCode == 12 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Hub.startAgentService()
        } else if (requestCode == 11) {
            Toast.makeText(this, "Mic permission ke bina voice nahi chalega", Toast.LENGTH_LONG).show()
        }
    }
}
