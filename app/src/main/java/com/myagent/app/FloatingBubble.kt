package com.myagent.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Chhota floating orb: doosri app me kaam karte waqt dikhta hai.
 * Tap = app kholo, lamba dabao = task ruko/chalao, ghasito = jagah badlo.
 */
class FloatingBubble(private val app: Context) {
    private val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var box: LinearLayout? = null
    private var orb: OrbView? = null
    private var tv: TextView? = null
    private var lp: WindowManager.LayoutParams? = null

    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false
    private var downT = 0L

    private fun dp(v: Int): Int = (v * app.resources.displayMetrics.density).toInt()

    private fun shouldShow(): Boolean =
        Hub.prefs.masterOn && Hub.prefs.floatingOn && !Hub.appVisible && Settings.canDrawOverlays(app)

    fun update() {
        main.post { if (shouldShow()) show() else hide() }
    }

    fun setStatus(s: String) {
        main.post { tv?.text = s.take(60) }
    }

    fun setBusy(b: Boolean) {
        main.post { orb?.setBusy(b) }
    }

    private fun show() {
        if (box != null) return
        val d = dp(64)
        val o = OrbView(app)
        o.lowPower = true
        o.setActive(true)
        o.setBusy(Hub.engine.isBusy())
        val t = TextView(app)
        t.textSize = 10f
        t.setTextColor(Color.WHITE)
        t.gravity = Gravity.CENTER
        t.maxWidth = dp(140)
        t.maxLines = 2
        t.setShadowLayer(4f, 0f, 0f, Color.BLACK)
        t.text = Hub.statusText
        val b = LinearLayout(app)
        b.orientation = LinearLayout.VERTICAL
        b.gravity = Gravity.CENTER_HORIZONTAL
        b.addView(o, LinearLayout.LayoutParams(d, d))
        b.addView(t)

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = dp(8)
        p.y = dp(180)

        b.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = p.x; startY = p.y
                    moved = false; downT = SystemClock.uptimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (Math.abs(dx) > 12 || Math.abs(dy) > 12) moved = true
                    if (moved) {
                        p.x = startX + dx.toInt()
                        p.y = startY + dy.toInt()
                        try { wm.updateViewLayout(b, p) } catch (ex: Exception) { }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        if (SystemClock.uptimeMillis() - downT > 600) togglePause() else if (Hub.prefs.panelOn) MiniPanel.show(true) else openApp()
                    }
                    true
                }
                else -> false
            }
        }
        try {
            wm.addView(b, p)
            box = b; orb = o; tv = t; lp = p
        } catch (e: Exception) {
            box = null
        }
    }

    private fun togglePause() {
        if (Hub.engine.isBusy()) {
            Hub.engine.pause()
            setStatus("⏸ ruka (save hai)")
        } else {
            Hub.engine.resumeIfNeeded()
        }
    }

    private fun openApp() {
        val i = Intent(app, MainActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        app.startActivity(i)
    }

    private fun hide() {
        val b = box ?: return
        try { wm.removeView(b) } catch (e: Exception) { }
        box = null; orb = null; tv = null; lp = null
    }
}
