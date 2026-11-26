package com.myagent.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import kotlin.math.abs
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var mic: ImageView
    private lateinit var send: ImageView
    private lateinit var wave: WaveView
    private lateinit var dot: TextView
    private lateinit var scrim: View
    private lateinit var drawer: LinearLayout
    private lateinit var histBox: LinearLayout
    private lateinit var bigToggle: TextView
    private var drawerOpen = false
    private var pillView: View? = null
    private var brainSwitch: android.widget.Switch? = null
    private var vt: android.view.VelocityTracker? = null
    private var dragging = false
    private var tracking = false
    private var dX0 = 0f
    private var dY0 = 0f
    private var drawerW = 0
    private var voice: VoiceInput? = null
    private var asked = false

    private val chatCb: () -> Unit = { runOnUiThread { rebuild(); refreshHistory() } }
    private val stateCb: () -> Unit = { runOnUiThread { updateState() } }

    private fun d(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        window.statusBarColor = Color.parseColor("#05060B")
        window.navigationBarColor = Color.BLACK
        buildUi()
        requestBasics()
    }

    private fun buildUi() {
        val root = FrameLayout(this)
        root.fitsSystemWindows = true
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#05060B"), Color.parseColor("#070A16"), Color.parseColor("#0A1240"), Color.parseColor("#1A2E9A"))
        )

        wave = WaveView(this)
        root.addView(wave, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, d(230), Gravity.BOTTOM))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        root.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // top bar: menu (left), status (right)
        val top = FrameLayout(this)
        val menu = ImageView(this)
        menu.setImageResource(R.drawable.ic_menu)
        menu.setPadding(d(8), d(8), d(8), d(8))
        menu.setOnClickListener { openDrawer() }
        top.addView(menu, FrameLayout.LayoutParams(d(44), d(44), Gravity.START or Gravity.CENTER_VERTICAL).apply {
            leftMargin = d(14)
        })
        dot = Ui.tv(this, "", 12f, Color.parseColor("#8B90B5"))
        top.addView(dot, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL
        ).apply { rightMargin = d(18) })
        col.addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, d(60)))

        scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(d(14), d(4), d(14), d(12))
        scroll.addView(list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // input pill
        val pill = LinearLayout(this)
        pillView = pill
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER_VERTICAL
        pill.setPadding(d(10), d(8), d(10), d(8))
        pill.background = Ui.round(this, Color.parseColor("#0F1224"), 34, Color.parseColor("#2B3160"), 2)

        mic = ImageView(this)
        mic.setImageResource(R.drawable.ic_mic)
        mic.setPadding(d(9), d(9), d(9), d(9))
        mic.setOnClickListener { toggleMic() }
        pill.addView(mic, LinearLayout.LayoutParams(d(44), d(44)))

        input = EditText(this)
        input.hint = "Type or Speak..."
        input.setHintTextColor(Color.parseColor("#7A7F9A"))
        input.setTextColor(Color.WHITE)
        input.textSize = 16f
        input.background = null
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.maxLines = 4
        input.setPadding(d(8), d(8), d(8), d(8))
        pill.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        send = ImageView(this)
        send.setImageResource(R.drawable.ic_send)
        send.setPadding(d(12), d(12), d(12), d(12))
        val sg = GradientDrawable()
        sg.shape = GradientDrawable.OVAL
        sg.setColor(Color.parseColor("#5B4BF0"))
        send.background = sg
        send.setOnClickListener { onSend() }
        pill.addView(send, LinearLayout.LayoutParams(d(48), d(48)))

        val plp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        plp.leftMargin = d(14)
        plp.rightMargin = d(14)
        plp.bottomMargin = d(26)
        col.addView(pill, plp)

        buildDrawer(root)
        setContentView(root)
        updateState()
    }

    // ---------- side menu (like ChatGPT's) ----------

    private fun buildDrawer(root: FrameLayout) {
        drawerW = minOf((resources.displayMetrics.widthPixels * 0.84f).toInt(), d(380))

        scrim = View(this)
        scrim.setBackgroundColor(Color.parseColor("#AA000000"))
        scrim.visibility = View.GONE
        scrim.alpha = 0f
        scrim.setOnClickListener { closeDrawer() }
        root.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        drawer = LinearLayout(this)
        drawer.orientation = LinearLayout.VERTICAL
        drawer.setBackgroundColor(Color.parseColor("#0A0B12"))
        drawer.setPadding(d(18), d(14), d(18), d(14))
        drawer.isClickable = true
        drawer.visibility = View.GONE

        drawer.addView(Ui.tv(this, "My Agent", 26f, Color.WHITE, true))

        bigToggle = TextView(this)
        bigToggle.gravity = Gravity.CENTER
        bigToggle.textSize = 22f
        bigToggle.setTypeface(bigToggle.typeface, android.graphics.Typeface.BOLD)
        bigToggle.setTextColor(Color.WHITE)
        bigToggle.setOnClickListener { toggleAgent() }
        drawer.addView(bigToggle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, d(64)).apply {
            topMargin = d(14)
            bottomMargin = d(8)
        })

        val brainSw = android.widget.Switch(this)
        brainSw.text = "Phone ke AI apps use karo"
        brainSw.setTextColor(Color.WHITE)
        brainSw.textSize = 15f
        brainSw.isChecked = AppBrain.on(this)
        brainSw.setOnCheckedChangeListener { _, v -> AppBrain.setOn(this, v) }
        brainSwitch = brainSw
        drawer.addView(brainSw, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = d(4)
            bottomMargin = d(10)
        })

        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.VERTICAL
        sv.addView(inner, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        fun item(glyph: String, label: String, page: String) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, d(14), 0, d(14))
            row.addView(Ui.tv(this, glyph, 20f), LinearLayout.LayoutParams(d(44), ViewGroup.LayoutParams.WRAP_CONTENT))
            row.addView(Ui.tv(this, label, 18f, Color.WHITE, true))
            row.setOnClickListener {
                closeDrawer()
                startActivity(Intent(this, SettingsActivity::class.java).putExtra("page", page))
            }
            inner.addView(row)
        }
        item("🔑", "API keys", "api")
        item("🎙️", "Voice", "voice")
        item("🔗", "Connected apps", "connect")
        item("♿", "Accessibility & permissions", "perm")
        item("⚙️", "Agent settings", "agent")

        val line = View(this)
        line.setBackgroundColor(Color.parseColor("#262938"))
        inner.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            topMargin = d(10)
            bottomMargin = d(10)
        })
        inner.addView(Ui.tv(this, "Chats", 13f, Color.parseColor("#8B90B5"), true))
        histBox = LinearLayout(this)
        histBox.orientation = LinearLayout.VERTICAL
        inner.addView(histBox)

        drawer.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val newChat = TextView(this)
        newChat.text = "✎  New chat"
        newChat.gravity = Gravity.CENTER
        newChat.textSize = 17f
        newChat.setTypeface(newChat.typeface, android.graphics.Typeface.BOLD)
        newChat.setTextColor(Color.WHITE)
        newChat.background = Ui.round(this, Color.parseColor("#3B6CFF"), 28)
        newChat.setOnClickListener {
            Engine.cancel()
            Engine.resetMemory()
            Chat.newChat()
            closeDrawer()
        }
        drawer.addView(newChat, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, d(54)).apply { topMargin = d(10) })

        root.addView(drawer, FrameLayout.LayoutParams(drawerW, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
        drawer.translationX = -drawerW.toFloat()
        paintToggle()
        refreshHistory()
    }

    private fun refreshHistory() {
        if (!::histBox.isInitialized) return
        histBox.removeAllViews()
        val cur = Chat.currentId()
        val h = Chat.history()
        if (h.isEmpty()) {
            histBox.addView(Ui.tv(this, "No chats yet", 14f, Color.parseColor("#6B7090")), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = d(10) })
            return
        }
        for ((id, title) in h) {
            val t = Ui.tv(this, title, 16f, if (id == cur) Color.parseColor("#9D8CFF") else Color.WHITE)
            t.setPadding(0, d(12), 0, d(12))
            t.maxLines = 1
            t.ellipsize = android.text.TextUtils.TruncateAt.END
            t.setOnClickListener {
                Engine.cancel()
                Engine.resetMemory()
                Chat.open(id)
                closeDrawer()
            }
            histBox.addView(t)
        }
    }

    private fun paintToggle() {
        brainSwitch?.isChecked = AppBrain.on(this)
        val on = Prefs.enabled(this)
        val g = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            if (on) intArrayOf(Color.parseColor("#00C853"), Color.parseColor("#00B0FF"))
            else intArrayOf(Color.parseColor("#FF1744"), Color.parseColor("#FF9100"))
        )
        g.cornerRadius = d(40).toFloat()
        bigToggle.background = g
        bigToggle.text = if (on) "ON" else "OFF"
    }

    private fun toggleAgent() {
        val on = !Prefs.enabled(this)
        Prefs.setEnabled(this, on)
        paintToggle()
        updateState()
        if (on) {
            OverlayService.start(this)
        } else {
            Engine.cancel()
            Voice.stop()
            OverlayService.start(this)
        }
        Chat.fireState()
    }

    private fun openDrawer() {
        paintToggle()
        refreshHistory()
        scrim.visibility = View.VISIBLE
        drawer.visibility = View.VISIBLE
        settle(true)
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun closeDrawer() {
        if (!drawerOpen && drawer.visibility != View.VISIBLE) return
        settle(false)
    }

    /** Animate the drawer from wherever it is now to fully open or fully closed. */
    private fun settle(open: Boolean) {
        drawerOpen = open
        val target = if (open) 0f else -drawerW.toFloat()
        scrim.visibility = View.VISIBLE
        drawer.visibility = View.VISIBLE
        scrim.animate().alpha(if (open) 1f else 0f).setDuration(180).withEndAction {
            if (!drawerOpen) scrim.visibility = View.GONE
        }.start()
        drawer.animate().translationX(target).setDuration(200).withEndAction {
            if (!drawerOpen) drawer.visibility = View.GONE
        }.start()
    }

    private fun dragTo(x: Float) {
        val t = x.coerceIn(-drawerW.toFloat(), 0f)
        drawer.animate().cancel()
        scrim.animate().cancel()
        drawer.visibility = View.VISIBLE
        scrim.visibility = View.VISIBLE
        drawer.translationX = t
        scrim.alpha = 1f + t / drawerW
    }

    /** Swipe in from the side to open, swipe back to close. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val slop = d(12).toFloat()
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dX0 = ev.rawX
                dY0 = ev.rawY
                dragging = false
                val pv = pillView
                var overPill = false
                if (pv != null) {
                    val loc = IntArray(2)
                    pv.getLocationOnScreen(loc)
                    overPill = ev.rawY >= loc[1]
                }
                tracking = drawerOpen || !overPill
                vt?.recycle()
                vt = android.view.VelocityTracker.obtain()
                vt?.addMovement(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                if (tracking) {
                    vt?.addMovement(ev)
                    val dx = ev.rawX - dX0
                    val dy = ev.rawY - dY0
                    if (!dragging && abs(dx) > slop && abs(dx) > abs(dy) * 1.5f &&
                        ((!drawerOpen && dx > 0) || (drawerOpen && dx < 0))
                    ) {
                        dragging = true
                        val c = MotionEvent.obtain(ev)
                        c.action = MotionEvent.ACTION_CANCEL
                        super.dispatchTouchEvent(c)
                        c.recycle()
                    }
                    if (dragging) {
                        dragTo((if (drawerOpen) 0f else -drawerW.toFloat()) + dx)
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    vt?.addMovement(ev)
                    vt?.computeCurrentVelocity(1000)
                    val vx = vt?.xVelocity ?: 0f
                    dragging = false
                    tracking = false
                    val open = when {
                        vx > 900f -> true
                        vx < -900f -> false
                        else -> drawer.translationX > -drawerW / 2f
                    }
                    if (open) paintToggle()
                    if (open) refreshHistory()
                    settle(open)
                    vt?.recycle()
                    vt = null
                    return true
                }
                tracking = false
                vt?.recycle()
                vt = null
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (drawerOpen) closeDrawer() else super.onBackPressed()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerW = minOf((resources.displayMetrics.widthPixels * 0.84f).toInt(), d(380))
        drawer.layoutParams = (drawer.layoutParams as FrameLayout.LayoutParams).apply { width = drawerW }
        if (!drawerOpen) drawer.translationX = -drawerW.toFloat()
        rebuild()
    }

    override fun onStart() {
        super.onStart()
        Chat.listeners.add(chatCb)
        Chat.state.add(stateCb)
        rebuild()
        refreshHistory()
        updateState()
    }

    override fun onStop() {
        Chat.listeners.remove(chatCb)
        Chat.state.remove(stateCb)
        voice?.cancel()
        voice = null
        resetMic()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        paintToggle()
        updateState()
        OverlayService.start(this)
        if (!asked && !setupComplete()) {
            asked = true
            val needOverlay = !Settings.canDrawOverlays(this)
            val msg = if (needOverlay)
                "Side wala floating icon (jise dabake bolte ho) dikhane ke liye 'Display over other apps' ON karna hoga. Open dabao, My Agent ko ON karo, phir wapas aao."
            else "Agent ko Microphone aur Accessibility permission chahiye. Open dabao."
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Floating icon setup")
                .setMessage(msg)
                .setPositiveButton("Open") { _, _ ->
                    requestBasics()
                    if (needOverlay) {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    } else {
                        startActivity(Intent(this, SettingsActivity::class.java).putExtra("page", "perm"))
                    }
                }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    private fun setupComplete(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            Settings.canDrawOverlays(this) && AgentAccessibilityService.enabled(this)

    private fun requestBasics() {
        val need = ArrayList<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) need.add(Manifest.permission.POST_NOTIFICATIONS)
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 7)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        OverlayService.start(this)
    }

    // ---------- chat ----------

    private fun rebuild() {
        list.removeAllViews()
        val maxW = (resources.displayMetrics.widthPixels * 0.8f).toInt()
        for (item in Chat.snapshot()) {
            val t = TextView(this)
            t.text = item.text
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = d(5)
            lp.bottomMargin = d(5)
            when (item.kind) {
                Chat.USER -> {
                    t.setTextColor(Color.WHITE)
                    t.textSize = 15f
                    t.background = Ui.round(this, Color.parseColor("#5B4BF0"), 18)
                    t.setPadding(d(14), d(10), d(14), d(10))
                    lp.gravity = Gravity.END
                    t.maxWidth = maxW
                }
                Chat.AGENT -> {
                    t.setTextColor(Color.parseColor("#E8EAFB"))
                    t.textSize = 15f
                    t.background = Ui.round(this, Color.parseColor("#151933"), 18, Color.parseColor("#262B52"))
                    t.setPadding(d(14), d(10), d(14), d(10))
                    lp.gravity = Gravity.START
                    t.maxWidth = maxW
                    t.setTextIsSelectable(true)
                }
                else -> {
                    t.setTextColor(Color.parseColor("#7F84A8"))
                    t.textSize = 12f
                    lp.gravity = Gravity.CENTER_HORIZONTAL
                    t.maxWidth = (maxW * 1.1f).toInt()
                }
            }
            list.addView(t, lp)
        }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun updateState() {
        val on = Prefs.enabled(this)
        val running = Engine.running
        dot.text = if (!on) "OFF" else if (running) "working…" else ""
        send.setImageResource(if (running && input.text.isNullOrBlank()) R.drawable.ic_stop else R.drawable.ic_send)
        wave.fast = running
    }

    private fun onSend() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) {
            if (Engine.running) Engine.cancel()
            return
        }
        input.setText("")
        Voice.stop()
        Engine.submit(t)
    }

    // ---------- voice ----------

    private fun toggleMic() {
        if (voice != null) {
            voice?.stop()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestBasics()
            return
        }
        Voice.stop()
        mic.setColorFilter(Color.parseColor("#9D8CFF"))
        input.hint = "Listening..."
        val vi = VoiceInput(this, object : VoiceInput.Callback {
            override fun onPartial(text: String) {
                runOnUiThread { input.setText(text) }
            }

            override fun onFinal(text: String) {
                runOnUiThread {
                    input.setText("")
                    Engine.submit(text)
                }
            }

            override fun onDone(error: String?) {
                runOnUiThread {
                    voice = null
                    resetMic()
                    if (error != null) Chat.add(Chat.INFO, error)
                }
            }
        })
        voice = vi
        vi.start()
    }

    private fun resetMic() {
        mic.clearColorFilter()
        input.hint = "Type or Speak..."
    }
}
