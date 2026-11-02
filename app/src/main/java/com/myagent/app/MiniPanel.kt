package com.myagent.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Google-Assistant jaisa chhota panel: kisi bhi app ke upar. Isme status, jawab, type-box aur mic hota hai.
 * Poori app kholne ki zarurat nahi - doosri app ka kaam disturb nahi hota.
 */
object MiniPanel {
    private var box: LinearLayout? = null
    private var lp: WindowManager.LayoutParams? = null
    private var orb: OrbView? = null
    private var tvStatus: TextView? = null
    private var tvReply: TextView? = null
    private var et: EditText? = null
    private var mic: TextView? = null
    private var voice: VoiceIn? = null
    private var hideRunnable: Runnable? = null

    private val ctx: Context get() = Hub.app
    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()
    private val wm: WindowManager get() = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun isShown(): Boolean = box != null

    fun show(listen: Boolean) {
        Hub.mainHandler.post {
            if (!Settings.canDrawOverlays(ctx)) {
                Hub.log("⚠ Panel ke liye 'Display over other apps' permission chahiye (Permissions screen).")
                openMain()
                return@post
            }
            if (box == null) build()
            cancelHide()
            if (listen) startMic()
        }
    }

    fun hide() {
        Hub.mainHandler.post {
            cancelHide()
            try { voice?.destroy() } catch (e: Exception) { }
            voice = null
            val b = box
            if (b != null) try { wm.removeView(b) } catch (e: Exception) { }
            box = null; orb = null; tvStatus = null; tvReply = null; et = null; mic = null; lp = null
        }
    }

    fun setStatus(s: String) {
        Hub.mainHandler.post { tvStatus?.text = s.take(90) }
    }

    fun setReply(s: String) {
        Hub.mainHandler.post {
            tvReply?.text = s.take(700)
            if (box != null && !Hub.engine.isBusy()) scheduleHide(15000)
        }
    }

    fun setBusy(b: Boolean) {
        Hub.mainHandler.post { orb?.setBusy(b) }
    }

    private fun scheduleHide(ms: Long) {
        cancelHide()
        val r = Runnable { hide() }
        hideRunnable = r
        Hub.mainHandler.postDelayed(r, ms)
    }

    private fun cancelHide() {
        hideRunnable?.let { Hub.mainHandler.removeCallbacks(it) }
        hideRunnable = null
    }

    private fun openMain() {
        try {
            ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        } catch (e: Exception) { }
    }

    private fun setFocusable(on: Boolean) {
        val p = lp ?: return
        val b = box ?: return
        p.flags = if (on) WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        try { wm.updateViewLayout(b, p) } catch (e: Exception) { }
    }

    private fun build() {
        val pal = Palettes.current()
        val b = LinearLayout(ctx)
        b.orientation = LinearLayout.VERTICAL
        b.setPadding(dp(12), dp(10), dp(12), dp(10))
        val bg = GradientDrawable()
        bg.setColor(Color.argb(238, 12, 15, 30))
        bg.cornerRadius = dp(20).toFloat()
        bg.setStroke(dp(2), pal.c1)
        b.background = bg

        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val o = OrbView(ctx)
        o.lowPower = true
        o.setActive(true)
        o.setBusy(Hub.engine.isBusy())
        head.addView(o, LinearLayout.LayoutParams(dp(40), dp(40)))
        val st = TextView(ctx)
        st.setTextColor(pal.c2)
        st.textSize = 12f
        st.maxLines = 2
        st.text = Hub.statusText
        val slp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        slp.leftMargin = dp(8)
        head.addView(st, slp)
        val open = chip("⤢")
        open.setOnClickListener { openMain(); hide() }
        head.addView(open)
        val x = chip("✕")
        x.setOnClickListener { hide() }
        head.addView(x)
        b.addView(head)

        val reply = TextView(ctx)
        reply.setTextColor(Color.rgb(221, 227, 255))
        reply.textSize = 14f
        reply.text = ""
        val sv = ScrollView(ctx)
        sv.addView(reply)
        val slp2 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        slp2.topMargin = dp(6)
        b.addView(sv, slp2)

        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val e = EditText(ctx)
        e.hint = "Type ya mic..."
        e.setHintTextColor(Color.rgb(127, 137, 176))
        e.setTextColor(Color.WHITE)
        e.textSize = 14f
        e.maxLines = 2
        e.imeOptions = EditorInfo.IME_ACTION_SEND
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        e.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_DOWN) setFocusable(true)
            false
        }
        e.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEND) { send(); true } else false
        }
        row.addView(e, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val m = chip("🎙")
        m.setOnClickListener { if (voice?.listening == true) stopMic() else startMic() }
        row.addView(m)
        val s = chip("➤")
        s.setOnClickListener { send() }
        row.addView(s)
        val rlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        rlp.topMargin = dp(6)
        b.addView(row, rlp)

        val dm = ctx.resources.displayMetrics
        val p = WindowManager.LayoutParams(
            (dm.widthPixels * 0.92f).toInt().coerceAtMost(dp(420)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        p.y = dp(48)
        p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING

        // header ghasit kar jagah badlo
        var dy = 0f
        var sy = 0
        head.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { dy = ev.rawY; sy = p.y; true }
                MotionEvent.ACTION_MOVE -> {
                    p.y = (sy + (ev.rawY - dy).toInt()).coerceIn(0, dm.heightPixels - dp(120))
                    try { wm.updateViewLayout(b, p) } catch (ex: Exception) { }
                    true
                }
                else -> true
            }
        }
        try {
            wm.addView(b, p)
            box = b; lp = p; orb = o; tvStatus = st; tvReply = reply; et = e; mic = m
        } catch (ex: Exception) {
            box = null
            Hub.log("⚠ Panel nahi khula: " + (ex.message ?: ""))
        }
    }

    private fun chip(t: String): TextView {
        val tv = TextView(ctx)
        tv.text = t
        tv.textSize = 18f
        tv.setTextColor(Color.WHITE)
        tv.gravity = Gravity.CENTER
        tv.setPadding(dp(10), dp(6), dp(10), dp(6))
        return tv
    }

    private fun send() {
        val e = et ?: return
        val t = e.text.toString().trim()
        if (t.isEmpty()) return
        e.setText("")
        setFocusable(false)
        Hub.submitText(t)
        setStatus("🧠 soch raha hoon...")
    }

    private fun startMic() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setStatus("🎙 Mic permission chahiye - app me Permissions kholo")
            try { ctx.startActivity(Intent(ctx, PermissionsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
            return
        }
        Hub.voice.stop()
        val v = voice ?: VoiceIn(ctx).also { voice = it }
        v.start(object : VoiceIn.CB {
            override fun onPartial(text: String) { et?.setText(text) }
            override fun onFinal(text: String) {
                et?.setText("")
                setStatus("🧑 " + text.take(60))
                Hub.onHeard(text, true)
            }
            override fun onState(text: String) { setStatus(text) }
            override fun onFail(msg: String, needFallback: Boolean) {
                setStatus("⚠ $msg")
                if (needFallback) {
                    try { ctx.startActivity(Intent(ctx, VoiceActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                }
            }
        })
    }

    private fun stopMic() { voice?.stop(); setStatus("Ready") }
}
