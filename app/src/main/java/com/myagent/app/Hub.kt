package com.myagent.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/** Poore app ka common kendra: settings, awaaz, agent engine, floating bubble aur chat log. */
object Hub {
    lateinit var app: Application
    lateinit var prefs: Prefs
    lateinit var voice: VoiceManager
    lateinit var engine: AgentEngine
    lateinit var bubble: FloatingBubble

    @Volatile var statusText: String = "Ready"
    @Volatile var onMasterUi: (() -> Unit)? = null
    val mainHandler = Handler(Looper.getMainLooper())
    @Volatile var appVisible: Boolean = false
    private val lines = ArrayList<String>()

    fun init(a: Application) {
        app = a
        prefs = Prefs(a)
        voice = VoiceManager(a, prefs)
        engine = AgentEngine(a)
        bubble = FloatingBubble(a)
    }

    /** Master ON/OFF ka ek hi kendra (button, voice, tile, panel sab yahi use karte hain). */
    fun setMaster(on: Boolean) {
        prefs.masterOn = on
        bubble.update()
        if (on) {
            startAgentService()
            PhoneAccessibilityService.selfEnable(app)
            if (Slots.available(prefs).isEmpty()) log("⚠ Koi API ready nahi. 3-dot menu -> Settings me API key daalo aur ON rakho.")
            if (PhoneAccessibilityService.state(app) != 2) log(PhoneAccessibilityService.statusLine(app) + " (sirf baat-cheet/web/code chalega, screen-control nahi)")
            engine.resumeIfNeeded()
        } else {
            engine.pause()
            voice.stop()
            MiniPanel.hide()
            try { app.stopService(Intent(app, AgentService::class.java)) } catch (e: Exception) { }
            statusText = "OFF"
        }
        mainHandler.post { onMasterUi?.invoke() }
    }

    fun startAgentService() {
        try {
            ContextCompat.startForegroundService(app, Intent(app, AgentService::class.java))
        } catch (e: Exception) {
            log("⚠ Service start nahi hui: " + (e.message ?: ""))
        }
    }

    /** Mic/panel/tile/wake-word se aaya text. direct = user ne khud mic dabaya (naam zaroori nahi). */
    fun onHeard(heard: String, direct: Boolean) {
        var text = heard.trim()
        if (text.isEmpty()) return
        val rest = NameMatch.strip(text, prefs.appName)
        if (rest != null) {
            text = rest
            if (text.isBlank()) {
                log("🤖 Ji, boliye?")
                voice.speak("Ji, boliye?")
                if (!direct) MiniPanel.show(true)
                return
            }
        } else if (prefs.requireName && !direct) {
            statusText = "👂 naam '" + prefs.appName + "' nahi suna - ignore"
            return
        }
        submitText(text)
    }

    /** Typed ya bola hua kaam: agar OFF ho to khud ON kar do (pehle "pehle ON dabao" bol kar kuch nahi hota tha). */
    fun submitText(text: String) {
        if (!prefs.masterOn) setMaster(true)
        log("🧑 $text")
        engine.submit(text)
    }

    /** Phone call (ya VoIP call) chal rahi hai? Tab agent ruk jata hai aur bolta nahi. */
    fun inCall(): Boolean {
        return try {
            val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION
        } catch (e: Exception) {
            false
        }
    }

    fun log(s: String) {
        synchronized(lines) {
            lines.add(s)
            while (lines.size > 40) lines.removeAt(0)
        }
        engine.listener?.onLog()
    }

    fun logText(): String = synchronized(lines) { lines.joinToString("\n\n") }

    fun clearLog() {
        synchronized(lines) { lines.clear() }
        engine.listener?.onLog()
    }
}
