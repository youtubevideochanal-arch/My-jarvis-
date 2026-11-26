package com.myagent.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The agent's hands and eyes: reads the screen as numbered elements, taps, swipes, types. */
class AgentAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var inst: AgentAccessibilityService? = null

        /** True when the user has switched the service on in system settings (even if not yet connected). */
        fun enabled(c: android.content.Context): Boolean {
            if (inst != null) return true
            val v = android.provider.Settings.Secure.getString(
                c.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return v.contains(c.packageName + "/", ignoreCase = true)
        }

        /** The service object, waiting a few seconds for the system to (re)connect it after it was enabled. */
        fun get(c: android.content.Context): AgentAccessibilityService? {
            var n = 0
            while (inst == null && n < 16 && enabled(c)) {
                Thread.sleep(250)
                n++
            }
            return inst
        }
    }

    @Volatile
    private var nodes: List<AccessibilityNodeInfo> = emptyList()

    override fun onServiceConnected() {
        super.onServiceConnected()
        inst = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        inst = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        inst = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    // ---------- reading ----------

    fun currentPackage(): String = rootInActiveWindow?.packageName?.toString() ?: "?"

    fun dump(maxLines: Int, maxText: Int): String {
        val root = rootInActiveWindow ?: return "SCREEN: unavailable (no active window)"
        val list = ArrayList<AccessibilityNodeInfo>()
        val sb = StringBuilder()
        val dm = resources.displayMetrics
        sb.append("APP: ").append(root.packageName).append('\n')
        sb.append("SIZE: ").append(dm.widthPixels).append('x').append(dm.heightPixels).append('\n')
        walk(root, list, sb, maxLines, maxText, 0)
        nodes = list
        if (list.isEmpty()) sb.append("(no readable elements; try coordinates or wait)\n")
        return sb.toString()
    }

    private fun walk(
        n: AccessibilityNodeInfo?, list: ArrayList<AccessibilityNodeInfo>, sb: StringBuilder,
        max: Int, maxText: Int, depth: Int
    ) {
        if (n == null || list.size >= max || depth > 45) return
        if (!n.isVisibleToUser) return
        val raw = n.text ?: n.contentDescription
        val txt = raw?.toString()?.replace("\n", " ")?.take(maxText) ?: ""
        val interesting = n.isClickable || n.isEditable || n.isScrollable || n.isCheckable || txt.isNotBlank()
        if (interesting) {
            val r = Rect()
            n.getBoundsInScreen(r)
            val idx = list.size
            list.add(n)
            sb.append('[').append(idx).append("] ")
                .append(n.className?.toString()?.substringAfterLast('.') ?: "")
                .append(" \"").append(if (n.isPassword) "" else txt).append("\" ")
                .append(r.centerX()).append(',').append(r.centerY())
            if (n.isClickable) sb.append(" tap")
            if (n.isEditable) sb.append(" edit")
            if (n.isScrollable) sb.append(" scroll")
            if (n.isChecked) sb.append(" checked")
            if (n.isPassword) sb.append(" PASSWORD")
            sb.append('\n')
        }
        for (i in 0 until n.childCount) {
            if (list.size >= max) break
            walk(n.getChild(i), list, sb, max, maxText, depth + 1)
        }
    }

    fun label(i: Int): String {
        val n = nodes.getOrNull(i) ?: return ""
        return (n.text ?: n.contentDescription ?: n.viewIdResourceName ?: "").toString()
    }

    // ---------- acting ----------

    fun tapIndex(i: Int): String {
        val n = nodes.getOrNull(i) ?: return "no such element [$i] (screen changed?)"
        var c: AccessibilityNodeInfo? = n
        var hops = 0
        while (c != null && !c.isClickable && hops < 4) {
            c = c.parent
            hops++
        }
        if (c != null && c.isClickable && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return "tapped [$i]"
        val r = Rect()
        n.getBoundsInScreen(r)
        return tapXY(r.centerX().toFloat(), r.centerY().toFloat())
    }

    fun tapXY(x: Float, y: Float): String {
        val p = Path()
        p.moveTo(x, y)
        return gesture(p, 60)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long): String {
        val p = Path()
        p.moveTo(x1, y1)
        p.lineTo(x2, y2)
        return gesture(p, ms.coerceIn(100, 1500))
    }

    fun scroll(dir: String): String {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        return when (dir) {
            "down" -> swipe(w / 2, h * 0.72f, w / 2, h * 0.28f, 350)
            "up" -> swipe(w / 2, h * 0.28f, w / 2, h * 0.72f, 350)
            "left" -> swipe(w * 0.8f, h / 2, w * 0.2f, h / 2, 350)
            "right" -> swipe(w * 0.2f, h / 2, w * 0.8f, h / 2, 350)
            else -> "unknown direction"
        }
    }

    private fun gesture(p: Path, durationMs: Long): String {
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, durationMs))
            .build()
        val latch = CountDownLatch(1)
        var ok = false
        val posted = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                ok = true
                latch.countDown()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                latch.countDown()
            }
        }, Handler(Looper.getMainLooper()))
        if (!posted) return "gesture rejected"
        latch.await(4, TimeUnit.SECONDS)
        return if (ok) "ok" else "gesture cancelled"
    }

    fun typeText(text: String, index: Int?): String {
        val n: AccessibilityNodeInfo? =
            if (index != null) nodes.getOrNull(index)
            else rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (n == null) return "no input field (tap a field first or pass its [index])"
        if (n.isPassword) return "refused: password field"
        n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val b = Bundle()
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return if (n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)) "typed" else "type failed"
    }

    fun key(k: String): String {
        return when (k) {
            "back" -> res(performGlobalAction(GLOBAL_ACTION_BACK))
            "home" -> res(performGlobalAction(GLOBAL_ACTION_HOME))
            "recents" -> res(performGlobalAction(GLOBAL_ACTION_RECENTS))
            "notifications" -> res(performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS))
            "enter" -> {
                val f = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (f != null && Build.VERSION.SDK_INT >= 30) {
                    res(f.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id))
                } else "no focused field"
            }
            else -> "unknown key"
        }
    }

    private fun res(b: Boolean) = if (b) "ok" else "failed"
}
