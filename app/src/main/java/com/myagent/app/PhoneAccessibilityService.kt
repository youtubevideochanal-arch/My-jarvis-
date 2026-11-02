package com.myagent.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Android-level control: screen padhna, tap/swipe/type, app kholna, back/home, screenshot.
 * Ye service tabhi chalti hai jab aap Settings -> Accessibility me ise ON karo.
 */
class PhoneAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        connectedAt = System.currentTimeMillis()
        try { Hub.bubble.update() } catch (e: Exception) { }
        try { Hub.log("✅ Accessibility connected - ab main screen dekh/chala sakta hoon.") } catch (e: Exception) { }
    }

    /** Active window ka root; agar apna overlay/panel focus me ho to doosri app ki window dhundho. */
    private fun bestRoot(): AccessibilityNodeInfo? {
        val r = rootInActiveWindow
        if (r != null && r.packageName?.toString() != packageName) return r
        try {
            val ws = windows
            var fallback: AccessibilityNodeInfo? = r
            for (w in ws) {
                val root = w.root ?: continue
                val pkg = root.packageName?.toString() ?: continue
                if (pkg == packageName) continue
                if (pkg == "com.android.systemui" && w.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) {
                    if (fallback == null) fallback = root
                    continue
                }
                if (w.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) return root
                if (fallback == null) fallback = root
            }
            return fallback
        } catch (e: Exception) {
            return r
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // ------------------------------------------------------------ screen padhna
    @Volatile var maxElements = 70
    private val marks = ArrayList<IntArray>()

    /** [n] numbered element ka center (screen dump ke baad). */
    fun markPoint(id: Int): IntArray? = synchronized(marks) { marks.getOrNull(id - 1) }

    fun dumpScreen(): String {
        val root = bestRoot() ?: return "(screen abhi available nahi - 1 second ruk kar dobara dekho)"
        val sb = StringBuilder()
        sb.append("APP: ").append(root.packageName).append('\n')
        val dm = resources.displayMetrics
        sb.append("SCREEN_SIZE: ").append(dm.widthPixels).append('x').append(dm.heightPixels).append('\n')
        val counter = IntArray(1)
        synchronized(marks) { marks.clear() }
        walk(root, sb, counter, 0)
        if (counter[0] == 0) sb.append("(koi readable element nahi mila - 'look' action try karo)\n")
        return sb.toString()
    }

    private fun walk(n: AccessibilityNodeInfo?, sb: StringBuilder, counter: IntArray, depth: Int) {
        if (n == null || counter[0] >= maxElements || depth > 30) return
        if (n.isVisibleToUser) {
            val text = if (n.isPassword) "" else n.text?.toString()?.trim().orEmpty()
            val desc = n.contentDescription?.toString()?.trim().orEmpty()
            val clickable = n.isClickable || n.isLongClickable
            val editable = n.isEditable
            if (text.isNotEmpty() || desc.isNotEmpty() || clickable || editable) {
                val r = Rect()
                n.getBoundsInScreen(r)
                val idx = synchronized(marks) { marks.add(intArrayOf(r.centerX(), r.centerY())); marks.size }
                sb.append('[').append(idx).append("] ")
                if (text.isNotEmpty()) sb.append('"').append(text.take(60)).append('"')
                if (desc.isNotEmpty() && desc != text) sb.append(" desc=\"").append(desc.take(40)).append('"')
                if (n.isPassword) sb.append(" [password field]")
                if (clickable) sb.append(" [click]")
                if (editable) sb.append(" [edit]")
                if (n.isScrollable) sb.append(" [scroll]")
                sb.append('\n')
                counter[0]++
            }
        }
        for (i in 0 until n.childCount) walk(n.getChild(i), sb, counter, depth + 1)
    }

    // ------------------------------------------------------------ click / type / scroll
    fun clickText(q: String): Boolean {
        val root = bestRoot() ?: return false
        val needle = q.trim().lowercase()
        if (needle.isEmpty()) return false
        val found = ArrayList<AccessibilityNodeInfo>()
        collect(root, needle, true, found, 0)
        if (found.isEmpty()) collect(root, needle, false, found, 0)
        for (node in found) {
            var c: AccessibilityNodeInfo? = node
            while (c != null && !c.isClickable) c = c.parent
            if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            // clickable parent nahi mila -> us jagah par tap
            val r = Rect()
            node.getBoundsInScreen(r)
            if (r.width() > 0 && r.height() > 0 && tap(r.centerX().toFloat(), r.centerY().toFloat())) return true
        }
        return false
    }

    private fun collect(n: AccessibilityNodeInfo?, needle: String, exact: Boolean, out: MutableList<AccessibilityNodeInfo>, depth: Int) {
        if (n == null || out.size >= 10 || depth > 30) return
        val t = n.text?.toString()?.lowercase().orEmpty()
        val d = n.contentDescription?.toString()?.lowercase().orEmpty()
        val hit = if (exact) (t == needle || d == needle) else (t.contains(needle) || d.contains(needle))
        if (hit && n.isVisibleToUser) out.add(n)
        for (i in 0 until n.childCount) collect(n.getChild(i), needle, exact, out, depth + 1)
    }

    fun typeText(text: String, submit: Boolean): Boolean {
        val root = bestRoot() ?: return false
        var target: AccessibilityNodeInfo? = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (target == null || !target.isEditable) target = findEditable(root, 0)
        if (target == null) return false
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (ok && submit && Build.VERSION.SDK_INT >= 30) {
            target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        }
        return ok
    }

    private fun findEditable(n: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (n == null || depth > 30) return null
        if (n.isEditable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) {
            val r = findEditable(n.getChild(i), depth + 1)
            if (r != null) return r
        }
        return null
    }

    private fun findScrollable(n: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (n == null || depth > 30) return null
        if (n.isScrollable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) {
            val r = findScrollable(n.getChild(i), depth + 1)
            if (r != null) return r
        }
        return null
    }

    fun scroll(down: Boolean): Boolean {
        val root = bestRoot()
        val node = if (root != null) findScrollable(root, 0) else null
        if (node != null) {
            val act = if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            if (node.performAction(act)) return true
        }
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val a = dm.heightPixels * 0.7f
        val b = dm.heightPixels * 0.3f
        return if (down) swipe(x, a, x, b, 350) else swipe(x, b, x, a, 350)
    }

    // ------------------------------------------------------------ gestures
    fun tap(x: Float, y: Float): Boolean {
        val p = Path()
        p.moveTo(x, y)
        return gesture(p, 60)
    }

    fun longPress(x: Float, y: Float): Boolean {
        val p = Path()
        p.moveTo(x, y)
        return gesture(p, 900)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long): Boolean {
        val p = Path()
        p.moveTo(x1, y1)
        p.lineTo(x2, y2)
        return gesture(p, ms.coerceIn(80, 3000))
    }

    private fun gesture(path: Path, dur: Long): Boolean {
        val latch = CountDownLatch(1)
        var ok = false
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, dur))
            .build()
        val posted = dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                ok = true
                latch.countDown()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                latch.countDown()
            }
        }, null)
        if (!posted) return false
        latch.await(5, TimeUnit.SECONDS)
        return ok
    }

    // ------------------------------------------------------------ global keys / apps
    fun global(key: String): Boolean = when (key.trim().lowercase()) {
        "back" -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        "home" -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        "recents" -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        "notifications" -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        "quick_settings" -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        else -> false
    }

    fun openApp(name: String): String = Apps.launch(this, name)

    // ------------------------------------------------------------ screenshot (Android 11+)
    fun screenshotJpegBase64(): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        return takeShot()
    }

    @RequiresApi(30)
    private fun takeShot(): String? {
        val latch = CountDownLatch(1)
        var out: String? = null
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                try {
                    val hb = screenshot.hardwareBuffer
                    val hw = Bitmap.wrapHardwareBuffer(hb, screenshot.colorSpace)
                    val soft = hw?.copy(Bitmap.Config.ARGB_8888, false)
                    hb.close()
                    if (soft != null) {
                        val maxSide = 1280f
                        val scale = Math.min(1f, maxSide / Math.max(soft.width, soft.height))
                        val bmp = if (scale < 1f)
                            Bitmap.createScaledBitmap(soft, (soft.width * scale).toInt(), (soft.height * scale).toInt(), true)
                        else soft
                        val bos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 70, bos)
                        out = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
                    }
                } catch (e: Exception) {
                    out = null
                } finally {
                    latch.countDown()
                }
            }

            override fun onFailure(errorCode: Int) {
                latch.countDown()
            }
        })
        latch.await(8, TimeUnit.SECONDS)
        return out
    }

    companion object {
        @Volatile var instance: PhoneAccessibilityService? = null
        @Volatile var connectedAt = 0L

        /** 0 = band, 1 = Settings me ON par service connect nahi (ColorOS/Realme aksar maar deta hai), 2 = connected. */
        fun state(ctx: Context): Int {
            if (instance != null) return 2
            return if (listedAsEnabled(ctx)) 1 else 0
        }

        fun listedAsEnabled(ctx: Context): Boolean {
            val s = try { Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) } catch (e: Exception) { null }
                ?: return false
            val me = ctx.packageName
            return s.split(':').any { it.startsWith("$me/") && it.endsWith("PhoneAccessibilityService") }
        }

        fun isEnabled(ctx: Context): Boolean = state(ctx) >= 1

        fun statusLine(ctx: Context): String = when (state(ctx)) {
            2 -> "✅ Accessibility connected"
            1 -> "⚠ Accessibility ON dikh raha par connect nahi: Settings -> Accessibility -> is app ko OFF karke dobara ON karo (aur battery 'No restrictions' rakho)"
            else -> "❌ Accessibility OFF: Permissions -> Accessibility -> ON"
        }
    }
}
