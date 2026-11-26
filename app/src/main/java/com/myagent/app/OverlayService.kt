package com.myagent.app

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Floating icon that works over every app.
 *  - press & hold: walkie-talkie. Listens only while held; on release the words go to the agent.
 *  - tap: small type box.
 *  - drag: move it (snaps to the screen edge).
 * Also shows the allow/deny panel for sensitive actions.
 */
class OverlayService : Service() {

    companion object {
        @Volatile
        var inst: OverlayService? = null
        private const val CH = "agent"
        private const val ACT_STOP = "com.myagent.app.STOP"

        fun start(c: Context) {
            if (!Settings.canDrawOverlays(c)) return
            try {
                c.startForegroundService(Intent(c, OverlayService::class.java))
            } catch (e: Exception) {
            }
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, OverlayService::class.java))
        }

        /** Blocks the calling (engine) thread until the user taps Allow/Deny. Denies on timeout. */
        fun confirm(text: String): Boolean {
            val s = inst ?: return false
            val latch = CountDownLatch(1)
            val ok = AtomicBoolean(false)
            s.handler.post {
                s.showConfirm(text) { v ->
                    ok.set(v)
                    latch.countDown()
                }
            }
            Voice.speak("Permission chahiye. Allow ya deny dabao.")
            val answered = latch.await(60, TimeUnit.SECONDS)
            s.handler.post { s.hideConfirm() }
            return answered && ok.get()
        }

        fun dismissConfirm() {
            val s = inst ?: return
            s.handler.post { s.hideConfirm() }
        }
    }

    val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var root: LinearLayout? = null
    private var rootLp: WindowManager.LayoutParams? = null
    private var statusTv: TextView? = null
    private var iconBox: FrameLayout? = null
    private var panel: View? = null
    private var confirmView: View? = null
    private var voice: VoiceInput? = null
    private var talking = false
    private var iconBg: GradientDrawable? = null
    private var pulse: ObjectAnimator? = null
    private val hideStatus = Runnable { statusTv?.visibility = View.GONE }
    private val stateCb: () -> Unit = { handler.post { refreshState() } }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        inst = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        Chat.state.add(stateCb)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACT_STOP) Engine.cancel()
        if (!startFg()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (root == null) {
            if (!Settings.canDrawOverlays(this)) {
                stopSelf()
                return START_NOT_STICKY
            }
            buildIcon()
            paintIcon()
        }
        return START_STICKY
    }

    private fun startFg(): Boolean {
        return try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH, "Agent", NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            val stop = PendingIntent.getService(
                this, 1, Intent(this, OverlayService::class.java).setAction(ACT_STOP), PendingIntent.FLAG_IMMUTABLE
            )
            val n = Notification.Builder(this, CH)
                .setSmallIcon(R.drawable.ic_sparkle)
                .setContentTitle("My Agent")
                .setContentText("Active. Hold the floating icon to talk.")
                .setContentIntent(open)
                .addAction(
                    Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_sparkle), "Stop task", stop)
                        .build()
                )
                .setOngoing(true)
                .build()
            val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            startForeground(
                1, n,
                if (mic) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun d(v: Int) = Ui.dp(this, v)

    private fun overlayType() = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    private fun buildIcon() {
        val size = d(54)
        val box = FrameLayout(this)
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#7C5CFF"), Color.parseColor("#2D7BFF"))
        )
        bg.shape = GradientDrawable.OVAL
        iconBg = bg
        box.background = bg
        box.elevation = d(6).toFloat()
        box.alpha = 0.88f
        val iv = ImageView(this)
        iv.setImageResource(R.drawable.ic_sparkle)
        box.addView(iv, FrameLayout.LayoutParams(d(28), d(28), Gravity.CENTER))
        iconBox = box

        val tv = TextView(this)
        tv.setTextColor(Color.WHITE)
        tv.textSize = 12f
        tv.maxWidth = d(230)
        tv.setPadding(d(12), d(7), d(12), d(7))
        tv.background = Ui.round(this, Color.parseColor("#EE101428"), 14)
        tv.visibility = View.GONE
        statusTv = tv

        val r = LinearLayout(this)
        r.orientation = LinearLayout.VERTICAL
        r.gravity = Gravity.CENTER_HORIZONTAL
        val tlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tlp.bottomMargin = d(6)
        r.addView(tv, tlp)
        r.addView(box, LinearLayout.LayoutParams(size, size))
        root = r

        val dm = resources.displayMetrics
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = Prefs.getInt(this, "ox", dm.widthPixels - size)
        lp.y = Prefs.getInt(this, "oy", (dm.heightPixels * 0.45f).toInt())
        rootLp = lp

        val slop = d(10).toFloat()
        box.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0f
            var sy = 0f
            var ox = 0
            var oy = 0
            var moved = false
            var longFired = false
            val longRun = Runnable {
                if (!moved) {
                    longFired = true
                    if (Prefs.enabled(this@OverlayService)) startTalk()
                    else showStatus("Agent OFF. Tap karke ON karo", 2500)
                }
            }

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = e.rawX
                        sy = e.rawY
                        ox = lp.x
                        oy = lp.y
                        moved = false
                        longFired = false
                        handler.postDelayed(longRun, 280)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - sx
                        val dy = e.rawY - sy
                        if (!longFired && (abs(dx) > slop || abs(dy) > slop)) {
                            moved = true
                            handler.removeCallbacks(longRun)
                        }
                        if (moved) {
                            lp.x = (ox + dx).toInt()
                            lp.y = (oy + dy).toInt()
                            try {
                                wm.updateViewLayout(r, lp)
                            } catch (x: Exception) {
                            }
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(longRun)
                        if (longFired) {
                            if (talking) endTalk()
                        } else if (moved) {
                            snap()
                        } else if (e.actionMasked == MotionEvent.ACTION_UP) {
                            if (Prefs.enabled(this@OverlayService)) {
                                togglePanel()
                            } else {
                                Prefs.setEnabled(this@OverlayService, true)
                                Chat.fireState()
                                showStatus("Agent ON", 1500)
                            }
                        }
                    }
                }
                return true
            }
        })
        try {
            wm.addView(r, lp)
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun snap() {
        val lp = rootLp ?: return
        val r = root ?: return
        val w = resources.displayMetrics.widthPixels
        val iconW = d(54)
        lp.x = if (lp.x + iconW / 2 < w / 2) 0 else w - iconW
        lp.y = lp.y.coerceIn(0, resources.displayMetrics.heightPixels - d(120))
        try {
            wm.updateViewLayout(r, lp)
        } catch (e: Exception) {
        }
        Prefs.putInt(this, "ox", lp.x)
        Prefs.putInt(this, "oy", lp.y)
    }

    // ---------- push to talk ----------

    private fun showStatus(t: String, autoHideMs: Long = 0) {
        val tv = statusTv ?: return
        handler.removeCallbacks(hideStatus)
        tv.text = t
        tv.visibility = View.VISIBLE
        if (autoHideMs > 0) handler.postDelayed(hideStatus, autoHideMs)
    }

    private fun startTalk() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission chahiye", Toast.LENGTH_SHORT).show()
            return
        }
        Voice.stop()
        hidePanel()
        iconBox?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        talking = true
        showStatus("Listening…")
        startPulse()
        voice?.cancel()
        val vi = VoiceInput(this, object : VoiceInput.Callback {
            override fun onPartial(text: String) {
                showStatus(text.takeLast(80))
            }

            override fun onFinal(text: String) {
                showStatus(text.takeLast(80), 2500)
                Engine.submit(text)
            }

            override fun onDone(error: String?) {
                talking = false
                stopPulse()
                if (error != null) showStatus(error, 3000)
                else if (!Engine.running && statusTv?.text == "Listening…") showStatus("Didn't catch that", 1500)
            }
        })
        voice = vi
        vi.start()
    }

    private fun endTalk() {
        iconBox?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        showStatus("…")
        voice?.stop()
    }

    private fun startPulse() {
        val b = iconBox ?: return
        pulse?.cancel()
        val a = ObjectAnimator.ofPropertyValuesHolder(
            b,
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.25f),
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.25f)
        )
        a.duration = 450
        a.repeatCount = ValueAnimator.INFINITE
        a.repeatMode = ValueAnimator.REVERSE
        a.start()
        pulse = a
        b.alpha = 1f
    }

    private fun stopPulse() {
        pulse?.cancel()
        pulse = null
        iconBox?.scaleX = 1f
        iconBox?.scaleY = 1f
        iconBox?.alpha = 0.88f
    }

    private fun paintIcon() {
        val bg = iconBg ?: return
        if (Prefs.enabled(this)) {
            bg.colors = intArrayOf(Color.parseColor("#7C5CFF"), Color.parseColor("#2D7BFF"))
            if (!talking && !Engine.running) iconBox?.alpha = 0.88f
        } else {
            bg.colors = intArrayOf(Color.parseColor("#555560"), Color.parseColor("#33333A"))
            iconBox?.alpha = 0.6f
        }
    }

    private fun refreshState() {
        paintIcon()
        if (!Prefs.enabled(this)) {
            stopPulse()
            iconBox?.alpha = 0.6f
            return
        }
        if (Engine.running) {
            if (!talking) {
                showStatus(Engine.status.ifBlank { "Working…" }.takeLast(80))
                startPulse()
            }
        } else if (!talking) {
            stopPulse()
            val s = Engine.status
            if (s.isNotBlank() && statusTv?.visibility == View.VISIBLE) showStatus(s.takeLast(80), 5000)
        }
    }

    // ---------- type box ----------

    private fun togglePanel() {
        if (panel != null) hidePanel() else showPanel()
    }

    private fun showPanel() {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(d(14), d(8), d(8), d(8))
        row.background = Ui.round(this, Color.parseColor("#F00E1122"), 28, Color.parseColor("#2B3160"))

        val et = EditText(this)
        et.hint = "Type to agent…"
        et.setHintTextColor(Color.parseColor("#7A7F9A"))
        et.setTextColor(Color.WHITE)
        et.background = null
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        et.maxLines = 3
        row.addView(et, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        fun circle(res: Int, color: String, onClick: () -> Unit): ImageView {
            val b = ImageView(this)
            b.setImageResource(res)
            b.setPadding(d(10), d(10), d(10), d(10))
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            g.setColor(Color.parseColor(color))
            b.background = g
            b.setOnClickListener { onClick() }
            val lp = LinearLayout.LayoutParams(d(42), d(42))
            lp.leftMargin = d(6)
            row.addView(b, lp)
            return b
        }
        circle(R.drawable.ic_send, "#5B4BF0") {
            val t = et.text.toString().trim()
            hidePanel()
            if (t.isNotEmpty()) Engine.submit(t)
        }
        circle(R.drawable.ic_stop, "#3A2030") {
            Engine.cancel()
            hidePanel()
        }

        val dm = resources.displayMetrics
        val lp = WindowManager.LayoutParams(
            dm.widthPixels - d(24), ViewGroup.LayoutParams.WRAP_CONTENT, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.y = d(12)
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        try {
            wm.addView(row, lp)
            panel = row
            et.requestFocus()
            handler.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
            }, 200)
        } catch (e: Exception) {
        }
    }

    private fun hidePanel() {
        val p = panel ?: return
        try {
            wm.removeView(p)
        } catch (e: Exception) {
        }
        panel = null
    }

    // ---------- allow / deny ----------

    fun showConfirm(text: String, result: (Boolean) -> Unit) {
        hideConfirm()
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(d(20), d(18), d(20), d(14))
        box.background = Ui.round(this, Color.parseColor("#F2101428"), 20, Color.parseColor("#3A4080"), 2)
        val msg = Ui.tv(this, text, 16f)
        box.addView(msg)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.END
        val deny = Ui.btn(this, "Deny") { result(false) }
        val allow = Ui.btn(this, "Allow") { result(true) }
        allow.background = Ui.round(this, Color.parseColor("#2E9E5B"), 12)
        deny.background = Ui.round(this, Color.parseColor("#8E2B3A"), 12)
        val blp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        blp.leftMargin = d(10)
        blp.topMargin = d(14)
        row.addView(deny, blp)
        row.addView(allow, blp)
        box.addView(row)

        val dm = resources.displayMetrics
        val lp = WindowManager.LayoutParams(
            (dm.widthPixels * 0.88f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.CENTER
        try {
            wm.addView(box, lp)
            confirmView = box
        } catch (e: Exception) {
            result(false)
        }
    }

    fun hideConfirm() {
        val v = confirmView ?: return
        try {
            wm.removeView(v)
        } catch (e: Exception) {
        }
        confirmView = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        hidePanel()
        hideConfirm()
        voice?.cancel()
        voice = null
        pulse?.cancel()
        root?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
            }
        }
        root = null
        Chat.state.remove(stateCb)
        inst = null
        super.onDestroy()
    }
}
