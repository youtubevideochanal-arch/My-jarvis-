package com.myagent.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/** App ki apni sehat-jaanch: kaunsi cheez ON/OFF hai aur kya karna hai. */
object Scanner {
    class Item(val ok: Boolean, val title: String, val fix: String)

    fun items(ctx: Context): List<Item> {
        val p = Hub.prefs
        val out = ArrayList<Item>()
        val acc = PhoneAccessibilityService.state(ctx)
        out.add(
            Item(acc == 2, "Accessibility", when (acc) {
                2 -> "connected"
                1 -> "Settings me ON dikh raha par connect nahi: Accessibility me is app ko OFF->ON karo; battery 'No restrictions'; recents me app lock karo"
                else -> "OFF - Permissions me ON karo"
            })
        )
        out.add(Item(Settings.canDrawOverlays(ctx), "Display over other apps", "bubble/panel ke liye allow karo"))
        val mic = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        out.add(Item(mic, "Microphone", "Permissions me allow karo"))
        out.add(Item(try { android.speech.SpeechRecognizer.isRecognitionAvailable(ctx) } catch (e: Exception) { false }, "Speech service", "Google app / 'Speech Recognition & Synthesis' install-update karo"))
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        out.add(Item(pm.isIgnoringBatteryOptimizations(ctx.packageName), "Battery unrestricted", "Permissions -> Battery; Realme/Oppo me App info -> Battery -> Allow background activity"))
        val n = Slots.available(p)
        out.add(Item(n.isNotEmpty(), "AI API", if (n.isEmpty()) "Settings me kam se kam ek API key daalo" else n.joinToString(", ") { p.name(it) } + " ready"))
        out.add(Item(p.masterOn, "Master ON", "ON button dabao"))
        return out
    }

    fun report(ctx: Context): String = items(ctx).joinToString("\n") { (if (it.ok) "✅ " else "❌ ") + it.title + ": " + it.fix }

    fun problems(ctx: Context): Int = items(ctx).count { !it.ok }
}
