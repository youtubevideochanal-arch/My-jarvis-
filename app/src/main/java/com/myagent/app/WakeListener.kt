package com.myagent.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * "Hamesha sunne wala" naam-mode: foreground service ke andar mic chalta rehta hai; jab aap naam bolo
 * (jaise "doremon, WhatsApp kholo") to kaam shuru. Battery zyada khata hai - Settings me ON/OFF.
 * Screen band hone par ruk jata hai (battery bachane aur Android ki rok ki wajah se).
 */
object WakeListener {
    private var voice: VoiceIn? = null
    @Volatile private var running = false
    private var fails = 0

    fun isRunning(): Boolean = running

    fun start(ctx: Context) {
        Hub.mainHandler.post {
            if (running) return@post
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Hub.log("⚠ Hamesha-suno ke liye Microphone permission chahiye.")
                return@post
            }
            running = true
            fails = 0
            Hub.log("👂 Hamesha-suno ON: bolo '" + Hub.prefs.appName + " ...'")
            loop(ctx)
        }
    }

    fun stop() {
        Hub.mainHandler.post {
            running = false
            try { voice?.destroy() } catch (e: Exception) { }
            voice = null
        }
    }

    private fun later(ctx: Context, ms: Long) {
        if (!running) return
        Hub.mainHandler.postDelayed({ if (running) loop(ctx) }, ms)
    }

    private fun loop(ctx: Context) {
        if (!running) return
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive || Hub.inCall() || Hub.voice.isSpeaking()) {
            later(ctx, if (Hub.voice.isSpeaking()) 1200 else 4000)
            return
        }
        val v = voice ?: VoiceIn(ctx).also { voice = it }
        v.start(object : VoiceIn.CB {
            override fun onPartial(text: String) {}
            override fun onState(text: String) {}
            override fun onFinal(text: String) {
                fails = 0
                val rest = NameMatch.strip(text, Hub.prefs.appName)
                if (rest == null) { later(ctx, 300); return }
                if (Hub.prefs.panelOn) MiniPanel.show(rest.isBlank())
                if (rest.isBlank()) { Hub.voice.speak("Ji, boliye?"); later(ctx, 2500) }
                else { Hub.submitText(rest); later(ctx, 1500) }
            }

            override fun onFail(msg: String, needFallback: Boolean) {
                fails++
                if (needFallback || fails > 25) {
                    Hub.log("⚠ Hamesha-suno ruk gaya: $msg")
                    running = false
                    return
                }
                later(ctx, if (fails < 4) 400L else 3000L)
            }
        })
    }
}
