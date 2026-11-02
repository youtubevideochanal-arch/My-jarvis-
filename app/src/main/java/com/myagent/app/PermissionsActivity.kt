package com.myagent.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Android-level permissions ek jagah: screen dekhna/control (Accessibility), doosri apps ke upar,
 * mic, notification aur battery.
 */
class PermissionsActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout

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
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun granted(p: String): Boolean = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun build() {
        root.removeAllViews()
        title("🔐 Permissions")
        note("Har row ke aage ✅ aaye tab sab theek hai. Button dabao, jo screen khule wahan ON karo, phir back aao.")

        val acc = PhoneAccessibilityService.state(this)
        row(
            "1. Accessibility (screen padhna + tap/swipe/type)",
            when (acc) {
                2 -> "Connected. Sab theek."
                1 -> "Settings me ON dikh raha hai par service CONNECT nahi hui (Realme/Oppo/Vivo/Xiaomi background me maar dete hain). Fix: yahan dabao -> is app ko OFF karo, phir ON. Fir Battery 'No restrictions' (neeche 7) aur Recents me app ko lock karo."
                else -> "Sabse zaroori. Iske bina AI sirf baat/web/code kar sakta hai, phone nahi chala sakta. Button dabao -> Installed apps -> is app -> ON."
            },
            acc == 2
        ) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        row(
            "   Accessibility grey/band dikh raha hai?",
            "APK sideload ki hai to Android 13+ 'Restricted settings' roak deta hai: App info kholo -> upar 3 dot -> 'Allow restricted settings' -> phir Accessibility me ON karo.",
            null,
            "App info kholo"
        ) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }

        row(
            "   Daayin taraf ka system 'accessibility' icon hatana",
            "Wo icon Android ka apna shortcut hai (app ka nahi). Settings -> Accessibility -> is app -> 'Shortcut' / 'Accessibility button' ko OFF karo. Realme/Oppo: Settings -> Additional settings -> Accessibility -> is app -> Shortcut OFF. Baayin taraf wala asli bubble agent OFF hote hi chala jata hai.",
            null,
            "Accessibility kholo"
        ) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        row(
            "2. Doosri apps ke upar dikhna",
            "Floating bubble dikhane aur background se apps kholne ke liye.",
            Settings.canDrawOverlays(this)
        ) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }

        row(
            "3. Microphone",
            "Voice me baat karne ke liye.",
            granted(Manifest.permission.RECORD_AUDIO)
        ) { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 21) }

        val notifOk = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
        row(
            "4. Notifications",
            "Agent chal raha hai wali notification (background me zinda rakhne ke liye).",
            notifOk
        ) {
            if (Build.VERSION.SDK_INT >= 33) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 22)
            }
        }

        row(
            "6. Termux se code chalana",
            "Termux me ek baar chalao: echo 'allow-external-apps=true' >> ~/.termux/termux.properties && termux-reload-settings (phir Termux band karke dobara kholo). Phir yahan Allow. Termux me python/node bhi install ho: pkg install python nodejs",
            granted("com.termux.permission.RUN_COMMAND")
        ) { ActivityCompat.requestPermissions(this, arrayOf("com.termux.permission.RUN_COMMAND"), 23) }

        row(
            "8. Digital assistant banao (home long-press se bulao)",
            "Settings -> Default apps -> Digital assistant app -> is app ko chuno. Phir kisi bhi screen par home long-press / swipe karte hi chhota panel + mic khulega. Quick Settings (neeche khinchne par) me bhi 'tile' add kar sakte ho.",
            null,
            "Assistant settings kholo"
        ) {
            try { startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }
            catch (e: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }

        row(
            "10. Notification padhna (WhatsApp/Instagram/SMS ke naye msg)",
            "Optional. ON karne par agent 'kisi ka message aaya?' padh sakta hai. Sirf phone ki RAM me rehta hai, kahin upload nahi hota. Settings me is app ko Allow karo.",
            NotifListener.enabled(this),
            "Notification access kholo"
        ) { try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (e: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) } }

        row(
            "9. Hamesha naam sunna (wake word)",
            "Settings -> Voice me 'Hamesha naam sunna' ON karo (mic permission + notification chahiye). Battery thodi zyada lagti hai. App ON karte waqt screen par khuli honi chahiye (Android 12+ ka niyam).",
            Hub.prefs.wakeAlways && granted(Manifest.permission.RECORD_AUDIO),
            "Mic allow karo"
        ) { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 21) }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        row(
            "7. Battery optimization se chhoot",
            "Taaki Android lamba task beech me na maar de.",
            pm.isIgnoringBatteryOptimizations(packageName)
        ) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    private fun title(t: String) {
        val tv = TextView(this)
        tv.text = t
        tv.textSize = 22f
        tv.setTextColor(Color.WHITE)
        tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
        root.addView(tv)
    }

    private fun note(t: String) {
        val tv = TextView(this)
        tv.text = t
        tv.textSize = 13f
        tv.setTextColor(Color.rgb(140, 150, 185))
        tv.setPadding(0, dp(6), 0, dp(6))
        root.addView(tv)
    }

    private fun row(head: String, desc: String, ok: Boolean?, btn: String = "Kholo / Allow", onClick: () -> Unit) {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(14), dp(12), dp(14), dp(12))
        val bg = GradientDrawable()
        bg.setColor(Color.rgb(21, 24, 38))
        bg.cornerRadius = dp(14).toFloat()
        c.background = bg

        val h = TextView(this)
        val mark = when (ok) {
            true -> "✅ "
            false -> "❌ "
            null -> ""
        }
        h.text = mark + head
        h.textSize = 15f
        h.setTextColor(Color.WHITE)
        c.addView(h)

        val d = TextView(this)
        d.text = desc
        d.textSize = 12f
        d.setTextColor(Color.rgb(140, 150, 185))
        d.setPadding(0, dp(4), 0, dp(6))
        c.addView(d)

        if (ok != true) {
            val b = Button(this)
            b.text = btn
            b.isAllCaps = false
            b.setOnClickListener { onClick() }
            c.addView(b)
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(10)
        root.addView(c, lp)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        build()
    }
}
